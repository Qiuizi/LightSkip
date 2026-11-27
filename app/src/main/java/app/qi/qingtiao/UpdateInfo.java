package app.qi.qingtiao;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed remote release metadata for in-app updates.
 * Strictly validates HTTPS download URLs and 64-hex SHA-256 checksums.
 */
final class UpdateInfo {
    static final String DEFAULT_RELEASE_PAGE = "https://github.com/Qiuizi/LightSkip/releases/latest";

    private static final Pattern HEX64 = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Pattern BODY_SHA256 = Pattern.compile("(?i)sha-?256\\s*[:=]\\s*([0-9a-fA-F]{64})");
    private static final Pattern SEMVER = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)$");

    final int versionCode;
    final String versionName;
    final String apkUrl;
    final String mirrorApkUrl;
    final String sha256;
    final long sizeBytes;
    final String releasePageUrl;
    final String notes;

    UpdateInfo(
            int versionCode,
            String versionName,
            String apkUrl,
            String mirrorApkUrl,
            String sha256,
            long sizeBytes,
            String releasePageUrl,
            String notes) {
        this.versionCode = versionCode;
        this.versionName = versionName == null ? "" : versionName.trim();
        this.apkUrl = apkUrl == null ? "" : apkUrl.trim();
        this.mirrorApkUrl = mirrorApkUrl == null ? "" : mirrorApkUrl.trim();
        this.sha256 = sha256 == null ? "" : sha256.trim().toLowerCase(Locale.US);
        this.sizeBytes = Math.max(0L, sizeBytes);
        this.releasePageUrl = isHttpsUrl(releasePageUrl) ? releasePageUrl.trim() : DEFAULT_RELEASE_PAGE;
        this.notes = notes == null ? "" : notes.trim();
    }

    boolean isNewerThan(int localVersionCode) {
        return versionCode > localVersionCode;
    }

    boolean isInstallable() {
        return versionCode > 0 && isHttpsUrl(apkUrl) && isValidSha256(sha256);
    }

    static boolean isHttpsUrl(String url) {
        if (url == null) return false;
        String u = url.trim();
        return u.length() > 8 && u.regionMatches(true, 0, "https://", 0, 8) && u.indexOf('\n') < 0 && u.indexOf('\r') < 0;
    }

    static boolean isValidSha256(String hex) {
        return hex != null && HEX64.matcher(hex.trim()).matches();
    }

    @SuppressWarnings("unchecked")
    static UpdateInfo parseUpdateJson(String jsonText) {
        Object parsed = Json.parse(jsonText);
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("update.json root must be an object");
        }
        Map<String, Object> obj = (Map<String, Object>) parsed;
        int code = asInt(obj.get("versionCode"), -1);
        if (code <= 0) {
            throw new IllegalArgumentException("Missing or invalid versionCode");
        }
        String name = asString(obj.get("versionName"));
        String apkUrl = asString(obj.get("apkUrl"));
        String mirrorApkUrl = asString(obj.get("mirrorApkUrl"));
        String sha256 = asString(obj.get("sha256"));
        long size = asLong(obj.get("sizeBytes"), 0L);
        String pageUrl = asString(obj.get("releasePageUrl"));
        String notes = asString(obj.get("notes"));

        if (!apkUrl.isEmpty() && !isHttpsUrl(apkUrl)) {
            throw new IllegalArgumentException("apkUrl must use https://");
        }
        if (!mirrorApkUrl.isEmpty() && !isHttpsUrl(mirrorApkUrl)) {
            throw new IllegalArgumentException("mirrorApkUrl must use https://");
        }
        if (!sha256.isEmpty() && !isValidSha256(sha256)) {
            throw new IllegalArgumentException("sha256 must be 64 hex chars");
        }
        return new UpdateInfo(code, name, apkUrl, mirrorApkUrl, sha256, size, pageUrl, notes);
    }

    @SuppressWarnings("unchecked")
    static UpdateInfo parseGitHubRelease(String jsonText, int currentVersionCode) {
        Object parsed = Json.parse(jsonText);
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("GitHub release root must be an object");
        }
        Map<String, Object> obj = (Map<String, Object>) parsed;
        String tag = asString(obj.get("tag_name"));
        String versionName = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
        String body = asString(obj.get("body"));
        String htmlUrl = asString(obj.get("html_url"));

        int versionCode = parseVersionCodeFromTagOrBody(tag, body, currentVersionCode);
        String apkUrl = "";
        long size = 0L;
        String sha256 = "";

        Object assetsObj = obj.get("assets");
        if (assetsObj instanceof List) {
            for (Object item : (List<Object>) assetsObj) {
                if (!(item instanceof Map)) continue;
                Map<String, Object> asset = (Map<String, Object>) item;
                String name = asString(asset.get("name"));
                if (!name.toLowerCase(Locale.US).endsWith(".apk")) continue;
                String dl = asString(asset.get("browser_download_url"));
                if (!isHttpsUrl(dl)) continue;
                apkUrl = dl;
                size = asLong(asset.get("size"), 0L);
                String digest = asString(asset.get("digest"));
                if (digest.toLowerCase(Locale.US).startsWith("sha256:")) {
                    String candidate = digest.substring("sha256:".length()).trim();
                    if (isValidSha256(candidate)) {
                        sha256 = candidate;
                    }
                }
                break;
            }
        }
        if (sha256.isEmpty()) {
            Matcher m = BODY_SHA256.matcher(body);
            if (m.find()) {
                sha256 = m.group(1);
            }
        }
        if (versionCode <= 0) {
            throw new IllegalArgumentException("Cannot determine versionCode from release");
        }
        return new UpdateInfo(versionCode, versionName, apkUrl, "", sha256, size, htmlUrl, body);
    }

    static int parseVersionCodeFromTagOrBody(String tag, String body, int currentVersionCode) {
        if (body != null) {
            Matcher m = Pattern.compile("(?i)versionCode\\s*[:=]\\s*(\\d+)").matcher(body);
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {}
            }
        }
        if (tag != null) {
            Matcher m = SEMVER.matcher(tag.trim());
            if (m.matches()) {
                int major = Integer.parseInt(m.group(1));
                int minor = Integer.parseInt(m.group(2));
                int patch = Integer.parseInt(m.group(3));
                if (major == 0 && patch == 0 && minor > 0) {
                    return minor;
                }
                return major * 10000 + minor * 100 + patch;
            }
        }
        return currentVersionCode;
    }

    private static String asString(Object v) {
        return v instanceof String ? ((String) v).trim() : "";
    }

    private static int asInt(Object v, int fallback) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt(((String) v).trim()); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static long asLong(Object v, long fallback) {
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) {
            try { return Long.parseLong(((String) v).trim()); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }
}
