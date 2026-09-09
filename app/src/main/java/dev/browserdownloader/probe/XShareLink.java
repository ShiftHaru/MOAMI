package dev.browserdownloader.probe;

import dev.browserdownloader.xprobe.XLink;
import java.util.regex.Pattern;

final class XShareLink {
    static String extract(String text) {
        if (text == null || text.length() > 8192) return "";
        var links = Pattern.compile("https?://[^\\s<>\\\"]+", Pattern.CASE_INSENSITIVE).matcher(text);
        if (!links.find()) return "";
        String candidate = links.group();
        if (links.find()) return "";
        try { return XLink.canonical(candidate); } catch (IllegalArgumentException invalid) { return ""; }
    }
}
