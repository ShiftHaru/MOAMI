package dev.browserdownloader.probe;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;


import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import org.json.JSONArray;
import android.os.SystemClock;
import java.util.*;

public final class ChromeProbeService extends AccessibilityService {
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (prefs, key) -> {
        if (!prefs.getBoolean("enabled", false) || ("xDrawerConsent".equals(key) && !prefs.getBoolean(key, false))) hideDrawer();
        ScanNotification.refresh(this);
    };

    private WindowManager windowManager;
    private LinearLayout drawer;
    private String drawerPackage = "";
    private TextView status;
    private boolean expanded;
    private final Map<String,Integer> imageUrls = new LinkedHashMap<>();
    private final Map<AccessibilityNodeInfo,Integer> missingImages = new HashMap<>();
    private AccessibilityNodeInfo document;
    private String collectionId = "", pageIdentity = "";
    private JSONArray collectedNodes = new JSONArray();
    private int passes, recordBytes;
    private ChromePageAddress addressLookup;

    @Override protected void onServiceConnected() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        getSharedPreferences("probe", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(preferenceListener);
        ScanNotification.connection(this, true);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        String packageName = root == null ? "" : String.valueOf(root.getPackageName());
        if (root != null) {
            if (document != null && addressLookup == null && isChrome(root.getPackageName())) {
                AccessibilityNodeInfo page = findDocument(root);
                try { if (page != null && !page.equals(document)) resetSession(); }
                finally { if (page != null) page.recycle(); }
            }
            root.recycle();
        }
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        // Chrome briefly has no active root while its own page-info window closes.
        // The bounded lookup waits without input; real app changes and disabling still cancel it.
        if (root == null && addressLookup != null && ScanNotification.enabled(this) && !keyguard.isKeyguardLocked()) return;
        String target = DrawerTarget.select(packageName, ScanNotification.enabled(this),
                getSharedPreferences("probe", MODE_PRIVATE).getBoolean("xDrawerConsent", false), keyguard.isKeyguardLocked());
        if (!target.isEmpty()) showDrawer(target);
        else hideDrawer();
    }

    private void showDrawer(String target) {
        if (drawer != null && target.equals(drawerPackage)) return;
        hideDrawer(); // Keep manual results while visiting the app or X.
        drawerPackage = target;
        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setPadding(8,8,8,8);
        drawer.setBackgroundColor(0xffe8eef7);
        Button handle = button("<");
        handle.setOnClickListener(v -> {
            expanded = !expanded;
            for (int i = 1; i < drawer.getChildCount(); i++)
                drawer.getChildAt(i).setVisibility(expanded ? android.view.View.VISIBLE : android.view.View.GONE);
            handle.setText(expanded ? ">" : "<");
        });
        if (DrawerTarget.CHROME.equals(target)) {
        Button scan = button("현재 탭 검사");
        scan.setOnClickListener(v -> capture());
        } else {
            Button input = button("X 링크 입력하기");
            input.setOnClickListener(v -> {
                AccessibilityNodeInfo active = getRootInActiveWindow();
                String current = active == null ? "" : String.valueOf(active.getPackageName());
                if (active != null) active.recycle();
                String valid = DrawerTarget.select(current, ScanNotification.enabled(this),
                        getSharedPreferences("probe", MODE_PRIVATE).getBoolean("xDrawerConsent", false),
                        ((KeyguardManager) getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked());
                if (!DrawerTarget.X.equals(valid)) { hideDrawer(); return; }
                String message = DrawerTarget.openX(this);
                if (status != null) status.setText(message);
            });
        }
        if (DrawerTarget.X.equals(target)) { Button stop = button("중지");
        stop.setOnClickListener(v -> {
            ScanNotification.stop(this);
            hideDrawer();
        });
        }
        status = new TextView(this);
        status.setText(DrawerTarget.X.equals(target)
                ? "앱 내부 X 화면에서 직접 붙여넣기·다운로드\n알림 중지는 X 작업에도 적용됩니다."
                : "스크롤 후 다시 검사하면 이미지 추가\n최근 결과에서 선택 저장");
        status.setTextColor(0xff182738);
        drawer.addView(status);
        for (int i=1;i<drawer.getChildCount();i++) drawer.getChildAt(i).setVisibility(android.view.View.GONE);
        expanded = false;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        windowManager.addView(drawer,params);
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        drawer.addView(button);
        return button;
    }

    private AccessibilityNodeInfo chromeRoot() {
        if (!getSharedPreferences("probe", MODE_PRIVATE).getBoolean("enabled", false) || !PreviewStore.consent(this)) return null;
        if (((KeyguardManager) getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked()) return null;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && isChrome(root.getPackageName())) return root;
        if (root != null) root.recycle();
        return null;
    }

    private void capture() {
        cancelLookup();
        if (status != null) status.setText("Chrome 사이트 정보에서 전체 주소 확인 중");
        addressLookup = new ChromePageAddress(this::chromeRoot, full -> {
            addressLookup = null;
            if (full.isEmpty()) {
                if (status != null) status.setText("전체 주소 확인 실패 · 원래 탭에서 다시 검사해 주세요.");
            } else captureResolved(full);
        });
        addressLookup.start();
    }
    private void captureResolved(String fullAddress) {
        AccessibilityNodeInfo root = chromeRoot(), page = null;
        Map<Integer,AccessibilityNodeInfo> identities = new HashMap<>();
        if (root == null) return;
        try {
            page = findDocument(root);
            if (page == null) return;
            boolean shared = !SharedPage.pending.peek(SystemClock.elapsedRealtime()).isEmpty();
            if (shared && !SharedPage.pending.matches(fullAddress, SystemClock.elapsedRealtime())) {
                if (status != null) status.setText("공유 주소와 현재 탭이 다릅니다. 앱에서 공유 대기를 취소하거나 원래 탭으로 돌아가 주세요.");
                return;
            }
            Map<Integer,String> raw = new HashMap<>();
            String displayed = ChromePageAddress.text(root, "url_bar");
            JSONObject snapshot = NodeProbe.capture(root, raw::put, "pending", identities);
            AccessibilityNodeInfo current = chromeRoot(), currentPage = current == null ? null : findDocument(current);
            boolean unchanged;
            try { unchanged = currentPage != null && currentPage.equals(page)
                    && displayed.equals(ChromePageAddress.text(current, "url_bar")); }
            finally { if (currentPage != null) currentPage.recycle(); if (current != null) current.recycle(); }
            if (!unchanged) { if (status != null) status.setText("검사 도중 탭이 바뀌었습니다. 다시 검사해 주세요."); return; }
            if (shared && !SharedPage.pending.consume(fullAddress, SystemClock.elapsedRealtime())) return;
            PreviewStore previews = PreviewStore.get(this);
            if (!page.equals(document) || !fullAddress.equals(pageIdentity) || !previews.isCurrent(collectionId)) {
                resetSession();
                collectionId = previews.begin(); document = AccessibilityNodeInfo.obtain(page); pageIdentity = fullAddress;
            }
            passes++;
            JSONArray nodes = snapshot.getJSONArray("nodes");
            String limit = "";
            int added = 0;
            for (int i=0; i<nodes.length(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                if (!node.optBoolean("hasImage")) continue;
                int source = node.getInt("index");
                String url = PreviewRules.requestUrl(raw.get(source));
                AccessibilityNodeInfo identity = identities.get(source);
                Integer index = url.isEmpty() ? missingImages.get(identity) : imageUrls.get(url);
                if (index != null) continue;
                Integer missing = missingImages.get(identity);
                if (!url.isEmpty() && imageUrls.size() >= 500) { limit = "이미지 주소 500개 보호 한도"; continue; }
                if (missing == null && collectedNodes.length() >= 1000) { limit = "이미지 카드 1000개 보호 한도"; continue; }
                index = missing == null ? collectedNodes.length()+1 : missing;
                node.put("sourceIndex", source).put("sourcePass", passes).put("index", index).put("parentIndex", 0);
                int oldSize = missing == null ? 0 : collectedNodes.getJSONObject(index-1).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                int size = node.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (recordBytes-oldSize+size > 4*1024*1024) { limit = "기록 크기 보호 한도"; continue; }
                recordBytes += size-oldSize;
                collectedNodes.put(index-1, node); added++;
                if (!url.isEmpty()) {
                    imageUrls.put(url, index); previews.remember(collectionId, index, url);
                    if (missing != null) {
                        var entries = missingImages.entrySet().iterator();
                        while (entries.hasNext()) {var entry=entries.next(); if (entry.getValue().equals(missing)) {entry.getKey().recycle(); entries.remove(); break;}}
                    }
                } else missingImages.put(AccessibilityNodeInfo.obtain(identity), index);
            }
            String message = "이번 검사 추가 " + added + "개 · 누적 " + collectedNodes.length() + "개";
            if (!limit.isEmpty()) message += " · " + limit;
            snapshot.put("schemaVersion", 4).put("captureId", collectionId).put("entry", shared ? "ACTION_SEND_CURRENT_TAB" : "DRAWER")
                    .put("addressBarUrl", NodeProbe.safeUrl(fullAddress)).put("manualMode", true).put("passes", passes)
                    .put("collectionStatus", message).put("complete", false).put("nodes", collectedNodes)
                    .put("imageNodes", collectedNodes.length()).put("imageNodesWithUrl", imageUrls.size()).put("uniqueImageUrls", imageUrls.size())
                    .put("truncated", snapshot.optBoolean("truncated") || !limit.isEmpty())
                    .put("chromeVersion", getPackageManager().getPackageInfo("com.android.chrome",0).versionName);
            NodeProbe.write(this, "latest.json", snapshot);
            previews.resultChanged();
            if (status != null) status.setText(message + "\n스크롤 후 다시 검사 · 전체/원본 미확인");
            ScanNotification.status(this, "수동 검사 · 누적 이미지 " + collectedNodes.length() + "개");
            hideDrawer();
            try {
                startActivity(new android.content.Intent(this, ResultsActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));
            } catch (RuntimeException unavailable) {
                android.widget.Toast.makeText(this, "검사 완료 · 앱의 최근 결과 확인을 열어 주세요.", android.widget.Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            if (status != null) status.setText("검사 실패 · 다시 검사해 주세요.");
        } finally {
            for (AccessibilityNodeInfo identity : identities.values()) identity.recycle();
            if (page != null) page.recycle(); root.recycle();
        }
    }
    private void cancelLookup() {
        if (addressLookup != null) { addressLookup.cancel(); addressLookup = null; }
    }
    private void resetSession() {
        if (document != null) { document.recycle(); document = null; }
        for (AccessibilityNodeInfo node : missingImages.keySet()) node.recycle();
        missingImages.clear(); imageUrls.clear(); collectedNodes = new JSONArray();
        collectionId = pageIdentity = ""; passes = recordBytes = 0;
    }
    private void hideDrawer() {
        cancelLookup();

        if (drawer != null) {
            windowManager.removeView(drawer);
            drawer = null;
            status = null;
        }
        drawerPackage = "";
    }

    @Override public void onInterrupt() { hideDrawer(); ScanNotification.status(this, "검사 중단 · Chrome에서 다시 시작 가능"); }
    @Override public boolean onUnbind(android.content.Intent intent) {
        hideDrawer(); resetSession(); ScanNotification.disconnected(this);
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        hideDrawer();
        ScanNotification.disconnected(this);
        getSharedPreferences("probe", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferenceListener);
        resetSession(); super.onDestroy();
    }

    static boolean isChrome(CharSequence name) { return "com.android.chrome".equals(String.valueOf(name)); }
    static AccessibilityNodeInfo findDocument(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>(); queue.add(AccessibilityNodeInfo.obtain(root));
        AccessibilityNodeInfo found = null;
        for (int examined=0; !queue.isEmpty() && examined<5000; examined++) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if ("android.webkit.WebView".equals(String.valueOf(node.getClassName())) && node.isVisibleToUser()) { found = node; break; }
            for (int i=0; i<node.getChildCount(); i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null) queue.add(child); }
            node.recycle();
        }
        while (!queue.isEmpty()) queue.removeFirst().recycle();
        return found;
    }
}
