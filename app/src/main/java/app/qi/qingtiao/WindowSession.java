package app.qi.qingtiao;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * 一个「扫描窗口」的状态。只在主线程（无障碍回调线程）使用，不做任何 IO。
 */
final class WindowSession {
    static final int NO_SCORE = Integer.MIN_VALUE;
    private static final int MAX_TRACKED_PACKAGES = 256;

    /** 会话结束时既没点击也没产出候选的诊断快照。 */
    static final class Report {
        String pkg = "", activity = "", rawEventClass = "";
        int visited;
        int bestScore = NO_SCORE;
    }

    String pkg = "";
    /** 当前会话所属包最近一次确认过的真实 Activity；未知时为空串。 */
    String screen = "";
    /** 最近一次 TYPE_WINDOW_STATE_CHANGED 的原始 className。 */
    String rawEventClass = "";
    int window = -1;
    long opened;
    long lastContent;
    boolean used;
    boolean dedicated;
    int attempts;
    /** 每开一个新会话加一，供到期定时器核对是不是同一个会话。 */
    int generation;

    private boolean diagnose;
    private boolean produced;
    private boolean reported;
    private int visited;
    private int bestScore = NO_SCORE;
    private Report closed;
    private final Map<String, String> lastActivity = new HashMap<String, String>();
    private final HashSet<String> nearMisses = new HashSet<String>();

    boolean enter(String pkgName, String cls, int windowId, boolean isDedicated, long now) {
        return enter(pkgName, cls, windowId, isDedicated, now, true);
    }

    /** @return true 表示开了一个新会话。 */
    boolean enter(String pkgName, String cls, int windowId, boolean isDedicated, long now, boolean isActivity) {
        String activity = resolveActivity(pkgName, cls, isActivity);
        if (window == windowId && pkg.equals(pkgName) && now - opened <= RuleBook.WINDOW_MS) {
            rawEventClass = nz(cls);
            screen = activity;
            dedicated |= isDedicated;
            return false;
        }
        open(pkgName, windowId, now, true);
        rawEventClass = nz(cls);
        screen = activity;
        dedicated = isDedicated;
        return true;
    }

    /** 内容变化事件。@return true 表示开了一个新会话（这种会话不做诊断）。 */
    boolean touch(String pkgName, int windowId, long now) {
        if (pkg.equals(pkgName) && window == windowId && now - opened <= RuleBook.WINDOW_MS) {
            return false;
        }
        boolean samePackage = pkg.equals(pkgName);
        open(pkgName, windowId, now, false);
        if (!samePackage) {
            String known = lastActivity.get(pkgName);
            screen = known == null ? "" : known;
            rawEventClass = "";
        }
        dedicated = false;
        return true;
    }

    /**
     * 记下 pkg 的最近一个真实 Activity，并返回本次应使用的 Activity。
     * cls 不是 Activity（View / Dialog / PopupWindow）时沿用该包上一次记录的值。
     */
    String resolveActivity(String pkgName, String cls, boolean isActivity) {
        if (isActivity && cls != null && !cls.isEmpty()) {
            if (lastActivity.size() >= MAX_TRACKED_PACKAGES && !lastActivity.containsKey(pkgName)) {
                lastActivity.clear();
            }
            lastActivity.put(pkgName, cls);
            return cls;
        }
        String known = lastActivity.get(pkgName);
        return known == null ? "" : known;
    }

    /** 某包最近一次确认的真实 Activity；未知返回空串。 */
    String knownActivity(String pkgName) {
        String known = lastActivity.get(pkgName);
        return known == null ? "" : known;
    }

    void noteScan(int visitedNodes, int best) {
        if (visitedNodes > visited) visited = visitedNodes;
        if (best > bestScore) bestScore = best;
    }

    /**
     * 同一会话里同一个候选（节点 ID + 文字 + 位置 + 分数）的 near_miss 只记一次。
     * @return true 表示第一次出现，应该写日志。
     */
    boolean firstNearMiss(String nodeId, String text, int[] bounds, int score) {
        StringBuilder k = new StringBuilder();
        k.append(nz(nodeId)).append('|').append(nz(text)).append('|').append(score);
        if (bounds != null) {
            for (int b : bounds) k.append(',').append(b);
        }
        return nearMisses.add(k.toString());
    }

    /** 本会话已产出 clicked / failed / near_miss 记录。 */
    void markProduced() { produced = true; }

    /** 到期定时器调用；只有仍是同一会话且符合条件时才返回一次报告。 */
    Report expire(int gen) {
        if (gen != generation) return null;
        return snapshotIfQuiet();
    }

    /** 取走被新会话或 reset 顶掉的旧会话报告（最多一次）。 */
    Report takeClosed() {
        Report r = closed;
        closed = null;
        return r;
    }

    boolean live(String pkgName, int windowId, long now) {
        return !used
            && attempts < 2
            && windowId >= 0
            && window == windowId
            && pkg.equals(pkgName)
            && now >= opened
            && now - opened <= RuleBook.WINDOW_MS;
    }

    /** 结束当前会话；保留各包的最近 Activity 和当前 screen。 */
    void reset() {
        Report r = snapshotIfQuiet();
        if (r != null) closed = r;
        window = -1;
        pkg = "";
        rawEventClass = "";
        used = false;
        lastContent = 0;
        attempts = 0;
        diagnose = false;
        generation++;
    }

    private void open(String pkgName, int windowId, long now, boolean diagnosed) {
        Report r = snapshotIfQuiet();
        if (r != null) closed = r;
        pkg = pkgName;
        window = windowId;
        opened = now;
        lastContent = 0;
        used = false;
        attempts = 0;
        diagnose = diagnosed;
        produced = false;
        reported = false;
        visited = 0;
        bestScore = NO_SCORE;
        nearMisses.clear();
        generation++;
    }

    private Report snapshotIfQuiet() {
        if (!diagnose || used || produced || reported || window < 0) return null;
        reported = true;
        Report r = new Report();
        r.pkg = pkg;
        r.activity = screen;
        r.rawEventClass = rawEventClass;
        r.visited = visited;
        r.bestScore = bestScore;
        return r;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
