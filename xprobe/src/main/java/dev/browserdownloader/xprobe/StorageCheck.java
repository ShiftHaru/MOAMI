package dev.browserdownloader.xprobe;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.*;
import java.util.ArrayList;

/** Opt-in device check; only its own UUID-named files are removed. */
final class StorageCheck {
    static String run(Context context) throws Exception {
        synchronized (PublicDownloads.class) {
            PublicDownloads.recover(context);
            File file = File.createTempFile("storage-check-", ".png", context.getCacheDir());
            ArrayList<Uri> created = new ArrayList<>();
            String key = null;
            try {
                Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
                try (OutputStream output = new FileOutputStream(file)) {
                    bitmap.eraseColor(0xff2477aa);
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException();
                } finally { bitmap.recycle(); }
                String hash;
                try (InputStream input = new FileInputStream(file)) { hash = PublicDownloads.sha256(input); }
                key = file.getName() + ":" + hash;
                Uri saved = PublicDownloads.publish(context, file, "image/png"); created.add(saved);
                try (InputStream input = context.getContentResolver().openInputStream(saved)) {
                    require(input != null && hash.equals(PublicDownloads.sha256(input)));
                }
                require(saved.equals(PublicDownloads.publish(context, file, "image/png")));
                journal(context, saved);
                require(PublicDownloads.recover(context) == 0);
                try (InputStream input = context.getContentResolver().openInputStream(saved)) {
                    require(input != null && hash.equals(PublicDownloads.sha256(input)));
                }
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, file.getName() + ".pending");
                values.put(MediaStore.Downloads.MIME_TYPE, "image/png");
                values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/BrowserDownloader");
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri partial = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                require(partial != null); created.add(partial); journal(context, partial);
                require(PublicDownloads.recover(context) == 1);
                try (android.database.Cursor row = context.getContentResolver().query(partial,
                        new String[]{MediaStore.Downloads._ID}, null, null, null)) {
                    require(row != null && !row.moveToFirst());
                }
                return "저장 복구 자체 검사 통과\n파일 해시 일치 · 반복 저장 중복 방지 · 완료 파일 보존 · 미완료 파일 정리 확인\n시험용 파일은 삭제했습니다. 실제 강제 종료·저장 공간 부족 시험은 별도입니다.";
            } finally {
                boolean interrupted = Thread.interrupted();
                try {
                    boolean clean = true;
                    for (Uri uri : created) {
                        try {
                            context.getContentResolver().delete(uri, null, null);
                            try (android.database.Cursor row = context.getContentResolver().query(uri,
                                    new String[]{MediaStore.Downloads._ID}, null, null, null)) {
                                if (row == null || row.moveToFirst()) clean = false;
                            }
                        } catch (Exception e) { clean = false; }
                    }
                    if (key != null) context.getSharedPreferences("published", 0).edit().remove(key).commit();
                    if (!file.delete() && file.exists()) clean = false;
                    if (!clean) throw new IOException("시험 파일 정리 실패");
                    PublicDownloads.recover(context);
                } finally { if (interrupted) Thread.currentThread().interrupt(); }
            }
        }
    }
    private static void journal(Context context, Uri uri) throws IOException {
        if (!context.getSharedPreferences(PublicDownloads.JOURNAL, 0).edit().putString("uri", uri.toString()).commit())
            throw new IOException("시험 기록 실패");
    }
    private static void require(boolean condition) throws IOException {
        if (!condition) throw new IOException("저장 복구 자체 검사 실패");
    }
}
