package dev.browserdownloader.probe;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
import android.widget.*;

/** Exercise exported ACTION_SEND routing and actual popup selection/save controls. */
final class XShareCheck {
    static void run(Instrumentation test,Context context,UiAutomation ui){
        Bundle out=new Bundle();Activity popup=null;int code=Activity.RESULT_CANCELED;
        try {
            context.getSharedPreferences("probe",0).edit().putBoolean("enabled",true).putBoolean("xDrawerConsent",true).commit();
            for(String link:List.of("https://x.com/RaminNasibov/status/2096854147219808434","https://x.com/GiFShitpost/status/2097106892585918695")){
                context.startActivity(context.getPackageManager().getLaunchIntentForPackage("com.twitter.android"));Thread.sleep(2000);
                var monitor=test.addMonitor(XShareActivity.class.getName(),null,false);
                context.startActivity(new Intent(Intent.ACTION_SEND).setType("text/plain").setClass(context,ShareActivity.class)
                        .putExtra(Intent.EXTRA_TEXT,"공유 게시물\n"+link+"?s=20").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                popup=monitor.waitForActivityWithTimeout(10000);test.removeMonitor(monitor);require(popup!=null,"share did not open popup");
                final Activity view=popup;
                waitFor(test,()->!(Boolean)field(view,"busy") && !((List<?>)field(view,"media")).isEmpty(),110000,"media inspection failed");
                require(((Set<?>)field(view,"selected")).isEmpty(),"new share inherited selection");
                waitFor(test,()->!((Map<?,?>)field(view,"images")).isEmpty(),40000,"thumbnail failed");
                test.runOnMainSync(()->{GridView grid=(GridView)field(view,"grid");LinearLayout card=(LinearLayout)grid.getChildAt(0);
                    ((CheckBox)card.getChildAt(2)).performClick();});
                require(((Set<?>)field(view,"selected")).size()==1,"single selection failed");
                Thread.sleep(1000);
                var screenshot=ui.takeScreenshot();try(var file=new java.io.FileOutputStream(new java.io.File(context.getFilesDir(),"x-share-test.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file);}finally{screenshot.recycle();}
                ChromeBoundaryCheck.click(ui,"선택 저장",10000);ChromeBoundaryCheck.click(ui,"저장",10000);
                waitFor(test,()->!(Boolean)field(view,"busy") && ((Map<?,?>)field(view,"savedStatus")).size()==1,240000,"selected save timed out");
                Map<?,?> saved=(Map<?,?>)field(view,"savedStatus");require(saved.values().iterator().next().toString().startsWith("저장 완료"),"selected save failed: "+saved.values());
                require(saved.size()==1,"unselected media saved");
                ChromeBoundaryCheck.click(ui,"중지",10000);
                ChromeBoundaryCheck.click(ui,"선택 저장",10000);ChromeBoundaryCheck.click(ui,"저장",10000);
                waitFor(test,()->!(Boolean)field(view,"busy") && ((Map<?,?>)field(view,"savedStatus")).values().stream().allMatch(v->v.toString().startsWith("저장 완료")),240000,"retry failed");
                ChromeBoundaryCheck.click(ui,"닫기",10000);popup=null;Thread.sleep(700);
                var root=ui.getRootInActiveWindow();try{require(root!=null&&"com.twitter.android".equals(String.valueOf(root.getPackageName())),"close did not return to X");}finally{if(root!=null)root.recycle();}
            }
            var monitor=test.addMonitor(MainActivity.class.getName(),null,false);
            context.startActivity(new Intent(Intent.ACTION_SEND).setClass(context,ShareActivity.class).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT,"https://example.org/share-check").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity main=monitor.waitForActivityWithTimeout(10000);test.removeMonitor(monitor);
            require(main!=null,"Chrome share route missing");
            require(SharedPage.pending.matches("https://example.org/share-check",SystemClock.elapsedRealtime()),"Chrome share ticket changed");
            SharedPage.pending.clear();test.runOnMainSync(main::finish);
            out.putString("report","PASS X share: ACTION_SEND routing, automatic inspect, thumbnails, fresh selection, selected-only photo and GIF/MP4 save, retry, close to X; Chrome share preserved");code=Activity.RESULT_OK;
        }catch(Throwable e){out.putString("report","FAIL X share: "+e.getClass().getSimpleName()+" "+e.getMessage());}
        finally{if(popup!=null){Activity last=popup;test.runOnMainSync(last::finish);}}
        test.finish(code,out);
    }
    private static Object field(Object object,String name){try{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new RuntimeException(e);}}
    private interface Check{boolean ok()throws Exception;}
    private static void waitFor(Instrumentation test,Check check,long ms,String error)throws Exception{
        long end=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<end){
            boolean[] ok={false};test.runOnMainSync(()->{try{ok[0]=check.ok();}catch(Exception e){throw new RuntimeException(e);}});
            if(ok[0])return;Thread.sleep(100);
        }throw new AssertionError(error);
    }
    private static void require(boolean b,String error){if(!b)throw new AssertionError(error);}
}
