package app.qi.qingtiao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RuleBookTest {
    @Test public void builtinEntriesHaveExpectedVerifiedFlags() {
        int verified = 0;
        int unverified = 0;
        for (RuleBook.Entry r : RuleBook.BUILTIN_ENTRIES) {
            if (RuleBook.SELF.equals(r.pkg)
                    || "tv.danmaku.bili".equals(r.pkg)
                    || "com.taobao.taobao".equals(r.pkg)
                    || "com.zhihu.android".equals(r.pkg)
                    || "com.baidu.netdisk".equals(r.pkg)
                    || "com.tencent.qqmusic".equals(r.pkg)) {
                assertTrue(r.pkg + " should be verified", r.verified);
                verified++;
            } else {
                assertFalse(r.pkg + " should be unverified", r.verified);
                unverified++;
            }
        }
        assertEquals(6, verified);
        assertEquals(20, unverified);
        assertTrue(RuleBook.find("com.baidu.netdisk").preferGesture);
        assertFalse(RuleBook.find("tv.danmaku.bili").preferGesture);
    }

    @Test public void protectedPackagesIncludeBanksWalletsAndAuthenticators() {
        assertTrue(RuleBook.isProtected("com.chinamworld.bocmbci"));
        assertTrue(RuleBook.isProtected("com.yitong.mbank.psbc"));
        assertTrue(RuleBook.isProtected("com.mipay.wallet"));
        assertTrue(RuleBook.isProtected("com.unionpay.tsmservice.mi"));
        assertTrue(RuleBook.isProtected("com.miui.securitymanager"));
        assertTrue(RuleBook.isProtected("com.azure.authenticator"));
    }

    @Test public void customRulesParseOptionalFields() {
        RuleBook.loadCustomJson("[{\"pkg\":\"com.demo.app\",\"title\":\"示例\",\"ids\":[\"tv_close\"],"
            + "\"verified\":true,\"activity\":\".SplashActivity\"}]");
        RuleBook.Entry e = RuleBook.find("com.demo.app");
        assertEquals("com.demo.app", e.pkg);
        assertEquals("示例", e.title);
        assertTrue(e.ids.contains("tv_close"));
        assertTrue(e.verified);
        assertEquals(".SplashActivity", e.activity);
    }

    @Test public void customRulesDefaultToUnverifiedWithoutActivity() {
        RuleBook.loadCustomJson("[{\"pkg\":\"com.demo.app\",\"ids\":[\"btn_skip\"]}]");
        RuleBook.Entry e = RuleBook.find("com.demo.app");
        assertFalse(e.verified);
        assertEquals(null, e.activity);
    }

    @Test public void customRulesProtectedPackagesAreRejected() {
        RuleBook.loadCustomJson("[{\"pkg\":\"com.tencent.mm\",\"ids\":[\"skip\"]}]");
        RuleBook.Entry e = RuleBook.find("com.tencent.mm");
        assertTrue(e.ids.isEmpty());
        assertFalse(e.verified);
    }

    @Test public void laterLoadReplacesEarlierCustomRules() {
        RuleBook.loadCustomJson("[{\"pkg\":\"com.a.b\",\"ids\":[\"x\"]}]");
        RuleBook.loadCustomJson("[{\"pkg\":\"com.c.d\",\"ids\":[\"y\"]}]");
        assertTrue(RuleBook.find("com.a.b").ids.isEmpty());
        assertTrue(RuleBook.find("com.c.d").ids.contains("y"));
    }

    @Test public void customEntryActivityCountsAsDedicated() {
        RuleBook.loadCustomJson("[{\"pkg\":\"com.demo.app\",\"ids\":[\"btn_skip\"],"
            + "\"activity\":\"com.demo.app.SplashActivity\"}]");
        RuleBook.Entry e = RuleBook.find("com.demo.app");
        assertTrue(RuleBook.dedicated(e, "com.demo.app.SplashActivity"));
        assertFalse(RuleBook.dedicated(e, "com.demo.app.MainActivity"));
    }
}
