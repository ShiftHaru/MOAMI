package dev.browserdownloader.xprobe;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.zip.ZipInputStream;

/** Installs only the signed APK's source-built ARM64 runtime into private storage. */
final class NativeRuntime {
    private static File installed;
    static synchronized File install(Context context) throws Exception {
        if (installed != null) return installed;
        String expected;
        try (var input = context.getAssets().open("native/runtime.sha256")) {
            byte[] bytes=new byte[65];int size=0;
            while(size<bytes.length){int n=input.read(bytes,size,bytes.length-size);if(n<0)break;size+=n;}
            if(input.read()!=-1)throw new IOException("Invalid runtime digest size");
            expected = new String(bytes,0,size, StandardCharsets.US_ASCII).trim();
        }
        if (!expected.matches("[0-9a-f]{64}")) throw new IOException("Invalid runtime digest");
        File parent = new File(context.getNoBackupFilesDir(), "moami-native");
        File target = new File(parent, expected);
        if (!new File(target,".ready").isFile()) {
            File staging = new File(parent, expected + ".tmp");
            remove(staging);
            if (!staging.mkdirs()) throw new IOException("Cannot prepare runtime");
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (var input = context.getAssets().open("native/runtime.zip")) {
                    byte[] buffer = new byte[65536];
                    for (int n; (n=input.read(buffer)) != -1;) digest.update(buffer,0,n);
                }
                StringBuilder actual = new StringBuilder();
                for (byte b : digest.digest()) actual.append(String.format("%02x", b));
                if (!expected.contentEquals(actual)) throw new IOException("Runtime digest mismatch");
                long total=0; int count=0;
                String base=staging.getCanonicalPath()+File.separator;
                try (var zip=new ZipInputStream(context.getAssets().open("native/runtime.zip"))) {
                    for (var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                        if (++count>10000) throw new IOException("Runtime entry limit");
                        File file=new File(staging,entry.getName());
                        if (!file.getCanonicalPath().startsWith(base)) throw new IOException("Invalid runtime path");
                        if (entry.isDirectory()) { if (!file.mkdirs()&&!file.isDirectory()) throw new IOException("Runtime directory"); continue; }
                        if (!file.getParentFile().mkdirs()&&!file.getParentFile().isDirectory()) throw new IOException("Runtime directory");
                        try(var out=new FileOutputStream(file)) {
                            byte[] buffer=new byte[65536];
                            for(int n;(n=zip.read(buffer))!=-1;) {
                                total+=n;if(total>128L*1024*1024)throw new IOException("Runtime size limit");
                                out.write(buffer,0,n);
                            }
                        }
                    }
                }
                if (!new File(staging,"usr/lib/python3.12/os.py").isFile()||!new File(staging,"usr/etc/tls/cert.pem").isFile()) throw new IOException("Incomplete runtime");
                Files.writeString(new File(staging,".ready").toPath(),expected);
                remove(target);
                if(!staging.renameTo(target))throw new IOException("Runtime activation failed");
            } finally { remove(staging); }
        }
        installed=target;return target;
    }
    private static void remove(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children=file.listFiles();
            if(children==null)throw new IOException("Runtime cleanup failed");
            for(File child:children)remove(child);
        }
        if(!file.delete())throw new IOException("Runtime cleanup failed");
    }
}
