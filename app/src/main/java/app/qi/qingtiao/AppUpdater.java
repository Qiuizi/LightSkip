package app.qi.qingtiao;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-app updater for LightSkip.
 * - Zero third-party dependencies (HttpURLConnection + built-in Json + UpdateFileProvider)
 * - 24h silent check throttle + manual check trigger
 * - Strict HTTPS enforcement (including redirects) and mandatory SHA-256 verification
 * - Automatic fallback to mirrorApkUrl and browser release page
 */
final class AppUpdater {
    static final String PRIMARY_METADATA_URL = "https://qiuizi.github.io/LightSkip/update.json";
    static final String FALLBACK_GITHUB_API_URL = "https://api.github.com/repos/Qiuizi/LightSkip/releases/latest";
    static final long CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private static final String PREFS_NAME = "lightskip_updater";
    private static final String KEY_LAST_CHECK_AT = "last_check_at";
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_METADATA_BYTES = 256 * 1024;
    private static final long PROGRESS_THROTTLE_MS = 100L;

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static volatile Handler mainHandler;
    private static void postMain(Runnable r) {
        Handler h = mainHandler;
        if (h == null) { h = new Handler(Looper.getMainLooper()); mainHandler = h; }
        h.post(r);
    }

    private AppUpdater() {}

    interface CheckCallback {
        void onUpdateAvailable(UpdateInfo info);
        void onUpToDate(int currentVersionCode, String currentVersionName);
        void onError(String message, String fallbackReleasePageUrl);
    }

    interface DownloadCallback {
        void onProgress(long bytesRead, long totalBytes, int percent);
        void onNeedInstallPermission(File apkFile);
        void onReadyToInstall(File apkFile);
        void onCancelled();
        void onError(String message, String fallbackReleasePageUrl);
    }

    static final class DownloadHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        void cancel() {
            cancelled.set(true);
        }

        boolean isCancelled() {
            return cancelled.get();
        }
    }

    static boolean isCheckDue(long lastCheckMs, long nowMs, long intervalMs) {
        if (lastCheckMs <= 0L) return true;
        if (nowMs < lastCheckMs) return true;
        return (nowMs - lastCheckMs) >= intervalMs;
    }

    static int currentVersionCode(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) {
                return (int) pi.getLongVersionCode();
            }
            return pi.versionCode;
        } catch (Exception e) {
            return 1;
        }
    }

    static String currentVersionName(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pi.versionName == null ? "" : pi.versionName;
        } catch (Exception e) {
            return "";
        }
    }

    static void checkIfDue(Context context, CheckCallback callback) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long last = prefs.getLong(KEY_LAST_CHECK_AT, 0L);
        long now = System.currentTimeMillis();
        if (!isCheckDue(last, now, CHECK_INTERVAL_MS)) {
            return;
        }
        checkNow(app, callback);
    }

    static void checkNow(Context context, CheckCallback callback) {
        final Context app = context.getApplicationContext();
        final int localCode = currentVersionCode(app);
        final String localName = currentVersionName(app);

        EXEC.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    UpdateInfo info = fetchLatestMetadata(localCode);
                    app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis())
                            .apply();
                    if (info.isNewerThan(localCode)) {
                        if (!info.isInstallable()) {
                            postCheckError(callback, "发现新版本，但缺少校验信息，请改用浏览器查看", info.releasePageUrl);
                        } else {
                            postMain(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onUpdateAvailable(info);
                                }
                            });
                        }
                    } else {
                        postMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onUpToDate(localCode, localName);
                            }
                        });
                    }
                } catch (Exception e) {
                    postCheckError(callback, "检查更新失败（网络超时或不可达），可改用浏览器打开", UpdateInfo.DEFAULT_RELEASE_PAGE);
                }
            }
        });
    }

    static DownloadHandle downloadAndInstall(Context context, final UpdateInfo info, final DownloadCallback callback) {
        final Context app = context.getApplicationContext();
        final DownloadHandle handle = new DownloadHandle();
        if (info == null || !info.isInstallable()) {
            postDownloadError(callback, "更新元数据不完整或缺少 SHA-256 校验值",
                    info == null ? UpdateInfo.DEFAULT_RELEASE_PAGE : info.releasePageUrl);
            return handle;
        }

        EXEC.execute(new Runnable() {
            @Override
            public void run() {
                File updatesDir = new File(app.getCacheDir(), UpdateFileProvider.UPDATES_SUBDIR);
                if (!updatesDir.exists() && !updatesDir.mkdirs()) {
                    postDownloadError(callback, "无法创建本地缓存目录", info.releasePageUrl);
                    return;
                }
                File partFile = new File(updatesDir, "LightSkip-" + info.versionCode + ".apk.part");
                File finalFile = new File(updatesDir, "LightSkip-" + info.versionCode + ".apk");

                try {
                    if (finalFile.isFile() && verifyFileSha256(finalFile, info.sha256)) {
                        dispatchInstallOrPermission(app, finalFile, callback);
                        return;
                    }
                    String actualSha = downloadWithMirrorFallback(info, partFile, handle, callback);
                    if (handle.isCancelled()) {
                        deleteQuietly(partFile);
                        postMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onCancelled();
                            }
                        });
                        return;
                    }
                    if (!info.sha256.equalsIgnoreCase(actualSha)) {
                        deleteQuietly(partFile);
                        postDownloadError(callback, "安装包 SHA-256 校验不匹配，已自动删除", info.releasePageUrl);
                        return;
                    }
                    cleanupOldApks(updatesDir, partFile.getName());
                    if (finalFile.exists()) {
                        deleteQuietly(finalFile);
                    }
                    if (!partFile.renameTo(finalFile)) {
                        deleteQuietly(partFile);
                        postDownloadError(callback, "保存安装包失败，请重试", info.releasePageUrl);
                        return;
                    }
                    dispatchInstallOrPermission(app, finalFile, callback);
                } catch (Exception e) {
                    deleteQuietly(partFile);
                    if (handle.isCancelled()) {
                        postMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onCancelled();
                            }
                        });
                    } else {
                        postDownloadError(callback, "下载失败，可重试或改用浏览器打开", info.releasePageUrl);
                    }
                }
            }
        });
        return handle;
    }

    static boolean canInstallPackages(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            return context.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    static boolean openInstallPermissionSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean installApk(Context context, File apkFile) {
        if (apkFile == null || !apkFile.isFile()) return false;
        try {
            Uri uri = UpdateFileProvider.uriFor(context, apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, UpdateFileProvider.APK_MIME);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean openReleasePage(Context context, String url) {
        String target = UpdateInfo.isHttpsUrl(url) ? url.trim() : UpdateInfo.DEFAULT_RELEASE_PAGE;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return toHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean verifyFileSha256(File file, String expectedHex) throws IOException {
        if (file == null || !file.isFile() || !UpdateInfo.isValidSha256(expectedHex)) return false;
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] buf = new byte[16 * 1024];
        try (InputStream in = new FileInputStream(file)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        return expectedHex.trim().equalsIgnoreCase(toHex(md.digest()));
    }

    private static UpdateInfo fetchLatestMetadata(int localCode) throws IOException {
        IOException primaryErr = null;
        try {
            String json = httpGetText(PRIMARY_METADATA_URL, "application/json");
            return UpdateInfo.parseUpdateJson(json);
        } catch (Exception e) {
            primaryErr = e instanceof IOException ? (IOException) e : new IOException(e);
        }
        try {
            String ghJson = httpGetText(FALLBACK_GITHUB_API_URL, "application/vnd.github+json");
            return UpdateInfo.parseGitHubRelease(ghJson, localCode);
        } catch (Exception e) {
            throw primaryErr;
        }
    }

    private static String downloadWithMirrorFallback(
            UpdateInfo info,
            File destPart,
            DownloadHandle handle,
            DownloadCallback callback) throws IOException {
        try {
            return streamDownload(info.apkUrl, info.sizeBytes, destPart, handle, callback);
        } catch (IOException primaryErr) {
            if (handle.isCancelled() || !UpdateInfo.isHttpsUrl(info.mirrorApkUrl)) {
                throw primaryErr;
            }
            deleteQuietly(destPart);
            return streamDownload(info.mirrorApkUrl, info.sizeBytes, destPart, handle, callback);
        }
    }

    private static String streamDownload(
            String urlStr,
            long hintTotalBytes,
            File destPart,
            DownloadHandle handle,
            final DownloadCallback callback) throws IOException {
        HttpURLConnection conn = openHttpsWithRedirects(urlStr, "*/*");
        try {
            long contentLength = conn.getContentLengthLong();
            final long total = contentLength > 0 ? contentLength : hintTotalBytes;
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            byte[] buf = new byte[16 * 1024];
            long read = 0L;
            long lastReportAt = 0L;
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(destPart)) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (handle.isCancelled()) {
                        return "";
                    }
                    out.write(buf, 0, n);
                    md.update(buf, 0, n);
                    read += n;
                    long now = System.currentTimeMillis();
                    if (now - lastReportAt >= PROGRESS_THROTTLE_MS || (total > 0 && read >= total)) {
                        lastReportAt = now;
                        final long r = read;
                        final int pct = total > 0 ? (int) Math.min(100L, (r * 100L) / total) : -1;
                        postMain(new Runnable() {
                            @Override
                            public void run() {
                                callback.onProgress(r, total, pct);
                            }
                        });
                    }
                }
                out.flush();
            }
            return toHex(md.digest());
        } finally {
            conn.disconnect();
        }
    }

    private static String httpGetText(String urlStr, String accept) throws IOException {
        HttpURLConnection conn = openHttpsWithRedirects(urlStr, accept);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_METADATA_BYTES) {
                    throw new IOException("Metadata response too large");
                }
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection openHttpsWithRedirects(String startUrl, String accept) throws IOException {
        String current = startUrl;
        for (int hops = 0; hops <= MAX_REDIRECTS; hops++) {
            if (!UpdateInfo.isHttpsUrl(current)) {
                throw new IOException("Refusing non-HTTPS URL");
            }
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", accept);
            conn.setRequestProperty("User-Agent", "LightSkip-Updater");
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER
                    || code == 307
                    || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.trim().isEmpty()) {
                    throw new IOException("Redirect without Location header");
                }
                current = new URL(new URL(current), location.trim()).toString();
                continue;
            }
            if (code < 200 || code >= 300) {
                conn.disconnect();
                throw new IOException("HTTP " + code);
            }
            return conn;
        }
        throw new IOException("Too many redirects");
    }

    private static void dispatchInstallOrPermission(final Context app, final File apkFile, final DownloadCallback callback) {
        postMain(new Runnable() {
            @Override
            public void run() {
                if (!canInstallPackages(app)) {
                    callback.onNeedInstallPermission(apkFile);
                } else {
                    installApk(app, apkFile);
                    callback.onReadyToInstall(apkFile);
                }
            }
        });
    }

    private static void postCheckError(final CheckCallback callback, final String msg, final String pageUrl) {
        postMain(new Runnable() {
            @Override
            public void run() {
                callback.onError(msg, pageUrl);
            }
        });
    }

    private static void postDownloadError(final DownloadCallback callback, final String msg, final String pageUrl) {
        postMain(new Runnable() {
            @Override
            public void run() {
                callback.onError(msg, pageUrl);
            }
        });
    }

    private static void cleanupOldApks(File dir, String keepName) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.getName().equals(keepName)) {
                deleteQuietly(f);
            }
        }
    }

    private static void deleteQuietly(File f) {
        if (f != null && f.exists()) {
            f.delete();
        }
    }

    private static String toHex(byte[] bytes) {
        char[] hex = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = hex[v >>> 4];
            out[i * 2 + 1] = hex[v & 0x0F];
        }
        return new String(out);
    }
}
