package app.qi.qingtiao;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.io.File;

public final class SkipService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        AppCatalog.Listener, SkipRecorder.Listener {
    private final WindowSession session = new WindowSession();
    private Scope scope;
    private static final long CONTENT_THROTTLE_MS = 120L;
    private static final long BURST_WINDOW_MS = 2500L;
    private static final long BURST_THROTTLE_MS = 25L;
    private static final long BURST_POLL_MS = 50L;
    private int burstGeneration = -1;
    private volatile boolean screenOff = false;
    private boolean screenReceiverRegistered = false;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                screenOff = true;
                stopBurstPoll();
                session.reset();
            } else if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
                screenOff = false;
                session.reset();
            }
        }
    };
    private final Runnable burstPoll = new Runnable() {
        @Override public void run() { onBurstPoll(); }
    };
    private HitLog hitLog;
    /** 无障碍回调本身就在主线程；定时器也投到主线程，只做内存判断，写日志交给 HitLog 的单线程。 */
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HashMap<String, Boolean> activityCache = new HashMap<String, Boolean>();
    private static final int ACTIVITY_CACHE_MAX = 512;

    // ---- 任务 B：生效范围快照（applyScope 时刷新，事件入口只读内存） ----
    private AppCatalog catalog;
    private boolean snapEnabled;
    private boolean snapAll;
    private Set<String> snapPicked = new HashSet<String>();
    private Set<String> snapExcluded = new HashSet<String>();
    private Set<String> homes = new HashSet<String>();
    private Set<String> imes = new HashSet<String>();
    private final HashMap<String, Boolean> systemCache = new HashMap<String, Boolean>();

    // ---- 任务 C：录制 ----
    private final SkipRecorder recorder = SkipRecorder.get();
    private RecordingStore recordings;
    private String lastListenTarget;
    private final Runnable recorderTick = new Runnable() {
        @Override public void run() { onRecorderTick(); }
    };
    private final Runnable recorderTimeout = new Runnable() {
        @Override public void run() { recorder.state(SystemClock.uptimeMillis()); }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        scope = new Scope(this);
        hitLog = new HitLog(getFilesDir());
        recordings = new RecordingStore(getFilesDir());
        catalog = AppCatalog.get(this);
        catalog.addListener(this);
        catalog.ensureLoaded();
        recorder.addListener(this);
        scope.p.registerOnSharedPreferenceChangeListener(this);
        if (!screenReceiverRegistered) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_USER_PRESENT);
            registerReceiver(screenReceiver, f);
            screenReceiverRegistered = true;
        }
        applyScope();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (key == null || key.equals(Scope.ENABLED) || key.equals(Scope.ALL)
                || key.equals(Scope.PICKED) || key.equals(Scope.EXCLUDED)
                || key.equals(Scope.CUSTOM_RULES)) {
            applyScope();
        }
    }

    /** 装卸包后：清掉包分类缓存，重新取桌面/输入法集合（只在「全部应用」模式需要）。 */
    @Override
    public void onCatalogChanged() {
        systemCache.clear();
        if (catalog != null && catalog.isLoaded() && scope != null) {
            List<String> allowed = new ArrayList<String>();
            for (AppInfo info : catalog.listable()) allowed.add(info.pkg);
            scope.pruneSelected(allowed);
        }
        if (snapEnabled) refreshPackageClasses();
    }

    /** 录制状态变化（主线程）。目标包进出监听名单时重配无障碍；录完立刻落盘。 */
    @Override
    public void onRecorderChanged(SkipRecorder.State state) {
        long now = SystemClock.uptimeMillis();
        if (state == SkipRecorder.State.DONE && recordings != null) {
            recordings.saveAsync(recorder.lastRecording());
        }
        main.removeCallbacks(recorderTimeout);
        if (state == SkipRecorder.State.ARMED) {
            main.postDelayed(recorderTimeout, recorder.remainingMs(now) + 100L);
        }
        if (state != SkipRecorder.State.CAPTURING) main.removeCallbacks(recorderTick);
        stopBurstPoll();
        String target = recorder.listenTarget(now);
        boolean changed = target == null ? lastListenTarget != null : !target.equals(lastListenTarget);
        if (changed) applyScope();
    }

    private Set<String> launcherPackages() {
        Set<String> launchers = new HashSet<String>();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent, 0);
        if (list != null) {
            for (ResolveInfo info : list) {
                if (info.activityInfo != null) {
                    launchers.add(info.activityInfo.packageName);
                }
            }
        }
        return launchers;
    }

    private void applyScope() {
        if (scope == null) return;
        session.reset();
        session.takeClosed();
        snapEnabled = scope.enabled();
        snapAll = scope.all();
        snapPicked = scope.selected();
        snapExcluded = scope.excluded();
        if (catalog != null && catalog.isLoaded()) {
            List<String> allowed = new ArrayList<String>();
            for (AppInfo info : catalog.listable()) allowed.add(info.pkg);
            scope.pruneSelected(allowed);
            snapPicked = scope.selected();
        }
        if (snapEnabled) refreshPackageClasses();
        String target = recorder.listenTarget(SystemClock.uptimeMillis());
        lastListenTarget = target;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        ScopePolicy.Filter f = ScopePolicy.serviceFilter(snapEnabled, snapAll, snapPicked, target);
        info.eventTypes = f.listen
            ? AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            : 0;
        info.packageNames = f.packages;
        setServiceInfo(info);
    }

    /** 桌面和输入法集合都很小；只在 applyScope / 装卸包时查一次，事件里不查。 */
    private void refreshPackageClasses() {
        try {
            homes = launcherPackages();
        } catch (RuntimeException ignored) {
            homes = new HashSet<String>();
        }
        Set<String> next = new HashSet<String>();
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                for (InputMethodInfo m : imm.getInputMethodList()) next.add(m.getPackageName());
            }
        } catch (RuntimeException ignored) { }
        imes = next;
    }

    /** 未更新过的预装系统应用。优先用 AppCatalog 的缓存，没有时查一次并缓存。 */
    private boolean isSystemPackage(String pkg) {
        Boolean known = catalog == null ? null : catalog.isSystem(pkg);
        if (known != null) return known;
        Boolean cached = systemCache.get(pkg);
        if (cached != null) return cached;
        boolean system;
        try {
            ApplicationInfo app = getPackageManager().getApplicationInfo(pkg, 0);
            system = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                && (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
        } catch (PackageManager.NameNotFoundException gone) {
            system = false;
        } catch (RuntimeException unexpected) {
            system = true;
        }
        if (systemCache.size() >= ACTIVITY_CACHE_MAX) systemCache.clear();
        systemCache.put(pkg, system);
        return system;
    }

    /** 这个包能不能找「跳过」并点击（PROTECTED_PACKAGES 永远不能）。 */
    private boolean handles(String pkg) {
        if (!snapEnabled || RuleBook.isProtected(pkg)) return false;
        if (!snapAll) {
            return ScopePolicy.shouldHandle(pkg, true, false, snapPicked, snapExcluded, false, false, false);
        }
        if (snapExcluded.contains(pkg)) return false;
        boolean home = homes.contains(pkg) || (catalog != null && catalog.isHome(pkg));
        boolean ime = imes.contains(pkg) || (catalog != null && catalog.isIme(pkg));
        return ScopePolicy.shouldHandle(pkg, true, true, snapPicked, snapExcluded,
            home, !home && !ime && isSystemPackage(pkg), ime);
    }

    private static String string(CharSequence text) {
        return text == null ? "" : text.toString();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (screenOff || scope == null || hitLog == null || event == null) return;
        String pkg = string(event.getPackageName());
        if (pkg.isEmpty() || RuleBook.isProtected(pkg)) return;
        long now = SystemClock.uptimeMillis();
        if (recorder.isRecording(pkg, now)) {
            onRecordingEvent(event, pkg, now);
            return;
        }
        if (!handles(pkg)) return;

        RuleBook.Entry r = RuleBook.find(pkg);
        int window = event.getWindowId();
        int type = event.getEventType();

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String raw = string(event.getClassName());
            if (window < 0) {
                session.reset();
                logClosedSession();
                return;
            }
            boolean isActivity = isActivityClass(pkg, raw);
            String activity = session.resolveActivity(pkg, raw, isActivity);
            boolean dedicated = RuleBook.dedicated(r, activity) || RuleBook.dedicated(r, raw);
            boolean opened = session.enter(pkg, raw, window, dedicated, now, isActivity);
            logClosedSession();
            if (opened) {
                hitLog.log(sessionOpenEntry(pkg, session.screen, raw));
                scheduleExpiry(session.generation);
                scheduleBurstPoll(session.generation);
            }
        } else if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (window < 0) return;
            long throttle = (now - session.opened <= BURST_WINDOW_MS) ? BURST_THROTTLE_MS : CONTENT_THROTTLE_MS;
            if (now - session.lastContent < throttle) return;
            boolean opened = session.touch(pkg, window, now);
            session.lastContent = now;
            logClosedSession();
            if (opened) scheduleBurstPoll(session.generation);
        } else {
            return;
        }

        if (!session.live(pkg, window, now)) return;
        trySkipCurrent(r, pkg, window, now);
    }

    private void scheduleBurstPoll(int generation) {
        burstGeneration = generation;
        main.removeCallbacks(burstPoll);
        main.postDelayed(burstPoll, BURST_POLL_MS);
    }

    private void stopBurstPoll() {
        burstGeneration = -1;
        main.removeCallbacks(burstPoll);
    }

    private void onBurstPoll() {
        if (screenOff || scope == null || hitLog == null) {
            stopBurstPoll();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (session.generation != burstGeneration || session.used || session.pkg.isEmpty() || session.window < 0) {
            stopBurstPoll();
            return;
        }
        if (now - session.opened > BURST_WINDOW_MS || !session.live(session.pkg, session.window, now)) {
            stopBurstPoll();
            return;
        }
        String pkg = session.pkg;
        if (!handles(pkg) || recorder.isRecording(pkg, now)) {
            stopBurstPoll();
            return;
        }
        trySkipCurrent(RuleBook.find(pkg), pkg, session.window, now);
        if (!session.used && session.generation == burstGeneration && now - session.opened <= BURST_WINDOW_MS) {
            main.postDelayed(burstPoll, BURST_POLL_MS);
        } else {
            stopBurstPoll();
        }
    }

    private void trySkipCurrent(RuleBook.Entry r, String pkg, int window, long now) {
        Candidate best = findTarget(r, window, now);
        if (best == null) return;

        try {
            if (!handles(pkg) || RuleBook.PROTECTED_PACKAGES.contains(pkg)
                    || recorder.isRecording(pkg, SystemClock.uptimeMillis())
                    || !session.live(pkg, window, SystemClock.uptimeMillis())) return;
            if (best.score.total >= SkipScorer.CLICK_THRESHOLD) {
                session.attempts++;
                session.markProduced();
                String method;
                if (r.preferGesture) {
                    method = performGestureClick(best.node) ? "gesture" : performNodeOrParentClick(best.node);
                } else {
                    method = performNodeOrParentClick(best.node);
                    if ("none".equals(method) && performGestureClick(best.node)) {
                        method = "gesture";
                    }
                }
                boolean clicked = !"none".equals(method);
                if (clicked) {
                    session.used = true;
                    stopBurstPoll();
                    scope.recordSkip();
                }
                hitLog.log(hitEntry(r, best.features, best.score, method,
                    clicked ? "clicked" : "failed", session.rawEventClass));
            } else if (best.score.total >= SkipScorer.NEAR_MISS_MIN) {
                session.markProduced();
                if (session.firstNearMiss(best.features.viewId, best.features.text,
                        best.features.boundsInScreen, best.score.total)) {
                    hitLog.log(hitEntry(r, best.features, best.score, "none", "near_miss",
                        session.rawEventClass));
                }
            }
        } finally {
            best.node.recycle();
        }
    }

    // ---- 任务 C：录制采集（只采不点） ----

    private void onRecordingEvent(AccessibilityEvent event, String pkg, long now) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getWindowId() >= 0) {
            String raw = string(event.getClassName());
            String activity = session.resolveActivity(pkg, raw, isActivityClass(pkg, raw));
            if (recorder.onWindowStateChanged(pkg, activity, now)) {
                main.removeCallbacks(recorderTick);
        stopBurstPoll();
                main.postDelayed(recorderTick, SkipRecorder.SNAPSHOT_INTERVAL_MS);
            }
        }
        if (recorder.snapshotDue(pkg, now)) captureSnapshot(pkg, now);
    }

    /** 采集期间每 500ms 抓一次；满 5 秒结束（结果由 onRecorderChanged 落盘）。 */
    private void onRecorderTick() {
        long now = SystemClock.uptimeMillis();
        if (recorder.finishIfDue(now) != null) return;
        if (recorder.state(now) != SkipRecorder.State.CAPTURING) return;
        String target = recorder.target();
        if (recorder.snapshotDue(target, now)) captureSnapshot(target, now);
        main.postDelayed(recorderTick, SkipRecorder.SNAPSHOT_INTERVAL_MS);
    }

    /** 抓一次当前窗口的候选节点快照。遍历上限 800，所有节点都 recycle，不点击。 */
    private void captureSnapshot(String pkg, long now) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        if (!pkg.equals(string(root.getPackageName()))) {
            root.recycle();
            return;
        }
        RuleBook.Entry r = RuleBook.find(pkg);
        int[] screen = physicalScreenSize();
        String activity = session.knownActivity(pkg);
        ArrayList<RecordedNode> nodes = new ArrayList<RecordedNode>();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<AccessibilityNodeInfo>();
        HashMap<AccessibilityNodeInfo, Boolean> scrollable = new HashMap<AccessibilityNodeInfo, Boolean>();
        queue.add(root);
        scrollable.put(root, Boolean.FALSE);
        boolean marker = false;
        int visited = 0;
        try {
            while (!queue.isEmpty()) {
                AccessibilityNodeInfo n = queue.removeFirst();
                try {
                    if (++visited > 800) break;
                    boolean scrollableAncestor = Boolean.TRUE.equals(scrollable.remove(n));
                    if (!pkg.equals(string(n.getPackageName()))) continue;
                    if (n.isVisibleToUser()) {
                        String id = RuleBook.localId(r, string(n.getViewIdResourceName()));
                        String text = string(n.getText()).trim();
                        String desc = string(n.getContentDescription()).trim();
                        marker |= RuleBook.adText(text) || RuleBook.adText(desc) || RuleBook.adContainer(id);
                        if (!id.isEmpty() || !text.isEmpty() || !desc.isEmpty()) {
                            Rect rect = new Rect();
                            n.getBoundsInScreen(rect);
                            RecordedNode rn = new RecordedNode();
                            rn.viewId = id;
                            rn.text = text;
                            rn.desc = desc;
                            rn.className = string(n.getClassName());
                            rn.activity = activity;
                            rn.clickable = n.isClickable();
                            rn.scrollableAncestor = scrollableAncestor;
                            rn.bounds = new int[]{rect.left, rect.top, rect.right, rect.bottom};
                            rn.screenW = screen[0];
                            rn.screenH = screen[1];
                            nodes.add(rn);
                        }
                    }
                    boolean childScrollable = scrollableAncestor || n.isScrollable();
                    int children = n.getChildCount();
                    if (children + queue.size() + visited <= 800) {
                        for (int i = 0; i < children; i++) {
                            AccessibilityNodeInfo child = n.getChild(i);
                            if (child != null) {
                                queue.add(child);
                                scrollable.put(child, childScrollable);
                            }
                        }
                    }
                } finally {
                    n.recycle();
                }
            }
        } finally {
            while (!queue.isEmpty()) {
                queue.removeFirst().recycle();
            }
        }
        recorder.addSnapshot(pkg, nodes, marker, now);
    }

    /** 被新窗口或 reset 顶掉、且没点击也没候选的旧会话，补一条 no_candidate。 */
    private void logClosedSession() {
        WindowSession.Report closed = session.takeClosed();
        if (closed != null) hitLog.log(noCandidateEntry(closed));
    }

    /** 会话到期（4.5s）后检查一次；同一窗口最多写一条 no_candidate。 */
    private void scheduleExpiry(final int generation) {
        main.postDelayed(new Runnable() {
            @Override public void run() {
                WindowSession.Report report = session.expire(generation);
                if (report != null && hitLog != null) hitLog.log(noCandidateEntry(report));
            }
        }, RuleBook.WINDOW_MS + 100L);
    }

    private String performNodeOrParentClick(AccessibilityNodeInfo node) {
        if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return "node";
        }
        AccessibilityNodeInfo parent = node.getParent();
        int depth = 0;
        while (parent != null && depth < 3) {
            AccessibilityNodeInfo next = parent.getParent();
            try {
                if (parent.isClickable() && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    if (next != null) next.recycle();
                    return "parent";
                }
            } finally {
                parent.recycle();
            }
            parent = next;
            depth++;
        }
        if (parent != null) parent.recycle();
        return "none";
    }

    private boolean performGestureClick(AccessibilityNodeInfo node) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.width() <= 0 || bounds.height() <= 0) return false;
        float x = bounds.exactCenterX();
        float y = bounds.exactCenterY();
        if (x < 0 || y < 0) return false;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0L, 25L);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        return dispatchGesture(gesture, null, null);
    }

    /** 已验证规则的极速通道：直接按完整 viewId 查找，命中达到点击阈值即刻返回，跳过全树遍历。 */
    private Candidate findVerifiedFast(AccessibilityNodeInfo root, RuleBook.Entry r,
            int screenW, int screenH, long windowAgeMs) {
        AccessibilityNodeInfo bestNode = null;
        NodeFeatures bestFeatures = null;
        SkipScorer.Score bestScore = null;
        for (String id : r.ids) {
            if (id == null || id.isEmpty()) continue;
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(r.pkg + ":id/" + id);
            if (nodes == null || nodes.isEmpty()) continue;
            for (AccessibilityNodeInfo n : nodes) {
                if (n == null) continue;
                try {
                    if (!n.isVisibleToUser() || !n.isEnabled()) continue;
                    Rect rect = new Rect();
                    n.getBoundsInScreen(rect);
                    if (rect.width() <= 0 || rect.height() <= 0 || rect.width() >= 650 || rect.height() >= 320) continue;
                    if (hasScrollableAncestor(n)) continue;
                    NodeFeatures f = new NodeFeatures();
                    f.text = string(n.getText()).trim();
                    f.desc = string(n.getContentDescription()).trim();
                    f.viewId = id;
                    f.className = string(n.getClassName());
                    f.ruleIdHit = true;
                    f.ruleVerified = true;
                    f.boundsInScreen = new int[]{rect.left, rect.top, rect.right, rect.bottom};
                    f.screenW = screenW;
                    f.screenH = screenH;
                    f.windowAgeMs = windowAgeMs;
                    f.activityClass = session.screen;
                    f.isDedicatedActivity = session.dedicated;
                    f.windowHasAdMarker = true;
                    f.hasScrollableAncestor = false;
                    SkipScorer.Score s = SkipScorer.score(f);
                    if (s.total >= SkipScorer.CLICK_THRESHOLD && (bestScore == null || s.total > bestScore.total)) {
                        if (bestNode != null) bestNode.recycle();
                        bestNode = AccessibilityNodeInfo.obtain(n);
                        bestFeatures = f;
                        bestScore = s;
                    }
                } finally {
                    n.recycle();
                }
            }
        }
        if (bestNode == null) return null;
        return new Candidate(bestNode, bestFeatures, bestScore);
    }

    private static boolean hasScrollableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo p = node.getParent();
        int depth = 0;
        while (p != null && depth < 6) {
            AccessibilityNodeInfo next = p.getParent();
            try {
                if (p.isScrollable()) {
                    if (next != null) next.recycle();
                    return true;
                }
            } finally {
                p.recycle();
            }
            p = next;
            depth++;
        }
        if (p != null) p.recycle();
        return false;
    }

    private static final String[] FAST_TEXT_QUERIES = {"跳过", "Skip"};

    private Candidate findTextFast(AccessibilityNodeInfo root, RuleBook.Entry r,
                                   int screenW, int screenH, long windowAgeMs) {
        AccessibilityNodeInfo bestNode = null;
        NodeFeatures bestFeatures = null;
        SkipScorer.Score bestScore = null;
        for (String query : FAST_TEXT_QUERIES) {
            List<AccessibilityNodeInfo> found;
            try {
                found = root.findAccessibilityNodeInfosByText(query);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (found == null || found.isEmpty()) continue;
            for (AccessibilityNodeInfo n : found) {
                if (n == null) continue;
                try {
                    if (!n.isVisibleToUser()) continue;
                    if (!r.pkg.equals(string(n.getPackageName()))) continue;
                    String text = string(n.getText()).trim();
                    String desc = string(n.getContentDescription()).trim();
                    if (!RuleBook.skipText(text) && !RuleBook.skipText(desc)) continue;
                    Rect rect = new Rect();
                    n.getBoundsInScreen(rect);
                    if (rect.right <= rect.left || rect.bottom <= rect.top) continue;
                    if (hasScrollableAncestor(n)) continue;
                    String id = RuleBook.localId(r, string(n.getViewIdResourceName()));
                    NodeFeatures f = new NodeFeatures();
                    f.text = text;
                    f.desc = desc;
                    f.viewId = id;
                    f.className = string(n.getClassName());
                    f.ruleIdHit = !id.isEmpty() && r.ids.contains(id);
                    f.ruleVerified = r.verified;
                    f.boundsInScreen = new int[]{rect.left, rect.top, rect.right, rect.bottom};
                    f.screenW = screenW;
                    f.screenH = screenH;
                    f.windowAgeMs = windowAgeMs;
                    f.activityClass = session.screen;
                    f.isDedicatedActivity = session.dedicated;
                    f.windowHasAdMarker = false;
                    f.hasScrollableAncestor = false;
                    SkipScorer.Score s = SkipScorer.score(f);
                    if (s.total >= SkipScorer.CLICK_THRESHOLD && (bestScore == null || s.total > bestScore.total)) {
                        if (bestNode != null) bestNode.recycle();
                        bestNode = AccessibilityNodeInfo.obtain(n);
                        bestFeatures = f;
                        bestScore = s;
                    }
                } finally {
                    n.recycle();
                }
            }
        }
        if (bestNode == null) return null;
        return new Candidate(bestNode, bestFeatures, bestScore);
    }

    private Candidate findTarget(RuleBook.Entry r, int window, long now) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        if (root.getWindowId() != window || !r.pkg.equals(string(root.getPackageName()))) {
            root.recycle();
            return null;
        }

        int[] fastScreen = physicalScreenSize();
        long fastAgeMs = now - session.opened;
        if (r.verified && !r.ids.isEmpty()) {
            Candidate fast = findVerifiedFast(root, r, fastScreen[0], fastScreen[1], fastAgeMs);
            if (fast != null) {
                root.recycle();
                return fast;
            }
        }
        Candidate textFast = findTextFast(root, r, fastScreen[0], fastScreen[1], fastAgeMs);
        if (textFast != null) {
            root.recycle();
            return textFast;
        }

        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<AccessibilityNodeInfo>();
        HashMap<AccessibilityNodeInfo, Boolean> scrollable = new HashMap<AccessibilityNodeInfo, Boolean>();
        queue.add(root);
        scrollable.put(root, Boolean.FALSE);
        int[] screen = physicalScreenSize();
        int screenW = screen[0];
        int screenH = screen[1];
        long windowAgeMs = now - session.opened;
        AccessibilityNodeInfo candidate = null;
        NodeFeatures features = null;
        int bestSoFar = Integer.MIN_VALUE;
        boolean marker = false;
        boolean ambiguous = false;
        int visited = 0;

        try {
            while (!queue.isEmpty() && !ambiguous) {
                AccessibilityNodeInfo n = queue.removeFirst();
                try {
                    if (++visited > 800) break;
                    boolean scrollableAncestor = Boolean.TRUE.equals(scrollable.remove(n));
                    if (!r.pkg.equals(string(n.getPackageName()))) continue;
                    boolean selfScrollable = n.isScrollable();
                    if (n.isVisibleToUser()) {
                        String id = RuleBook.localId(r, string(n.getViewIdResourceName()));
                        String text = string(n.getText()).trim();
                        String desc = string(n.getContentDescription()).trim();
                        marker |= RuleBook.adText(text) || RuleBook.adText(desc) || RuleBook.adContainer(id);
                        boolean label = RuleBook.skipText(text) || RuleBook.skipText(desc);
                        boolean idHit = !id.isEmpty() && r.ids.contains(id);
                        boolean idHint = !id.isEmpty() && SkipScorer.ID_HINT.matcher(id.toLowerCase(Locale.ROOT)).matches();

                        if (n.isEnabled() && (label || idHit || idHint)) {
                            Rect rect = new Rect();
                            n.getBoundsInScreen(rect);
                            boolean reasonableSize = rect.width() > 0 && rect.height() > 0
                                && rect.width() < 650 && rect.height() < 320;
                            if (reasonableSize) {
                                NodeFeatures f = new NodeFeatures();
                                f.text = text;
                                f.desc = desc;
                                f.viewId = id;
                                f.className = string(n.getClassName());
                                f.ruleIdHit = idHit;
                                f.ruleVerified = idHit && r.verified;
                                f.boundsInScreen = new int[]{rect.left, rect.top, rect.right, rect.bottom};
                                f.screenW = screenW;
                                f.screenH = screenH;
                                f.windowAgeMs = windowAgeMs;
                                f.activityClass = session.screen;
                                f.isDedicatedActivity = session.dedicated;
                                f.windowHasAdMarker = marker;
                                f.hasScrollableAncestor = scrollableAncestor;
                                SkipScorer.Score s = SkipScorer.score(f);
                                if (candidate != null && s.total == bestSoFar) {
                                    ambiguous = true;
                                } else if (candidate == null || s.total > bestSoFar) {
                                    bestSoFar = s.total;
                                    features = f;
                                    AccessibilityNodeInfo next = AccessibilityNodeInfo.obtain(n);
                                    if (candidate != null) candidate.recycle();
                                    candidate = next;
                                }
                            }
                        }
                    }
                    // 修正：任务 A 里这一步漏了，子节点从没登记「有可滚动祖先」，-40 实际从未生效。
                    boolean childScrollable = scrollableAncestor || selfScrollable;
                    int children = n.getChildCount();
                    if (children + queue.size() + visited <= 800) {
                        for (int i = 0; i < children; i++) {
                            AccessibilityNodeInfo child = n.getChild(i);
                            if (child != null) {
                                queue.add(child);
                                if (childScrollable) scrollable.put(child, Boolean.TRUE);
                            }
                        }
                    }
                } finally {
                    n.recycle();
                }
            }
        } finally {
            while (!queue.isEmpty()) {
                queue.removeFirst().recycle();
            }
        }

        session.noteScan(visited, bestSoFar);
        if (ambiguous || candidate == null) {
            if (candidate != null) candidate.recycle();
            return null;
        }
        features.windowHasAdMarker = marker;
        SkipScorer.Score score = SkipScorer.score(features);
        session.noteScan(visited, score.total);
        if (RuleBook.SELF.equals(r.pkg) && !(features.ruleIdHit && session.dedicated && marker)) {
            candidate.recycle();
            return null;
        }
        return new Candidate(candidate, features, score);
    }

    private static final class Candidate {
        final AccessibilityNodeInfo node;
        final NodeFeatures features;
        final SkipScorer.Score score;

        Candidate(AccessibilityNodeInfo node, NodeFeatures features, SkipScorer.Score score) {
            this.node = node;
            this.features = features;
            this.score = score;
        }
    }

    private static HitLog.Entry hitEntry(RuleBook.Entry r, NodeFeatures f, SkipScorer.Score s,
            String method, String result, String rawEventClass) {
        HitLog.Entry e = new HitLog.Entry();
        e.ts = System.currentTimeMillis();
        e.pkg = r.pkg;
        e.activity = f.activityClass;
        e.rawEventClass = rawEventClass;
        e.nodeId = f.viewId;
        e.nodeText = f.text;
        e.bounds = f.boundsInScreen;
        e.score = s.total;
        e.details = s.details;
        e.clickMethod = method;
        e.result = result;
        return e;
    }

    private static HitLog.Entry sessionOpenEntry(String pkg, String activity, String rawEventClass) {
        HitLog.Entry e = new HitLog.Entry();
        e.ts = System.currentTimeMillis();
        e.pkg = pkg;
        e.activity = activity;
        e.rawEventClass = rawEventClass;
        e.result = "session_open";
        return e;
    }

    private static HitLog.Entry noCandidateEntry(WindowSession.Report report) {
        HitLog.Entry e = new HitLog.Entry();
        e.ts = System.currentTimeMillis();
        e.pkg = report.pkg;
        e.activity = report.activity;
        e.rawEventClass = report.rawEventClass;
        e.result = "no_candidate";
        boolean none = report.bestScore == WindowSession.NO_SCORE;
        e.score = none ? 0 : report.bestScore;
        e.details = java.util.Arrays.asList("visited:" + report.visited,
            "bestScore:" + (none ? "none" : String.valueOf(report.bestScore)));
        return e;
    }

    /**
     * 物理屏幕分辨率（含状态栏、导航栏），和 getBoundsInScreen 用同一坐标系。
     * API 30+ 用 WindowMetrics；更低版本用 Display.getRealMetrics。
     */
    private int[] physicalScreenSize() {
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Rect current = wm.getCurrentWindowMetrics().getBounds();
                    Rect max = wm.getMaximumWindowMetrics().getBounds();
                    int w = Math.max(current.width(), max.width());
                    int h = Math.max(current.height(), max.height());
                    if (w > 0 && h > 0) return new int[]{w, h};
                } else {
                    DisplayMetrics m = new DisplayMetrics();
                    wm.getDefaultDisplay().getRealMetrics(m);
                    if (m.widthPixels > 0 && m.heightPixels > 0) {
                        return new int[]{m.widthPixels, m.heightPixels};
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // 取不到就退回下面的近似值，最多少一个 corner 加分。
        }
        DisplayMetrics fallback = getResources().getDisplayMetrics();
        return new int[]{fallback.widthPixels, fallback.heightPixels};
    }

    /** className 是否为该包里声明过的 Activity；结果按 pkg/cls 缓存，避免每次事件都走 Binder。 */
    private boolean isActivityClass(String pkg, String cls) {
        if (cls.isEmpty() || !cls.contains(".") || cls.startsWith("android.")) return false;
        String key = pkg + "/" + cls;
        Boolean cached = activityCache.get(key);
        if (cached != null) return cached;
        boolean result;
        try {
            getPackageManager().getActivityInfo(new ComponentName(pkg, cls), 0);
            result = true;
        } catch (PackageManager.NameNotFoundException notAnActivity) {
            result = false;
        } catch (RuntimeException unexpected) {
            result = false;
        }
        if (activityCache.size() >= ACTIVITY_CACHE_MAX) activityCache.clear();
        activityCache.put(key, result);
        return result;
    }

    @Override
    public void onInterrupt() {
        session.reset();
    }

    @Override
    public void onDestroy() {
        if (screenReceiverRegistered) {
            try { unregisterReceiver(screenReceiver); } catch (IllegalArgumentException ignored) {}
            screenReceiverRegistered = false;
        }
        if (scope != null) scope.p.unregisterOnSharedPreferenceChangeListener(this);
        main.removeCallbacksAndMessages(null);
        recorder.removeListener(this);
        if (catalog != null) catalog.removeListener(this);
        if (recordings != null) recordings.close();
        if (hitLog != null) hitLog.close();
        session.reset();
        super.onDestroy();
    }
}
