package com.repcopharma.app;

import android.Manifest;
import android.content.Intent;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Build;
import org.json.JSONObject;
import com.google.firebase.messaging.FirebaseMessaging;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
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

// Self-updater release point for Cloudflare-hosted APK metadata.
public final class MainActivity extends Activity {
    private static final String APP_URL = "https://repco.pages.dev";
    private static final String UPDATE_JSON_URL = "https://repco-app-download.pages.dev/version.json";
    private static final String APK_DOWNLOAD_URL = "https://repco-app-download.pages.dev/Rephco-Pharma.apk";
    private static final int INSTALL_SOURCE_SETTINGS_REQUEST = 43;
    private static final String UPDATE_PREFS = "app_update_preferences";
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private long updateDownloadId = -1L;
    private boolean waitingForInstallPermission = false;
    private static final int DOWNLOAD_PERMISSION_REQUEST = 41;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 42;
    private static volatile MainActivity activeActivity;
    private WebView webView;
    private PendingBlob pendingBlob;
    private ValueCallback<Uri[]> pendingFileChooser;
    private static final int IMAGE_PICKER_REQUEST = 77;

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

    // Save Blob-generated reports directly to the Android Downloads collection.
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


    private void checkForAppUpdate() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(UPDATE_JSON_URL).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Cache-Control", "no-cache");
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                JSONObject manifest = new JSONObject(body.toString());
                int latestCode = manifest.optInt("versionCode", 0);
                String latestName = manifest.optString("versionName", "");
                if (latestCode <= getPackageManager().getPackageInfo(getPackageName(), 0).versionCode) return;
                if (getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE).getInt("dismissed_version", 0) == latestCode) return;
                runOnUiThread(() -> showUpdateDialog(latestCode, latestName));
            } catch (Exception ignored) {
                // A temporary network failure should not interrupt opening the app.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "rephco-update-check").start();
    }

    private void showUpdateDialog(int versionCode, String versionName) {
        if (isFinishing() || isDestroyed()) return;
        String version = versionName.isEmpty() ? "" : " (" + versionName + ")";
        new AlertDialog.Builder(this)
                .setTitle("নতুন app update")
                .setMessage("Rephco app-এর নতুন version" + version + " পাওয়া গেছে। এখন update করবেন?")
                .setCancelable(false)
                .setPositiveButton("Update Now", (dialog, which) -> requestInstallPermissionThenDownload())
                .setNegativeButton("পরে", (dialog, which) ->
                        getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE).edit()
                                .putInt("dismissed_version", versionCode).apply())
                .show();
    }

    private void requestInstallPermissionThenDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !getPackageManager().canRequestPackageInstalls()) {
            waitingForInstallPermission = true;
            try {
                Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(settingsIntent, INSTALL_SOURCE_SETTINGS_REQUEST);
            } catch (Exception error) {
                waitingForInstallPermission = false;
                Toast.makeText(this, "এই app থেকে update install করার অনুমতি চালু করুন।", Toast.LENGTH_LONG).show();
            }
            return;
        }
        startUpdateDownload();
    }

    private void startUpdateDownload() {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(APK_DOWNLOAD_URL));
            request.setTitle("Rephco app update");
            request.setDescription("নতুন app version download হচ্ছে");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS,
                    "Rephco-Pharma-update-" + (getPackageManager().getPackageInfo(getPackageName(), 0).versionCode + 1) + ".apk");
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            updateDownloadId = manager.enqueue(request);
            Toast.makeText(this, "Update download শুরু হয়েছে।", Toast.LENGTH_LONG).show();
            updateHandler.postDelayed(this::checkUpdateDownload, 1200);
        } catch (Exception error) {
            Toast.makeText(this, "Update download শুরু করা যায়নি।", Toast.LENGTH_LONG).show();
        }
    }

    private void checkUpdateDownload() {
        if (updateDownloadId < 0) return;
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        Cursor cursor = null;
        try {
            cursor = manager.query(new DownloadManager.Query().setFilterById(updateDownloadId));
            if (cursor == null || !cursor.moveToFirst()) return;
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING ||
                    status == DownloadManager.STATUS_PAUSED) {
                updateHandler.postDelayed(this::checkUpdateDownload, 1200);
            } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                Uri apkUri = manager.getUriForDownloadedFile(updateDownloadId);
                updateDownloadId = -1L;
                if (apkUri == null) throw new IllegalStateException("Downloaded APK URI is missing");
                Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(installIntent);
            } else {
                updateDownloadId = -1L;
                Toast.makeText(this, "Update download হয়নি। Internet দেখে আবার চেষ্টা করুন।", Toast.LENGTH_LONG).show();
            }
        } catch (Exception error) {
            updateDownloadId = -1L;
            Toast.makeText(this, "APK install screen খোলা যায়নি।", Toast.LENGTH_LONG).show();
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    public static void notifyPushTokenChanged(String token) {
        MainActivity activity = activeActivity;
        if (activity != null) activity.sendPushTokenToWeb(token);
    }

    private void sendPushTokenToWeb(String token) {
        WebView target = webView;
        if (target == null || token == null) return;
        Uri uri = Uri.parse(target.getUrl() == null ? "" : target.getUrl());
        if (!"repco.pages.dev".equals(uri.getHost())) return;
        String script = "window.repcoReceiveFcmToken && window.repcoReceiveFcmToken(" + JSONObject.quote(token) + ");";
        target.post(() -> target.evaluateJavascript(script, null));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activeActivity = this;
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
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!"repco.pages.dev".equals(Uri.parse(url).getHost())) return;
                FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) sendPushTokenToWeb(task.getResult());
                });
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback, FileChooserParams params) {
                if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
                pendingFileChooser = filePathCallback;
                Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                picker.setType("image/*");
                try {
                    startActivityForResult(Intent.createChooser(picker, "ছবি নির্বাচন করুন"), IMAGE_PICKER_REQUEST);
                    return true;
                } catch (Exception error) {
                    pendingFileChooser = null;
                    filePathCallback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "ছবি নির্বাচন করা যাচ্ছে না।", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
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
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        }

        if (savedInstanceState == null) webView.loadUrl(APP_URL);
        else webView.restoreState(savedInstanceState);
        checkForAppUpdate();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == INSTALL_SOURCE_SETTINGS_REQUEST && waitingForInstallPermission) {
            waitingForInstallPermission = false;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls()) startUpdateDownload();
            else Toast.makeText(this, "Update করতে install permission চালু করতে হবে।", Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == IMAGE_PICKER_REQUEST) {
            if (pendingFileChooser != null) {
                Uri[] result = resultCode == Activity.RESULT_OK && data != null && data.getData() != null ? new Uri[]{data.getData()} : null;
                pendingFileChooser.onReceiveValue(result);
                pendingFileChooser = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
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
        if (activeActivity == this) activeActivity = null;
        updateHandler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
