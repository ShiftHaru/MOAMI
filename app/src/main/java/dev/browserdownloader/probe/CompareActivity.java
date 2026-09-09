package dev.browserdownloader.probe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** The user supplies both files; matching a reference never proves its provenance. */
public final class CompareActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String[] hashes = new String[]{"", ""};
    private final String[] descriptions = new String[]{"파일 미선택", "파일 미선택"};
    private TextView output;
    private int pickerSlot;
    private ReadJob active;
    private ReadJob inFlight;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) {
            pickerSlot = saved.getInt("slot", 0);
            for (int i=0;i<2;i++) {
                hashes[i] = saved.getString("hash"+i, "");
                descriptions[i] = saved.getString("description"+i, "파일 미선택");
            }
        }
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(24,48,24,24);
        body.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(24+insets.getSystemWindowInsetLeft(),24+insets.getSystemWindowInsetTop(),24+insets.getSystemWindowInsetRight(),24+insets.getSystemWindowInsetBottom()); return insets;
        });
        TextView title = new TextView(this); title.setText("저장 파일과 기준 파일 비교"); title.setTextSize(22); body.addView(title);
        TextView guide = new TextView(this); guide.setText("앱으로 저장한 이미지와 Chrome에서 별도로 저장한 사이트 제공 기준 이미지를 선택하세요.\n파일당 20MiB·30초로 읽고 해시·크기·해상도를 비교합니다. 서버 업로드나 파일 변경은 하지 않습니다. 클라우드 파일을 선택하면 해당 제공자가 내려받을 수 있습니다.\n일치해도 기준 파일의 출처가 원본이라는 뜻은 아닙니다. 실제 파일 두 개의 비교 결과만 표시합니다."); body.addView(guide);
        for (int i=0;i<2;i++) {
            final int slot=i;
            Button pick = new Button(this); pick.setText(i==0 ? "앱 저장 파일 선택" : "기준 파일 선택");
            pick.setOnClickListener(v -> pick(slot)); body.addView(pick);
        }
        Button clear = new Button(this); clear.setText("비교 지우기·중지"); clear.setOnClickListener(v -> {
            cancel(); for (int i=0;i<2;i++) { hashes[i]=""; descriptions[i]="파일 미선택"; } render();
        }); body.addView(clear);
        output = new TextView(this); output.setTextIsSelectable(true); body.addView(output);
        Button back = new Button(this); back.setText("돌아가기"); back.setOnClickListener(v -> finish()); body.addView(back);
        ScrollView scroll = new ScrollView(this); scroll.addView(body); setContentView(scroll); render();
    }
    private void pick(int slot) {
        if (inFlight != null && !inFlight.finished) {
            Toast.makeText(this,"이전 파일 읽기 정리 중입니다. 잠시 후 다시 선택해 주세요.",Toast.LENGTH_LONG).show(); return;
        }
        cancel(); pickerSlot=slot;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(intent, 70); }
        catch (Exception e) { descriptions[slot]="파일 선택기를 열 수 없습니다."; hashes[slot]=""; render(); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 70 || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (!"content".equals(uri.getScheme())) return;
        cancel(); hashes[pickerSlot]=""; descriptions[pickerSlot]="파일 읽는 중"; render();
        active = new ReadJob(pickerSlot, uri); inFlight=active; active.future = worker.submit(active);
    }
    private void render() {
        if (output == null) return;
        String result = hashes[0].isEmpty() || hashes[1].isEmpty() ? "이미지 두 파일을 선택해 주세요."
                : hashes[0].equals(hashes[1]) ? "기준 파일과 바이트 일치 · 사이트 원본 여부 미확인"
                : "기준 파일과 바이트 불일치 · 크기·형식·내용을 확인해 주세요.";
        output.setText("\n앱 저장 파일\n" + descriptions[0] + "\n\n기준 파일\n" + descriptions[1] + "\n\n" + result);
    }
    private void cancel() {
        if (active == null) return;
        ReadJob job=active; active=null; job.cancelled=true;
        descriptions[job.slot]="파일 읽기 중단 · 다시 선택해 주세요."; hashes[job.slot]="";
        if (job.future != null) job.future.cancel(true);
        synchronized (job) { if (!job.started) job.finished=true; }
        job.signal.cancel(); deadlines.execute(job::close);
        render();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        cancel(); state.putInt("slot", pickerSlot);
        for (int i=0;i<2;i++) { state.putString("hash"+i,hashes[i]); state.putString("description"+i,descriptions[i]); }
        super.onSaveInstanceState(state);
    }
    @Override protected void onStop() { cancel(); super.onStop(); }
    @Override protected void onDestroy() { cancel(); worker.shutdownNow(); deadlines.shutdown(); main.removeCallbacksAndMessages(null); super.onDestroy(); }
    private final class ReadJob implements Runnable {
        final int slot; final Uri uri;
        final CancellationSignal signal = new CancellationSignal();
        volatile InputStream input;
        volatile boolean cancelled, timedOut, finished;
        boolean started;
        Future<?> future;
        ReadJob(int slot, Uri uri) { this.slot=slot; this.uri=uri; }
        void close() { InputStream stream=input; if (stream != null) try { stream.close(); } catch (IOException ignored) { } }
        @Override public void run() {
            synchronized (this) { if (cancelled) { finished=true; return; } started=true; }
            String description, hash="";
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            ScheduledFuture<?> timer;
            try { timer = deadlines.schedule(() -> {
                timedOut=true;
                main.post(() -> { if (active == this && !isDestroyed()) { descriptions[slot]="시간 제한 초과 (30초) · 파일 읽기 정리 중"; hashes[slot]=""; render(); } });
                signal.cancel(); close();
            },30,TimeUnit.SECONDS); }
            catch (RejectedExecutionException stopped) { finished=true; return; }
            try (android.content.res.AssetFileDescriptor file = getContentResolver().openAssetFileDescriptor(uri,"r",signal)) {
                if (file == null) throw new IOException();
                input=file.createInputStream();
                if (cancelled || timedOut) throw new InterruptedIOException();
                FileFingerprint.Data data = FileFingerprint.read(input, PreviewRules.MAX_BYTES, deadline);
                BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(data.bytes(),0,data.bytes().length,bounds);
                if (bounds.outWidth<=0 || bounds.outHeight<=0) throw new IOException("이미지 형식 확인 실패");
                if (timedOut || System.nanoTime() >= deadline) throw new IOException("시간 제한 초과");
                hash=data.sha256();
                description=bounds.outWidth+" × "+bounds.outHeight+" · "+bounds.outMimeType+" · "+data.bytes().length+" bytes\nSHA-256: "+hash;
            } catch (OutOfMemoryError error) { hash=""; description="메모리 부족 · 더 작은 파일로 확인해 주세요."; }
            catch (Exception error) {
                hash="";
                String message=error.getMessage();
                description=timedOut ? "시간 제한 초과 (30초)" : "파일 크기 제한 초과".equals(message) ? "파일 크기 제한 초과 (20MiB)"
                        : "시간 제한 초과".equals(message) ? "시간 제한 초과 (30초)" : "파일 읽기 또는 이미지 형식 확인 실패";
            } finally { close(); timer.cancel(false); finished=true; }
            final String result=description, fingerprint=hash;
            main.post(() -> {
                if (active != this || cancelled || isDestroyed()) return;
                active=null; descriptions[slot]=timedOut ? "시간 제한 초과 (30초)" : result; hashes[slot]=timedOut ? "" : fingerprint; render();
            });
        }
    }
}
