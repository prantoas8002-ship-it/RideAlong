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
import com.example.ridealong.data.model.GroupCall;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.repository.CallRepository;
import com.example.ridealong.data.repository.GroupCallRepository;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.ui.call.CallActivity;
import com.example.ridealong.ui.call.GroupCallActivity;
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
    private ListenerRegistration groupReg;
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
                deliver(app, s);
            }
        });
        listenGroupInvites(me);
    }

    /**
     * Group invites. A failed listener (e.g. the index is still building right after a deploy, or a
     * network error) is dead for good, so retry instead of silently never ringing again.
     */
    private synchronized void listenGroupInvites(String me) {
        if (!me.equals(uid)) return;
        if (groupReg != null) groupReg.remove();
        groupReg = new GroupCallRepository().activeForMe(me).addSnapshotListener((snap, err) -> {
            if (err != null) {
                android.util.Log.w("IncomingCallWatcher", "group invite listener failed, retrying", err);
                com.example.ridealong.utils.AppExecutors.mainDelayed(() -> listenGroupInvites(me), 30_000);
                return;
            }
            if (snap == null) return;
            for (DocumentChange ch : snap.getDocumentChanges()) {
                if (ch.getType() != DocumentChange.Type.ADDED) continue;
                GroupCall g = ch.getDocument().toObject(GroupCall.class);
                if (me.equals(g.hostId) || g.createdAt == null) continue;
                long age = System.currentTimeMillis() - g.createdAt.toDate().getTime();
                if (age > AppConfig.get().callStaleAfterMs) continue; // can still join from the ride chat
                if (ServiceLocator.get().safety().hiddenUserIds().contains(g.hostId)) continue;
                deliverGroup(app, g);
            }
        });
    }

    /** Rings for a group video call I was invited to (live listener and FCM push). */
    public static void deliverGroup(Context c, GroupCall g) {
        Context app = c.getApplicationContext();
        boolean foreground = ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
        GroupCallManager gm = GroupCallManager.get(app);
        gm.onInvite(g, foreground);
        GroupCall pending = gm.pendingInvite();
        if (pending == null || !g.id.equals(pending.id)) return; // busy on another call
        Intent open = new Intent(app, GroupCallActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(GroupCallActivity.EXTRA_CALL_ID, g.id);
        if (foreground) {
            app.startActivity(open);
            return;
        }
        if (!PermissionUtils.hasNotificationPermission(app)) return;
        PendingIntent full = PendingIntent.getActivity(app, 31, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent join = PendingIntent.getActivity(app, 32, new Intent(open).putExtra(GroupCallActivity.EXTRA_AUTO_JOIN, true),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent decline = PendingIntent.getBroadcast(app, 33, new Intent(app, CallActionReceiver.class)
                .setAction(CallActionReceiver.ACTION_DECLINE_GROUP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String name = g.hostName == null || g.hostName.isEmpty() ? app.getString(R.string.unknown_user) : g.hostName;
        NotificationCompat.Builder b = new NotificationCompat.Builder(app, Constants.CHANNEL_CALLS)
                .setSmallIcon(R.drawable.ic_group)
                .setColor(app.getColor(R.color.ra_primary))
                .setContentTitle(app.getString(R.string.group_call_incoming_title))
                .setContentText(app.getString(R.string.group_call_incoming_body, name))
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(true)
                .setTimeoutAfter(AppConfig.get().callRingTimeoutMs)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .addAction(R.drawable.ic_close, app.getString(R.string.call_decline), decline)
                .addAction(R.drawable.ic_videocam, app.getString(R.string.group_call_join), join);
        android.app.Notification n = b.build();
        n.flags |= android.app.Notification.FLAG_INSISTENT; // keep ringing until joined/declined/timeout
        try {
            NotificationManagerCompat.from(app).notify(Constants.NOTIF_ID_GROUP_CALL_INCOMING, n);
        } catch (SecurityException ignored) {
            // notification permission revoked
        }
    }

    public static void cancelGroupInviteNotification(Context c) {
        NotificationManagerCompat.from(c).cancel(Constants.NOTIF_ID_GROUP_CALL_INCOMING);
    }

    /**
     * Hands a ringing call to CallManager and shows it: call screen when the app is visible, otherwise a
     * ringing full-screen notification (used by both the live listener and the FCM push path).
     */
    public static void deliver(Context c, CallSession s) {
        Context app = c.getApplicationContext();
        boolean foreground = ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
        CallManager cm = CallManager.get(app);
        cm.onIncoming(s, foreground);
        CallManager.State st = cm.state().getValue();
        if (st == null || !s.id.equals(st.callId)) return; // busy on another call
        Intent open = new Intent(app, CallActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(CallActivity.EXTRA_CALL_ID, s.id);
        if (foreground) {
            app.startActivity(open);
            return;
        }
        if (!PermissionUtils.hasNotificationPermission(app)) return;
        PendingIntent full = PendingIntent.getActivity(app, 21, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent accept = PendingIntent.getActivity(app, 22, new Intent(open).putExtra(CallActivity.EXTRA_AUTO_ACCEPT, true),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent decline = PendingIntent.getBroadcast(app, 23, new Intent(app, CallActionReceiver.class)
                .setAction(CallActionReceiver.ACTION_DECLINE).putExtra(CallActivity.EXTRA_CALL_ID, s.id),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
        android.app.Notification n = b.build();
        n.flags |= android.app.Notification.FLAG_INSISTENT; // keep ringing until answered/declined/timeout
        try {
            NotificationManagerCompat.from(app).notify(Constants.NOTIF_ID_CALL_INCOMING, n);
        } catch (SecurityException ignored) {
            // notification permission revoked
        }
    }

    public static void cancelIncomingNotification(Context c) {
        NotificationManagerCompat.from(c).cancel(Constants.NOTIF_ID_CALL_INCOMING);
    }

    public synchronized void stop() {
        if (reg != null) reg.remove();
        if (groupReg != null) groupReg.remove();
        reg = null;
        groupReg = null;
        uid = null;
    }
}