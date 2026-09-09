package dev.browserdownloader.probe;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Reads Chrome's own expanded page-info URL. Never guesses a scheme or reads the clipboard. */
final class ChromePageAddress {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Supplier<AccessibilityNodeInfo> current;
    private final Consumer<String> done;
    private AccessibilityNodeInfo document, dialog;
    private String displayed = "", full = "";
    private long deadline;
    private int step;
    private boolean cancelled;

    ChromePageAddress(Supplier<AccessibilityNodeInfo> current, Consumer<String> done) {
        this.current = current; this.done = done;
    }

    void start() {
        AccessibilityNodeInfo root = current.get();
        try {
            if (root == null) { finish(""); return; }
            document = ChromeProbeService.findDocument(root);
            displayed = text(root, "url_bar");
            if (document == null || displayed.isBlank()) { finish(""); return; }
            if (!PreviewRules.requestUrl(displayed).isEmpty()) { finish(displayed); return; }
            if (!click(root, "location_bar_status_icon")) { finish(""); return; }
            deadline = SystemClock.elapsedRealtime() + 5000;
            handler.postDelayed(this::advance, 150);
        } finally { if (root != null) root.recycle(); }
    }

    private void advance() {
        if (cancelled) return;
        AccessibilityNodeInfo root = current.get();
        try {
            if (SystemClock.elapsedRealtime() >= deadline) { finish(""); return; }
            if (root == null) { handler.postDelayed(this::advance, 150); return; }
            if (step == 0) {
                if (has(root, "page_info_close")) {
                    dialog = AccessibilityNodeInfo.obtain(root);
                    if (has(root, "page_info_url") || click(root, "page_info_truncated_url")) step = 1;
                    else { finish(""); return; }
                }
            } else if (step == 1) {
                if (!root.equals(dialog)) { finish(""); return; }
                full = text(root, "page_info_url");
                if (!PreviewRules.requestUrl(full).isEmpty()) {
                    if (!click(root, "page_info_close")) { finish(""); return; }
                    dialog.recycle(); dialog = null; step = 2;
                }
            } else {
                AccessibilityNodeInfo page = ChromeProbeService.findDocument(root);
                try {
                    if (page != null) {
                        finish(page.equals(document) && displayed.equals(text(root, "url_bar")) ? full : "");
                        return;
                    }
                } finally { if (page != null) page.recycle(); }
            }
            handler.postDelayed(this::advance, 150);
        } finally { if (root != null) root.recycle(); }
    }

    private void finish(String value) { cancel(); done.accept(value); }

    void cancel() {
        cancelled = true; handler.removeCallbacksAndMessages(null);
        if (dialog != null) {
            AccessibilityNodeInfo root = current.get();
            try { if (root != null && root.equals(dialog)) click(root, "page_info_close"); }
            finally { if (root != null) root.recycle(); dialog.recycle(); dialog = null; }
        }
        if (document != null) { document.recycle(); document = null; }
        displayed = full = "";
    }

    static String text(AccessibilityNodeInfo root, String id) {
        String result = "";
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/" + id)) {
            if (!node.isPassword() && node.getText() != null) result = node.getText().toString();
            node.recycle();
        }
        return result;
    }

    private static boolean has(AccessibilityNodeInfo root, String id) {
        var nodes = root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/" + id);
        boolean found = !nodes.isEmpty(); for (var node : nodes) node.recycle(); return found;
    }

    private static boolean click(AccessibilityNodeInfo root, String id) {
        boolean clicked = false;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/" + id)) {
            AccessibilityNodeInfo action = AccessibilityNodeInfo.obtain(node);
            for (int depth = 0; action != null && depth < 4; depth++) {
                if (action.isClickable()) { if (!clicked) clicked = action.performAction(AccessibilityNodeInfo.ACTION_CLICK); break; }
                AccessibilityNodeInfo parent = action.getParent(); action.recycle(); action = parent;
            }
            if (action != null) action.recycle(); node.recycle();
        }
        return clicked;
    }
}
