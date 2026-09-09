package dev.browserdownloader.probe;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Same gallery and actions hosted by the full screen and both existing popup entry points. */
public class GalleryActivity extends androidx.appcompat.app.AppCompatActivity {
    private GallerySession session;
    private GridView grid;
    private TextView summary, status, empty, feedback;
    private ProgressBar progress;
    private com.google.android.material.textfield.TextInputLayout addressField;
    private Button submit, save, select;
    private EditText address;
    private com.google.android.material.materialswitch.MaterialSwitch drawer;
    private boolean setupOpen, foreground;
    private int scrollPosition;
    private boolean scrolling, connected;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Cards adapter=new Cards();
    private final Runnable permissions=new Runnable(){public void run(){if(!foreground)return;if(!SetupActivity.ready(GalleryActivity.this)){gate();return;}if(connected!=ScanNotification.connected())refresh();handler.postDelayed(this,1000);}};
    protected boolean popup(){return true;}
    @Override public void onCreate(Bundle saved){
        GalleryUi.theme(this,popup());super.onCreate(saved);setFinishOnTouchOutside(false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        session=GallerySession.get(this);if(saved!=null)scrollPosition=saved.getInt("position",0);
        LinearLayout root=GalleryUi.column(this);GalleryUi.inset(root);
        if(popup()){root.setBackground(GalleryUi.rounded(this,24,GalleryUi.bg(this)));root.setClipToOutline(true);}
        LinearLayout header=GalleryUi.column(this);GalleryUi.Header scroll=new GalleryUi.Header(this);scroll.addView(header);root.addView(scroll);
        LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(GalleryUi.text(this,popup()?"미디어 선택":getApplicationInfo().loadLabel(getPackageManager()).toString(),22),new LinearLayout.LayoutParams(0,-2,1));
        if(popup())title.addView(GalleryUi.button(this,"닫기",this::finish));
        root.addView(title,0);
        if(!popup()){
            if(!BuildConfig.SHARE_ONLY){drawer=new com.google.android.material.materialswitch.MaterialSwitch(this);drawer.setText("Chrome 서랍");drawer.setMinHeight(dp(56));drawer.setChecked(getSharedPreferences("probe",0).getBoolean("chromeDrawer",true));
            drawer.setOnCheckedChangeListener((v,checked)->{getSharedPreferences("probe",0).edit().putBoolean("chromeDrawer",checked).apply();if(checked)activate();});header.addView(drawer);}
            LinearLayout input=new LinearLayout(this);input.setGravity(Gravity.CENTER_VERTICAL);
            com.google.android.material.textfield.TextInputLayout field=new com.google.android.material.textfield.TextInputLayout(this,null,com.google.android.material.R.attr.textInputOutlinedStyle);
            addressField=field;
            field.setHint(BuildConfig.SHARE_ONLY?"X · Instagram 게시물 주소":"X · Instagram · 웹페이지 주소");field.setBoxCornerRadii(dp(16),dp(16),dp(16),dp(16));
            address=new com.google.android.material.textfield.TextInputEditText(field.getContext());address.setSingleLine(true);address.setTextSize(16);address.setMinHeight(dp(56));address.setSaveEnabled(false);
            address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);address.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
            address.setText(session.draft);
            address.setId(View.generateViewId());address.setContentDescription(field.getHint());field.addView(address,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams fieldParams=new LinearLayout.LayoutParams(0,-2,1);fieldParams.setMarginEnd(dp(8));input.addView(field,fieldParams);
            submit=GalleryUi.button(this,"확인",this::submit);GalleryUi.primary(submit);input.addView(submit);header.addView(input);
            address.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_GO){submit();return true;}return false;});
        }
        summary=GalleryUi.text(this,"최근 결과",14);header.addView(summary);
        status=GalleryUi.text(this,"",14);status.setTextColor(GalleryUi.muted(this));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);header.addView(status);
        LinearLayout controls=new LinearLayout(this);
        select=GalleryUi.button(this,"모두 선택",()->{
            Set<Integer> available=available();
            if(session.selected.containsAll(available))session.selected.clear();else session.selected.addAll(available);
            persistSelection();refresh();
        });controls.addView(select,new LinearLayout.LayoutParams(0,-2,1));
        save=GalleryUi.button(this,"선택 저장",this::save);GalleryUi.primary(save);LinearLayout.LayoutParams saveParams=new LinearLayout.LayoutParams(0,-2,1);saveParams.setMarginStart(dp(8));controls.addView(save,saveParams);
        root.addView(controls);
        feedback=GalleryUi.text(this,"",14);feedback.setPadding(dp(12),dp(8),dp(12),dp(8));
        feedback.setBackground(GalleryUi.rounded(this,16,GalleryUi.color(this,com.google.android.material.R.attr.colorSecondaryContainer)));
        feedback.setTextColor(GalleryUi.color(this,com.google.android.material.R.attr.colorOnSecondaryContainer));
        feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(feedback);
        progress=new com.google.android.material.progressindicator.LinearProgressIndicator(this);progress.setIndeterminate(true);progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
        grid=new GridView(this);grid.setHorizontalSpacing(dp(8));grid.setVerticalSpacing(dp(8));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setNumColumns(2);grid.setAdapter(adapter);
        grid.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int count=GalleryUi.columns(Math.round((r-l)/getResources().getDisplayMetrics().density));if(grid.getNumColumns()!=count){grid.setNumColumns(count);grid.post(grid::requestLayout);}});
        grid.setOnItemClickListener((p,v,n,id)->details(session.nodes().optJSONObject(n)));
        grid.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView v,int s){scrolling=s!=SCROLL_STATE_IDLE;if(!scrolling)refresh();}public void onScroll(AbsListView v,int f,int n,int total){requestVisible();}});
        FrameLayout results=new FrameLayout(this);results.addView(grid,new FrameLayout.LayoutParams(-1,-1));empty=GalleryUi.text(this,"",16);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(24),dp(24),dp(24),dp(24));results.addView(empty,new FrameLayout.LayoutParams(-1,-1));grid.setEmptyView(empty);
        root.addView(results,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);receive(getIntent());
    }
    @Override public void onAttachedToWindow(){super.onAttachedToWindow();if(!popup())return;
        android.util.DisplayMetrics m=new android.util.DisplayMetrics();getWindowManager().getDefaultDisplay().getMetrics(m);int w=m.widthPixels,h=m.heightPixels;
        if(Build.VERSION.SDK_INT>=30){var b=getWindowManager().getCurrentWindowMetrics().getBounds();w=b.width();h=b.height();}
        getWindow().setLayout(Math.min(dp(900),Math.round(w*.94f)),Math.round(h*.90f));}
    @Override protected void onResume(){super.onResume();if(isFinishing())return;foreground=true;session.reload();session.listen(this::refresh);session.previews.listen(this::refresh);
        if(gate())return;refresh();grid.setSelection(scrollPosition);handler.post(permissions);consumePending();}
    @Override protected void onPause(){foreground=false;handler.removeCallbacksAndMessages(null);persistSelection();scrollPosition=grid.getFirstVisiblePosition();if(address!=null)session.draft=address.getText().toString();session.listen(null);session.previews.listen(null);super.onPause();}
    @Override protected void onStop(){if(!isChangingConfigurations()&&!session.observed())session.cancel();super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("position",grid.getFirstVisiblePosition());super.onSaveInstanceState(out);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);receive(intent);if(SetupActivity.ready(this))consumePending();}
    private void receive(Intent intent){
        if(intent==null)return;
        String text=intent.getStringExtra("post");
        if(Intent.ACTION_SEND.equals(intent.getAction())&&"text/plain".equals(intent.getType())){
            text=intent.getStringExtra(Intent.EXTRA_TEXT);String x=XShareLink.extract(text);if(!x.isEmpty())text=x;
        }
        if(text!=null)session.pendingInput=text;setIntent(new Intent(this,getClass()));
    }
    private boolean gate(){
        if(SetupActivity.ready(this))return false;
        session.cancel();if(!setupOpen){setupOpen=true;startActivityForResult(new Intent(this,SetupActivity.class),701);}return true;
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==701){setupOpen=false;if(result!=RESULT_OK)finish();}}
    private void consumePending(){if(session.pendingInput.isEmpty())return;String text=session.pendingInput;session.pendingInput="";if(address!=null)address.setText(text);if(session.saving())session.cancel();activate();session.lookup(text);}
    private void activate(){if(SetupActivity.ready(this)){getSharedPreferences("probe",0).edit().putBoolean("enabled",true).putBoolean("notificationHidden",false).apply();ScanNotification.refresh(this);}}
    private void submit(){if(session.busy){session.cancel();return;}if(gate())return;
        if(PreviewRules.requestUrl(address.getText().toString()).isEmpty()){addressField.setError(BuildConfig.SHARE_ONLY?"X·Instagram 게시물 주소를 입력하세요.":"https://로 시작하는 X·Instagram 게시물 또는 웹페이지 주소를 입력하세요.");address.requestFocus();return;}
        addressField.setError(null);activate();session.lookup(address.getText().toString());
        var keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);keyboard.hideSoftInputFromWindow(address.getWindowToken(),0);address.clearFocus();}
    private void save(){
        if(session.saving()){session.cancel();return;}if(gate())return;if(session.selected.isEmpty())return;activate();
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(session.selected.size()+"개 선택 저장")
                .setMessage("Download/BrowserDownloader에 저장합니다. 원본 여부는 별도 확인이 필요합니다."+(session.source.equals("X")?" GIF 항목은 MP4와 변환 GIF를 함께 저장합니다.":session.source.equals("Instagram")?" 오디오 트랙이 없는 영상은 GIF만, 있는 영상은 MP4로 저장합니다. GIF 변환은 30초·1080p 픽셀 수 이하, 10fps·256색이며 화질 손실이 있습니다. 변환 실패 시 MP4를 대신 저장하지 않습니다.":""))
                .setNegativeButton("취소",null).setPositiveButton("저장",(d,w)->{if(SetupActivity.ready(this)){activate();session.save();}}).show();
    }
    private void persistSelection(){session.previews.select(session.id,session.selected);}
    private Set<Integer> available(){Set<Integer> result=new HashSet<>();for(int i=0;i<session.nodes().length();i++){int index=session.nodes().optJSONObject(i).optInt("index");if(session.canSave(index))result.add(index);}return result;}
    private void refresh(){
        if(summary==null)return;
        session.updateSaveFeedback();
        int savedCount=0;for(int n=0;n<session.nodes().length();n++)if(session.saved(session.nodes().optJSONObject(n).optInt("index")))savedCount++;
        String saveText=session.saveSummary.isEmpty()?(savedCount>0?"✓ 저장된 항목 "+savedCount+"개":""):session.saveSummary;
        feedback.setText(saveText);feedback.setVisibility(saveText.isEmpty()?View.GONE:View.VISIBLE);
        long at=session.report.optLong("capturedAtEpochMs");
        summary.setText("최근 결과 · "+(session.source.isEmpty()?"아직 없음":session.source)+" · "+session.nodes().length()+"개"
                +(at>0?"\n"+android.text.format.DateFormat.format("MM/dd HH:mm",at):""));
        String extra=session.report.optBoolean("truncated")?"\n수집 한도 도달 · 부분 결과":"";
        connected=ScanNotification.connected();
        String page=NodeProbe.safeUrl(session.report.optString("addressBarUrl"));
        status.setText((page.isEmpty()?"":page+"\n")+session.message+extra+(!BuildConfig.SHARE_ONLY&&SetupActivity.ready(this)&&!connected?"\n접근성 허용됨 · 시스템 연결 대기":""));
        if(submit!=null){submit.setText(session.busy?"취소":"확인");submit.setEnabled(!session.previews.saving());}
        boolean working=session.saving();Set<Integer> available=available();
        if(!working&&session.nodes().length()>0&&available.isEmpty())status.append(BuildConfig.SHARE_ONLY?"\n저장 주소가 없습니다. 게시물 링크를 다시 조회하세요.":"\n저장 주소가 없습니다. 링크를 다시 조회하거나 Chrome 현재 탭을 다시 검사하세요.");
        save.setText(working?"작업 취소":"선택 "+session.selected.size()+"개 저장");save.setEnabled(working||!Collections.disjoint(session.selected,available));
        select.setText(!available.isEmpty()&&session.selected.containsAll(available)?"선택 해제":"모두 선택");select.setEnabled(!working&&!available.isEmpty());
        progress.setVisibility(working?View.VISIBLE:View.GONE);
        empty.setText(working?"미디어를 확인하고 있습니다…\n위의 작업 취소로 중지할 수 있습니다.":BuildConfig.SHARE_ONLY?"표시할 미디어가 없습니다.\nX·Instagram 게시물을 공유하거나 게시물 주소를 입력하세요.":"표시할 이미지가 없습니다.\nX·Instagram 게시물을 공유하거나 웹주소를 입력하세요.\nChrome에서는 < 버튼으로 현재 탭을 검사하세요.");
        if(!scrolling)adapter.notifyDataSetChanged();grid.post(this::requestVisible);
        if(foreground&&!session.saveNotice.isEmpty()){
            String notice=session.saveNotice;session.saveNotice="";
            com.google.android.material.snackbar.Snackbar.make(grid,notice+"\nDownload/BrowserDownloader",com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    .setTextMaxLines(3).setAction("확인",v->{}).show();
        }
    }
    private void requestVisible(){if(!foreground||!SetupActivity.ready(this)||session.busy)return;Set<Integer> visible=new HashSet<>();
        for(int p=grid.getFirstVisiblePosition();p<=grid.getLastVisiblePosition()&&p<session.nodes().length();p++)if(p>=0){JSONObject item=session.nodes().optJSONObject(p);if(item!=null)visible.add(item.optInt("index"));}
        session.previews.retainVisible(visible);
        for(int p=grid.getFirstVisiblePosition();p<=grid.getLastVisiblePosition()&&p<session.nodes().length();p++)if(p>=0){JSONObject item=session.nodes().optJSONObject(p);if(item!=null)session.previews.request(session.id,item.optInt("index"),!item.optString("targetUrl").isEmpty());}
    }
    private void details(JSONObject node){if(node==null)return;LinearLayout body=GalleryUi.column(this);body.setPadding(dp(24),0,dp(24),dp(8));int index=node.optInt("index");String capture=session.id;
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);var bitmap=session.previews.bitmap(session.id,index);image.setImageBitmap(bitmap);if(bitmap!=null)body.addView(image,new LinearLayout.LayoutParams(-1,dp(260)));
        PreviewStore.State state=session.previews.state(session.id,index,!node.optString("targetUrl").isEmpty());
        String result=session.social()?session.savedStatus.getOrDefault(index,""):session.previews.saveStatus(index);
        String dimensions=state.width()>0&&state.height()>0?state.width()+" × "+state.height():"미확인";
        String media=session.social()?"\n미디어: "+(session.source.equals("Instagram")&&"video".equals(node.optString("kind"))?"영상 · 오디오 없으면 GIF":GallerySession.label(node.optString("kind")))+(node.optInt("width")>0?" · 제공 정보 "+node.optInt("width")+" × "+node.optInt("height"):""):"";
        body.addView(GalleryUi.text(this,state.text()+"\n전달된 이미지 크기: "+dimensions+media+"\n미리보기는 최대 512px · 원본 미확인"+(result.isEmpty()?"":"\n\n"+result+"\n저장 위치: Download/BrowserDownloader"),14));
        if(!session.social())body.addView(GalleryUi.text(this,session.previews.saveTargetInfo(session.id,index),14));
        String url=NodeProbe.safeUrl(node.optString("targetUrl"));if(!url.isEmpty()){TextView link=GalleryUi.text(this,"미리보기 주소 (쿼리 제외)\n"+url,12);link.setTextIsSelectable(true);body.addView(link);}
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        var dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(node.optString("description","이미지")).setView(scroll).setPositiveButton("닫기",null)
                .setNeutralButton(session.selected.contains(index)?"선택 해제":"저장 선택",(d,w)->{if(session.id.equals(capture)&&session.canSave(index)){if(!session.selected.remove(index))session.selected.add(index);persistSelection();refresh();}}).create();
        dialog.setOnShowListener(d->dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setEnabled(session.canSave(index)));dialog.show();}
    private int dp(int n){return GalleryUi.dp(this,n);}
    private final class Cards extends BaseAdapter{
        public int getCount(){return session.nodes().length();}public Object getItem(int n){return session.nodes().optJSONObject(n);}public long getItemId(int n){return session.nodes().optJSONObject(n).optInt("index");}
        public View getView(int position,View old,ViewGroup parent){
            LinearLayout card;com.google.android.material.card.MaterialCardView shell;
            if(old instanceof com.google.android.material.card.MaterialCardView){shell=(com.google.android.material.card.MaterialCardView)old;card=(LinearLayout)shell.getChildAt(0);}else{shell=new com.google.android.material.card.MaterialCardView(GalleryActivity.this);shell.setRadius(dp(16));shell.setCardElevation(0);shell.setPreventCornerOverlap(false);card=GalleryUi.column(GalleryActivity.this);card.setPadding(dp(8),dp(8),dp(8),dp(8));shell.addView(card);
                FrameLayout preview=new FrameLayout(GalleryActivity.this);ImageView image=new ImageView(GalleryActivity.this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.addView(image,new FrameLayout.LayoutParams(-1,-1));TextView placeholder=GalleryUi.text(GalleryActivity.this,"",14);placeholder.setGravity(Gravity.CENTER);preview.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));card.addView(preview,new LinearLayout.LayoutParams(-1,dp(156)));
                card.addView(GalleryUi.text(GalleryActivity.this,"",14));CheckBox check=new com.google.android.material.checkbox.MaterialCheckBox(GalleryActivity.this);check.setMinHeight(dp(48));check.setFocusable(false);card.addView(check);card.addView(GalleryUi.text(GalleryActivity.this,"",12));}
            JSONObject node=session.nodes().optJSONObject(position);int index=node.optInt("index");boolean selected=session.selected.contains(index);
            boolean downloaded=session.saved(index);
            shell.setOnClickListener(v->details(node));
            shell.setCardBackgroundColor(GalleryUi.card(GalleryActivity.this));shell.setStrokeWidth(dp(selected?2:1));shell.setStrokeColor(selected?GalleryUi.accent(GalleryActivity.this):GalleryUi.color(GalleryActivity.this,com.google.android.material.R.attr.colorOutlineVariant));FrameLayout preview=(FrameLayout)card.getChildAt(0);ImageView image=(ImageView)preview.getChildAt(0);var bitmap=session.previews.bitmap(session.id,index);image.setImageBitmap(bitmap);image.setContentDescription(node.optString("description","이미지")+" 미리보기");
            String description=node.optString("description",node.optString("text","이미지"));if(description.isBlank())description="이미지";
            if(downloaded){shell.setStrokeWidth(dp(2));shell.setStrokeColor(GalleryUi.accent(GalleryActivity.this));}
            if(preview.getChildCount()<3){
                TextView badge=GalleryUi.text(GalleryActivity.this,"✓ 저장됨",14);badge.setPadding(dp(10),dp(4),dp(10),dp(4));
                badge.setBackground(GalleryUi.rounded(GalleryActivity.this,16,GalleryUi.color(GalleryActivity.this,com.google.android.material.R.attr.colorSecondaryContainer)));
                badge.setTextColor(GalleryUi.color(GalleryActivity.this,com.google.android.material.R.attr.colorOnSecondaryContainer));
                FrameLayout.LayoutParams badgeParams=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);preview.addView(badge,badgeParams);
            }
            preview.getChildAt(2).setVisibility(downloaded?View.VISIBLE:View.GONE);
            // GridView advances rows using the trailing cell height: reserve equal text lines per card.
            TextView caption=(TextView)card.getChildAt(1);caption.setText(description);caption.setLines(2);caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
            CheckBox check=(CheckBox)card.getChildAt(2);check.setLines(getResources().getConfiguration().fontScale>1.3f?2:1);check.setOnCheckedChangeListener(null);check.setText("저장 선택 "+(position+1));check.setChecked(selected);check.setEnabled(session.canSave(index));
            check.setOnCheckedChangeListener((v,value)->{if(value)session.selected.add(index);else session.selected.remove(index);persistSelection();refresh();});
            String result=session.social()?session.savedStatus.getOrDefault(index,session.xPost==null?"다시 조회 필요":""):session.previews.saveStatus(index);
            PreviewStore.State state=session.previews.state(session.id,index,!node.optString("targetUrl").isEmpty());
            TextView placeholder=(TextView)preview.getChildAt(1);placeholder.setVisibility(bitmap==null?View.VISIBLE:View.GONE);placeholder.setText(state.failed()?"미리보기 실패\n터치하여 상세 확인":node.optString("targetUrl").isEmpty()?"미리보기 주소 없음":state.text());
            int width=node.optInt("width",state.width()),height=node.optInt("height",state.height());
            TextView info=(TextView)card.getChildAt(3);info.setLines(3);info.setEllipsize(android.text.TextUtils.TruncateAt.END);info.setTextColor(GalleryUi.muted(GalleryActivity.this));info.setText((width>0&&height>0?(session.social()?"제공 정보 ":"미리보기 ")+width+" × "+height+"\n":"")+(result.isEmpty()?state.text():result.split("\n",2)[0]));return shell;
        }
    }
}
