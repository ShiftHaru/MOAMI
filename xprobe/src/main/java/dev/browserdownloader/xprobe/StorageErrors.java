package dev.browserdownloader.xprobe;

/** Classify actual OS storage failures without exposing exception text. */
public final class StorageErrors {
    private StorageErrors() { }
    public static boolean isNoSpace(Throwable error) {
        for(int depth=0; error!=null && depth<16; depth++,error=error.getCause()) {
            if(error instanceof android.system.ErrnoException errno
                && (errno.errno==android.system.OsConstants.ENOSPC || errno.errno==android.system.OsConstants.EDQUOT)) return true;
        }
        return false;
    }
}
