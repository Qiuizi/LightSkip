package app.qi.qingtiao;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class Scope {
    static final String ENABLED = "enabled";
    static final String ALL = "all";
    static final String PICKED = "picked";
    static final String CUSTOM_RULES = "custom_rules";
    static final String TOTAL_COUNT = "total_count";
    static final String TODAY_COUNT = "today_count";
    static final String TODAY_DATE = "today_date";
    /** 「全部应用」模式下用户手动排除的包。 */
    static final String EXCLUDED = "excluded";
    /** v0.3 一次性迁移：老设备也回到默认「仅勾选」。 */
    static final String MIGRATED_V03 = "migrated_v03";
    /** 估算口径：每次跳过约省 3 秒（V5 决定 3c）。 */
    static final int SECONDS_PER_SKIP = 3;

    final SharedPreferences p;
    private final File filesDir;

    Scope(Context context) {
        p = context.getSharedPreferences("scope", Context.MODE_PRIVATE);
        filesDir = context.getFilesDir();
        if (!p.getBoolean(MIGRATED_V03, false)) {
            p.edit().putBoolean(ALL, false).putBoolean(MIGRATED_V03, true).apply();
        }
        RuleBook.loadCustomJson(customRules());
    }

    boolean enabled() {
        return p.getBoolean(ENABLED, false);
    }

    boolean all() {
        return p.getBoolean(ALL, false);
    }

    /** 已勾选的包（已去掉受保护包名）。 */
    Set<String> selected() {
        return ScopePolicy.sanitize(p.getStringSet(PICKED, Collections.singleton(RuleBook.SELF)));
    }

    /** 「全部应用」模式下的排除名单。 */
    Set<String> excluded() {
        return ScopePolicy.sanitize(p.getStringSet(EXCLUDED, Collections.<String>emptySet()));
    }

    /**
     * 粗粒度判断（不知道桌面/系统/输入法，界面层用）。
     * SkipService 用 ScopePolicy.shouldHandle 加上自己缓存的包分类做最终判断。
     */
    boolean includes(String pkg) {
        return ScopePolicy.shouldHandle(pkg, enabled(), all(), selected(), excluded(), false, false, false);
    }

    /** 受保护包永远不能勾选。 */
    static boolean canPick(String pkg) {
        return pkg != null && !pkg.isEmpty() && !RuleBook.isProtected(pkg);
    }

    void enable(boolean value) {
        p.edit().putBoolean(ENABLED, value).apply();
    }

    void mode(boolean value) {
        p.edit().putBoolean(ALL, value).apply();
    }

    /** 勾选/取消勾选。受保护包返回 false 且不写入。 */
    boolean pick(String pkg, boolean value) {
        if (value && !canPick(pkg)) return false;
        Set<String> s = selected();
        if (value) s.add(pkg);
        else s.remove(pkg);
        p.edit().putStringSet(PICKED, s).apply();
        return true;
    }

    /** 整体替换勾选集合（受保护包自动剔除）。 */
    void pickAll(Collection<String> pkgs) {
        p.edit().putStringSet(PICKED, ScopePolicy.sanitize(pkgs)).apply();
    }

    /**
     * 「一键勾选推荐应用」：把已安装的推荐应用并入勾选集合。
     * @param installed 已安装包名，一般传 AppCatalog.get(ctx).installedPackages()
     * @return 新增勾选的个数
     */
    int pickRecommended(Collection<String> installed) {
        List<String> rec = ScopePolicy.recommended(installed);
        Set<String> s = selected();
        int before = s.size();
        s.addAll(rec);
        p.edit().putStringSet(PICKED, s).apply();
        return s.size() - before;
    }

    /** 「全部应用」模式下排除/取消排除一个包。 */
    void exclude(String pkg, boolean value) {
        if (pkg == null || pkg.isEmpty()) return;
        Set<String> s = new HashSet<String>(p.getStringSet(EXCLUDED, Collections.<String>emptySet()));
        if (value) s.add(pkg);
        else s.remove(pkg);
        p.edit().putStringSet(EXCLUDED, s).apply();
    }

    String customRules() {
        return p.getString(CUSTOM_RULES, "");
    }

    void saveCustomRules(String json) {
        RuleBook.loadCustomJson(json);
        p.edit().putString(CUSTOM_RULES, json).apply();
    }

    /** 同包覆盖写入一条自定义规则。@return false 表示受保护包或现有 JSON 已损坏。 */
    boolean upsertCustomRule(RuleBook.Entry entry) {
        String json = CustomRules.upsert(customRules(), entry);
        if (json == null) return false;
        saveCustomRules(json);
        return true;
    }

    /** 删除某包的自定义规则。@return false 表示现有 JSON 已损坏。 */
    boolean removeCustomRule(String pkg) {
        String json = CustomRules.remove(customRules(), pkg);
        if (json == null) return false;
        saveCustomRules(json);
        return true;
    }

    /** 录制页「保存为规则」。成功时规则立即生效。 */
    SkipRecorder.SaveCode saveRecordedRule(Recording rec, int index, String title) {
        SkipRecorder.RuleResult r = SkipRecorder.buildRule(rec, index, title);
        if (r.code != SkipRecorder.SaveCode.OK) return r.code;
        return upsertCustomRule(r.entry) ? SkipRecorder.SaveCode.OK : SkipRecorder.SaveCode.BAD_EXISTING_JSON;
    }

    private String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    int todaySkips() {
        String key = todayKey();
        if (!key.equals(p.getString(TODAY_DATE, ""))) return 0;
        return p.getInt(TODAY_COUNT, 0);
    }

    int totalSkips() {
        return p.getInt(TOTAL_COUNT, 0);
    }

    /** 累计约省的秒数（按每次 3 秒估算），格式化交给界面 Ui.saved()。 */
    long savedSeconds() {
        return (long) totalSkips() * SECONDS_PER_SKIP;
    }

    /** 启动或应用目录加载完成后清洗历史残留的无效/受保护/系统包。 */
    int pruneSelected(Collection<String> allowedPkgs) {
        if (allowedPkgs == null) return 0;
        Set<String> raw = p.getStringSet(PICKED, Collections.singleton(RuleBook.SELF));
        Set<String> cleaned = ScopePolicy.prunePicked(raw, allowedPkgs);
        int rawSize = raw == null ? 0 : raw.size();
        if (cleaned.size() != rawSize || (raw != null && !cleaned.equals(raw))) {
            p.edit().putStringSet(PICKED, cleaned).apply();
            return Math.max(0, rawSize - cleaned.size());
        }
        return 0;
    }

    /** 关于页「清除跳过记录」：同时清计数与 HitLog 文件。 */
    void clearStats() {
        p.edit().remove(TOTAL_COUNT).remove(TODAY_COUNT).remove(TODAY_DATE).apply();
        HitLog.clearFile(filesDir);
    }

    void recordSkip() {
        String key = todayKey();
        int today = key.equals(p.getString(TODAY_DATE, "")) ? p.getInt(TODAY_COUNT, 0) + 1 : 1;
        int total = p.getInt(TOTAL_COUNT, 0) + 1;
        p.edit()
            .putString(TODAY_DATE, key)
            .putInt(TODAY_COUNT, today)
            .putInt(TOTAL_COUNT, total)
            .apply();
    }
}
