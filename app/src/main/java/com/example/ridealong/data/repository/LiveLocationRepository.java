package com.example.ridealong.data.repository;

import androidx.lifecycle.LiveData;

import com.example.ridealong.data.model.LiveLocation;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.remote.FirestoreLiveData;
import com.example.ridealong.data.remote.FirestorePaths;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** liveLocations/{rideId}/members/{uid}: rules restrict reads/writes to ride members. */
public class LiveLocationRepository {
    private CollectionReference members(String rideId) {
        return FirebaseProvider.db().collection(FirestorePaths.LIVE_LOCATIONS).document(rideId)
                .collection(FirestorePaths.MEMBERS);
    }

    public Task<Void> update(String rideId, String uid, String name, double lat, double lng, float accuracy, float bearing) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("lat", lat);
        m.put("lng", lng);
        m.put("accuracy", accuracy);
        m.put("bearing", bearing);
        m.put("updatedAt", FieldValue.serverTimestamp());
        return members(rideId).document(uid).set(m);
    }

    public Task<Void> remove(String rideId, String uid) { return members(rideId).document(uid).delete(); }

    public LiveData<Resource<List<LiveLocation>>> observe(String rideId) {
        return FirestoreLiveData.query(members(rideId), LiveLocation.class);
    }
}