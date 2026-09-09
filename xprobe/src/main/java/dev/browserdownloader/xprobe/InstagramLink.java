package dev.browserdownloader.xprobe;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

public final class InstagramLink {
    public static boolean host(String host) {
        return host != null && Set.of("instagram.com", "www.instagram.com", "m.instagram.com").contains(host.toLowerCase(Locale.ROOT));
    }
    public static String canonical(String value) {
        try {
            URI u = new URI(value);
            if (!"https".equalsIgnoreCase(u.getScheme()) || !host(u.getHost()) || u.getUserInfo()!=null || u.getPort()!=-1
                    || !u.getPath().matches("/(p|reel|reels)/[A-Za-z0-9_-]{1,28}/?")) throw new IllegalArgumentException();
            return "https://www.instagram.com" + u.getPath().replaceFirst("^/reels/", "/reel/").replaceAll("/$", "") + "/";
        } catch (Exception e) { throw new IllegalArgumentException("공개 Instagram 게시물 또는 릴스 주소를 입력하세요."); }
    }
    public static boolean mediaUrl(String value) {
        try {
            URI u = new URI(value); String h = u.getHost();
            return "https".equals(u.getScheme()) && h != null && u.getUserInfo()==null && u.getPort()==-1
                    && (h.endsWith(".cdninstagram.com") || h.endsWith(".fbcdn.net"));
        } catch (Exception e) { return false; }
    }
}
