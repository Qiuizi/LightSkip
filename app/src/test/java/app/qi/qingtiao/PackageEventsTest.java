package app.qi.qingtiao;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PackageEventsTest {
    @Test public void freshInstallAndUninstall() {
        assertEquals(PackageEvents.Change.ADDED, PackageEvents.decide(PackageEvents.ADDED, false, "a.b"));
        assertEquals(PackageEvents.Change.REMOVED, PackageEvents.decide(PackageEvents.REMOVED, false, "a.b"));
    }

    @Test public void upgradeCountsOnceAsUpdated() {
        assertEquals(PackageEvents.Change.IGNORE, PackageEvents.decide(PackageEvents.REMOVED, true, "a.b"));
        assertEquals(PackageEvents.Change.IGNORE, PackageEvents.decide(PackageEvents.ADDED, true, "a.b"));
        assertEquals(PackageEvents.Change.UPDATED, PackageEvents.decide(PackageEvents.REPLACED, true, "a.b"));
    }

    @Test public void missingPackageOrUnknownActionIgnored() {
        assertEquals(PackageEvents.Change.IGNORE, PackageEvents.decide(PackageEvents.ADDED, false, ""));
        assertEquals(PackageEvents.Change.IGNORE, PackageEvents.decide("android.intent.action.BOOT_COMPLETED", false, "a.b"));
    }
}
