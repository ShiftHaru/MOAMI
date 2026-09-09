package dev.browserdownloader.xprobe;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView output;
    private LinearLayout actions;
    private EditText link;
    private final WorkCancellation cancellation = new WorkCancellation();

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        scroll.addView(layout);
        TextView title = new TextView(this);
        title.setText("BrowserDownloader · X 검증 버전\n\n공개 게시물의 사진·영상·GIF를 다운로드합니다. 저장 권한이 있는 링크를 입력하세요. 링크는 X에 요청되며 사용자 쿠키나 API 키는 사용하지 않습니다.\n\n저장 위치: Download/BrowserDownloader\n사진은 원본 후보와 실제 크기를 비교합니다. 영상은 제공되는 MP4 중 해상도를 비교합니다. GIF 게시물은 MP4를 보존하고 별도 GIF로 변환합니다.\n\n현재 GIF 변환은 10fps·고정 256색으로 색상 손실이 있습니다. 30초·1080p 픽셀 수 이하의 시험 범위이며 중지는 현재 프레임 처리 뒤 반영될 수 있습니다.");
        layout.addView(title);
        link = new EditText(this);
        link.setHint("https://x.com/사용자/status/게시물번호");
        link.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        link.setSaveEnabled(false);
        layout.addView(link);
        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        layout.addView(actions);
        button("Android 실행 환경 검사", () -> new NativeProbe(this).environment().toString(2));
        button("자체 제작 MP4 → GIF 검사", () -> new NativeProbe(this).convertFixture().toString(2));
        button("저장 복구 자체 검사 (시험파일 생성·삭제)", () -> StorageCheck.run(this));
        Button inspect = new Button(this);
        inspect.setText("미디어 확인");
        inspect.setOnClickListener(v -> {
            String input = link.getText().toString();
            start(() -> showX(new NativeProbe(this).inspectX(input)));
        });
        actions.addView(inspect);
        Button download = new Button(this);
        download.setText("게시물 미디어 다운로드");
        download.setOnClickListener(v -> {
            String input = link.getText().toString();
            start(() -> showX(new NativeProbe(this).downloadX(input, true)));
        });
        actions.addView(download);
        Button stop = new Button(this);
        stop.setText("검사 중지");
        stop.setOnClickListener(v -> {
            cancelWork();
        });
        layout.addView(stop);
        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setText("대기 중");
        layout.addView(output);
        setContentView(scroll);
    }

    private interface Job { String run() throws Exception; }
    protected boolean workAllowed() { return true; }
    protected void workState(boolean running) { }
    protected final void cancelWork() {
        cancellation.cancel();
    }
    private String showX(org.json.JSONObject result) throws Exception {
        StringBuilder text = new StringBuilder();
        org.json.JSONArray items = result.optJSONArray("items");
        if (items == null) {
            items = result.getJSONArray("media");
            text.append("미디어 ").append(items.length()).append("개를 찾았습니다.\n");
        } else text.append(result.optBoolean("allSaved") ? "다운로드 완료\n" : "일부 작업이 완료되지 않았습니다.\n");
        for (int i = 0; i < items.length(); i++) {
            org.json.JSONObject item = items.getJSONObject(i);
            String kind = item.optString("kind");
            text.append('\n').append(i + 1).append(". ").append(switch (kind) {
                case "photo" -> "사진";
                case "animated_gif" -> "GIF";
                case "video" -> "영상";
                default -> "미지원 미디어";
            });
            if (!item.has("complete")) {
                int width = item.optInt("expectedWidth"), height = item.optInt("expectedHeight");
                text.append(width > 0 && height > 0 ? " · 제공 정보 " + width + "×" + height : " · 제공 해상도 없음");
                org.json.JSONArray variants = item.optJSONArray("mp4Candidates");
                if (variants != null) {
                    text.append("\nMP4 후보 ").append(variants.length()).append("개");
                    for (int j=0; j<variants.length(); j++) {
                        org.json.JSONObject variant = variants.getJSONObject(j);
                        int w = variant.optInt("width"), h = variant.optInt("height");
                        text.append(" · ").append(w > 0 && h > 0 ? w + "×" + h : "해상도 미확인");
                    }
                }
            }
            if (item.has("complete")) {
                text.append(item.optBoolean("cancelled") ? " · 중지됨" : item.optBoolean("complete") ? " · 저장 완료" : " · 완료되지 않음");
                if (item.has("width")) text.append(" · ").append(item.getInt("width")).append('×').append(item.getInt("height"));
                if (item.optBoolean("photoSaved")) text.append(item.optBoolean("originalVerified") ? "\n사이트 원본 크기 일치" : "\n원본 미확인 후보");
                if (item.has("savedUri")) text.append("\n다운로드 폴더에 ").append(kind.equals("photo") ? "사진" : "MP4").append(" 저장됨");
                else if (item.optBoolean("mp4Saved") || item.optBoolean("photoSaved")) text.append("\n파일은 앱 내부에 보존됨 · 다운로드 폴더 저장 필요");
                if (item.has("gif")) text.append(item.getJSONObject("gif").has("savedUri")
                        ? "\n변환 GIF도 다운로드 폴더에 저장됨" : "\nGIF 변환됨 · 다운로드 폴더 저장 필요");
                for (String key : new String[]{"error", "gifError", "exportError"}) {
                    if (item.has(key)) text.append('\n').append(key.equals("gifError") ? "GIF 변환: " : key.equals("exportError") ? "폴더 저장: " : "실패 이유: ").append(item.getString(key));
                }
            }
            text.append('\n');
        }
        if (result.optBoolean("cancelled")) text.append("\n중지됨: 이미 저장한 파일은 유지됩니다.\n");
        if (result.optInt("unattempted") > 0) text.append("\n시작하지 않은 항목: ").append(result.getInt("unattempted")).append("개\n");
        if (result.has("folder")) text.append("\n저장 위치: ").append(result.getString("folder"));
        return text.toString();
    }
    private void button(String label, Job job) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(v -> start(job));
        actions.addView(button);
    }
    private void start(Job job) {
        if (!workAllowed()) { output.setText("메인 화면에서 검사 활성화와 X 동의를 확인해 주세요."); return; }
        for (int i = 0; i < actions.getChildCount(); i++) actions.getChildAt(i).setEnabled(false);
        output.setText("검사 중…");
        cancellation.reset();
        workState(true);
        worker.submit(() -> {
            String result;
            try {
                cancellation.enter();
                result = job.run();
            }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); result = "검사를 중지했습니다."; }
            catch (Exception e) { result = FailureText.describe(e); }
            finally { cancellation.leave(); }
            String text = result;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                output.setText(text);
                workState(false);
                for (int i = 0; i < actions.getChildCount(); i++) actions.getChildAt(i).setEnabled(true);
            });
        });
    }
    @Override protected void onDestroy() {
        cancelWork();
        worker.shutdownNow();
        super.onDestroy();
    }
}
