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

public class KioskActivity extends Activity {
    private WebView webView;

    public class GregBridge {
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
            // Volume and other key events
            try {
                Runtime.getRuntime().exec(new String[]{"input", "keyevent", String.valueOf(keyCode)});
            } catch (Exception e) { /* best effort */ }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Force landscape globally
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
            Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, 1);
        } catch (Exception e) { /* needs WRITE_SETTINGS permission */ }

        // Fullscreen — hide status bar, BUT KEEP NAVIGATION BAR for gesture nav
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        // NOTE: deliberately NOT setting HIDE_NAVIGATION or IMMERSIVE_STICKY
        // so the gesture navigation bar (swipe up = home) remains visible and functional

        // Start floating home button overlay as foreground service
        if (Settings.canDrawOverlays(this)) {
            Intent svc = new Intent(this, FloatingHomeService.class);
            startForegroundService(svc);
        }

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.addJavascriptInterface(new GregBridge(), "Greg");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                view.postDelayed(new Runnable() {
                    public void run() { view.reload(); }
                }, 2000);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

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

        if (url != null && !url.startsWith("file")) {
            webView.loadUrl(url);
        } else {
            try {
                java.io.File f = new java.io.File("/sdcard/dashboard.html");
                byte[] bytes = new byte[(int) f.length()];
                java.io.FileInputStream fis = new java.io.FileInputStream(f);
                fis.read(bytes);
                fis.close();
                String html = new String(bytes, "UTF-8");
                webView.loadDataWithBaseURL("http://localhost/", html, "text/html", "UTF-8", null);
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
            // Keep status bar hidden but nav bar visible
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        }
    }
}
