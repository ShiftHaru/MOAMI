package dev.browserdownloader.probe;

import java.net.URI;

/** Network address validation is separate from redacted diagnostic URLs. */
final class PreviewRules {
    static final long MAX_BYTES = 20L * 1024 * 1024;
    static final long CACHE_BYTES = 32L * 1024 * 1024;
    static String requestUrl(String value) {
        if (value == null || value.length() > 16384) return "";
        try {
            String trimmed = value.trim();
            URI uri = URI.create(trimmed);
            if ((!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getPort() > 65535 || uri.getPort() == 0) return "";
            int fragment = trimmed.indexOf('#');
            return fragment < 0 ? trimmed : trimmed.substring(0, fragment);
        } catch (Exception e) { return ""; }
    }
    static int sampleSize(int width, int height) {
        int sample = 1;
        while ((Math.max(width, height) + (long) sample - 1) / sample > 512) sample *= 2;
        return sample;
    }
    /** Ruliweb's own board_read.js maps dated /img/ and /mo/ images to /ori/.
     * Keep this host/path-specific: other sites and all query parameters stay untouched. */
    static String originalCandidate(String raw) {
        String url = requestUrl(raw);
        if (url.isEmpty()) return "";
        URI uri = URI.create(url);
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if (!(host.matches("i[0-9]+\\.ruliweb\\.com") || host.equals("img.ruliweb.com"))) return "";
        if (uri.getPort() != -1 && !(uri.getPort()==443 && uri.getScheme().equalsIgnoreCase("https"))
                && !(uri.getPort()==80 && uri.getScheme().equalsIgnoreCase("http"))) return "";
        String path = uri.getRawPath();
        if (!path.matches("/(?:img|mo|ori)/[0-9]{2}/[0-9]{2}/[0-9]{2}/[A-Za-z0-9_-]+\\.(?:webp|png|jpg|jpeg|gif)")) return "";
        if (path.startsWith("/ori/")) return url;
        int offset = url.indexOf('/', url.indexOf("://")+3);
        int prefix = path.startsWith("/img/") ? 5 : 4;
        return url.substring(0,offset) + "/ori/" + url.substring(offset+prefix);
    }
    static String saveUrl(String raw) {
        String candidate = originalCandidate(raw);
        return candidate.isEmpty() ? requestUrl(raw) : candidate;
    }
}
