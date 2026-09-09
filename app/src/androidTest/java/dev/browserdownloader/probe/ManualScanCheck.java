package dev.browserdownloader.probe;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.*;
import org.json.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

/** Actual drawer, manual Chrome scrolling, recreated result UI and product saving. */
final class ManualScanCheck {
    static void run(Instrumentation test, Context context, UiAutomation ui) {
        Bundle out=new Bundle(); int code=Activity.RESULT_CANCELED; Activity results=null;
        try {
            SharedPage.pending.clear();
            open(context,"fixture"); Thread.sleep(2000);
            expand(ui);
            Set<String> texts=new HashSet<>();
            for(var window:ui.getWindows()) {
                var root=window.getRoot();if(root==null)continue;
                ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>();nodes.add(root);
                while(!nodes.isEmpty()) {var n=nodes.remove();if("dev.browserdownloader.probe".equals(String.valueOf(n.getPackageName())))texts.add(String.valueOf(n.getText()));
                    for(int i=0;i<n.getChildCount();i++){var child=n.getChild(i);if(child!=null)nodes.add(child);}n.recycle();}
            }
            for(String removed:List.of("스크롤 후 검사","자동 누적 검사 (3분)","공유 탭 검사","공유 탭 누적 검사","수집 중단 · 결과 유지","중지"))
                require(!texts.contains(removed),"removed drawer button remains");
            JSONObject first=scan(context,ui,0); String id=first.getString("captureId");
            int count=first.getInt("imageNodesWithUrl"); require(count>0,"initial images missing");
            require(!first.toString().contains("g1-late-original"),"late image already loaded before scrolling");
            JSONObject repeated=scan(context,ui,1);
            require(id.equals(repeated.getString("captureId")) && count==repeated.getInt("imageNodesWithUrl"),"repeat duplicated or reset images");
            results=test.startActivitySync(new Intent(context,ResultsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ChromeBoundaryCheck.click(ui,"모두 선택",10000);
            java.lang.reflect.Field selection=ResultsActivity.class.getDeclaredField("selected");selection.setAccessible(true);
            Activity firstView=results; Set<Integer> selected=new LinkedHashSet<>();
            test.runOnMainSync(()-> {
                var attributes=firstView.getWindow().getAttributes();
                var floating=firstView.obtainStyledAttributes(new int[]{android.R.attr.windowIsFloating});
                try {require(floating.getBoolean(0,false),"results is not floating");}finally{floating.recycle();}
                require(attributes.width>0 && attributes.height>0,"popup size is not bounded");
                require((attributes.flags & android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)==0,"popup passes touch to Chrome");
            });
            Thread.sleep(500);
            var screenshot=ui.takeScreenshot();
            try(var stream=new java.io.FileOutputStream(new File(context.getFilesDir(),"popup-test.png"))) {
                require(screenshot!=null && screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream),"popup screenshot failed");
            }finally{if(screenshot!=null)screenshot.recycle();}
            test.runOnMainSync(()->{try{selected.addAll((Set<Integer>)selection.get(firstView));}catch(Exception e){throw new RuntimeException(e);}});
            require(selected.size()==count,"initial selection missing");
            test.runOnMainSync(firstView::finish);results=null;Thread.sleep(700);
            // The test represents the user's scroll. Product code must not scroll or collect by itself.
            int swipes=0;
            for(;swipes<24 && !ChromeSiteCheck.imageReady(ui,"late-original.png");swipes++) {
                var root=ui.getRootInActiveWindow(); var page=ChromeProbeService.findDocument(root);root.recycle();
                require(page!=null,"Chrome not restored after results");
                android.graphics.Rect bounds=new android.graphics.Rect();page.getBoundsInScreen(bounds);page.recycle();
                long down=SystemClock.uptimeMillis();
                for(int step=0;step<=8;step++) {
                    var touch=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),step==0?0:step==8?1:2,
                            bounds.centerX(),bounds.bottom-bounds.height()*.15f-step*bounds.height()*.07f,0);
                    touch.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                    try{require(ui.injectInputEvent(touch,true),"manual swipe injection failed");}finally{touch.recycle();}
                    Thread.sleep(40);
                }
                Thread.sleep(350);
            }
            Thread.sleep(1000);
            out.putInt("manualSwipes",swipes);
            require(ChromeSiteCheck.imageReady(ui,"late-original.png"),"user swipes did not load the late fixture image");
            require(read(context).getInt("passes")==2,"scroll triggered automatic scan");
            expand(ui);
            JSONObject added=scan(context,ui,2);
            require(id.equals(added.getString("captureId")) && added.getInt("imageNodesWithUrl")>count
                    && added.toString().contains("g1-late-original"),"manual scroll rescan did not accumulate");
            results=test.startActivitySync(new Intent(context,ResultsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity secondView=results;Set<Integer> restored=new LinkedHashSet<>();
            test.runOnMainSync(()->{try{restored.addAll((Set<Integer>)selection.get(secondView));}catch(Exception e){throw new RuntimeException(e);}});
            require(restored.equals(selected),"selection lost or new images selected automatically");
            var monitor=test.addMonitor(ResultsActivity.class.getName(),null,false);
            test.runOnMainSync(secondView::recreate);
            Activity recreated=monitor.waitForActivityWithTimeout(5000);test.removeMonitor(monitor);
            require(recreated!=null,"result recreation not observed");results=recreated;
            Set<Integer> recreatedSelection=new LinkedHashSet<>();
            test.runOnMainSync(()->{try{recreatedSelection.addAll((Set<Integer>)selection.get(recreated));}catch(Exception e){throw new RuntimeException(e);}});
            require(recreatedSelection.equals(selected),"recreation lost selection");
            ChromeBoundaryCheck.click(ui,"선택 저장",10000);ChromeBoundaryCheck.click(ui,"저장",10000);
            PreviewStore store=PreviewStore.get(context);long by=SystemClock.elapsedRealtime()+35000;
            while(SystemClock.elapsedRealtime()<by && selected.stream().anyMatch(i->!store.saveStatus(i).startsWith("저장 완료")))Thread.sleep(100);
            for(int index:selected)require(store.saveStatus(index).startsWith("저장 완료"),"selected save failed");
            test.runOnMainSync(recreated::finish);results=null;
            for(String page:List.of("fixture","other")) {
                String previous=read(context).getString("captureId");open(context,page);Thread.sleep(1500);
                expand(ui);JSONObject next=scan(context,ui,0);
                require(!next.getString("captureId").equals(previous) && next.getInt("passes")==1,"new tab/page retained old session");
                require(store.selection(next.getString("captureId")).isEmpty(),"selection crossed sessions");
            }
            String beforeDelete=read(context).getString("captureId");
            Activity main=test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ChromeBoundaryCheck.click(ui,"검사 기록 삭제",10000);
            require(!new File(context.getFilesDir(),"latest.json").exists()&&!store.isCurrent(beforeDelete),"record deletion retained session");
            test.runOnMainSync(main::finish);Thread.sleep(500);expand(ui);
            JSONObject afterDelete=scan(context,ui,0);
            require(!afterDelete.getString("captureId").equals(beforeDelete)&&afterDelete.getInt("passes")==1,"deleted session resumed");
            open(context,"count");Thread.sleep(1500);expand(ui);
            JSONObject capped=scan(context,ui,0),cappedAgain=scan(context,ui,1);
            require(capped.getInt("imageNodesWithUrl")==500&&capped.optBoolean("truncated")
                    &&cappedAgain.getInt("imageNodesWithUrl")==500
                    &&cappedAgain.getString("captureId").equals(capped.getString("captureId")),"manual cap or repeat failed");
            out.putString("report","PASS manual popup: automatic open/close to Chrome, floating modal bounds, drawer cleanup, repeat dedup, user scroll only, cumulative images, recreated selection, selected save, same-URL new tab/different page reset, record deletion, 500 URL cap");
            code=Activity.RESULT_OK;
        }catch(Throwable error){out.putString("report","FAIL manual: "+error.getClass().getSimpleName()+" "+error.getMessage());}
        finally{if(results!=null){Activity last=results;test.runOnMainSync(last::finish);}}
        test.finish(code,out);
    }
    private static void expand(UiAutomation ui)throws Exception {
        boolean visible=false;
        for(var window:ui.getWindows()) {
            var root=window.getRoot();if(root==null)continue;
            if("dev.browserdownloader.probe".equals(String.valueOf(root.getPackageName())))
                for(var node:root.findAccessibilityNodeInfosByText("현재 탭 검사")){visible|=node.isVisibleToUser();node.recycle();}
            root.recycle();
        }
        if(!visible)ChromeBoundaryCheck.click(ui,"<",10000);
    }
    private static JSONObject scan(Context context,UiAutomation ui,int previousPass) throws Exception {
        expand(ui);
        String previous=new File(context.getFilesDir(),"latest.json").isFile()?read(context).optString("captureId"):"";
        ChromeBoundaryCheck.click(ui,"현재 탭 검사",10000);
        long by=SystemClock.elapsedRealtime()+10000;
        while(SystemClock.elapsedRealtime()<by){Thread.sleep(100);if(!new File(context.getFilesDir(),"latest.json").isFile())continue;JSONObject report=read(context);
            if(report.optBoolean("manualMode") && (previousPass==0?!report.optString("captureId").equals(previous):report.optInt("passes")>previousPass)) {
                ChromeBoundaryCheck.click(ui,"닫기",10000);
                Thread.sleep(500);
                var root=ui.getRootInActiveWindow();
                try { require(root!=null && ChromeProbeService.isChrome(root.getPackageName()),"popup did not return to Chrome"); }
                finally { if(root!=null)root.recycle(); }
                return report;
            }}
        throw new AssertionError("manual result did not advance");
    }
    private static JSONObject read(Context context)throws Exception{return new JSONObject(Files.readString(new File(context.getFilesDir(),"latest.json").toPath()));}
    private static void open(Context context,String page){context.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("http://127.0.0.1:8787/"+page+".html")).setPackage("com.android.chrome").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("create_new_tab",true));}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
