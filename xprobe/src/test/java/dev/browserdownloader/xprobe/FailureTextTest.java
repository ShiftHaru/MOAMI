package dev.browserdownloader.xprobe;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class FailureTextTest {
    @Test public void neverEchoesRemoteCredentialsOrResponseBodies() {
        String fallback = FailureText.describe(new IOException());
        for (String message : new String[]{"https://media.example/a?token=secret", "Cookie: private",
                "<html>private response</html>", "authentication-required https://secret.example"})
            assertEquals(fallback, FailureText.describe(new IOException(message)));
    }
    @Test public void separatesAccessRestrictionFromThrottlingAndUnavailablePost() {
        String auth = FailureText.describe(new IOException("authentication-required"));
        String rate = FailureText.describe(new IOException("rate-limited"));
        String unavailable = FailureText.describe(new IOException("unavailable"));
        assertTrue(auth.contains("로그인"));
        assertTrue(rate.contains("한도"));
        assertTrue(unavailable.contains("접근"));
        assertNotEquals(auth, rate);
        assertNotEquals(rate, unavailable);
    }
    @Test public void timeoutIsNotReportedAsUserCancellation() {
        assertTrue(FailureText.describe(new java.net.SocketTimeoutException("secret")).contains("응답 시간"));
        assertTrue(FailureText.describe(new InterruptedException("secret")).contains("중지"));
    }
    @Test public void explainsInputAndValidationFailuresWithoutEchoingInput() {
        try { XLink.canonical("https://private.example/token"); fail(); }
        catch (IllegalArgumentException e) { assertTrue(FailureText.describe(e).contains("X 게시물")); }
        assertTrue(FailureText.describe(new IOException("사진 수신 크기 불일치")).contains("검증"));
        assertTrue(FailureText.describe(new IOException("다운로드 파일 쓰기 실패")).contains("저장 공간"));
    }
}
