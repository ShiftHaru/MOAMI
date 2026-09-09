package dev.browserdownloader.probe;

import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Read-only bounded comparison input. No files, URIs, or content are persisted. */
final class FileFingerprint {
    record Data(byte[] bytes, String sha256) { }
    static Data read(InputStream input, long maxBytes, long deadlineNanos) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[16384];
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            if (System.nanoTime() >= deadlineNanos) throw new IOException("시간 제한 초과");
            int count = input.read(buffer);
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            if (System.nanoTime() >= deadlineNanos) throw new IOException("시간 제한 초과");
            if (count < 0) break;
            if ((long) output.size() + count > maxBytes) throw new IOException("파일 크기 제한 초과");
            output.write(buffer, 0, count); digest.update(buffer, 0, count);
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", value & 255));
        return new Data(output.toByteArray(), hash.toString());
    }
}
