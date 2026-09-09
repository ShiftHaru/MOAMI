package dev.browserdownloader.probe;

import org.junit.Test;
import static org.junit.Assert.*;

public class DrawerTargetTest {
    @Test public void onlyExactTargetPackagesCanShowTheirDrawer() {
        assertEquals(DrawerTarget.CHROME, DrawerTarget.select(DrawerTarget.CHROME, true, false, false));
        assertEquals("", DrawerTarget.select(DrawerTarget.X, true, false, false));
        assertEquals(DrawerTarget.X, DrawerTarget.select(DrawerTarget.X, true, true, false));
        for (String other : new String[]{null, "", "com.twitter.android.fake", "com.android.chrome.beta",
                "dev.browserdownloader.xprobe", "com.android.settings"})
            assertEquals("", DrawerTarget.select(other, true, true, false));
    }
    @Test public void stopOrLockBlocksBothTargetsEvenWithXConsent() {
        for (String target : new String[]{DrawerTarget.CHROME, DrawerTarget.X}) {
            assertEquals("", DrawerTarget.select(target, false, true, false));
            assertEquals("", DrawerTarget.select(target, true, true, true));
        }
    }
}
