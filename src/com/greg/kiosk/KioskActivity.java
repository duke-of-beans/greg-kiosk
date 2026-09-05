package com.greg.kiosk;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.content.Intent;
import android.net.Uri;

public class KioskActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen — hide everything
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // Retry after 2 seconds if httpd wasn't ready
                view.postDelayed(new Runnable() {
                    public void run() { view.reload(); }
                }, 2000);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        // Start httpd as fallback for http:// URLs
        try {
            Runtime.getRuntime().exec(new String[]{
                "/system/bin/busybox", "httpd", "-p", "127.0.0.1:8080", "-h", "/sdcard"
            });
        } catch (Exception e) { /* best effort */ }

        // Load dashboard: read file directly, inject into WebView
        String url = null;
        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            url = intent.getData().toString();
        }

        if (url != null && !url.startsWith("file")) {
            webView.loadUrl(url);
        } else {
            // Read HTML from sdcard and inject directly — bypasses file:// restrictions
            try {
                java.io.File f = new java.io.File("/sdcard/dashboard.html");
                byte[] bytes = new byte[(int) f.length()];
                java.io.FileInputStream fis = new java.io.FileInputStream(f);
                fis.read(bytes);
                fis.close();
                String html = new String(bytes, "UTF-8");
                webView.loadDataWithBaseURL("http://localhost/", html, "text/html", "UTF-8", null);
            } catch (Exception e) {
                // Fallback: try httpd after delay
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
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
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
