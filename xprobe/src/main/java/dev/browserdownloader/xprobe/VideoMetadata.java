package dev.browserdownloader.xprobe;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import java.io.File;
import java.io.IOException;

/** Metadata from the downloaded file only; never substitutes server-advertised dimensions. */
final class VideoMetadata {
    record Info(int width, int height, long durationMs) { }

    static Info read(MediaMetadataRetriever decoder, File file, boolean needDuration) throws IOException {
        int width = (int) positive(decoder.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH), Integer.MAX_VALUE);
        int height = (int) positive(decoder.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT), Integer.MAX_VALUE);
        long duration = positive(decoder.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION), Long.MAX_VALUE);
        if (width == 0 || height == 0 || (needDuration && duration == 0)) {
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource(file.getPath());
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat track = extractor.getTrackFormat(i);
                    String mime = track.getString(MediaFormat.KEY_MIME);
                    if (mime == null || !mime.startsWith("video/")) continue;
                    if (width == 0) width = track.getInteger(MediaFormat.KEY_WIDTH, 0);
                    if (height == 0) height = track.getInteger(MediaFormat.KEY_HEIGHT, 0);
                    if (duration == 0) duration = millis(track.getLong(MediaFormat.KEY_DURATION, 0));
                    break;
                }
            } catch (IOException | RuntimeException e) {
                throw new IOException("MP4 영상 트랙 정보 읽기 실패", e);
            } finally { extractor.release(); }
        }
        if (width <= 0 || height <= 0) throw new IOException("MP4 해상도 정보 없음");
        if (needDuration && duration <= 0) throw new IOException("MP4 재생 시간 정보 없음");
        return new Info(width, height, duration);
    }

    static long positive(String value, long maximum) {
        if (value == null) return 0;
        try {
            long number = Long.parseLong(value.trim());
            return number > 0 && number <= maximum ? number : 0;
        } catch (NumberFormatException e) { return 0; }
    }

    static long millis(long microseconds) {
        // Round up without overflow, preserving the 30-second conversion boundary.
        return microseconds <= 0 ? 0 : microseconds / 1000 + (microseconds % 1000 == 0 ? 0 : 1);
    }
}
