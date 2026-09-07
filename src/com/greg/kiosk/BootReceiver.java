package com.greg.kiosk;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

public class BootReceiver extends BroadcastReceiver {
    // Fire HD 8 10th gen model identifiers (K72LL4 / KFONWI board).
    // Fire loads Greg's face from Sentinel HTTPS. Skylight loads the
    // local dashboard.html via busybox httpd. Same APK, device-aware URL.
    private static final String FIRE_URL = "https://192.168.2.11:9443/";
    private static final String SKYLIGHT_URL = "http://127.0.0.1:8080/dashboard.html";
    private static final long WATCHDOG_INTERVAL_MS = 60000; // Android enforces
    // a ~60s floor on setInexactRepeating regardless of a shorter request
    // ("Suspiciously short interval ... expanding to 60 seconds" in logcat)
    // so we ask for what it will actually deliver.

    private boolean isFire() {
        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        return model.contains("KFONWI") || model.contains("K72LL4") || model.contains("FIRE");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Start httpd for local dashboard serving (Skylight only needs
            // this, but harmless to start on Fire too — unused there)
            try {
                Runtime.getRuntime().exec(new String[]{
                    "/system/bin/busybox", "httpd", "-p", "127.0.0.1:8080", "-h", "/sdcard"
                });
            } catch (Exception e) { /* best effort */ }

            String url = isFire() ? FIRE_URL : SKYLIGHT_URL;

            // Launch the kiosk activity
            Intent launch = new Intent(context, KioskActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launch.setData(android.net.Uri.parse(url));
            context.startActivity(launch);

            // Start the floating home button overlay
            try {
                context.startForegroundService(new Intent(context, FloatingHomeService.class));
            } catch (Exception e) { /* best effort */ }

            // Register the repeating watchdog alarm. This runs in a
            // separate process (see manifest) and survives even if the
            // main greg-kiosk process gets OOM-killed by a heavy launched
            // app. It periodically revives FloatingHomeService if the
            // process holding it died.
            scheduleWatchdog(context);
        }
    }

    private void scheduleWatchdog(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent watchdogIntent = new Intent(context, WatchdogReceiver.class);
        watchdogIntent.setAction(WatchdogReceiver.ACTION_CHECK);
        PendingIntent pi = PendingIntent.getBroadcast(
            context, 0, watchdogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT);
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS,
            WATCHDOG_INTERVAL_MS,
            pi);
    }
}
