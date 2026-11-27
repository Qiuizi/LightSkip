package app.qi.qingtiao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class HitLogTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static HitLog.Entry entry(String pkg, int score, String result) {
        HitLog.Entry e = new HitLog.Entry();
        e.ts = 1000L;
        e.pkg = pkg;
        e.activity = "com.example.SplashActivity";
        e.nodeId = "btn_skip";
        e.nodeText = "跳过";
        e.bounds = new int[]{10, 20, 110, 60};
        e.score = score;
        e.details = Arrays.asList("text_skip:+40", "dedicated:+25");
        e.clickMethod = "node";
        e.result = result;
        return e;
    }

    @Test public void lineContainsAllFieldsAsJson() {
        String line = HitLog.line(entry("com.example", 80, "clicked"));
        assertTrue(line.startsWith("{\"ts\":1000,"));
        assertTrue(line.contains("\"pkg\":\"com.example\""));
        assertTrue(line.contains("\"score\":80"));
        assertTrue(line.contains("\"details\":[\"text_skip:+40\",\"dedicated:+25\"]"));
        assertTrue(line.contains("\"clickMethod\":\"node\""));
        assertTrue(line.contains("\"result\":\"clicked\""));
        assertTrue(line.contains("\"bounds\":[10,20,110,60]"));
    }

    @Test public void logRecentExportAndClear() throws Exception {
        File dir = tmp.newFolder();
        HitLog log = new HitLog(dir);
        log.log(entry("a", 80, "clicked"));
        log.log(entry("b", 45, "near_miss"));
        log.flush();
        List<String> recent = log.recent(1);
        assertEquals(1, recent.size());
        assertTrue(recent.get(0).contains("\"pkg\":\"b\""));
        String exported = log.exportText();
        assertTrue(exported.contains("\"pkg\":\"a\""));
        assertTrue(exported.contains("\"pkg\":\"b\""));
        log.clear();
        log.flush();
        assertEquals(0, log.recent(10).size());
        log.close();
    }

    @Test public void keepsOnlyMostRecentFiveHundredEntries() throws Exception {
        File dir = tmp.newFolder();
        HitLog log = new HitLog(dir);
        for (int i = 0; i < 505; i++) log.log(entry("p" + i, 80, "clicked"));
        log.flush();
        List<String> all = log.recent(1000);
        assertEquals(500, all.size());
        assertTrue(all.get(0).contains("\"pkg\":\"p5\""));
        assertTrue(all.get(499).contains("\"pkg\":\"p504\""));
        log.close();
    }
}
