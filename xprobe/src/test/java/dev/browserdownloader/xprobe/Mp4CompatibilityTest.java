package dev.browserdownloader.xprobe;

import org.junit.Test;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class Mp4CompatibilityTest {
    private byte[] box(String type, byte[] payload) {
        return ByteBuffer.allocate(payload.length+8).putInt(payload.length+8)
            .put(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII)).put(payload).array();
    }
    private byte[] fixture(int flags, int count) {
        byte[] data=box("ctts", ByteBuffer.allocate(16).putInt(flags).putInt(count).putInt(1).putInt(6).array());
        for(String type:new String[]{"stbl","minf","mdia","trak","moov"}) data=box(type,data);
        return data;
    }
    @Test public void changesOnlyReservedFlagsInDisposableCopy() throws Exception {
        File source=File.createTempFile("mp4-test-", ".mp4");
        try {
            byte[] bytes=fixture(1,1); Files.write(source.toPath(),bytes);
            File copy;
            try(Mp4Compatibility prepared=Mp4Compatibility.prepare(source)) {
                copy=prepared.file(); assertNotEquals(source,copy);
                assertArrayEquals(fixture(0,1),Files.readAllBytes(copy.toPath()));
                assertArrayEquals(bytes,Files.readAllBytes(source.toPath()));
            }
            assertFalse(copy.exists()); assertTrue(source.exists());
            for(int flags:new int[]{0,0x01000000,2}) {
                Files.write(source.toPath(),fixture(flags,1));
                try(Mp4Compatibility prepared=Mp4Compatibility.prepare(source)) { assertEquals(source,prepared.file()); }
            }
            Files.write(source.toPath(),fixture(1,2));
            assertThrows(IOException.class,()->Mp4Compatibility.prepare(source));
            Files.write(source.toPath(),new byte[]{0,0,0,4,109,111,111,118});
            assertThrows(IOException.class,()->Mp4Compatibility.prepare(source));
            Thread.currentThread().interrupt();
            try { assertThrows(InterruptedException.class,()->Mp4Compatibility.prepare(source)); }
            finally { Thread.interrupted(); }
        } finally { Files.deleteIfExists(source.toPath()); }
    }
}
