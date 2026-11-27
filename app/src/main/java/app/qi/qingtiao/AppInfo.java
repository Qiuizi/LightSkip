package app.qi.qingtiao;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 应用目录里的一行（纯数据，不含图标）。规则相关的状态每次实时查 RuleBook，
 * 所以录制或导入规则后不用重扫应用也能显示最新的「专属规则」。
 */
final class AppInfo {
    final String pkg;
    final String label;
    /** 未更新过的预装系统应用。 */
    final boolean system;
    /** 桌面（CATEGORY_HOME）。 */
    final boolean home;
    /** 输入法。 */
    final boolean ime;
    /** 有桌面图标（CATEGORY_LAUNCHER 入口）。 */
    final boolean launchable;

    AppInfo(String pkg, String label, boolean system, boolean home, boolean ime, boolean launchable) {
        this.pkg = pkg == null ? "" : pkg;
        this.label = label == null || label.trim().isEmpty() ? this.pkg : label.trim();
        this.system = system;
        this.home = home;
        this.ime = ime;
        this.launchable = launchable;
    }

    boolean isSelf() { return RuleBook.SELF.equals(pkg); }
    boolean isProtected() { return RuleBook.PROTECTED_PACKAGES.contains(pkg); }
    boolean hasBuiltinRule() { return RuleBook.hasBuiltinRule(pkg); }
    boolean hasCustomRule() { return RuleBook.hasCustomRule(pkg); }
    boolean hasRule() { return RuleBook.hasPresetRule(pkg); }
    boolean verified() { return hasRule() && RuleBook.find(pkg).verified; }

    /** 列表行的单行状态标签（V5 6.1-2）。 */
    String ruleTag() { return hasRule() ? "专属规则" : "智能识别"; }

    /** 「生效范围」列表里显示的普通应用：有桌面入口，不是系统、桌面、输入法，不受保护。 */
    boolean listable() {
        if (isSelf()) return true;
        return launchable && !system && !home && !ime && !isProtected();
    }

    // ---- 列表工具（纯函数） ----

    /** 可勾选的应用：内置测试页置顶，其后有规则的在前，再按中文名排序。 */
    static List<AppInfo> listableOf(List<AppInfo> all) {
        List<AppInfo> out = new ArrayList<AppInfo>();
        if (all == null) return out;
        for (AppInfo a : all) if (a.listable()) out.add(a);
        sort(out);
        return out;
    }

    /** 「永不点击」组：已安装的受保护应用，只用来显示。 */
    static List<AppInfo> lockedOf(List<AppInfo> all) {
        List<AppInfo> out = new ArrayList<AppInfo>();
        if (all == null) return out;
        for (AppInfo a : all) if (a.isProtected()) out.add(a);
        sort(out);
        return out;
    }

    /** 按中文名或包名模糊匹配，忽略大小写；空查询返回原列表副本。 */
    static List<AppInfo> filter(List<AppInfo> list, String query) {
        List<AppInfo> out = new ArrayList<AppInfo>();
        if (list == null) return out;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (AppInfo a : list) {
            if (q.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(q)
                    || a.pkg.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(a);
            }
        }
        return out;
    }

    static void sort(List<AppInfo> list) {
        final Collator zh = Collator.getInstance(Locale.CHINA);
        Collections.sort(list, new Comparator<AppInfo>() {
            @Override public int compare(AppInfo a, AppInfo b) {
                if (a.isSelf() != b.isSelf()) return a.isSelf() ? -1 : 1;
                if (a.hasRule() != b.hasRule()) return a.hasRule() ? -1 : 1;
                int c = zh.compare(a.label, b.label);
                return c != 0 ? c : a.pkg.compareTo(b.pkg);
            }
        });
    }
}
