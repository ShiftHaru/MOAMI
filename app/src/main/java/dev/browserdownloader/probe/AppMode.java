package dev.browserdownloader.probe;

/** Shared entry checks for the gallery and setup flow. */
final class AppMode {
    static boolean ready(boolean consent, boolean accessibility, boolean notifications) {
        return consent && (BuildConfig.SHARE_ONLY || accessibility) && notifications;
    }
    static boolean accepts(String input) {
        return !BuildConfig.SHARE_ONLY || !XShareLink.extract(input).isEmpty();
    }
}
