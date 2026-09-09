package dev.browserdownloader.xprobe;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import static org.junit.Assert.*;

public class PublicDownloadsTest {
    @Test public void hashingChecksCancellationEvenAtEmptyEndOfStream() throws Exception {
        try {
            Thread.currentThread().interrupt();
            try { PublicDownloads.sha256(new ByteArrayInputStream(new byte[0])); fail(); }
            catch (InterruptedException expected) { }
        } finally { Thread.interrupted(); }
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                PublicDownloads.sha256(new ByteArrayInputStream(new byte[0])));
        try {
            ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]) {
                @Override public synchronized int read(byte[] bytes, int offset, int length) {
                    Thread.currentThread().interrupt(); return -1;
                }
            };
            try { PublicDownloads.sha256(input); fail(); } catch (InterruptedException expected) { }
        } finally { Thread.interrupted(); }
    }
}
