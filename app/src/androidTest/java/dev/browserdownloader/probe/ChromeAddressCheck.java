package dev.browserdownloader.probe;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;

/** Product drawer regression: exact HTTP scheme, query/fragment ticket, and lookup cancellation. */
final class ChromeAddressCheck {
    static void run(Instrumentation test, Context context, UiAutomation ui, String mode) {
        Bundle out = new Bundle();
        int result = Activity.RESULT_CANCELED;
        try {
            if (mode.equals("lookupStop")) {
                context.getSharedPreferences("probe",0).edit().putBoolean("notificationHidden",false).commit();
                test.runOnMainSync(() -> ScanNotification.refresh(context));
                require(ScanNotification.allowed(context), "notification permission required for stop test");
            }
            String base = "http://127.0.0.1:8787/fixture.html";
            String url = base + "?address-check=private-fixture#sample";
            File latest = new File(context.getFilesDir(), "latest.json");
            String before = id(latest);
            open(context, url);
            Thread.sleep(1500);
            boolean share = mode.equals("httpShare") || mode.equals("httpMismatch");
            if (share) {
                context.startActivity(new Intent(Intent.ACTION_SEND).setClass(context, MainActivity.class).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, mode.equals("httpMismatch") ? url.replace("http:", "https:") : url)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                ChromeBoundaryCheck.click(ui, "공유한 Chrome 탭으로 돌아가기", 10000);
            }
            ChromeBoundaryCheck.click(ui, "<", 20000);
            ChromeBoundaryCheck.click(ui, share ? "공유 탭 누적 검사" : "현재 탭 검사", 10000);
            if (mode.startsWith("lookup")) {
                long by = SystemClock.elapsedRealtime() + 5000;
                boolean seen = false;
                while (!seen && SystemClock.elapsedRealtime() < by) {
                    var root = ui.getRootInActiveWindow();
                    if (root != null) {
                        var nodes = root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/page_info_close");
                        seen = !nodes.isEmpty(); for (var node : nodes) node.recycle(); root.recycle();
                    }
                    if (!seen) Thread.sleep(10);
                }
                require(seen, "lookup dialog not observed");
                if (mode.equals("lookupStop")) {
                    var manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    boolean sent = false;
                    for (var notification : manager.getActiveNotifications()) {
                        if (notification.getId() == 100 && notification.getNotification().actions != null) {
                            notification.getNotification().actions[0].actionIntent.send(); sent = true; break;
                        }
                    }
                    require(sent, "stop action not available");
                } else open(context, "http://127.0.0.1:8787/other.html");
                Thread.sleep(6000);
                require(id(latest).equals(before), "cancelled lookup started a capture");
            } else if (mode.equals("httpMismatch")) {
                Thread.sleep(6000);
                require(id(latest).equals(before), "HTTP accepted HTTPS share ticket");
                require(!SharedPage.pending.peek(SystemClock.elapsedRealtime()).isEmpty(), "mismatched ticket consumed");
            } else {
                long by = SystemClock.elapsedRealtime() + 12000;
                while (id(latest).equals(before) && SystemClock.elapsedRealtime() < by) Thread.sleep(100);
                require(!id(latest).equals(before), "capture did not start");
                if (share) ChromeBoundaryCheck.click(ui, "수집 중단 · 결과 유지", 10000);
                JSONObject report = new JSONObject(Files.readString(latest.toPath()));
                require(report.optString("addressBarUrl").equals(base), "HTTP diagnostic scheme incorrect");
                require(!report.toString().contains("private-fixture") && !report.toString().contains("#sample"), "page query persisted");
                require(report.optInt("imageNodesWithUrl") > 0, "images missing");
                if (share) require(report.optString("entry").equals("ACTION_SEND_CURRENT_TAB")
                        && SharedPage.pending.peek(SystemClock.elapsedRealtime()).isEmpty(), "exact share ticket not consumed");
            }
            out.putString("report", "PASS " + mode + ": product address lookup and capture contract");
            result = Activity.RESULT_OK;
        } catch (Throwable error) {
            out.putString("report", "FAIL " + mode + ": " + error.getClass().getSimpleName()
                    + (error instanceof AssertionError ? " " + error.getMessage() : ""));
        }
        test.finish(result, out);
    }
    private static void open(Context context, String url) {
        context.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).setPackage("com.android.chrome")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("create_new_tab", true));
    }
    private static String id(File file) throws Exception {
        return file.isFile() ? new JSONObject(Files.readString(file.toPath())).optString("captureId") : "";
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
