package dev.browserdownloader.probe;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;

/** Slim 20dp contour inside a 48 x 112dp accessible touch target. */
final class EdgeHandle extends View {
    interface Drag { void start(); void move(float deltaY); void end(boolean cancelled); }
    private final Paint arrow=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath=new Path();
    private Drag drag;
    private boolean tracking, dragging, blocked;
    private float downX, downY, lastY;
    void setDrag(Drag value){drag=value;}
    EdgeHandle(Context context){
        super(context);setClickable(true);setFocusable(true);setContentDescription("검사 메뉴 열기. 길게 눌러 위아래로 이동");
        setOnLongClickListener(v->{if(!tracking||blocked||drag==null)return false;dragging=true;drag.start();return true;});
        int fill=GalleryUi.color(context,com.google.android.material.R.attr.colorPrimaryContainer);
        arrow.setColor(GalleryUi.color(context,com.google.android.material.R.attr.colorOnPrimaryContainer));
        arrow.setStyle(Paint.Style.STROKE);arrow.setStrokeWidth(GalleryUi.dp(context,2));arrow.setStrokeCap(Paint.Cap.ROUND);arrow.setStrokeJoin(Paint.Join.ROUND);
        setBackground(new RippleDrawable(ColorStateList.valueOf(GalleryUi.accent(context)&0x00ffffff|0x33000000),new Drop(fill),new Drop(Color.WHITE)));
    }
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(GalleryUi.dp(getContext(),48),GalleryUi.dp(getContext(),112));}
    @Override public CharSequence getAccessibilityClassName(){return android.widget.Button.class.getName();}
    @Override public boolean performClick(){return super.performClick();}
    @Override public boolean onTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN: tracking=true;dragging=false;blocked=false;downX=e.getRawX();downY=lastY=e.getRawY();break;
            case MotionEvent.ACTION_POINTER_DOWN: blocked=true;cancelLongPress();if(dragging){dragging=false;drag.end(true);}break;
            case MotionEvent.ACTION_MOVE:
                if(dragging){drag.move(e.getRawY()-lastY);lastY=e.getRawY();return true;}
                if(Math.hypot(e.getRawX()-downX,e.getRawY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop()){blocked=true;cancelLongPress();}
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                tracking=false;
                if(dragging||blocked){boolean moved=dragging;dragging=false;if(moved)drag.end(e.getActionMasked()==MotionEvent.ACTION_CANCEL);
                    MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.onTouchEvent(cancel);cancel.recycle();return true;}
                break;
        }
        return super.onTouchEvent(e);
    }
    @Override protected void onDetachedFromWindow(){tracking=false;cancelLongPress();if(dragging){dragging=false;drag.end(true);}super.onDetachedFromWindow();}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.handle_up,"위로 이동"));info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.handle_down,"아래로 이동"));}
    @Override public boolean performAccessibilityAction(int action,android.os.Bundle args){if(drag!=null&&(action==R.id.handle_up||action==R.id.handle_down)){drag.start();drag.move(GalleryUi.dp(getContext(),action==R.id.handle_up?-48:48));drag.end(false);return true;}return super.performAccessibilityAction(action,args);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float x=getWidth()-GalleryUi.dp(getContext(),12),y=getHeight()*.5f,d=GalleryUi.dp(getContext(),3);arrowPath.reset();arrowPath.moveTo(x+d,y-d);arrowPath.lineTo(x,y);arrowPath.lineTo(x+d,y+d);canvas.drawPath(arrowPath,arrow);}
    private final class Drop extends Drawable{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path=new Path();
        Drop(int color){paint.setColor(color);}
        @Override public void draw(Canvas canvas){Rect b=getBounds();float w=GalleryUi.dp(getContext(),20),h=b.height();path.reset();path.moveTo(w,0);path.cubicTo(w,h*.27f,0,h*.25f,0,h*.5f);path.cubicTo(0,h*.75f,w,h*.73f,w,h);path.close();canvas.save();canvas.translate(b.right-w,b.top);canvas.drawPath(path,paint);canvas.restore();}
        @Override public void setAlpha(int value){paint.setAlpha(value);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter value){paint.setColorFilter(value);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
