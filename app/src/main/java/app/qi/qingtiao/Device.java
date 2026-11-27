package app.qi.qingtiao;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

/** 权限检测、多机型 ROM 识别和系统设置跳转（界面层共用）。全部是本机系统调用，不需要新权限。 */
final class Device {
    private static final String UI_PREFS = "ui";
    private static final String RESTRICTED_SEEN = "restricted_seen";
    private static volatile RomProfile cachedRom;

    private Device() {}

    static RomProfile rom() {
        RomProfile r = cachedRom;
        if (r == null) {
            r = RomProfile.current();
            cachedRom = r;
        }
        return r;
    }

    static boolean accessibilityOn(Context c) {
        AccessibilityManager m = (AccessibilityManager) c.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (m == null) return false;
        for (AccessibilityServiceInfo i : m.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (i.getResolveInfo() == null || i.getResolveInfo().serviceInfo == null) continue;
            android.content.pm.ServiceInfo s = i.getResolveInfo().serviceInfo;
            if (c.getPackageName().equals(s.packageName) && SkipService.class.getName().equals(s.name)) return true;
        }
        return false;
    }

    static boolean batteryIgnored(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE);
    }

    /** 「允许受限设置」无法用 API 检测：无障碍已开就一定做过；否则以用户去看过为准。 */
    static boolean restrictedDone(Context c) {
        return accessibilityOn(c) || prefs(c).getBoolean(RESTRICTED_SEEN, false);
    }

    /** 3 步里已完成几步：受限设置、无障碍、省电无限制。 */
    static int permSteps(Context c) {
        int n = 0;
        if (restrictedDone(c)) n++;
        if (accessibilityOn(c)) n++;
        if (batteryIgnored(c)) n++;
        return n;
    }

    static void openAccessibility(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(a, "请在系统设置里搜「无障碍」，找到「轻跳」并打开", Toast.LENGTH_LONG).show();
        }
    }

    /** 应用信息页：右上角 ⋮ → 允许受限设置。 */
    static void openAppDetails(Activity a) {
        prefs(a).edit().putBoolean(RESTRICTED_SEEN, true).apply();
        try {
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + a.getPackageName())));
            Toast.makeText(a, rom().restrictedHint, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(a, "请在 设置 → 应用管理 → 轻跳 里找「允许受限设置」", Toast.LENGTH_LONG).show();
        }
    }

    static void requestBattery(Activity a) {
        RomProfile profile = rom();
        try {
            a.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + a.getPackageName())));
            return;
        } catch (Exception ignored) { }

        if (launchFirstAvailable(a, profile.batteryTargets)) {
            Toast.makeText(a, profile.batteryHint, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            a.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            Toast.makeText(a, profile.batteryHint, Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {
            openAppDetails(a);
        }
    }

    /** 按当前机型 ROM 自动跳转对应的自启动/后台管理页；不支持或打不开时优雅回退到应用信息页。 */
    static void openAutostart(Activity a) {
        RomProfile profile = rom();
        if (launchFirstAvailable(a, profile.autostartTargets)) {
            Toast.makeText(a, profile.autostartHint, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + a.getPackageName())));
            Toast.makeText(a, profile.autostartHint, Toast.LENGTH_LONG).show();
        } catch (Exception ignored) { }
    }

    private static boolean launchFirstAvailable(Activity a, String[][] targets) {
        if (targets == null || targets.length == 0) return false;
        PackageManager pm = a.getPackageManager();
        for (String[] pair : targets) {
            if (pair == null || pair.length < 2) continue;
            Intent intent = new Intent().setComponent(new ComponentName(pair[0], pair[1]));
            try {
                if (pm != null && pm.resolveActivity(intent, 0) == null) continue;
                a.startActivity(intent);
                return true;
            } catch (Exception ignored) {
                // 继续尝试下一个候选组件
            }
        }
        return false;
    }

    static void page(Activity a, String page) {
        a.startActivity(new Intent(a, PageActivity.class).putExtra(PageActivity.EXTRA_PAGE, page));
    }
}