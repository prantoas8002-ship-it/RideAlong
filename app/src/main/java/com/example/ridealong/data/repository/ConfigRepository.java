package com.example.ridealong.data.repository;

import android.util.Log;

import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.remote.FirestorePaths;
import com.example.ridealong.domain.config.AppConfig;

/**
 * Loads optional overrides from Firestore config/app (editable in the Firebase console) into
 * AppConfig. Missing document = built-in defaults.
 */
public class ConfigRepository {
    private static final String TAG = "ConfigRepository";
    private boolean loaded;

    public synchronized void loadOnce() {
        if (loaded || !FirebaseProvider.isAvailable() || FirebaseProvider.uidOrNull() == null) return;
        loaded = true;
        FirebaseProvider.db().collection(FirestorePaths.CONFIG).document(FirestorePaths.CONFIG_APP).get()
                .addOnSuccessListener(snap -> {
                    if (snap != null && snap.exists()) AppConfig.get().applyOverrides(snap.getData());
                })
                .addOnFailureListener(e -> {
                    Log.i(TAG, "config/app not loaded, using defaults", e);
                    synchronized (ConfigRepository.this) {
                        loaded = false;
                    }
                });
    }
}