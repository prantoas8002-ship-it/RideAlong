package com.example.ridealong.service;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;

import com.example.ridealong.R;
import com.example.ridealong.utils.Constants;

/**
 * OPTIONAL (Settings → Background sync). A low-priority foreground service that keeps the
 * process alive so RealtimeNotificationListener keeps delivering notifications while the app is
 * in the background. Android may still stop it (battery savers, Android 15 dataSync time limits).
 */
public class RealtimeSyncService extends LifecycleService {

    public static void start(Context c) {
        try {
            ContextCompat.startForegroundService(c, new Intent(c, RealtimeSyncService.class));
        } catch (Exception ignored) {
            // Background start restrictions: it will start next time the app is in the foreground.
        }
    }

    public static void stop(Context c) { c.stopService(new Intent(c, RealtimeSyncService.class)); }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Notification n = new NotificationCompat.Builder(this, Constants.CHANNEL_SYNC)
                .setSmallIcon(R.drawable.ic_navigation)
                .setContentTitle(getString(R.string.notif_sync_title))
                .setContentText(getString(R.string.notif_sync_body))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setContentIntent(NotificationHelper.openNotificationsIntent(this))
                .build();
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0;
        try {
            ServiceCompat.startForeground(this, Constants.NOTIF_ID_SYNC_SERVICE, n, type);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        RealtimeNotificationListener.get(this).start();
        return START_STICKY;
    }

    /** Android 15+: dataSync services get a daily time budget; stop cleanly when it's used up. */
    @Override
    public void onTimeout(int startId) {
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        super.onBind(intent);
        return null;
    }
}