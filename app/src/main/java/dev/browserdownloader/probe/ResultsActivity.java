package dev.browserdownloader.probe;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.util.*;

/** One card per captured image node, including nodes with no usable address. */
public final class ResultsActivity extends Activity {
    private PreviewStore previews;
    private TextView summary;
    private GridView grid;
    private final ArrayList<JSONObject> images = new ArrayList<>();
    private JSONObject report;
    private String captureId = "";
    private final Cards adapter = new Cards();
    private AlertDialog details;
    private int restoredPosition;
    private final Set<Integer> selected = new LinkedHashSet<>();
    private TextView selection;
    private long revision;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setFinishOnTouchOutside(false);
        previews = PreviewStore.get(this);
        restoredPosition = saved == null ? 0 : saved.getInt("position", 0);
        if (saved != null) {
            captureId = saved.getString("capture", "");
            int[] checked = saved.getIntArray("selected");
            if (checked != null) for (int index : checked) selected.add(index);
        }
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(32), dp(12), dp(12));
        body.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(12) + insets.getSystemWindowInsetLeft(), dp(12) + insets.getSystemWindowInsetTop(),
                    dp(12) + insets.getSystemWindowInsetRight(), dp(12) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title = new TextView(this); title.setText("최근 검사 · 이미지 미리보기"); title.setTextSize(22); body.addView(title);
        LinearLayout actions = new LinearLayout(this);
        Button back = new Button(this); back.setText("닫기"); back.setOnClickListener(v -> finish()); actions.addView(back);
        Button json = new Button(this); json.setText("진단 정보 보기"); json.setOnClickListener(v -> {
            if (report == null) return;
            try { showText("정제된 진단 정보", report.toString(2)); } catch (Exception ignored) { }
        }); actions.addView(json); body.addView(actions);
        LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL);
        summary = new TextView(this); summary.setPadding(0, dp(8), 0, dp(8)); info.addView(summary);
        Button compare = new Button(this); compare.setText("저장 파일·기준 파일 비교");
        compare.setOnClickListener(v -> startActivity(new android.content.Intent(this, CompareActivity.class))); info.addView(compare);
        ScrollView infoScroll = new ScrollView(this); infoScroll.addView(info);
        body.addView(infoScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        selection = new TextView(this); body.addView(selection);
        LinearLayout downloads = new LinearLayout(this);
        Button all = new Button(this); all.setText("모두 선택"); all.setOnClickListener(v -> {
            for (JSONObject node : images) if (previews.canSave(captureId, node.optInt("index"))) selected.add(node.optInt("index"));
            adapter.notifyDataSetChanged(); updateSummary();
        }); downloads.addView(all);
        Button reset = new Button(this); reset.setText("선택 해제"); reset.setOnClickListener(v -> {
            selected.clear(); adapter.notifyDataSetChanged(); updateSummary();
        }); downloads.addView(reset);
        Button save = new Button(this); save.setText("선택 저장"); save.setOnClickListener(v -> confirmSave(new ArrayList<>(selected))); downloads.addView(save);
        Button cancel = new Button(this); cancel.setText("중지"); cancel.setOnClickListener(v -> previews.cancelSaves()); downloads.addView(cancel);
        HorizontalScrollView downloadScroll = new HorizontalScrollView(this); downloadScroll.addView(downloads); body.addView(downloadScroll);
        grid = new GridView(this);
        grid.setNumColumns(2);
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int columns = r-l >= dp(600) ? 3 : 2;
            if (grid.getNumColumns() != columns) grid.setNumColumns(columns);
        });
        grid.setHorizontalSpacing(dp(8)); grid.setVerticalSpacing(dp(8));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> showDetails(images.get(position)));
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int state) { }
            @Override public void onScroll(AbsListView view, int first, int count, int total) { requestVisible(); }
        });
        body.addView(grid, new LinearLayout.LayoutParams(-1, 0, 2));
        setContentView(body);
    }
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int width = metrics.widthPixels, height = metrics.heightPixels;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.graphics.Rect bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
            width = bounds.width(); height = bounds.height();
        }
        getWindow().setLayout(Math.min(dp(900), Math.round(width * .94f)), Math.round(height * .90f));
    }
    @Override protected void onResume() {
        super.onResume();
        previews.listen(() -> {
            if (!previews.isCurrent(captureId) || revision != previews.revision()) {
                if (details != null) details.dismiss();
                loadResult(); return;
            }
            updateSummary(); adapter.notifyDataSetChanged(); grid.post(this::requestVisible);
        });
        loadResult();
    }
    @Override protected void onPause() {
        previews.select(captureId, selected);
        previews.listen(null); previews.cancelRequests();
        if (!isChangingConfigurations()) previews.cancelSaves();
        if (details != null) details.dismiss();
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("capture", captureId);
        out.putIntArray("selected", selected.stream().mapToInt(Integer::intValue).toArray());
        out.putInt("position", grid.getFirstVisiblePosition()); super.onSaveInstanceState(out);
    }
    private void loadResult() {
        images.clear(); report = null;
        File file = new File(getFilesDir(), "latest.json");
        try {
            if (!file.isFile()) { summary.setText("검사 결과가 없습니다. Chrome의 < 버튼에서 검사를 실행해 주세요."); return; }
            if (file.length() > 8L * 1024 * 1024) throw new IllegalArgumentException();
            JSONObject loaded = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            JSONArray nodes = loaded.getJSONArray("nodes");
            if (nodes.length() > 5000) throw new IllegalArgumentException();
            String nextId = loaded.optString("captureId", "legacy-" + loaded.getLong("capturedAtEpochMs"));
            if (!captureId.equals(nextId)) { selected.clear(); selected.addAll(previews.selection(nextId)); }
            captureId = nextId;
            if (!previews.open(captureId)) { summary.setText("새 검사 결과가 준비 중입니다. 결과를 다시 열어 주세요."); return; }
            Set<Integer> indices = new HashSet<>();
            for (int i=0; i<nodes.length(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                if (!node.optBoolean("hasImage")) continue;
                int index = node.getInt("index");
                if (index <= 0 || !indices.add(index)) throw new IllegalArgumentException();
                images.add(node);
            }
            selected.retainAll(indices);
            revision = previews.revision(); report = loaded; updateSummary();
        } catch (Exception e) {
            images.clear(); report = null;
            summary.setText("검사 기록을 읽을 수 없습니다. Chrome에서 다시 검사해 주세요.");
        } finally {
            adapter.notifyDataSetChanged();
            grid.setSelection(restoredPosition); restoredPosition = 0;
            grid.post(this::requestVisible);
        }
    }
    private void updateSummary() {
        selection.setText("선택 " + selected.size() + "개 · 현재 검사 목록만 저장 · 원본 미확인");
        if (report == null) return;
        int withUrl = 0; for (JSONObject node : images) if (hasUrl(node)) withUrl++;
        String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                .format(new Date(report.optLong("capturedAtEpochMs")));
        String page = report.optString("addressBarUrl", "페이지 주소 미확인");
        if (page.length() > 160) page = page.substring(0,157) + "…";
        summary.setText(time + "\n" + page
                + ("ACTION_SEND_CURRENT_TAB".equals(report.optString("entry")) ? "\n공유 진입 · 검사 시작 시 주소 일치" : "")
                + "\n이미지 " + images.size() + " · 주소 확보 " + withUrl
                + "\n미리보기 성공 " + previews.count(true) + " · 실패 " + previews.count(false)
                + (report.optBoolean("manualMode") ? "\n수동 검사 " + report.optInt("passes") + "회 · " + report.optString("collectionStatus")
                    + "\n스크롤 후 현재 탭 검사로 추가 · 페이지 전체/원본 미확인"
                : report.optBoolean("collectionMode") ? "\n누적 관측 " + report.optInt("passes") + "회 · 서로 다른 주소 "
                    + report.optInt("uniqueImageUrls") + "개\n" + (report.optBoolean("collectionFinished")
                    ? report.optString("collectionStatus") : "수집 종료 기록 없음 · 부분 결과")
                    + "\n페이지 전체 수집·원본 여부 미확인" : "")
                + (images.isEmpty() ? "\n이미지 노드가 검출되지 않았습니다." : "\n썸네일 표시는 원본 확인을 의미하지 않습니다.")
                + (report.optBoolean("truncated") ? "\n검사 범위 제한: 일부 노드가 포함되지 않았습니다." : "")
                + (!PreviewStore.consent(this) ? "\n메인 화면에서 변경된 미리보기 안내에 동의해 주세요." : ""));
    }
    private void requestVisible() {
        if (report == null || !previews.isCurrent(captureId)) return;
        Set<Integer> visible = new HashSet<>();
        int first = grid.getFirstVisiblePosition(), last = grid.getLastVisiblePosition();
        for (int i=first; i<=last && i<images.size(); i++) if (i>=0) visible.add(images.get(i).optInt("index"));
        previews.retainVisible(visible);
        for (int i=first; i<=last && i<images.size(); i++) if (i>=0) {
            JSONObject node = images.get(i); previews.request(captureId, node.optInt("index"), hasUrl(node));
        }
    }
    private static boolean hasUrl(JSONObject node) { return !node.optString("targetUrl").isEmpty(); }
    private static String description(JSONObject node) {
        String text = node.optString("description");
        if (text.isEmpty() || text.equals("null")) text = node.optString("text");
        return text.isEmpty() || text.equals("null") ? "설명 없음" : text;
    }
    private void showDetails(JSONObject node) {
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(16),dp(12),dp(16),dp(12));
        int index = node.optInt("index");
        Bitmap bitmap = previews.bitmap(captureId, index);
        if (bitmap != null) {
            ImageView image = new ImageView(this); image.setImageBitmap(bitmap); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            body.addView(image, new LinearLayout.LayoutParams(-1, dp(320)));
        }
        PreviewStore.State state = previews.state(captureId, index, hasUrl(node));
        TextView text = new TextView(this); text.setTextIsSelectable(true);
        text.setText(state.text() + "\n전달된 이미지 크기: " + (state.width()>0 ? state.width()+" × "+state.height() : "미확인")
                + "\n확대 화면은 최대 512px 미리보기입니다.\n\n" + description(node)
                + "\n\n주소 (쿼리 제외): " + node.optString("targetUrl", "")
                + "\n노드 번호: " + index + " · 역할: " + node.optString("role")
                + "\n화면 내 표시: " + node.optBoolean("visible") + "\n위치: " + node.optJSONArray("bounds"));
        if (node.has("sourcePass")) text.append("\n첫 관측: " + node.optInt("sourcePass") + "회차 · 당시 노드 " + node.optInt("sourceIndex"));
        text.append("\n\n" + previews.saveStatus(index));
        text.append("\n\n" + previews.saveTargetInfo(captureId,index));
        body.addView(text); ScrollView scroll = new ScrollView(this); scroll.addView(body);
        details = new AlertDialog.Builder(this).setTitle("검출 이미지 #" + index).setView(scroll).setPositiveButton("닫기", null)
                .setNeutralButton("이미지 파일 저장", (dialog, which) -> confirmSave(Collections.singletonList(index))).show();
        details.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(previews.canSave(captureId,index));
    }
    private void confirmSave(Collection<Integer> indices) {
        if (indices.isEmpty()) { Toast.makeText(this, "저장할 이미지를 선택해 주세요.", Toast.LENGTH_SHORT).show(); return; }
        String id = captureId;
        new AlertDialog.Builder(this).setTitle(indices.size() + "개 이미지 파일 저장")
                .setMessage("검출 주소를 사용하되 루리웹의 지원 이미지 주소는 사이트 원본 후보 경로(/ori/)로 요청합니다. 후보 실패 시 축소 이미지로 자동 대체하지 않습니다. 받은 파일을 변환 없이 Download/BrowserDownloader에 저장합니다. 사이트 원본 여부는 파일 대조로 확인하세요.\n파일당 20MiB·30초이며 실패 항목은 다시 저장할 수 있습니다. 화면을 떠나면 미완료 저장을 취소합니다. 저장된 파일은 검사 기록 삭제로 지워지지 않습니다.")
                .setPositiveButton("저장", (dialog, which) -> {
                    if (previews.isCurrent(id)) previews.save(id, indices);
                }).setNegativeButton("취소", null).show();
    }
    private void showText(String title, String value) {
        TextView text = new TextView(this); text.setText(value); text.setTextIsSelectable(true); text.setPadding(dp(16),dp(12),dp(16),dp(12));
        ScrollView scroll = new ScrollView(this); scroll.addView(text);
        details = new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("닫기", null).show();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private final class Cards extends BaseAdapter {
        @Override public int getCount() { return images.size(); }
        @Override public Object getItem(int position) { return images.get(position); }
        @Override public long getItemId(int position) { return images.get(position).optInt("index"); }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            LinearLayout card;
            if (recycled instanceof LinearLayout) card = (LinearLayout) recycled;
            else {
                card = new LinearLayout(ResultsActivity.this); card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundColor(0xffedf1f6); card.setPadding(dp(8),dp(8),dp(8),dp(8));
                ImageView image = new ImageView(ResultsActivity.this); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                card.addView(image, new LinearLayout.LayoutParams(-1, dp(150)));
                TextView title = new TextView(ResultsActivity.this); title.setMaxLines(2); title.setMinLines(2); card.addView(title);
                TextView status = new TextView(ResultsActivity.this); status.setMinLines(2); status.setMaxLines(2); card.addView(status);
                CheckBox check = new CheckBox(ResultsActivity.this); check.setText("저장 선택"); check.setFocusable(false); card.addView(check);
                TextView saved = new TextView(ResultsActivity.this); saved.setMaxLines(2); card.addView(saved);
            }
            JSONObject node = images.get(position); int index = node.optInt("index");
            ImageView image = (ImageView) card.getChildAt(0);
            image.setImageBitmap(previews.bitmap(captureId, index)); image.setContentDescription(description(node));
            ((TextView) card.getChildAt(1)).setText("#" + index + " " + description(node));
            ((TextView) card.getChildAt(2)).setText(previews.state(captureId,index,hasUrl(node)).text());
            CheckBox check = (CheckBox) card.getChildAt(3); check.setOnCheckedChangeListener(null);
            check.setChecked(selected.contains(index)); check.setEnabled(previews.canSave(captureId,index));
            check.setOnCheckedChangeListener((button, checked) -> { if (checked) selected.add(index); else selected.remove(index); updateSummary(); });
            ((TextView) card.getChildAt(4)).setText(previews.saveStatus(index));
            return card;
        }
    }
}
