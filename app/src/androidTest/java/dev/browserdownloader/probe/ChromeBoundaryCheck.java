package dev.browserdownloader.probe;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.*;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;

/** Real Chrome + accessibility overlay; never invokes collection internals directly. */
final class ChromeBoundaryCheck {
    static void run(Instrumentation test,String mode) {
        Bundle output=new Bundle();
        try {
            Context context=test.getTargetContext();
            UiAutomation ui=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            android.accessibilityservice.AccessibilityServiceInfo info=ui.getServiceInfo();
            info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            ui.setServiceInfo(info);
            if(mode.equals("notificationVerify")) {
                if(context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED
                    || ScanNotification.allowed(context)) throw new Exception("notification permission not revoked");
                JSONObject interrupted=new JSONObject(Files.readString(new File(context.getFilesDir(),"latest.json").toPath()));
                if(!interrupted.optBoolean("collectionMode") || interrupted.optBoolean("complete") || interrupted.optInt("passes")<2) throw new Exception("partial collection not preserved");
                shell(ui,"am start -n "+context.getPackageName()+"/.MainActivity");
                if(!hasText(ui,"실행 알림 차단됨",10000)) throw new Exception("notification denial missing from screen");
                output.putString("report","PASS notification revoke; blocked status shown; partial collection preserved");
                test.finish(Activity.RESULT_OK,output); return;
            }
            // Instrumentation restarts the target process; rebind its service for this device test.
            String services=android.provider.Settings.Secure.getString(context.getContentResolver(),"enabled_accessibility_services");
            String base=services==null||services.equals("null")?"":java.util.Arrays.stream(services.split(":"))
                .filter(s->!s.startsWith(context.getPackageName()+"/")).collect(java.util.stream.Collectors.joining(":"));
            if(!base.matches("[A-Za-z0-9_.$/:]*")) throw new Exception("unexpected service list");
            shell(ui,"settings put secure enabled_accessibility_services "+(base.isEmpty()?"null":base));
            Thread.sleep(500);
            shell(ui,"settings put secure enabled_accessibility_services "+(base.isEmpty()?"":base+":")+context.getPackageName()+"/.ChromeProbeService");
            shell(ui,"settings put secure accessibility_enabled 1");
            long connectedBy=SystemClock.elapsedRealtime()+10000;
            while(!ScanNotification.connected() && SystemClock.elapsedRealtime()<connectedBy) Thread.sleep(100);
            if(!ScanNotification.connected()) throw new Exception("accessibility service not connected");
            context.getSharedPreferences("probe",0).edit().putInt("previewConsentVersion",1).putBoolean("enabled",true).commit();
            if(mode.equals("manual")){ManualScanCheck.run(test,context,ui);return;}
            if(mode.equals("xShare")){XShareCheck.run(test,context,ui);return;}
            if(java.util.Set.of("httpCapture","httpShare","httpMismatch","lookupStop","lookupTab").contains(mode)) {
                ChromeAddressCheck.run(test,context,ui,mode); return;
            }
            if(java.util.Set.of("xOther","xRotate","xNotify").contains(mode)) {
                XWorkBoundaryCheck.run(test,context,ui,mode); return;
            }
            if(java.util.Set.of("siteRuliweb","siteWikipedia","siteRuliwebShare").contains(mode)) {
                ChromeSiteCheck.run(test,context,ui,mode); return;
            }
            File latest=new File(context.getFilesDir(),"latest.json");
            String previous=latest.isFile()?new JSONObject(Files.readString(latest.toPath())).optString("captureId"):"";
            String page=(mode.equals("count")||mode.equals("grid"))?"count":mode.equals("static")?"fixture":mode.equals("infinite")?"infinite":"duration";
            open(context,page);
            if(mode.equals("addressProbe")) {
                Thread.sleep(1500);
                var finder=ChromeProbeService.class.getDeclaredMethod("findDocument",AccessibilityNodeInfo.class);
                finder.setAccessible(true);
                AccessibilityNodeInfo root=ui.getRootInActiveWindow();
                AccessibilityNodeInfo before=(AccessibilityNodeInfo)finder.invoke(null,root);root.recycle();
                if(before==null) throw new Exception("fixture document missing");
                try {
                    for(String id:new String[]{"location_bar_status_icon","page_info_truncated_url"}) {
                        root=ui.getRootInActiveWindow();boolean clicked=false;
                        try {for(var node:root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/"+id)) {
                            var action=AccessibilityNodeInfo.obtain(node);
                            for(int depth=0;action!=null&&depth<4;depth++) {
                                if(action.isClickable()){clicked|=action.performAction(AccessibilityNodeInfo.ACTION_CLICK);action.recycle();action=null;break;}
                                var parent=action.getParent();action.recycle();action=parent;
                            }
                            if(action!=null)action.recycle();node.recycle();
                        }}finally{root.recycle();}
                        if(!clicked) throw new Exception("page info action unavailable: "+id);
                        Thread.sleep(500);
                    }
                    root=ui.getRootInActiveWindow();String full="";
                    try {for(var node:root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/page_info_url")) {
                        full=String.valueOf(node.getText());node.recycle();
                    }}finally{root.recycle();}
                    if(!full.equals("http://127.0.0.1:8787/duration.html")) throw new Exception("full fixture URL mismatch");
                    root=ui.getRootInActiveWindow();boolean closed=false;
                    try {for(var node:root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/page_info_close")) {
                        closed|=node.performAction(AccessibilityNodeInfo.ACTION_CLICK);node.recycle();
                    }}finally{root.recycle();}
                    if(!closed) throw new Exception("page info close unavailable");
                    Thread.sleep(500);
                    root=ui.getRootInActiveWindow();
                    var after=(AccessibilityNodeInfo)finder.invoke(null,root);root.recycle();
                    try {if(after==null||!before.equals(after)) throw new Exception("document changed after page info");}
                    finally{if(after!=null)after.recycle();}
                    output.putString("report","PASS full HTTP URL via page info accessibility IDs; same document after close");
                    test.finish(Activity.RESULT_OK,output);return;
                }finally{before.recycle();}
            }
            click(ui,"<",20000);
            click(ui,"자동 누적 검사 (3분)",10000);
            JSONObject record=null;
            long end=SystemClock.elapsedRealtime()+210000;
            boolean changed=false;
            while(SystemClock.elapsedRealtime()<end) {
                Thread.sleep(500);
                if(!latest.isFile()) continue;
                record=new JSONObject(Files.readString(latest.toPath()));
                if(record.optString("captureId").equals(previous)) continue;
                if(!record.optBoolean("collectionMode")) continue;
                if(!changed && record.optInt("passes")>=2) {
                    if(mode.equals("notificationPrepare")) {
                        output.putString("report","READY notification permission revoke during collection"); test.sendStatus(1,output);
                        Thread.sleep(120000); throw new Exception("permission was not revoked");
                    }
                    if(mode.equals("tab")) { open(context,"other"); changed=true; }
                    if(mode.equals("tabSame")) { open(context,"duration"); changed=true; }
                    if(mode.equals("lock")) {
                        changed=true;
                        try { shell(ui,"input keyevent KEYCODE_SLEEP"); Thread.sleep(1800); }
                        finally { shell(ui,"input keyevent KEYCODE_WAKEUP"); shell(ui,"wm dismiss-keyguard"); }
                    }
                    if(mode.equals("consent")) { context.getSharedPreferences("probe",0).edit().putInt("previewConsentVersion",0).putBoolean("enabled",false).commit(); changed=true; }
                    if(mode.equals("accessibility")) { shell(ui,"settings put secure enabled_accessibility_services "+(base.isEmpty()?"null":base)); changed=true; }
                    if(mode.equals("otherapp")) { context.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); changed=true; }
                }
                if(record.optBoolean("collectionFinished")) break;
            }
            if(record==null || record.optString("captureId").equals(previous) || !record.optBoolean("collectionFinished")) throw new Exception("new collection did not finish");
            output.putString("record",record.toString());
            if(record.optBoolean("complete")) throw new Exception("partial collection marked complete");
            String status=record.optString("collectionStatus");
            if(mode.equals("count") && (record.optInt("uniqueImageUrls")!=500 || !status.contains("500"))) throw new Exception("500 URL boundary failed");
            if(mode.equals("infinite") && (record.optInt("uniqueImageUrls")!=500 || !status.contains("500") || !record.toString().contains("infinite-499"))) throw new Exception("infinite scroll accumulation failed");
            if(mode.equals("duration") && (record.optLong("elapsedMs")<180000 || !status.contains("3분"))) throw new Exception("duration boundary failed");
            if(mode.equals("static") && (!record.toString().contains("g1-direct-original") || !record.toString().contains("g1-late-original"))) throw new Exception("static or lazy image missing");
            if(java.util.Set.of("tab","tabSame","lock","consent","accessibility","otherapp").contains(mode) && !changed) throw new Exception("transition not exercised");
            if(mode.equals("lock")) {
                if(!status.contains("잠금") && !status.contains("화면")) throw new Exception("lock did not interrupt collection");
                boolean locked=((KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked();
                output.putBoolean("unlockRequired",locked);
                if(!locked) {
                    click(ui,"<",10000); click(ui,"자동 누적 검사 (3분)",10000);
                    JSONObject resumed=null;
                    long resumeBy=SystemClock.elapsedRealtime()+10000;
                    while(SystemClock.elapsedRealtime()<resumeBy) {
                        resumed=new JSONObject(Files.readString(latest.toPath()));
                        if(!resumed.optString("captureId").equals(record.optString("captureId")) && resumed.optInt("passes")>0) break;
                        Thread.sleep(200);
                    }
                    if(resumed==null || resumed.optString("captureId").equals(record.optString("captureId"))
                            || resumed.optInt("passes")==0) throw new Exception("unlock did not resume a fresh collection");
                    click(ui,"수집 중단 · 결과 유지",10000);
                    resumed=new JSONObject(Files.readString(latest.toPath()));
                    if(!resumed.optBoolean("collectionFinished") || resumed.optBoolean("complete")) throw new Exception("resumed collection did not stop cleanly");
                    output.putBoolean("resumedWithoutRebind",true);
                }
            }
            if(mode.equals("tabSame") && (!status.contains("변경") && !status.contains("전환"))) throw new Exception("same URL tab was not distinguished");
            if(mode.equals("tab") && record.toString().contains("other-document")) throw new Exception("new document mixed into old collection");
            if(mode.equals("grid")) {
                if(record.optInt("uniqueImageUrls")!=500) throw new Exception("grid fixture not fully collected");
                context.startActivity(new Intent(context,ResultsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                click(ui,"모두 선택",15000);
                if(!hasText(ui,"선택 500개",5000)) throw new Exception("select all failed");
                android.graphics.Rect box=checkbox(ui);
                java.util.concurrent.atomic.AtomicBoolean scrolled=new java.util.concurrent.atomic.AtomicBoolean();
                ui.setOnAccessibilityEventListener(e->{ if(e.getEventType()==AccessibilityEvent.TYPE_VIEW_SCROLLED
                    && "android.widget.GridView".equals(String.valueOf(e.getClassName()))) scrolled.set(true); });
                long down=SystemClock.uptimeMillis();
                for(int i=0;i<=8;i++) {
                    android.view.MotionEvent event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),
                        i==0?android.view.MotionEvent.ACTION_DOWN:i==8?android.view.MotionEvent.ACTION_UP:android.view.MotionEvent.ACTION_MOVE,
                        box.centerX(),box.centerY()-i*25,0);
                    event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                    try { if(!ui.injectInputEvent(event,true)) throw new Exception("swipe injection failed"); } finally { event.recycle(); }
                    Thread.sleep(40);
                }
                Thread.sleep(700); ui.setOnAccessibilityEventListener(null);
                if(!scrolled.get()) throw new Exception("swipe did not scroll grid");
                if(!hasText(ui,"선택 500개",3000)) throw new Exception("swipe changed selection");
                int auto=android.provider.Settings.System.getInt(context.getContentResolver(),"accelerometer_rotation",0);
                int rotation=android.provider.Settings.System.getInt(context.getContentResolver(),"user_rotation",0);
                try {
                    ui.setRotation(UiAutomation.ROTATION_FREEZE_90); Thread.sleep(1200);
                    if(!hasText(ui,"선택 500개",5000)) throw new Exception("rotation lost selection");
                } finally {
                    ui.setRotation(rotation);
                    if(auto!=0) ui.setRotation(UiAutomation.ROTATION_UNFREEZE);
                }
            }
            output.putString("report","PASS "+mode+"; "+status+"; urls="+record.optInt("uniqueImageUrls")+"; elapsed="+record.optLong("elapsedMs"));
            output.remove("record");
            test.finish(Activity.RESULT_OK,output);
        } catch(Exception e) { output.remove("record"); output.putString("report","FAIL "+mode+": "+e.getMessage()); test.finish(Activity.RESULT_CANCELED,output); }
    }
    private static boolean hasText(UiAutomation ui,String text,long timeout) throws Exception {
        long end=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<end) {
            for(AccessibilityWindowInfo window:ui.getWindows()) {
                AccessibilityNodeInfo root=window.getRoot();
                if(root==null) continue;
                try { if(!root.findAccessibilityNodeInfosByText(text).isEmpty()) return true; }
                finally { root.recycle(); }
            }
            Thread.sleep(200);
        }
        return false;
    }
    private static android.graphics.Rect checkbox(UiAutomation ui) throws Exception {
        for(AccessibilityWindowInfo window:ui.getWindows()) {
            AccessibilityNodeInfo root=window.getRoot();
            if(root==null) continue;
            try {
                for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("저장 선택")) {
                    try {
                        if(node.isVisibleToUser()) { android.graphics.Rect box=new android.graphics.Rect(); node.getBoundsInScreen(box); if(!box.isEmpty()) return box; }
                    } finally { node.recycle(); }
                }
            } finally { root.recycle(); }
        }
        throw new Exception("visible checkbox not found");
    }
    private static void shell(UiAutomation ui,String command) throws Exception {
        try(android.os.ParcelFileDescriptor output=ui.executeShellCommand(command);
            java.io.InputStream input=new java.io.FileInputStream(output.getFileDescriptor())) {
            byte[] buffer=new byte[1024]; while(input.read(buffer)!=-1) { }
        }
    }
    private static void open(Context context,String page) {
        context.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("http://127.0.0.1:8787/"+page+".html"))
            .setPackage("com.android.chrome").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("create_new_tab",true));
    }
    static void click(UiAutomation ui,String text,long timeout) throws Exception {
        long end=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<end) {
            for(AccessibilityWindowInfo window:ui.getWindows()) {
                AccessibilityNodeInfo root=window.getRoot();
                if(root==null) continue;
                try {
                    if(!"dev.browserdownloader.probe".equals(String.valueOf(root.getPackageName()))) continue;
                    for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)) {
                        try { if(text.equalsIgnoreCase(String.valueOf(node.getText())) && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return; }
                        finally { node.recycle(); }
                    }
                } finally { root.recycle(); }
            }
            Thread.sleep(250);
        }
        throw new Exception("button unavailable: "+text);
    }
}
