package app.qi.qingtiao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WindowSessionTest {
    private static final String TB = "com.taobao.taobao";
    private static final String TB_SPLASH = "com.taobao.taobao.SplashActivity";

    @Test public void touchDoesNotClearScreenAfterEnter() {
        WindowSession s = new WindowSession();
        s.enter(TB, TB_SPLASH, 5, true, 1000L);
        s.touch(TB, 5, 1100L);
        assertEquals(TB_SPLASH, s.screen);
    }

    @Test public void enterRecordsActivityClassEvenIfNotSplash() {
        WindowSession s = new WindowSession();
        s.enter(TB, "android.view.View", 6, false, 1000L);
        assertEquals("android.view.View", s.rawEventClass);
    }

    @Test public void enterWithNonActivityKeepsLastKnownActivity() {
        WindowSession s = new WindowSession();
        s.enter(TB, TB_SPLASH, 7, true, 1000L);
        s.reset();
        s.enter(TB, "android.view.View", 8, false, 2000L, false);
        assertEquals(TB_SPLASH, s.screen);
        assertEquals("android.view.View", s.rawEventClass);
        assertFalse(s.dedicated);
    }

    @Test public void touchOpeningFreshSessionOnSamePackageKeepsActivity() {
        WindowSession s = new WindowSession();
        s.enter(TB, TB_SPLASH, 5, true, 1000L);
        assertTrue("过期后 touch 应开新会话", s.touch(TB, 5, 1000L + RuleBook.WINDOW_MS + 1));
        assertEquals(TB_SPLASH, s.screen);
    }

    @Test public void lastActivityIsTrackedPerPackage() {
        WindowSession s = new WindowSession();
        s.enter(TB, TB_SPLASH, 5, true, 1000L, true);
        s.enter("tv.danmaku.bili", "android.widget.FrameLayout", 9, false, 1200L, false);
        assertEquals("不能把淘宝的 Activity 串给 B 站", "", s.screen);
        s.enter(TB, "android.app.Dialog", 10, false, 1400L, false);
        assertEquals(TB_SPLASH, s.screen);
    }

    @Test public void touchIntoAnotherPackageUsesThatPackagesActivity() {
        WindowSession s = new WindowSession();
        s.enter("tv.danmaku.bili", "tv.danmaku.bili.MainActivityV2", 3, false, 500L, true);
        s.enter(TB, TB_SPLASH, 5, true, 1000L, true);
        s.touch("tv.danmaku.bili", 3, 1100L);
        assertEquals("tv.danmaku.bili.MainActivityV2", s.screen);
    }

    @Test public void enterReportsWhetherANewSessionOpened() {
        WindowSession s = new WindowSession();
        assertTrue(s.enter(TB, TB_SPLASH, 5, true, 1000L, true));
        assertFalse("同一窗口 4.5s 内不重复开会话", s.enter(TB, "android.view.View", 5, false, 1500L, false));
        assertTrue(s.enter(TB, TB_SPLASH, 6, true, 1600L, true));
    }

    @Test public void expiredSessionWithoutCandidateReportsOnceWithScanStats() {
        WindowSession s = new WindowSession();
        s.enter(TB, "android.view.View", 5, true, 1000L, false);
        s.enter(TB, TB_SPLASH, 5, true, 1100L, true);
        int gen = s.generation;
        s.noteScan(120, WindowSession.NO_SCORE);
        s.noteScan(150, 35);
        s.noteScan(90, 20);
        WindowSession.Report r = s.expire(gen);
        assertNotNull(r);
        assertEquals(TB, r.pkg);
        assertEquals(TB_SPLASH, r.activity);
        assertEquals(TB_SPLASH, r.rawEventClass);
        assertEquals(150, r.visited);
        assertEquals(35, r.bestScore);
        assertNull("同一窗口只报一次", s.expire(gen));
    }

    @Test public void sessionThatProducedCandidateDoesNotReport() {
        WindowSession s = new WindowSession();
        s.enter(TB, TB_SPLASH, 5, true, 1000L, true);
        s.noteScan(80, 45);
        s.markProduced();
        assertNull(s.expire(s.generation));
    }

    @Test public void clickedSessionDoesNotReport() {
        WindowSession s = new WindowSession();
        s.enter(TB, TB_SPLASH, 5, true, 1000L, true);
        s.used = true;
        assertNull(s.expire(s.generation));
    }

    @Test public void replacedSessionHandsOverClosedReportAndStaleTimerIsIgnored() {
        WindowSession s = new WindowSession();
        s.enter(TB, TB_SPLASH, 5, true, 1000L, true);
        int oldGen = s.generation;
        s.noteScan(60, WindowSession.NO_SCORE);
        s.enter(TB, "com.taobao.tao.welcome.Welcome", 6, false, 2000L, true);
        WindowSession.Report closed = s.takeClosed();
        assertNotNull(closed);
        assertEquals(60, closed.visited);
        assertEquals(WindowSession.NO_SCORE, closed.bestScore);
        assertNull("只交接一次", s.takeClosed());
        assertNull("旧窗口的定时器不能再报", s.expire(oldGen));
    }

    @Test public void contentOnlySessionIsNotDiagnosed() {
        WindowSession s = new WindowSession();
        assertTrue(s.touch(TB, 5, 1000L));
        assertNull(s.expire(s.generation));
        s.enter(TB, TB_SPLASH, 6, true, 1200L, true);
        assertNull("touch 开的会话被替换时也不报", s.takeClosed());
    }

    @Test public void nearMissForSameCandidateIsLoggedOncePerSession() {
        WindowSession s = new WindowSession();
        s.enter(TB, TB_SPLASH, 5, true, 1000L, true);
        int[] b = {941, 220, 1122, 273};
        assertTrue(s.firstNearMiss("tv_close", "关闭", b, 50));
        for (int i = 0; i < 30; i++) assertFalse(s.firstNearMiss("tv_close", "关闭", b, 50));
        assertTrue("分数变了算新记录", s.firstNearMiss("tv_close", "关闭", b, 55));
        assertTrue("新会话重新计", s.enter(TB, TB_SPLASH, 6, true, 2000L, true));
        assertTrue(s.firstNearMiss("tv_close", "关闭", b, 50));
    }

    @Test public void knownActivityReturnsPerPackageValue() {
        WindowSession s = new WindowSession();
        s.enter(TB, TB_SPLASH, 5, true, 1000L, true);
        assertEquals(TB_SPLASH, s.knownActivity(TB));
        assertEquals("", s.knownActivity("tv.danmaku.bili"));
    }
}
