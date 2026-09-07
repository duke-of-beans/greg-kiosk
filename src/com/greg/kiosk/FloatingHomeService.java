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

        // TAP = back
        floatingView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Runtime.getRuntime().exec(new String[]{"input", "keyevent", "4"});
                } catch (Exception e) {}
            }
        });

        // LONG PRESS = kill foreground app + go home
        floatingView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // Kill whatever app is in foreground (except greg-kiosk)
                try {
                    ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                    List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
                    if (procs != null) {
                        for (ActivityManager.RunningAppProcessInfo proc : procs) {
                            if (proc.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                                && !proc.processName.equals("com.greg.kiosk")) {
                                // Force stop via shell (works on rooted/system apps)
                                Runtime.getRuntime().exec(new String[]{
                                    "am", "force-stop", proc.processName
                                });
                                break;
                            }
                        }
                    }
                } catch (Exception e) { /* best effort */ }

                // Go home
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
}
