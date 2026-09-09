package dev.browserdownloader.probe;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.Field;

/** Real X screen/job and notification PendingIntent, using the user's authorized GIF sample. */
final class XWorkBoundaryCheck {
    private static final String LINK="https://x.com/GiFShitpost/status/2097106892585918695";
    static void run(Instrumentation test,Context context,UiAutomation ui,String mode) {
        Bundle out=new Bundle(); int code=Activity.RESULT_CANCELED;
        Activity activity=null;
        int auto=android.provider.Settings.System.getInt(context.getContentResolver(),"accelerometer_rotation",0);
        int rotation=android.provider.Settings.System.getInt(context.getContentResolver(),"user_rotation",0);
        try {
            context.getSharedPreferences("probe",0).edit().putBoolean("enabled",true)
                    .putBoolean("xDrawerConsent",true).putBoolean("notificationHidden",false).commit();
            activity=open(test,context);
            start(test,activity);
            Object cancellation=field(activity,"cancellation");
            long enteredBy=SystemClock.elapsedRealtime()+10000;
            while(!running(cancellation) && SystemClock.elapsedRealtime()<enteredBy) Thread.sleep(10);
            require(running(cancellation),"job did not enter; cancellation not exercised");
            if(mode.equals("xOther")) context.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            if(mode.equals("xRotate")) ui.setRotation(rotation==1?UiAutomation.ROTATION_FREEZE_0:UiAutomation.ROTATION_FREEZE_90);
            if(mode.equals("xNotify")) {
                android.service.notification.StatusBarNotification status=null;
                for(var item:context.getSystemService(NotificationManager.class).getActiveNotifications()) if(item.getId()==100) status=item;
                require(status!=null && status.getNotification().actions!=null,"running notification missing");
                status.getNotification().actions[0].actionIntent.send();
            }
            long stoppedBy=SystemClock.elapsedRealtime()+45000;
            while(SystemClock.elapsedRealtime()<stoppedBy && (!cancelled(cancellation)||running(cancellation))) Thread.sleep(50);
            require(cancelled(cancellation) && !running(cancellation),"work did not cancel/leave");
            if(mode.equals("xNotify")) require(!ScanNotification.enabled(context),"notification stop did not disable work");
            require(context.getSharedPreferences("x-pending-download",0).getString("uri","").isEmpty(),"pending journal remains");
            Activity stopped=activity; test.runOnMainSync(stopped::finish); activity=null;
            ui.setRotation(rotation);
            // Retry through the same real screen after explicitly restoring the test's activation state.
            context.getSharedPreferences("probe",0).edit().putBoolean("enabled",true).commit();
            activity=open(test,context); start(test,activity);
            Activity retry=activity;
            long doneBy=SystemClock.elapsedRealtime()+90000;
            String[] result={""};
            do {
                test.runOnMainSync(()->{try {result[0]=((TextView)field(retry,"output")).getText().toString();}catch(Exception e){throw new RuntimeException(e);}});
                if(!result[0].equals("검사 중…")) break;
                Thread.sleep(100);
            } while(SystemClock.elapsedRealtime()<doneBy);
            require(result[0].startsWith("다운로드 완료"),"retry did not complete");
            require(context.getSharedPreferences("x-pending-download",0).getString("uri","").isEmpty(),"retry pending journal remains");
            java.io.File[] files=new java.io.File(context.getNoBackupFilesDir(),"x-media").listFiles();
            require(files!=null,"media directory missing");
            for(java.io.File file:files) require(!file.getName().endsWith(".partial")&&!file.getName().startsWith("mp4-decode-"),"temporary media remains");
            out.putString("report","PASS "+mode+": real X job cancellation observed; UI retry completed; pending/temporary files absent");
            code=Activity.RESULT_OK;
        } catch(Throwable e) { out.putString("report","FAIL "+mode+": "+e.getClass().getSimpleName()+" "+e.getMessage()); }
        finally {
            if(activity!=null) {Activity last=activity;test.runOnMainSync(last::finish);}
            ui.setRotation(rotation); if(auto!=0) ui.setRotation(UiAutomation.ROTATION_UNFREEZE);
        }
        test.finish(code,out);
    }
    private static Activity open(Instrumentation test,Context context) {
        Instrumentation.ActivityMonitor monitor=test.addMonitor(XActivity.class.getName(),null,false);
        try {
            context.startActivity(new Intent(context,XActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity activity=monitor.waitForActivityWithTimeout(10000);
            require(activity!=null,"X screen did not open"); test.waitForIdleSync(); return activity;
        } finally {test.removeMonitor(monitor);}
    }
    private static void start(Instrumentation test,Activity activity) throws Exception {
        EditText link=(EditText)field(activity,"link"); LinearLayout actions=(LinearLayout)field(activity,"actions");
        test.runOnMainSync(()->{
            link.setText(LINK);
            for(int i=0;i<actions.getChildCount();i++) {
                Button button=(Button)actions.getChildAt(i);
                if(button.getText().toString().equals("게시물 미디어 다운로드")) {require(button.isEnabled()&&button.performClick(),"download button unavailable");return;}
            }
            throw new AssertionError("download button absent");
        });
    }
    private static Object field(Object object,String name) throws Exception {
        Class<?> type=object instanceof Activity?dev.browserdownloader.xprobe.MainActivity.class:object.getClass();
        Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    private static boolean running(Object cancellation) throws Exception {synchronized(cancellation){return field(cancellation,"thread")!=null;}}
    private static boolean cancelled(Object cancellation) throws Exception {synchronized(cancellation){return (Boolean)field(cancellation,"cancelled");}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
