package app.qi.qingtiao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class CustomRulesTest {
    @Before public void clearCustom() { RuleBook.loadCustomJson(""); }

    private static final String TWO = "[{\"pkg\":\"com.a\",\"title\":\"A\",\"ids\":[\"x\"]},"
        + "{\"pkg\":\"com.b\",\"title\":\"B\",\"ids\":[\"y\"],\"activity\":\".Splash\"}]";

    @Test public void upsertOverridesSamePackageAndKeepsOthers() {
        RuleBook.Entry e = new RuleBook.Entry("com.a", "A2", true, "com.a.SplashActivity", "tv_skip");
        String json = CustomRules.upsert(TWO, e);
        Map<String, RuleBook.Entry> m = RuleBook.parseCustom(json);
        assertEquals(2, m.size());
        RuleBook.Entry a = m.get("com.a");
        assertEquals("A2", a.title);
        assertTrue(a.verified);
        assertTrue(a.ids.contains("tv_skip"));
        assertFalse("同包覆盖，旧 ID 不保留", a.ids.contains("x"));
        assertEquals("com.a.SplashActivity", a.activity);
        assertEquals(".Splash", m.get("com.b").activity);
    }

    @Test public void upsertIntoEmptyCreatesArray() {
        String json = CustomRules.upsert("", new RuleBook.Entry("com.n", "N", true, null, "skip"));
        assertEquals("[{\"pkg\":\"com.n\",\"title\":\"N\",\"ids\":[\"skip\"],\"verified\":true}]", json);
    }

    @Test public void upsertRejectsProtectedPackage() {
        assertNull(CustomRules.upsert(TWO, new RuleBook.Entry("com.tencent.mm", "微信", true, null, "skip")));
    }

    @Test public void brokenExistingJsonIsNotOverwritten() {
        assertNull(CustomRules.upsert("[{\"pkg\":", new RuleBook.Entry("com.n", "N", true, null, "skip")));
        assertNull(CustomRules.remove("{not json", "com.a"));
        assertNull(RuleBook.parseCustom("{\"pkg\":\"com.a\"}"));
    }

    @Test public void removeDeletesOnlyThatPackage() {
        Map<String, RuleBook.Entry> m = RuleBook.parseCustom(CustomRules.remove(TWO, "com.a"));
        assertEquals(1, m.size());
        assertTrue(m.containsKey("com.b"));
    }

    @Test public void toJsonRoundTripsThroughLoader() {
        String json = CustomRules.upsert(TWO, new RuleBook.Entry("com.c", "引号\"测试", false, null, "b", "a"));
        RuleBook.loadCustomJson(json);
        List<RuleBook.Entry> custom = RuleBook.customEntries();
        assertEquals(3, custom.size());
        assertEquals("com.a", custom.get(0).pkg);
        assertEquals("引号\"测试", RuleBook.find("com.c").title);
        assertTrue(RuleBook.hasCustomRule("com.c"));
        assertFalse(RuleBook.hasBuiltinRule("com.c"));
    }

    @Test public void builtinsExposeAllEntriesReadOnly() {
        assertEquals(26, RuleBook.builtins().size());
        assertTrue(RuleBook.hasBuiltinRule("tv.danmaku.bili"));
        assertTrue(RuleBook.isProtected("com.tencent.mm"));
        try {
            RuleBook.builtins().clear();
            throw new AssertionError("builtins() 应该只读");
        } catch (UnsupportedOperationException expected) { }
    }
}
