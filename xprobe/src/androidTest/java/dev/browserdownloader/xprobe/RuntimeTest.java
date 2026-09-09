package dev.browserdownloader.xprobe;

import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import android.system.Os;
import android.system.OsConstants;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;

/** Explicit on-device smoke test; never launches or inspects Chrome. */
public final class RuntimeTest extends Instrumentation {
    private String xLink;
    private boolean downloadX;
    private boolean repeat;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        xLink = arguments == null ? null : arguments.getString("xLink");
        downloadX = arguments != null && "true".equals(arguments.getString("downloadX"));
        repeat = arguments != null && "true".equals(arguments.getString("repeat"));
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        JSONObject report = new JSONObject();
        int code = Activity.RESULT_OK;
        try {
            report.put("runId", java.util.UUID.randomUUID().toString());
            report.put("startedAt", java.time.Instant.now().toString());
            report.put("finished", false);
            report.put("passed", false);
            Files.write(new File(getTargetContext().getFilesDir(), "runtime-test.json").toPath(),
                    report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            report.put("pageSize", Os.sysconf(OsConstants._SC_PAGESIZE));
            checkResultStates();
            report.put("resultStateChecks", true);
            NativeProbe probe = new NativeProbe(getTargetContext());
            report.put("environment", probe.environment());
            try { report.put("fixture", probe.convertFixture()); }
            catch (Exception e) { report.put("fixtureError", e.getMessage()); }
            if (xLink != null) {
                try {
                    JSONObject first = downloadX ? probe.downloadX(xLink, true) : probe.inspectX(xLink);
                    report.put("x", first);
                    if (downloadX && !first.getBoolean("allSaved")) throw new AssertionError("Not all requested media saved");
                    if (downloadX && repeat) {
                        JSONObject second = probe.downloadX(xLink, true);
                        if (!second.getBoolean("allSaved")) throw new AssertionError("Repeat save failed");
                        org.json.JSONArray a = first.getJSONArray("items"), b = second.getJSONArray("items");
                        if (a.length() != b.length()) throw new AssertionError("Media count changed");
                        for (int i = 0; i < a.length(); i++) {
                            if (!a.getJSONObject(i).getString("savedUri").equals(b.getJSONObject(i).getString("savedUri")))
                                throw new AssertionError("Repeat created duplicate output");
                        }
                        report.put("sameUrisAfterRepeat", true);
                    }
                }
                catch (Exception e) { report.put("xError", e.getMessage()); }
                catch (AssertionError e) { report.put("xError", e.getMessage()); }
            }
            boolean passed = !report.has("fixtureError") && !report.has("xError")
                    && !(report.has("x") && report.getJSONObject("x").has("gifError"));
            report.put("passed", passed);
            if (!passed) code = Activity.RESULT_CANCELED;
        } catch (Exception e) {
            code = Activity.RESULT_CANCELED;
            try { report.put("passed", false).put("errorClass", e.getClass().getName())
                    .put("error", e.getMessage()); } catch (Exception ignored) { }
        }
        try {
            report.put("finished", true);
            report.put("finishedAt", java.time.Instant.now().toString());
            Files.write(new File(getTargetContext().getFilesDir(), "runtime-test.json").toPath(),
                    report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) { code = Activity.RESULT_CANCELED; }
        result.putString("stream", report.toString());
        finish(code, result);
    }

    private void checkResultStates() throws Exception {
        JSONObject unsupported = new JSONObject("{\"postId\":\"1\",\"media\":[{\"id\":\"2\",\"kind\":\"unknown\"}]}");
        JSONObject failed = XMedia.downloadAll(getTargetContext(), unsupported, false);
        if (failed.getBoolean("allSaved") || failed.getInt("failed") != 1
                || failed.getJSONArray("items").getJSONObject(0).getBoolean("complete"))
            throw new IllegalStateException("Unsupported media reported as success");
        Thread.currentThread().interrupt();
        try {
            JSONObject stopped = XMedia.downloadAll(getTargetContext(), unsupported, false);
            if (!stopped.getBoolean("cancelled") || stopped.getBoolean("allSaved")
                    || stopped.getInt("unattempted") != 1 || stopped.getInt("failed") != 0)
                throw new IllegalStateException("Pre-start cancellation lost");
        } finally { Thread.interrupted(); }
    }
}
