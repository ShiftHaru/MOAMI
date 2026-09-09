package dev.browserdownloader.probe;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Generated media only. Restore user preferences/report and remove only this test's exports. */
final class GalleryCheck {
    static void run(Instrumentation test){run(test,false);}
    static void run(Instrumentation test,boolean includeX){
        Context c=test.getTargetContext();Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;
        var prefs=c.getSharedPreferences("probe",0);Map<String,?> prior=new HashMap<>(prefs.getAll());
        File record=new File(c.getFilesDir(),"latest.json");byte[] original=null;Activity activity=null;
        ExecutorService serverThread=Executors.newCachedThreadPool();Set<String> sessions=new HashSet<>();
        try(ServerSocket server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))){
            if(record.isFile())original=Files.readAllBytes(record.toPath());
            ByteArrayOutputStream imageBytes=new ByteArrayOutputStream();Bitmap image=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888);image.eraseColor(0xff16796a);image.compress(Bitmap.CompressFormat.PNG,100,imageBytes);image.recycle();
            serverThread.submit(()->{while(!server.isClosed())try{Socket client=server.accept();serverThread.submit(()->serve(client,imageBytes.toByteArray()));}catch(IOException done){break;}});
            require(ScanNotification.configured(c)&&ScanNotification.allowed(c),"Existing user system permissions are required; never toggle them here");
            onMain(test,()->prefs.edit().remove("galleryConsentVersion").commit());
            Activity setup=test.startActivitySync(new Intent(c,SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            snapshot(test,c,"material-setup.png");
            onMain(test,()->{require(find(setup.getWindow().getDecorView(),CheckBox.class)!=null,"consent tutorial displayed");setup.finish();});
            onMain(test,()->prefs.edit().putInt("galleryConsentVersion",1).putInt("previewConsentVersion",1).putBoolean("enabled",true).putBoolean("chromeDrawer",true).commit());
            GallerySession state=GallerySession.get(c);String url="http://127.0.0.1:"+server.getLocalPort();
            onMain(test,()->state.lookup(url+"/page?private=fixture-secret"));
            await(test,()->!state.busy,35000);onMain(test,()->{require(state.nodes().length()==2,"deduplicated HTML gallery");sessions.add(state.id);});
            require(!Files.readString(record.toPath()).contains("fixture-secret"),"redacted persisted URLs");
            activity=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            Activity screen=activity;test.waitForIdleSync();
            onMain(test,()->{GridView grid=find(screen.getWindow().getDecorView(),GridView.class);require(grid.getChildCount()>=2&&grid.getChildAt(0).getHeight()==grid.getChildAt(1).getHeight(),"equal card heights for mixed descriptions");});
            onMain(test,()->{GridView grid=find(screen.getWindow().getDecorView(),GridView.class);require(grid.getChildAt(0).performClick(),"card handles detail click");});
            test.waitForIdleSync();Thread.sleep(500);
            var detailRoot=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();
            require(detailRoot!=null&&!detailRoot.findAccessibilityNodeInfosByText("전달된 이미지 크기").isEmpty(),"actual detail dialog is visible");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
            onMain(test,()->{require(find(screen.getWindow().getDecorView(),GridView.class)!=null,"main grid");require(find(screen.getWindow().getDecorView(),com.google.android.material.materialswitch.MaterialSwitch.class)!=null,"drawer switch");
                var check=find(screen.getWindow().getDecorView(),com.google.android.material.checkbox.MaterialCheckBox.class);require(check!=null,"Material gallery checkbox");check.performClick();require(state.selected.contains(1),"checkbox selects actual gallery item");
                Button all=button(screen.getWindow().getDecorView(),"모두 선택");require(all!=null,"partial selection still offers select all");all.performClick();require(state.selected.size()==2,"select all includes remaining items");
                button(screen.getWindow().getDecorView(),"선택 해제").performClick();require(state.selected.isEmpty(),"clear full selection");
                for(int mode:new int[]{android.content.res.Configuration.UI_MODE_NIGHT_NO,android.content.res.Configuration.UI_MODE_NIGHT_YES}){
                    var config=new android.content.res.Configuration(c.getResources().getConfiguration());config.uiMode=(config.uiMode&~android.content.res.Configuration.UI_MODE_NIGHT_MASK)|mode;
                    Context fallback=new androidx.appcompat.view.ContextThemeWrapper(c.createConfigurationContext(config),R.style.GalleryTheme);
                    require(androidx.core.graphics.ColorUtils.calculateContrast(GalleryUi.ink(fallback),GalleryUi.bg(fallback))>=4.5,"fallback text contrast");
                    EdgeHandle edge=new EdgeHandle(GalleryUi.overlay(fallback));edge.measure(0,0);require(edge.getMeasuredWidth()==GalleryUi.dp(c,48)&&edge.getMeasuredHeight()==GalleryUi.dp(c,112),"edge touch window size");
                }
            });
            test.waitForIdleSync();
            onMain(test,()->{find(screen.getWindow().getDecorView(),com.google.android.material.checkbox.MaterialCheckBox.class).performClick();require(state.selected.contains(1),"reselect after layout");state.save();});
            await(test,()->!state.previews.saving(),35000);
            onMain(test,()->{
                require(state.previews.saveStatus(1).startsWith("저장 완료"),"selected image save");
                state.updateSaveFeedback();
                require(state.saved(1),"completed cell feedback");
                require(!state.previews.isSaved("different-capture",1),"saved state isolated by capture");
                require(state.saveSummary.contains("저장 완료 1개"),"batch completion feedback");
            });
            String retained=state.id;
            onMain(test,()->state.lookup(url+"/missing"));await(test,()->!state.busy,35000);
            onMain(test,()->{require(state.id.equals(retained),"HTTP failure preserves prior gallery");String message=state.message;state.reload();require(state.message.equals(message)&&message.contains("404"),"resume preserves actionable error");});
            onMain(test,()->{state.lookup(url+"/slow");state.cancel();});Thread.sleep(500);
            onMain(test,()->require(state.id.equals(retained),"cancel preserves prior gallery"));
            Activity popup=test.startActivitySync(new Intent(c,ResultsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
            snapshot(test,c,"material-popup.png");
            onMain(test,()->{GridView g=find(popup.getWindow().getDecorView(),GridView.class);require(g.getChildCount()>0&&g.getHeight()>0,"popup grid visible: height="+g.getHeight()+" children="+g.getChildCount()+" visibility="+g.getVisibility()+" count="+g.getCount());});
            onMain(test,()->{require(popup.getWindow().isFloating(),"floating results");require(state.selected.contains(1),"main to popup selection");popup.finish();});
            test.waitForIdleSync();Thread.sleep(1200);
            onMain(test,()->require(GalleryUi.columns(320)==1&&GalleryUi.columns(400)==2&&GalleryUi.columns(700)==3&&GalleryUi.columns(900)==4,"responsive columns"));
            Bitmap capture=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();
            if(capture!=null)try(FileOutputStream output=new FileOutputStream(new File(c.getFilesDir(),"gallery-test.png"))){capture.compress(Bitmap.CompressFormat.PNG,100,output);capture.recycle();}
            onMain(test,()->{EditText input=find(screen.getWindow().getDecorView(),EditText.class);input.requestFocus();((android.view.inputmethod.InputMethodManager)screen.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input,0);});
            snapshot(test,c,"material-keyboard.png");
            onMain(test,()->{EditText input=find(screen.getWindow().getDecorView(),EditText.class);((android.view.inputmethod.InputMethodManager)screen.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);input.clearFocus();});
            var rotation=test.addMonitor(MainActivity.class.getName(),null,false);
            onMain(test,()->screen.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            Activity landscape=test.waitForMonitorWithTimeout(rotation,10000);test.removeMonitor(rotation);
            if(landscape!=null){activity=landscape;
            snapshot(test,c,"material-landscape.png");
            onMain(test,()->require(state.selected.contains(1)&&find(landscape.getWindow().getDecorView(),GridView.class)!=null,"rotation preserves gallery selection"));
            var portraitMonitor=test.addMonitor(MainActivity.class.getName(),null,false);
            onMain(test,()->landscape.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
            Activity portrait=test.waitForMonitorWithTimeout(portraitMonitor,10000);test.removeMonitor(portraitMonitor);require(portrait!=null,"restored orientation recreation");activity=portrait;result.putString("rotation","PASS recreation preserves selection");
            }else{onMain(test,()->screen.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));result.putString("rotation","UNVERIFIED: device did not recreate on requested landscape orientation");}
            Activity active=activity;
            onMain(test,()->{EditText input=find(active.getWindow().getDecorView(),EditText.class);input.setText("");button(active.getWindow().getDecorView(),"확인").performClick();require(find(active.getWindow().getDecorView(),com.google.android.material.textfield.TextInputLayout.class).getError()!=null&&!state.busy,"invalid address stays inline without request");state.lookup(url+"/empty");});
            await(test,()->!state.busy,35000);test.waitForIdleSync();
            onMain(test,()->{GridView g=find(active.getWindow().getDecorView(),GridView.class);require(g.getCount()==0&&g.getEmptyView().isShown(),"empty gallery provides visible guidance");});
            snapshot(test,c,"ux-empty.png");
            if(includeX){
                for(String link:List.of("https://x.com/RaminNasibov/status/2096854147219808434","https://x.com/GiFShitpost/status/2097106892585918695")){
                    var monitor=test.addMonitor(XShareActivity.class.getName(),null,false);
                    c.startActivity(new Intent(Intent.ACTION_SEND).setType("text/plain").setClass(c,ShareActivity.class).putExtra(Intent.EXTRA_TEXT,link).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    Activity x=test.waitForMonitorWithTimeout(monitor,15000);test.removeMonitor(monitor);require(x!=null,"X share popup routing");
                    await(test,()->!state.busy&&state.source.equals("X")&&state.xPost!=null,60000);
                    snapshot(test,c,"ux-x-media.png");
                    onMain(test,()->require(find(x.getWindow().getDecorView(),GridView.class).getChildCount()>0,"X shared media cards visible"));
                    onMain(test,()->{require(state.nodes().length()>0,"X media gallery");state.selected.add(1);state.save();});
                    await(test,()->!state.busy,120000);
                    onMain(test,()->{require("저장 완료".equals(state.savedStatus.get(1)),"X selected media saved");x.finish();});
                    test.waitForIdleSync();Thread.sleep(500);
                }
            }
            result.putString("result","PASS HTML/redaction/Material selection/save/404/cancel/popup/grid/contrast/48dp edge; generated media only");code=Activity.RESULT_OK;
            if(includeX)result.putString("x","PASS actual X share routing, photo and GIF/MP4 selected saves; authorized prior samples");
        }catch(Throwable failure){result.putString("error",failure.getClass().getSimpleName()+": "+failure.getMessage());}
        finally{
            serverThread.shutdownNow();Activity last=activity;
            onMain(test,()->{GallerySession.get(c).cancel();if(last!=null)last.finish();});
            for(String id:sessions)c.getContentResolver().delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,MediaStore.Downloads.DISPLAY_NAME+" LIKE ?",new String[]{"Chrome_"+id+"_%"});
            try{if(original!=null)Files.write(record.toPath(),original);else Files.deleteIfExists(record.toPath());}catch(IOException e){result.putString("cleanup","report restoration failed");code=Activity.RESULT_CANCELED;}
            var edit=prefs.edit().clear();for(var e:prior.entrySet()){Object v=e.getValue();if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);else if(v instanceof String)edit.putString(e.getKey(),(String)v);else if(v instanceof Long)edit.putLong(e.getKey(),(Long)v);else if(v instanceof Float)edit.putFloat(e.getKey(),(Float)v);}edit.commit();
            test.finish(code,result);
        }
    }
    private static void snapshot(Instrumentation test,Context c,String name)throws Exception{test.waitForIdleSync();Thread.sleep(1000);Bitmap image=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();if(image!=null)try(FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);image.recycle();}}
    private static <T> T find(View view,Class<T> type){if(type.isInstance(view))return type.cast(view);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){T found=find(((ViewGroup)view).getChildAt(i),type);if(found!=null)return found;}return null;}
    private static void onMain(Instrumentation test,Runnable work){AtomicReference<Throwable> error=new AtomicReference<>();test.runOnMainSync(()->{try{work.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError(error.get());}
    private static Button button(View view,String label){if(view instanceof Button b&&label.contentEquals(b.getText()))return b;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){Button b=button(g.getChildAt(i),label);if(b!=null)return b;}return null;}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void await(Instrumentation test,java.util.function.BooleanSupplier condition,long timeout)throws Exception{long until=SystemClock.elapsedRealtime()+timeout;AtomicBoolean done=new AtomicBoolean();while(SystemClock.elapsedRealtime()<until){onMain(test,()->done.set(condition.getAsBoolean()));if(done.get())return;Thread.sleep(100);}throw new AssertionError("timeout");}
    private static void serve(Socket client,byte[] image){try(client){BufferedReader in=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.US_ASCII));String line=in.readLine();String path=line.split(" ")[1];while((line=in.readLine())!=null&&!line.isEmpty()){}
        boolean page=path.startsWith("/page")||path.startsWith("/empty");boolean missing=path.startsWith("/missing");if(path.startsWith("/slow"))Thread.sleep(1500);
        byte[] data=page?"<img alt='Generated landscape with a much longer description for card row height testing' src='/one.png?key=fixture-secret'><img src='/one.png?key=fixture-secret'><img alt='Short' data-src='/two.png'>".getBytes(StandardCharsets.UTF_8):image;
        if(path.startsWith("/empty"))data="<html><p>No images</p></html>".getBytes(StandardCharsets.UTF_8);
        OutputStream out=client.getOutputStream();out.write(((missing?"HTTP/1.1 404 Not Found":"HTTP/1.1 200 OK")+"\r\nContent-Type: "+(page?"text/html; charset=utf-8":"image/png")+"\r\nContent-Length: "+data.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(data);
    }catch(Exception ignored){}}
}
