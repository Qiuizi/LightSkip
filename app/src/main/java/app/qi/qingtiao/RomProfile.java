package app.qi.qingtiao;

import android.os.Build;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * 多机型 / ROM 自动识别与厂商保活设置路由表（纯本地，零网络权限）。
 * 覆盖小米 HyperOS/MIUI、华为 HarmonyOS/EMUI、荣耀 MagicOS、OPPO/一加/真我 ColorOS、
 * vivo/iQOO OriginOS、三星 OneUI 及原生安卓。
 */
final class RomProfile {
    enum Brand {
        XIAOMI,
        HUAWEI,
        HONOR,
        OPPO,
        VIVO,
        SAMSUNG,
        STOCK
    }

    interface PropLookup {
        String get(String key);
    }

    final Brand brand;
    final String displayName;
    final String autostartLabel;
    final String autostartHint;
    final String batteryHint;
    final String restrictedHint;
    /** 按优先级尝试的厂商自启动管理页 [package, class]。 */
    final String[][] autostartTargets;
    /** 按优先级尝试的厂商后台耗电管理页 [package, class]。 */
    final String[][] batteryTargets;

    private RomProfile(
            Brand brand,
            String displayName,
            String autostartLabel,
            String autostartHint,
            String batteryHint,
            String restrictedHint,
            String[][] autostartTargets,
            String[][] batteryTargets) {
        this.brand = brand;
        this.displayName = displayName;
        this.autostartLabel = autostartLabel;
        this.autostartHint = autostartHint;
        this.batteryHint = batteryHint;
        this.restrictedHint = restrictedHint;
        this.autostartTargets = autostartTargets;
        this.batteryTargets = batteryTargets;
    }

    static RomProfile current() {
        return detect(Build.MANUFACTURER, Build.BRAND, new PropLookup() {
            @Override public String get(String key) {
                return readSystemProperty(key);
            }
        });
    }

    static RomProfile detect(String manufacturer, String brand, PropLookup props) {
        String m = norm(manufacturer);
        String b = norm(brand);
        String pMiui = prop(props, "ro.miui.ui.version.name");
        String pMagic = prop(props, "ro.build.version.magic");
        String pEmui = prop(props, "ro.build.version.emui");
        String pHarmony = prop(props, "hw_sc.build.platform.version");
        String pColor = prop(props, "ro.build.version.opporom");
        String pOplus = prop(props, "ro.build.version.oplusrom");
        String pVivo = prop(props, "ro.vivo.os.version");
        String pOneUi = prop(props, "ro.build.version.oneui");

        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")
                || b.contains("xiaomi") || b.contains("redmi") || b.contains("poco")
                || !pMiui.isEmpty()) {
            return new RomProfile(
                Brand.XIAOMI,
                "小米 / HyperOS",
                "自启动管理",
                "打开「轻跳」的自启动开关",
                "省电策略选「无限制」",
                "右上角 ⋮ → 允许受限设置",
                new String[][]{
                    {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                    {"com.miui.securitycenter", "com.miui.permcenter.MainAcitivty"}
                },
                new String[][]{
                    {"com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"},
                    {"com.miui.securitycenter", "com.miui.powercenter.PowerSettings"}
                }
            );
        }

        if (m.contains("honor") || b.contains("honor") || !pMagic.isEmpty()) {
            return new RomProfile(
                Brand.HONOR,
                "荣耀 / MagicOS",
                "应用启动管理",
                "找到「轻跳」，改为手动管理并允许自启动与后台活动",
                "电池优化中将「轻跳」设为不允许优化",
                "右上角 ⋮ → 允许受限设置",
                new String[][]{
                    {"com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                    {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"}
                },
                new String[][]{
                    {"com.hihonor.systemmanager", "com.hihonor.systemmanager.power.ui.HwPowerManagerActivity"}
                }
            );
        }

        if (m.contains("huawei") || b.contains("huawei") || !pEmui.isEmpty() || !pHarmony.isEmpty()) {
            return new RomProfile(
                Brand.HUAWEI,
                "华为 / HarmonyOS",
                "应用启动管理",
                "找到「轻跳」，关闭自动管理并打开允许自启动与后台活动",
                "电池优化中将「轻跳」设为不允许",
                "右上角 ⋮ → 允许受限设置",
                new String[][]{
                    {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                    {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity"}
                },
                new String[][]{
                    {"com.huawei.systemmanager", "com.huawei.systemmanager.power.ui.HwPowerManagerActivity"}
                }
            );
        }

        if (m.contains("oppo") || m.contains("oneplus") || m.contains("realme")
                || b.contains("oppo") || b.contains("oneplus") || b.contains("realme")
                || !pColor.isEmpty() || !pOplus.isEmpty()) {
            return new RomProfile(
                Brand.OPPO,
                "OPPO / ColorOS",
                "自启动与后台运行",
                "在「自启动」和「允许完全后台行为」里打开「轻跳」",
                "耗电管理设为「允许后台运行」",
                "右上角 ⋮ → 允许受限设置",
                new String[][]{
                    {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                    {"com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"},
                    {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"}
                },
                new String[][]{
                    {"com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerControlActivity"},
                    {"com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"}
                }
            );
        }

        if (m.contains("vivo") || m.contains("iqoo") || b.contains("vivo") || b.contains("iqoo") || !pVivo.isEmpty()) {
            return new RomProfile(
                Brand.VIVO,
                "vivo / OriginOS",
                "自启动与后台高耗电",
                "打开「轻跳」的自启动与允许后台高耗电",
                "电池管理选「允许后台高耗电」",
                "右上角 ⋮ → 允许受限设置",
                new String[][]{
                    {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                    {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"}
                },
                new String[][]{
                    {"com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"},
                    {"com.iqoo.powersaving", "com.iqoo.powersaving.PowerSavingManagerActivity"}
                }
            );
        }

        if (m.contains("samsung") || b.contains("samsung") || !pOneUi.isEmpty()) {
            return new RomProfile(
                Brand.SAMSUNG,
                "三星 / OneUI",
                "后台电池管理",
                "在电池设置中将「轻跳」设为「不受限制」",
                "应用电池选「不受限制」",
                "右上角 ⋮ → 允许受限设置",
                new String[][]{
                    {"com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"},
                    {"com.samsung.android.sm_cn", "com.samsung.android.sm.battery.ui.BatteryActivity"}
                },
                new String[][]{
                    {"com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"}
                }
            );
        }

        return new RomProfile(
            Brand.STOCK,
            "原生安卓",
            "后台运行设置",
            "在应用信息 → 电池里设为「不受限制」",
            "电池优化中设为「不受限制」",
            "右上角 ⋮ → 允许受限设置",
            new String[0][0],
            new String[0][0]
        );
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String prop(PropLookup lookup, String key) {
        if (lookup == null) return "";
        try {
            String v = lookup.get(key);
            return v == null ? "" : v.trim();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String readSystemProperty(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class, String.class);
            Object val = get.invoke(null, key, "");
            return val == null ? "" : val.toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}