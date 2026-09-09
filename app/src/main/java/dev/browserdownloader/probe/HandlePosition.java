package dev.browserdownloader.probe;

/** Fractions preserve the anchor across screen sizes; menus clamp without changing it. */
final class HandlePosition {
    static int top(float fraction,int height,int itemHeight){
        if(!Float.isFinite(fraction))fraction=.5f;
        return Math.max(0,Math.min(Math.max(0,height-itemHeight),Math.round(height*fraction-itemHeight/2f)));
    }
    static float move(float fraction,float delta,int height,int itemHeight){
        if(height<=itemHeight||height<=0)return .5f;
        return (top(fraction+delta/height,height,itemHeight)+itemHeight/2f)/height;
    }
}
