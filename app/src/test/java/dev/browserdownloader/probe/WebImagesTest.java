package dev.browserdownloader.probe;

import org.junit.Test;
import static org.junit.Assert.*;

public class WebImagesTest {
    @Test public void resolvesBaseQueriesAndLargestCandidateWithoutFollowingParentLinks(){
        var result=WebImages.parse("<base href='https://cdn.example/a/'><a href='original.jpg'><img alt='hello' src='small.jpg' srcset='small.jpg 100w, big.jpg?token=fixture 900w'></a><img data-src='lazy.jpg'><picture><source srcset='hd.jpg 2x'><img src='low.jpg'></picture>","https://example.test/page");
        assertEquals(3,result.items().size());
        assertEquals("https://cdn.example/a/big.jpg?token=fixture",result.items().get(0).url());
        assertEquals("https://cdn.example/a/lazy.jpg",result.items().get(1).url());
        assertEquals("https://cdn.example/a/hd.jpg",result.items().get(2).url());
        assertEquals("hello",result.items().get(0).description());
    }
    @Test public void rejectsCredentialsScriptsAndDeduplicatesFullUrls(){
        var result=WebImages.parse("<img src='a.png?x=1'><img src='a.png?x=1'><img src='a.png?x=2'><img src='javascript:alert(1)'><img src='data:image/png,abc'><img src='https://user:pass@example.test/p.png'>", "https://example.test/");
        assertEquals(2,result.items().size());
    }
    @Test public void limitsWithoutClaimingCompleteness(){
        StringBuilder html=new StringBuilder();for(int n=0;n<502;n++)html.append("<img src='/").append(n).append(".png'>");
        var result=WebImages.parse(html.toString(),"https://example.test");assertEquals(500,result.items().size());assertTrue(result.limited());
    }
    @Test public void dynamicOnlyHtmlIsAnEmptyResult(){assertTrue(WebImages.parse("<script>document.createElement('img')</script>","https://example.test").items().isEmpty());}
}
