package dev.browserdownloader.probe;

import android.content.Context;
import android.content.Intent;

final class DrawerTarget {
    static final String CHROME = "com.android.chrome", X = "com.twitter.android";
    static String select(String packageName, boolean enabled, boolean xAllowed, boolean locked) {
        if (!enabled || locked) return "";
        if (CHROME.equals(packageName)) return CHROME;
        return xAllowed && X.equals(packageName) ? X : "";
    }
    static String openX(Context context) {
        try {
            context.startActivity(new Intent(context, XActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return "앱 내부 X 입력 화면 열기를 요청했습니다. 열리지 않으면 G1의 X 링크 입력 버튼을 눌러 주세요.";
        } catch (android.content.ActivityNotFoundException e) {
            return "X 입력 화면을 찾지 못했습니다. 최신 통합 APK를 설치해 주세요.";
        } catch (SecurityException e) {
            return "X 입력 화면을 열 수 없습니다. G1의 X 링크 입력 버튼을 눌러 주세요.";
        }
    }
}
