package dev.browserdownloader.probe;

import android.content.SharedPreferences;
import android.os.Bundle;

/** Uses the same X implementation as the standalone diagnostic, in this app's process. */
public final class XActivity extends dev.browserdownloader.xprobe.MainActivity {
    private final SharedPreferences.OnSharedPreferenceChangeListener preferences = (prefs, key) -> {
        if (!workAllowed()) cancelWork();
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getSharedPreferences("probe", 0).registerOnSharedPreferenceChangeListener(preferences);
    }
    @Override protected boolean workAllowed() {
        return ScanNotification.enabled(this) && getSharedPreferences("probe", 0).getBoolean("xDrawerConsent", false);
    }
    @Override protected void workState(boolean running) {
        if (ScanNotification.enabled(this)) ScanNotification.status(this, running ? "X 미디어 작업 중 · 알림에서 중지 가능" : "X 작업 종료 · 결과는 X 화면에서 확인");
    }
    @Override protected void onStop() {
        cancelWork();
        if (ScanNotification.enabled(this)) ScanNotification.status(this, "X 화면 이탈 · 작업 중지 요청");
        super.onStop();
    }
    @Override protected void onDestroy() {
        getSharedPreferences("probe", 0).unregisterOnSharedPreferenceChangeListener(preferences);
        super.onDestroy();
    }
}
