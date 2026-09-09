package dev.browserdownloader.probe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** G1 diagnostic screen, not a production downloader. */
public final class MainActivity extends Activity {
    private TextView result;
    private TextView execution;
    private final android.os.Handler statusHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private int statusChecks;
    private final Runnable statusCheck = new Runnable() {
        @Override public void run() { updateExecution(); if (++statusChecks < 20) statusHandler.postDelayed(this, 500); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(24, 56, 24, 40);
        scroll.addView(body);
        setContentView(scroll);
        text(body, "BrowserDownloader · G1 검사", 24);
        execution = text(body, "", 16);
        button(body, "실행 알림 허용·설정", v -> {
            getSharedPreferences("probe",0).edit().putBoolean("notificationHidden",false).apply(); ScanNotification.refresh(this);
            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 42);
            else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        });
        text(body, "현재 Chrome 탭의 이미지 주소 전달 여부를 확인합니다.\n최근 결과에서 단일·선택 이미지 파일을 저장할 수 있습니다. 사이트 원본 여부는 아직 미확인입니다.", 16);
        text(body, "접근성·미리보기 안내\n검사 버튼을 누르면 Chrome 화면 요소의 종류·이미지 설명·정제한 주소·위치를 기기 내부에 기록합니다. 최근 결과 화면에서는 검출된 이미지 주소로 직접 접속합니다. 이미지 서버에 네트워크 요청이 전달되며 데이터가 사용됩니다. 웹페이지 HTML을 다시 읽거나 Chrome 쿠키·인증 헤더를 가져오지 않습니다.\n전체 이미지 주소는 최신 검사 세션의 메모리에만 보관합니다. 미리보기는 앱 전용 캐시에 저장하고 새 검사·기록 삭제 시 지웁니다. 사용 권리가 있는 공개 시험 페이지만 검사하세요.", 16);
        CheckBox consent = new CheckBox(this);
        text(body, "루리웹 지원 이미지의 저장은 사이트 원본 후보 주소(/ori/)를 사용합니다. 미리보기는 기존 검출 이미지를 표시합니다. 저장 후 기준 파일과 비교해 주세요.", 16);
        text(body, "검사 완료 후 최근 결과 팝업에서 선택·저장하고 닫으면 Chrome으로 돌아갑니다. 현재 탭 검사는 Chrome이 전달하는 이미지를 한 번 읽고 같은 탭·페이지의 이전 결과에 추가합니다. 직접 스크롤한 뒤 다시 검사하면 추가 이미지를 모을 수 있습니다. 자동 스크롤은 하지 않습니다. 다른 탭·페이지 또는 앱 프로세스 재시작 후 검사는 새 수집입니다. 이미지 주소 500개·카드 1000개·기록 크기 보호 한도가 있으며 전체 수집과 원본 여부는 미확인입니다.", 16);
        consent.setText("위 내용을 확인했으며 검사에 동의합니다");
        consent.setChecked(PreviewStore.consent(this));
        consent.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                SharedPage.pending.clear();
                getSharedPreferences("probe", MODE_PRIVATE).edit().putInt("previewConsentVersion", 0).putBoolean("enabled", false).apply();
                PreviewStore.get(this).cancelRequests();
                PreviewStore.get(this).cancelSaves();
                ScanNotification.refresh(this); updateExecution();
            }
        });
        body.addView(consent);
        text(body, "X 서랍 안내\nX 앱의 실행 여부를 확인해 < 버튼을 표시하고, 누르면 앱 내부 X 링크 입력 화면을 엽니다. X 게시물 화면 내용이나 클립보드는 자동으로 읽지 않습니다. 링크는 직접 붙여넣거나 X 공유 → BrowserDownloader로 전달합니다. 공유 시 X에 미디어 정보와 썸네일을 자동 요청하고 팝업 그리드에서 선택 저장합니다. G1 또는 알림의 중지는 X 작업에도 적용됩니다. X 화면 이탈·회전 시 진행 작업을 취소하며 이미 저장된 파일은 유지합니다.", 16);
        CheckBox xConsent = new CheckBox(this);
        xConsent.setText("위 안내에 동의하고 X에서도 서랍 표시");
        xConsent.setChecked(getSharedPreferences("probe", MODE_PRIVATE).getBoolean("xDrawerConsent", false));
        xConsent.setOnCheckedChangeListener((view, checked) -> getSharedPreferences("probe", MODE_PRIVATE)
                .edit().putBoolean("xDrawerConsent", checked).apply());
        body.addView(xConsent);
        button(body, "X 링크 입력", v -> result.setText(DrawerTarget.openX(this)));
        button(body, "동의하고 검사 활성화", v -> {
            if (!consent.isChecked()) {
                result.setText("동의를 선택해야 검사를 활성화할 수 있습니다.");
                return;
            }
            getPreferences(MODE_PRIVATE).edit().putBoolean("consent", true).apply();
            getSharedPreferences("probe", MODE_PRIVATE).edit().putInt("previewConsentVersion", 1).apply();
            getSharedPreferences("probe", MODE_PRIVATE).edit().putBoolean("enabled", true).putBoolean("notificationHidden",false).apply();
            ScanNotification.refresh(this);
            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 41);
            else startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        button(body, "검사 중지", v -> {
            ScanNotification.stop(this); updateExecution();
            result.setText("검사를 중지했습니다.");
        });
        button(body, "최근 결과 확인", v -> showResult());
        button(body, "공유한 Chrome 탭으로 돌아가기", v -> {
            if (SharedPage.pending.peek(android.os.SystemClock.elapsedRealtime()).isEmpty()) {
                result.setText("공유 대기가 없거나 2분이 지났습니다. Chrome에서 페이지 주소를 다시 공유해 주세요."); return;
            }
            if (!ScanNotification.enabled(this) || !ScanNotification.connected()) {
                result.setText("먼저 동의하고 검사를 활성화한 뒤 접근성 연결을 확인해 주세요."); return;
            }
            finish(); // Return to the sender; never reopen the URL or create a new tab.
        });
        button(body, "공유 검사 대기 취소", v -> {
            SharedPage.pending.clear(); result.setText("공유 검사 대기를 취소했습니다.");
        });
        button(body, "저장 파일·기준 파일 비교", v -> startActivity(new Intent(this, CompareActivity.class)));
        button(body, "검사 기록 삭제", v -> {
            SharedPage.pending.clear();
            boolean cacheDeleted = PreviewStore.get(this).clear();
            java.io.File[] files = getFilesDir().listFiles((dir, name) ->
                    name.matches("(?:latest|shared)\\.json(?:\\.bak|\\.new)?") || name.startsWith("capture-"));
            int failed = cacheDeleted ? 0 : 1;
            if (files != null) for (java.io.File file : files) if (!file.delete()) failed++;
            result.setText(failed == 0 ? "검사 기록을 삭제했습니다." : "일부 검사 기록을 삭제하지 못했습니다.");
        });
        result = text(body, "Chrome에서 오른쪽 < 버튼을 열고 검사해 주세요.\n공유 진입: Chrome 페이지 주소 공유 → G1 → 돌아가기 → < → 현재 탭 검사. 주소가 같은 탭인지 확인하며 새 페이지를 열지 않습니다. 공유 대기는 메모리에만 보관하며 2분간 유효합니다.", 14);
        result.setTextIsSelectable(true);
        receiveShare(getIntent());
    }
    @Override protected void onResume() { super.onResume(); ScanNotification.refresh(this); statusChecks=0;statusHandler.post(statusCheck); }
    @Override protected void onPause() { statusHandler.removeCallbacks(statusCheck);super.onPause(); }
    private void updateExecution() {
        if (execution == null) return;
        execution.setText((!ScanNotification.enabled(this) ? "검사 비활성" : ScanNotification.connected() ? "접근성 연결됨 · 검사 활성"
                : ScanNotification.configured(this) ? "접근성 허용됨 · 시스템 연결 대기\n연결이 계속되지 않으면 접근성 설정의 서비스 상태를 확인해 주세요."
                : "접근성 허용 필요 · 설정을 확인해 주세요")
                + (!ScanNotification.allowed(this) ? "\n실행 알림 차단됨 · 알림 권한 또는 채널 설정을 확인해 주세요"
                : ScanNotification.hidden(this) ? "\n실행 알림 숨김 · 알림 허용·설정 버튼으로 다시 표시할 수 있습니다."
                : "\n실행 알림 허용됨 (상태바 표시는 시스템 설정의 영향을 받습니다)"));
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        ScanNotification.refresh(this); updateExecution();
        if (request == 41) startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        receiveShare(intent);
    }

    private void receiveShare(Intent intent) {
        if (!Intent.ACTION_SEND.equals(intent.getAction())) return;
        try {
            String raw = "text/plain".equals(intent.getType()) ? intent.getStringExtra(Intent.EXTRA_TEXT) : null;
            boolean accepted = SharedPage.pending.offer(raw, android.os.SystemClock.elapsedRealtime());
            if (accepted) new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    () -> SharedPage.pending.peek(android.os.SystemClock.elapsedRealtime()), SharedPage.TTL_MS);
            result.setText(accepted ? "공유 검사 대기 (2분)\n" + NodeProbe.safeUrl(raw)
                    + "\n돌아가기 버튼을 누른 뒤 Chrome의 < → 현재 탭 검사를 선택하세요. 주소창이 보이고 공유 주소와 일치해야 합니다. 자동 검사·페이지 재요청은 하지 않습니다."
                    : "HTTP(S) 페이지 주소 하나만 공유해 주세요. 여러 주소·설명이 섞인 텍스트는 지원하지 않습니다.");
        } catch (Exception e) {
            SharedPage.pending.clear(); result.setText("공유 내용을 읽지 못했습니다. Chrome에서 페이지 주소를 다시 공유해 주세요.");
        } finally {
            setIntent(new Intent(this, MainActivity.class));
        }
    }

    private void showResult() {
        startActivity(new Intent(this, ResultsActivity.class));
    }

    private TextView text(LinearLayout body, String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setPadding(0, 12, 0, 12);
        body.addView(view);
        return view;
    }

    private void button(LinearLayout body, String label, View.OnClickListener listener) {
        Button view = new Button(this);
        view.setText(label);
        view.setOnClickListener(listener);
        body.addView(view);
    }
}
