package dev.browserdownloader.xprobe;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.SystemClock;
import com.squareup.gifencoder.GifEncoder;
import com.squareup.gifencoder.ImageOptions;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** G3 trial setting: full pixel dimensions, 10 fps, opaque GIF, up to 30 seconds/1080p. */
public final class GifConversion {
    /** Reuse only a conversion tied to these exact input bytes and settings. */
    public static JSONObject convertForDownload(File source, File destination) throws Exception {
        String sourceHash = hash(source);
        android.util.AtomicFile metadata = new android.util.AtomicFile(new File(destination.getPath()+".json"));
        try {
            JSONObject cached = new JSONObject(new String(metadata.readFully(), java.nio.charset.StandardCharsets.UTF_8));
            if (cached.optInt("settingsVersion") == 1 && sourceHash.equals(cached.optString("sourceSha256"))
                    && destination.isFile() && cached.optString("outputSha256").equals(hash(destination))) {
                return cached.getJSONObject("result").put("reusedConversion", true).put("elapsedMs", 0);
            }
        } catch (InterruptedException e) { throw e; }
        catch (Exception ignored) { /* Missing/corrupt metadata requires a fresh conversion. */ }
        JSONObject result = convert(source, destination).put("reusedConversion", false);
        JSONObject cached = new JSONObject().put("settingsVersion", 1).put("sourceSha256", sourceHash)
                .put("outputSha256", hash(destination)).put("result", result);
        FileOutputStream output = metadata.startWrite();
        try {
            output.write(cached.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            metadata.finishWrite(output);
        } catch (Exception e) { metadata.failWrite(output); throw e; }
        return result;
    }
    private static String hash(File file) throws Exception {
        try (java.io.InputStream input = new java.io.FileInputStream(file)) { return PublicDownloads.sha256(input); }
    }
    public static JSONObject convert(File source, File destination) throws Exception {
        if (source.getCanonicalFile().equals(destination.getCanonicalFile())) throw new IOException("입력 파일 덮어쓰기 금지");
        File partial = new File(destination.getPath() + ".partial");
        long start = SystemClock.elapsedRealtime();
        try (Mp4Compatibility compatible = Mp4Compatibility.prepare(source);
             MediaMetadataRetriever decoder = new MediaMetadataRetriever()) {
            decoder.setDataSource(compatible.file().getPath());
            VideoMetadata.Info metadata = VideoMetadata.read(decoder, compatible.file(), true);
            long duration = metadata.durationMs();
            if (duration <= 0 || duration > 30_000) throw new IOException("현재 GIF 검증 범위는 30초 이하입니다.");
            int sourceWidth = metadata.width();
            int sourceHeight = metadata.height();
            if (sourceWidth <= 0 || sourceHeight <= 0 || (long) sourceWidth * sourceHeight > 1920L * 1080)
                throw new IOException("현재 GIF 검증 범위는 1080p 픽셀 수 이하입니다.");
            int width = 0, height = 0, frames = 0;
            try (FileOutputStream output = new FileOutputStream(partial)) {
                GifEncoder encoder = null;
                for (long time = 0; time < duration; time += 100) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("변환 중지");
                    if (SystemClock.elapsedRealtime() - start > 180_000) throw new IOException("GIF 변환 시간 한도 초과");
                    Bitmap frame = decoder.getFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST);
                    if (frame == null) throw new IOException("영상 프레임 읽기 실패");
                    try {
                        if (encoder == null) {
                            width = frame.getWidth(); height = frame.getHeight();
                            if ((long) width * height > 1920L * 1080) throw new IOException("현재 GIF 검증 범위는 1080p 픽셀 수 이하입니다.");
                            encoder = new GifEncoder(output, width, height, 0);
                        }
                        if (frame.getWidth() != width || frame.getHeight() != height) throw new IOException("프레임 크기 변경");
                        int[] pixels = new int[width * height];
                        frame.getPixels(pixels, 0, width, 0, 0, width, height);
                        // Fixed 256-color trial palette avoids per-pixel nearest-color searches.
                        // Color loss is explicit; source MP4 is retained unchanged.
                        for (int i = 0; i < pixels.length; i++) {
                            int rgb = pixels[i];
                            int r = (((rgb >>> 16) & 255) * 7 + 127) / 255 * 255 / 7;
                            int g = (((rgb >>> 8) & 255) * 7 + 127) / 255 * 255 / 7;
                            int b = ((rgb & 255) * 3 + 127) / 255 * 255 / 3;
                            pixels[i] = (r << 16) | (g << 8) | b;
                        }
                        encoder.addImage(pixels, width,
                                new ImageOptions().setDelay(Math.min(100, duration - time), TimeUnit.MILLISECONDS));
                        frames++;
                        if (partial.length() > 100L * 1024 * 1024) throw new IOException("GIF 시험 파일 한도 100 MiB 초과");
                    } finally { frame.recycle(); }
                }
                encoder.finishEncoding();
            }
            java.nio.file.Files.move(partial.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return new JSONObject().put("width", width).put("height", height).put("frames", frames)
                    .put("durationMs", duration).put("samplingFps", 10).put("palette", "RGB332-no-dithering")
                    .put("converted", true)
                    .put("originalFile", false).put("elapsedMs", SystemClock.elapsedRealtime() - start);
        } finally { java.nio.file.Files.deleteIfExists(partial.toPath()); }
    }
}
