package com.greg.kiosk;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

public class BootReceiver extends BroadcastReceiver {
    private static final String FACE_URL = "https://192.168.2.11:9443/";
    private static final long WATCHDOG_INTERVAL_MS = 60000;

    private boolean isFire() {
        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        String board = Build.BOARD != null ? Build.BOARD.toUpperCase() : "";
        return model.contains("KFONWI") || model.contains("KFMUWI")
            || board.contains("ONYX") || model.contains("FIRE");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Start httpd for legacy fallback
            try {
                Runtime.getRuntime().exec(new String[]{
                    "/system/bin/busybox", "httpd", "-p", "127.0.0.1:8080", "-h", "/sdcard"
                });
            } catch (Exception e) { /* best effort */ }

            // Kill Amazon launchers that fight for the foreground on Fire OS
            if (isFire()) {
                new Thread(new Runnable() {
                    public void run() {
                        // Repeated kills over 30 seconds — Amazon relaunches aggressively
                        for (int i = 0; i < 10; i++) {
                            try {
                                Runtime.getRuntime().exec(new String[]{"am", "force-stop", "com.amazon.firelauncher"}).waitFor();
                                Runtime.getRuntime().exec(new String[]{"am", "force-stop", "com.amazon.tv.launcher"}).waitFor();
                                Thread.sleep(3000);
                            } catch (Exception e) { /* best effort */ }
                        }
                    }
                }).start();
            }

            // Launch kiosk — Fire gets Sentinel face URL
            final Intent launch = new Intent(context, KioskActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (isFire()) {
                launch.setData(android.net.Uri.parse(FACE_URL));
            }
            context.startActivity(launch);

            // On Fire, re-launch every 5s for 30s to fight Amazon's launcher
            if (isFire()) {
                final Context ctx = context.getApplicationContext();
                new Thread(new Runnable() {
                    public void run() {
                        for (int i = 0; i < 6; i++) {
                            try { Thread.sleep(5000); } catch (Exception e) {}
                            try {
                                Intent relaunch = new Intent(ctx, KioskActivity.class);
                                relaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                relaunch.setData(android.net.Uri.parse(FACE_URL));
                                ctx.startActivity(relaunch);
                            } catch (Exception e) { /* best effort */ }
                        }
                    }
                }).start();
            }

            // Start the floating home button overlay
            try {
                context.startForegroundService(new Intent(context, FloatingHomeService.class));
            } catch (Exception e) { /* best effort */ }

            // Watchdog alarm
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