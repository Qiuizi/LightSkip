package app.qi.qingtiao;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class HitLog {
    static final int MAX_LINES = 500;
    private final Path path;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** 文件当前行数；-1 表示未知，下次写入时数一遍。只在 synchronized(this) 内读写。 */
    private int lineCount = -1;

    static final class Entry {
        long ts;
        String pkg = "", activity = "", nodeId = "", nodeText = "";
        String rawEventClass = "";
        String clickMethod = "none", result = "near_miss";
        int[] bounds = new int[4];
        int score;
        List<String> details = new ArrayList<String>();
    }

    static void clearFile(File dir) {
        if (dir == null) return;
        try {
            Files.deleteIfExists(new File(dir, "hits.jsonl").toPath());
        } catch (Exception ignored) {}
    }

    HitLog(File dir) {
        this.path = new File(dir, "hits.jsonl").toPath();
    }

    /** 只入队，不在调用线程做任何磁盘 IO。 */
    void log(final Entry e) {
        try {
            io.execute(new Runnable() {
                @Override public void run() { write(e); }
            });
        } catch (java.util.concurrent.RejectedExecutionException closed) {
            // 服务已销毁，丢弃这条日志。
        }
    }

    List<String> recent(int n) {
        synchronized (this) {
            List<String> all = readAll();
            int from = Math.max(0, all.size() - Math.max(0, n));
            return new ArrayList<String>(all.subList(from, all.size()));
        }
    }

    String exportText() {
        synchronized (this) {
            StringBuilder sb = new StringBuilder();
            for (String line : readAll()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            return sb.toString();
        }
    }

    void clear() {
        synchronized (this) {
            try {
                Files.deleteIfExists(path);
                lineCount = 0;
            } catch (java.io.IOException ignored) {
                lineCount = -1;
            }
        }
    }

    void flush() {
        try {
            io.submit(new Runnable() { @Override public void run() { } }).get(10, TimeUnit.SECONDS);
        } catch (Exception interrupted) {
            throw new IllegalStateException(interrupted);
        }
    }

    void close() { io.shutdown(); }

    /** 追加一行；只有超过 MAX_LINES 时才整体压缩到最近 MAX_LINES 行。 */
    private void write(Entry e) {
        synchronized (this) {
            try {
                if (lineCount < 0) lineCount = readAll().size();
                Files.write(path,
                    java.util.Collections.singletonList(line(e)),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
                lineCount++;
                if (lineCount > MAX_LINES) compact();
            } catch (java.io.IOException ignored) {
                lineCount = -1;
            }
        }
    }

    private void compact() throws java.io.IOException {
        List<String> lines = readAll();
        if (lines.size() <= MAX_LINES) {
            lineCount = lines.size();
            return;
        }
        List<String> keep = new ArrayList<String>(
            lines.subList(lines.size() - MAX_LINES, lines.size()));
        Path tmp = path.resolveSibling("hits.jsonl.tmp");
        Files.write(tmp, keep, StandardCharsets.UTF_8);
        Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        lineCount = keep.size();
    }

    private List<String> readAll() {
        try {
            if (!Files.exists(path)) return new ArrayList<String>();
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (java.io.IOException broken) {
            return new ArrayList<String>();
        }
    }

    static String line(Entry e) {
        StringBuilder b = new StringBuilder();
        b.append("{\"ts\":").append(e.ts);
        b.append(",\"pkg\":").append(Json.quote(e.pkg));
        b.append(",\"activity\":").append(Json.quote(e.activity));
        b.append(",\"rawEventClass\":").append(Json.quote(e.rawEventClass));
        b.append(",\"nodeId\":").append(Json.quote(e.nodeId));
        b.append(",\"nodeText\":").append(Json.quote(e.nodeText));
        b.append(",\"bounds\":[").append(e.bounds[0]).append(',').append(e.bounds[1])
            .append(',').append(e.bounds[2]).append(',').append(e.bounds[3]).append(']');
        b.append(",\"score\":").append(e.score);
        b.append(",\"details\":[");
        for (int i = 0; i < e.details.size(); i++) {
            if (i > 0) b.append(',');
            b.append(Json.quote(e.details.get(i)));
        }
        b.append("],\"clickMethod\":").append(Json.quote(e.clickMethod));
        b.append(",\"result\":").append(Json.quote(e.result));
        b.append('}');
        return b.toString();
    }
}
