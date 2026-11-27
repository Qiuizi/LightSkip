package app.qi.qingtiao;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppUpdaterTest {
    private static final String VALID_SHA = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void parseUpdateJsonValid() {
        String json = "{"
                + "\"versionCode\":4,"
                + "\"versionName\":\"0.4.0\","
                + "\"apkUrl\":\"https://github.com/Qiuizi/LightSkip/releases/download/v0.4.0/LightSkip-0.4.0.apk\","
                + "\"mirrorApkUrl\":\"https://cloudfitness.top/lightskip/LightSkip-0.4.0.apk\","
                + "\"sha256\":\"" + VALID_SHA.toUpperCase() + "\","
                + "\"sizeBytes\":123456,"
                + "\"notes\":\"bug fixes\""
                + "}";
        UpdateInfo info = UpdateInfo.parseUpdateJson(json);
        assertEquals(4, info.versionCode);
        assertEquals("0.4.0", info.versionName);
        assertEquals(VALID_SHA, info.sha256);
        assertEquals(123456L, info.sizeBytes);
        assertTrue(info.isNewerThan(2));
        assertFalse(info.isNewerThan(4));
        assertTrue(info.isInstallable());
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseUpdateJsonRejectsHttpApkUrl() {
        String json = "{\"versionCode\":4,\"versionName\":\"0.4.0\",\"apkUrl\":\"http://example.com/a.apk\",\"sha256\":\"" + VALID_SHA + "\"}";
        UpdateInfo.parseUpdateJson(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseUpdateJsonRejectsBadSha256() {
        String json = "{\"versionCode\":4,\"versionName\":\"0.4.0\",\"apkUrl\":\"https://example.com/a.apk\",\"sha256\":\"not-a-hash\"}";
        UpdateInfo.parseUpdateJson(json);
    }

    @Test
    public void parseUpdateJsonWithoutShaIsNotInstallable() {
        String json = "{\"versionCode\":4,\"versionName\":\"0.4.0\",\"apkUrl\":\"https://example.com/a.apk\",\"sha256\":\"\"}";
        UpdateInfo info = UpdateInfo.parseUpdateJson(json);
        assertTrue(info.isNewerThan(2));
        assertFalse(info.isInstallable());
    }

    @Test
    public void parseGitHubReleaseWithDigestAndBodyVersionCode() {
        String gh = "{"
                + "\"tag_name\":\"v0.4.0\","
                + "\"html_url\":\"https://github.com/Qiuizi/LightSkip/releases/tag/v0.4.0\","
                + "\"body\":\"versionCode: 4\\nFast update\","
                + "\"assets\":[{"
                + "\"name\":\"LightSkip-0.4.0-release.apk\","
                + "\"size\":204800,"
                + "\"digest\":\"sha256:" + VALID_SHA + "\","
                + "\"browser_download_url\":\"https://github.com/Qiuizi/LightSkip/releases/download/v0.4.0/LightSkip-0.4.0-release.apk\""
                + "}]"
                + "}";
        UpdateInfo info = UpdateInfo.parseGitHubRelease(gh, 2);
        assertEquals(4, info.versionCode);
        assertEquals("0.4.0", info.versionName);
        assertEquals(VALID_SHA, info.sha256);
        assertEquals(204800L, info.sizeBytes);
        assertTrue(info.isInstallable());
    }

    @Test
    public void parseGitHubReleaseExtractsShaFromBody() {
        String gh = "{"
                + "\"tag_name\":\"v0.3.0\","
                + "\"html_url\":\"https://github.com/Qiuizi/LightSkip/releases/tag/v0.3.0\","
                + "\"body\":\"SHA-256: " + VALID_SHA + "\","
                + "\"assets\":[{"
                + "\"name\":\"app-release.apk\","
                + "\"size\":1024,"
                + "\"browser_download_url\":\"https://github.com/Qiuizi/LightSkip/releases/download/v0.3.0/app-release.apk\""
                + "}]"
                + "}";
        UpdateInfo info = UpdateInfo.parseGitHubRelease(gh, 2);
        assertEquals(3, info.versionCode);
        assertEquals(VALID_SHA, info.sha256);
        assertTrue(info.isInstallable());
    }

    @Test
    public void checkThrottleRespects24HourWindow() {
        long day = AppUpdater.CHECK_INTERVAL_MS;
        assertTrue(AppUpdater.isCheckDue(0L, 1000L, day));
        assertFalse(AppUpdater.isCheckDue(1000L, 1000L + day - 1L, day));
        assertTrue(AppUpdater.isCheckDue(1000L, 1000L + day, day));
        assertTrue(AppUpdater.isCheckDue(5000L, 1000L, day));
    }

    @Test
    public void fileSha256VerificationDetectsTampering() throws Exception {
        File f = tmp.newFile("LightSkip-4.apk");
        byte[] content = "lightskip-apk-payload".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(content);
        }
        String expected = AppUpdater.sha256Hex(content);
        assertTrue(AppUpdater.verifyFileSha256(f, expected));
        assertFalse(AppUpdater.verifyFileSha256(f, VALID_SHA));
    }

    @Test
    public void updateFileProviderRejectsPathTraversal() {
        assertTrue(UpdateFileProvider.isSafeApkFileName("LightSkip-4.apk"));
        assertFalse(UpdateFileProvider.isSafeApkFileName("../LightSkip-4.apk"));
        assertFalse(UpdateFileProvider.isSafeApkFileName("sub/LightSkip-4.apk"));
        assertFalse(UpdateFileProvider.isSafeApkFileName("LightSkip-4.txt"));
        assertFalse(UpdateFileProvider.isSafeApkFileName(""));
    }

    @Test
    public void httpsGuardRejectsUnsafeSchemes() {
        assertTrue(UpdateInfo.isHttpsUrl("https://github.com/Qiuizi/LightSkip"));
        assertFalse(UpdateInfo.isHttpsUrl("http://github.com/Qiuizi/LightSkip"));
        assertFalse(UpdateInfo.isHttpsUrl("file:///sdcard/a.apk"));
        assertFalse(UpdateInfo.isHttpsUrl("https://example.com/a\nb"));
    }
}
