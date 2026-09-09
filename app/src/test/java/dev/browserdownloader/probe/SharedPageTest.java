package dev.browserdownloader.probe;

import org.junit.Test;
import static org.junit.Assert.*;

public class SharedPageTest {
    @Test public void sharedTicketIsExactSingleUseAndExpires() {
        SharedPage page = new SharedPage();
        assertTrue(page.offer("https://EXAMPLE.org:443/p?q=a%2Fb#view", 10));
        for (String other : new String[]{"", "https://example.org/p?q=other#view", "https://example.org/p?q=a%2Fb",
                "https://example.org/p?q=a/b#view", "http://example.org/p?q=a%2Fb#view", "https://evil.example/p?q=a%2Fb#view"})
            assertFalse(page.consume(other, 11));
        assertTrue(page.consume("https://example.org/p?q=a%2Fb#view", 12));
        assertFalse(page.consume("https://example.org/p?q=a%2Fb#view", 13));
        assertTrue(page.offer("https://example.org", 100));
        assertTrue(page.matches("https://example.org/", 100 + SharedPage.TTL_MS - 1));
        assertFalse(page.matches("https://example.org/", 100 + SharedPage.TTL_MS));
        assertEquals("", page.peek(100 + SharedPage.TTL_MS));
    }
    @Test public void invalidReplacementCancellationAndProcessLossNeverReuseAnOldTicket() {
        SharedPage page = new SharedPage();
        for (String bad : new String[]{null, "text https://example.org", "https://example.org\nhttps://other.org",
                "file:///tmp/image", "https://user:secret@example.org", "https://example.org:99999/"}) {
            page.offer("https://example.org", 10);
            assertFalse(page.offer(bad, 11)); assertEquals("", page.peek(12));
        }
        page.offer("https://example.org", 10); page.clear(); assertFalse(page.matches("https://example.org", 11));
        page.offer("https://example.org", 10); assertEquals("", new SharedPage().peek(11));
        assertEquals("", page.peek(9));
    }
}
