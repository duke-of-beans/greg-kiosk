package com.greg.kiosk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * ShadeGuardService — blocks the notification shade / quick-settings pull-down
 * on kid devices.
 *
 * Why this replaces the old approach (found 2026-09-09 on Lilly's OnePlus 6T):
 * KioskActivity used to try
 *
 *     cmd statusbar send-disable-flag statusbar-expansion
 *
 * That subcommand DOES NOT EXIST on OxygenOS 11. `cmd statusbar help` on the 6T
 * lists only: help, expand-notifications, expand-settings, collapse, add-tile,
 * remove-tile, click-tile, check-support, get-status-icons, disable-for-setup.
 * The exec threw, was swallowed by the surrounding best-effort catch, and the
 * shade was never blocked at all — it just looked like it was because nobody
 * checked. Even where send-disable-flag does exist it is bound to the caller's
 * token and resets the moment SystemUI restarts, which is exactly what happens
 * when someone triggers "Dump SysUI heap" from Developer Options.
 *
 * A TYPE_APPLICATION_OVERLAY window that simply swallows touches in the top
 * strip has neither problem: it is owned by this app, so it survives SystemUI
 * restarts entirely, and WatchdogReceiver revives it if the process is reaped.
 * The shade pull-down gesture must begin at the top edge; if that gesture never
 * reaches SystemUI, the shade never opens — including from inside YouTube Kids
 * and every other app, which is the point on a kid device.
 *
 * Requires SYSTEM_ALERT_WINDOW. It is declared in the manifest but on this
 * device it also has to be granted explicitly, because it is an appop rather
 * than a normal permission:
 *
 *     adb shell appops set com.greg.kiosk SYSTEM_ALERT_WINDOW allow
 *
 * Escape hatch: create /sdcard/kiosk-allow-shade to disable this guard (for
 * debugging on the wall panels). KioskActivity checks for that file.
 */
public class ShadeGuardService extends Service {

    private static final String CHANNEL_ID = "greg_shade_guard";
    private static final int NOTIFICATION_ID = 2;

    /** Minimum guard height in dp, used when status_bar_height is unavailable. */
    private static final int FALLBACK_HEIGHT_DP = 28;

    private WindowManager windowManager;
    private View guard;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY so Android rebuilds this after a low-memory kill. Full
        // process kills are covered by WatchdogReceiver, which runs in its own
        // process for exactly that reason.
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationChannel chan = new NotificationChannel(
            CHANNEL_ID, "Greg Shade Guard", NotificationManager.IMPORTANCE_MIN);
        chan.setShowBadge(false);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(chan);

        startForeground(NOTIFICATION_ID, new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Greg Kiosk")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .build());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            stopSelf();
            return;
        }

        guard = new View(this);
        guard.setBackgroundColor(0x00000000); // fully transparent — invisible to the kid
        guard.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Consume everything. Returning true here is the whole mechanism:
                // the gesture is absorbed by this window and never reaches
                // SystemUI's shade handler.
                return true;
            }
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            resolveGuardHeightPx(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_FOCUSABLE keeps key events flowing to the app underneath while
            // still delivering touch to this window. Deliberately NOT using
            // FLAG_NOT_TOUCHABLE, which would pass the swipe straight through and
            // defeat the entire purpose.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.FILL_HORIZONTAL;
        params.x = 0;
        params.y = 0;

        try {
            windowManager.addView(guard, params);
            android.util.Log.i("greg-kiosk", "ShadeGuard active, height=" + params.height + "px");
        } catch (Exception e) {
            // Almost always a missing SYSTEM_ALERT_WINDOW appop. Log loudly —
            // silent failure here is what hid the original bug for weeks.
            android.util.Log.e("greg-kiosk",
                "ShadeGuard FAILED to attach — grant SYSTEM_ALERT_WINDOW: " + e);
            guard = null;
            stopSelf();
        }
    }

    /**
     * Cover the status bar strip. Uses the platform's own status_bar_height so
     * notched devices (the 6T has one) are handled correctly, with a dp floor as
     * a backstop.
     */
    private int resolveGuardHeightPx() {
        int height = 0;
        try {
            int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resId > 0) height = getResources().getDimensionPixelSize(resId);
        } catch (Exception e) { /* fall through to the dp floor */ }

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int floor = (int) (FALLBACK_HEIGHT_DP * dm.density);
        return Math.max(height, floor);
    }

    /** True unless the operator dropped the escape-hatch file on the device. */
    public static boolean isEnabled(Context ctx) {
        return !new java.io.File("/sdcard/kiosk-allow-shade").exists();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (guard != null && windowManager != null) {
            try { windowManager.removeView(guard); } catch (Exception e) { /* already gone */ }
        }
    }
}
