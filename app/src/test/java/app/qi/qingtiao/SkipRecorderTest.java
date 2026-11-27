package app.qi.qingtiao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SkipRecorderTest {
    private static final String PKG = RecordingTest.PKG;
    private SkipRecorder rec;
    private final List<SkipRecorder.State> seen = new ArrayList<SkipRecorder.State>();

    @Before public void setUp() {
        RuleBook.loadCustomJson("");
        rec = new SkipRecorder();
        rec.addListener(new SkipRecorder.Listener() {
            @Override public void onRecorderChanged(SkipRecorder.State state) { seen.add(state); }
        });
    }

    private static List<RecordedNode> skipNode() {
        return Collections.singletonList(RecordingTest.node("tv_skip", "跳过 3", new int[]{941, 220, 1122, 273}));
    }

    @Test public void refusesProtectedSelfAndBlank() {
        assertFalse(rec.arm("com.tencent.mm", 0));
        assertFalse(rec.arm(RuleBook.SELF, 0));
        assertFalse(rec.arm("", 0));
        assertEquals(SkipRecorder.State.IDLE, rec.state(0));
        assertNull(rec.listenTarget(0));
    }

    @Test public void fullFlowArmCaptureFiveSecondsThenDone() {
        assertTrue(rec.arm(PKG, 1000));
        assertEquals(SkipRecorder.State.ARMED, rec.state(1000));
        assertEquals(PKG, rec.listenTarget(1000));
        assertTrue(rec.isRecording(PKG, 1000));
        assertFalse("别的包不触发", rec.onWindowStateChanged("com.other", "", 2000));
        assertTrue(rec.onWindowStateChanged(PKG, PKG + ".SplashActivity", 3000));
        assertEquals(SkipRecorder.State.CAPTURING, rec.state(3000));
        assertTrue(rec.snapshotDue(PKG, 3000));
        rec.addSnapshot(PKG, skipNode(), true, 3000);
        assertFalse("500ms 内不重复抓", rec.snapshotDue(PKG, 3300));
        assertTrue(rec.snapshotDue(PKG, 3500));
        assertNull(rec.finishIfDue(7999));
        Recording r = rec.finishIfDue(8000);
        assertNotNull(r);
        assertNull("只结束一次", rec.finishIfDue(8100));
        assertEquals(SkipRecorder.State.DONE, rec.state(8100));
        assertNull("录完立刻停止额外监听", rec.listenTarget(8100));
        assertEquals(PKG + ".SplashActivity", r.activity);
        assertEquals("tv_skip", r.candidates.get(0).viewId);
        assertEquals(r, rec.lastRecording());
        assertEquals(java.util.Arrays.asList(SkipRecorder.State.ARMED, SkipRecorder.State.CAPTURING,
            SkipRecorder.State.DONE), seen);
    }

    @Test public void stateQueryAlsoFinishesExpiredCapture() {
        rec.arm(PKG, 0);
        rec.onWindowStateChanged(PKG, "", 100);
        rec.addSnapshot(PKG, skipNode(), false, 100);
        assertEquals(SkipRecorder.State.DONE, rec.state(5200));
        assertNotNull(rec.lastRecording());
    }

    @Test public void armedTimesOutAfterSixtySeconds() {
        rec.arm(PKG, 0);
        assertEquals(60000L, rec.remainingMs(0));
        assertEquals(SkipRecorder.State.ARMED, rec.state(60000));
        assertEquals(SkipRecorder.State.TIMED_OUT, rec.state(60001));
        assertNull(rec.listenTarget(60001));
        assertFalse(rec.onWindowStateChanged(PKG, "", 60002));
    }

    @Test public void cancelDropsCaptureAndStopsListening() {
        rec.arm(PKG, 0);
        rec.onWindowStateChanged(PKG, "", 10);
        rec.addSnapshot(PKG, skipNode(), false, 10);
        rec.cancel();
        assertEquals(SkipRecorder.State.CANCELLED, rec.state(20));
        assertNull(rec.listenTarget(20));
        assertNull(rec.finishIfDue(6000));
        assertNull(rec.lastRecording());
    }

    @Test public void snapshotsForOtherPackagesAreIgnored() {
        rec.arm(PKG, 0);
        rec.onWindowStateChanged(PKG, "", 0);
        rec.addSnapshot("com.other", skipNode(), true, 0);
        Recording r = rec.finishIfDue(5000);
        assertEquals(0, r.candidates.size());
        assertFalse(r.adMarker);
    }

    @Test public void buildRuleMakesVerifiedRuleWithActivity() {
        Recording r = recordingWith(RecordingTest.node("tv_skip", "跳过", new int[]{941, 220, 1122, 273}));
        SkipRecorder.RuleResult res = SkipRecorder.buildRule(r, 0, "演示");
        assertEquals(SkipRecorder.SaveCode.OK, res.code);
        assertEquals(PKG, res.entry.pkg);
        assertEquals("演示", res.entry.title);
        assertTrue(res.entry.verified);
        assertTrue(res.entry.ids.contains("tv_skip"));
        assertEquals(PKG + ".SplashActivity", res.entry.activity);
    }

    @Test public void buildRuleWithoutViewIdReturnsNoId() {
        Recording r = recordingWith(RecordingTest.node("", "跳过", new int[]{941, 220, 1122, 273}));
        SkipRecorder.RuleResult res = SkipRecorder.buildRule(r, 0, "演示");
        assertEquals(SkipRecorder.SaveCode.NO_ID, res.code);
        assertNull(res.entry);
    }

    @Test public void buildRuleRejectsBadIndexMissingRecordingAndProtected() {
        Recording r = recordingWith(RecordingTest.node("tv_skip", "跳过", new int[]{1, 1, 50, 50}));
        assertEquals(SkipRecorder.SaveCode.BAD_INDEX, SkipRecorder.buildRule(r, 3, null).code);
        assertEquals(SkipRecorder.SaveCode.NO_RECORDING, SkipRecorder.buildRule(null, 0, null).code);
        r.pkg = "com.tencent.mm";
        assertEquals(SkipRecorder.SaveCode.PROTECTED, SkipRecorder.buildRule(r, 0, null).code);
    }

    @Test public void savedRecordedRuleScoresVerifiedIdOnSplash() {
        Recording r = recordingWith(RecordingTest.node("iv_jump", "", new int[]{941, 220, 1122, 273}));
        SkipRecorder.RuleResult res = SkipRecorder.buildRule(r, 0, "演示");
        RuleBook.loadCustomJson(CustomRules.upsert("", res.entry));
        RuleBook.Entry e = RuleBook.find(PKG);
        NodeFeatures f = new NodeFeatures();
        f.viewId = "iv_jump";
        f.ruleIdHit = e.ids.contains("iv_jump");
        f.ruleVerified = f.ruleIdHit && e.verified;
        f.isDedicatedActivity = RuleBook.dedicated(e, PKG + ".SplashActivity");
        f.boundsInScreen = new int[]{941, 220, 1122, 273};
        f.screenW = 1200;
        f.screenH = 2670;
        SkipScorer.Score s = SkipScorer.score(f);
        assertTrue(s.details.contains("verified_id:+50"));
        assertTrue(s.details.contains("dedicated:+25"));
        assertTrue(s.total >= SkipScorer.CLICK_THRESHOLD);
    }

    private static Recording recordingWith(RecordedNode n) {
        Recording r = new Recording();
        r.pkg = PKG;
        r.activity = PKG + ".SplashActivity";
        r.candidates.add(n);
        return r;
    }
}
