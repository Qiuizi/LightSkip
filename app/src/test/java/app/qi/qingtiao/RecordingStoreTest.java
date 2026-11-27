package app.qi.qingtiao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

public class RecordingStoreTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static Recording rec(String pkg, long ts) {
        Recording r = new Recording();
        r.pkg = pkg;
        r.createdAt = ts;
        r.candidates.add(RecordingTest.node("tv_skip", "跳过" + ts, new int[]{1, 1, 50, 50}));
        return r;
    }

    @Test public void keepsNewestThreePerPackage() throws Exception {
        File dir = tmp.newFolder();
        RecordingStore store = new RecordingStore(dir);
        for (long ts = 1; ts <= 5; ts++) store.saveAsync(rec("com.a", ts * 1000));
        store.saveAsync(rec("com.a.b", 9000));
        store.flush();
        List<File> files = store.files("com.a");
        assertEquals(3, files.size());
        assertEquals("com.a-5000.json", files.get(0).getName());
        assertEquals("com.a-3000.json", files.get(2).getName());
        assertEquals("前缀相同的别的包不受影响", 1, store.files("com.a.b").size());
        assertTrue(new File(dir, "recordings/com.a.b-9000.json").isFile());
        store.close();
    }

    @Test public void latestReadsBackNewestRecording() throws Exception {
        RecordingStore store = new RecordingStore(tmp.newFolder());
        store.save(rec("com.a", 1000));
        store.save(rec("com.a", 2000));
        Recording r = store.latest("com.a");
        assertEquals(2000, r.createdAt);
        assertEquals("跳过2000", r.candidates.get(0).text);
        assertNull(store.latest("com.none"));
        store.close();
    }

    @Test public void deleteAllRemovesOnlyThatPackage() throws Exception {
        RecordingStore store = new RecordingStore(tmp.newFolder());
        store.save(rec("com.a", 1000));
        store.save(rec("com.a", 2000));
        store.save(rec("com.b", 3000));
        assertEquals(2, store.deleteAll("com.a"));
        assertEquals(0, store.files("com.a").size());
        assertEquals(1, store.files("com.b").size());
        store.close();
    }
}
