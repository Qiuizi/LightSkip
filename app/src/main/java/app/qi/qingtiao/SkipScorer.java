package app.qi.qingtiao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class SkipScorer {
    static final int CLICK_THRESHOLD = 60;
    static final int NEAR_MISS_MIN = 40;
    static final long EXPIRED_MS = 4500L;
    static final int TEXT_SKIP_POINTS = 40;
    static final int COUNTDOWN_POINTS = 15;
    static final int VERIFIED_ID_POINTS = 50;
    static final int UNVERIFIED_ID_POINTS = 20;
    static final int ID_HINT_POINTS = 10;
    static final int DEDICATED_POINTS = 25;
    static final int AD_MARKER_POINTS = 15;
    static final int CORNER_POINTS = 10;
    static final int CLOSE_TEXT_POINTS = -15;
    static final int CLOSE_WORD_POINTS = -15;
    static final int SCROLLABLE_POINTS = -40;
    private static final Pattern COUNTDOWN = Pattern.compile("\\d{1,2}\\s*(?:s|秒)?");
    static final Pattern ID_HINT = Pattern.compile(".*(skip|jump|countdown).*");

    static final class Score {
        final int total;
        final List<String> details;
        Score(int total, List<String> details) {
            this.total = total;
            this.details = details;
        }
    }

    private SkipScorer() {}

    static Score score(NodeFeatures f) {
        if (f.windowAgeMs > EXPIRED_MS) return new Score(0, Arrays.asList("expired_window:0"));
        int total = 0;
        List<String> d = new ArrayList<String>();
        String text = nz(f.text).trim();
        String desc = nz(f.desc).trim();
        String combined = (text + " " + desc).toLowerCase(Locale.ROOT);
        boolean skipHit = RuleBook.skipText(text) || RuleBook.skipText(desc);
        if (skipHit) {
            total += TEXT_SKIP_POINTS;
            d.add("text_skip:+" + TEXT_SKIP_POINTS);
            boolean skipWord = combined.contains("跳过") || combined.contains("跳過")
                || combined.contains("略過") || combined.contains("skip");
            if (!skipWord) {
                total += CLOSE_WORD_POINTS;
                d.add("close_word:" + CLOSE_WORD_POINTS);
            }
        }
        if (COUNTDOWN.matcher(combined).find()) {
            total += COUNTDOWN_POINTS;
            d.add("countdown:+" + COUNTDOWN_POINTS);
        }
        if (f.ruleIdHit && f.ruleVerified) {
            total += VERIFIED_ID_POINTS;
            d.add("verified_id:+" + VERIFIED_ID_POINTS);
        } else if (f.ruleIdHit) {
            total += UNVERIFIED_ID_POINTS;
            d.add("unverified_id:+" + UNVERIFIED_ID_POINTS);
        }
        if (ID_HINT.matcher(nz(f.viewId).toLowerCase(Locale.ROOT)).matches()) {
            total += ID_HINT_POINTS;
            d.add("id_hint:+" + ID_HINT_POINTS);
        }
        if (f.isDedicatedActivity) {
            total += DEDICATED_POINTS;
            d.add("dedicated:+" + DEDICATED_POINTS);
        }
        if (f.windowHasAdMarker) {
            total += AD_MARKER_POINTS;
            d.add("ad_marker:+" + AD_MARKER_POINTS);
        }
        if (corner(f)) {
            total += CORNER_POINTS;
            d.add("corner:+" + CORNER_POINTS);
        }
        boolean close = !skipHit && (combined.contains("关闭") || combined.contains("關閉")
            || combined.contains("close"));
        if (close) {
            total += CLOSE_TEXT_POINTS;
            d.add("close_text:" + CLOSE_TEXT_POINTS);
        }
        if (f.hasScrollableAncestor) {
            total += SCROLLABLE_POINTS;
            d.add("scrollable_ancestor:" + SCROLLABLE_POINTS);
        }
        return new Score(total, d);
    }

    private static boolean corner(NodeFeatures f) {
        if (f.screenW <= 0 || f.screenH <= 0) return false;
        int l = f.boundsInScreen[0], t = f.boundsInScreen[1];
        int r = f.boundsInScreen[2], b = f.boundsInScreen[3];
        boolean visible = r > l && b > t;
        boolean small = (r - l) < f.screenW * 0.35 && (b - t) < f.screenH * 0.12;
        boolean nearH = l <= f.screenW * 0.1 || r >= f.screenW * 0.9;
        boolean nearV = t <= f.screenH * 0.1 || b >= f.screenH * 0.9;
        return visible && small && nearH && nearV;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
