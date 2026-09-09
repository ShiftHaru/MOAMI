package dev.browserdownloader.probe;

import org.junit.Test;
import static org.junit.Assert.*;

public class PreviewRulesTest {
    @Test public void ruliwebSaveUsesSiteOriginalRouteButPreviewRetainsDetectedRoute() {
        String detected="https://i1.ruliweb.com/img/26/09/08/abc123.webp";
        assertEquals("https://i1.ruliweb.com/ori/26/09/08/abc123.webp", PreviewRules.saveUrl(detected));
        assertEquals(detected,PreviewRules.requestUrl(detected));
        assertEquals("https://i2.ruliweb.com/ori/26/09/08/abc123.png", PreviewRules.saveUrl("https://i2.ruliweb.com/mo/26/09/08/abc123.png"));
    }
    @Test public void originalCandidatePreservesEncodedQueryAndIsIdempotent() {
        String source="https://img.ruliweb.com/img/26/09/08/abc.jpg?value=a%2Fb&key=fixture#view";
        String expected="https://img.ruliweb.com/ori/26/09/08/abc.jpg?value=a%2Fb&key=fixture";
        assertEquals(expected, PreviewRules.saveUrl(source));
        assertEquals(expected, PreviewRules.saveUrl(expected));
        assertEquals("https://img.ruliweb.com/ori/26/09/08/abc.jpg",NodeProbe.safeUrl(expected));
    }
    @Test public void unknownOrDeceptiveHostsAndPathsNeverGetRewritten() {
        for (String url : new String[]{"https://example.org/img/26/09/08/abc.webp",
                "https://i1.ruliweb.com.evil.example/img/26/09/08/abc.webp", "https://ruliweb.com/img/26/09/08/abc.webp",
                "https://i1.ruliweb.com:8443/img/26/09/08/abc.webp", "https://i1.ruliweb.com/logo/img/icon.png",
                "https://i1.ruliweb.com/img/26/09/08/%2e%2e.webp"}) {
            assertEquals("",PreviewRules.originalCandidate(url)); assertEquals(url,PreviewRules.saveUrl(url));
        }
        assertEquals("",PreviewRules.saveUrl("https://user:secret@i1.ruliweb.com/img/26/09/08/abc.webp"));
    }
    @Test public void requestPreservesQueryButReportNeverDoes() {
        String raw = "https://example.org/a.png?size=large&token=secret#fragment";
        assertEquals("https://example.org/a.png?size=large&token=secret", PreviewRules.requestUrl(raw));
        assertEquals("https://example.org/a.png", NodeProbe.safeUrl(raw));
    }
    @Test public void rejectsCredentialsSchemesAndMalformedPorts() {
        for (String raw : new String[]{"https://user:pass@example.org/a", "file:///a", "data:image/png,x", "blob:https://example.org/1", "https://example.org:99999/a"})
            assertEquals("", PreviewRules.requestUrl(raw));
        assertEquals("http://127.0.0.1:8787/a?x=1", PreviewRules.requestUrl("http://127.0.0.1:8787/a?x=1"));
    }
    @Test public void decodingNeverRequiresALongerSideAbove512() {
        for (int size : new int[]{1,512,513,4096,100000,Integer.MAX_VALUE}) {
            int sample = PreviewRules.sampleSize(size, 100);
            assertTrue((size + (long) sample - 1) / sample <= 512);
        }
    }
    @Test public void descriptionsDoNotReintroduceQueryCredentials() {
        assertEquals("이미지 https://example.org/a.png 및 https://example.org/b", NodeProbe.safeText(
                "이미지 https://example.org/a.png?token=secret#private 및 https://example.org/b?q=private"));
    }
}
