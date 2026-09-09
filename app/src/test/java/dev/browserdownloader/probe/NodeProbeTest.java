package dev.browserdownloader.probe;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class NodeProbeTest {
    @Test public void removesQueryAndFragmentFromDiagnosticReport() {
        assertEquals("https://example.org/image.png",
                NodeProbe.safeUrl("https://example.org/image.png?token=secret#private"));
    }
    @Test public void removesQueryAfterFragmentToo() {
        assertEquals("https://example.org/image.png",
                NodeProbe.safeUrl("https://example.org/image.png#private?token=secret"));
    }
    @Test public void preservesEscapedPathWithoutDecodingIt() {
        assertEquals("https://example.org/a%3Fb.png", NodeProbe.safeUrl("https://example.org/a%3Fb.png?q=1"));
    }
    @Test public void rejectsCredentialsInUrl() {
        assertEquals("", NodeProbe.safeUrl("https://user:password@example.org/image.png"));
    }
    @Test public void rejectsNonNetworkSchemesAndMalformedHosts() {
        for (String value : new String[]{"file:///private/image.png", "data:image/png;base64,SECRET",
                "javascript:alert(1)", "https:///no-host", "https://", "not a URL", "https://exa mple.org/a"})
            assertEquals(value, "", NodeProbe.safeUrl(value));
    }
    @Test public void acceptsLocalFixturePortAndTrimsWhitespace() {
        assertEquals("http://127.0.0.1:8787/images/original.png",
                NodeProbe.safeUrl("  http://127.0.0.1:8787/images/original.png  "));
    }
    @Test public void rejectsNullEmptyAndOversizedInput() {
        assertEquals("", NodeProbe.safeUrl(null));
        assertEquals("", NodeProbe.safeUrl(""));
        assertEquals("", NodeProbe.safeUrl("https://example.org/" + "a".repeat(16384)));
    }
}
