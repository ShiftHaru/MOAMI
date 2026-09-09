package dev.browserdownloader.probe;

import android.content.Context;
import android.os.*;
import dev.browserdownloader.xprobe.*;
import org.json.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

/** One process-owned session. Raw media URLs never enter the persisted report. */
final class GallerySession {
    private static GallerySession instance;
    static synchronized GallerySession get(Context c) { if(instance==null)instance=new GallerySession(c.getApplicationContext());return instance; }
    final Context context;
    final PreviewStore previews;
    final Set<Integer> selected=new LinkedHashSet<>();
    final Map<Integer,String> savedStatus=new HashMap<>();
    JSONObject report=new JSONObject(), xPost;
    String id="", source="", message="링크를 입력하거나 Chrome에서 검사해 주세요.";
    boolean busy;
    String pendingInput="", draft="";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private Future<?> job;
    private int generation;
    private Runnable listener;
    private List<Integer> saveBatch=List.of();
    private String saveCapture="";
    String saveSummary="";
    String saveNotice="";
    boolean saved(int index){return social()?savedStatus.getOrDefault(index,"").startsWith("저장 완료"):previews.isSaved(id,index);}
    void updateSaveFeedback(){
        if(!saveCapture.equals(id)){saveBatch=List.of();saveSummary="";saveNotice="";saveCapture=id;}
        if(saveBatch.isEmpty())return;
        int complete=0;for(int index:saveBatch)if(saved(index))complete++;
        if(saving()){saveSummary="저장 중 · 완료 "+complete+" / "+saveBatch.size()+"개";return;}
        int incomplete=saveBatch.size()-complete;
        saveSummary="저장 완료 "+complete+"개"+(incomplete>0?" · 미완료 "+incomplete+"개 (실패·취소 등)":"");
        saveNotice=saveSummary;saveBatch=List.of();
    }
    private GallerySession(Context c){if(BuildConfig.SHARE_ONLY)message="X·Instagram 게시물을 공유하거나 주소를 입력하세요.";context=c;previews=PreviewStore.get(c);reload();}
    void listen(Runnable value){listener=value;}
    boolean observed(){return listener!=null;}
    void changed(){if(listener!=null)listener.run();}
    JSONArray nodes(){return report.optJSONArray("nodes")==null?new JSONArray():report.optJSONArray("nodes");}
    void reload(){
        File f=new File(context.getFilesDir(),"latest.json");
        if(!f.isFile())return;
        try{
            if(f.length()>8L*1024*1024)throw new IllegalArgumentException();
            JSONObject next=new JSONObject(Files.readString(f.toPath(),StandardCharsets.UTF_8));
            if(BuildConfig.SHARE_ONLY&&!Set.of("X","Instagram").contains(next.optString("source")))throw new IllegalArgumentException();
            JSONArray nodes=next.getJSONArray("nodes");if(nodes.length()>5000)throw new IllegalArgumentException();
            JSONArray images=new JSONArray();Set<Integer> seen=new HashSet<>();
            for(int n=0;n<nodes.length();n++){JSONObject node=nodes.getJSONObject(n);if(!node.optBoolean("hasImage"))continue;
                int index=node.getInt("index");if(index<1||!seen.add(index))throw new IllegalArgumentException();images.put(node);}
            next.put("nodes",images);
            String nextId=next.optString("captureId","legacy-"+next.optLong("capturedAtEpochMs"));
            if(!previews.open(nextId))return;
            boolean replaced=!id.equals(nextId);
            if(replaced){selected.clear();selected.addAll(previews.selection(nextId));xPost=null;savedStatus.clear();}
            id=nextId;report=next;source=next.optString("source","Chrome 검사");
            if(replaced)message=next.optString("collectionStatus","저장할 항목을 선택하세요.");
        }catch(Exception invalid){message=BuildConfig.SHARE_ONLY?"최근 기록을 읽을 수 없습니다. 게시물 링크를 다시 조회하세요.":"최근 기록을 읽을 수 없습니다. 링크 조회 또는 Chrome 검사를 다시 진행하세요.";report=new JSONObject();selected.clear();}
    }
    void chromeChanged(){cancel();xPost=null;reload();changed();}
    boolean social(){return source.equals("X")||source.equals("Instagram");}
    boolean canSave(int index){return !saving() && (social()?xPost!=null&&index>0&&index<=nodes().length():previews.hasRequest(id,index));}
    boolean saving(){return busy||previews.saving();}
    void cancel(){boolean wasBusy=saving();generation++;if(job!=null)job.cancel(true);job=null;busy=false;previews.cancelSaves();previews.cancelRequests();if(wasBusy)message="작업 취소 · 완료 파일은 유지됩니다.";if(BuildConfig.SHARE_ONLY)ScanNotification.status(context,"미디어 작업 종료");changed();}
    void lookup(String raw){
        if(busy)return;
        if(!AppMode.accepts(raw)){message="X·Instagram 게시물 또는 릴스만 지원합니다.";changed();return;}
        String valid=PreviewRules.requestUrl(BuildConfig.SHARE_ONLY?XShareLink.extract(raw):raw);
        if(valid.isEmpty()){message="HTTP(S) 주소 하나를 입력하세요.";changed();return;}
        String host=URI.create(valid).getHost().toLowerCase(Locale.ROOT);
        boolean x=Set.of("x.com","www.x.com","twitter.com","www.twitter.com","mobile.twitter.com").contains(host);
        boolean instagram=InstagramLink.host(host);
        if(instagram){try{valid=InstagramLink.canonical(valid);}catch(Exception invalid){message="공개 Instagram 게시물 또는 릴스 주소를 입력하세요.";changed();return;}}
        try{if(x)valid=XLink.canonical(valid);}catch(Exception invalid){message="공개 X 게시물 주소를 입력하세요.";changed();return;}
        String target=valid;
        run(()->instagram?new NativeProbe(context).extractInstagram(target):x?new NativeProbe(context).extractX(target):WebImages.fetch(target),value->{
            try{publish(target,value,x||instagram);}catch(Exception error){message="결과 기록 실패 · 다시 조회해 주세요.";}
        },"미디어 확인 중… 이전 결과는 조회가 끝날 때까지 유지됩니다.");
    }
    private void publish(String page,Object result,boolean x)throws Exception{
        boolean instagram=x&&"Instagram".equals(((JSONObject)result).optString("provider"));
        String provider=instagram?"Instagram":x?"X":"웹주소 조회";
        JSONArray nodes=new JSONArray();Map<Integer,String> urls=new LinkedHashMap<>();
        if(x){
            JSONArray media=((JSONObject)result).getJSONArray("media");if(media.length()>(instagram?20:16))throw new IllegalArgumentException();
            for(int n=0;n<media.length();n++){
                JSONObject item=media.getJSONObject(n);int index=n+1;
                String thumb=PreviewRules.requestUrl(item.optString("thumbnail",item.optString("url")));
                if(!thumb.isEmpty()) {URI u=URI.create(thumb);if(instagram?!InstagramLink.mediaUrl(thumb):!"https".equalsIgnoreCase(u.getScheme())||!"pbs.twimg.com".equalsIgnoreCase(u.getHost())||u.getPort()!=-1)thumb="";}
                urls.put(index,thumb);
                nodes.put(new JSONObject().put("index",index).put("hasImage",true).put("targetUrl",NodeProbe.safeUrl(thumb))
                        .put("description",instagram&&"video".equals(item.optString("kind"))?"영상 · 오디오 없으면 GIF":label(item.optString("kind"))).put("kind",item.optString("kind"))
                        .put("width",item.optInt("expectedWidth")).put("height",item.optInt("expectedHeight")));
            }
        }else{
            WebImages.Result web=(WebImages.Result)result;page=web.page();
            for(WebImages.Item item:web.items()) {int index=nodes.length()+1;urls.put(index,item.url());
                nodes.put(new JSONObject().put("index",index).put("hasImage",true).put("targetUrl",NodeProbe.safeUrl(item.url()))
                        .put("description",NodeProbe.safeText(item.description())).put("kind","photo"));}
        }
        String next=previews.begin();
        for(var e:urls.entrySet())previews.remember(next,e.getKey(),e.getValue());
        JSONObject record=new JSONObject().put("schemaVersion",5).put("captureId",next).put("source",provider)
                .put("capturedAtEpochMs",System.currentTimeMillis()).put("addressBarUrl",NodeProbe.safeUrl(page)).put("nodes",nodes)
                .put("truncated",!x&&((WebImages.Result)result).limited())
                .put("collectionStatus",x?"원본 확인 상태는 항목별 저장 결과를 확인하세요.":"공개 HTML에서 찾은 이미지 · 동적 이미지/전체/원본 미확인");
        NodeProbe.write(context,"latest.json",record);
        report=record;id=next;source=provider;xPost=x?(JSONObject)result:null;
        selected.clear();savedStatus.clear();
        message=nodes.length()==0?(BuildConfig.SHARE_ONLY?"미디어를 찾지 못했습니다. 게시물을 확인하세요.":"이미지를 찾지 못했습니다. 동적 페이지는 Chrome 현재 탭 검사를 이용하세요."):record.optString("collectionStatus");
        previews.resultChanged();
    }
    void save(){
        if(saving()||selected.isEmpty())return;
        List<Integer> indices=new ArrayList<>(selected);
        saveCapture=id;saveBatch=indices;saveSummary="저장 준비 · "+indices.size()+"개";saveNotice="";
        if(!social()){previews.save(id,indices);changed();return;}
        if(xPost==null){message="게시물을 다시 공유하거나 조회해 주세요.";changed();return;}
        try{
            JSONObject subset=new JSONObject().put("postId",xPost.getString("postId"));JSONArray media=new JSONArray();
            for(int i:indices){media.put(xPost.getJSONArray("media").getJSONObject(i-1));savedStatus.put(i,"저장 대기");}
            subset.put("media",media);
            run(()->XMedia.downloadAll(context,subset,true),value->{
                JSONObject result=(JSONObject)value;JSONArray items=result.optJSONArray("items");
                for(int n=0;n<indices.size();n++) {JSONObject item=items==null?null:items.optJSONObject(n);
                    savedStatus.put(indices.get(n),item!=null&&item.optBoolean("complete")?"저장 완료"+(item.has("savedFormat")?" · "+item.optString("savedFormat"):""):"미완료 · 다시 선택 저장 가능\n"+(item==null?"처리되지 않은 항목입니다.":item.optString("error",item.optString("gifError",item.optString("exportError","작업이 취소되었습니다.")))));}
                message=result.optBoolean("allSaved")?"선택 미디어 저장 완료":"일부 항목 미완료 · 다시 시도해 주세요.";
            },source.equals("Instagram")?"선택 미디어 저장 중… 해상도·오디오 확인 후 오디오 트랙이 없으면 GIF로 변환합니다.":"선택 미디어 저장 중… GIF는 MP4 보존 및 GIF 변환");
        }catch(Exception invalid){message="미디어 정보를 다시 확인해 주세요.";changed();}
    }
    interface Job{Object execute()throws Exception;}
    private void run(Job task,java.util.function.Consumer<Object> done,String text){
        busy=true;message=text;int token=++generation;changed();ScanNotification.status(context,text);
        job=worker.submit(()->{
            Object value=null;String error=null;
            try{if(Thread.currentThread().isInterrupted())throw new InterruptedException();value=task.execute();}
            catch(Exception failure){
                String reason=Objects.toString(failure.getMessage(),"");
                error=reason.matches("HTTP [0-9]{3}")||Set.of("HTML 5MiB 제한 초과","30초 시간 제한 초과","리디렉션 제한 초과","HTML 또는 이미지 주소를 입력하세요.").contains(reason)
                        ? reason+" · 주소를 확인하고 다시 시도하세요.":FailureText.describe(failure);
            }
            catch(OutOfMemoryError failure){error="이미지가 너무 큽니다. 다른 페이지로 다시 시도하세요.";}
            Object output=value;String failure=error;
            main.post(()->{if(token!=generation)return;busy=false;job=null;
                if(failure!=null)message=failure;else done.accept(output);
                ScanNotification.status(context,"미디어 작업 종료");changed();});
        });
    }
    static String label(String kind){return switch(kind){case "video"->"영상 · MP4";case "animated_gif"->"GIF · MP4 + GIF";default->"이미지";};}
}
