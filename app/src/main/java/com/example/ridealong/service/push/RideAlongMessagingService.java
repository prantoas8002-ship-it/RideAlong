package com.example.ridealong.service.push;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.ridealong.data.model.AppNotification;
import com.example.ridealong.data.model.CallSession;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.remote.FirestorePaths;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.service.RealtimeNotificationListener;
import com.example.ridealong.service.call.CallManager;
import com.example.ridealong.service.call.IncomingCallWatcher;
import com.example.ridealong.utils.AppExecutors;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Receives the relay's data-only pushes. Works when RideAlong was fully closed: Android starts the
 * app process for a high-priority FCM message. Pushes carry ids only; content comes from Firestore
 * (subject to the normal security rules) and message text is never shown.
 */
public class RideAlongMessagingService extends FirebaseMessagingService {
    private static final String TAG = "RideAlongPush";

    @Override
    public void onNewToken(@NonNull String token) { PushTokens.save(token); }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        if (!FirebaseProvider.isAvailable() || FirebaseProvider.uidOrNull() == null) return;
        Map<String, String> d = message.getData();
        String kind = d.get("kind");
        try {
            if ("call".equals(kind)) onCall(d.get("callId"));
            else if ("notification".equals(kind)) onNotification(d.get("notificationId"));
        } catch (Exception e) {
            Log.w(TAG, "push handling failed", e);
        }
    }

    /** Runs on FCM's background thread, so blocking reads are fine (FCM allows ~10 s). */
    private void onCall(String callId) throws Exception {
        if (callId == null) return;
        DocumentSnapshot snap = Tasks.await(FirebaseProvider.db().collection(FirestorePaths.CALLS).document(callId).get(),
                8, TimeUnit.SECONDS);
        CallSession s = snap.toObject(CallSession.class);
        if (s == null || !CallSession.RINGING.equals(s.status)) return;
        long age = s.createdAt == null ? 0 : System.currentTimeMillis() - s.createdAt.toDate().getTime();
        if (age > AppConfig.get().callStaleAfterMs) return;
        AppExecutors.main(() -> IncomingCallWatcher.deliver(getApplicationContext(), s));
    }

    private void onNotification(String id) throws Exception {
        if (id == null) return;
        DocumentSnapshot snap = Tasks.await(FirebaseProvider.db().collection(FirestorePaths.NOTIFICATIONS).document(id).get(),
                8, TimeUnit.SECONDS);
        AppNotification n = snap.toObject(AppNotification.class);
        if (n == null || n.read) return;
        AppExecutors.main(() -> RealtimeNotificationListener.showFromPush(getApplicationContext(), n));
    }
}
