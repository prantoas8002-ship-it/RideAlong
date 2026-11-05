package com.example.ridealong.data.remote;

import android.content.Context;
import android.util.Log;

import com.example.ridealong.BuildConfig;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;

/**
 * Single entry point to Firebase. Detects a missing google-services.json (no FirebaseApp) so the
 * UI can show a configuration screen instead of crashing, and wires the Emulator Suite when
 * USE_FIREBASE_EMULATOR=true in local.properties.
 */
public final class FirebaseProvider {
    private static final String TAG = "FirebaseProvider";
    private static volatile boolean available;
    private static volatile boolean initialised;

    private FirebaseProvider() { }

    public static synchronized void init(Context context) {
        if (initialised) return;
        initialised = true;
        try {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context);
            if (FirebaseApp.getApps(context).isEmpty() && BuildConfig.USE_FIREBASE_EMULATOR) {
                // Development mode without google-services.json: Firebase's "demo-" project convention.
                // demo-* projects only work against the Emulator Suite and can never reach production,
                // so these are placeholders, not credentials.
                FirebaseApp.initializeApp(context, new FirebaseOptions.Builder()
                        .setProjectId(BuildConfig.FIREBASE_EMULATOR_PROJECT_ID)
                        .setApplicationId("1:000000000000:android:0000000000000000")
                        .setApiKey("emulator-only")
                        .build());
            }
            available = !FirebaseApp.getApps(context).isEmpty();
        } catch (Exception e) {
            Log.w(TAG, "Firebase not configured", e);
            available = false;
        }
        if (!available) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            String host = BuildConfig.FIREBASE_EMULATOR_HOST;
            FirebaseAuth.getInstance().useEmulator(host, 9099);
            db.useEmulator(host, 8080);
            Log.i(TAG, "Using Firebase Emulator Suite at " + host);
        }
        // Offline persistence: Firestore keeps a local cache and syncs pending writes when back online.
        db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build());
    }

    public static boolean isAvailable() { return available; }

    public static FirebaseAuth auth() { return FirebaseAuth.getInstance(); }

    public static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    /** Current user id or null. Safe to call when Firebase is not configured. */
    public static String uidOrNull() {
        if (!available) return null;
        return auth().getCurrentUser() == null ? null : auth().getCurrentUser().getUid();
    }
}
