package dev.browserdownloader.xprobe;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class WorkCancellationTest {
    @Test public void stopBeforeExecutionPreventsWorkAndResetAllowsRetry() throws Exception {
        WorkCancellation control = new WorkCancellation();
        control.reset(); control.cancel();
        try { control.enter(); fail("cancelled job entered"); } catch (InterruptedException expected) { }
        control.reset(); control.enter(); control.leave();
        control.cancel(); assertFalse(Thread.currentThread().isInterrupted());
    }
    @Test public void stopInterruptsActiveWorkerButNeverItsFormerThread() throws Exception {
        WorkCancellation control = new WorkCancellation();
        CountDownLatch entered = new CountDownLatch(1), stopped = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            try { control.enter(); entered.countDown(); new CountDownLatch(1).await(); }
            catch (InterruptedException expected) { interrupted.set(true); }
            finally { control.leave(); stopped.countDown(); }
        });
        worker.start();
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS)); control.cancel();
            assertTrue(stopped.await(2, TimeUnit.SECONDS)); assertTrue(interrupted.get());
            control.cancel(); assertFalse(Thread.currentThread().isInterrupted());
        } finally { worker.interrupt(); worker.join(2000); }
    }
}
