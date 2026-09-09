package dev.browserdownloader.xprobe;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Validate media privately, then publish completed files through scoped storage. */
public final class XMedia {
    public static JSONArray summarize(JSONObject post) throws Exception {
        JSONArray items = post.getJSONArray("media"), result = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            JSONObject summary = new JSONObject().put("id", item.getString("id")).put("kind", item.getString("kind"))
                    .put("expectedWidth", item.optInt("expectedWidth")).put("expectedHeight", item.optInt("expectedHeight"));
            if (!"photo".equals(item.getString("kind"))) summary.put("mp4Candidates", candidateSummary(item));
            result.put(summary);
        }
        return result;
    }

    public static JSONObject downloadAll(Context context, JSONObject post, boolean convertGif) throws Exception {
        JSONArray media = post.getJSONArray("media"), results = new JSONArray();
        int failed = 0;
        boolean cancelled = false;
        for (int i = 0; i < media.length(); i++) {
            if (Thread.currentThread().isInterrupted()) { cancelled = true; break; }
            JSONObject item = media.getJSONObject(i);
            String kind = item.getString("kind"), id = item.getString("id");
            JSONObject result = null;
            try {
                if (kind.equals("photo")) result = downloadPhoto(context, item);
                else if (kind.equals("video") || kind.equals("animated_gif")) {
                    result = download(context, item, convertGif && kind.equals("animated_gif"));
                    File dir = new File(context.getNoBackupFilesDir(), "x-media");
                    try {
                        result.put("savedUri", PublicDownloads.publish(context, new File(dir, id + ".mp4"), "video/mp4"));
                        if (result.has("gif")) result.getJSONObject("gif").put("savedUri",
                                PublicDownloads.publish(context, new File(dir, id + ".converted.gif"), "image/gif"));
                    } catch (InterruptedException e) { throw e; }
                    catch (Exception e) { result.put("exportError", FailureText.describe(e)); }
                } else throw new IOException("미지원 미디어 유형");
                boolean complete = !result.has("gifError") && !result.has("exportError") && !result.optBoolean("cancelled");
                result.put("complete", complete).put("kind", kind);
                if (!complete && !result.optBoolean("cancelled")) failed++;
                results.put(result);
                if (result.optBoolean("cancelled") || Thread.currentThread().isInterrupted()) { cancelled = true; break; }
            } catch (Exception e) {
                if (e instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                    if (result == null) result = new JSONObject().put("mediaId", id);
                    results.put(result.put("complete", false).put("kind", kind).put("cancelled", true));
                    Thread.currentThread().interrupt(); cancelled = true; break;
                }
                failed++;
                results.put(new JSONObject().put("mediaId", id).put("kind", kind)
                        .put("complete", false).put("error", failureDescription(e)));
            }
        }
        return new JSONObject().put("postId", post.getString("postId")).put("items", results)
                .put("requested", media.length()).put("failed", failed).put("cancelled", cancelled)
                .put("unattempted", media.length() - results.length())
                .put("allSaved", media.length() > 0 && results.length() == media.length() && failed == 0 && !cancelled)
                .put("folder", "Download/BrowserDownloader");
    }

    private static JSONObject downloadPhoto(Context context, JSONObject item) throws Exception {
        String id = item.getString("id");
        if (!id.matches("[0-9-]{1,52}")) throw new IOException("미디어 식별자 형식 미확인");
        URI uri = URI.create(item.getString("url"));
        if (!"https".equals(uri.getScheme()) || !"pbs.twimg.com".equals(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1 || !uri.getPath().startsWith("/media/")
                || uri.getRawQuery() == null || !uri.getRawQuery().matches("format=(?:jpg|jpeg|png|webp)&name=orig"))
            throw new IOException("원본 사진 후보 주소 불일치");
        File dir = new File(context.getNoBackupFilesDir(), "x-media");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("저장 폴더 생성 실패");
        File partial = new File(dir, id + ".photo.partial");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15_000); connection.setReadTimeout(15_000);
        long start = android.os.SystemClock.elapsedRealtime();
        long total = 0;
        try {
            if (connection.getResponseCode() != 200) throw new IOException("사진 요청 실패");
            String type = connection.getContentType();
            String mime = type == null ? "" : type.split(";")[0].trim().toLowerCase(java.util.Locale.ROOT);
            String extension = switch (mime) {
                case "image/jpeg" -> "jpg";
                case "image/png" -> "png";
                case "image/webp" -> "webp";
                default -> throw new IOException("사진 응답 형식 불일치");
            };
            long expected = connection.getContentLengthLong();
            if (expected > 100L * 1024 * 1024) throw new IOException("사진 시험 한도 초과");
            try (java.io.InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[64 * 1024];
                for (int n; (n = input.read(buffer)) != -1;) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("사진 저장 중지");
                    total += n;
                    if (total > 100L * 1024 * 1024 || android.os.SystemClock.elapsedRealtime() - start > 180_000)
                        throw new IOException("사진 시험 한도 초과");
                    output.write(buffer, 0, n);
                }
            }
            if (expected >= 0 && total != expected) throw new IOException("사진 수신 크기 불일치");
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(partial.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || !mime.equals(bounds.outMimeType)
                    || (long) bounds.outWidth * bounds.outHeight > 100_000_000) throw new IOException("사진 디코딩 정보 불일치");
            android.graphics.Bitmap preview = android.graphics.ImageDecoder.decodeBitmap(
                    android.graphics.ImageDecoder.createSource(partial), (decoder, info, source) -> {
                        decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                        decoder.setTargetSampleSize(Math.max(1, Math.max(info.getSize().getWidth(), info.getSize().getHeight()) / 64));
                    });
            preview.recycle();
            String digest;
            try (java.io.InputStream input = new java.io.FileInputStream(partial)) { digest = PublicDownloads.sha256(input); }
            File target = new File(dir, id + "." + extension);
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            boolean original = bounds.outWidth == item.optInt("expectedWidth") && bounds.outHeight == item.optInt("expectedHeight");
            JSONObject result = new JSONObject().put("mediaId", id).put("width", bounds.outWidth)
                    .put("height", bounds.outHeight).put("bytes", total).put("sha256", digest)
                    .put("photoSaved", true).put("originalVerified", original)
                    .put("quality", original ? "X orig variant and original_info dimensions match" : "original candidate unverified");
            try { result.put("savedUri", PublicDownloads.publish(context, target, mime)); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result.put("cancelled", true).put("exportError", FailureText.describe(e));
            }
            catch (Exception e) { result.put("exportError", FailureText.describe(e)); }
            return result;
        } finally { connection.disconnect(); Files.deleteIfExists(partial.toPath()); }
    }

    private static List<Mp4Variant> candidates(JSONObject info) throws Exception {
        if (info.has("entries")) throw new IOException("복수 미디어 게시물은 후속 검증이 필요합니다.");
        JSONArray formats = info.optJSONArray("formats");
        List<Mp4Variant> result = new ArrayList<>();
        if (formats != null) for (int i = 0; i < formats.length(); i++) {
            JSONObject f = formats.getJSONObject(i);
            if (!"mp4".equals(f.optString("ext")) || !"https".equals(f.optString("protocol"))
                    || "none".equals(f.optString("vcodec")) || !Mp4Variant.permittedUrl(f.optString("url"))) continue;
            result.add(new Mp4Variant(f.optString("format_id"), f.getString("url"),
                    f.optInt("width"), f.optInt("height"), f.optDouble("tbr", 0)));
        }
        return result;
    }

    public static JSONArray candidateSummary(JSONObject info) throws Exception {
        JSONArray result = new JSONArray();
        for (Mp4Variant v : candidates(info)) result.put(new JSONObject().put("id", v.id())
                .put("width", v.width()).put("height", v.height()).put("bitrateKbps", v.bitrate()));
        return result;
    }

    public static JSONObject download(Context context, JSONObject info, boolean convertGif) throws Exception {
        String stage = "MP4 후보 선택";
        try {
        List<Mp4Variant> variants = candidates(info);
        Mp4Variant best = Mp4Variant.forDownload(variants, "animated_gif".equals(info.optString("kind")));
        stage = "MP4 저장 준비";
        String id = info.optString("id");
        if (!id.matches("[0-9-]{1,52}")) throw new IOException("미디어 식별자 형식 미확인");
        File dir = new File(context.getNoBackupFilesDir(), "x-media");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("저장 폴더 생성 실패");
        File target = new File(dir, id + ".mp4");
        File partial = new File(dir, id + ".mp4.partial");
        HttpURLConnection connection = (HttpURLConnection) URI.create(best.url()).toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(15_000);
        long total = 0;
        long start = android.os.SystemClock.elapsedRealtime();
        String hash;
        int width, height;
        try {
            stage = "MP4 서버 응답";
            if (connection.getResponseCode() != 200) throw new IOException("MP4 요청 실패: HTTP " + connection.getResponseCode());
            String type = connection.getContentType();
            if (type == null || !type.split(";")[0].trim().equalsIgnoreCase("video/mp4")) throw new IOException("MP4 응답 형식 불일치");
            long expected = connection.getContentLengthLong();
            if (expected > 500L * 1024 * 1024) throw new IOException("MP4 시험 한도 500 MiB 초과");
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            stage = "MP4 파일 수신";
            try (java.io.InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[64 * 1024];
                for (int n; (n = input.read(buffer)) != -1;) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("저장 중지");
                    if (android.os.SystemClock.elapsedRealtime() - start > 180_000) throw new IOException("MP4 저장 시험 시간 초과");
                    total += n;
                    if (total > 500L * 1024 * 1024) throw new IOException("MP4 시험 한도 500 MiB 초과");
                    digest.update(buffer, 0, n); output.write(buffer, 0, n);
                }
            }
            if (expected >= 0 && total != expected) throw new IOException("MP4 수신 크기 불일치");
            stage = "MP4 해상도·프레임 검증";
            try (Mp4Compatibility compatible = Mp4Compatibility.prepare(partial);
                 MediaMetadataRetriever decoder = new MediaMetadataRetriever()) {
                decoder.setDataSource(compatible.file().getPath());
                VideoMetadata.Info metadata = VideoMetadata.read(decoder, compatible.file(), false);
                width = metadata.width();
                height = metadata.height();
                if (width <= 0 || height <= 0 || (long) width * height > 100_000_000)
                    throw new IOException("MP4 픽셀 한도 초과");
                if (best.width() > 0 && best.height() > 0 && (width != best.width() || height != best.height()))
                    throw new IOException("선택 후보와 저장 파일의 해상도 불일치");
                android.graphics.Bitmap frame = decoder.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST);
                if (frame == null) throw new IOException("MP4 프레임 디코딩 실패");
                frame.recycle();
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b));
            hash = hex.toString();
            stage = "MP4 내부 파일 보존";
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            connection.disconnect();
            Files.deleteIfExists(partial.toPath());
        }
        JSONObject report = new JSONObject().put("mediaId", id).put("selectedFormat", best.id())
                .put("candidates", candidateSummary(info)).put("width", width).put("height", height)
                .put("bytes", total).put("sha256", hash).put("mp4Saved", true)
                .put("allMp4ResolutionsKnown", variants.stream().allMatch(v -> v.width() > 0 && v.height() > 0))
                .put("originalVerified", false).put("storage", "app-private/no-backup")
                .put("sourceType", info.optString("kind", "unconfirmed"));
        if (convertGif) {
            try {
                report.put("gif", GifConversion.convertForDownload(target, new File(dir, id + ".converted.gif")));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); report.put("gifError", "변환 중지; MP4 저장은 유지됨");
            } catch (Exception e) { report.put("gifError", FailureText.describe(e)); }
        }
        return report;
        } catch (InterruptedException e) { throw e; }
        catch (Exception e) { throw new MediaFailure(stage, e); }
    }
    static final class MediaFailure extends Exception {
        final String stage;
        MediaFailure(String stage, Exception cause) { super(cause); this.stage = stage; }
    }
    static String failureDescription(Exception error) {
        if (error instanceof MediaFailure failure)
            return failure.stage + " · " + FailureText.describe(failure.getCause())
                    + " [" + failure.getCause().getClass().getSimpleName() + "]";
        return FailureText.describe(error);
    }
}
