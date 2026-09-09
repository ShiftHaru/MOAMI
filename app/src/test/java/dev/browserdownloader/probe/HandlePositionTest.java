package dev.browserdownloader.probe;

import org.junit.Test;
import static org.junit.Assert.*;

public class HandlePositionTest {
    @Test public void clampAndRestoreAcrossSizes(){
        assertEquals(444,HandlePosition.top(.5f,1000,112));
        assertEquals(0,HandlePosition.top(-1,1000,112));
        assertEquals(888,HandlePosition.top(2,1000,112));
        assertEquals(444,HandlePosition.top(Float.NaN,1000,112));
        float moved=HandlePosition.move(.5f,200,1000,112);
        assertEquals(.7f,moved,.001f);
        assertEquals(344,HandlePosition.top(moved,500,12));
        assertEquals(.944f,HandlePosition.move(.5f,9999,1000,112),.001f);
        assertEquals(.056f,HandlePosition.move(.5f,-9999,1000,112),.001f);
        assertEquals(0,HandlePosition.top(.9f,100,200));
        assertEquals(.5f,HandlePosition.move(.9f,10,100,200),0);
    }
}
