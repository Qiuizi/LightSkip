package app.qi.qingtiao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 一次 5 秒录制的结果：候选节点按分数从高到低排好。 */
final class Recording {
    String pkg = "";
    /** 采集期间最后一次确认的真实 Activity（可能为空）。 */
    String activity = "";
    /** 墙钟时间，毫秒；也用作文件名。 */
    long createdAt;
    int snapshots;
    boolean adMarker;
    List<RecordedNode> candidates = new ArrayList<RecordedNode>();

    String toJson() {
        StringBuilder b = new StringBuilder();
        b.append("{\"pkg\":").append(Json.quote(pkg));
        b.append(",\"activity\":").append(Json.quote(nz(activity)));
        b.append(",\"createdAt\":").append(createdAt);
        b.append(",\"snapshots\":").append(snapshots);
        b.append(",\"adMarker\":").append(adMarker);
        b.append(",\"candidates\":[");
        for (int i = 0; i < candidates.size(); i++) {
            RecordedNode n = candidates.get(i);
            if (i > 0) b.append(',');
            b.append("{\"viewId\":").append(Json.quote(nz(n.viewId)));
            b.append(",\"text\":").append(Json.quote(nz(n.text)));
            b.append(",\"desc\":").append(Json.quote(nz(n.desc)));
            b.append(",\"className\":").append(Json.quote(nz(n.className)));
            b.append(",\"activity\":").append(Json.quote(nz(n.activity)));
            b.append(",\"clickable\":").append(n.clickable);
            b.append(",\"scrollableAncestor\":").append(n.scrollableAncestor);
            b.append(",\"bounds\":[").append(n.bounds[0]).append(',').append(n.bounds[1])
                .append(',').append(n.bounds[2]).append(',').append(n.bounds[3]).append(']');
            b.append(",\"screen\":[").append(n.screenW).append(',').append(n.screenH).append(']');
            b.append(",\"score\":").append(n.score);
            b.append(",\"details\":[");
            for (int j = 0; j < n.details.size(); j++) {
                if (j > 0) b.append(',');
                b.append(Json.quote(n.details.get(j)));
            }
            b.append("]}");
        }
        return b.append("]}").toString();
    }

    /** 解析录制文件；格式错误返回 null。 */
    static Recording fromJson(String json) {
        try {
            Object root = Json.parse(json);
            if (!(root instanceof Map)) return null;
            Map<?, ?> m = (Map<?, ?>) root;
            Recording r = new Recording();
            r.pkg = str(m.get("pkg"));
            if (r.pkg.isEmpty()) return null;
            r.activity = str(m.get("activity"));
            r.createdAt = num(m.get("createdAt"));
            r.snapshots = (int) num(m.get("snapshots"));
            r.adMarker = Boolean.TRUE.equals(m.get("adMarker"));
            Object list = m.get("candidates");
            if (list instanceof List) {
                for (Object o : (List<?>) list) {
                    if (!(o instanceof Map)) continue;
                    Map<?, ?> c = (Map<?, ?>) o;
                    RecordedNode n = new RecordedNode();
                    n.viewId = str(c.get("viewId"));
                    n.text = str(c.get("text"));
                    n.desc = str(c.get("desc"));
                    n.className = str(c.get("className"));
                    n.activity = str(c.get("activity"));
                    n.clickable = Boolean.TRUE.equals(c.get("clickable"));
                    n.scrollableAncestor = Boolean.TRUE.equals(c.get("scrollableAncestor"));
                    n.bounds = ints(c.get("bounds"), 4);
                    int[] screen = ints(c.get("screen"), 2);
                    n.screenW = screen[0];
                    n.screenH = screen[1];
                    n.score = (int) num(c.get("score"));
                    if (c.get("details") instanceof List) {
                        for (Object d : (List<?>) c.get("details")) n.details.add(str(d));
                    }
                    r.candidates.add(n);
                }
            }
            return r;
        } catch (RuntimeException broken) {
            return null;
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String str(Object o) { return o instanceof String ? (String) o : ""; }
    private static long num(Object o) { return o instanceof Number ? ((Number) o).longValue() : 0L; }

    private static int[] ints(Object o, int n) {
        int[] out = new int[n];
        if (o instanceof List) {
            List<?> l = (List<?>) o;
            for (int i = 0; i < n && i < l.size(); i++) out[i] = (int) num(l.get(i));
        }
        return out;
    }
}
