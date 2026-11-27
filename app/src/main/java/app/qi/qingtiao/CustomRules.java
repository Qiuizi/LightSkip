package app.qi.qingtiao;

import java.util.Map;

/** 自定义规则 JSON 的合并与删除（纯函数）。结果交给 Scope.saveCustomRules 持久化。 */
final class CustomRules {
    private CustomRules() {}

    /**
     * 同包覆盖地写入一条规则。
     * @return 新 JSON；受保护包返回 null；原 JSON 格式坏了也返回 null（不覆盖用户的数据）。
     */
    static String upsert(String existingJson, RuleBook.Entry entry) {
        if (entry == null || entry.pkg == null || entry.pkg.isEmpty()
                || RuleBook.isProtected(entry.pkg)) return null;
        Map<String, RuleBook.Entry> map = RuleBook.parseCustom(existingJson);
        if (map == null) return null;
        map.remove(entry.pkg);
        map.put(entry.pkg, entry);
        return RuleBook.toJson(map.values());
    }

    /** 删除某个包的自定义规则；原 JSON 格式坏了返回 null。 */
    static String remove(String existingJson, String pkg) {
        Map<String, RuleBook.Entry> map = RuleBook.parseCustom(existingJson);
        if (map == null) return null;
        map.remove(pkg);
        return RuleBook.toJson(map.values());
    }
}
