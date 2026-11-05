package com.example.ridealong.service.call;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.ridealong.R;
import com.example.ridealong.ui.call.CallActivity;
import com.example.ridealong.utils.Constants;

/**
 * Foreground service (type=microphone) that keeps a voice call alive with the screen off or the
 * app in the background. Android silences the microphone for background apps without it.
 */
public class CallService extends Service {
    private static final String ACTION_HANGUP = "com.example.ridealong.action.HANGUP";
    private PowerManager.WakeLock cpuLock;

    public static void start(Context c) {
        try {
            ContextCompat.startForegroundService(c, new Intent(c, CallService.class));
        } catch (Exception ignored) {
            // background-start restriction: the call still works while the app is visible
        }
    }

    /** Refresh the notification text (e.g. ringing → connected). */
    public static void update(Context c) { start(c); }

    public static void stop(Context c) { c.stopService(new Intent(c, CallService.class)); }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null && ACTION_HANGUP.equals(intent.getAction())) {
            CallManager.get(this).hangUp();
            return START_NOT_STICKY;
        }
        CallManager.State s = CallManager.get(this).state().getValue();
        String peer = s == null || s.peerName == null ? getString(R.string.unknown_user) : s.peerName;
        boolean connected = s != null && s.phase == CallManager.Phase.CONNECTED;
        PendingIntent open = PendingIntent.getActivity(this, 11, new Intent(this, CallActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent hang = PendingIntent.getService(this, 12, new Intent(this, CallService.class).setAction(ACTION_HANGUP),
                PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, Constants.CHANNEL_CALL_ONGOING)
                .setSmallIcon(R.drawable.ic_call)
                .setColor(getColor(R.color.ra_primary))
                .setContentTitle(getString(connected ? R.string.call_ongoing_title : R.string.call_status_calling))
                .setContentText(peer)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(open)
                .addAction(R.drawable.ic_close, getString(R.string.call_hang_up), hang);
        if (connected && s.connectedAt > 0) b.setUsesChronometer(true).setWhen(s.connectedAt);
        Notification n = b.build();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(Constants.NOTIF_ID_CALL_ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(Constants.NOTIF_ID_CALL_ONGOING, n);
            }
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (cpuLock == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RideAlong:call");
            cpuLock.acquire(2 * 60 * 60 * 1000L); // safety cap: 2 hours
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (cpuLock != null && cpuLock.isHeld()) cpuLock.release();
        cpuLock = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}