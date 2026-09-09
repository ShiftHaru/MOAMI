package dev.browserdownloader.xprobe;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class MediaFailureTest {
    @Test public void reportsStageAndHttpStatusWithoutRemoteExceptionText() {
        String text = XMedia.failureDescription(new XMedia.MediaFailure("MP4 서버 응답",
                new IOException("MP4 요청 실패: HTTP 403")));
        assertTrue(text.contains("MP4 서버 응답")); assertTrue(text.contains("HTTP 403"));
        assertTrue(text.contains("IOException"));
        text = XMedia.failureDescription(new XMedia.MediaFailure("MP4 파일 수신",
                new IOException("https://video.twimg.com/file?token=fixture-secret")));
        assertTrue(text.contains("MP4 파일 수신")); assertFalse(text.contains("token"));
        assertFalse(text.contains("https://")); assertFalse(text.contains("fixture-secret"));
    }
}
