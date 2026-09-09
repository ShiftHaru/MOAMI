package dev.browserdownloader.xprobe;
import org.junit.Test;
import static org.junit.Assert.*;
public class InstagramLinkTest {
    @Test public void canonicalAndTrustBoundary() {
        assertEquals("https://www.instagram.com/p/abc_12/",InstagramLink.canonical("https://instagram.com/p/abc_12/?stkn=test"));
        assertEquals("https://www.instagram.com/reel/ABC/",InstagramLink.canonical("https://www.instagram.com/reels/ABC/"));
        for(String s:new String[]{"https://instagram.com.evil/p/a/","https://user@instagram.com/p/a/","http://instagram.com/p/a/","https://instagram.com/stories/a/","https://instagram.com:443/p/a/"}){
            try{InstagramLink.canonical(s);fail(s);}catch(IllegalArgumentException expected){}
        }
        assertTrue(InstagramLink.mediaUrl("https://scontent.cdninstagram.com/a?signature=test"));
        assertTrue(Mp4Variant.permittedUrl("https://scontent.fbcdn.net/a.mp4"));
        for(String s:new String[]{"https://cdninstagram.com.evil/a","https://evilcdninstagram.com/a","https://user@s.fbcdn.net/a","http://s.fbcdn.net/a","https://s.fbcdn.net:443/a"})assertFalse(InstagramLink.mediaUrl(s));
    }
}
