package dev.browserdownloader.probe;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class FileFingerprintTest {
    private long deadline() { return System.nanoTime()+TimeUnit.SECONDS.toNanos(5); }
    @Test public void knownDigestAndBytesSurviveShortReads() throws Exception {
        byte[] bytes="abc".getBytes(StandardCharsets.UTF_8);
        InputStream input=new ByteArrayInputStream(bytes) {
            @Override public synchronized int read(byte[] buffer,int offset,int length) { return super.read(buffer,offset,Math.min(1,length)); }
        };
        FileFingerprint.Data result=FileFingerprint.read(input,3,deadline());
        assertArrayEquals(bytes,result.bytes());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",result.sha256());
    }
    @Test public void equalLengthDifferentFilesNeverMatch() throws Exception {
        String a=FileFingerprint.read(new ByteArrayInputStream(new byte[]{1,2,3}),3,deadline()).sha256();
        String b=FileFingerprint.read(new ByteArrayInputStream(new byte[]{1,2,4}),3,deadline()).sha256();
        assertNotEquals(a,b);
    }
    @Test public void exactLimitAllowedButOneExtraByteFails() throws Exception {
        assertEquals(4,FileFingerprint.read(new ByteArrayInputStream(new byte[4]),4,deadline()).bytes().length);
        assertThrows(IOException.class,() -> FileFingerprint.read(new ByteArrayInputStream(new byte[5]),4,deadline()));
    }
    @Test public void expiredDeadlineAndInterruptedWorkCannotSucceed() {
        assertThrows(IOException.class,() -> FileFingerprint.read(new ByteArrayInputStream(new byte[1]),4,System.nanoTime()-1));
        Thread.currentThread().interrupt();
        try { assertThrows(InterruptedIOException.class,() -> FileFingerprint.read(new ByteArrayInputStream(new byte[1]),4,deadline())); }
        finally { Thread.interrupted(); }
    }
    @Test public void providerFailureCannotBecomePartialSuccess() {
        InputStream broken=new InputStream() { @Override public int read() throws IOException { throw new IOException("private provider detail"); } };
        assertThrows(IOException.class,() -> FileFingerprint.read(broken,4,deadline()));
    }
}
