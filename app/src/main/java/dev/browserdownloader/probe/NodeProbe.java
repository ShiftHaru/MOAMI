package dev.browserdownloader.probe;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

/** Records what Chrome actually exposes; never invents an original URL. */
final class NodeProbe {
    static final String TARGET_URL = "AccessibilityNodeInfo.targetUrl";
    static final String ROLE = "AccessibilityNodeInfo.chromeRole";
    static final String HAS_IMAGE = "AccessibilityNodeInfo.hasImage";

    static JSONObject capture(AccessibilityNodeInfo root, PreviewStore previews, String captureId) throws Exception {
        return capture(root, (index, raw) -> previews.remember(captureId, index, raw), captureId);
    }
    static JSONObject capture(AccessibilityNodeInfo root, java.util.function.BiConsumer<Integer,String> remember, String captureId) throws Exception {
        return capture(root, remember, captureId, null);
    }
    static JSONObject capture(AccessibilityNodeInfo root, java.util.function.BiConsumer<Integer,String> remember, String captureId,
                              java.util.Map<Integer,AccessibilityNodeInfo> identities) throws Exception {
        JSONObject report = new JSONObject();
        report.put("schemaVersion", 2);
        report.put("captureId", captureId);
        report.put("capturedAtEpochMs", System.currentTimeMillis());
        report.put("androidRelease", Build.VERSION.RELEASE);
        report.put("androidSdk", Build.VERSION.SDK_INT);
        report.put("sourcePackage", String.valueOf(root.getPackageName()));
        report.put("networkPermission", true);
        JSONArray nodes = new JSONArray();
        ArrayDeque<AccessibilityNodeInfo> pending = new ArrayDeque<>();
        ArrayDeque<Integer> parents = new ArrayDeque<>();
        pending.add(AccessibilityNodeInfo.obtain(root));
        parents.add(0);
        int examined = 0;
        int images = 0;
        int imageUrls = 0;
        while (!pending.isEmpty() && examined < 5000) {
            AccessibilityNodeInfo node = pending.removeFirst();
            int parentIndex = parents.removeFirst();
            examined++;
            if (!node.isPassword()) {
                Bundle extras = node.getExtras();
                String role = String.valueOf(extras.getCharSequence(ROLE, ""));
                String rawUrl = String.valueOf(extras.getCharSequence(TARGET_URL, ""));
                String url = safeUrl(rawUrl);
                boolean image = extras.getBoolean(HAS_IMAGE, false)
                        || "image".equalsIgnoreCase(role)
                        || "img".equalsIgnoreCase(role);
                if (image) { images++; if (!url.isEmpty()) imageUrls++; }
                if (image) remember.accept(examined, rawUrl);
                if (image && identities != null) identities.put(examined, AccessibilityNodeInfo.obtain(node));
                JSONObject item = new JSONObject();
                item.put("index", examined);
                item.put("parentIndex", parentIndex);
                item.put("className", String.valueOf(node.getClassName()));
                item.put("role", role);
                item.put("hasImage", image);
                item.put("targetUrl", url);
                item.put("visible", node.isVisibleToUser());
                item.put("scrollable", node.isScrollable());
                item.put("childCount", node.getChildCount());
                item.put("viewId", node.getViewIdResourceName());
                item.put("extraKeys", new JSONArray(extras.keySet()));
                if (image) {
                    item.put("text", safeText(String.valueOf(node.getText())));
                    item.put("description", safeText(String.valueOf(node.getContentDescription())));
                    item.put("quality", "unverified");
                }
                if ("com.android.chrome:id/url_bar".equals(node.getViewIdResourceName())) {
                    String address = String.valueOf(node.getText());
                    report.put("addressBarUrl", safeUrl(address));
                }
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                item.put("bounds", new JSONArray(new int[]{bounds.left,bounds.top,bounds.right,bounds.bottom}));
                nodes.put(item);
                for (int i = 0; i < node.getChildCount(); i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) {
                        pending.add(child);
                        parents.add(examined);
                    }
                }
            }
            node.recycle();
        }
        report.put("truncated", !pending.isEmpty());
        while (!pending.isEmpty()) pending.removeFirst().recycle();
        report.put("nodesExamined", examined);
        report.put("imageNodes", images);
        report.put("imageNodesWithUrl", imageUrls);
        report.put("nodes", nodes);
        return report;
    }

    static String safeUrl(String raw) {
        if (raw == null || raw.length() > 16384) return "";
        try {
            URI uri = new URI(raw.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null) return "";
            // Diagnostic reports omit tokens and fragments. This is not a download URL normalizer.
            String trimmed = raw.trim();
            int query = trimmed.indexOf('?');
            int fragment = trimmed.indexOf('#');
            int cut = trimmed.length();
            if (query >= 0) cut = Math.min(cut, query);
            if (fragment >= 0) cut = Math.min(cut, fragment);
            return trimmed.substring(0, cut);
        } catch (Exception ignored) {
            return "";
        }
    }

    static String safeText(String text) {
        if (text == null || text.equals("null")) return "";
        String limited = text.substring(0, Math.min(4096, text.length()));
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)https?://\\S+").matcher(limited);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(safeUrl(matcher.group())));
        matcher.appendTail(result);
        return result.toString();
    }

    static void write(Context context, String name, JSONObject report) throws Exception {
        android.util.AtomicFile file = new android.util.AtomicFile(new java.io.File(context.getFilesDir(), name));
        java.io.FileOutputStream out = file.startWrite();
        try {
            out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (Exception e) { file.failWrite(out); throw e; }
    }
}
