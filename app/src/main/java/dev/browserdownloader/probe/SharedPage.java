package dev.browserdownloader.probe;

import java.net.URI;
import java.util.Locale;

/** A one-shot share ticket. Full page URLs never leave process memory. */
final class SharedPage {
    static final SharedPage pending = new SharedPage();
    static final long TTL_MS = 120_000;
    private String url = "";
    private long started;

    synchronized boolean offer(String raw, long now) {
        clear();
        if (identity(raw).isEmpty()) return false;
        url = raw.trim(); started = now; return true;
    }
    synchronized String peek(long now) {
        if (now < started || now - started >= TTL_MS) clear();
        return url;
    }
    synchronized boolean matches(String current, long now) {
        String expected = identity(peek(now));
        return !expected.isEmpty() && expected.equals(identity(current));
    }
    synchronized boolean consume(String current, long now) {
        if (!matches(current, now)) return false;
        clear(); return true;
    }
    synchronized void clear() { url = ""; started = 0; }

    private static String identity(String raw) {
        if (PreviewRules.requestUrl(raw).isEmpty()) return "";
        URI uri = URI.create(raw.trim());
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if ((scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80)) port = -1;
        String path = uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        // Do not merge queries, fragments, encoded paths, redirects or HTTP/HTTPS.
        return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (port < 0 ? "" : ":" + port)
                + path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
    }
}
