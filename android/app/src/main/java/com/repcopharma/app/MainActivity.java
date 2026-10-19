package com.repcopharma.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Color;
import android.util.Base64;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public final class MainActivity extends Activity {
    private static final String APP_URL = "https://repco.pages.dev";
    private static final int DOWNLOAD_PERMISSION_REQUEST = 41;
    private WebView webView;
    private PendingBlob pendingBlob;

    private static final class PendingBlob {
        final String name, mime;
        final byte[] bytes;
        PendingBlob(String name, String mime, byte[] bytes) {
            this.name = name; this.mime = mime; this.bytes = bytes;
        }
    }

    private final class AndroidDownloadBridge {
        @JavascriptInterface
        public void saveBlob(String filename, String mimeType, String base64) {
            try {
                String safeName = new File(filename == null ? "report.xlsx" : filename).getName().replaceAll("[^A-Za-z0-9._-]", "_");
                String mime = (mimeType == null || mimeType.isEmpty()) ? "application/octet-stream" : mimeType;
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                runOnUiThread(() -> saveDownload(new PendingBlob(safeName, mime, bytes)));
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Excel ফাইল তৈরি করা যায়নি।", Toast.LENGTH_LONG).show());
            }
        }
    }

    private void saveDownload(PendingBlob file) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingBlob = file;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, DOWNLOAD_PERMISSION_REQUEST);
            return;
        }
        try {
            OutputStream output;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, file.name);
                values.put(MediaStore.Downloads.MIME_TYPE, file.mime);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("Could not create download");
                output = getContentResolver().openOutputStream(uri);
                if (output == null) throw new IllegalStateException("Could not open download");
                output.write(file.bytes);
                output.close();
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, ready, null, null);
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create Downloads folder");
                File target = new File(dir, file.name);
                output = new FileOutputStream(target);
                output.write(file.bytes);
                output.close();
                sendBroadcast(new android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(target)));
            }
            Toast.makeText(this, "ফাইলটি Downloads ফোল্ডারে সেভ হয়েছে।", Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Toast.makeText(this, "ফাইল সেভ করা যায়নি। আবার চেষ্টা করুন।", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == DOWNLOAD_PERMISSION_REQUEST && pendingBlob != null) {
            PendingBlob file = pendingBlob;
            pendingBlob = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) saveDownload(file);
            else Toast.makeText(this, "Downloads ফোল্ডারে সেভ করার অনুমতি দিন।", Toast.LENGTH_LONG).show();
        }
    }

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
        webView.addJavascriptInterface(new AndroidDownloadBridge(), "AndroidDownload");
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
                request.setDescription("Rephco Pharmaceutical download");
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

        if (savedInstanceState == null) webView.loadUrl(APP_URL);
        else webView.restoreState(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
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
