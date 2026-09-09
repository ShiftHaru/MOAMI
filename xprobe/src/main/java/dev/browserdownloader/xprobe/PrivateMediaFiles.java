package dev.browserdownloader.xprobe;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;

/** Recover only known unfinished files, once before any X work in this process. */
final class PrivateMediaFiles {
    private static boolean recovered;
    static synchronized void recoverOnce(File directory) throws IOException {
        if (!recovered) { clean(directory); recovered = true; }
    }
    static void clean(File directory) throws IOException {
        if (!directory.exists()) return;
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("내부 임시 파일 복구 실패");
        for (File file : files) {
            String name = file.getName();
            boolean temporary = name.matches("[0-9-]{1,52}\\.(mp4|photo|converted\\.gif)\\.partial")
                    || name.matches("mp4-decode-[0-9]+\\.mp4");
            if (temporary && Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                try { Files.delete(file.toPath()); }
                catch (IOException e) { throw new IOException("내부 임시 파일 복구 실패", e); }
            }
        }
    }
}
