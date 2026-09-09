package dev.browserdownloader.xprobe;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/** Media adapter using the source-built, hash-verified MOAMI runtime. */
public final class NativeProbe {
    private final Context context;
    private final File root;

    public NativeProbe(Context context) throws Exception {
        this.context = context.getApplicationContext();
        PrivateMediaFiles.recoverOnce(new File(this.context.getNoBackupFilesDir(), "x-media"));
        PublicDownloads.recover(this.context);
        root = NativeRuntime.install(this.context);
        File executable = new File(root, "yt-dlp/yt-dlp");
        if (!executable.getParentFile().mkdirs() && !executable.getParentFile().isDirectory()) throw new IOException("Runtime directory failed");
        byte[] bundled;
        try (java.io.InputStream input = context.getResources().openRawResource(R.raw.ytdlp)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = input.read(buffer)) != -1;) bytes.write(buffer, 0, n);
            bundled = bytes.toByteArray();
        }
        StringBuilder digest = new StringBuilder();
        for (byte b : java.security.MessageDigest.getInstance("SHA-256").digest(bundled)) digest.append(String.format("%02x", b));
        if (!digest.toString().equals("1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6")) {
            throw new IOException("번들 yt-dlp 해시 불일치");
        }
        // Update only from the hash-checked APK resource, never downloaded executable code.
        if (!executable.isFile() || !Arrays.equals(bundled, Files.readAllBytes(executable.toPath()))) Files.write(executable.toPath(), bundled);
    }

    private String run(String binary, int timeoutSeconds, String... args) throws Exception {
        ArrayList<String> command = new ArrayList<>();
        command.add(new File(context.getApplicationInfo().nativeLibraryDir, binary).getPath());
        command.addAll(Arrays.asList(args));
        File output = File.createTempFile("native-", ".txt", context.getCacheDir());
        File errorOutput = File.createTempFile("native-error-", ".txt", context.getCacheDir());
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectOutput(output).redirectError(errorOutput);
            builder.environment().put("LD_LIBRARY_PATH", new File(root,"usr/lib").getPath());
            builder.environment().put("PYTHONHOME", new File(root,"usr").getPath());
            builder.environment().put("SSL_CERT_FILE", new File(root,"usr/etc/tls/cert.pem").getPath());
            builder.environment().put("PYTHONDONTWRITEBYTECODE", "1");
            builder.environment().put("TMPDIR", context.getCacheDir().getPath());
            builder.environment().put("PYTHONIOENCODING", "utf-8");
            process = builder.start();
            process.getOutputStream().close();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new IOException("검사 제한 시간 초과");
            }
            if (output.length() + errorOutput.length() > 8 * 1024 * 1024) throw new IOException("검사 출력 한도 초과");
            String text = new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                text = new String(Files.readAllBytes(errorOutput.toPath()), StandardCharsets.UTF_8);
                // Avoid retaining remote response bodies or signed media URLs in logs.
                String reason = "unclassified";
                if (text.contains("CANNOT LINK EXECUTABLE") || text.contains("dlopen failed")) {
                    java.util.regex.Matcher detail = java.util.regex.Pattern.compile(
                            "(?:cannot locate symbol|library) \"([^\"]+)\"").matcher(text);
                    reason = "native-linker" + (detail.find() ? ": " + detail.group(1)
                            : ": " + text.substring(0, Math.min(1200, text.length())));
                }
                else if (text.contains("No video could be found")) reason = "no-video";
                else if (text.contains("Unable to extract")) reason = "extractor-schema";
                else if (text.contains("login") || text.contains("Sign in")) reason = "authentication";
                else if (text.contains("HTTP Error 403")) reason = "http-403";
                else if (text.contains("HTTP Error 429")) reason = "http-429";
                else if (text.contains("timed out")) reason = "network-timeout";
                else if (text.contains("No such filter")) reason = "missing-filter";
                if (reason.equals("unclassified")) {
                    java.util.regex.Matcher error = java.util.regex.Pattern.compile("(?m)^ERROR: (.+)$").matcher(text);
                    if (error.find()) reason = error.group(1)
                            .replaceAll("https?://\\S+", "[url]")
                            .replaceAll("(?i)(?:bearer|token|cookie|authorization)[=: ]+\\S+", "[redacted]");
                    reason = reason.substring(0, Math.min(500, reason.length()));
                }
                throw new IOException(binary + ": " + reason + " (exit " + process.exitValue() + ")");
            }
            return text.trim();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(output.toPath());
            Files.deleteIfExists(errorOutput.toPath());
        }
    }

    private String yt(int seconds, String... args) throws Exception {
        ArrayList<String> full = new ArrayList<>();
        full.add(new File(root, "yt-dlp/yt-dlp").getPath());
        full.addAll(Arrays.asList("--ignore-config", "--no-cache-dir", "--no-warnings", "--no-progress"));
        full.addAll(Arrays.asList(args));
        return run("libpython.so", seconds, full.toArray(new String[0]));
    }

    public JSONObject environment() throws Exception {
        JSONObject report = new JSONObject().put("runtime", "moami-source-built").put("android", Build.VERSION.RELEASE)
                .put("sdk", Build.VERSION.SDK_INT).put("abi", Build.SUPPORTED_ABIS[0])
                .put("python", run("libpython.so", 15, "--version"))
                .put("ytDlp", yt(20, "--version"));
        report.put("quickjs", run("libqjs.so",15,"-e","console.log('quickjs-ok')"));
        report.put("modules", run("libpython.so",15,"-c","import ssl,sqlite3,ctypes,bz2,lzma,zlib,hashlib,dbm.gnu; print('imports-ok')"));
        report.put("gifEncoder", "com.squareup:gifencoder:0.10.1");
        return report;
    }

    public JSONObject convertFixture() throws Exception {
        File dir = new File(context.getNoBackupFilesDir(), "fixture");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("시험 폴더 생성 실패");
        File mp4 = new File(dir, "self-authored.mp4");
        File gif = new File(dir, "converted.gif");
        try (java.io.InputStream input = context.getAssets().open("self_authored.mp4")) {
            Files.copy(input, mp4.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        byte[] before = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(mp4.toPath()));
        long start = SystemClock.elapsedRealtime();
        JSONObject conversion = GifConversion.convert(mp4, gif);
        long elapsed = SystemClock.elapsedRealtime() - start;
        byte[] header = Files.readAllBytes(gif.toPath());
        boolean preserved = Arrays.equals(before,
                java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(mp4.toPath())));
        boolean animated = ImageDecoder.decodeDrawable(ImageDecoder.createSource(gif)) instanceof AnimatedImageDrawable;
        boolean valid = new String(header, 0, 6, StandardCharsets.US_ASCII).equals("GIF89a")
                && conversion.getInt("width") == 160 && conversion.getInt("height") == 120
                && conversion.getInt("frames") == 20;
        if (!preserved || !animated || !valid) throw new IOException("MP4 보존 또는 GIF 재생 검증 실패");
        return new JSONObject().put("sample", "self-authored testsrc 160x120 10fps 2s")
                .put("mp4Preserved", preserved).put("androidAnimatedDecoder", animated)
                .put("conversion", conversion).put("conversionMs", elapsed)
                .put("mp4Bytes", mp4.length()).put("gifBytes", gif.length())
                .put("xDownloadTest", false).put("storage", "app-private/no-backup");
    }

    public JSONObject extractX(String link) throws Exception {
        String canonical = XLink.canonical(link);
        File script = new File(root, "x_extract.py");
        try (java.io.InputStream input = context.getAssets().open("x_extract.py")) {
            Files.copy(input, script.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        JSONObject raw = new JSONObject(run("libpython.so", 90, script.getPath(),
                new File(root, "yt-dlp/yt-dlp").getPath(), canonical));
        if (raw.has("error")) throw new IOException(raw.getString("error"));
        return raw;
    }

    public JSONObject extractInstagram(String link) throws Exception {
        String canonical = InstagramLink.canonical(link);
        File script = new File(root, "instagram_extract.py");
        try (java.io.InputStream input = context.getAssets().open("instagram_extract.py")) {
            Files.copy(input, script.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        JSONObject raw = new JSONObject(run("libpython.so", 90, script.getPath(),
                new File(root, "yt-dlp/yt-dlp").getPath(), canonical));
        if (raw.has("error")) throw new IOException(raw.getString("error"));
        return raw;
    }

    public JSONObject inspectX(String link) throws Exception {
        JSONObject raw = extractX(link);
        return new JSONObject().put("post", XLink.canonical(link)).put("extractor", raw.optString("extractor"))
                .put("downloaded", false).put("media", XMedia.summarize(raw));
    }

    public JSONObject downloadX(String link, boolean convertGif) throws Exception {
        return XMedia.downloadAll(context, extractX(link), convertGif);
    }
}
