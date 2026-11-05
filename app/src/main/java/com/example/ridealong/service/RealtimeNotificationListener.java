package com.example.ridealong.service;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.AppNotification;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.repository.NotificationRepository;
import com.example.ridealong.data.repository.SettingsRepository;
import com.example.ridealong.utils.Constants;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Process-wide listener on notifications/{userId == me}. New documents become local Android
 * notifications. Works while the app process is alive (foreground, recently backgrounded, or
 * kept alive by RealtimeSyncService / LocationShareService). Server push to a killed app would
 * need FCM + Cloud Functions, which require the paid Blaze plan; this limitation is documented.
 */
public final class RealtimeNotificationListener {
    private static RealtimeNotificationListener instance;
    /** Chat currently on screen: its message notifications are suppressed. */
    public static volatile String visibleChatRideId;

    private final Context app;
    private final MutableLiveData<Integer> unread = new MutableLiveData<>(0);
    private ListenerRegistration reg;
    private String uid;

    private RealtimeNotificationListener(Context app) { this.app = app.getApplicationContext(); }

    public static synchronized RealtimeNotificationListener get(Context c) {
        if (instance == null) instance = new RealtimeNotificationListener(c);
        return instance;
    }

    public LiveData<Integer> unreadCount() { return unread; }

    public synchronized void start() {
        String me = FirebaseProvider.uidOrNull();
        if (me == null) {
            stop();
            return;
        }
        if (reg != null && me.equals(uid)) return;
        stop();
        uid = me;
        SettingsRepository settings = ServiceLocator.get().settings();
        NotificationRepository repo = ServiceLocator.get().notifications();
        reg = repo.unreadQuery(me).addSnapshotListener((snap, err) -> {
            if (err != null || snap == null) return;
            int count = 0;
            for (DocumentSnapshot d : snap.getDocuments()) {
                Boolean read = d.getBoolean("read");
                if (read == null || !read) count++;
            }
            unread.setValue(count);
            if (snap.getMetadata().hasPendingWrites()) return;
            long lastSeen = settings.lastNotificationSeen(me);
            long newest = lastSeen;
            for (DocumentChange ch : snap.getDocumentChanges()) {
                if (ch.getType() != DocumentChange.Type.ADDED) continue;
                AppNotification n = ch.getDocument().toObject(AppNotification.class);
                long created = n.createdAt == null ? 0 : n.createdAt.toDate().getTime();
                if (created <= lastSeen || n.read) continue;
                newest = Math.max(newest, created);
                if (lastSeen == 0) continue; // first launch: don't replay history as alerts
                maybeShow(n, settings);
            }
            if (newest > lastSeen || lastSeen == 0) {
                settings.setLastNotificationSeen(me, Math.max(newest, System.currentTimeMillis() - 1000));
            }
        });
    }

    private void maybeShow(AppNotification n, SettingsRepository settings) {
        boolean isMessage = Constants.N_NEW_MESSAGE.equals(n.type);
        if (isMessage && !settings.notifyMessages()) return;
        if (!isMessage && !settings.notifyRequests()) return;
        if (isMessage && n.rideId != null && n.rideId.equals(visibleChatRideId)) return;
        String[] tb = NotificationRepository.render(app, n);
        String channel = isMessage ? Constants.CHANNEL_MESSAGES : Constants.CHANNEL_RIDES;
        // One notification per chat (updates in place); one per other notification.
        int id = isMessage && n.rideId != null ? ("chat" + n.rideId).hashCode() : (n.id == null ? 0 : n.id.hashCode());
        NotificationHelper.show(app, channel, id, tb[0], tb[1],
                n.rideId == null ? NotificationHelper.openNotificationsIntent(app)
                        : NotificationHelper.openRideIntent(app, n.rideId, isMessage, id));
    }

    public synchronized void stop() {
        if (reg != null) {
            reg.remove();
            reg = null;
        }
        uid = null;
        unread.postValue(0);
    }
}