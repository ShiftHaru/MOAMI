package dev.browserdownloader.xprobe;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Private decoding copy only. Never changes the MP4 which is published to Downloads. */
public final class Mp4Compatibility implements AutoCloseable {
    private static final String[] PATH = {"moov", "trak", "mdia", "minf", "stbl", "ctts"};
    private final File original, file;
    private Mp4Compatibility(File original, File file) { this.original = original; this.file = file; }
    public File file() { return file; }

    public static Mp4Compatibility prepare(File source) throws IOException, InterruptedException {
        List<Long> flags = new ArrayList<>();
        try (RandomAccessFile input = new RandomAccessFile(source, "r")) {
            if (input.length() > 500L * 1024 * 1024) throw new IOException("MP4 시험 한도 500 MiB 초과");
            scan(input, 0, input.length(), 0, flags, new int[]{0}, null);
        }
        if (flags.isEmpty()) return new Mp4Compatibility(source, source);
        File copy = File.createTempFile("mp4-decode-", ".mp4", source.getParentFile());
        boolean ready = false;
        try {
            try (InputStream input = new FileInputStream(source); OutputStream output = new FileOutputStream(copy)) {
                byte[] buffer = new byte[65536];
                for (int n; (n = input.read(buffer)) != -1;) { interrupted(); output.write(buffer, 0, n); }
            }
            try (RandomAccessFile output = new RandomAccessFile(copy, "rw")) {
                for (long position : flags) { interrupted(); output.seek(position); output.writeInt(0); }
            }
            ready = true;
            return new Mp4Compatibility(source, copy);
        } finally { if (!ready) Files.deleteIfExists(copy.toPath()); }
    }

    /** Read track handler types from the actual container; silence in an audio track still counts. */
    public static boolean hasAudioTrack(File source) throws IOException, InterruptedException {
        List<String> handlers = new ArrayList<>();
        try (RandomAccessFile input = new RandomAccessFile(source, "r")) {
            if (input.length() > 500L * 1024 * 1024) throw new IOException("MP4 시험 한도 500 MiB 초과");
            scan(input, 0, input.length(), 0, new ArrayList<>(), new int[]{0}, handlers);
        }
        if (!handlers.contains("vide")) throw new IOException("MP4 오디오 트랙 판별 실패");
        return handlers.contains("soun");
    }

    private static void scan(RandomAccessFile input, long start, long end, int depth,
                             List<Long> flags, int[] boxes, List<String> handlers) throws IOException, InterruptedException {
        for (long p = start; p < end;) {
            interrupted();
            if (++boxes[0] > 10000 || end - p < 8) throw new IOException("MP4 구조 검증 실패");
            input.seek(p);
            long size = Integer.toUnsignedLong(input.readInt());
            byte[] type = new byte[4]; input.readFully(type);
            String name = new String(type, java.nio.charset.StandardCharsets.US_ASCII);
            int header = 8;
            if (size == 1) { if (end - p < 16) throw new IOException("MP4 구조 검증 실패"); size = input.readLong(); header = 16; }
            if (size == 0) size = end - p;
            if (size < header || size > end - p) throw new IOException("MP4 구조 검증 실패");
            if (handlers != null && depth == 3 && name.equals("hdlr")) {
                if(size < header + 24) throw new IOException("MP4 오디오 트랙 판별 실패");
                input.seek(p + header);
                if(input.readInt() != 0) throw new IOException("MP4 오디오 트랙 판별 실패");
                input.skipBytes(4);
                byte[] handler = new byte[4]; input.readFully(handler);
                String handlerType = new String(handler, java.nio.charset.StandardCharsets.US_ASCII);
                if(!handlerType.equals("vide") && !handlerType.equals("soun"))
                    throw new IOException("MP4 오디오 트랙 판별 실패");
                handlers.add(handlerType);
            }
            if (name.equals(PATH[depth])) {
                if (depth < PATH.length - 1) {
                    int before = handlers == null ? 0 : handlers.size();
                    scan(input, p + header, p + size, depth + 1, flags, boxes, handlers);
                    if(handlers != null && depth == 1 && handlers.size() != before + 1)
                        throw new IOException("MP4 오디오 트랙 판별 실패");
                }
                else {
                    if (size < header + 8) throw new IOException("MP4 구조 검증 실패");
                    input.seek(p + header);
                    int versionFlags = input.readInt();
                    long count = Integer.toUnsignedLong(input.readInt());
                    if (count > 1_000_000 || size - header - 8 != count * 8) throw new IOException("MP4 구조 검증 실패");
                    // Observed Twitter CTTS v0 with reserved flags=1. Do not reinterpret signed v1 offsets.
                    if (versionFlags == 1) flags.add(p + header);
                }
            }
            p += size;
        }
    }
    private static void interrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("작업 중지");
    }
    @Override public void close() throws IOException { if (!file.equals(original)) Files.deleteIfExists(file.toPath()); }
}
