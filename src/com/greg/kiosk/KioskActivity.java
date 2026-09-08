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
            // Fallback: find any handler that isn't greg-kiosk
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

        // ── Persistent debloat: kill Amazon services on every launch ──────
        // These services respawn after reboot and eat 300+ MB of RAM on a
        // 2GB device. force-stop them here so Greg gets the memory instead.
        // Safe: uses am force-stop (no root required), services simply stop.
        new Thread(new Runnable() {
            public void run() {
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
                // Also set background process limit and disable animations
                try {
                    Runtime.getRuntime().exec(new String[]{"settings", "put", "global", "background_process_limit", "2"}).waitFor();
                    Runtime.getRuntime().exec(new String[]{"settings", "put", "global", "window_animation_scale", "0"}).waitFor();
                    Runtime.getRuntime().exec(new String[]{"settings", "put", "global", "transition_animation_scale", "0"}).waitFor();
                    Runtime.getRuntime().exec(new String[]{"settings", "put", "global", "animator_duration_scale", "0"}).waitFor();
                    Runtime.getRuntime().exec(new String[]{"settings", "put", "global", "always_finish_activities", "1"}).waitFor();
                    Runtime.getRuntime().exec(new String[]{"settings", "put", "global", "policy_control", "immersive.full=*"}).waitFor();
                    // Block notification shade pull-down (kid device lockdown)
                    Runtime.getRuntime().exec(new String[]{"cmd", "statusbar", "send-disable-flag", "statusbar-expansion"}).waitFor();
                } catch (Exception e) { /* best effort */ }
            }
        }).start();

        // Start floating home button overlay as foreground service
        if (Settings.canDrawOverlays(this)) {
            Intent svc = new Intent(this, FloatingHomeService.class);
            startForegroundService(svc);
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
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                view.postDelayed(new Runnable() {
                    public void run() { view.reload(); }
                }, 2000);
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

        try {
            Runtime.getRuntime().exec(new String[]{
                "/system/bin/busybox", "httpd", "-p", "127.0.0.1:8080", "-h", "/sdcard"
            });
        } catch (Exception e) { /* best effort */ }

        String url = null;
        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            url = intent.getData().toString();
        }

        // Force cache bust with timestamp
        String cacheBust = "?t=" + System.currentTimeMillis();
        if (url != null && !url.startsWith("file")) {
            // Strip any existing query params and add cache bust
            String cleanUrl = url.contains("?") ? url.substring(0, url.indexOf("?")) : url;
            webView.loadUrl(cleanUrl + cacheBust);
        } else {
            try {
                java.io.File f = new java.io.File("/sdcard/dashboard.html");
                byte[] bytes = new byte[(int) f.length()];
                java.io.FileInputStream fis = new java.io.FileInputStream(f);
                fis.read(bytes);
                fis.close();
                String html = new String(bytes, "UTF-8");
                webView.loadDataWithBaseURL("file:///sdcard/", html, "text/html", "UTF-8", null);
            } catch (Exception e) {
                webView.postDelayed(new Runnable() {
                    public void run() { webView.loadUrl("http://127.0.0.1:8080/dashboard.html"); }
                }, 3000);
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
