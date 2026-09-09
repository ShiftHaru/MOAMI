package dev.browserdownloader.xprobe;

import java.net.URI;
import java.util.List;

public record Mp4Variant(String id, String url, int width, int height, double bitrate) {
    public static boolean permittedUrl(String url) {
        try {
            URI uri = new URI(url);
            return "https".equals(uri.getScheme()) && "video.twimg.com".equals(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1;
        } catch (Exception e) { return false; }
    }

    public static Mp4Variant best(List<Mp4Variant> variants) {
        return variants.stream().filter(v -> v.width > 0 && v.height > 0 && permittedUrl(v.url))
                .max(java.util.Comparator.comparingLong((Mp4Variant v) -> (long) v.width * v.height)
                        .thenComparingDouble(v -> v.bitrate)).orElseThrow(() ->
                        new IllegalArgumentException("해상도를 확인한 공개 MP4 후보가 없습니다."));
    }
    public static Mp4Variant forDownload(List<Mp4Variant> variants, boolean animatedGif) {
        // A single GIF MP4 may omit dimensions; verify its actual file before publishing.
        if (animatedGif && variants.size() == 1 && permittedUrl(variants.get(0).url)) return variants.get(0);
        return best(variants);
    }
}
