package com.greg.kiosk;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

/**
 * DeviceProfile — one place that answers "what kind of box am I running on?"
 *
 * Why this exists (2026-09-09): greg-kiosk ships one APK to three very different
 * devices — a 2GB Fire HD 8, a 2GB Skylight calendar, and 6-8GB OnePlus 6T phones
 * (Lilly's and Dwight's). Until now KioskActivity applied the SAME aggressive
 * low-memory tuning to all of them, unconditionally, on every single launch:
 *
 *   settings put global always_finish_activities 1   <- "Don't keep activities"
 *   settings put global background_process_limit 2
 *   + 47 `am force-stop com.amazon.*` execs
 *
 * On a 2GB Fire that is a deliberate and necessary memory play. On a OnePlus 6T
 * it is actively destructive: always_finish_activities=1 destroys the kiosk
 * Activity the instant it loses focus, so every tap into YouTube Kids and back
 * tore down and rebuilt the whole WebView (replaying a stale launch intent and
 * re-running the 47-package debloat against packages that do not exist on the
 * phone). That churn is the single largest source of the "it keeps falling over"
 * behaviour on the kid phones.
 *
 * Detection is by capability, not by brand, because that is what actually
 * governs the decision — the question is "is RAM scarce here", not "who made it".
 */
public final class DeviceProfile {

    private DeviceProfile() { }

    /** Anything under 3GB total RAM gets the aggressive memory tuning. */
    private static final long LOW_MEMORY_THRESHOLD_BYTES = 3L * 1024L * 1024L * 1024L;

    /**
     * Amazon Fire hardware. Kept a superset of BootReceiver's original check so
     * the Wall Greg boot path behaves exactly as it did before this refactor.
     */
    public static boolean isFire() {
        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        String board = Build.BOARD != null ? Build.BOARD.toUpperCase() : "";
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toUpperCase() : "";
        return manufacturer.contains("AMAZON")
            || model.contains("KFONWI") || model.contains("KFMUWI")
            || board.contains("ONYX") || model.contains("FIRE");
    }

    /**
     * True on the 2GB wall panels (Fire HD 8, Skylight), false on the phones.
     * Falls back to false when ActivityManager is unavailable — the safe
     * direction, since the tuning is a last-resort hack and skipping it merely
     * means the device behaves like stock Android.
     */
    public static boolean isLowMemory(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            return info.totalMem > 0 && info.totalMem < LOW_MEMORY_THRESHOLD_BYTES;
        } catch (Exception e) {
            return false;
        }
    }

    /** Human-readable one-liner for logcat, so a misdetection is obvious. */
    public static String describe(Context ctx) {
        return "model=" + Build.MODEL
            + " manufacturer=" + Build.MANUFACTURER
            + " fire=" + isFire()
            + " lowMemory=" + isLowMemory(ctx);
    }
}
