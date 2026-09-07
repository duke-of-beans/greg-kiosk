package com.greg.kiosk;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.List;

public class FloatingHomeService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private static final String CHANNEL_ID = "greg_home";

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY: if this service is killed (OOM under memory
        // pressure — 2GB devices with a heavy foreground app can trigger
        // this even for a foreground service), Android recreates it with
        // a null intent once resources free up. This alone doesn't cover
        // full-process kills though — see WatchdogReceiver for that case.
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationChannel chan = new NotificationChannel(
            CHANNEL_ID, "Greg Home Button",
            NotificationManager.IMPORTANCE_MIN);
        chan.setShowBadge(false);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(chan);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Greg Wall")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build();
        startForeground(1, notification);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        floatingView = new View(this) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                paint.setColor(0xCC222222);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(getWidth()/2f, getHeight()/2f, getWidth()/2f, paint);
                paint.setColor(0xAAFFFFFF);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3f);
                canvas.drawCircle(getWidth()/2f, getHeight()/2f, getWidth()/2f - 2f, paint);
                paint.setColor(0xCCFFFFFF);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(getWidth()/2f, getHeight()/2f, 6f, paint);
            }
        };

        // TAP = back. The `input` binary needs the shell uid, so an app can't inject keys itself —
        // ask Sentinel (which holds an ADB session to this device) and keep the local attempt as
        // a best-effort fallback for devices Sentinel can't reach.
        floatingView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askSentinel("back");
                try {
                    Runtime.getRuntime().exec(new String[]{"input", "keyevent", "4"});
                } catch (Exception e) {}
            }
        });

        // LONG PRESS = close the foreground app + go home (WG-40).
        // `am force-stop` needs the shell uid and getRunningAppProcesses() only returns our own
        // process since Android 7, so the previous in-app attempt silently did nothing and apps
        // (KTLA, Spotify) stayed resident. Sentinel does the force-stop over ADB.
        floatingView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                askSentinel("home");
                Intent home = new Intent(Intent.ACTION_MAIN);
                home.addCategory(Intent.CATEGORY_HOME);
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(home);
                return true;
            }
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            72, 72,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.LEFT;
        params.x = 32;
        params.y = 32;

        windowManager.addView(floatingView, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
    }

    /** Fire-and-forget GET to Sentinel's surface server; the cert is a trust anchor via the app's
     *  Network Security Config (res/raw/sentinel.crt), so a plain HttpsURLConnection works. */
    private void askSentinel(final String action) {
        new Thread(new Runnable() {
            public void run() {
                java.net.HttpURLConnection c = null;
                try {
                    java.net.URL u = new java.net.URL("https://192.168.2.11:9443/v1/skylight/nav?action=" + action);
                    c = (java.net.HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(1500);
                    c.setReadTimeout(8000);
                    c.getResponseCode();
                } catch (Exception e) {
                    /* Sentinel unreachable — the local fallback already ran */
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }).start();
    }
}
