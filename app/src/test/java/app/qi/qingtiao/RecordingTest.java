package app.qi.qingtiao;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RecordingTest {
    static final String PKG = "com.demo.app";

    @Before public void clearCustom() { RuleBook.loadCustomJson(""); }

    static RecordedNode node(String id, String text, int[] b) {
        RecordedNode n = new RecordedNode();
        n.viewId = id;
        n.text = text;
        n.bounds = b;
        n.screenW = 1200;
        n.screenH = 2670;
        n.clickable = true;
        n.activity = PKG + ".SplashActivity";
        return n;
    }

    @Test public void jsonRoundTripKeepsEveryField() {
        Recording r = new Recording();
        r.pkg = PKG;
        r.activity = PKG + ".SplashActivity";
        r.createdAt = 1795581627513L;
        r.snapshots = 9;
        r.adMarker = true;
        RecordedNode n = node("tv_skip", "跳过 \"3\"", new int[]{941, 220, 1122, 273});
        n.desc = "广告";
        n.className = "android.widget.TextView";
        n.scrollableAncestor = true;
        n.score = 95;
        n.details = new ArrayList<String>(Arrays.asList("text_skip:+40", "corner:+10"));
        r.candidates.add(n);
        Recording back = Recording.fromJson(r.toJson());
        assertEquals(PKG, back.pkg);
        assertEquals(r.activity, back.activity);
        assertEquals(1795581627513L, back.createdAt);
        assertEquals(9, back.snapshots);
        assertTrue(back.adMarker);
        RecordedNode m = back.candidates.get(0);
        assertEquals("tv_skip", m.viewId);
        assertEquals("跳过 \"3\"", m.text);
        assertEquals("广告", m.desc);
        assertEquals("android.widget.TextView", m.className);
        assertTrue(m.clickable);
        assertTrue(m.scrollableAncestor);
        assertArrayEquals(new int[]{941, 220, 1122, 273}, m.bounds);
        assertEquals(1200, m.screenW);
        assertEquals(2670, m.screenH);
        assertEquals(95, m.score);
        assertEquals(Arrays.asList("text_skip:+40", "corner:+10"), m.details);
    }

    @Test public void brokenJsonReturnsNull() {
        assertNull(Recording.fromJson("{\"pkg\":"));
        assertNull(Recording.fromJson("[]"));
        assertNull(Recording.fromJson("{\"pkg\":\"\"}"));
    }

    @Test public void centerPercentUsesScreenSize() {
        RecordedNode n = node("tv_skip", "跳过", new int[]{941, 220, 1122, 273});
        assertArrayEquals(new int[]{86, 9}, n.centerPercent());
        n.screenW = 0;
        assertArrayEquals(new int[]{-1, -1}, n.centerPercent());
    }

    @Test public void builderDedupesFiltersAndRanksSkipFirst() {
        RecordingBuilder b = new RecordingBuilder(PKG, 100L);
        b.noteActivity(PKG + ".SplashActivity");
        List<RecordedNode> snap = new ArrayList<RecordedNode>();
        snap.add(node("", "精选好物推荐", new int[]{100, 1500, 600, 1600}));
        snap.add(node("tv_skip", "跳过 3", new int[]{941, 220, 1122, 273}));
        snap.add(node("", "", new int[]{0, 0, 10, 10}));
        snap.add(node("bg", "", new int[]{0, 0, 1200, 2670}));
        snap.add(node("x", "很长很长很长很长很长很长很长很长很长很长很长的一段正文", new int[]{0, 400, 1200, 600}));
        snap.add(node("zero", "跳过", new int[]{5, 5, 5, 5}));
        assertEquals(2, b.addSnapshot(snap, true));
        List<RecordedNode> again = new ArrayList<RecordedNode>();
        again.add(node("tv_skip", "跳过 3", new int[]{941, 220, 1122, 273}));
        again.add(node("tv_skip", "跳过 2", new int[]{941, 220, 1122, 273}));
        assertEquals("倒计时变了算新节点，同一个只算一次", 1, b.addSnapshot(again, false));
        Recording r = b.build();
        assertEquals(PKG, r.pkg);
        assertEquals(2, r.snapshots);
        assertTrue(r.adMarker);
        assertEquals(3, r.candidates.size());
        RecordedNode top = r.candidates.get(0);
        assertEquals("tv_skip", top.viewId);
        assertTrue(top.details.contains("text_skip:+40"));
        assertTrue(top.details.contains("dedicated:+25"));
        assertTrue(top.details.contains("ad_marker:+15"));
        assertTrue(top.details.contains("corner:+10"));
        assertEquals("精选好物推荐", r.candidates.get(2).text);
    }

    @Test public void builderCapsUniqueNodes() {
        RecordingBuilder b = new RecordingBuilder(PKG, 1L);
        List<RecordedNode> many = new ArrayList<RecordedNode>();
        for (int i = 0; i < RecordingBuilder.MAX_UNIQUE + 50; i++) {
            many.add(node("id" + i, "t", new int[]{0, i, 100, i + 20}));
        }
        b.addSnapshot(many, false);
        assertEquals(RecordingBuilder.MAX_UNIQUE, b.uniqueCount());
        assertEquals(RecordingBuilder.MAX_CANDIDATES, b.build().candidates.size());
    }
}
