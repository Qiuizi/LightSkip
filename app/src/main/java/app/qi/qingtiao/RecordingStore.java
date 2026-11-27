package app.qi.qingtiao;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 录制结果的持久化：filesDir/recordings/&lt;pkg&gt;-&lt;createdAt&gt;.json，每个包只留最新 3 份。
 * saveAsync 走单线程 Executor；其余读方法是同步磁盘读，界面层请放到后台线程或接受一次小读。
 */
final class RecordingStore {
    static final int KEEP_PER_PACKAGE = 3;
    private final File dir;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    RecordingStore(File filesDir) {
        this.dir = new File(filesDir, "recordings");
    }

    void saveAsync(final Recording r) {
        try {
            io.execute(new Runnable() {
                @Override public void run() { save(r); }
            });
        } catch (RejectedExecutionException closed) {
            // 已关闭，丢弃。
        }
    }

    synchronized boolean save(Recording r) {
        if (r == null || r.pkg == null || r.pkg.isEmpty()) return false;
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            File out = new File(dir, r.pkg + "-" + r.createdAt + ".json");
            File tmp = new File(dir, out.getName() + ".tmp");
            Files.write(tmp.toPath(), r.toJson().getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            List<File> all = files(r.pkg);
            for (int i = KEEP_PER_PACKAGE; i < all.size(); i++) {
                Files.deleteIfExists(all.get(i).toPath());
            }
            return true;
        } catch (IOException broken) {
            return false;
        }
    }

    /** 某包的录制文件，最新在前。 */
    synchronized List<File> files(final String pkg) {
        File[] found = dir.listFiles();
        List<File> out = new ArrayList<File>();
        if (found == null || pkg == null) return out;
        String prefix = pkg + "-";
        for (File f : Arrays.asList(found)) {
            String n = f.getName();
            if (n.startsWith(prefix) && n.endsWith(".json") && isTs(n.substring(prefix.length(), n.length() - 5))) {
                out.add(f);
            }
        }
        Collections.sort(out, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                long ta = ts(a.getName()), tb = ts(b.getName());
                return ta == tb ? 0 : (ta > tb ? -1 : 1);
            }
        });
        return out;
    }

    synchronized Recording latest(String pkg) {
        List<File> all = files(pkg);
        for (File f : all) {
            try {
                Recording r = Recording.fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                if (r != null) return r;
            } catch (IOException ignored) { }
        }
        return null;
    }

    synchronized int deleteAll(String pkg) {
        int n = 0;
        for (File f : files(pkg)) if (f.delete()) n++;
        return n;
    }

    void flush() {
        try {
            io.submit(new Runnable() { @Override public void run() { } }).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    void close() { io.shutdown(); }

    private static boolean isTs(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    private static long ts(String name) {
        int dash = name.lastIndexOf('-');
        try {
            return Long.parseLong(name.substring(dash + 1, name.length() - 5));
        } catch (RuntimeException bad) {
            return 0;
        }
    }
}
