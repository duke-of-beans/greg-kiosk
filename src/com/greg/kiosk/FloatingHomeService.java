package com.greg.kiosk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * FloatingHomeService — always-visible floating circle button for kid navigation.
 *
 * Tap  = BACK (dismiss current card / go back in foreground app)
 * Hold = HOME (close foreground app, return to kiosk dashboard)
 *
 * 120px, centered at bottom, always on top. Sized for a 2-year-old.
 */
public class FloatingHomeService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private static final String CHANNEL_ID = "greg_home";
    private static final int BUTTON_SIZE = 120;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationChannel chan = new NotificationChannel(
            CHANNEL_ID, "Greg Home Button", NotificationManager.IMPORTANCE_MIN);
        chan.setShowBadge(false);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(chan);

        startForeground(1, new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Greg Wall")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        floatingView = new View(this) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float r = getWidth() / 2f;

                paint.setColor(0xDD222222);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, r, paint);

                paint.setColor(0xCCFFFFFF);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(4f);
                canvas.drawCircle(cx, cy, r - 3f, paint);

                paint.setColor(0xEEFFFFFF);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, 10f, paint);
            }
        };

        // Drag + tap + long-press handler
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private int origX, origY;
            private boolean dragging = false;
            private long downTime = 0;
            private static final int DRAG_THRESHOLD = 15;
            private static final long LONG_PRESS_MS = 600;
            private android.os.Handler lpHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            private Runnable longPressRun = new Runnable() {
                @Override public void run() {
                    if (!dragging) {
                        // LONG PRESS = kiosk home
                        if (KioskActivity.instance != null) KioskActivity.instance.dismissActiveCard();
                        askSentinel("home");
                        Intent home = new Intent(FloatingHomeService.this, KioskActivity.class);
                        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(home);
                        dragging = true; // prevent tap on UP
                    }
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        origX = lp.x;
                        origY = lp.y;
                        dragging = false;
                        downTime = System.currentTimeMillis();
                        lpHandler.postDelayed(longPressRun, LONG_PRESS_MS);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX;
                        float dy = event.getRawY() - startY;
                        if (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD) {
                            dragging = true;
                            lpHandler.removeCallbacks(longPressRun);
                            lp.x = origX + (int) dx;
                            lp.y = origY - (int) dy; // y is inverted for BOTTOM gravity
                            windowManager.updateViewLayout(v, lp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        lpHandler.removeCallbacks(longPressRun);
                        if (!dragging && (System.currentTimeMillis() - downTime < LONG_PRESS_MS)) {
                            // TAP = back
                            if (KioskActivity.instance != null) KioskActivity.instance.dismissActiveCard();
                            askSentinel("back");
                            try {
                                Runtime.getRuntime().exec(new String[]{"input", "keyevent", "4"});
                            } catch (Exception e) {}
                        }
                        return true;
                }
                return false;
            }
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            BUTTON_SIZE, BUTTON_SIZE,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.x = 0;
        params.y = 48;

        windowManager.addView(floatingView, params);
        android.util.Log.i("greg-kiosk", "FloatingHome active, size=" + BUTTON_SIZE + "px, centered bottom");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
    }

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
                } catch (Exception e) { } finally {
                    if (c != null) c.disconnect();
                }
            }
        }).start();
    }
}
