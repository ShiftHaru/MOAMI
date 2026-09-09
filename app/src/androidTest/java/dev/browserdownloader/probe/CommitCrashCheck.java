package dev.browserdownloader.probe;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import dev.browserdownloader.xprobe.PublicDownloads;
import java.io.*;
import java.nio.file.Files;

/** Real force-stop immediately after MediaStore publishes, before publish() returns. */
final class CommitCrashCheck {
    static void run(Instrumentation test,String phase) {
        Context context=test.getTargetContext(); Bundle out=new Bundle();
        var state=context.getSharedPreferences("commit-crash-test",0);
        boolean prepare=phase.equals("commitPrepare");int code=Activity.RESULT_CANCELED;
        File source=null;Uri retry=null;
        try {
            if(prepare) {
                require(!state.contains("source"),"previous fixture needs verification");
                PublicDownloads.recover(context);
                source=File.createTempFile("commit-crash-",".png",context.getCacheDir());
                var bitmap=android.graphics.Bitmap.createBitmap(4,4,android.graphics.Bitmap.Config.ARGB_8888);
                try(OutputStream stream=new FileOutputStream(source)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}finally{bitmap.recycle();}
                String hash;try(InputStream stream=new FileInputStream(source)){hash=PublicDownloads.sha256(stream);}
                require(state.edit().putString("source",source.getName()).putString("hash",hash).putInt("pid",android.os.Process.myPid()).commit(),"fixture state");
                var provider=new StorageFaultCheck.FaultProvider(context.getContentResolver());provider.full=false;
                var info=new android.content.pm.ProviderInfo();info.authority="media";provider.attachInfo(context,info);
                provider.afterPublish=uri->{
                    require(state.edit().putString("uri",uri.toString()).commit(),"published state");
                    out.putString("report","READY: actual MediaStore publication completed; force-stop before method return");test.sendStatus(1,out);
                    try{Thread.sleep(120000);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                    throw new AssertionError("force-stop was not performed");
                };
                ContentResolver resolver=ContentResolver.wrap(provider);
                Context wrapped=new ContextWrapper(context){@Override public ContentResolver getContentResolver(){return resolver;}};
                PublicDownloads.publish(wrapped,source,"image/png");throw new AssertionError("missing publication interception");
            }
            require(state.contains("uri")&&state.getInt("pid",-1)!=android.os.Process.myPid(),"prepared new process required");
            String name=state.getString("source","");require(name.matches("commit-crash-[A-Za-z0-9-]+\\.png"),"fixture name");
            source=new File(context.getCacheDir(),name);Uri original=Uri.parse(state.getString("uri",""));
            PublicDownloads.recover(context);
            try(InputStream stream=context.getContentResolver().openInputStream(original)){require(state.getString("hash","").equals(PublicDownloads.sha256(stream)),"published file lost");}
            retry=PublicDownloads.publish(context,source,"image/png");
            require(original.equals(retry),"retry duplicated a file published immediately before kill");
            out.putString("report","PASS: killed after actual publication; original preserved; retry returned same URI");code=Activity.RESULT_OK;
        }catch(Throwable e){out.putString("report","FAIL commit: "+e.getClass().getSimpleName()+(e instanceof AssertionError?" "+e.getMessage():""));}
        finally {
            if(!prepare && source!=null) try {
                Uri original=Uri.parse(state.getString("uri",""));
                if(retry!=null&&!retry.equals(original))context.getContentResolver().delete(retry,null,null);
                context.getContentResolver().delete(original,null,null);
                context.getSharedPreferences("published",0).edit().remove(source.getName()+":"+state.getString("hash","")).commit();
                Files.deleteIfExists(source.toPath());state.edit().clear().commit();
            }catch(Exception e){code=Activity.RESULT_CANCELED;out.putString("report","FAIL own fixture cleanup");}
        }
        test.finish(code,out);
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
