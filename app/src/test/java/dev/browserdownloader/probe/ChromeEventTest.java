package dev.browserdownloader.probe;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChromeEventTest {
    @Test public void missingPackageDoesNotCrashAndOnlyExactChromeMatches() {
        assertFalse(ChromeProbeService.isChrome(null));
        assertFalse(ChromeProbeService.isChrome(""));
        assertFalse(ChromeProbeService.isChrome("com.android.chrome.fake"));
        assertTrue(ChromeProbeService.isChrome("com.android.chrome"));
        assertTrue(ChromeProbeService.isChrome(new StringBuilder("com.android.chrome")));
    }
}
