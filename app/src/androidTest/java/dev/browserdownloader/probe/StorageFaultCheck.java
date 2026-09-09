package dev.browserdownloader.probe;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import dev.browserdownloader.xprobe.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** ENOSPC fault injection at the provider boundary; never fills the user's storage. */
final class StorageFaultCheck {
    static void run(Instrumentation test) {
        Bundle out=new Bundle();
        Context context=test.getTargetContext();
        FaultProvider provider=new FaultProvider(context.getContentResolver());
        File source=null;
        PreviewStore store=null;
        String publishedKey=null;
        int code=Activity.RESULT_CANCELED;
        try {
            require(!StorageErrors.isNoSpace(new android.system.ErrnoException("fixture",android.system.OsConstants.EACCES)),"permission failure is not no-space");
            require(StorageErrors.isNoSpace(new android.system.ErrnoException("fixture",android.system.OsConstants.EDQUOT)),"quota classified");
            android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo(); info.authority="media";
            provider.attachInfo(context,info);
            ContentResolver resolver=ContentResolver.wrap(provider);
            Context wrapped=new ContextWrapper(context) { @Override public ContentResolver getContentResolver() { return resolver; } };
            source=File.createTempFile("storage-fault-", ".png",context.getCacheDir());
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(4,4,android.graphics.Bitmap.Config.ARGB_8888);
            try(OutputStream output=new FileOutputStream(source)) { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output); }
            finally { bitmap.recycle(); }
            String hash;
            try(InputStream input=new FileInputStream(source)) { hash=PublicDownloads.sha256(input); }
            publishedKey=source.getName()+":"+hash;
            try { PublicDownloads.publish(wrapped,source,"image/png"); throw new AssertionError("ENOSPC not raised"); }
            catch(IOException e) { require(StorageErrors.isNoSpace(e),"injected ENOSPC propagated"); require(FailureText.describe(e).contains("저장 공간"),"X storage reason"); }
            provider.requireAbsent();
            require(context.getSharedPreferences("x-pending-download",0).getString("uri","").isEmpty(),"X journal cleaned");
            provider.full=false;
            Uri saved=PublicDownloads.publish(wrapped,source,"image/png");
            require(saved.equals(PublicDownloads.publish(wrapped,source,"image/png")),"X retry duplicate");
            try(InputStream input=resolver.openInputStream(saved)) { require(hash.equals(PublicDownloads.sha256(input)),"X retry hash"); }
            provider.removeOwn();

            context.getSharedPreferences("probe",0).edit().putInt("previewConsentVersion",1).putBoolean("enabled",true).commit();
            java.lang.reflect.Constructor<PreviewStore> constructor=PreviewStore.class.getDeclaredConstructor(Context.class);
            constructor.setAccessible(true); store=constructor.newInstance(wrapped);
            String capture=store.begin(); store.remember(capture,1,"http://127.0.0.1:8787/images/decorative.png");
            provider.full=true; store.save(capture,List.of(1)); await(store);
            require(store.saveStatus(1).contains("저장 공간이 부족"),"Chrome storage reason: "+store.saveStatus(1));
            provider.requireAbsent();
            require(context.getSharedPreferences("pending-download",0).getString("uri","").isEmpty(),"Chrome journal cleaned");
            provider.full=false; store.save(capture,List.of(1)); await(store);
            require(store.saveStatus(1).startsWith("저장 완료"),"Chrome retry failed");
            out.putString("report","PASS: injected ENOSPC; Chrome/X failure reasons; no pending files; retry and completed bytes verified");
            code=Activity.RESULT_OK;
        } catch(Throwable e) {
            out.putString("report","FAIL storageFault: "+e.getClass().getSimpleName()+" "+e.getMessage());
        } finally {
            try {
            if(store!=null) require(store.clear(),"fixture cache cleanup");
            provider.removeOwn();
            if(source!=null) Files.deleteIfExists(source.toPath());
            if(publishedKey!=null) context.getSharedPreferences("published",0).edit().remove(publishedKey).commit();
            } catch(Exception e) { code=Activity.RESULT_CANCELED; out.putString("report","FAIL fixture cleanup"); }
        }
        test.finish(code,out);
    }
    private static void await(PreviewStore store) throws Exception {
        long end=SystemClock.elapsedRealtime()+35000;
        while(SystemClock.elapsedRealtime()<end) {
            String state=store.saveStatus(1); if(state.startsWith("저장 실패")||state.startsWith("저장 완료")) return;
            Thread.sleep(100);
        }
        throw new Exception("save timeout");
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    static final class FaultProvider extends ContentProvider {
        final ContentResolver real;
        final List<Uri> own=new ArrayList<>();
        volatile boolean full=true;
        java.util.function.Consumer<Uri> afterPublish;
        FaultProvider(ContentResolver real) { this.real=real; }
        public boolean onCreate(){return true;}
        public String getType(Uri uri){return real.getType(uri);}
        public Cursor query(Uri u,String[] p,String s,String[] a,String o){return real.query(u,p,s,a,o);}
        public Uri insert(Uri u,ContentValues v){Uri saved=real.insert(u,v);if(saved!=null)own.add(saved);return saved;}
        public int delete(Uri u,String s,String[] a){return real.delete(u,s,a);}
        public int update(Uri u,ContentValues v,String s,String[] a){
            int count=real.update(u,v,s,a);
            if(count==1 && afterPublish!=null && Integer.valueOf(0).equals(v.getAsInteger("is_pending"))) afterPublish.accept(u);
            return count;
        }
        public ParcelFileDescriptor openFile(Uri u,String mode) throws FileNotFoundException {
            if(full && mode.contains("w")) {
                FileNotFoundException error=new FileNotFoundException("fixture ENOSPC");
                error.initCause(new android.system.ErrnoException("fixture-write",android.system.OsConstants.ENOSPC));
                throw error;
            }
            return real.openFileDescriptor(u,mode);
        }
        void requireAbsent() {
            for(Uri uri:own) try(Cursor row=real.query(uri,new String[]{"_id"},null,null,null)) { require(row!=null&&!row.moveToFirst(),"pending row remains"); }
        }
        void removeOwn() { for(Uri uri:own) real.delete(uri,null,null); requireAbsent(); own.clear(); }
    }
}
