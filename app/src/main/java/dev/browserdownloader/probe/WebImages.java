package dev.browserdownloader.probe;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Static public HTML only. Never executes page scripts or imports browser credentials. */
final class WebImages {
    record Item(String url, String description) { }
    record Result(String page, List<Item> items, boolean limited) { }
    private static final int MAX_HTML = 5 * 1024 * 1024;
    static Result parse(String html, String page) {
        Document document = Jsoup.parse(html, page);
        LinkedHashMap<String, Item> found = new LinkedHashMap<>();
        boolean limited = false;
        for (Element image : document.select("img")) {
            String best = ""; double score = -1;
            List<Element> sources = new ArrayList<>();
            sources.add(image);
            if (image.parent() != null && image.parent().normalName().equals("picture")) sources.addAll(image.parent().select("source"));
            for (Element source : sources) for (String attr : List.of("srcset", "data-srcset")) {
                for (String candidate : source.attr(attr).split(",")) {
                    String[] parts = candidate.trim().split("\\s+");
                    if (parts.length == 0) continue;
                    double rank = 1;
                    if (parts.length > 1) {
                        String descriptor = parts[parts.length - 1];
                        try { rank = Double.parseDouble(descriptor.substring(0, descriptor.length() - 1));
                            if (descriptor.endsWith("x")) rank *= 10000;
                            else if (!descriptor.endsWith("w")) continue;
                        } catch (RuntimeException invalid) { continue; }
                    }
                    String url = resolve(source.baseUri(), parts[0]);
                    if (!url.isEmpty() && Double.isFinite(rank) && rank > score) { best = url; score = rank; }
                }
            }
            if (best.isEmpty()) for (String attr : List.of("data-src", "src")) {
                best = resolve(image.baseUri(), image.attr(attr)); if (!best.isEmpty()) break;
            }
            if (best.isEmpty() || found.containsKey(best)) continue;
            if (found.size() == 500) { limited = true; break; }
            // Do not persist arbitrary HTML attributes; descriptions are display-only.
            found.put(best, new Item(best, image.attr("alt").substring(0, Math.min(200, image.attr("alt").length()))));
        }
        return new Result(page, List.copyOf(found.values()), limited);
    }
    private static String resolve(String base, String value) {
        if (value.isBlank()) return "";
        try { return PreviewRules.requestUrl(new URI(base).resolve(value.trim()).toString()); }
        catch (Exception invalid) { return ""; }
    }
    static Result fetch(String input) throws Exception {
        String url = PreviewRules.requestUrl(input);
        if (url.isEmpty()) throw new IOException("HTTP(S) 주소 하나를 입력하세요.");
        final HttpURLConnection[] active = new HttpURLConnection[1];
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        ScheduledFuture<?> expiry = timer.schedule(() -> { synchronized(active) { if(active[0] != null) active[0].disconnect(); } }, 30, TimeUnit.SECONDS);
        try {
            for (int redirect = 0; redirect <= 3; redirect++) {
                check(deadline);
                HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
                synchronized(active) { active[0] = connection; }
                try {
                    connection.setInstanceFollowRedirects(false);
                    connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
                    connection.setRequestProperty("User-Agent", "BrowserDownloader/0.22");
                    connection.setRequestProperty("Accept-Encoding", "identity");
                    int code = connection.getResponseCode();
                    if (Set.of(301,302,303,307,308).contains(code)) {
                        String location = connection.getHeaderField("Location");
                        if (redirect == 3 || location == null) throw new IOException("리디렉션 제한 초과");
                        url = resolve(url, location); if (url.isEmpty()) throw new IOException("지원하지 않는 이동 주소");
                        continue;
                    }
                    if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
                    String type = Objects.toString(connection.getContentType(), "").toLowerCase(Locale.ROOT);
                    if (type.startsWith("image/")) return new Result(url, List.of(new Item(url, "이미지 주소")), false);
                    if (!type.startsWith("text/html") && !type.startsWith("application/xhtml+xml")) throw new IOException("HTML 또는 이미지 주소를 입력하세요.");
                    if (connection.getContentLengthLong() > MAX_HTML) throw new IOException("HTML 5MiB 제한 초과");
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream stream = connection.getInputStream()) {
                        byte[] buffer = new byte[16384];
                        for (int n; (n=stream.read(buffer)) != -1;) {
                            check(deadline);
                            if (bytes.size() + n > MAX_HTML) throw new IOException("HTML 5MiB 제한 초과");
                            bytes.write(buffer,0,n);
                        }
                    }
                    String charset = null;
                    for (String part : type.split(";")) if (part.trim().startsWith("charset=")) charset = part.trim().substring(8).replace("\"", "");
                    Document doc = Jsoup.parse(new ByteArrayInputStream(bytes.toByteArray()), charset, url);
                    check(deadline);
                    return parse(doc.outerHtml(), url);
                } finally { connection.disconnect(); }
            }
            throw new IOException("리디렉션 제한 초과");
        } finally { expiry.cancel(false); timer.shutdownNow(); }
    }
    private static void check(long deadline) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
        if (System.nanoTime() > deadline) throw new IOException("30초 시간 제한 초과");
    }
}
