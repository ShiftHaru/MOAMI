package dev.browserdownloader.xprobe;

import org.junit.Test;
import java.nio.file.*;
import static org.junit.Assert.*;

public class PrivateMediaFilesTest {
    @Test public void removesOnlyKnownTemporaryRegularFiles() throws Exception {
        Path dir=Files.createTempDirectory("private-media-test-");
        String[] remove={"123.mp4.partial","123.photo.partial","123.converted.gif.partial","mp4-decode-123.mp4","ig-ABC-1.best.partial","ig-ABC-2.photo.partial","ig-ABC-1.converted.gif.partial"};
        String[] keep={"123.mp4","123.converted.gif","unknown.partial","mp4-decode-note.mp4"};
        try {
            for(String name:remove) Files.write(dir.resolve(name),new byte[]{1});
            for(String name:keep) Files.write(dir.resolve(name),new byte[]{2});
            Files.createDirectory(dir.resolve("999.mp4.partial"));
            PrivateMediaFiles.clean(dir.toFile());
            for(String name:remove) assertFalse(Files.exists(dir.resolve(name)));
            for(String name:keep) assertArrayEquals(new byte[]{2},Files.readAllBytes(dir.resolve(name)));
            assertTrue(Files.isDirectory(dir.resolve("999.mp4.partial")));
            PrivateMediaFiles.clean(dir.toFile());
        } finally {
            try(var paths=Files.list(dir)) { for(Path p:paths.toList()) Files.delete(p); }
            Files.delete(dir);
        }
    }
}
