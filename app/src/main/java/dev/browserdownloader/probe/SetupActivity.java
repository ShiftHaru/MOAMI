package dev.browserdownloader.probe;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.nio.charset.StandardCharsets;

/** User-driven consent and system settings; no writes to secure settings. */
public final class SetupActivity extends androidx.appcompat.app.AppCompatActivity {
    static final int VERSION=2;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView connection;
    private final Runnable poll=new Runnable(){public void run(){if(isFinishing())return;render();handler.postDelayed(this,1000);}};
    private int step=-1;
    static boolean consent(Context c){return c.getSharedPreferences("probe",0).getInt("galleryConsentVersion",0)==VERSION;}
    static boolean ready(Context c){return AppMode.ready(consent(c),ScanNotification.configured(c),ScanNotification.allowed(c));}
    @Override public void onCreate(Bundle b){GalleryUi.theme(this,false);super.onCreate(b);}
    @Override protected void onResume(){super.onResume();step=-1;render();handler.postDelayed(poll,1000);}
    @Override protected void onPause(){handler.removeCallbacksAndMessages(null);super.onPause();}
    private void render(){
        if(connection!=null)connection.setText(!ScanNotification.configured(this)?"접근성: 아직 허용되지 않음":ScanNotification.connected()?"접근성: 허용됨 · 서비스 연결됨":"접근성: 허용됨 · 시스템 연결 대기");
        if(ready(this)){setResult(RESULT_OK);finish();return;}
        int next=!consent(this)?0:!BuildConfig.SHARE_ONLY&&!ScanNotification.configured(this)?1:2;
        if(step==next)return;step=next;connection=null;
        LinearLayout root=GalleryUi.column(this);GalleryUi.inset(root);
        ScrollView scroll=new ScrollView(this);LinearLayout body=GalleryUi.column(this);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(GalleryUi.text(this,"시작하기 · "+(BuildConfig.SHARE_ONLY?(step==0?1:2):step+1)+" / "+(BuildConfig.SHARE_ONLY?2:3),14));
        body.addView(GalleryUi.text(this,step==0?"안전하게 시작하세요":step==1?"Chrome 검사 준비":"실행 상태를 확인하세요",26));
        LinearLayout content=GalleryUi.column(this);int padding=GalleryUi.dp(this,16);content.setPadding(padding,padding,padding,padding);content.setBackground(GalleryUi.surface(this,false));
        LinearLayout.LayoutParams contentParams=new LinearLayout.LayoutParams(-1,-2);contentParams.topMargin=padding;body.addView(content,contentParams);body=content;
        if(step==0){
            body.addView(GalleryUi.text(this,"이용 안내\n저장 권한이 있는 콘텐츠만 이용하세요. 권리 확인과 사이트 이용 조건 준수는 사용자 책임입니다. 앱은 법이 허용하는 범위에서 무보증으로 제공되며 개발자의 책임은 GPL 조건과 적용 법률에 따라 제한됩니다. 법률상 배제할 수 없는 책임을 면제하지 않습니다.",16));
            body.addView(GalleryUi.button(this,"GPL·오픈소스 라이선스",this::licenses));
            CheckBox terms=new com.google.android.material.checkbox.MaterialCheckBox(this);terms.setText("이용 안내와 무보증 조건을 확인했습니다");body.addView(terms);
            body.addView(GalleryUi.text(this,BuildConfig.SHARE_ONLY?"네트워크 이용 안내\nX·Instagram 게시물 링크와 미디어 주소를 해당 서비스에 요청합니다. 데이터 요금이 발생할 수 있습니다. 로그인·쿠키·인증 헤더·클립보드는 가져오지 않습니다. 전체 요청 주소는 현재 프로세스 메모리에, 정제된 최근 결과와 썸네일은 앱 전용 저장소에 보관합니다. 최근 결과 교체 시 캐시를 정리하며 앱 데이터 삭제로 기록을 지울 수 있습니다.":"접근성·네트워크 이용 안내\nChrome에서 사용자가 검사를 누르면 화면 요소의 이미지 주소·설명·위치를 읽습니다. 전체 주소 확인을 위해 Chrome 사이트 정보 창을 잠시 열 수 있습니다. 원본/전체 수집을 보장하지 않습니다.\n웹주소 입력은 서버의 공개 HTML을 직접 요청합니다. X 공유·입력은 X와 미디어 서버에 정보·썸네일을 요청합니다. 데이터 요금이 발생할 수 있습니다. X·Instagram 공유 링크로 공개 미디어를 조회합니다. Instagram 로그인은 지원하지 않습니다. Chrome·Instagram 앱의 쿠키·인증 헤더·클립보드는 가져오지 않습니다.\n전체 요청 주소는 현재 프로세스 메모리에, 정제한 최근 결과와 썸네일은 앱 전용 저장소에 보관합니다. 최신 결과 교체 시 이전 캐시는 정리합니다. 앱 데이터 삭제로 기록을 지울 수 있습니다.",16));
            CheckBox data=new com.google.android.material.checkbox.MaterialCheckBox(this);data.setText(BuildConfig.SHARE_ONLY?"미디어 정보 처리와 네트워크 요청에 동의합니다":"접근성 정보 처리와 네트워크 요청에 동의합니다");body.addView(data);
            Button accept=GalleryUi.button(this,"동의하고 계속",()->{
                if(!terms.isChecked()||!data.isChecked())return;
                var edit=getSharedPreferences("probe",0).edit().putInt("galleryConsentVersion",VERSION).putInt("previewConsentVersion",1).putBoolean("enabled",true).putBoolean("notificationHidden",false).putBoolean("xDrawerConsent",false);
                if(!BuildConfig.SHARE_ONLY&&!getSharedPreferences("probe",0).contains("chromeDrawer"))edit.putBoolean("chromeDrawer",true);
                edit.apply();step=-1;render();
            });accept.setEnabled(false);GalleryUi.primary(accept);
            android.widget.CompoundButton.OnCheckedChangeListener changed=(v,c)->accept.setEnabled(terms.isChecked()&&data.isChecked());terms.setOnCheckedChangeListener(changed);data.setOnCheckedChangeListener(changed);body.addView(accept);
        }else if(step==1){
            body.addView(GalleryUi.text(this,"1. 아래 버튼으로 접근성 설정을 여세요.\n2. 설치된 앱에서 "+getApplicationInfo().loadLabel(getPackageManager())+"을 선택하세요.\n3. 서비스 사용을 허용하고 이 화면으로 돌아오세요.\n\n설정 메뉴 이름은 기기에 따라 다를 수 있습니다. 허용 전에는 갤러리 조회·저장을 시작하지 않습니다.",16));
            connection=GalleryUi.text(this,"접근성: 아직 허용되지 않음",14);body.addView(connection);
            Button settings=GalleryUi.button(this,"접근성 설정 열기",()->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));GalleryUi.primary(settings);body.addView(settings);
            if(Build.VERSION.SDK_INT>=33){
                body.addView(GalleryUi.text(this,"설정이 차단되거나 켤 수 없나요?",18));
                body.addView(GalleryUi.text(this,"직접 설치한 앱은 ‘제한된 설정’ 때문에 접근성 허용이 막힐 수 있습니다. 이 앱은 Chrome 검사 시 화면의 이미지 정보를 읽습니다. 이용 안내를 확인하고 신뢰하는 경우에만 허용하세요.",14));
                body.addView(GalleryUi.button(this,"앱 정보 열기",this::restrictedSettings));
            }
        }else{
            if(!BuildConfig.SHARE_ONLY){connection=GalleryUi.text(this,ScanNotification.connected()?"접근성: 허용됨 · 서비스 연결됨":"접근성: 허용됨 · 시스템 연결 대기",14);body.addView(connection);}
            body.addView(GalleryUi.text(this,BuildConfig.SHARE_ONLY?"미디어 조회·저장 상태와 중지 동작을 알림으로 제공합니다. 앱 알림과 ‘미디어 저장 상태’ 채널을 허용해 주세요. 상태바 표시는 시스템 설정에 따라 다릅니다.":"검사·저장 상태와 중지 동작을 알림으로 제공합니다. 앱 알림과 ‘Chrome 검사 실행 상태’ 채널을 허용해 주세요. 상태바 아이콘 표시는 시스템 설정에 따라 다릅니다.",16));
            Button notifications=GalleryUi.button(this,"실행 알림 허용",()->{
                if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},42);
                else notificationSettings();
            });GalleryUi.primary(notifications);body.addView(notifications);
            body.addView(GalleryUi.button(this,"알림 설정 열기",this::notificationSettings));
        }
        root.addView(GalleryUi.button(this,"나가기",()->{setResult(RESULT_CANCELED);finish();}));setContentView(root);
    }
    private void restrictedSettings(){
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("제한된 설정 허용 안내")
                .setMessage("1. 앱 정보 오른쪽 위 ⋮를 누르세요.\n2. ‘제한된 설정 허용’을 선택하고 본인 확인을 진행하세요.\n3. 앱으로 돌아와 ‘접근성 설정 열기’를 눌러 서비스 사용을 허용하세요.\n\n메뉴가 없다면 접근성 화면에서 이 앱을 눌러 차단 안내를 확인한 뒤 다시 시도하세요. 이미 허용됐거나 제한이 적용되지 않는 경우에는 접근성 설정으로 바로 진행할 수 있습니다.\n\n회사·보호자 관리 정책으로 제한된 기기는 관리자 확인이 필요할 수 있습니다. 앱 정보 화면을 여는 것만으로 권한이 허용되지는 않습니다.")
                .setNegativeButton("닫기",null)
                .setPositiveButton("앱 정보로 이동",(dialog,which)->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.fromParts("package",getPackageName(),null))))
                .show();
    }
    private void notificationSettings(){open(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,"chrome-scan-status"));}
    private void open(Intent intent){try{startActivity(intent);}catch(RuntimeException e){Toast.makeText(this,"설정 → 앱 → "+getApplicationInfo().loadLabel(getPackageManager())+"에서 권한을 확인해 주세요.",Toast.LENGTH_LONG).show();}}
    private void licenses(){
        try{StringBuilder text=new StringBuilder();for(String name:getAssets().list("licenses"))if(name.endsWith(".txt"))try(var in=getAssets().open("licenses/"+name);var bytes=new java.io.ByteArrayOutputStream()){byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))!=-1;)bytes.write(buffer,0,n);text.append(name).append("\n").append(new String(bytes.toByteArray(),StandardCharsets.UTF_8)).append("\n\n");}
            TextView view=GalleryUi.text(this,text.toString(),14);view.setTextIsSelectable(true);ScrollView scroll=new ScrollView(this);scroll.addView(view);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("오픈소스 라이선스").setView(scroll).setPositiveButton("닫기",null).show();
        }catch(Exception e){Toast.makeText(this,"라이선스를 읽지 못했습니다.",Toast.LENGTH_LONG).show();}
    }
}
