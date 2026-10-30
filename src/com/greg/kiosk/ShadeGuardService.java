package com.greg.kiosk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * ShadeGuardService — blocks the notification shade on kid devices.
 *
 * MECHANISM: 150ms loop sending ACTION_CLOSE_SYSTEM_DIALOGS broadcast.
 * On Android 11 (OxygenOS), this broadcast is still permitted for apps
 * targeting API 28. It was blocked starting in Android 12. This dismisses
 * the notification shade, power menu, and any other system dialog the
 * moment it appears — faster than a toddler can interact with anything.
 *
 * The old overlay approach (TYPE_APPLICATION_OVERLAY at the top of the
 * screen) is kept as a secondary barrier but is not relied upon, because
 * OxygenOS system gestures bypass overlay touch consumption.
 *
 * Requires: adb shell appops set com.greg.kiosk SYSTEM_ALERT_WINDOW allow
 * Escape hatch: create /sdcard/kiosk-allow-shade to disable.
 */
public class ShadeGuardService extends Service {

    private static final String CHANNEL_ID = "greg_shade_guard";
    private static final int NOTIFICATION_ID = 2;
    private static final int GUARD_HEIGHT_DP = 100;
    private static final int COLLAPSE_INTERVAL_MS = 50;

    private WindowManager windowManager;
    private View guard;
    private Handler collapseHandler;
    private boolean collapseRunning = false;

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

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

        // Secondary barrier: touch-consuming overlay at the top
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            guard = new View(this);
            guard.setBackgroundColor(0x00000000);
            guard.setOnTouchListener(new View.OnTouchListener() {
                @Override public boolean onTouch(View v, MotionEvent event) { return true; }
            });

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int heightPx = (int) (GUARD_HEIGHT_DP * dm.density);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, heightPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.FILL_HORIZONTAL;
            params.x = 0;
            params.y = 0;

            try {
                windowManager.addView(guard, params);
                android.util.Log.i("greg-kiosk", "ShadeGuard overlay height=" + heightPx + "px");
            } catch (Exception e) {
                android.util.Log.e("greg-kiosk", "ShadeGuard overlay FAILED: " + e);
                guard = null;
            }
        }

        // Primary mechanism: broadcast ACTION_CLOSE_SYSTEM_DIALOGS every 150ms.
        // On Android 11 this is still permitted. It closes the notification shade,
        // power menu, and any other system dialog instantly.
        collapseHandler = new Handler(Looper.getMainLooper());
        collapseRunning = true;
        collapseHandler.post(collapseRunnable);
        android.util.Log.i("greg-kiosk", "ShadeGuard CLOSE_SYSTEM_DIALOGS loop started (" + COLLAPSE_INTERVAL_MS + "ms)");
    }

    private final Runnable collapseRunnable = new Runnable() {
        @Override public void run() {
            if (!collapseRunning) return;
            try {
                sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            } catch (Exception e) {
                // Fallback: try shell command
                try {
                    Runtime.getRuntime().exec(new String[]{
                        "am", "broadcast", "-a", "android.intent.action.CLOSE_SYSTEM_DIALOGS"
                    });
                } catch (Exception e2) { }
            }
            collapseHandler.postDelayed(this, COLLAPSE_INTERVAL_MS);
        }
    };

    public static boolean isEnabled(Context ctx) {
        return !new java.io.File("/sdcard/kiosk-allow-shade").exists();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        collapseRunning = false;
        if (collapseHandler != null) collapseHandler.removeCallbacksAndMessages(null);
        if (guard != null && windowManager != null) {
            try { windowManager.removeView(guard); } catch (Exception e) { }
        }
    }
}
