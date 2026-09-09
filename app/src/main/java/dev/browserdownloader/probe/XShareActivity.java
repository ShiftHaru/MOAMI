package dev.browserdownloader.probe;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import dev.browserdownloader.xprobe.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Shared post metadata and preview URLs are kept only in this popup's memory. */
public final class XShareActivity extends Activity {
    // Serialize popup jobs so cancellation cleanup finishes before a new share writes the same file.
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService thumbnails=Executors.newFixedThreadPool(3);
    private final List<Future<?>> thumbnailJobs=new ArrayList<>();
    private final List<JSONObject> media=new ArrayList<>();
    private final Set<Integer> selected=new LinkedHashSet<>();
    private final Map<Integer,Bitmap> images=new HashMap<>();
    private final Map<Integer,String> previewStatus=new HashMap<>(), savedStatus=new HashMap<>();
    private final Cards adapter=new Cards();
    private Future<?> job;
    private int generation;
    private boolean busy, started;
    private String link="",postId="";
    private TextView status, count;
    private Button retry, save;
    private GridView grid;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferences=(prefs,key)->{if(!allowed())cancel();};
    private boolean allowed(){return ScanNotification.enabled(this)&&getSharedPreferences("probe",0).getBoolean("xDrawerConsent",false);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);setFinishOnTouchOutside(false);
        try{link=XLink.canonical(getIntent().getStringExtra("post"));}catch(Exception ignored){}
        if(state!=null){int[] indices=state.getIntArray("selected");if(indices!=null)for(int i:indices)selected.add(i);}
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(12),dp(12),dp(12),dp(12));
        TextView title=new TextView(this);title.setText("X 공유 · 미디어 선택 저장");title.setTextSize(22);body.addView(title);
        LinearLayout actions=new LinearLayout(this);body.addView(actions);
        button(actions,"닫기",()->finish());retry=button(actions,"다시 확인",this::inspect);button(actions,"중지",this::cancel);
        TextView notice=new TextView(this);notice.setText("공유 주소와 미리보기 요청이 X에 전달됩니다. 쿠키·API 키는 사용하지 않습니다. 권한 있는 공개 미디어만 저장하세요. GIF는 MP4와 변환 GIF를 함께 저장합니다(10fps·256색, 30초/1080p 한도). 닫기·화면 이탈 시 미완료 작업은 중지합니다.");
        ScrollView noticeScroll=new ScrollView(this);noticeScroll.addView(notice);body.addView(noticeScroll,new LinearLayout.LayoutParams(-1,dp(72)));
        status=new TextView(this);status.setMaxLines(3);body.addView(status);
        count=new TextView(this);body.addView(count);
        LinearLayout controls=new LinearLayout(this);
        button(controls,"모두 선택",()->{for(int i=0;i<media.size();i++)if(supported(media.get(i)))selected.add(i);refresh();});
        button(controls,"선택 해제",()->{selected.clear();refresh();});
        save=button(controls,"선택 저장",this::confirmSave);
        HorizontalScrollView controlScroll=new HorizontalScrollView(this);controlScroll.addView(controls);body.addView(controlScroll);
        grid=new GridView(this);grid.setNumColumns(2);grid.setHorizontalSpacing(dp(8));grid.setVerticalSpacing(dp(8));grid.setAdapter(adapter);
        grid.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int n=r-l>=dp(600)?3:2;if(grid.getNumColumns()!=n)grid.setNumColumns(n);});
        grid.setOnItemClickListener((p,v,position,id)->{
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageBitmap(images.get(position));
            new AlertDialog.Builder(this).setTitle(label(media.get(position))+" · 미리보기").setView(image).setPositiveButton("닫기",null).show();
        });
        body.addView(grid,new LinearLayout.LayoutParams(-1,0,1));setContentView(body);
        getSharedPreferences("probe",0).registerOnSharedPreferenceChangeListener(preferences);refresh();
    }
    @Override public void onAttachedToWindow(){
        super.onAttachedToWindow();var m=new android.util.DisplayMetrics();getWindowManager().getDefaultDisplay().getMetrics(m);
        int w=m.widthPixels,h=m.heightPixels;if(android.os.Build.VERSION.SDK_INT>=30){var b=getWindowManager().getCurrentWindowMetrics().getBounds();w=b.width();h=b.height();}
        getWindow().setLayout(Math.min(dp(900),Math.round(w*.94f)),Math.round(h*.90f));
    }
    @Override protected void onPostResume(){super.onPostResume();if(!started){started=true;inspect();}}
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);cancel();setIntent(intent);selected.clear();link="";
        try{link=XLink.canonical(intent.getStringExtra("post"));}catch(Exception ignored){}
        media.clear();images.clear();previewStatus.clear();savedStatus.clear();refresh();inspect();
    }
    private void inspect(){
        if(busy)return;
        if(link.isEmpty()){status.setText("공개 X 게시물 주소 하나를 공유해 주세요.");return;}
        if(!allowed()){status.setText("메인 화면에서 검사 활성화와 X 동의를 확인한 뒤 다시 공유해 주세요.");return;}
        stopThumbnails();images.clear();previewStatus.clear();savedStatus.clear();media.clear();refresh();
        start(()->{
            JSONObject post=new NativeProbe(getApplicationContext()).extractX(link);
            JSONArray items=post.getJSONArray("media");if(items.length()>16)throw new IllegalArgumentException();
            return post;
        },post->{
            postId=post.optString("postId");JSONArray items=post.optJSONArray("media");
            for(int i=0;items!=null&&i<items.length();i++)media.add(items.optJSONObject(i));
            selected.removeIf(i->i<0||i>=media.size()||!supported(media.get(i)));
            status.setText(media.isEmpty()?"게시물에서 미디어를 찾지 못했습니다.":"미디어 "+media.size()+"개 · 저장할 항목을 선택하세요.");
            refresh();loadThumbnails();
        },"미디어 확인 중…");
    }
    private void loadThumbnails(){
        int token=generation;
        for(int i=0;i<media.size();i++){
            int index=i;String url=media.get(i).optString("thumbnail",media.get(i).optString("url"));
            previewStatus.put(i,"미리보기 확인 중");
            thumbnailJobs.add(thumbnails.submit(()->{
                Bitmap bitmap=null;try{bitmap=XThumbnail.load(url);}catch(Exception ignored){}
                Bitmap result=bitmap;runOnUiThread(()->{
                    if(isDestroyed()||token!=generation){if(result!=null)result.recycle();return;}
                    if(result!=null)images.put(index,result);
                    previewStatus.put(index,result==null?"미리보기 실패 · 저장은 선택 가능":"미리보기 · 원본 확인 아님");adapter.notifyDataSetChanged();
                });
            }));
        }
    }
    private void confirmSave(){
        if(busy||selected.isEmpty())return;
        List<Integer> indices=new ArrayList<>(selected);
        new AlertDialog.Builder(this).setTitle(indices.size()+"개 미디어 저장")
                .setMessage("Download/BrowserDownloader에 저장합니다. 사진은 원본 후보, 영상은 제공 MP4 중 최고 해상도를 확인합니다. GIF는 MP4 보존과 별도 GIF 변환을 수행합니다.")
                .setNegativeButton("취소",null).setPositiveButton("저장",(d,w)->download(indices)).show();
    }
    private void download(List<Integer> indices){
        if(busy||!allowed())return;
        final JSONObject post=new JSONObject();final JSONArray items=new JSONArray();
        try{for(int i:indices){items.put(media.get(i));savedStatus.put(i,"저장 대기");}post.put("postId",postId).put("media",items);}catch(Exception e){return;}
        stopThumbnails();
        start(()->XMedia.downloadAll(getApplicationContext(),post,true),result->{
            JSONArray saved=result.optJSONArray("items");
            for(int n=0;n<indices.size();n++){
                JSONObject item=saved==null?null:saved.optJSONObject(n);String text="시작하지 않음 · 재시도 가능";
                if(item!=null){text=item.optBoolean("complete")?"저장 완료":item.optBoolean("cancelled")?"중지됨 · 재시도 가능":"저장 실패 · 재시도 가능";
                    for(String k:List.of("error","gifError","exportError"))if(item.has(k))text+="\n"+item.optString(k);
                    if(item.has("savedUri"))text+="\n"+("photo".equals(item.optString("kind"))?"사진 저장됨":"MP4 저장됨");
                    if(item.optJSONObject("gif")!=null&&item.optJSONObject("gif").has("savedUri"))text+=" · 변환 GIF 저장됨";
                }savedStatus.put(indices.get(n),text);
            }
            status.setText(result.optBoolean("allSaved")?"선택 미디어 저장 완료":"일부 항목 미완료 · 카드별 결과를 확인하세요.");refresh();
        },"선택 미디어 저장 중…");
    }
    private interface Job{JSONObject run()throws Exception;}
    private void start(Job task,java.util.function.Consumer<JSONObject> done,String message){
        busy=true;int token=++generation;status.setText(message);refresh();
        ScanNotification.status(this,"X 공유 미디어 작업 중 · 알림에서 중지 가능");
        job=worker.submit(()->{
            JSONObject result=null;String failure=null;
            try{if(Thread.currentThread().isInterrupted())throw new InterruptedException();result=task.run();}catch(Exception e){failure=FailureText.describe(e);}
            JSONObject value=result;String error=failure;
            runOnUiThread(()->{if(isDestroyed()||token!=generation)return;busy=false;job=null;
                if(error!=null){status.setText("작업 실패 · "+error);
                    for(int i:new ArrayList<>(savedStatus.keySet()))if(savedStatus.get(i).equals("저장 대기"))savedStatus.put(i,"저장 실패 · 재시도 가능");
                }else done.accept(value);
                if(ScanNotification.enabled(this))ScanNotification.status(this,"X 공유 작업 종료");refresh();
            });
        });
    }
    private void stopThumbnails(){for(Future<?> f:thumbnailJobs)f.cancel(true);thumbnailJobs.clear();
        for(int i:new ArrayList<>(previewStatus.keySet()))if(previewStatus.get(i).equals("미리보기 확인 중"))previewStatus.put(i,"미리보기 중지 · 다시 확인 가능");}
    private void cancel(){generation++;if(job!=null)job.cancel(true);job=null;busy=false;stopThumbnails();
        for(int i:new ArrayList<>(savedStatus.keySet()))if(savedStatus.get(i).equals("저장 대기"))savedStatus.put(i,"중지 요청 · 완료 파일은 유지 · 재시도 가능");
        if(status!=null){status.setText("작업 중지 · 완료 파일은 유지됩니다.");refresh();}}
    @Override protected void onStop(){cancel();super.onStop();}
    @Override protected void onDestroy(){getSharedPreferences("probe",0).unregisterOnSharedPreferenceChangeListener(preferences);thumbnails.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){out.putIntArray("selected",selected.stream().mapToInt(Integer::intValue).toArray());super.onSaveInstanceState(out);}
    private void refresh(){if(count==null)return;count.setText("선택 "+selected.size()+" / "+media.size());save.setEnabled(!busy&&!selected.isEmpty()&&allowed());retry.setEnabled(!busy);adapter.notifyDataSetChanged();}
    private static boolean supported(JSONObject item){return item!=null&&Set.of("photo","video","animated_gif").contains(item.optString("kind"));}
    private static String label(JSONObject item){return switch(item.optString("kind")){case "photo"->"사진";case "video"->"영상 · MP4";case "animated_gif"->"GIF · MP4 + 변환 GIF";default->"미지원 미디어";};}
    private Button button(LinearLayout parent,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setOnClickListener(v->action.run());parent.addView(b);return b;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private final class Cards extends BaseAdapter{
        public int getCount(){return media.size();}public Object getItem(int i){return media.get(i);}public long getItemId(int i){return i;}
        public View getView(int i,View old,ViewGroup parent){
            LinearLayout card;if(old instanceof LinearLayout)card=(LinearLayout)old;else{
                card=new LinearLayout(XShareActivity.this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(8),dp(8),dp(8),dp(8));card.setBackgroundColor(0xffedf1f6);
                ImageView image=new ImageView(XShareActivity.this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);card.addView(image,new LinearLayout.LayoutParams(-1,dp(140)));
                card.addView(new TextView(XShareActivity.this));CheckBox check=new CheckBox(XShareActivity.this);check.setFocusable(false);card.addView(check);
                card.addView(new TextView(XShareActivity.this));
            }
            JSONObject item=media.get(i);ImageView image=(ImageView)card.getChildAt(0);image.setImageBitmap(images.get(i));image.setContentDescription(label(item)+" 미리보기");
            ((TextView)card.getChildAt(1)).setText((i+1)+". "+label(item)+"\n제공 정보 "+item.optInt("expectedWidth")+" × "+item.optInt("expectedHeight"));
            CheckBox check=(CheckBox)card.getChildAt(2);check.setOnCheckedChangeListener(null);check.setText("저장 선택");check.setChecked(selected.contains(i));check.setEnabled(!busy&&supported(item));
            check.setOnCheckedChangeListener((v,yes)->{if(yes)selected.add(i);else selected.remove(i);refresh();});
            ((TextView)card.getChildAt(3)).setText(previewStatus.getOrDefault(i,"미리보기 대기")+"\n"+savedStatus.getOrDefault(i,""));return card;
        }
    }
}
