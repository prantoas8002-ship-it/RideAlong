package com.example.ridealong.service.call;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.CallSession;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.repository.CallRepository;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.ui.call.CallActivity;
import com.example.ridealong.utils.Constants;
import com.example.ridealong.utils.PermissionUtils;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Listens for calls ringing for me. Foreground → opens the call screen directly; background →
 * full-screen call notification with Accept / Decline. Like other notifications on the Spark
 * plan, this works while the app process is alive (enable Settings → Background sync).
 */
public final class IncomingCallWatcher {
    private static IncomingCallWatcher instance;
    private final Context app;
    private ListenerRegistration reg;
    private String uid;

    private IncomingCallWatcher(Context c) { app = c.getApplicationContext(); }

    public static synchronized IncomingCallWatcher get(Context c) {
        if (instance == null) instance = new IncomingCallWatcher(c);
        return instance;
    }

    public synchronized void start() {
        String me = FirebaseProvider.uidOrNull();
        if (me == null) {
            stop();
            return;
        }
        if (reg != null && me.equals(uid)) return;
        stop();
        uid = me;
        reg = new CallRepository().incoming(me).addSnapshotListener((snap, err) -> {
            if (err != null || snap == null) return;
            for (DocumentChange ch : snap.getDocumentChanges()) {
                if (ch.getType() != DocumentChange.Type.ADDED) continue;
                CallSession s = ch.getDocument().toObject(CallSession.class);
                long age = s.createdAt == null ? 0 : System.currentTimeMillis() - s.createdAt.toDate().getTime();
                if (age > AppConfig.get().callStaleAfterMs) continue; // left over from a crashed caller
                if (ServiceLocator.get().safety().hiddenUserIds().contains(s.callerId)) {
                    new CallRepository().setStatus(s.id, CallSession.REJECTED);
                    continue;
                }
                CallManager.get(app).onIncoming(s);
                if (s.id.equals(currentCallId())) present(s);
            }
        });
    }

    private String currentCallId() {
        CallManager.State st = CallManager.get(app).state().getValue();
        return st == null ? null : st.callId;
    }

    private void present(CallSession s) {
        boolean foreground = ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
        Intent open = new Intent(app, CallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (foreground) {
            app.startActivity(open);
            return;
        }
        if (!PermissionUtils.hasNotificationPermission(app)) return;
        PendingIntent full = PendingIntent.getActivity(app, 21, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent accept = PendingIntent.getActivity(app, 22, new Intent(open).putExtra(CallActivity.EXTRA_AUTO_ACCEPT, true),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent decline = PendingIntent.getBroadcast(app, 23, new Intent(app, CallActionReceiver.class)
                .setAction(CallActionReceiver.ACTION_DECLINE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String name = s.callerName == null || s.callerName.isEmpty() ? app.getString(R.string.unknown_user) : s.callerName;
        NotificationCompat.Builder b = new NotificationCompat.Builder(app, Constants.CHANNEL_CALLS)
                .setSmallIcon(R.drawable.ic_call)
                .setColor(app.getColor(R.color.ra_primary))
                .setContentTitle(app.getString(s.video ? R.string.call_incoming_video_title : R.string.call_incoming_title))
                .setContentText(name)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(true)
                .setTimeoutAfter(AppConfig.get().callRingTimeoutMs)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .addAction(R.drawable.ic_close, app.getString(R.string.call_decline), decline)
                .addAction(R.drawable.ic_call, app.getString(R.string.call_accept), accept);
        try {
            NotificationManagerCompat.from(app).notify(Constants.NOTIF_ID_CALL_INCOMING, b.build());
        } catch (SecurityException ignored) {
            // notification permission revoked
        }
    }

    public static void cancelIncomingNotification(Context c) {
        NotificationManagerCompat.from(c).cancel(Constants.NOTIF_ID_CALL_INCOMING);
    }

    public synchronized void stop() {
        if (reg != null) reg.remove();
        reg = null;
        uid = null;
    }
}