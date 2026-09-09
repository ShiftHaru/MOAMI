package dev.browserdownloader.probe;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

final class GalleryUi {
    static boolean dark(Context c){return (c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
    static int color(Context c,int attr){return MaterialColors.getColor(c,attr,"GalleryUi");}
    static int bg(Context c){return color(c,com.google.android.material.R.attr.colorSurface);}
    static int card(Context c){return color(c,com.google.android.material.R.attr.colorSurfaceContainerLow);}
    static int ink(Context c){return color(c,com.google.android.material.R.attr.colorOnSurface);}
    static int muted(Context c){return color(c,com.google.android.material.R.attr.colorOnSurfaceVariant);}
    static int accent(Context c){return color(c,androidx.appcompat.R.attr.colorPrimary);}
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static void theme(Activity a,boolean popup){a.setTheme(popup?R.style.ResultsPopup:R.style.GalleryTheme);DynamicColors.applyToActivityIfAvailable(a);}
    static Context overlay(Context c){return DynamicColors.wrapContextIfAvailable(new androidx.appcompat.view.ContextThemeWrapper(c,R.style.GalleryTheme));}
    static MaterialShapeDrawable rounded(Context c,int radius,int fill){MaterialShapeDrawable d=new MaterialShapeDrawable(ShapeAppearanceModel.builder().setAllCornerSizes(dp(c,radius)).build());d.setFillColor(ColorStateList.valueOf(fill));return d;}
    static MaterialShapeDrawable surface(Context c,boolean selected){MaterialShapeDrawable d=rounded(c,16,card(c));d.setStroke(dp(c,selected?2:1),selected?accent(c):color(c,com.google.android.material.R.attr.colorOutlineVariant));return d;}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static TextView text(Context c,String text,int size){TextView t=new com.google.android.material.textview.MaterialTextView(c);t.setTextAppearance(size>=22?com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall:com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);t.setText(text);t.setTextSize(size);t.setTextColor(ink(c));t.setPadding(0,dp(c,4),0,dp(c,4));if(size>=22)t.setAccessibilityHeading(true);return t;}
    static Button button(Context c,String title,Runnable action){MaterialButton b=new MaterialButton(c,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);b.setCornerRadius(dp(c,16));b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(c,48));b.setOnClickListener(v->action.run());return b;}
    static void primary(Button b){Context c=b.getContext();int disabled=color(c,com.google.android.material.R.attr.colorSurfaceContainerHighest);b.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{disabled,accent(c)}));b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{muted(c),color(c,com.google.android.material.R.attr.colorOnPrimary)}));if(b instanceof MaterialButton)((MaterialButton)b).setStrokeWidth(0);}
    static void inset(LinearLayout root){Context c=root.getContext();root.setBackgroundColor(bg(c));root.setPadding(dp(c,16),dp(c,16),dp(c,16),dp(c,16));root.setOnApplyWindowInsetsListener((v,i)->{
        v.setPadding(dp(c,16)+i.getSystemWindowInsetLeft(),dp(c,8)+i.getSystemWindowInsetTop(),dp(c,16)+i.getSystemWindowInsetRight(),dp(c,8)+i.getSystemWindowInsetBottom());return i;});}
    static int columns(int widthDp){return widthDp<360?1:widthDp<600?2:widthDp<840?3:4;}
    static class Header extends ScrollView{
        Header(Context c){super(c);setFillViewport(false);}
        @Override protected void onMeasure(int w,int h){int available=MeasureSpec.getMode(h)==MeasureSpec.UNSPECIFIED?dp(getContext(),getResources().getConfiguration().screenHeightDp):MeasureSpec.getSize(h);super.onMeasure(w,MeasureSpec.makeMeasureSpec(available*45/100,MeasureSpec.AT_MOST));}
    }
}
