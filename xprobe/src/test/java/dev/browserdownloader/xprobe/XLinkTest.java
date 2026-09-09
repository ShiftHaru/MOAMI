package dev.browserdownloader.xprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class XLinkTest {
    @Test public void removesTrackingAndPreservesMediaIndex() {
        assertEquals("https://x.com/example/status/123/photo/2",
                XLink.canonical("https://twitter.com/example/status/123/photo/2?s=20#unused"));
    }
    @Test public void acceptsSharedWebPost() {
        assertEquals("https://x.com/i/web/status/123", XLink.canonical("https://x.com/i/web/status/123"));
    }
    @Test public void rejectsUntrustedHostsAndOptions() {
        for (String input : new String[]{"--exec=sh", "https://x.com.evil.test/a/status/1",
                "https://x.com@evil.test/a/status/1", "https://user@x.com/a/status/1",
                "http://x.com/a/status/1", "https://x.com:443/a/status/1", "https://x.com/a/status/1%2f..",
                "https://x.com/a", "file:///status/1", "https://x.com/a/status/1/video/9"}) {
            assertThrows(input, IllegalArgumentException.class, () -> XLink.canonical(input));
        }
    }
}
