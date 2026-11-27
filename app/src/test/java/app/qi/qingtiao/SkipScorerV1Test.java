package app.qi.qingtiao;

import org.junit.Test;
import static org.junit.Assert.*;

public class SkipScorerV1Test {

    @Test
    public void excludedVideoSkipPhrasesNeverScore() {
        String[] bad = {
            "跳过片头", "跳过片尾", "跳过片头曲", "跳过剧情",
            "跳过动画", "跳过引导", "跳过此步", "跳过绑定",
            "跳过登录", "跳过验证", "Skip Intro", "Skip Outro", "Skip Recap"
        };
        for (String s : bad) {
            assertTrue("should be excluded: " + s, RuleBook.isExcludedSkipText(s));
            assertFalse("skipText must reject: " + s, RuleBook.skipText(s));

            NodeFeatures f = new NodeFeatures();
            f.text = s;
            f.viewId = "btn_skip_intro";
            f.boundsInScreen = new int[]{800, 50, 1050, 140};
            f.screenW = 1080;
            f.screenH = 2400;
            f.windowAgeMs = 500L;
            f.isDedicatedActivity = true;
            f.windowHasAdMarker = true;
            SkipScorer.Score score = SkipScorer.score(f);
            assertEquals("excluded phrase must score 0: " + s, 0, score.total);
        }
    }

    @Test
    public void realSplashSkipPhrasesStillPass() {
        String[] good = {"跳过", "跳过 3s", "3s 跳过", "跳过广告", "Skip", "Skip 5s"};
        for (String s : good) {
            assertFalse("should not be excluded: " + s, RuleBook.isExcludedSkipText(s));
            assertTrue("skipText must accept: " + s, RuleBook.skipText(s));
        }
    }
}
