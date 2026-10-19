package com.repcopharma.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Color;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String APP_URL = "https://repco.pages.dev";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#07823E"));
        getWindow().setNavigationBarColor(Color.parseColor("#F0FDF4"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportZoom(false);
        settings.setAllowFileAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager.getInstance().setAcceptCookie(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (!url.startsWith("https://") && !url.startsWith("http://")) {
                Toast.makeText(this, "এই ফাইলটি অ্যাপ থেকে download করা যাচ্ছে না।", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.setDescription("Repco Pharmaceutical download");
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, mimeType));
                DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                manager.enqueue(request);
                Toast.makeText(this, "Download শুরু হয়েছে।", Toast.LENGTH_SHORT).show();
            } catch (Exception error) {
                Toast.makeText(this, "Download শুরু করা যায়নি।", Toast.LENGTH_LONG).show();
            }
        });
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentView(webView);

        if (savedInstanceState == null) {
            webView.loadUrl(APP_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
