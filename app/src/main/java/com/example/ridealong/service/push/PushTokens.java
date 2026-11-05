package com.example.ridealong.service.push;

import com.example.ridealong.data.remote.FirebaseProvider;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

/** Stores this device's FCM token at pushTokens/{uid} (owner-write only, never client-readable). */
public final class PushTokens {
    private PushTokens() { }

    public static void registerCurrentDevice() {
        if (!FirebaseProvider.isAvailable() || FirebaseProvider.uidOrNull() == null) return;
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(PushTokens::save);
        } catch (Exception ignored) {
            // Play services missing: pushes simply won't arrive on this device
        }
    }

    static void save(String token) {
        String uid = FirebaseProvider.uidOrNull();
        if (uid == null || token == null) return;
        Map<String, Object> m = new HashMap<>();
        m.put("token", token);
        m.put("platform", "android");
        m.put("updatedAt", FieldValue.serverTimestamp());
        FirebaseProvider.db().collection("pushTokens").document(uid).set(m);
    }

    /** On logout: invalidate this device's token so the old account stops receiving pushes here. */
    public static void unregisterCurrentDevice() {
        try {
            FirebaseMessaging.getInstance().deleteToken();
        } catch (Exception ignored) {
            // nothing registered
        }
    }
}
