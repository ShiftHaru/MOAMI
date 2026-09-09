package dev.browserdownloader.probe;

import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import android.media.MediaMetadataRetriever;
import android.media.MediaExtractor;
import java.io.*;
import java.net.*;

/** Opt-in check of the user-reported public GIF; private temporary files only. */
final class XGifDeviceCheck {
    static void run(Instrumentation test) {
        Bundle result = new Bundle();
        File file = new File(test.getTargetContext().getCacheDir(), "xgif-device-check.mp4");
        StringBuilder report = new StringBuilder();
        int code = Activity.RESULT_CANCELED;
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("https://video.twimg.com/tweet_video/HRmW_qlaUAAvnoj.mp4").openConnection();
            connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
            try {
                report.append("HTTP ").append(connection.getResponseCode()).append(" type=").append(connection.getContentType()).append('\n');
                try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(file)) {
                    byte[] buffer = new byte[8192]; int total = 0;
                    for (int n; (n = input.read(buffer)) != -1;) {
                        total += n; if (total > 1024 * 1024) throw new IOException("fixture size limit");
                        output.write(buffer, 0, n);
                    }
                }
            } finally { connection.disconnect(); }
            report.append("bytes=").append(file.length()).append('\n');
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            for (int i=0; i<Math.min(32,bytes.length);i++) report.append(String.format("%02x",bytes[i]));
            report.append('\n');
            try (dev.browserdownloader.xprobe.Mp4Compatibility compatible = dev.browserdownloader.xprobe.Mp4Compatibility.prepare(file)) {
            File remux = compatible.file();
            if (remux.exists()) {
                try (MediaMetadataRetriever decoder = new MediaMetadataRetriever()) {
                    decoder.setDataSource(remux.getPath());
                    report.append("REMUX ").append(decoder.extractMetadata(18)).append('x').append(decoder.extractMetadata(19))
                        .append(" duration=").append(decoder.extractMetadata(9)).append('\n');
                    android.graphics.Bitmap frame = decoder.getFrameAtTime(0);
                    report.append("REMUX frame=").append(frame == null ? "null" : frame.getWidth()+"x"+frame.getHeight()).append('\n');
                    if(frame != null) frame.recycle();
                }
            }
            try (MediaMetadataRetriever decoder = new MediaMetadataRetriever()) {
                decoder.setDataSource(file.getPath());
                report.append("MMR ").append(decoder.extractMetadata(18)).append('x').append(decoder.extractMetadata(19))
                    .append(" duration=").append(decoder.extractMetadata(9)).append('\n');
                android.graphics.Bitmap frame = decoder.getFrameAtTime(0);
                report.append("frame=").append(frame == null ? "null" : frame.getWidth()+"x"+frame.getHeight()).append('\n');
                if (frame != null) frame.recycle();
            }
            }
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource(file.getPath());
                report.append("tracks=").append(extractor.getTrackCount()).append('\n');
                for(int i=0;i<extractor.getTrackCount();i++) report.append(extractor.getTrackFormat(i)).append('\n');
            } finally { extractor.release(); }
            org.json.JSONObject saved = new dev.browserdownloader.xprobe.NativeProbe(test.getTargetContext())
                .downloadX("https://x.com/GiFShitpost/status/2097106892585918695", true);
            report.append("DOWNLOAD ").append(saved).append('\n');
            if (!saved.getBoolean("allSaved")) throw new IOException("download failed");
            org.json.JSONObject item = saved.getJSONArray("items").getJSONObject(0);
            File dir = new File(test.getTargetContext().getNoBackupFilesDir(), "x-media");
            File mp4 = new File(dir, item.getString("mediaId")+".mp4");
            if (!java.util.Arrays.equals(bytes, java.nio.file.Files.readAllBytes(mp4.toPath()))) throw new IOException("MP4 bytes changed");
            android.net.Uri gifUri = android.net.Uri.parse(item.getJSONObject("gif").getString("savedUri"));
            android.graphics.drawable.Drawable gif = android.graphics.ImageDecoder.decodeDrawable(
                android.graphics.ImageDecoder.createSource(test.getTargetContext().getContentResolver(), gifUri));
            if (!(gif instanceof android.graphics.drawable.AnimatedImageDrawable)) throw new IOException("not animated GIF");
            report.append("PASS: preserved MP4 bytes, published animated GIF ").append(gif.getIntrinsicWidth()).append('x').append(gif.getIntrinsicHeight()).append('\n');
            org.json.JSONObject repeated = new dev.browserdownloader.xprobe.NativeProbe(test.getTargetContext())
                .downloadX("https://x.com/GiFShitpost/status/2097106892585918695", true);
            if (!repeated.getBoolean("allSaved")) throw new IOException("repeat download failed");
            org.json.JSONObject repeatItem=repeated.getJSONArray("items").getJSONObject(0);
            if (!item.getString("savedUri").equals(repeatItem.getString("savedUri"))) throw new IOException("repeat MP4 duplicated");
            if (!gifUri.toString().equals(repeatItem.getJSONObject("gif").getString("savedUri"))) throw new IOException("repeat converted GIF duplicated");
            if (!repeatItem.getJSONObject("gif").getBoolean("reusedConversion")) throw new IOException("repeat conversion not reused");
            report.append("PASS: repeated MP4/GIF download returned same URIs\n");
            for (File candidate : dir.listFiles()) if(candidate.getName().startsWith("mp4-decode-") || candidate.getName().endsWith(".partial")) throw new IOException("temporary file remained");
            code = Activity.RESULT_OK;
        } catch (Exception e) { report.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()); }
        finally { file.delete(); }
        result.putString("report", report.toString()); test.finish(code, result);
    }
}
