package dev.browserdownloader.xprobe;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

/** Publish a verified file through scoped storage, committing only a complete copy. */
public final class PublicDownloads {
    static final String JOURNAL = "x-pending-download";
    private static void checkCancelled() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("저장 중지");
    }
    public static synchronized int recover(Context context) throws Exception {
        return recover(context, JOURNAL);
    }
    public static synchronized int recover(Context context, String journal) throws Exception {
        android.content.SharedPreferences pending = context.getSharedPreferences(journal, 0);
        String value = pending.getString("uri", "");
        if (value.isEmpty()) return 0;
        if (!value.matches("content://media/(?:external|external_primary)/downloads/[0-9]+"))
            throw new IOException("미완료 저장 복구 실패");
        Uri uri = Uri.parse(value);
        int deleted = 0;
        try (android.database.Cursor row = context.getContentResolver().query(uri,
                new String[]{MediaStore.Downloads.IS_PENDING, MediaStore.Downloads.OWNER_PACKAGE_NAME,
                        MediaStore.Downloads.RELATIVE_PATH}, null, null, null)) {
            if (row == null) throw new IOException("미완료 저장 복구 실패");
            if (row.moveToFirst() && row.getInt(0) == 1) {
                if (!context.getPackageName().equals(row.getString(1))
                        || !"Download/BrowserDownloader/".equals(row.getString(2)))
                    throw new IOException("미완료 저장 복구 실패");
                deleted = context.getContentResolver().delete(uri, null, null);
                if (deleted != 1) throw new IOException("미완료 저장 복구 실패");
            }
        }
        if (!pending.edit().remove("uri").commit()) throw new IOException("미완료 저장 복구 실패");
        return deleted;
    }
    public static String sha256(InputStream input) throws Exception {
        checkCancelled();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        for (int n; (n = input.read(buffer)) != -1;) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("중지");
            digest.update(buffer, 0, n);
        }
        checkCancelled();
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    public static synchronized Uri publish(Context context, File source, String mime) throws Exception {
        checkCancelled();
        recover(context);
        String digest;
        try (InputStream input = new java.io.FileInputStream(source)) { digest = sha256(input); }
        String key = source.getName() + ":" + digest;
        android.content.SharedPreferences records = context.getSharedPreferences("published", Context.MODE_PRIVATE);
        String previous = records.getString(key, null);
        if (previous != null) {
            Uri existing = Uri.parse(previous);
            try (InputStream input = context.getContentResolver().openInputStream(existing)) {
                if (input != null && digest.equals(sha256(input))) return existing;
            } catch (IOException | SecurityException e) { records.edit().remove(key).commit(); }
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, source.getName());
        values.put(MediaStore.Downloads.MIME_TYPE, mime);
        values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/BrowserDownloader");
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        checkCancelled();
        Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("다운로드 파일 생성 실패");
        boolean committed = false;
        try {
            if (!context.getSharedPreferences(JOURNAL, 0).edit().putString("uri", uri.toString()).commit())
                throw new IOException("미완료 저장 복구 실패");
            try (InputStream input = new java.io.FileInputStream(source);
                 OutputStream output = context.getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) throw new IOException("다운로드 파일 쓰기 실패");
                byte[] buffer = new byte[64 * 1024];
                for (int n; (n = input.read(buffer)) != -1;) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("저장 중지");
                    output.write(buffer, 0, n);
                }
            }
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null || !digest.equals(sha256(input))) throw new IOException("공용 저장 파일 검증 실패");
            }
            checkCancelled();
            // Persist deduplication before publication so a kill after IS_PENDING=0
            // cannot leave a completed file without its retry identity.
            if (!records.edit().putString(key, uri.toString()).commit())
                throw new IOException("미완료 저장 복구 실패");
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0);
            if (context.getContentResolver().update(uri, values, null, null) != 1) throw new IOException("다운로드 완료 처리 실패");
            committed = true;
            return uri;
        } finally {
            // Keep the journal on cleanup failure; the next X operation retries it.
            if (!committed && context.getContentResolver().delete(uri, null, null) != 1)
                throw new IOException("미완료 저장 복구 실패");
            context.getSharedPreferences(JOURNAL, 0).edit().remove("uri").commit();
        }
    }
}
