package dev.browserdownloader.probe;

import android.app.*;
import android.content.*;
import android.os.Handler;
import android.os.Looper;

/** Status of the system-bound accessibility service, not a separate foreground service. */
public final class ScanNotification extends BroadcastReceiver {
    private static final String CHANNEL = "chrome-scan-status", STOP = "dev.browserdownloader.probe.STOP";
    private static final String DISMISS = "dev.browserdownloader.probe.DISMISS";
    private static final int ID = 100;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static boolean connected;
    private static String status = "Chrome 검사 대기";
    private static Runnable heartbeat;
    static boolean connected() { return connected; }
    static boolean hidden(Context context) { return context.getSharedPreferences("probe",0).getBoolean("notificationHidden",false); }
    static boolean enabled(Context context) {
        return context.getSharedPreferences("probe", 0).getBoolean("enabled", false) && PreviewStore.consent(context);
    }
    static void connection(Context context, boolean value) {
        connected = value; status = "Chrome 검사 대기"; refresh(context);
    }
    static boolean configured(Context context) {
        String list = android.provider.Settings.Secure.getString(context.getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName own = new ComponentName(context, ChromeProbeService.class);
        if (list != null) for (String item : list.split(":"))
            if (own.equals(ComponentName.unflattenFromString(item))) return true;
        return false;
    }
    static void disconnected(Context context) {
        SharedPage.pending.clear();
        PreviewStore.get(context).cancelRequests(); PreviewStore.get(context).cancelSaves();
        connection(context, false);
        // A package replacement or system unbind is not the user's Stop command.
        if (!configured(context)) stop(context);
    }
    static void status(Context context, String value) { status = value; refresh(context); }
    static boolean allowed(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Chrome 검사 실행 상태", NotificationManager.IMPORTANCE_LOW));
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL).getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    static void refresh(Context context) {
        if (heartbeat != null) { handler.removeCallbacks(heartbeat); heartbeat = null; }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!connected || !enabled(context) || hidden(context) || !allowed(context)) { manager.cancel(ID); return; }
        Context app = context.getApplicationContext();
        PendingIntent open = PendingIntent.getActivity(app, 0, new Intent(app, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getBroadcast(app, 1, new Intent(app, ScanNotification.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent dismiss = PendingIntent.getBroadcast(app, 2, new Intent(app, ScanNotification.class).setAction(DISMISS), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_mascot).setContentTitle(context.getString(R.string.app_name)+" · 검사 활성")
                .setContentText(status).setContentIntent(open).setDeleteIntent(dismiss).setOnlyAlertOnce(true).setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_SERVICE)
                // A killed process cannot keep renewing a stale 'running' status.
                .setTimeoutAfter(45_000).addAction(new Notification.Action.Builder(null, "검사·저장 중지", stop).build()).build();
        try { manager.notify(ID, notification); } catch (SecurityException ignored) { return; }
        heartbeat = () -> refresh(app); handler.postDelayed(heartbeat, 15_000);
    }
    static void stop(Context context) {
        SharedPage.pending.clear();
        GallerySession.get(context).cancel();
        context.getSharedPreferences("probe", 0).edit().putBoolean("enabled", false).apply();
        PreviewStore.get(context).cancelRequests(); PreviewStore.get(context).cancelSaves();
        status = "Chrome 검사 대기"; refresh(context);
    }
    @Override public void onReceive(Context context, Intent intent) {
        if (STOP.equals(intent.getAction())) stop(context);
        else if (DISMISS.equals(intent.getAction())) {
            context.getSharedPreferences("probe",0).edit().putBoolean("notificationHidden",true).apply(); refresh(context);
        }
    }
}
