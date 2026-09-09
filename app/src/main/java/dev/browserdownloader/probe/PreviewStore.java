package dev.browserdownloader.probe;

import android.content.Context;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import java.security.MessageDigest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

/** Latest-session URLs live only here. Disk files contain dimensions and thumbnails only. */
final class PreviewStore {
    synchronized boolean saving() { return !saving.isEmpty(); }
    synchronized boolean hasRequest(String id, int index) { return current.equals(id) && urls.containsKey(index); }
    record State(String text, int width, int height, boolean success, boolean failed) { }
    private static PreviewStore instance;
    static synchronized PreviewStore get(Context context) {
        if (instance == null) instance = new PreviewStore(context.getApplicationContext());
        return instance;
    }
    static boolean consent(Context context) {
        return context.getSharedPreferences("probe", Context.MODE_PRIVATE).getInt("previewConsentVersion", 0) >= 1;
    }
    private final Context context;
    private final File directory;
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Integer, String> urls = new HashMap<>();
    private final Map<Integer, State> states = new HashMap<>();
    private final Map<Integer, Job> jobs = new HashMap<>();
    private final LruCache<Integer, Bitmap> bitmaps = new LruCache<>(8 * 1024 * 1024) {
        @Override protected int sizeOf(Integer key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    private String current = "";
    private final Set<Integer> selection = new LinkedHashSet<>();
    private long revision;
    synchronized Set<Integer> selection(String id) { return current.equals(id) ? new LinkedHashSet<>(selection) : new LinkedHashSet<>(); }
    synchronized void select(String id, Collection<Integer> indices) { if (current.equals(id)) { selection.clear(); selection.addAll(indices); } }
    synchronized long revision() { return revision; }
    synchronized void resultChanged() { revision++; changed(); }
    private Runnable listener;
    private final ExecutorService saver = Executors.newFixedThreadPool(1);
    private final Map<Integer, String> saves = new HashMap<>();
    private final Set<Integer> saved = new HashSet<>();
    private final Map<String, String> savedUrls = new HashMap<>();
    private final Map<Integer, Job> saving = new HashMap<>();
    synchronized String saveStatus(int index) { return saves.getOrDefault(index, ""); }
    synchronized boolean isSaved(String id, int index) { return current.equals(id) && saved.contains(index); }
    synchronized String saveTargetInfo(String id, int index) {
        String raw = current.equals(id) ? urls.get(index) : null;
        if (raw == null) return "저장 주소를 확인하려면 다시 검사해 주세요.";
        String candidate = PreviewRules.originalCandidate(raw);
        return candidate.isEmpty() ? "저장: 검출 주소 그대로 · 원본 미확인" :
                "저장: 루리웹 원본 후보 경로\n" + NodeProbe.safeUrl(candidate)
                + "\n미리보기는 검출 이미지입니다. 후보 실패 시 축소 주소로 자동 대체하지 않습니다.";
    }
    synchronized boolean canSave(String id, int index) { return current.equals(id) && urls.containsKey(index) && ScanNotification.enabled(context); }
    synchronized void cancelSaves() {
        for (Map.Entry<Integer, Job> entry : saving.entrySet()) {
            entry.getValue().cancel(); saves.put(entry.getKey(), "저장 취소 · 다시 시도 가능");
        }
        saving.clear(); changed();
        ((ThreadPoolExecutor) saver).purge();
    }
    synchronized void save(String id, Collection<Integer> indices) {
        for (int index : indices) {
            if (!canSave(id, index)) { saves.put(index, "저장 불가 · 동의 또는 다시 검사 필요"); continue; }
            if (saving.containsKey(index) || saved.contains(index)) continue;
            Job job = new Job(id, index, PreviewRules.saveUrl(urls.get(index)), null);
            saving.put(index, job); saves.put(index, "저장 대기");
            job.future = saver.submit(() -> saveFile(job));
        }
        changed();
    }
    private void saveFile(Job job) {
        Uri uri = null;
        boolean journalOwned = false;
        ScheduledFuture<?> timer = deadlines.schedule(() -> {
            job.timedOut = true;
            if (job.connection != null) job.connection.disconnect();
        }, 30, TimeUnit.SECONDS);
        try {
            synchronized (this) {
                if (!saveValid(job)) return;
                if (savedUrls.containsKey(job.url)) {
                    saved.add(job.index);
                    saves.put(job.index, "동일 주소 · " + savedUrls.get(job.url));
                    return;
                }
                saves.put(job.index, "파일 수신·검증 중");
            }
            changed();
            if (!cleanInterruptedDownload()) throw new IOException("미완료 저장 복구 실패");
            if (context.getSharedPreferences("pending-download", 0).contains("uri")) throw new IOException();
            byte[] bytes = job.fetch();
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType == null)
                throw new IOException("이미지 디코딩 실패");
            if ((long) bounds.outWidth * bounds.outHeight > 100_000_000) throw new IOException("이미지 픽셀 제한 초과");
            BitmapFactory.Options sample = new BitmapFactory.Options();
            sample.inSampleSize = PreviewRules.sampleSize(bounds.outWidth, bounds.outHeight);
            Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, sample);
            if (decoded == null) throw new IOException("이미지 디코딩 실패");
            decoded.recycle();
            String extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(bounds.outMimeType);
            if (extension == null || !extension.matches("[a-zA-Z0-9]{1,10}")) throw new IOException("이미지 디코딩 실패");
            String hash = hex(MessageDigest.getInstance("SHA-256").digest(bytes));
            job.check();
            ContentValues values = new ContentValues();
            String name = "Chrome_" + job.id + "_" + job.index + "." + extension;
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, bounds.outMimeType);
            values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/BrowserDownloader");
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            synchronized (this) {
                if (!saveValid(job)) return;
                uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                journalOwned = uri != null;
                if (uri != null && !context.getSharedPreferences("pending-download", 0).edit().putString("uri", uri.toString()).commit())
                    throw new IOException();
            }
            if (uri == null) throw new IOException();
            try (OutputStream output = context.getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IOException();
                for (int offset=0; offset<bytes.length; offset+=16384) {
                    job.check(); output.write(bytes, offset, Math.min(16384, bytes.length-offset));
                }
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException();
                byte[] buffer = new byte[16384];
                for (int n; (n=input.read(buffer)) != -1;) { job.check(); digest.update(buffer,0,n); }
            }
            if (!hash.equals(hex(digest.digest()))) throw new IOException();
            synchronized (this) {
                job.check();
                if (!saveValid(job)) return;
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0);
                if (context.getContentResolver().update(uri, values, null, null) != 1) throw new IOException();
                uri = null; // Published files belong to the user, not to the diagnostic cache.
                saved.add(job.index);
                saves.put(job.index, (PreviewRules.originalCandidate(job.url).isEmpty() ? "저장 완료 · 원본 미확인\n" : "저장 완료 · 루리웹 원본 후보 (대조 필요)\n") + bounds.outWidth + " × " + bounds.outHeight
                        + " · " + bounds.outMimeType + " · " + bytes.length + " bytes\n"
                        + "Download/BrowserDownloader/" + name + "\nSHA-256: " + hash
                        + "\n요청 주소 (쿼리 제외): " + NodeProbe.safeUrl(job.url));
                savedUrls.put(job.url, saves.get(job.index));
            }
        } catch (OutOfMemoryError e) {
            synchronized (this) { if (saveValid(job)) saves.put(job.index, "저장 실패 · 메모리 부족"); }
        } catch (Exception e) {
            synchronized (this) { if (saveValid(job)) saves.put(job.index, "저장 실패 · " + (job.timedOut ? "시간 제한 초과 (30초)" : safeFailure(e))); }
        } finally {
            timer.cancel(false);
            if (job.connection != null) job.connection.disconnect();
            boolean cleaned = true;
            if (uri != null) try { cleaned = context.getContentResolver().delete(uri, null, null) == 1; } catch (Exception ignored) { cleaned = false; }
            if (cleaned && journalOwned) context.getSharedPreferences("pending-download", 0).edit().remove("uri").commit();
            synchronized (this) { if (saving.get(job.index) == job) saving.remove(job.index); }
            changed();
        }
    }
    private boolean saveValid(Job job) {
        return !job.cancelled && current.equals(job.id) && saving.get(job.index) == job && ScanNotification.enabled(context);
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
    private PreviewStore(Context context) {
        this.context = context;
        directory = new File(context.getCacheDir(), "image-previews");
        directory.mkdirs();
        saver.submit(this::cleanInterruptedDownload);
        // Only our own interrupted temporary cache files are removed.
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".partial"));
        if (files != null) for (File file : files) file.delete();
    }
    synchronized String begin() {
        if (!clear()) throw new IllegalStateException("이전 미리보기 캐시 삭제 실패");
        current = UUID.randomUUID().toString();
        return current;
    }
    synchronized boolean open(String id) {
        if (!id.matches("[A-Za-z0-9-]{1,80}")) return false;
        if (current.isEmpty()) current = id; // After process death: no request URLs are restored.
        return current.equals(id);
    }
    synchronized void remember(String id, int index, String raw) {
        if (!id.equals(current)) return;
        String url = PreviewRules.requestUrl(raw);
        if (!url.isEmpty()) urls.put(index, url);
    }
    synchronized void listen(Runnable changed) { listener = changed; }
    private void changed() { main.post(() -> { Runnable callback; synchronized (this) { callback = listener; } if (callback != null) callback.run(); }); }
    synchronized void cancelRequests() {
        for (Job job : jobs.values()) job.cancel();
        for (Integer index : jobs.keySet()) states.remove(index);
        jobs.clear();
        ((ThreadPoolExecutor) workers).purge();
    }
    synchronized boolean clear() {
        cancelRequests();
        cancelSaves(); saves.clear(); saved.clear(); savedUrls.clear();
        current = ""; selection.clear(); revision++; urls.clear(); states.clear(); bitmaps.evictAll();
        File[] files = directory.listFiles();
        boolean deleted = true;
        if (files != null) for (File file : files) if (file.isFile() && !file.delete()) deleted = false;
        changed();
        return deleted;
    }
    synchronized boolean isCurrent(String id) { return id.equals(current); }
    synchronized Bitmap bitmap(String id, int index) { return id.equals(current) ? bitmaps.get(index) : null; }
    synchronized State state(String id, int index, boolean hasUrl) {
        if (!id.equals(current)) return new State("새 검사가 있습니다. 결과를 다시 열어 주세요.", 0, 0, false, false);
        State state = states.get(index);
        if (state != null) return state;
        return new State(hasUrl ? "미리보기 대기" : "이미지 검출됨 · 주소 없음", 0, 0, false, false);
    }
    synchronized int count(boolean success) {
        int count = 0;
        for (State state : states.values()) if (success ? state.success : state.failed) count++;
        return count;
    }
    synchronized void retainVisible(Set<Integer> visible) {
        Iterator<Map.Entry<Integer, Job>> iterator = jobs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Job> entry = iterator.next();
            if (!visible.contains(entry.getKey())) { entry.getValue().cancel(); states.remove(entry.getKey()); iterator.remove(); }
        }
        ((ThreadPoolExecutor) workers).purge();
    }
    synchronized void request(String id, int index, boolean hasUrl) {
        if (!current.equals(id) || jobs.containsKey(index) || bitmaps.get(index) != null) return;
        State state = states.get(index);
        if (state != null && !state.success) return;
        if (!hasUrl) return;
        File cache = new File(directory, id + "_" + index + ".thumb");
        if (!cache.isFile() && !ScanNotification.enabled(context)) {
            states.put(index, new State("검사 활성화 후 다시 검사 필요", 0, 0, false, false)); changed(); return;
        }
        if (!cache.isFile() && !consent(context)) {
            states.put(index, new State("미리보기 안내 동의 필요", 0, 0, false, false)); changed(); return;
        }
        String raw = urls.get(index);
        if (!cache.isFile() && raw == null) {
            states.put(index, new State("다시 검사 필요", 0, 0, false, false)); changed(); return;
        }
        Job job = new Job(id, index, raw, cache);
        jobs.put(index, job);
        states.put(index, new State("불러오는 중", 0, 0, false, false));
        job.future = workers.submit(job);
        changed();
    }
    private final class Job implements Runnable {
        final String id, url;
        final int index;
        final File cache;
        volatile boolean cancelled, timedOut;
        volatile HttpURLConnection connection;
        Future<?> future;
        Job(String id, int index, String url, File cache) { this.id=id; this.index=index; this.url=url; this.cache=cache; }
        void cancel() {
            cancelled = true;
            if (future != null) future.cancel(true);
            HttpURLConnection active = connection;
            if (active != null) deadlines.execute(active::disconnect);
        }
        void check() throws IOException {
            if (timedOut) throw new IOException("시간 제한 초과 (30초)");
            if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
        }
        @Override public void run() {
            Bitmap bitmap = null;
            State result;
            ScheduledFuture<?> timer = deadlines.schedule(() -> {
                timedOut = true;
                HttpURLConnection active = connection;
                if (active != null) active.disconnect();
            }, 30, TimeUnit.SECONDS);
            try {
                check();
                int width = 0, height = 0;
                if (cache.isFile()) {
                    try (DataInputStream input = new DataInputStream(new FileInputStream(cache))) {
                        width = input.readInt(); height = input.readInt();
                        bitmap = BitmapFactory.decodeStream(input);
                        if (bitmap == null || width <= 0 || height <= 0) throw new IOException();
                    } catch (IOException e) {
                        bitmap = null;
                        synchronized (PreviewStore.this) { if (!valid()) return; cache.delete(); }
                    }
                }
                if (bitmap == null) {
                    if (url == null) throw new IOException("다시 검사 필요");
                    if (!consent(context)) throw new IOException("미리보기 안내 동의 필요");
                    byte[] bytes = fetch();
                    BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                    width = bounds.outWidth; height = bounds.outHeight;
                    if (width <= 0 || height <= 0) throw new IOException("이미지 디코딩 실패");
                    if ((long) width * height > 100_000_000) throw new IOException("이미지 픽셀 제한 초과");
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = PreviewRules.sampleSize(width, height);
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                    if (bitmap == null) throw new IOException("이미지 디코딩 실패");
                }
                check();
                result = new State("미리보기 표시 · 원본 미확인", width, height, true, false);
                byte[] encoded = null;
                if (!cache.isFile()) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (DataOutputStream output = new DataOutputStream(bytes)) {
                        output.writeInt(width); output.writeInt(height);
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("캐시 저장 실패");
                    }
                    encoded = bytes.toByteArray();
                }
                check();
                synchronized (PreviewStore.this) {
                    if (!valid()) return;
                    if (encoded != null) {
                        File temporary = new File(directory, cache.getName()+".partial");
                        try {
                            trimCache(encoded.length);
                            try (FileOutputStream output = new FileOutputStream(temporary)) { output.write(encoded); }
                            if (!temporary.renameTo(cache)) throw new IOException();
                        } finally { temporary.delete(); }
                    }
                    bitmaps.put(index, bitmap);
                }
            } catch (OutOfMemoryError e) { result = new State("이미지 메모리 제한 초과", 0, 0, false, true); }
            catch (Exception e) {
                String message = timedOut ? "시간 제한 초과 (30초)" : safeFailure(e);
                result = new State(message, 0, 0, false, !message.equals("다시 검사 필요"));
            } finally {
                timer.cancel(false);
                if (connection != null) connection.disconnect();
            }
            synchronized (PreviewStore.this) {
                if (!valid()) return;
                jobs.remove(index); states.put(index, result);
            }
            changed();
        }
        boolean valid() { return !cancelled && id.equals(current) && jobs.get(index) == this; }
        byte[] fetch() throws Exception {
            String target = url;
            for (int redirects = 0; redirects <= 3; redirects++) {
                check();
                connection = (HttpURLConnection) URI.create(target).toURL().openConnection();
                connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
                connection.setConnectTimeout(10_000); connection.setReadTimeout(10_000);
                connection.setRequestProperty("Accept", "image/*");
                check();
                int status = connection.getResponseCode();
                if (status >= 300 && status <= 399) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || redirects == 3) throw new IOException("리디렉션 제한 초과");
                    String next = PreviewRules.requestUrl(URI.create(target).resolve(location).toString());
                    if (next.isEmpty()) throw new IOException("지원하지 않는 이미지 주소");
                    if ("pbs.twimg.com".equalsIgnoreCase(URI.create(url).getHost())
                            && (!"https".equalsIgnoreCase(URI.create(next).getScheme()) || !"pbs.twimg.com".equalsIgnoreCase(URI.create(next).getHost())))
                        throw new IOException("지원하지 않는 이미지 주소");
                    connection.disconnect(); target = next; continue;
                }
                if (status != 200) throw new IOException("HTTP 오류 " + status);
                String type = connection.getContentType();
                if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("image/")) throw new IOException("이미지 응답이 아님");
                long expected = connection.getContentLengthLong();
                if (expected > PreviewRules.MAX_BYTES) throw new IOException("파일 크기 제한 초과 (20MiB)");
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[16 * 1024];
                    for (int n; (n=input.read(buffer)) != -1;) {
                        check();
                        if ((long) output.size()+n > PreviewRules.MAX_BYTES) throw new IOException("파일 크기 제한 초과 (20MiB)");
                        output.write(buffer, 0, n);
                    }
                    if (expected >= 0 && expected != output.size()) throw new IOException("이미지 수신 크기 불일치");
                    return output.toByteArray();
                } catch (java.net.ProtocolException | java.io.EOFException e) {
                    throw new IOException("이미지 수신 크기 불일치", e);
                }
            }
            throw new IOException("리디렉션 제한 초과");
        }
    }
    private void trimCache(long extra) throws IOException {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".thumb"));
        if (files == null) throw new IOException("캐시 저장 실패");
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        long total = extra;
        for (File file : files) total += file.length();
        for (File file : files) if (total > PreviewRules.CACHE_BYTES) {
            long size = file.length(); if (file.delete()) total -= size;
        }
        if (total > PreviewRules.CACHE_BYTES) throw new IOException("캐시 용량 제한");
    }
    private static String safeFailure(Exception error) {
        if (dev.browserdownloader.xprobe.StorageErrors.isNoSpace(error)) return "저장 공간이 부족합니다. 공간을 확보한 뒤 다시 시도하세요.";
        if (error instanceof java.net.SocketTimeoutException) return "네트워크 응답 시간 초과";
        String text = error.getMessage();
        if (text != null && (text.matches("HTTP 오류 [0-9]{3}") || Set.of("다시 검사 필요", "미리보기 안내 동의 필요",
                "시간 제한 초과 (30초)", "이미지 디코딩 실패", "이미지 픽셀 제한 초과", "이미지 응답이 아님",
                "파일 크기 제한 초과 (20MiB)", "이미지 수신 크기 불일치", "리디렉션 제한 초과",
                "지원하지 않는 이미지 주소", "캐시 저장 실패", "캐시 용량 제한", "미완료 저장 복구 실패").contains(text))) return text;
        return "불러오기 실패 · 네트워크 또는 파일 확인 필요";
    }
    private boolean cleanInterruptedDownload() {
        try {
            dev.browserdownloader.xprobe.PublicDownloads.recover(context, "pending-download");
            return true;
        } catch (Exception ignored) { return false; }
    }
}
