package app.qi.qingtiao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HitLogSessionTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static List<String> read(File dir) throws Exception {
        return Files.readAllLines(new File(dir, "hits.jsonl").toPath(), StandardCharsets.UTF_8);
    }

    @Test public void appendDoesNotLoseEarlierLines() throws Exception {
        File dir = tmp.newFolder();
        HitLog log = new HitLog(dir);
        HitLog.Entry e1 = new HitLog.Entry();
        e1.ts = 1L; e1.pkg = "a"; e1.result = "session_open";
        e1.rawEventClass = "android.view.View";
        log.log(e1);
        log.flush();
        HitLog.Entry e2 = new HitLog.Entry();
        e2.ts = 2L; e2.pkg = "b"; e2.result = "no_candidate";
        e2.details = Arrays.asList("visited:5", "bestScore:none");
        log.log(e2);
        log.flush();
        log.close();
        List<String> lines = read(dir);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("session_open"));
        assertTrue(lines.get(1).contains("no_candidate"));
    }

    @Test public void appendsAfterLinesWrittenByAnEarlierProcess() throws Exception {
        File dir = tmp.newFolder();
        List<String> old = new ArrayList<String>();
        for (int i = 0; i < 3; i++) old.add("{\"ts\":" + i + ",\"pkg\":\"old" + i + "\"}");
        Files.write(new File(dir, "hits.jsonl").toPath(), old, StandardCharsets.UTF_8);
        HitLog log = new HitLog(dir);
        HitLog.Entry e = new HitLog.Entry();
        e.ts = 9L; e.pkg = "new"; e.result = "session_open";
        log.log(e);
        log.flush();
        log.close();
        List<String> lines = read(dir);
        assertEquals(4, lines.size());
        assertTrue(lines.get(0).contains("old0"));
        assertTrue(lines.get(3).contains("\"pkg\":\"new\""));
    }

    @Test public void oversizedFileIsCompactedToNewestFiveHundred() throws Exception {
        File dir = tmp.newFolder();
        List<String> old = new ArrayList<String>();
        for (int i = 0; i < 600; i++) old.add("{\"ts\":" + i + ",\"pkg\":\"old" + i + "\"}");
        Files.write(new File(dir, "hits.jsonl").toPath(), old, StandardCharsets.UTF_8);
        HitLog log = new HitLog(dir);
        HitLog.Entry e = new HitLog.Entry();
        e.ts = 999L; e.pkg = "newest"; e.result = "near_miss";
        log.log(e);
        log.flush();
        List<String> lines = read(dir);
        assertEquals(HitLog.MAX_LINES, lines.size());
        assertTrue(lines.get(0).contains("\"pkg\":\"old101\""));
        assertTrue(lines.get(499).contains("\"pkg\":\"newest\""));
        log.log(e);
        log.flush();
        log.close();
        assertEquals(HitLog.MAX_LINES, read(dir).size());
    }

    @Test public void clearThenLogStartsFromEmptyFile() throws Exception {
        File dir = tmp.newFolder();
        HitLog log = new HitLog(dir);
        for (int i = 0; i < 3; i++) {
            HitLog.Entry e = new HitLog.Entry();
            e.pkg = "p" + i;
            log.log(e);
        }
        log.flush();
        log.clear();
        HitLog.Entry e = new HitLog.Entry();
        e.pkg = "after";
        log.log(e);
        log.flush();
        log.close();
        List<String> lines = read(dir);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"pkg\":\"after\""));
    }

    @Test public void sessionOpenAndNoCandidateFieldsSerialize() {
        HitLog.Entry e = new HitLog.Entry();
        e.ts = 100L;
        e.pkg = "com.taobao.taobao";
        e.activity = "com.taobao.taobao.SplashActivity";
        e.rawEventClass = "android.view.View";
        e.result = "session_open";
        String line = HitLog.line(e);
        assertTrue(line.contains("\"result\":\"session_open\""));
        assertTrue(line.contains("\"activity\":\"com.taobao.taobao.SplashActivity\""));
        assertTrue(line.contains("\"rawEventClass\":\"android.view.View\""));
        assertTrue(line.contains("\"pkg\":\"com.taobao.taobao\""));
    }

    @Test public void clickedLineCarriesRawEventClassToo() {
        HitLog.Entry e = new HitLog.Entry();
        e.pkg = "com.taobao.taobao";
        e.rawEventClass = "android.widget.FrameLayout";
        e.result = "clicked";
        assertTrue(HitLog.line(e).contains("\"rawEventClass\":\"android.widget.FrameLayout\""));
    }

    @Test public void noCandidateIncludesVisitedAndBestScore() {
        HitLog.Entry e = new HitLog.Entry();
        e.ts = 100L;
        e.pkg = "com.taobao.taobao";
        e.result = "no_candidate";
        e.details = Arrays.asList("visited:120", "bestScore:45");
        String line = HitLog.line(e);
        assertTrue(line.contains("visited:120"));
        assertTrue(line.contains("bestScore:45"));
    }
}
