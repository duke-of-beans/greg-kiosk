package com.greg.kiosk;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.provider.Settings;
import android.print.PrintManager;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.webkit.PermissionRequest;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.Manifest;
import android.content.pm.PackageManager;

public class KioskActivity extends Activity {
    private WebView webView;
    private View dimOverlay;
    private Handler dimHandler = new Handler(Looper.getMainLooper());
    private static final long DIM_DELAY_MS = 330000; // 5.5 min — 30s after JS sleep (5min)
    private static final long FADE_DURATION_MS = 3000; // 3 second fade

    private static final String LOCAL_DASHBOARD = "/sdcard/dashboard.html";
    private static final String URL_OVERRIDE_FILE = "/sdcard/kiosk-url.txt";
    private static final String BUSYBOX = "/system/bin/busybox";
    private static final String LEGACY_HTTPD_URL = "http://127.0.0.1:8080/dashboard.html";

    // Bounded retry. The old code retried onReceivedError -> loadDashboard()
    // every 2s forever against the SAME failing URL, which is why the phone sat
    // on "webpage not available" indefinitely instead of recovering, and why it
    // burned CPU at 0.5Hz while doing it. See onReceivedError below.
    private static final int MAX_LOAD_RETRIES = 4;
    private int loadFailures = 0;
    private boolean forceLocalOnly = false;

    // Lets FloatingHomeService (a separate Service, no WebView of its own) reach into the
    // dashboard's JS card state. The dashboard is a single-page app with no navigation
    // history, so webView.canGoBack() is always false — the system BACK key and a plain
    // "go home" intent never touched showCard(activeCard) at all. A card someone opened
    // (the weather widget, the grocery list) just sat there forever; tapping "back" looked
    // like it did nothing, and long-pressing "home" left it open once the kiosk resurfaced.
    public static KioskActivity instance;

    public void dismissActiveCard() {
        if (webView == null) return;
        runOnUiThread(new Runnable() {
            public void run() {
                webView.evaluateJavascript(
                    "(function(){ if (typeof showCard === 'function') showCard(null); })();", null);
            }
        });
    }

    private Runnable dimRunnable = new Runnable() {
        public void run() {
            if (dimOverlay != null) {
                // Fade to full black — Greg's face departs gracefully
                dimOverlay.animate()
                    .alpha(1.0f) // Full black — Greg is asleep
                    .setDuration(FADE_DURATION_MS)
                    .start();
            }
        }
    };

    private void resetDimTimer() {
        if (dimOverlay != null) {
            // Greg wakes — graceful arrival from darkness
            dimOverlay.animate().alpha(0f).setDuration(800).start();
        }
        dimHandler.removeCallbacks(dimRunnable);
        dimHandler.postDelayed(dimRunnable, DIM_DELAY_MS);
    }

    public class GregBridge {
        @JavascriptInterface
        public void resetDim() {
            // Called by JS skylight schedule to keep APK overlay clear during awake hours
            runOnUiThread(new Runnable() { public void run() { resetDimTimer(); } });
        }

        @JavascriptInterface
        public void launchApp(String packageName) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }

        @JavascriptInterface
        public void launchUrl(String url) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Try known browser packages first (not greg-kiosk)
            String[] browsers = {
                "com.android.chrome", "com.chrome.beta",
                "org.mozilla.firefox", "com.brave.browser",
                "acr.browser.barebones", "acr.browser.lightning",
                "com.opera.browser", "com.opera.mini.native"
            };
            for (String pkg : browsers) {
                intent.setPackage(pkg);
                try {
                    startActivity(intent);
                    return;
                } catch (Exception e) { /* not installed */ }
            }
            // Fallback: find any handler that isn't greg-kiosk.
            // Critical: greg-kiosk's own manifest registers an ACTION_VIEW filter for
            // http/https/file, so without this exclusion an external link would relaunch
            // the kiosk with that URL as its intent data — which is precisely how
            // http://127.0.0.1:8080/dashboard.html got burned into the task history on
            // Lilly's phone and made "webpage not available" survive every restart.
            intent.setPackage(null);
            java.util.List<android.content.pm.ResolveInfo> resolvers =
                getPackageManager().queryIntentActivities(intent, 0);
            for (android.content.pm.ResolveInfo ri : resolvers) {
                if (!ri.activityInfo.packageName.equals("com.greg.kiosk")) {
                    intent.setPackage(ri.activityInfo.packageName);
                    try { startActivity(intent); return; }
                    catch (Exception e) { /* skip */ }
                }
            }
        }

        @JavascriptInterface
        public void goHome() {
            Intent intent = new Intent(KioskActivity.this, KioskActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }

        @JavascriptInterface
        public void sendKey(int keyCode) {
            try {
                Runtime.getRuntime().exec(new String[]{"input", "keyevent", String.valueOf(keyCode)});
            } catch (Exception e) { /* best effort */ }
        }

        @JavascriptInterface
        public void printHtml(final String html) {
            runOnUiThread(new Runnable() {
                public void run() {
                    WebView printView = new WebView(KioskActivity.this);
                    printView.getSettings().setJavaScriptEnabled(false);
                    printView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                            PrintDocumentAdapter adapter = view.createPrintDocumentAdapter("GroceryList");
                            PrintAttributes.Builder b = new PrintAttributes.Builder();
                            b.setMediaSize(PrintAttributes.MediaSize.NA_LETTER);
                            pm.print("Grocery List", adapter, b.build());
                        }
                    });
                    printView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
                }
            });
        }

        @JavascriptInterface
        public void printGrocery() {
            runOnUiThread(new Runnable() {
                public void run() {
                    webView.evaluateJavascript("printGroceryList()", null);
                }
            });
        }

        @JavascriptInterface
        public String getAppIcon(String packageName) {
            try {
                Drawable d = getPackageManager().getApplicationIcon(packageName);
                Bitmap bmp;
                if (d instanceof BitmapDrawable) {
                    bmp = ((BitmapDrawable) d).getBitmap();
                } else {
                    int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 96;
                    int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 96;
                    bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bmp);
                    d.setBounds(0, 0, w, h);
                    d.draw(canvas);
                }
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, 128, 128, true);
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.PNG, 90, baos);
                return android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public String getAppLabel(String packageName) {
            try {
                return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0)).toString();
            } catch (Exception e) {
                return packageName;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        android.util.Log.i("greg-kiosk", "onCreate " + DeviceProfile.describe(this));

        // Orientation is now controlled by each HTML page (viewport meta + CSS),
        // not the kiosk APK. Skylight dashboard forces landscape via viewport;
        // phone pages use responsive portrait. No global rotation override.

        // Fullscreen — hide status bar, BUT KEEP NAVIGATION BAR for gesture nav
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        // Full immersive — no nav bar. Swipe from bottom edge to temporarily reveal.

        // Camera/mic permissions are granted at install time via
        // `adb install -g` (or `pm grant` post-install), never at runtime.
        // A runtime requestPermissions() call here would show a system
        // dialog that blocks unattended boot forever — nobody is present
        // to tap "Allow" after a headless reboot. checkSelfPermission()
        // can also transiently report DENIED in the first seconds after
        // BOOT_COMPLETED even when the grant is persisted, so we only log,
        // never prompt.
        for (String p : new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w("greg-kiosk", "Permission not granted: " + p + " — reinstall with 'adb install -g' or 'pm grant'");
            }
        }

        applyDeviceTuning();

        // Floating home button overlay (back / long-press-home).
        if (Settings.canDrawOverlays(this)) {
            startForegroundService(new Intent(this, FloatingHomeService.class));
        } else {
            android.util.Log.w("greg-kiosk",
                "SYSTEM_ALERT_WINDOW not granted — home button and shade guard are BOTH disabled. "
                + "Fix: adb shell appops set com.greg.kiosk SYSTEM_ALERT_WINDOW allow");
        }

        // Notification shade blocker. See ShadeGuardService for why the old
        // `cmd statusbar send-disable-flag` approach never worked on this hardware.
        if (Settings.canDrawOverlays(this) && ShadeGuardService.isEnabled(this)) {
            startForegroundService(new Intent(this, ShadeGuardService.class));
        }

        webView = new WebView(this);
        WebView.setWebContentsDebuggingEnabled(true);
        webView.clearCache(true);
        webView.clearHistory();

        // Dim overlay — sits on top of WebView, fades to near-black on inactivity
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.addView(webView, new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        dimOverlay = new View(this);
        dimOverlay.setBackgroundColor(Color.BLACK);
        dimOverlay.setAlpha(0f);
        dimOverlay.setClickable(false); // Touch passes through to WebView
        frame.addView(dimOverlay, new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(frame);
        resetDimTimer();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowUniversalAccessFromFileURLs(true);  // file:// → https:// fetch for Supabase

        webView.addJavascriptInterface(new GregBridge(), "Greg");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // A successful load clears the failure budget so a later transient
                // error still gets its full retry allowance.
                loadFailures = 0;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                loadFailures++;
                android.util.Log.w("greg-kiosk",
                    "load error " + errorCode + " (" + description + ") on " + failingUrl
                    + " attempt=" + loadFailures);

                if (loadFailures > MAX_LOAD_RETRIES) {
                    // Stop hammering a URL that is not coming back. Drop to the
                    // local file for the remainder of this process lifetime.
                    // This is the escape the old code lacked entirely: it retried
                    // the same dead URL forever.
                    android.util.Log.e("greg-kiosk",
                        "giving up on remote URL after " + loadFailures
                        + " failures — falling back to " + LOCAL_DASHBOARD);
                    forceLocalOnly = true;
                    view.post(new Runnable() {
                        public void run() { loadDashboard(); }
                    });
                    return;
                }

                // Re-run the real load path (loadDashboard()), not view.reload() --
                // reload() cannot properly re-fetch content that was loaded via
                // loadDataWithBaseURL(), so it was leaving the WebView stuck on
                // "webpage not available" instead of recovering.
                // Exponential backoff: 2s, 4s, 8s, 16s.
                long delay = 2000L * (1L << (loadFailures - 1));
                view.postDelayed(new Runnable() {
                    public void run() { loadDashboard(); }
                }, delay);
            }

            @Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                // Accept self-signed cert for Sentinel LAN IP (192.168.2.11).
                // We control both the cert and the server. This enables HTTPS
                // for getUserMedia (camera requires secure context) while keeping
                // face detection on the LAN (11ms vs 200ms proxy chain).
                String url = error.getUrl();
                if (url != null && url.contains("192.168.2.")) {
                    handler.proceed();
                } else {
                    handler.cancel();
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                // Auto-grant camera and microphone permissions for getUserMedia
                runOnUiThread(new Runnable() {
                    public void run() {
                        request.grant(request.getResources());
                    }
                });
            }
        });

        startLegacyHttpdIfAvailable();

        loadDashboard();
    }

    /**
     * A fresh `am start -d <url>` against an ALREADY RUNNING kiosk used to do
     * nothing: getIntent() still returned the intent the task was created with,
     * so loadDashboard() kept reading the old (often poisoned) URL. setIntent()
     * makes the new intent authoritative, which is what makes remote reloads of
     * the Fire's face URL actually take effect without a force-stop first.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;

        if (intent.getData() == null) {
            // A bare HOME/MAIN intent: the home button, or BootReceiver's non-Fire
            // launch. Deliberately do NOT setIntent() here -- clobbering a working
            // remote URL (the Fire's Sentinel face) with a data-less HOME intent
            // would silently downgrade the wall panel to the local dashboard. And
            // do NOT reload: pressing home while already on the kiosk should be
            // free, not a full WebView teardown.
            dismissActiveCard();
            return;
        }

        setIntent(intent);
        loadFailures = 0;
        forceLocalOnly = false;
        loadDashboard();
    }

    /**
     * Device-scoped system tuning.
     *
     * The aggressive settings below are a memory play for the 2GB wall panels.
     * Applying them to a 6-8GB phone was actively harmful — see DeviceProfile.
     * On non-low-memory devices we now actively HEAL the settings back to stock,
     * because they are global and sticky: skipping the write is not enough once
     * a previous build has already set them.
     */
    private void applyDeviceTuning() {
        final boolean lowMemory = DeviceProfile.isLowMemory(this);
        final boolean fire = DeviceProfile.isFire();

        new Thread(new Runnable() {
            public void run() {
                if (fire) {
                    // Persistent debloat: these Amazon services respawn after reboot
                    // and eat 300+ MB on a 2GB device. force-stop needs no root.
                    // Gated to Fire hardware: on a OnePlus none of these packages
                    // exist, so this was 47 wasted process spawns on every launch.
                    String[] bloat = {
                        "com.amazon.kindle.unifiedSearch",
                        "com.amazon.client.metrics",
                        "com.amazon.device.messaging",
                        "com.amazon.tcomm",
                        "com.amazon.imp",
                        "com.amazon.device.software.ota",
                        "com.amazon.whisperlink.core.android",
                        "com.amazon.sync.service",
                        "com.amazon.sync.provider.ipc",
                        "com.amazon.diode",
                        "com.here.odnp.service",
                        "com.amazon.identity.auth.device.authorization",
                        "com.amazon.device.backup",
                        "com.amazon.securitysyncclient",
                        "com.amazon.dp.contacts",
                        "com.amazon.dp.fbcontacts",
                        "amazon.speech.sim",
                        "amazon.speech.davs.davcservice",
                        "com.amazon.fireos.cirruscloud",
                        "com.amazon.kindle",
                        "com.amazon.photos",
                        "com.amazon.avod",
                        "com.amazon.venezia",
                        "com.amazon.windowshop",
                        "com.amazon.cloud9",
                        "com.amazon.mp3",
                        "com.amazon.dee.app",
                        "com.amazon.device.metrics",
                        "com.amazon.device.logmanager",
                        "com.amazon.device.crashmanager",
                        "com.amazon.wirelessmetrics.service",
                        "jp.co.omronsoft.iwnnime.mlaz",
                        "com.amazon.firespotlight",
                        "com.amazon.weather",
                        "com.amazon.wallpaper",
                        "com.amazon.kindle.starsight",
                        "com.amazon.ods.kindleconnect",
                        "com.amazon.csapp",
                        "com.amazon.cardinal",
                        "com.amazon.hedwig",
                        "com.amazon.ags.app",
                        "com.amazon.device.sale.service",
                        "com.amazon.parentalcontrols",
                        "com.amazon.recess",
                        "com.amazon.tahoe",
                        "com.amazon.webapp",
                        "com.amazon.media.session.monitor",
                    };
                    for (String pkg : bloat) {
                        try {
                            Runtime.getRuntime().exec(new String[]{"am", "force-stop", pkg}).waitFor();
                        } catch (Exception e) { /* best effort */ }
                    }
                }

                try {
                    // Immersive mode is the kiosk contract on every device.
                    put("global", "policy_control", "immersive.full=*");
                    put("global", "window_animation_scale", "0");
                    put("global", "transition_animation_scale", "0");
                    put("global", "animator_duration_scale", "0");

                    if (lowMemory) {
                        put("global", "background_process_limit", "2");
                        put("global", "always_finish_activities", "1");
                    } else {
                        // Heal: undo what earlier unconditional builds set on the
                        // phones. always_finish_activities=1 destroyed the kiosk
                        // Activity every time it lost focus, so every trip into
                        // YouTube Kids and back rebuilt the whole WebView from a
                        // stale intent. -1 is the platform's "standard limit".
                        put("global", "background_process_limit", "-1");
                        put("global", "always_finish_activities", "0");
                    }
                } catch (Exception e) { /* best effort */ }
            }

            private void put(String namespace, String key, String value) {
                try {
                    Runtime.getRuntime().exec(
                        new String[]{"settings", "put", namespace, key, value}).waitFor();
                } catch (Exception e) { /* best effort */ }
            }
        }).start();
    }

    /**
     * The legacy busybox httpd only ever existed to serve /sdcard over
     * 127.0.0.1:8080 on the wall panels. There is no busybox on OxygenOS, so on
     * the phones this exec always threw and was swallowed — while
     * http://127.0.0.1:8080/dashboard.html stayed wired in as a fallback URL,
     * guaranteeing an unrecoverable "webpage not available" any time the kiosk
     * reached it. Check for the binary instead of guessing.
     */
    private void startLegacyHttpdIfAvailable() {
        if (!new java.io.File(BUSYBOX).exists()) return;
        try {
            Runtime.getRuntime().exec(new String[]{
                BUSYBOX, "httpd", "-p", "127.0.0.1:8080", "-h", "/sdcard"
            });
        } catch (Exception e) { /* best effort */ }
    }

    /**
     * Decides what the kiosk should display, in priority order:
     *
     *   1. /sdcard/kiosk-url.txt   — explicit per-device override, survives everything
     *   2. the launch intent's URL — the Fire's Sentinel face URL
     *   3. null                    — caller loads /sdcard/dashboard.html
     *
     * Step 2 rejects loopback URLs whenever the local dashboard file exists.
     * That single guard is what permanently kills the failure this method was
     * rewritten for: a stale http://127.0.0.1:8080/dashboard.html sitting in the
     * task's intent history, replayed on every Activity recreation, pointing at
     * an httpd that cannot run on this hardware. The loopback URL was never
     * anything but a second way to read the very file we already have on disk,
     * so preferring the file loses nothing.
     */
    private String resolveRemoteUrl() {
        if (forceLocalOnly) return null;

        try {
            java.io.File override = new java.io.File(URL_OVERRIDE_FILE);
            if (override.exists()) {
                byte[] bytes = new byte[(int) override.length()];
                java.io.FileInputStream fis = new java.io.FileInputStream(override);
                fis.read(bytes);
                fis.close();
                String configured = new String(bytes, "UTF-8").trim();
                if (configured.length() > 0) return configured;
            }
        } catch (Exception e) { /* fall through to the intent */ }

        Intent intent = getIntent();
        if (intent == null || intent.getData() == null) return null;

        String url = intent.getData().toString();
        if (url.startsWith("file")) return null;

        boolean loopback = url.contains("127.0.0.1") || url.contains("localhost");
        if (loopback && new java.io.File(LOCAL_DASHBOARD).exists()) {
            android.util.Log.w("greg-kiosk",
                "ignoring poisoned loopback intent URL (" + url + ") — using " + LOCAL_DASHBOARD);
            return null;
        }
        return url;
    }

    /**
     * Loads the dashboard content. Extracted from onCreate() so onReceivedError()
     * can call the SAME logic to recover -- WebView.reload() does not work
     * correctly on content loaded via loadDataWithBaseURL() (there is no real
     * backing URL for it to re-fetch), so a plain reload() left the WebView stuck
     * showing "webpage not available" indefinitely after any transient resource
     * error. Found + fixed 2026-09-08 after Dwight's phone was hanging on that
     * error a few minutes into every boot.
     */
    private void loadDashboard() {
        String url = resolveRemoteUrl();

        if (url != null) {
            // Force cache bust with timestamp; strip any existing query params.
            String cleanUrl = url.contains("?") ? url.substring(0, url.indexOf("?")) : url;
            webView.loadUrl(cleanUrl + "?t=" + System.currentTimeMillis());
            return;
        }

        try {
            java.io.File f = new java.io.File(LOCAL_DASHBOARD);
            byte[] bytes = new byte[(int) f.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            fis.read(bytes);
            fis.close();
            String html = new String(bytes, "UTF-8");
            webView.loadDataWithBaseURL("file:///sdcard/", html, "text/html", "UTF-8", null);
        } catch (Exception e) {
            // Last resort. Only meaningful where busybox httpd actually runs;
            // elsewhere show a self-explanatory page instead of Chromium's
            // "webpage not available", which told nobody anything useful.
            if (new java.io.File(BUSYBOX).exists()) {
                webView.loadUrl(LEGACY_HTTPD_URL);
            } else {
                android.util.Log.e("greg-kiosk", "no dashboard at " + LOCAL_DASHBOARD + ": " + e);
                webView.loadDataWithBaseURL(null,
                    "<html><body style='background:#111;color:#eee;font-family:sans-serif;"
                    + "display:flex;align-items:center;justify-content:center;height:100vh;"
                    + "text-align:center;padding:24px'><div><h2>Greg is missing his dashboard</h2>"
                    + "<p style='opacity:.7'>" + LOCAL_DASHBOARD + " could not be read.</p></div>"
                    + "</body></html>", "text/html", "UTF-8", null);
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onBackPressed() {
        // Single-page dashboard: canGoBack() is always false, so BACK used to do nothing
        // visible at all. Dismiss whatever card is open first; only fall back to real
        // WebView history navigation (never happens in practice here) if nothing was open.
        dismissActiveCard();
        if (webView.canGoBack()) {
            webView.goBack();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        resetDimTimer();
        return super.dispatchTouchEvent(ev);
    }
}
