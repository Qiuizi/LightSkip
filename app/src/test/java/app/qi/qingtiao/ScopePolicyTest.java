package app.qi.qingtiao;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScopePolicyTest {
    private static final String BILI = "tv.danmaku.bili";
    private static final String ZHIHU = "com.zhihu.android";
    private static final String WECHAT = "com.tencent.mm";
    private static final Set<String> NONE = Collections.<String>emptySet();

    private static Set<String> set(String... s) { return new HashSet<String>(Arrays.asList(s)); }

    @Before public void clearCustom() { RuleBook.loadCustomJson(""); }

    @Test public void protectedPackageRejectedEvenInAllModeAndWhenPicked() {
        assertFalse(ScopePolicy.shouldHandle(WECHAT, true, true, set(WECHAT), NONE, false, false, false));
        assertFalse(ScopePolicy.shouldHandle(WECHAT, true, false, set(WECHAT), NONE, false, false, false));
    }

    @Test public void pickedModeOnlyHandlesPickedPackages() {
        assertTrue(ScopePolicy.shouldHandle(BILI, true, false, set(BILI), NONE, false, false, false));
        assertFalse(ScopePolicy.shouldHandle(ZHIHU, true, false, set(BILI), NONE, false, false, false));
    }

    @Test public void emptyPickSetHandlesNothing() {
        assertFalse(ScopePolicy.shouldHandle(BILI, true, false, NONE, NONE, false, false, false));
        assertFalse(ScopePolicy.shouldHandle(RuleBook.SELF, true, false, NONE, NONE, false, false, false));
    }

    @Test public void allModeSkipsLauncherSystemImeAndExcluded() {
        assertTrue(ScopePolicy.shouldHandle(ZHIHU, true, true, NONE, NONE, false, false, false));
        assertFalse("桌面", ScopePolicy.shouldHandle("com.miui.home", true, true, NONE, NONE, true, false, false));
        assertFalse("系统应用", ScopePolicy.shouldHandle("com.miui.gallery", true, true, NONE, NONE, false, true, false));
        assertFalse("输入法", ScopePolicy.shouldHandle("com.sohu.inputmethod.sogou", true, true, NONE, NONE, false, false, true));
        assertFalse("用户排除", ScopePolicy.shouldHandle(ZHIHU, true, true, NONE, set(ZHIHU), false, false, false));
    }

    @Test public void selfHandledInAllModeAndWhenPicked() {
        assertTrue(ScopePolicy.shouldHandle(RuleBook.SELF, true, true, NONE, NONE, false, false, false));
        assertTrue(ScopePolicy.shouldHandle(RuleBook.SELF, true, false, set(RuleBook.SELF), NONE, false, false, false));
    }

    @Test public void disabledHandlesNothing() {
        assertFalse(ScopePolicy.shouldHandle(BILI, false, true, set(BILI), NONE, false, false, false));
        assertFalse(ScopePolicy.shouldHandle(null, true, true, NONE, NONE, false, false, false));
    }

    @Test public void recordingTargetIsListenedEvenWhenDisabledButNeverIfProtected() {
        assertTrue(ScopePolicy.shouldListen(ZHIHU, false, false, NONE, NONE, false, false, false, ZHIHU));
        assertFalse(ScopePolicy.shouldListen(WECHAT, true, true, NONE, NONE, false, false, false, WECHAT));
        assertFalse(ScopePolicy.shouldListen(BILI, false, false, NONE, NONE, false, false, false, ZHIHU));
    }

    @Test public void serviceFilterAllModeListensToEveryPackage() {
        ScopePolicy.Filter f = ScopePolicy.serviceFilter(true, true, set(BILI), null);
        assertTrue(f.listen);
        assertNull(f.packages);
    }

    @Test public void serviceFilterPickedModeDropsProtectedAndSorts() {
        ScopePolicy.Filter f = ScopePolicy.serviceFilter(true, false, set(ZHIHU, WECHAT, BILI), null);
        assertTrue(f.listen);
        assertArrayEquals(new String[]{ZHIHU, BILI}, f.packages);
    }

    @Test public void serviceFilterEmptyOrDisabledStopsEvents() {
        ScopePolicy.Filter empty = ScopePolicy.serviceFilter(true, false, NONE, null);
        assertFalse(empty.listen);
        assertArrayEquals(new String[]{RuleBook.SELF}, empty.packages);
        ScopePolicy.Filter off = ScopePolicy.serviceFilter(false, true, set(BILI), null);
        assertFalse(off.listen);
    }

    @Test public void serviceFilterAddsRecordingTargetTemporarily() {
        ScopePolicy.Filter f = ScopePolicy.serviceFilter(true, false, set(BILI), ZHIHU);
        assertArrayEquals(new String[]{ZHIHU, BILI}, f.packages);
        ScopePolicy.Filter off = ScopePolicy.serviceFilter(false, false, set(BILI), ZHIHU);
        assertTrue(off.listen);
        assertArrayEquals(new String[]{ZHIHU}, off.packages);
        ScopePolicy.Filter prot = ScopePolicy.serviceFilter(false, false, NONE, WECHAT);
        assertFalse(prot.listen);
    }

    @Test public void serviceFilterIsIdempotentAcrossTenToggles() {
        Set<String> picked = set(BILI, ZHIHU);
        for (int i = 0; i < 10; i++) {
            boolean all = i % 2 == 0;
            ScopePolicy.Filter f = ScopePolicy.serviceFilter(true, all, picked, null);
            if (all) assertNull(f.packages);
            else assertArrayEquals(new String[]{ZHIHU, BILI}, f.packages);
        }
        assertEquals(2, picked.size());
    }

    @Test public void sanitizeDropsBlankAndProtected() {
        assertEquals(set(BILI), ScopePolicy.sanitize(Arrays.asList(BILI, " ", "", null, WECHAT)));
    }

    @Test public void recommendedIsInstalledRuleAppsVerifiedFirstWithoutProtectedOrSelf() {
        RuleBook.loadCustomJson("[{\"pkg\":\"com.demo.verified\",\"ids\":[\"btn_skip\"],\"verified\":true}]");
        List<String> rec = ScopePolicy.recommended(Arrays.asList(
            BILI, "com.sina.weibo", WECHAT, RuleBook.SELF, "com.no.rule", "com.demo.verified"));
        assertEquals(Arrays.asList("com.demo.verified", BILI, "com.sina.weibo"), rec);
    }

    @Test public void pickedModeStillRejectsLauncherSystemAndIme() {
        assertFalse(ScopePolicy.shouldHandle("com.miui.home", true, false, set("com.miui.home"), NONE, true, false, false));
        assertFalse(ScopePolicy.shouldHandle("com.miui.personalassistant", true, false, set("com.miui.personalassistant"), NONE, false, true, false));
        assertFalse(ScopePolicy.shouldHandle("com.tencent.wetype", true, false, set("com.tencent.wetype"), NONE, false, false, true));
    }

    @Test public void prunePickedDropsProtectedAndUnlistedStalePackages() {
        Set<String> raw = set(BILI, "com.yitong.mbank.psbc", "com.miui.personalassistant", "com.tencent.wetype", RuleBook.SELF);
        Set<String> allowed = set(BILI, ZHIHU, RuleBook.SELF);
        assertEquals(set(BILI, RuleBook.SELF), ScopePolicy.prunePicked(raw, allowed));
    }
}