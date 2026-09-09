package dev.browserdownloader.xprobe;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import java.io.*;
import java.nio.file.Files;

/** Explicit prepare -> external force-stop -> verify. Uses only its own fixture files. */
public final class CrashRecoveryCheck {
    public static void run(Instrumentation test, String phase) {
        Bundle result = new Bundle();
        Context context = test.getTargetContext();
        SharedPreferences state = context.getSharedPreferences("crash-recovery-test",0);
        File directory = new File(context.getNoBackupFilesDir(),"x-media");
        try {
            if (phase.equals("prepare")) {
                if (state.contains("id")) throw new IOException("previous test requires verification");
                new NativeProbe(context);
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("directory");
                String id = Long.toString(System.currentTimeMillis());
                File source = new File(context.getCacheDir(),"crash-check-"+id+".png");
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(4,4,android.graphics.Bitmap.Config.ARGB_8888);
                try(OutputStream out = new FileOutputStream(source)) { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out); }
                finally { bitmap.recycle(); }
                String hash;
                try(InputStream in = new FileInputStream(source)) { hash = PublicDownloads.sha256(in); }
                Uri completed = PublicDownloads.publish(context,source,"image/png");
                state.edit().putString("id",id).putString("completed",completed.toString()).putString("hash",hash)
                    .putInt("pid",android.os.Process.myPid()).commit();
                for(String name : names(id)) Files.write(new File(directory,name).toPath(),new byte[]{1,2,3});
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME,"crash-check-"+id+".pending.png");
                values.put(MediaStore.Downloads.MIME_TYPE,"image/png");
                values.put(MediaStore.Downloads.RELATIVE_PATH,"Download/BrowserDownloader");
                values.put(MediaStore.Downloads.IS_PENDING,1);
                Uri pending=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
                if(pending==null) throw new IOException("insert");
                state.edit().putString("pending",pending.toString()).commit();
                if(!context.getSharedPreferences(PublicDownloads.JOURNAL,0).edit().putString("uri",pending.toString()).commit()) throw new IOException("journal");
                try(OutputStream out=context.getContentResolver().openOutputStream(pending)) { out.write(new byte[]{1,2,3}); }
                result.putString("report","READY for force-stop; fixtures and journal committed");
                test.sendStatus(1,result);
                Thread.sleep(120000);
                throw new IOException("force-stop was not performed");
            }
            if(!phase.equals("verify") || !state.contains("id")) throw new IOException("no prepared test");
            String id=state.getString("id","");
            if(state.getInt("pid",-1)==android.os.Process.myPid()) throw new IOException("same process");
            new NativeProbe(context);
            String[] names=names(id);
            for(int i=0;i<names.length-1;i++) if(new File(directory,names[i]).exists()) throw new IOException("private temporary file remains");
            if(!java.util.Arrays.equals(new byte[]{1,2,3},Files.readAllBytes(new File(directory,names[4]).toPath()))) throw new IOException("private completed file changed");
            Uri pending=Uri.parse(state.getString("pending",""));
            try(android.database.Cursor row=context.getContentResolver().query(pending,new String[]{"_id"},null,null,null)) {
                if(row==null || row.moveToFirst()) throw new IOException("pending file remains");
            }
            Uri completed=Uri.parse(state.getString("completed",""));
            String hash=state.getString("hash","");
            try(InputStream in=context.getContentResolver().openInputStream(completed)) {
                if(!hash.equals(PublicDownloads.sha256(in))) throw new IOException("completed file changed");
            }
            File source=new File(context.getCacheDir(),"crash-check-"+id+".png");
            if(!completed.equals(PublicDownloads.publish(context,source,"image/png"))) throw new IOException("retry duplicated");
            if(context.getContentResolver().delete(completed,null,null)!=1) throw new IOException("fixture cleanup");
            Files.delete(source.toPath()); Files.delete(new File(directory,names[4]).toPath());
            context.getSharedPreferences("published",0).edit().remove(source.getName()+":"+hash).commit();
            state.edit().clear().commit();
            result.putString("report","PASS: new process; private partial/copy cleanup; pending deletion; completed bytes preserved; retry same URI; own fixtures removed");
            test.finish(Activity.RESULT_OK,result);
        } catch(Exception e) {
            result.putString("report","FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage());
            test.finish(Activity.RESULT_CANCELED,result);
        }
    }
    private static String[] names(String id) {
        return new String[]{id+".mp4.partial",id+".photo.partial",id+".converted.gif.partial","mp4-decode-"+id+".mp4",id+".mp4"};
    }
}
