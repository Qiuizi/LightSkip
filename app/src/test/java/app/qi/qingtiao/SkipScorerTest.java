package app.qi.qingtiao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SkipScorerTest {
    private static NodeFeatures base() {
        NodeFeatures f = new NodeFeatures();
        f.screenW = 1080;
        f.screenH = 2400;
        f.windowAgeMs = 500;
        f.activityClass = "com.example.app.SplashActivity";
        return f;
    }

    private static boolean has(SkipScorer.Score s, String item) {
        return s.details.contains(item);
    }

    @Test public void skipWithCountdownOnDedicatedActivityClicks() {
        NodeFeatures f = base();
        f.text = "跳过 3";
        f.isDedicatedActivity = true;
        SkipScorer.Score s = SkipScorer.score(f);
        assertTrue("expected >= threshold but was " + s.total, s.total >= SkipScorer.CLICK_THRESHOLD);
        assertTrue(has(s, "text_skip:+40"));
        assertTrue(has(s, "countdown:+15"));
        assertTrue(has(s, "dedicated:+25"));
    }

    @Test public void unverifiedIdAloneOnNormalPageDoesNotClick() {
        NodeFeatures f = base();
        f.viewId = "tv_close";
        f.ruleIdHit = true;
        f.ruleVerified = false;
        SkipScorer.Score s = SkipScorer.score(f);
        assertTrue(s.total < SkipScorer.CLICK_THRESHOLD);
        assertTrue(has(s, "unverified_id:+20"));
        assertFalse(has(s, "verified_id:+50"));
    }

    @Test public void skipTextInsideScrollableFeedDoesNotClick() {
        NodeFeatures f = base();
        f.text = "跳过";
        f.hasScrollableAncestor = true;
        SkipScorer.Score s = SkipScorer.score(f);
        assertTrue(s.total < SkipScorer.CLICK_THRESHOLD);
        assertTrue(has(s, "text_skip:+40"));
        assertTrue(has(s, "scrollable_ancestor:-40"));
    }

    @Test public void closeTextOnAdSplashClicks() {
        NodeFeatures f = base();
        f.text = "关闭广告";
        f.isDedicatedActivity = true;
        f.windowHasAdMarker = true;
        SkipScorer.Score s = SkipScorer.score(f);
        assertTrue("expected >= threshold but was " + s.total, s.total >= SkipScorer.CLICK_THRESHOLD);
        assertTrue(has(s, "text_skip:+40"));
        assertTrue(has(s, "ad_marker:+15"));
        assertTrue(has(s, "dedicated:+25"));
    }

    @Test public void plainCloseWordingWithoutSkipIsPenalized() {
        NodeFeatures f = base();
        f.text = "关闭并返回";
        f.isDedicatedActivity = true;
        f.windowHasAdMarker = true;
        SkipScorer.Score s = SkipScorer.score(f);
        assertTrue(s.total < SkipScorer.CLICK_THRESHOLD);
        assertFalse(has(s, "text_skip:+40"));
        assertTrue(has(s, "close_text:-15"));
    }

    @Test public void closeOnlyOnDedicatedActivityWithoutMarkerIsNearMiss() {
        NodeFeatures f = base();
        f.text = "关闭";
        f.isDedicatedActivity = true;
        SkipScorer.Score s = SkipScorer.score(f);
        assertTrue(s.total >= SkipScorer.NEAR_MISS_MIN);
        assertTrue(s.total < SkipScorer.CLICK_THRESHOLD);
        assertTrue(has(s, "close_word:-15"));
    }

    @Test public void verifiedIdWithoutTextOnDedicatedActivityClicks() {
        NodeFeatures f = base();
        f.viewId = "btn_skip";
        f.ruleIdHit = true;
        f.ruleVerified = true;
        f.isDedicatedActivity = true;
        SkipScorer.Score s = SkipScorer.score(f);
        assertTrue("expected >= threshold but was " + s.total, s.total >= SkipScorer.CLICK_THRESHOLD);
        assertTrue(has(s, "verified_id:+50"));
        assertTrue(has(s, "dedicated:+25"));
        assertTrue(has(s, "id_hint:+10"));
    }

    @Test public void skipAppearingAfterWindowExpiryDoesNotClick() {
        NodeFeatures f = base();
        f.text = "跳过";
        f.windowAgeMs = 5000;
        SkipScorer.Score s = SkipScorer.score(f);
        assertEquals(0, s.total);
        assertTrue(has(s, "expired_window:0"));
    }

    @Test public void cornerSkipWithoutOtherSignalsIsNearMiss() {
        NodeFeatures f = base();
        f.text = "跳过";
        f.boundsInScreen = new int[]{980, 40, 1070, 220};
        SkipScorer.Score s = SkipScorer.score(f);
        assertTrue(s.total >= SkipScorer.NEAR_MISS_MIN);
        assertTrue(s.total < SkipScorer.CLICK_THRESHOLD);
        assertTrue(has(s, "text_skip:+40"));
        assertTrue(has(s, "corner:+10"));
    }
    @Test public void taobaoTopRightSkipOnMi15PhysicalScreenGetsCorner() {
        NodeFeatures f = base();
        f.text = "跳过广告";
        f.desc = "广告";
        f.viewId = "tv_close";
        f.ruleIdHit = true;
        f.boundsInScreen = new int[]{941, 220, 1122, 273};
        f.screenW = 1200;
        f.screenH = 2670;
        SkipScorer.Score s = SkipScorer.score(f);
        assertTrue("details=" + s.details, has(s, "corner:+10"));
        assertTrue(has(s, "text_skip:+40"));
    }

    @Test public void bottomCenterButtonGetsNoCorner() {
        NodeFeatures f = base();
        f.text = "跳过";
        f.screenW = 1200;
        f.screenH = 2670;
        f.boundsInScreen = new int[]{500, 2400, 700, 2460};
        assertFalse(has(SkipScorer.score(f), "corner:+10"));
    }

    @Test public void unknownScreenSizeGivesNoCorner() {
        NodeFeatures f = base();
        f.text = "跳过";
        f.screenW = 0;
        f.screenH = 0;
        f.boundsInScreen = new int[]{941, 220, 1122, 273};
        assertFalse(has(SkipScorer.score(f), "corner:+10"));
    }
}
