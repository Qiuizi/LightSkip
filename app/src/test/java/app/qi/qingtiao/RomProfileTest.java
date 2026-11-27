package app.qi.qingtiao;

import org.junit.Test;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class RomProfileTest {
    private static RomProfile.PropLookup props(final Map<String, String> map) {
        return new RomProfile.PropLookup() {
            @Override public String get(String key) {
                String v = map.get(key);
                return v == null ? "" : v;
            }
        };
    }

    private static RomProfile.PropLookup emptyProps() {
        return props(Collections.<String, String>emptyMap());
    }

    @Test
    public void detectsXiaomiAndRedmi() {
        RomProfile mi = RomProfile.detect("Xiaomi", "Xiaomi", emptyProps());
        assertEquals(RomProfile.Brand.XIAOMI, mi.brand);
        assertTrue(mi.autostartTargets.length >= 1);

        RomProfile redmi = RomProfile.detect("Xiaomi", "Redmi", emptyProps());
        assertEquals(RomProfile.Brand.XIAOMI, redmi.brand);
    }

    @Test
    public void detectsHonorBeforeHuawei() {
        Map<String, String> m = new HashMap<String, String>();
        m.put("ro.build.version.magic", "8.0");
        RomProfile honor = RomProfile.detect("HONOR", "HONOR", props(m));
        assertEquals(RomProfile.Brand.HONOR, honor.brand);
        assertTrue(honor.autostartTargets.length >= 1);
    }

    @Test
    public void detectsHuaweiHarmony() {
        Map<String, String> m = new HashMap<String, String>();
        m.put("hw_sc.build.platform.version", "4.0.0");
        RomProfile hw = RomProfile.detect("HUAWEI", "HUAWEI", props(m));
        assertEquals(RomProfile.Brand.HUAWEI, hw.brand);
    }

    @Test
    public void detectsOppoOnePlusRealme() {
        assertEquals(RomProfile.Brand.OPPO, RomProfile.detect("OPPO", "OPPO", emptyProps()).brand);
        assertEquals(RomProfile.Brand.OPPO, RomProfile.detect("OnePlus", "OnePlus", emptyProps()).brand);
        assertEquals(RomProfile.Brand.OPPO, RomProfile.detect("realme", "realme", emptyProps()).brand);
    }

    @Test
    public void detectsVivoAndIqoo() {
        assertEquals(RomProfile.Brand.VIVO, RomProfile.detect("vivo", "vivo", emptyProps()).brand);
        assertEquals(RomProfile.Brand.VIVO, RomProfile.detect("vivo", "iQOO", emptyProps()).brand);
    }

    @Test
    public void detectsSamsung() {
        RomProfile sam = RomProfile.detect("samsung", "samsung", emptyProps());
        assertEquals(RomProfile.Brand.SAMSUNG, sam.brand);
    }

    @Test
    public void fallsBackToStockAndroid() {
        RomProfile pixel = RomProfile.detect("Google", "google", emptyProps());
        assertEquals(RomProfile.Brand.STOCK, pixel.brand);
        assertEquals(0, pixel.autostartTargets.length);
        assertFalse(pixel.autostartHint.isEmpty());
    }
}