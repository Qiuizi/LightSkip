package app.qi.qingtiao;

import java.util.ArrayList;
import java.util.List;

/** 录制时采到的一个候选节点（纯数据）。只记文字、控件 ID、位置，不截屏。 */
final class RecordedNode {
    String viewId = "";
    String text = "";
    String desc = "";
    String className = "";
    String activity = "";
    boolean clickable;
    boolean scrollableAncestor;
    int[] bounds = new int[4];
    int screenW;
    int screenH;
    /** 由 RecordingBuilder 用 SkipScorer 打的分。 */
    int score;
    List<String> details = new ArrayList<String>();

    boolean hasId() { return viewId != null && !viewId.isEmpty(); }

    int width() { return bounds[2] - bounds[0]; }
    int height() { return bounds[3] - bounds[1]; }

    /** 中心点在屏幕上的百分比位置 [x, y]，0–100；屏幕尺寸未知时为 -1。 */
    int[] centerPercent() {
        if (screenW <= 0 || screenH <= 0) return new int[]{-1, -1};
        int cx = (bounds[0] + bounds[2]) / 2;
        int cy = (bounds[1] + bounds[3]) / 2;
        return new int[]{Math.round(cx * 100f / screenW), Math.round(cy * 100f / screenH)};
    }

    /** 给界面用的一句话描述，例如「跳过 3」/「#tv_skip」/「(无文字)」。 */
    String displayText() {
        if (text != null && !text.isEmpty()) return text;
        if (desc != null && !desc.isEmpty()) return desc;
        if (hasId()) return "#" + viewId;
        return "(无文字)";
    }

    /** 去重键：同一 ID、文字、描述、位置只算一个。 */
    String key() {
        return viewId + "|" + text + "|" + desc + "|" + bounds[0] + "," + bounds[1] + ","
            + bounds[2] + "," + bounds[3];
    }
}
