package dev.browserdownloader.xprobe;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class VideoMetadataTest {
    @Test public void invalidMetadataRequestsFallbackInsteadOfThrowingOrOverflowing() {
        for (String value : new String[]{null, "", "null", "512.0", "unknown", "-1", "0", "2147483648", "9223372036854775808"})
            assertEquals(0, VideoMetadata.positive(value, Integer.MAX_VALUE));
        assertEquals(512, VideoMetadata.positive(" 512 ", Integer.MAX_VALUE));
        assertEquals(1920, VideoMetadata.positive("1920", Long.MAX_VALUE));
        assertEquals(0, VideoMetadata.millis(-1));
        assertEquals(0, VideoMetadata.millis(0));
        assertEquals(1, VideoMetadata.millis(1));
        assertEquals(1920, VideoMetadata.millis(1_920_000));
        assertEquals(30_001, VideoMetadata.millis(30_000_001));
        assertEquals(9_223_372_036_854_776L, VideoMetadata.millis(Long.MAX_VALUE));
        assertTrue(FailureText.describe(new IOException("MP4 해상도 정보 없음")).contains("기기에서 영상 정보"));
        assertFalse(FailureText.describe(new IOException("MP4 해상도 정보 없음")).contains("저장 공간"));
    }
}
