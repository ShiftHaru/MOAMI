package dev.browserdownloader.probe;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;

/** Uses native long-press scheduling on an attached view, without changing user settings. */
final class HandleCheck {
    static void run(Instrumentation test){
        Bundle result=new Bundle();Activity activity=null;int code=Activity.RESULT_CANCELED;
        try{
            var c=test.getTargetContext();if(!SetupActivity.ready(c))throw new AssertionError("User setup must already be complete");
            activity=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            Activity host=activity;EdgeHandle[] edge=new EdgeHandle[1];int[] clicks={0},starts={0},ends={0},cancels={0};float[] moved={0};
            test.runOnMainSync(()->{
                FrameLayout frame=new FrameLayout(host);host.setContentView(frame);edge[0]=new EdgeHandle(host);frame.addView(edge[0]);
                edge[0].setOnClickListener(v->clicks[0]++);
                edge[0].setDrag(new EdgeHandle.Drag(){public void start(){starts[0]++;}public void move(float delta){moved[0]+=delta;}public void end(boolean cancel){ends[0]++;if(cancel)cancels[0]++;}});
            });test.waitForIdleSync();
            touch(test,edge[0],MotionEvent.ACTION_DOWN,56);touch(test,edge[0],MotionEvent.ACTION_UP,56);test.waitForIdleSync();
            require(clicks[0]==1&&starts[0]==0,"tap opens once");
            touch(test,edge[0],MotionEvent.ACTION_DOWN,56);touch(test,edge[0],MotionEvent.ACTION_MOVE,200);touch(test,edge[0],MotionEvent.ACTION_UP,200);test.waitForIdleSync();
            require(clicks[0]==1&&starts[0]==0,"early swipe neither clicks nor moves");
            touch(test,edge[0],MotionEvent.ACTION_DOWN,56);Thread.sleep(ViewConfiguration.getLongPressTimeout()+200);test.waitForIdleSync();
            touch(test,edge[0],MotionEvent.ACTION_MOVE,180);touch(test,edge[0],MotionEvent.ACTION_UP,180);test.waitForIdleSync();
            require(starts[0]==1&&ends[0]==1&&cancels[0]==0&&moved[0]==124&&clicks[0]==1,"held drag moves without opening");
            touch(test,edge[0],MotionEvent.ACTION_DOWN,56);Thread.sleep(ViewConfiguration.getLongPressTimeout()+200);test.waitForIdleSync();
            touch(test,edge[0],MotionEvent.ACTION_MOVE,100);touch(test,edge[0],MotionEvent.ACTION_CANCEL,100);
            require(cancels[0]==1&&clicks[0]==1,"cancel rolls back without opening");
            test.runOnMainSync(()->edge[0].performAccessibilityAction(R.id.handle_up,null));
            require(starts[0]==3&&ends[0]==3,"accessibility move action commits");
            result.putString("result","PASS tap / early swipe / real long press + drag / cancel / accessibility move");code=Activity.RESULT_OK;
        }catch(Throwable e){result.putString("error",e.getClass().getSimpleName()+": "+e.getMessage());}
        finally{Activity last=activity;if(last!=null)test.runOnMainSync(last::finish);test.finish(code,result);}
    }
    private static void touch(Instrumentation test,EdgeHandle edge,int action,float y){test.runOnMainSync(()->{long now=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(now,now,action,32,y,0);edge.dispatchTouchEvent(event);event.recycle();});}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
