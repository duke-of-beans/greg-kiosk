package com.greg.kiosk;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.List;

/**
 * WatchdogReceiver — runs in a SEPARATE PROCESS (android:process=":watchdog"
 * in the manifest) so it survives even if the main greg-kiosk process
 * (KioskActivity + FloatingHomeService) gets OOM-killed under memory
 * pressure.
 *
 * Root cause this fixes (found 2026-09-07): launching a heavy app
 * (KTLA+, Maps, Pluto, YouTube via SmartTube) on the 2GB Skylight tablet
 * drives free memory low enough that Android's low-memory-killer reaps
 * the ENTIRE greg-kiosk process, including FloatingHomeService. This
 * isn't "the overlay window got hidden" -- the process holding it is
 * gone. A watchdog living in the same process would die too, so this
 * one runs separately and periodically checks + revives the service.
 *
 * Fired by a repeating AlarmManager alarm registered in BootReceiver.
 */
public class WatchdogReceiver extends BroadcastReceiver {
    public static final String ACTION_CHECK = "com.greg.kiosk.WATCHDOG_CHECK";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_CHECK.equals(intent.getAction())) return;

        if (!isServiceRunning(context, FloatingHomeService.class)) {
            // Main process (or just this service) is dead -- restart it.
            // startForegroundService works even if the whole app process
            // was killed; Android will spin up a fresh process for it.
            try {
                Intent svc = new Intent(context, FloatingHomeService.class);
                context.startForegroundService(svc);
            } catch (Exception e) { /* best effort -- retry on next tick */ }
        }
    }

    private boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> services = manager.getRunningServices(Integer.MAX_VALUE);
        if (services == null) return false;
        for (ActivityManager.RunningServiceInfo info : services) {
            if (serviceClass.getName().equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
