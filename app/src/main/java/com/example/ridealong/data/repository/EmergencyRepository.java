package com.example.ridealong.data.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.ridealong.data.local.AppDatabase;
import com.example.ridealong.data.local.EmergencyLogEntity;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.remote.FirestorePaths;
import com.example.ridealong.utils.AppExecutors;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Logs SOS actions locally first (always works offline) and then to Firestore for the user's history. */
public class EmergencyRepository {
    private final AppDatabase db;

    public EmergencyRepository(AppDatabase db) { this.db = db; }

    public void log(String action, @Nullable Double lat, @Nullable Double lng, @Nullable String rideId,
                    @Nullable String contactName) {
        String uid = FirebaseProvider.uidOrNull();
        if (uid == null) return;
        AppExecutors.io().execute(() -> {
            EmergencyLogEntity e = new EmergencyLogEntity();
            e.userId = uid;
            e.action = action;
            e.lat = lat;
            e.lng = lng;
            e.rideId = rideId;
            e.contactName = contactName;
            e.createdMillis = System.currentTimeMillis();
            e.localId = db.emergencyLog().insert(e);
            upload(e);
        });
    }

    private void upload(EmergencyLogEntity e) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", e.userId);
        m.put("action", e.action);
        m.put("lat", e.lat);
        m.put("lng", e.lng);
        m.put("rideId", e.rideId);
        m.put("contactName", e.contactName);
        m.put("clientCreatedMillis", e.createdMillis);
        m.put("createdAt", FieldValue.serverTimestamp());
        FirebaseProvider.db().collection(FirestorePaths.EMERGENCY_EVENTS).add(m)
                .addOnSuccessListener(r -> AppExecutors.io().execute(() -> db.emergencyLog().markSynced(e.localId)));
    }

    /** Re-uploads entries that were logged while offline. */
    public void syncPending() {
        if (FirebaseProvider.uidOrNull() == null) return;
        AppExecutors.io().execute(() -> {
            List<EmergencyLogEntity> pending = db.emergencyLog().unsynced();
            for (EmergencyLogEntity e : pending) {
                if (e.userId != null && e.userId.equals(FirebaseProvider.uidOrNull())) upload(e);
            }
        });
    }

    public LiveData<List<EmergencyLogEntity>> observeLocal(String uid) { return db.emergencyLog().observe(uid); }
}