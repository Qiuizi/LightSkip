package app.qi.qingtiao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppInfoTest {
    @Before public void clearCustom() { RuleBook.loadCustomJson(""); }

    private static AppInfo user(String pkg, String label) {
        return new AppInfo(pkg, label, false, false, false, true);
    }

    private static List<String> pkgs(List<AppInfo> list) {
        List<String> out = new ArrayList<String>();
        for (AppInfo a : list) out.add(a.pkg);
        return out;
    }

    @Test public void listableExcludesSystemHomeImeProtectedAndNonLaunchable() {
        List<AppInfo> all = Arrays.asList(
            user("com.zhihu.android", "知乎"),
            new AppInfo("com.miui.gallery", "相册", true, false, false, true),
            new AppInfo("com.miui.home", "系统桌面", false, true, false, true),
            new AppInfo("com.sohu.inputmethod.sogou", "搜狗输入法", false, false, true, true),
            user("com.tencent.mm", "微信"),
            new AppInfo("com.service.only", "后台服务", false, false, false, false),
            new AppInfo(RuleBook.SELF, "轻跳", false, false, false, true));
        assertEquals(Arrays.asList(RuleBook.SELF, "com.zhihu.android"), pkgs(AppInfo.listableOf(all)));
        assertEquals(Arrays.asList("com.tencent.mm"), pkgs(AppInfo.lockedOf(all)));
    }

    @Test public void sortPutsRuleAppsFirstThenCollatorOrder() {
        List<AppInfo> all = new ArrayList<AppInfo>(Arrays.asList(
            user("com.z.app", "阿里云盘"),
            user("com.zhihu.android", "知乎"),
            new AppInfo(RuleBook.SELF, "轻跳", false, false, false, true),
            user("tv.danmaku.bili", "哔哩哔哩"),
            user("com.b.app", "百度")));
        AppInfo.sort(all);
        assertEquals(RuleBook.SELF, all.get(0).pkg);
        assertEquals(new java.util.HashSet<String>(Arrays.asList("tv.danmaku.bili", "com.zhihu.android")),
            new java.util.HashSet<String>(pkgs(all.subList(1, 3))));
        assertEquals(new java.util.HashSet<String>(Arrays.asList("com.z.app", "com.b.app")),
            new java.util.HashSet<String>(pkgs(all.subList(3, 5))));
        // 组内顺序交给 Collator(Locale.CHINA)：安卓上是 ICU 拼音序，桌面 JVM 不是，这里只断言与 Collator 一致。
        java.text.Collator zh = java.text.Collator.getInstance(java.util.Locale.CHINA);
        assertTrue(zh.compare(all.get(1).label, all.get(2).label) <= 0);
        assertTrue(zh.compare(all.get(3).label, all.get(4).label) <= 0);
    }

    @Test public void filterMatchesLabelOrPackageIgnoringCase() {
        List<AppInfo> all = Arrays.asList(user("tv.danmaku.bili", "哔哩哔哩"), user("com.zhihu.android", "知乎"));
        assertEquals(Arrays.asList("com.zhihu.android"), pkgs(AppInfo.filter(all, "知")));
        assertEquals(Arrays.asList("tv.danmaku.bili"), pkgs(AppInfo.filter(all, "DANMAKU")));
        assertEquals(2, AppInfo.filter(all, "  ").size());
    }

    @Test public void ruleTagFollowsCustomRulesLive() {
        AppInfo a = user("com.new.app", "新应用");
        assertEquals("智能识别", a.ruleTag());
        assertFalse(a.hasCustomRule());
        RuleBook.loadCustomJson("[{\"pkg\":\"com.new.app\",\"ids\":[\"skip\"],\"verified\":true}]");
        assertEquals("专属规则", a.ruleTag());
        assertTrue(a.hasCustomRule());
        assertTrue(a.verified());
        assertTrue(user("com.zhihu.android", "知乎").hasBuiltinRule());
    }

    @Test public void blankLabelFallsBackToPackage() {
        assertEquals("com.x", new AppInfo("com.x", " ", false, false, false, true).label);
    }
}
