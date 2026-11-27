package app.qi.qingtiao;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 无悬浮窗录制的状态机（纯逻辑，时间由调用方传入，方便单测）。
 *
 * 流程：arm(pkg) 登记目标，60 秒内有效 → 目标 App 下一次 TYPE_WINDOW_STATE_CHANGED 开始采集 →
 * 5 秒内每 500ms 由 SkipService 抓一次快照 → 到时自动结束，得到 Recording →
 * 用户回到轻跳选一个候选，buildRule() 生成规则，由 Scope.saveRecordedRule() 持久化。
 * 录制目标在 ARMED / CAPTURING 期间只采集、绝不点击。
 *
 * 线程：只在主线程调用（界面和无障碍回调都在主线程），方法仍加 synchronized 兜底；
 * 监听器在锁外回调。
 */
final class SkipRecorder {
    static final long ARM_MS = 60000L;
    static final long CAPTURE_MS = 5000L;
    static final long SNAPSHOT_INTERVAL_MS = 500L;

    enum State { IDLE, ARMED, CAPTURING, DONE, CANCELLED, TIMED_OUT }

    /** saveAsRule 的结果码。 */
    enum SaveCode { OK, NO_ID, BAD_INDEX, NO_RECORDING, PROTECTED, BAD_EXISTING_JSON }

    interface Listener { void onRecorderChanged(State state); }

    static final class RuleResult {
        final SaveCode code;
        final RuleBook.Entry entry;
        RuleResult(SaveCode code, RuleBook.Entry entry) {
            this.code = code;
            this.entry = entry;
        }
    }

    private static final SkipRecorder INSTANCE = new SkipRecorder();

    static SkipRecorder get() { return INSTANCE; }

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private State state = State.IDLE;
    private String target;
    private long armedAt;
    private long captureStart;
    private long lastSnapshot;
    private RecordingBuilder builder;
    private Recording last;

    SkipRecorder() {}

    void addListener(Listener l) { if (l != null) listeners.addIfAbsent(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    /** 登记录制目标。受保护包、空包名、轻跳自己返回 false。会顶掉正在进行的录制。 */
    boolean arm(String pkg, long now) {
        if (pkg == null || pkg.isEmpty() || RuleBook.isProtected(pkg) || RuleBook.SELF.equals(pkg)) {
            return false;
        }
        synchronized (this) {
            state = State.ARMED;
            target = pkg;
            armedAt = now;
            captureStart = 0;
            lastSnapshot = 0;
            builder = null;
        }
        notifyListeners(State.ARMED);
        return true;
    }

    /** 手动取消；只对 ARMED / CAPTURING 有效，已采到的数据丢弃。 */
    void cancel() {
        boolean changed;
        synchronized (this) {
            changed = state == State.ARMED || state == State.CAPTURING;
            if (changed) {
                state = State.CANCELLED;
                builder = null;
            }
        }
        if (changed) notifyListeners(State.CANCELLED);
    }

    /** 当前状态；顺带处理 60 秒未开始的超时和 5 秒采集到期。 */
    State state(long now) {
        advance(now);
        synchronized (this) { return state; }
    }

    synchronized String target() { return target; }

    /** 需要额外监听的包（ARMED / CAPTURING 时为目标包，否则 null）。 */
    String listenTarget(long now) {
        advance(now);
        synchronized (this) {
            return state == State.ARMED || state == State.CAPTURING ? target : null;
        }
    }

    /** 该包当前是否处在录制中（期间不点击）。 */
    boolean isRecording(String pkg, long now) {
        return pkg != null && pkg.equals(listenTarget(now));
    }

    /** 剩余毫秒：ARMED 时是等待目标启动的剩余时间，CAPTURING 时是采集剩余时间。 */
    synchronized long remainingMs(long now) {
        if (state == State.ARMED) return Math.max(0, armedAt + ARM_MS - now);
        if (state == State.CAPTURING) return Math.max(0, captureStart + CAPTURE_MS - now);
        return 0;
    }

    /**
     * 目标包的窗口变化。ARMED 时开始采集并返回 true（调用方应立即抓一次快照并开始 500ms 定时）。
     */
    boolean onWindowStateChanged(String pkg, String activity, long now) {
        advance(now);
        boolean started = false;
        synchronized (this) {
            if (pkg == null || !pkg.equals(target)) return false;
            if (state == State.ARMED) {
                state = State.CAPTURING;
                captureStart = now;
                lastSnapshot = 0;
                builder = new RecordingBuilder(pkg, System.currentTimeMillis());
                started = true;
            }
            if (state == State.CAPTURING && builder != null) builder.noteActivity(activity);
        }
        if (started) notifyListeners(State.CAPTURING);
        return started;
    }

    /** 采集中、且距上次快照已满 500ms。 */
    synchronized boolean snapshotDue(String pkg, long now) {
        return state == State.CAPTURING && pkg != null && pkg.equals(target)
            && now - captureStart < CAPTURE_MS
            && (lastSnapshot == 0 || now - lastSnapshot >= SNAPSHOT_INTERVAL_MS);
    }

    /** 记录一次快照（节点由调用方采好、已 recycle）。 */
    synchronized void addSnapshot(String pkg, List<RecordedNode> nodes, boolean adMarker, long now) {
        if (state != State.CAPTURING || builder == null || pkg == null || !pkg.equals(target)) return;
        lastSnapshot = now;
        builder.addSnapshot(nodes, adMarker);
    }

    /** 采集已满 5 秒则结束并返回结果（只返回一次），否则 null。 */
    Recording finishIfDue(long now) {
        Recording done = null;
        synchronized (this) {
            if (state == State.CAPTURING && now - captureStart >= CAPTURE_MS && builder != null) {
                done = builder.build();
                last = done;
                builder = null;
                state = State.DONE;
            }
        }
        if (done != null) notifyListeners(State.DONE);
        return done;
    }

    /** 最近一次完成的录制（内存里，进程被杀后用 RecordingStore.latest 读回）。 */
    synchronized Recording lastRecording() { return last; }

    /** 从 RecordingStore 读回的录制放进内存，供界面继续点选。 */
    synchronized void restore(Recording r) { if (r != null) last = r; }

    /** 回到 IDLE（界面关掉录制页时调用）；保留 lastRecording。 */
    void reset() {
        synchronized (this) {
            state = State.IDLE;
            target = null;
            builder = null;
        }
        notifyListeners(State.IDLE);
    }

    /**
     * 把录制里第 index 个候选做成规则：{pkg, title, ids:[viewId], activity, verified:true}。
     * 没有 viewId 返回 NO_ID，不退化成坐标点击。
     */
    static RuleResult buildRule(Recording rec, int index, String title) {
        if (rec == null) return new RuleResult(SaveCode.NO_RECORDING, null);
        if (RuleBook.isProtected(rec.pkg)) return new RuleResult(SaveCode.PROTECTED, null);
        if (index < 0 || index >= rec.candidates.size()) return new RuleResult(SaveCode.BAD_INDEX, null);
        RecordedNode n = rec.candidates.get(index);
        if (!n.hasId()) return new RuleResult(SaveCode.NO_ID, null);
        String activity = n.activity != null && !n.activity.isEmpty() ? n.activity : rec.activity;
        String name = title == null || title.trim().isEmpty() ? rec.pkg : title.trim();
        RuleBook.Entry e = new RuleBook.Entry(rec.pkg, name, true,
            activity == null || activity.isEmpty() ? null : activity, n.viewId);
        return new RuleResult(SaveCode.OK, e);
    }

    private void advance(long now) {
        State changed = null;
        Recording done = null;
        synchronized (this) {
            if (state == State.ARMED && now - armedAt > ARM_MS) {
                state = State.TIMED_OUT;
                changed = State.TIMED_OUT;
            } else if (state == State.CAPTURING && now - captureStart >= CAPTURE_MS && builder != null) {
                done = builder.build();
                last = done;
                builder = null;
                state = State.DONE;
                changed = State.DONE;
            }
        }
        if (changed != null) notifyListeners(changed);
    }

    private void notifyListeners(State s) {
        List<Listener> copy = new ArrayList<Listener>(listeners);
        for (Listener l : copy) l.onRecorderChanged(s);
    }
}
