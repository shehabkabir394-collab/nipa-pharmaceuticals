package com.repcopharma.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public final class RepcoMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "repco_product_updates";

    @Override
    public void onNewToken(String token) {
        MainActivity.notifyPushTokenChanged(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        String title = message.getNotification() == null ? null : message.getNotification().getTitle();
        String body = message.getNotification() == null ? null : message.getNotification().getBody();
        if (title == null || title.isBlank()) title = message.getData().getOrDefault("title", "Rephco Pharma");
        if (body == null || body.isBlank()) body = message.getData().getOrDefault("body", "নতুন পণ্য যোগ হয়েছে।");

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Product updates", NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent tap = launch == null ? null : PendingIntent.getActivity(this, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_DEFAULT);
        if (tap != null) builder.setContentIntent(tap);
        manager.notify((int) (System.currentTimeMillis() & 0x7fffffff), builder.build());
    }
}
