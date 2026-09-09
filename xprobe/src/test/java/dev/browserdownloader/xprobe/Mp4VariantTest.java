package dev.browserdownloader.xprobe;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class Mp4VariantTest {
    @Test public void onlyASingleTrustedGifCanUseUnknownDimensions() {
        Mp4Variant gif = new Mp4Variant("gif", "https://video.twimg.com/tweet_video/fixture.mp4", 0, 0, 0);
        assertEquals(gif, Mp4Variant.forDownload(List.of(gif), true));
        assertThrows(IllegalArgumentException.class, () -> Mp4Variant.forDownload(List.of(gif), false));
        assertThrows(IllegalArgumentException.class, () -> Mp4Variant.forDownload(List.of(gif, gif), true));
        assertThrows(IllegalArgumentException.class, () -> Mp4Variant.forDownload(List.of(
                new Mp4Variant("bad", "https://other.example/gif.mp4", 0, 0, 0)), true));
    }
    @Test public void selectsPixelsBeforeBitrate() {
        Mp4Variant small = new Mp4Variant("small", "https://video.twimg.com/a.mp4", 640, 360, 9999);
        Mp4Variant large = new Mp4Variant("large", "https://video.twimg.com/b.mp4", 1920, 1080, 1000);
        assertEquals(large, Mp4Variant.best(List.of(small, large)));
    }
    @Test public void rejectsUnknownResolutionAndOtherHosts() {
        assertThrows(IllegalArgumentException.class, () -> Mp4Variant.best(List.of(
                new Mp4Variant("unknown", "https://video.twimg.com/a.mp4", 0, 0, 9999),
                new Mp4Variant("other", "https://example.com/a.mp4", 4000, 4000, 9999))));
    }
    @Test public void rejectsAuthorityTricks() {
        for (String url : List.of("https://user@video.twimg.com/a", "http://video.twimg.com/a",
                "https://video.twimg.com.evil.test/a", "https://video.twimg.com:443/a", "file:///video.twimg.com/a"))
            assertFalse(url, Mp4Variant.permittedUrl(url));
    }
}
