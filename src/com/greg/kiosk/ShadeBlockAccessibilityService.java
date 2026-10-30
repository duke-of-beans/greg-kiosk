package com.greg.kiosk;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.PixelFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class ShadeBlockAccessibilityService extends AccessibilityService {

    private static final int GUARD_HEIGHT_DP = 50;
    private WindowManager windowManager;
    private View guard;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null
                && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null && "com.android.systemui".equals(pkg.toString())) {
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
        }
    }

    @Override
    public void onInterrupt() { }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 0;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) return;

        guard = new View(this);
        guard.setBackgroundColor(0x00000000);
        guard.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int heightPx = (int) (GUARD_HEIGHT_DP * dm.density);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.FILL_HORIZONTAL;
        params.x = 0;
        params.y = 0;

        try {
            windowManager.addView(guard, params);
            android.util.Log.i("greg-kiosk",
                "ShadeBlock ACCESSIBILITY OVERLAY active, height=" + heightPx
                + "px");
        } catch (Exception e) {
            android.util.Log.e("greg-kiosk",
                "ShadeBlock overlay FAILED: " + e);
            guard = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (guard != null && windowManager != null) {
            try { windowManager.removeView(guard); } catch (Exception e) { }
        }
    }
}
