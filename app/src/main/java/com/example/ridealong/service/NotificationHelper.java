package com.example.ridealong.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.ridealong.R;
import com.example.ridealong.ui.MainActivity;
import com.example.ridealong.utils.Constants;
import com.example.ridealong.utils.PermissionUtils;

/** Notification channels and local notifications (the delivery path on the Spark plan). */
public final class NotificationHelper {
    private NotificationHelper() { }

    public static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(channel(c, Constants.CHANNEL_RIDES, R.string.channel_rides, R.string.channel_rides_desc,
                NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(channel(c, Constants.CHANNEL_MESSAGES, R.string.channel_messages, R.string.channel_messages_desc,
                NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(channel(c, Constants.CHANNEL_LOCATION, R.string.channel_location, R.string.channel_location_desc,
                NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(channel(c, Constants.CHANNEL_GEOFENCE, R.string.channel_geofence, R.string.channel_geofence_desc,
                NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(channel(c, Constants.CHANNEL_SYNC, R.string.channel_sync, R.string.channel_sync_desc,
                NotificationManager.IMPORTANCE_MIN));
        NotificationChannel calls = channel(c, Constants.CHANNEL_CALLS, R.string.channel_calls, R.string.channel_calls_desc,
                NotificationManager.IMPORTANCE_HIGH);
        calls.setSound(null, null); // CallManager plays the ringtone itself (and stops it on answer)
        nm.createNotificationChannel(calls);
        nm.createNotificationChannel(channel(c, Constants.CHANNEL_CALL_ONGOING, R.string.channel_call_ongoing,
                R.string.channel_call_ongoing_desc, NotificationManager.IMPORTANCE_LOW));
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private static NotificationChannel channel(Context c, String id, int name, int desc, int importance) {
        NotificationChannel ch = new NotificationChannel(id, c.getString(name), importance);
        ch.setDescription(c.getString(desc));
        return ch;
    }

    public static PendingIntent openRideIntent(Context c, String rideId, boolean openChat, int requestCode) {
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(Constants.EXTRA_RIDE_ID, rideId)
                .putExtra(Constants.EXTRA_OPEN_CHAT, openChat);
        return PendingIntent.getActivity(c, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static PendingIntent openDirectChatIntent(Context c, String chatId, int requestCode) {
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(Constants.EXTRA_DIRECT_CHAT_ID, chatId);
        return PendingIntent.getActivity(c, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static PendingIntent openNotificationsIntent(Context c) {
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(Constants.EXTRA_OPEN_NOTIFICATIONS, true);
        return PendingIntent.getActivity(c, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Shows a notification if permitted. Same id replaces the previous one (e.g. one per chat). */
    public static void show(Context c, String channel, int id, String title, String body, PendingIntent tap) {
        if (!PermissionUtils.hasNotificationPermission(c)) return;
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_navigation)
                .setColor(c.getColor(R.color.ra_primary))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(tap);
        try {
            NotificationManagerCompat.from(c).notify(id, b.build());
        } catch (SecurityException ignored) {
            // permission revoked between check and notify
        }
    }
}