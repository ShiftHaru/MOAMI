package dev.browserdownloader.probe;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Force-stop a real trickling HTTP receive, then verify no public partial and retry bytes. */
final class TransferCrashCheck {
    static void run(Instrumentation test,String phase) {
        Context context=test.getTargetContext(); Bundle out=new Bundle();
        SharedPreferences state=context.getSharedPreferences("transfer-crash-test",0);
        PreviewStore store=PreviewStore.get(context);
        ExecutorService serverWorker=Executors.newSingleThreadExecutor();
        int code=Activity.RESULT_CANCELED;
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) {
            context.getSharedPreferences("probe",0).edit().putInt("previewConsentVersion",1).putBoolean("enabled",true).commit();
            boolean prepare=phase.equals("transferPrepare");
            byte[] bytes=image();
            CountDownLatch started=new CountDownLatch(1);
            serverWorker.submit(()->{
                try(Socket client=server.accept()) {
                    BufferedReader input=new BufferedReader(new InputStreamReader(client.getInputStream()));
                    for(String line;(line=input.readLine())!=null&&!line.isEmpty();) {}
                    OutputStream output=client.getOutputStream();
                    output.write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                    if(prepare) {
                        output.write(bytes,0,8); output.flush(); started.countDown();
                        for(int i=8;i<bytes.length;i++) {Thread.sleep(500);output.write(bytes[i]);output.flush();}
                    } else {output.write(bytes);output.flush();}
                } catch(Exception ignored) { }
            });
            if(prepare) {
                require(!state.contains("capture"),"previous test requires verify");
                String capture=store.begin();
                require(state.edit().putString("capture",capture).putInt("pid",android.os.Process.myPid()).commit(),"test state commit");
                store.remember(capture,1,"http://127.0.0.1:"+server.getLocalPort()+"/transfer.png");store.save(capture,List.of(1));
                require(started.await(10,TimeUnit.SECONDS),"HTTP did not start"); Thread.sleep(500);
                require(store.saveStatus(1).equals("파일 수신·검증 중"),"not receiving");
                require(state.edit().putBoolean("receivingReady",true).commit(),"ready state commit");
                out.putString("report","READY: actual HTTP body trickling; receive in progress; force-stop now");test.sendStatus(1,out);
                // Durable marker distinguishes a genuine mid-receive kill from a later timeout.
                while(store.saveStatus(1).equals("파일 수신·검증 중")) Thread.sleep(50);
                state.edit().putBoolean("finishedBeforeKill",true).commit();
                throw new AssertionError("receive ended before force-stop");
            }
            String capture=state.getString("capture","");
            require(!capture.isEmpty()&&state.getInt("pid",-1)!=android.os.Process.myPid(),"prepared new process required");
            require(state.getBoolean("receivingReady",false),"receive was never observed");
            require(!state.getBoolean("finishedBeforeKill",false),"force-stop missed receive phase");
            require(rows(context,capture)==0,"interrupted receive published a file");
            require(context.getSharedPreferences("pending-download",0).getString("uri","").isEmpty(),"receive left journal");
            require(store.open(capture)&&!store.canSave(capture,1),"raw URL unexpectedly restored");
            store.remember(capture,1,"http://127.0.0.1:"+server.getLocalPort()+"/transfer.png");store.save(capture,List.of(1));
            long until=SystemClock.elapsedRealtime()+35000;
            while(store.saveStatus(1).equals("저장 대기")||store.saveStatus(1).equals("파일 수신·검증 중")) {require(SystemClock.elapsedRealtime()<until,"retry timeout");Thread.sleep(50);}
            require(store.saveStatus(1).startsWith("저장 완료"),"retry failed");store.save(capture,List.of(1));
            require(rows(context,capture)==1,"retry duplicated");
            try(var cursor=context.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,new String[]{"_id","is_pending"},"_display_name LIKE ?",new String[]{"Chrome_"+capture+"_%"},null)) {
                require(cursor!=null&&cursor.moveToFirst()&&cursor.getInt(1)==0,"retry not published");
                var uri=ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cursor.getLong(0));
                try(InputStream input=context.getContentResolver().openInputStream(uri)) {require(Arrays.equals(bytes,input.readAllBytes()),"retry bytes differ");}
                require(context.getContentResolver().delete(uri,null,null)==1,"fixture cleanup");
            }
            require(store.clear(),"cache cleanup");require(rows(context,capture)==0,"fixture row remains");state.edit().clear().commit();
            out.putString("report","PASS: killed during HTTP receive; new PID; no partial publish; URL not restored; retry exact bytes once; own fixture removed");code=Activity.RESULT_OK;
        } catch(Throwable e) {out.putString("report","FAIL transfer: "+e.getClass().getSimpleName()+" "+e.getMessage());}
        finally {serverWorker.shutdownNow();}
        test.finish(code,out);
    }
    private static int rows(Context context,String capture) {
        try(var cursor=context.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,new String[]{"_id"},"_display_name LIKE ?",new String[]{"Chrome_"+capture+"_%"},null)) {require(cursor!=null,"query failed");return cursor.getCount();}
    }
    private static byte[] image() throws IOException {
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(32,32,android.graphics.Bitmap.Config.ARGB_8888);
        try(ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {bitmap.eraseColor(0xff3589b2);require(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes),"encode");return bytes.toByteArray();}
        finally {bitmap.recycle();}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
