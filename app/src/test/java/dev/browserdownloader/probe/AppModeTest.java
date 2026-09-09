package dev.browserdownloader.probe;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppModeTest {
    @Test public void consentAndNotificationRequiredWithoutShareAccessibility() {
        assertFalse(AppMode.ready(false,true,true));
        assertFalse(AppMode.ready(true,true,false));
        assertTrue(AppMode.ready(true,true,true));
        assertEquals(BuildConfig.SHARE_ONLY,AppMode.ready(true,false,true));
    }
    @Test public void shareRejectsBrowserAndSpoofedLinks() {
        assertTrue(AppMode.accepts("https://x.com/name/status/123"));
        assertTrue(AppMode.accepts("https://www.instagram.com/reel/abc/"));
        for(String input:new String[]{"https://example.org/photo.jpg", "https://x.com.evil.test/name/status/123",
                "file:///photo.jpg", "https://instagram.com/accounts/login/", "https://example.org https://x.com/name/status/123"})
            assertEquals(!BuildConfig.SHARE_ONLY,AppMode.accepts(input));
    }
}
