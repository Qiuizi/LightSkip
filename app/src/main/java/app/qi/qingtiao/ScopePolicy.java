package app.qi.qingtiao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 生效范围的纯逻辑（不依赖 android.*）。
 * SkipService 和界面层都只通过这里判断「某个包该不该处理」。
 */
final class ScopePolicy {
    private ScopePolicy() {}

    /** 无障碍服务的监听配置。packages == null 表示监听所有包（「全部应用」模式）。 */
    static final class Filter {
        final boolean listen;
        final String[] packages;

        Filter(boolean listen, String[] packages) {
            this.listen = listen;
            this.packages = packages;
        }
    }

    /**
     * 是否在这个包里找「跳过」并允许点击。
     * PROTECTED_PACKAGES 永远拒绝；「仅勾选」只看勾选集合；
     * 「全部应用」排除用户排除名单、桌面、输入法和未更新过的系统应用。
     */
    static boolean shouldHandle(String pkg, boolean enabled, boolean all, Set<String> picked,
            Set<String> excluded, boolean isLauncher, boolean isSystem, boolean isIme) {
        if (pkg == null || pkg.isEmpty()) return false;
        if (RuleBook.PROTECTED_PACKAGES.contains(pkg)) return false;
        if (!enabled) return false;
        if (!all) {
            if (picked == null || !picked.contains(pkg)) return false;
            if (RuleBook.SELF.equals(pkg)) return true;
            return !isLauncher && !isSystem && !isIme;
        }
        if (excluded != null && excluded.contains(pkg)) return false;
        if (RuleBook.SELF.equals(pkg)) return true;
        return !isLauncher && !isSystem && !isIme;
    }

    /** 是否接收这个包的事件：可处理的包，或正在录制的目标包（只采集不点击）。 */
    static boolean shouldListen(String pkg, boolean enabled, boolean all, Set<String> picked,
            Set<String> excluded, boolean isLauncher, boolean isSystem, boolean isIme,
            String recordingTarget) {
        if (pkg == null || pkg.isEmpty() || RuleBook.PROTECTED_PACKAGES.contains(pkg)) return false;
        if (pkg.equals(recordingTarget)) return true;
        return shouldHandle(pkg, enabled, all, picked, excluded, isLauncher, isSystem, isIme);
    }

    /** 由当前状态算出无障碍服务的 eventTypes 开关和 packageNames。 */
    static Filter serviceFilter(boolean enabled, boolean all, Set<String> picked, String recordingTarget) {
        if (enabled && all) return new Filter(true, null);
        TreeSet<String> pkgs = new TreeSet<String>();
        if (enabled && picked != null) pkgs.addAll(sanitize(picked));
        if (recordingTarget != null && !recordingTarget.isEmpty()
                && !RuleBook.PROTECTED_PACKAGES.contains(recordingTarget)) {
            pkgs.add(recordingTarget);
        }
        if (pkgs.isEmpty()) return new Filter(false, new String[]{RuleBook.SELF});
        return new Filter(true, pkgs.toArray(new String[pkgs.size()]));
    }

    /** 去掉空值和受保护包名。勾选、全选、排除名单写入前都要过一遍。 */
    static Set<String> sanitize(Collection<String> pkgs) {
        Set<String> out = new HashSet<String>();
        if (pkgs == null) return out;
        for (String pkg : pkgs) {
            if (pkg == null) continue;
            String p = pkg.trim();
            if (!p.isEmpty() && !RuleBook.PROTECTED_PACKAGES.contains(p)) out.add(p);
        }
        return out;
    }

    /** 清洗历史残留的勾选包：剔除受保护包、空值以及不在可勾选名单里的系统/输入法/已卸载包。 */
    static Set<String> prunePicked(Collection<String> picked, Collection<String> allowed) {
        Set<String> clean = sanitize(picked);
        if (allowed == null) return clean;
        Set<String> allowSet = new HashSet<String>(allowed);
        Set<String> out = new HashSet<String>();
        for (String pkg : clean) {
            if (allowSet.contains(pkg) || RuleBook.SELF.equals(pkg)) out.add(pkg);
        }
        return out;
    }

    /**
     * 「一键勾选推荐应用」：已安装、且有内置或自定义规则、且不受保护的应用。
     * 已验证（verified）的排在前面；内置测试页不算推荐。
     */
    static List<String> recommended(Collection<String> installed) {
        List<String> verified = new ArrayList<String>();
        List<String> others = new ArrayList<String>();
        if (installed == null) return verified;
        for (String pkg : new TreeSet<String>(sanitize(installed))) {
            if (RuleBook.SELF.equals(pkg) || !RuleBook.hasPresetRule(pkg)) continue;
            if (RuleBook.find(pkg).verified) verified.add(pkg);
            else others.add(pkg);
        }
        verified.addAll(others);
        return verified;
    }
}
