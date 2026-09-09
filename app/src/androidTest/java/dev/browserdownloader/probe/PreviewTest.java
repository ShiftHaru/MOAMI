package dev.browserdownloader.probe;

import android.app.Instrumentation;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in on-device tests using self-generated media and loopback HTTP only. */
public final class PreviewTest extends Instrumentation {
    private boolean xgif;
    private String recovery;
    private String boundary;
    private String xpost;
    @Override public void onCreate(Bundle args) { super.onCreate(args); xgif = args != null && "true".equals(args.getString("xgif")); recovery = args == null ? null : args.getString("recovery"); boundary=args==null?null:args.getString("boundary"); xpost=args==null?null:args.getString("xpost"); start(); }
    @Override public void onStart() {
        if ("handle".equals(boundary)) { HandleCheck.run(this); return; }
        if ("gallery".equals(boundary)) { GalleryCheck.run(this); return; }
        if ("galleryX".equals(boundary)) { GalleryCheck.run(this,true); return; }
        if ("conversionReuse".equals(boundary)) { ConversionReuseCheck.run(this); return; }
        if ("commitPrepare".equals(boundary) || "commitVerify".equals(boundary)) { CommitCrashCheck.run(this,boundary); return; }
        if ("transferPrepare".equals(boundary) || "transferVerify".equals(boundary)) { TransferCrashCheck.run(this,boundary); return; }
        if ("storageFault".equals(boundary)) { StorageFaultCheck.run(this); return; }
        if (xpost != null) { XPostDeviceCheck.run(this,xpost); return; }
        if (boundary != null) { ChromeBoundaryCheck.run(this,boundary); return; }
        if (recovery != null) { dev.browserdownloader.xprobe.CrashRecoveryCheck.run(this, recovery); return; }
        if (xgif) { XGifDeviceCheck.run(this); return; }
        Bundle result = new Bundle();
        PreviewStore store = PreviewStore.get(getTargetContext());
        int priorConsent = getTargetContext().getSharedPreferences("probe", 0).getInt("previewConsentVersion", 0);
        boolean priorEnabled = getTargetContext().getSharedPreferences("probe", 0).getBoolean("enabled", false);
        ExecutorService serverWorkers = Executors.newCachedThreadPool();
        java.util.concurrent.atomic.AtomicInteger flaky = new java.util.concurrent.atomic.AtomicInteger();
        int code = Activity.RESULT_CANCELED;
        String exportPrefix = "";
        try (ServerSocket server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))) {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            Bitmap source = Bitmap.createBitmap(1024, 256, Bitmap.Config.ARGB_8888);
            source.eraseColor(0xff38a169); source.compress(Bitmap.CompressFormat.PNG, 100, png); source.recycle();
            byte[] image = png.toByteArray();
            serverWorkers.submit(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket client = server.accept();
                        serverWorkers.submit(() -> serve(client, image, flaky));
                    } catch (IOException e) { break; }
                }
            });
            getTargetContext().getSharedPreferences("probe", 0).edit().putInt("previewConsentVersion", 1).putBoolean("enabled", true).commit();
            String base = "http://127.0.0.1:"+server.getLocalPort();
            String id = store.begin();
            store.remember(id, 1, base+"/image?token=fixture-secret");
            store.remember(id, 2, base+"/image?token=fixture-secret");
            store.remember(id, 3, base+"/html");
            store.remember(id, 4, base+"/missing");
            store.remember(id, 5, base+"/huge");
            for (int i=1; i<=5; i++) store.request(id, i, true);
            for (int i=1; i<=5; i++) await(store, id, i);
            check(store.count(true)==2 && store.count(false)==3, "Per-node counts");
            check(store.bitmap(id,1).getWidth()<=512, "Thumbnail bound");
            check(store.state(id,1,true).width()==1024, "Source dimensions");
            check(store.state(id,3,true).text().contains("응답"), "HTML rejected");
            check(store.state(id,4,true).text().contains("404"), "HTTP status");
            check(store.state(id,5,true).text().contains("20MiB"), "Size bound");
            check(store.state(id,6,false).text().contains("주소 없음"), "Missing address retained");
            store.remember(id,10,base+"/stall"); store.remember(id,11,base+"/truncate");
            store.save(id,List.of(10,11)); awaitSave(store,10); awaitSave(store,11);
            check(store.saveStatus(10).contains("네트워크 응답 시간 초과"), "Read timeout identified");
            check(store.saveStatus(11).contains("수신 크기 불일치"), "Truncated response rejected");
            exportPrefix = "Chrome_" + id + "_";
            android.content.SharedPreferences pending = getTargetContext().getSharedPreferences("pending-download",0);
            check(pending.getString("uri", "").isEmpty(), "No inherited unfinished download in fixture test");
            String invalidJournal="content://invalid/fixture";
            pending.edit().putString("uri", invalidJournal).commit();
            store.remember(id,9,base+"/image?token=fixture-secret");
            store.save(id,List.of(9)); awaitSave(store,9);
            check(store.saveStatus(9).contains("미완료 저장 복구 실패"), "Failed recovery blocks next save");
            check(invalidJournal.equals(pending.getString("uri", "")), "Failed recovery preserves journal");
            pending.edit().remove("uri").commit();
            store.save(id, Arrays.asList(1,2,3,4,5,6));
            awaitSave(store,1); awaitSave(store,2); awaitSave(store,3); awaitSave(store,4); awaitSave(store,5);
            check(store.saveStatus(1).contains("저장 완료") && store.saveStatus(1).contains("1024 × 256"), "Unscaled file saved");
            check(store.saveStatus(2).startsWith("동일 주소"), "Duplicate URL saved once");
            check(store.saveStatus(3).contains("저장 실패") && store.saveStatus(4).contains("404")
                    && store.saveStatus(5).contains("20MiB"), "Failed downloads stay failures");
            check(store.saveStatus(6).contains("다시 검사"), "No raw URL no download");
            android.content.ContentResolver resolver = getTargetContext().getContentResolver();
            try (android.database.Cursor rows = resolver.query(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{"_id", "is_pending"}, "_display_name LIKE ?", new String[]{exportPrefix + "%"}, null)) {
                check(rows != null && rows.getCount() == 1 && rows.moveToFirst(), "One published file");
                check(rows.getInt(1) == 0, "Published only when complete");
                android.net.Uri uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, rows.getLong(0));
                try (InputStream input = resolver.openInputStream(uri); ByteArrayOutputStream actual = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    for (int n; (n=input.read(buffer)) != -1;) actual.write(buffer,0,n);
                    check(Arrays.equals(image, actual.toByteArray()), "Published bytes exactly match source");
                }
            }
            store.remember(id,12,base+"/flaky"); store.save(id,List.of(12)); awaitSave(store,12);
            check(store.saveStatus(12).contains("수신 크기 불일치"), "Connection closed during transfer");
            store.save(id,List.of(12)); awaitSave(store,12);
            check(store.saveStatus(12).startsWith("저장 완료"), "Same URL retry after connection restored");
            // Construct a new store to model loss of URL/state memory, retaining only disk cache.
            java.lang.reflect.Constructor<PreviewStore> constructor = PreviewStore.class.getDeclaredConstructor(android.content.Context.class);
            constructor.setAccessible(true);
            PreviewStore restored = constructor.newInstance(getTargetContext());
            restored.open(id); restored.request(id,1,true); await(restored,id,1);
            check(restored.state(id,1,true).success(), "Cached preview without URL");
            restored.request(id,99,true);
            check(restored.state(id,99,true).text().equals("다시 검사 필요"), "No sanitized URL fallback");
            restored.cancelRequests();
            ScanNotification.stop(getTargetContext());
            check(!store.canSave(id,1), "Stop blocks subsequent save");
            store.request(id,98,true);
            check(store.state(id,98,true).text().contains("활성화"), "Stop blocks preview restart");
            getTargetContext().getSharedPreferences("probe",0).edit().putBoolean("enabled",true).commit();
            check(!restored.canSave(id,1), "Cache never used as original file");
            store.remember(id,8,base+"/slow"); store.save(id,List.of(8)); store.cancelSaves();
            check(store.saveStatus(8).contains("취소"), "Save cancellation");
            store.remember(id,7,base+"/slow"); store.request(id,7,true);
            String replacement = store.begin();
            check(!store.isCurrent(id) && store.isCurrent(replacement), "New capture isolation");
            check(store.bitmap(replacement,1)==null, "Old bitmap cleared");
            check(store.clear(), "Cache deletion");
            File[] remaining = new File(getTargetContext().getCacheDir(),"image-previews").listFiles();
            check(remaining != null && remaining.length==0, "Cache empty");
            result.putString("stream", "PASS: local HTTP, query, duplicate, missing URL, HTML, 404, oversize, cache restore, new capture, deletion");
            code = Activity.RESULT_OK;
        } catch (Throwable e) { result.putString("stream", "FAIL: " + e.getClass().getSimpleName() + (e instanceof AssertionError ? " · " + e.getMessage() : "")); }
        finally {
            android.content.SharedPreferences fixtureJournal=getTargetContext().getSharedPreferences("pending-download",0);
            if ("content://invalid/fixture".equals(fixtureJournal.getString("uri",""))) fixtureJournal.edit().remove("uri").commit();
            store.clear(); serverWorkers.shutdownNow();
            if (!exportPrefix.isEmpty()) getTargetContext().getContentResolver().delete(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, "_display_name LIKE ?", new String[]{exportPrefix + "%"});
            getTargetContext().getSharedPreferences("probe",0).edit().putInt("previewConsentVersion",priorConsent).putBoolean("enabled",priorEnabled).commit();
        }
        finish(code, result);
    }
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); }
    private static void awaitSave(PreviewStore store, int index) throws Exception {
        long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
        while (System.nanoTime()<deadline) {
            String state = store.saveStatus(index);
            if (state.contains("저장 완료") || state.contains("저장 실패")) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Save deadline");
    }
    private static void await(PreviewStore store, String id, int index) throws Exception {
        long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
        while (System.nanoTime()<deadline) {
            PreviewStore.State state = store.state(id,index,true);
            if (state.success() || state.failed()) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Preview deadline");
    }
    private static void serve(Socket socket, byte[] png, java.util.concurrent.atomic.AtomicInteger flaky) {
        try (socket) {
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII));
            String first = input.readLine();
            for (String line; (line=input.readLine()) != null && !line.isEmpty();) { }
            String path = first == null ? "" : first.split(" ")[1];
            if (path.equals("/slow")) Thread.sleep(1000);
            boolean valid = path.equals("/image?token=fixture-secret") || path.equals("/slow") || path.equals("/stall") || path.equals("/truncate") || path.equals("/flaky");
            String status = valid || path.equals("/html") || path.equals("/huge") ? "200 OK" : "404 Not Found";
            String type = path.equals("/html") ? "text/html" : "image/png";
            byte[] body = valid ? png : new byte[]{1,2,3};
            long size = path.equals("/huge") ? PreviewRules.MAX_BYTES+1 : path.equals("/truncate") ? body.length+100 : body.length;
            OutputStream out = socket.getOutputStream();
            out.write(("HTTP/1.1 "+status+"\r\nContent-Type: "+type+"\r\nContent-Length: "+size+"\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.flush();
            if(path.equals("/stall")) Thread.sleep(12000);
            out.write(body,0,path.equals("/flaky") && flaky.getAndIncrement()==0 ? 8 : body.length);
        } catch (Exception ignored) { }
    }
}
