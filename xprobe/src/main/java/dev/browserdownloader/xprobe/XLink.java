package dev.browserdownloader.xprobe;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/** Accept only a public post URL. Never pass arbitrary input as a command option. */
public final class XLink {
    private static final Pattern POST = Pattern.compile(
            "/(?:[A-Za-z0-9_]{1,15}|i/web)/status/([0-9]{1,25})(?:/(?:photo|video)/[1-4])?/?");

    public static String canonical(String input) {
        try {
            URI uri = new URI(input.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || uri.getPort() != -1 || !java.util.Set.of("x.com", "www.x.com",
                    "twitter.com", "www.twitter.com", "mobile.twitter.com").contains(host)
                    || !POST.matcher(uri.getRawPath()).matches()) {
                throw new IllegalArgumentException("공개 X 게시물의 https 주소를 입력하세요.");
            }
            return "https://x.com" + uri.getRawPath();
        } catch (java.net.URISyntaxException | NullPointerException e) {
            throw new IllegalArgumentException("올바른 X 게시물 주소가 아닙니다.", e);
        }
    }
}
