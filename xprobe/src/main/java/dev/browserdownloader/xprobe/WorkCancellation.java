package dev.browserdownloader.xprobe;

/** Covers cancellation before a queued job starts as well as during execution. */
final class WorkCancellation {
    private boolean cancelled;
    private Thread thread;
    synchronized void reset() { cancelled = false; }
    synchronized void enter() throws InterruptedException {
        if (cancelled) throw new InterruptedException("중지");
        thread = Thread.currentThread();
    }
    synchronized void cancel() {
        cancelled = true;
        if (thread != null) thread.interrupt();
    }
    synchronized void leave() { thread = null; }
}
