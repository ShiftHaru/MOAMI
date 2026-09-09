package dev.browserdownloader.probe;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import org.json.*;

/** Actual Chrome page -> captured node -> product save -> independent reference comparison. */
final class ChromeSiteCheck {
    static void run(Instrumentation test,Context context,UiAutomation ui,String mode) {
        Bundle out=new Bundle(); int code=Activity.RESULT_CANCELED;
        try {
            boolean ruliweb=mode.startsWith("siteRuliweb"), shared=mode.equals("siteRuliwebShare");
            String page=ruliweb?"https://m.ruliweb.com/best/board/300143/read/76596340":"https://en.wikipedia.org/wiki/Android_(operating_system)";
            File latest=new File(context.getFilesDir(),"latest.json");
            String previous=latest.isFile()?new JSONObject(Files.readString(latest.toPath())).optString("captureId"):"";
            context.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(page)).setPackage("com.android.chrome")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("create_new_tab",true));
            String marker=ruliweb?"1a07f250aef176a40.webp":"Android_2023_3D_logo_and_wordmark";
            long readyBy=SystemClock.elapsedRealtime()+120000;
            while(!imageReady(ui,marker)) {require(SystemClock.elapsedRealtime()<readyBy,"page did not expose the reference image before collection");Thread.sleep(500);}
            if(shared) {
                context.startActivity(new Intent(Intent.ACTION_SEND).setClass(context,MainActivity.class).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT,page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                ChromeBoundaryCheck.click(ui,"공유한 Chrome 탭으로 돌아가기",10000);
            }
            ChromeBoundaryCheck.click(ui,"<",20000);
            ChromeBoundaryCheck.click(ui,shared?"공유 탭 누적 검사":"자동 누적 검사 (3분)",10000);
            JSONObject record=null,selected=null;
            long findBy=SystemClock.elapsedRealtime()+190000;
            while(SystemClock.elapsedRealtime()<findBy) {
                Thread.sleep(500);
                if(!latest.isFile()) continue;
                record=new JSONObject(Files.readString(latest.toPath()));
                if(record.optString("captureId").equals(previous)) continue;
                JSONArray nodes=record.optJSONArray("nodes"); if(nodes==null) continue;
                for(int i=0;i<nodes.length();i++) {
                    JSONObject node=nodes.getJSONObject(i);String url=node.optString("targetUrl");
                    if(url.contains(marker)) {selected=node;break;}
                }
                if(selected!=null || record.optBoolean("collectionFinished")) break;
            }
            require(selected!=null,"reference image not found in actual Chrome collection");
            if(shared) require(record.optString("entry").equals("ACTION_SEND_CURRENT_TAB")&&SharedPage.pending.peek(SystemClock.elapsedRealtime()).isEmpty(),"share ticket not consumed by current-tab collection");
            ChromeBoundaryCheck.click(ui,"수집 중단 · 결과 유지",10000);
            String capture=record.getString("captureId");int index=selected.getInt("index");
            PreviewStore store=PreviewStore.get(context);
            String reference;
            if(ruliweb) reference="https://i1.ruliweb.com/ori/26/09/08/1a07f250aef176a40.webp";
            else {
                java.lang.reflect.Field field=PreviewStore.class.getDeclaredField("urls");field.setAccessible(true);
                synchronized(store) {reference=(String)((Map<?,?>)field.get(store)).get(index);}
                require(reference!=null,"session URL missing");
            }
            byte[] expected=fetch(reference);
            String expectedHash=dev.browserdownloader.xprobe.PublicDownloads.sha256(new ByteArrayInputStream(expected));
            if(ruliweb) require(expectedHash.equals("6ad88399d8c7c1ea3770e8c831adec0e2706225020a8cf6ba8d6972bcc3bc14d"),"reference differs from user's Chrome saved baseline");
            store.save(capture,List.of(index));long savedBy=SystemClock.elapsedRealtime()+35000;
            while(!store.saveStatus(index).startsWith("저장 완료")&&!store.saveStatus(index).startsWith("저장 실패")) {require(SystemClock.elapsedRealtime()<savedBy,"save timeout");Thread.sleep(100);}
            require(store.saveStatus(index).startsWith("저장 완료"),"product save failed: "+store.saveStatus(index));
            try(var cursor=context.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,new String[]{"_id","is_pending"},
                    "_display_name LIKE ?",new String[]{"Chrome_"+capture+"_"+index+".%"},null)) {
                require(cursor!=null&&cursor.getCount()==1&&cursor.moveToFirst()&&cursor.getInt(1)==0,"one published file required");
                var uri=ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cursor.getLong(0));
                try(InputStream input=context.getContentResolver().openInputStream(uri)) {require(Arrays.equals(expected,input.readAllBytes()),"saved bytes differ from reference");}
            }
            out.putString("report","PASS "+mode+": actual Chrome node/save/reference bytes match; "+expected.length+" bytes; sha256="+expectedHash
                    +(ruliweb?"; matches user Chrome baseline":"; detected resource only, source original unverified"));
            code=Activity.RESULT_OK;
        } catch(Throwable e) {out.putString("report","FAIL "+mode+": "+e.getClass().getSimpleName()+
                (e instanceof AssertionError || (e.getMessage()!=null && e.getMessage().startsWith("button unavailable:"))?" "+e.getMessage():""));}
        test.finish(code,out);
    }
    private static byte[] fetch(String url) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(10000);connection.setReadTimeout(10000);connection.setRequestProperty("Accept","image/*");
        try {require(connection.getResponseCode()==200,"reference HTTP "+connection.getResponseCode());
            try(InputStream input=connection.getInputStream()) {byte[] bytes=input.readNBytes(20*1024*1024+1);require(bytes.length<=20*1024*1024,"reference exceeds cap");return bytes;}}
        finally {connection.disconnect();}
    }
    static boolean imageReady(UiAutomation ui,String marker) {
        var root=ui.getRootInActiveWindow();if(root==null)return false;
        ArrayDeque<android.view.accessibility.AccessibilityNodeInfo> nodes=new ArrayDeque<>();nodes.add(root);boolean found=false;
        for(int n=0;!nodes.isEmpty()&&n<5000;n++) {
            var node=nodes.removeFirst();
            if(!node.isPassword()) {
                found=String.valueOf(node.getExtras().getCharSequence(NodeProbe.TARGET_URL,"")).contains(marker);
                if(!found)for(int i=0;i<node.getChildCount();i++){var child=node.getChild(i);if(child!=null)nodes.add(child);}
            }
            node.recycle();if(found)break;
        }
        while(!nodes.isEmpty())nodes.removeFirst().recycle();return found;
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
