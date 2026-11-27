package app.qi.qingtiao;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class RuleBook {
    static final String SELF = "app.qi.qingtiao";
    static final long WINDOW_MS = 4500L;

    private static final Pattern SKIP = Pattern.compile(
        "^(?:(?:跳过(?:广告)?|略過(?:廣告)?|跳過(?:廣告)?|关闭(?:广告)?|skip(?:\\s+ad)?)(?:\\s*[|·(（]?\\s*\\d{1,2}\\s*(?:s|秒)?[)）]?)?|\\d{1,2}\\s*(?:s|秒)?\\s*[|·]?\\s*(?:跳过|跳過|略過|skip))$",
        Pattern.CASE_INSENSITIVE
    );

    static final Set<String> PROTECTED_PACKAGES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.eg.android.AlipayGphone",
        "com.unionpay",
        "com.unionpay.tsmservice.mi",
        "com.icbc",
        "com.chinamworld.main",
        "com.chinamworld.bocmbci",
        "com.yitong.mbank.psbc",
        "com.android.bankabc",
        "com.bankcomm.Bankcomm",
        "cmb.pb",
        "com.mipay.wallet",
        "com.azure.authenticator",
        "com.google.android.apps.authenticator2",
        "com.miui.securitymanager",
        "com.miui.packageinstaller",
        "com.android.settings",
        "com.android.systemui",
        "com.android.packageinstaller"
    )));

    static final class Entry {
        final String pkg;
        final String title;
        final Set<String> ids;
        final boolean verified;
        final boolean preferGesture;
        final String activity;

        Entry(String pkg, String title, String... ids) {
            this(pkg, title, false, false, null, ids);
        }

        Entry(String pkg, String title, boolean verified, String activity, String... ids) {
            this(pkg, title, verified, false, activity, ids);
        }

        Entry(String pkg, String title, boolean verified, boolean preferGesture, String activity, String... ids) {
            this.pkg = pkg;
            this.title = title;
            this.verified = verified;
            this.preferGesture = preferGesture;
            this.activity = activity;
            this.ids = new HashSet<String>(Arrays.asList(ids));
        }
    }

    static final List<Entry> BUILTIN_ENTRIES = Collections.unmodifiableList(Arrays.asList(
        new Entry(SELF, "内置测试页", true, null, "demo_skip"),
        new Entry("com.zhihu.android", "知乎", true, null, "btn_skip"),
        new Entry("tv.danmaku.bili", "哔哩哔哩", true, null, "count_down", "skip"),
        new Entry("com.netease.cloudmusic", "网易云音乐", false, null, "skipBtn", "tv_skip"),
        new Entry("com.ss.android.ugc.aweme", "抖音", false, null, "skip_btn", "tv_skip"),
        new Entry("com.smile.gifmaker", "快手", false, null, "splash_skip", "skip_view"),
        new Entry("com.xingin.xhs", "小红书", false, null, "tv_skip", "skip"),
        new Entry("com.sina.weibo", "微博", false, null, "tv_skip", "skip_btn"),
        new Entry("com.taobao.taobao", "淘宝", true, null, "tv_close", "skip_btn"),
        new Entry("com.jingdong.app.mall", "京东", false, null, "skip_btn", "tv_skip"),
        new Entry("com.xunmeng.pinduoduo", "拼多多", false, null, "tv_skip", "skip"),
        new Entry("com.sankuai.meituan", "美团", false, null, "skip_btn", "tv_skip"),
        new Entry("me.ele", "饿了么", false, null, "skip_btn", "tv_skip"),
        new Entry("com.baidu.netdisk", "百度网盘", true, true, null, "vilon_close_text", "tv_skip", "skip"),
        new Entry("com.autonavi.minimap", "高德地图", false, null, "skip_btn", "tv_skip"),
        new Entry("com.baidu.BaiduMap", "百度地图", false, null, "skip_btn", "tv_skip"),
        new Entry("ctrip.android.view", "携程旅行", false, null, "tv_skip", "skip_btn"),
        new Entry("com.MobileTicket", "铁路12306", false, null, "tv_skip", "skip"),
        new Entry("com.coolapk.market", "酷安", false, null, "tv_skip", "skip_view"),
        new Entry("com.ximalaya.ting.android", "喜马拉雅", false, null, "tv_skip", "skip"),
        new Entry("com.tencent.qqmusic", "QQ音乐", true, null, "skip_btn", "tv_skip"),
        new Entry("com.kugou.android", "酷狗音乐", false, null, "skip_btn", "tv_skip"),
        new Entry("com.qiyi.video", "爱奇艺", false, null, "skip_btn", "tv_skip"),
        new Entry("com.tencent.qqlive", "腾讯视频", false, null, "skip_btn", "tv_skip"),
        new Entry("com.youku.phone", "优酷视频", false, null, "skip_btn", "tv_skip"),
        new Entry("cn.wps.moffice_eng", "WPS Office", false, null, "tv_skip", "skip_btn")
    ));

    private static final Map<String, Entry> BUILTIN_MAP = new HashMap<String, Entry>();
    private static final Map<String, Entry> CUSTOM_MAP = new LinkedHashMap<String, Entry>();

    static {
        for (Entry e : BUILTIN_ENTRIES) {
            BUILTIN_MAP.put(e.pkg, e);
        }
    }

    static synchronized void loadCustomJson(String rawJson) {
        CUSTOM_MAP.clear();
        Map<String, Entry> parsed = parseCustom(rawJson);
        if (parsed != null) CUSTOM_MAP.putAll(parsed);
    }

    /**
     * 解析自定义规则 JSON（纯函数）。空串返回空表；格式错误返回 null。
     * 受保护包名和空包名直接丢弃；同包出现多次以最后一次为准。
     */
    static Map<String, Entry> parseCustom(String rawJson) {
        Map<String, Entry> out = new LinkedHashMap<String, Entry>();
        if (rawJson == null || rawJson.trim().isEmpty()) return out;
        Object parsed;
        try {
            parsed = Json.parse(rawJson);
        } catch (RuntimeException broken) {
            return null;
        }
        if (!(parsed instanceof List)) return null;
        for (Object item : (List<?>) parsed) {
            if (!(item instanceof Map)) continue;
            Map<?, ?> obj = (Map<?, ?>) item;
            String pkg = str(obj.get("pkg")).trim();
            if (pkg.isEmpty() || PROTECTED_PACKAGES.contains(pkg)) continue;
            String title = str(obj.get("title")).trim();
            Set<String> ids = new HashSet<String>();
            if (obj.get("ids") instanceof List) {
                for (Object id : (List<?>) obj.get("ids")) {
                    String s = str(id).trim();
                    if (!s.isEmpty()) ids.add(s);
                }
            }
            boolean verified = Boolean.TRUE.equals(obj.get("verified"));
            String activity = str(obj.get("activity")).trim();
            out.remove(pkg);
            out.put(pkg, new Entry(pkg, title.isEmpty() ? pkg : title, verified,
                activity.isEmpty() ? null : activity, ids.toArray(new String[ids.size()])));
        }
        return out;
    }

    /** 把规则序列化成导入页同款 JSON 数组；ids 排序，输出稳定。 */
    static String toJson(Collection<Entry> entries) {
        StringBuilder b = new StringBuilder("[");
        boolean first = true;
        for (Entry e : entries) {
            if (!first) b.append(',');
            first = false;
            b.append("{\"pkg\":").append(Json.quote(e.pkg));
            b.append(",\"title\":").append(Json.quote(e.title == null ? e.pkg : e.title));
            b.append(",\"ids\":[");
            boolean firstId = true;
            for (String id : new TreeSet<String>(e.ids)) {
                if (!firstId) b.append(',');
                firstId = false;
                b.append(Json.quote(id));
            }
            b.append("],\"verified\":").append(e.verified);
            if (e.activity != null) b.append(",\"activity\":").append(Json.quote(e.activity));
            b.append('}');
        }
        return b.append(']').toString();
    }

    /** 内置规则（含测试页）的只读列表。 */
    static List<Entry> builtins() {
        return BUILTIN_ENTRIES;
    }

    /** 当前生效的自定义规则（导入 + 录制），按写入顺序。 */
    static synchronized List<Entry> customEntries() {
        return Collections.unmodifiableList(new ArrayList<Entry>(CUSTOM_MAP.values()));
    }

    static synchronized boolean hasBuiltinRule(String pkg) {
        return BUILTIN_MAP.containsKey(pkg);
    }

    static synchronized boolean hasCustomRule(String pkg) {
        return CUSTOM_MAP.containsKey(pkg);
    }

    static boolean isProtected(String pkg) {
        return pkg != null && PROTECTED_PACKAGES.contains(pkg);
    }

    private static String str(Object o) {
        return o instanceof String ? (String) o : "";
    }

    static synchronized Entry find(String pkg) {
        if (CUSTOM_MAP.containsKey(pkg)) return CUSTOM_MAP.get(pkg);
        if (BUILTIN_MAP.containsKey(pkg)) return BUILTIN_MAP.get(pkg);
        return new Entry(pkg, pkg);
    }

    static synchronized boolean hasPresetRule(String pkg) {
        return CUSTOM_MAP.containsKey(pkg) || BUILTIN_MAP.containsKey(pkg);
    }

    static boolean dedicated(Entry r, String cls) {
        if (r.activity != null) {
            if (cls != null && cls.equals(r.activity)) return true;
            String tail = r.activity.startsWith(".") ? r.activity.substring(1) : r.activity;
            return cls != null && cls.equals(r.pkg + "." + tail);
        }
        if (SELF.equals(r.pkg)) return (SELF + ".DemoActivity").equals(cls);
        if (cls == null || !cls.startsWith(r.pkg + ".")) return false;
        String name = cls.toLowerCase(Locale.ROOT);
        return name.contains("splash") || name.contains("welcome")
            || name.contains("loading") || name.contains("adactivity");
    }

    private static final Pattern SKIP_EXCLUDED = Pattern.compile(
        "(跳过片头|跳过片尾|跳过片|跳过剧情|跳过动画|跳过引导|跳过此步|跳过绑定|跳过登录|跳过验证|跳過片頭|跳過片尾|skip\\s*intro|skip\\s*outro|skip\\s*recap|skip\\s*tutorial)",
        Pattern.CASE_INSENSITIVE
    );

    static boolean isExcludedSkipText(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        return !trimmed.isEmpty() && SKIP_EXCLUDED.matcher(trimmed).find();
    }

    static boolean skipText(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.length() > 14 || isExcludedSkipText(trimmed)) return false;
        return SKIP.matcher(trimmed).matches();
    }

    static boolean adText(String s) {
        if (s == null) return false;
        String trimmed = s.trim();
        return "广告".equals(trimmed) || "广告标识".equals(trimmed) || "廣告".equals(trimmed)
            || "商业推广".equals(trimmed) || "Advertisement".equalsIgnoreCase(trimmed)
            || "Ad".equalsIgnoreCase(trimmed);
    }

    static String localId(Entry r, String full) {
        if (full == null) return "";
        String prefix = r.pkg + ":id/";
        if (full.startsWith(prefix)) return full.substring(prefix.length());
        int slash = full.indexOf(":id/");
        return slash >= 0 ? full.substring(slash + 4) : full;
    }

    static boolean adContainer(String id) {
        if (id == null) return false;
        String lower = id.toLowerCase(Locale.ROOT);
        return !lower.contains("skip") && (lower.contains("splash_ad") || lower.contains("adsplash")
            || lower.contains("ad_splash") || lower.contains("splashadvert") || lower.contains("splash_container"));
    }

    private RuleBook() {}
}
