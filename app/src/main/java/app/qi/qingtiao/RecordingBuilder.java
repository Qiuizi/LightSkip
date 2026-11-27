package app.qi.qingtiao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把多次快照里的节点去重、过滤、打分，产出 Recording（纯逻辑）。
 */
final class RecordingBuilder {
    /** 单次录制最多保留的去重节点数（防内存膨胀）。 */
    static final int MAX_UNIQUE = 400;
    /** 交给界面的候选上限。 */
    static final int MAX_CANDIDATES = 60;
    /** 文字超过这个长度基本是正文/标题，不是按钮。 */
    static final int MAX_TEXT = 20;

    private final String pkg;
    private final long createdAt;
    private final Map<String, RecordedNode> unique = new LinkedHashMap<String, RecordedNode>();
    private String activity = "";
    private boolean adMarker;
    private int snapshots;

    RecordingBuilder(String pkg, long createdAt) {
        this.pkg = pkg;
        this.createdAt = createdAt;
    }

    void noteActivity(String cls) {
        if (cls != null && !cls.isEmpty()) activity = cls;
    }

    /** 加入一次快照。返回本次新增的去重节点数。 */
    int addSnapshot(List<RecordedNode> nodes, boolean markerInWindow) {
        snapshots++;
        adMarker |= markerInWindow;
        int added = 0;
        if (nodes == null) return 0;
        for (RecordedNode n : nodes) {
            if (!useful(n)) continue;
            String k = n.key();
            if (unique.containsKey(k)) continue;
            if (unique.size() >= MAX_UNIQUE) break;
            unique.put(k, n);
            added++;
        }
        return added;
    }

    /** 明显不可能是跳过按钮的节点：零尺寸、什么标识都没有、占屏过大、长段文字。 */
    static boolean useful(RecordedNode n) {
        if (n == null || n.bounds == null || n.bounds.length < 4) return false;
        if (n.width() <= 0 || n.height() <= 0) return false;
        boolean hasText = n.text != null && !n.text.trim().isEmpty();
        boolean hasDesc = n.desc != null && !n.desc.trim().isEmpty();
        if (!hasText && !hasDesc && !n.hasId()) return false;
        if (hasText && n.text.trim().length() > MAX_TEXT) return false;
        if (n.screenW > 0 && n.screenH > 0) {
            long area = (long) n.width() * n.height();
            long screen = (long) n.screenW * n.screenH;
            if (area * 100 > screen * 40) return false;
        }
        return true;
    }

    int uniqueCount() { return unique.size(); }

    Recording build() {
        RuleBook.Entry rule = RuleBook.find(pkg);
        boolean dedicated = RuleBook.dedicated(rule, activity);
        List<RecordedNode> list = new ArrayList<RecordedNode>(unique.values());
        for (RecordedNode n : list) {
            NodeFeatures f = new NodeFeatures();
            f.text = n.text;
            f.desc = n.desc;
            f.viewId = n.viewId;
            f.className = n.className;
            f.ruleIdHit = n.hasId() && rule.ids.contains(n.viewId);
            f.ruleVerified = f.ruleIdHit && rule.verified;
            f.boundsInScreen = n.bounds;
            f.screenW = n.screenW;
            f.screenH = n.screenH;
            f.windowAgeMs = 0;
            f.activityClass = n.activity;
            f.isDedicatedActivity = dedicated || RuleBook.dedicated(rule, n.activity);
            f.windowHasAdMarker = adMarker;
            f.hasScrollableAncestor = n.scrollableAncestor;
            SkipScorer.Score s = SkipScorer.score(f);
            n.score = s.total;
            n.details = new ArrayList<String>(s.details);
        }
        Collections.sort(list, new Comparator<RecordedNode>() {
            @Override public int compare(RecordedNode a, RecordedNode b) {
                if (a.score != b.score) return b.score - a.score;
                if (a.hasId() != b.hasId()) return a.hasId() ? -1 : 1;
                if (a.clickable != b.clickable) return a.clickable ? -1 : 1;
                return 0;
            }
        });
        Recording r = new Recording();
        r.pkg = pkg;
        r.activity = activity;
        r.createdAt = createdAt;
        r.snapshots = snapshots;
        r.adMarker = adMarker;
        r.candidates = new ArrayList<RecordedNode>(
            list.subList(0, Math.min(MAX_CANDIDATES, list.size())));
        return r;
    }
}
