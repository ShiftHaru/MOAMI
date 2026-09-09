package dev.browserdownloader.probe;

import org.junit.Test;
import static org.junit.Assert.*;

public class XShareLinkTest {
    @Test public void shareTextAndBoundaries(){
        assertEquals("https://www.instagram.com/p/abc/",XShareLink.extract("설명 https://www.instagram.com/p/abc/?img_index=2&stkn=test"));
        assertEquals("https://x.com/name/status/123",XShareLink.extract("게시물 설명\nhttps://x.com/name/status/123?s=20"));
        assertEquals("https://x.com/name/status/123",XShareLink.extract("https://twitter.com/name/status/123"));
        for(String invalid:new String[]{null,"", "https://x.com.evil.test/name/status/123", "https://x.com@evil.test/name/status/123",
            "https://x.com/name/status/123 https://example.org", "https://x.com/name", "https://t.co/abc", "http://x.com/name/status/123"})
            assertEquals("",XShareLink.extract(invalid));
    }
    @Test public void previewTrustBoundary(){
        assertTrue(XThumbnail.permitted("https://pbs.twimg.com/media/a.jpg"));
        for(String invalid:new String[]{"https://pbs.twimg.com.evil/a", "http://pbs.twimg.com/a", "https://user@pbs.twimg.com/a", "https://pbs.twimg.com:443/a", "file:///a"})
            assertFalse(XThumbnail.permitted(invalid));
    }
}
