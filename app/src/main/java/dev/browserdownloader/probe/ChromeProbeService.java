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
        if (!prefs.getBoolean("enabled", false) || !prefs.getBoolean("chromeDrawer", true) || !SetupActivity.ready(this)) hideDrawer();
        ScanNotification.refresh(this);
    };

    private WindowManager windowManager;
    private LinearLayout drawer;
    private WindowManager.LayoutParams drawerParams;
    private float drawerFraction=.5f;
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
        String target = DrawerTarget.select(packageName, ScanNotification.enabled(this) && SetupActivity.ready(this),
                getSharedPreferences("probe", MODE_PRIVATE).getBoolean("chromeDrawer", true), keyguard.isKeyguardLocked());
        if (!target.isEmpty()) showDrawer(target);
        else hideDrawer();
    }

    private void showDrawer(String target) {
        if (drawer != null && target.equals(drawerPackage)) return;
        hideDrawer(); // Keep manual results while visiting the app or X.
        drawerPackage = target;
        android.content.Context themed = GalleryUi.overlay(this);
        drawer = new LinearLayout(themed);
        drawer.setOrientation(LinearLayout.VERTICAL);
        EdgeHandle handle = new EdgeHandle(themed);
        drawer.addView(handle);
        drawerFraction=getSharedPreferences("probe",MODE_PRIVATE).getFloat("chromeDrawerPosition",.5f);
        handle.setDrag(new EdgeHandle.Drag(){
            private float original;
            public void start(){original=drawerFraction;}
            public void move(float delta){drawerFraction=HandlePosition.move(drawerFraction,delta,drawerHeight(),GalleryUi.dp(themed,112));positionDrawer();}
            public void end(boolean cancelled){if(cancelled){drawerFraction=original;positionDrawer();}else getSharedPreferences("probe",MODE_PRIVATE).edit().putFloat("chromeDrawerPosition",drawerFraction).apply();}
        });
        drawer.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->positionDrawer());
        LinearLayout panel=GalleryUi.column(themed);
        int pad=GalleryUi.dp(themed,16);
        panel.setPadding(pad,pad,pad,pad);
        panel.setBackground(GalleryUi.rounded(themed,24,GalleryUi.color(themed,com.google.android.material.R.attr.colorSurfaceContainer)));
        int width=GalleryUi.dp(themed,Math.min(260,Math.max(48,getResources().getConfiguration().screenWidthDp-16)));
        android.widget.ScrollView scroll=new android.widget.ScrollView(themed){
            @Override protected void onMeasure(int w,int h){int max=GalleryUi.dp(getContext(),getResources().getConfiguration().screenHeightDp*8/10);super.onMeasure(w,MeasureSpec.makeMeasureSpec(max,MeasureSpec.AT_MOST));}
        };
        scroll.addView(panel);scroll.setBackground(panel.getBackground().getConstantState().newDrawable());scroll.setClipToOutline(true);
        drawer.addView(scroll,new LinearLayout.LayoutParams(width,LinearLayout.LayoutParams.WRAP_CONTENT));
        handle.setOnClickListener(v -> {
            expanded = true;handle.setVisibility(android.view.View.GONE);scroll.setVisibility(android.view.View.VISIBLE);
        });
        panel.addView(GalleryUi.button(themed,"닫기 ›",()->{expanded=false;scroll.setVisibility(android.view.View.GONE);handle.setVisibility(android.view.View.VISIBLE);}));
        Button scan=GalleryUi.button(themed,"현재 탭 검사",this::capture);GalleryUi.primary(scan);panel.addView(scan);
        status=GalleryUi.text(themed,"스크롤 후 다시 검사하면 이미지 추가\n최근 결과에서 선택 저장",14);
        status.setTextColor(GalleryUi.muted(themed));status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);panel.addView(status);
        scroll.setVisibility(android.view.View.GONE);
        expanded = false;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.RIGHT | Gravity.TOP;
        if(android.os.Build.VERSION.SDK_INT>=30){params.flags|=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;params.setFitInsetsTypes(0);}
        params.y=drawerTop()+HandlePosition.top(drawerFraction,drawerHeight(),GalleryUi.dp(themed,112));
        drawerParams=params;
        windowManager.addView(drawer,params);
    }

    private int drawerTop(){
        if(android.os.Build.VERSION.SDK_INT>=30)return windowManager.getCurrentWindowMetrics().getWindowInsets().getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout()).top;
        return 0;
    }
    private int drawerHeight(){
        if(android.os.Build.VERSION.SDK_INT>=30){var metrics=windowManager.getCurrentWindowMetrics();var insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());return metrics.getBounds().height()-insets.top-insets.bottom;}
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();windowManager.getDefaultDisplay().getMetrics(metrics);return metrics.heightPixels;
    }
    private void positionDrawer(){
        if(drawer==null||drawerParams==null||!drawer.isAttachedToWindow())return;
        int top=drawerTop()+HandlePosition.top(drawerFraction,drawerHeight(),drawer.getHeight());
        if(drawerParams.y!=top){drawerParams.y=top;windowManager.updateViewLayout(drawer,drawerParams);}
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration configuration){super.onConfigurationChanged(configuration);hideDrawer();}

    private AccessibilityNodeInfo chromeRoot() {
        if (!getSharedPreferences("probe", MODE_PRIVATE).getBoolean("enabled", false) || !PreviewStore.consent(this) || !SetupActivity.ready(this) || !getSharedPreferences("probe",0).getBoolean("chromeDrawer",true)) return null;
        if (((KeyguardManager) getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked()) return null;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && isChrome(root.getPackageName())) return root;
        if (root != null) root.recycle();
        return null;
    }

    private void capture() {
        cancelLookup();
        if (status != null) status.setText("Chrome 사이트 정보에서 전체 주소 확인 중");
        addressLookup = new ChromePageAddress(this::chromeRoot, () -> performGlobalAction(GLOBAL_ACTION_BACK), full -> {
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
            GallerySession.get(this).chromeChanged();
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
            LinearLayout old=drawer;
            drawer = null;
            drawerParams=null;
            windowManager.removeView(old);
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
