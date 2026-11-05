package com.example.ridealong.data.repository;

import androidx.lifecycle.LiveData;

import com.example.ridealong.data.local.AppDatabase;
import com.example.ridealong.data.local.CacheMapper;
import com.example.ridealong.data.local.CachedRideEntity;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.model.RideContact;
import com.example.ridealong.data.model.RideRequest;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.remote.FirestoreLiveData;
import com.example.ridealong.data.remote.FirestorePaths;
import com.example.ridealong.domain.algorithm.GeoHash;
import com.example.ridealong.domain.algorithm.Haversine;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.domain.model.RequestStatus;
import com.example.ridealong.domain.model.RideStateMachine;
import com.example.ridealong.domain.model.RideStatus;
import com.example.ridealong.utils.AppExecutors;
import com.example.ridealong.utils.Constants;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rides: posting, geohash discovery, my rides, lifecycle (start / complete / cancel). */
public class RideRepository {
    public static final int GEOHASH_PRECISION = 9;

    private final AppDatabase db;
    private final NotificationRepository notifications;
    private final SafetyRepository safety;

    public RideRepository(AppDatabase db, NotificationRepository notifications, SafetyRepository safety) {
        this.db = db;
        this.notifications = notifications;
        this.safety = safety;
    }

    /** Everything the Post Ride form produces (already validated). */
    public static final class RideDraft {
        public GeoCoord pickup;
        public String pickupAddress;
        public GeoCoord drop;
        public String dropAddress;
        public long departureMillis;
        public double costPerPerson;
        public int seats;
        public double maxDetourKm;
        public String comment;
        public String transportType;
        public String routePolyline;
        public double routeDistanceKm;
        public int routeDurationMin;
        public boolean routeApproximate;
        public String ownerName;
        public String ownerPhone;
    }

    private CollectionReference rides() { return FirebaseProvider.db().collection(FirestorePaths.RIDES); }

    private DocumentReference contactRef(String rideId, String uid) {
        return rides().document(rideId).collection(FirestorePaths.CONTACTS).document(uid);
    }

    public Task<String> postRide(RideDraft d) {
        String uid = FirebaseProvider.uidOrNull();
        if (uid == null) return Tasks.forException(new DomainException(DomainException.Reason.NOT_SIGNED_IN));
        DocumentReference ref = rides().document();
        Map<String, Object> m = new HashMap<>();
        m.put("ownerId", uid);
        m.put("ownerName", d.ownerName);
        m.put("pickupLat", d.pickup.lat);
        m.put("pickupLng", d.pickup.lng);
        m.put("pickupAddress", d.pickupAddress);
        m.put("pickupGeohash", GeoHash.encode(d.pickup, GEOHASH_PRECISION));
        m.put("dropLat", d.drop.lat);
        m.put("dropLng", d.drop.lng);
        m.put("dropAddress", d.dropAddress);
        m.put("dropGeohash", GeoHash.encode(d.drop, GEOHASH_PRECISION));
        m.put("departureTime", new Timestamp(new Date(d.departureMillis)));
        m.put("costPerPerson", d.costPerPerson);
        m.put("totalSeats", d.seats);
        m.put("seatsAvailable", d.seats);
        m.put("maxDetourKm", d.maxDetourKm);
        m.put("comment", d.comment == null ? "" : d.comment.trim());
        m.put("transportType", d.transportType);
        m.put("status", RideStatus.OPEN.name());
        m.put("memberIds", Collections.singletonList(uid));
        m.put("routePolyline", d.routePolyline == null ? "" : d.routePolyline);
        m.put("routeDistanceKm", d.routeDistanceKm);
        m.put("routeDurationMin", d.routeDurationMin);
        m.put("routeApproximate", d.routeApproximate);
        m.put("createdAt", FieldValue.serverTimestamp());
        m.put("updatedAt", FieldValue.serverTimestamp());

        WriteBatch batch = FirebaseProvider.db().batch();
        batch.set(ref, m);
        batch.set(contactRef(ref.getId(), uid), contactMap(d.ownerName, d.ownerPhone));
        return batch.commit().continueWith(t -> {
            if (!t.isSuccessful() && t.getException() != null) throw t.getException();
            return ref.getId();
        });
    }

    static Map<String, Object> contactMap(String name, String phone) {
        Map<String, Object> c = new HashMap<>();
        c.put("name", name == null ? "" : name);
        c.put("phone", phone == null ? "" : phone);
        c.put("updatedAt", FieldValue.serverTimestamp());
        return c;
    }

    public LiveData<Resource<Ride>> observeRide(String rideId) {
        return FirestoreLiveData.doc(rides().document(rideId), Ride.class, r -> {
            String uid = FirebaseProvider.uidOrNull();
            if (uid != null && r.isMember(uid)) AppExecutors.io().execute(() -> db.rides().upsert(CacheMapper.toEntity(r, uid)));
        });
    }

    public Task<Ride> getRide(String rideId) {
        return rides().document(rideId).get().continueWith(t -> {
            if (!t.isSuccessful()) throw t.getException();
            DocumentSnapshot d = t.getResult();
            return d != null && d.exists() ? d.toObject(Ride.class) : null;
        });
    }

    /**
     * Nearby OPEN rides: one Firestore range query per geohash cell (center + 8 neighbours),
     * merged, de-duplicated, then filtered by true Haversine distance, departure time and blocks.
     */
    public Task<List<Ride>> nearbyOpenRides(GeoCoord center, double radiusKm) {
        List<String[]> bounds = GeoHash.queryBounds(center, radiusKm);
        List<Task<QuerySnapshot>> tasks = new ArrayList<>();
        for (String[] b : bounds) {
            Query q = rides().whereEqualTo("status", RideStatus.OPEN.name())
                    .orderBy("pickupGeohash")
                    .startAt(b[0]).endAt(b[1])
                    .limit(AppConfig.get().feedQueryLimitPerCell);
            tasks.add(q.get());
        }
        return Tasks.whenAllComplete(tasks).continueWith(AppExecutors.io(), done -> {
            Map<String, Ride> unique = new LinkedHashMap<>();
            Exception firstError = null;
            int ok = 0;
            for (Task<QuerySnapshot> t : tasks) {
                if (!t.isSuccessful()) {
                    if (firstError == null) firstError = t.getException();
                    continue;
                }
                ok++;
                for (DocumentSnapshot d : t.getResult().getDocuments()) {
                    Ride r = d.toObject(Ride.class);
                    if (r != null) unique.put(r.id, r);
                }
            }
            if (ok == 0 && firstError != null) throw firstError;
            long cutoff = System.currentTimeMillis() - AppConfig.get().feedGraceAfterDepartureMs;
            Set<String> hidden = safety.hiddenUserIds();
            List<Ride> out = new ArrayList<>();
            for (Ride r : unique.values()) {
                if (r.departureMillis() < cutoff) continue;
                if (hidden.contains(r.ownerId)) continue;
                if (r.seatsAvailable <= 0) continue;
                if (Haversine.distanceKm(center, r.pickup()) > radiusKm) continue;
                out.add(r);
            }
            return out;
        });
    }

    /** All rides I posted or joined (owner is always in memberIds), newest first; cached to Room. */
    public LiveData<Resource<List<Ride>>> observeMyRides(String uid) {
        Query q = rides().whereArrayContains("memberIds", uid)
                .orderBy("departureTime", Query.Direction.DESCENDING)
                .limit(AppConfig.get().historyPageSize);
        return FirestoreLiveData.query(q, Ride.class, list -> AppExecutors.io().execute(() -> {
            List<CachedRideEntity> entities = new ArrayList<>();
            for (Ride r : list) entities.add(CacheMapper.toEntity(r, uid));
            db.rides().upsertAll(entities);
        }));
    }

    public LiveData<List<CachedRideEntity>> observeCachedRides(String uid) { return db.rides().observeForUser(uid); }

    public Task<RideContact> getContact(String rideId, String userId) {
        return contactRef(rideId, userId).get().continueWith(t -> {
            if (!t.isSuccessful() || t.getResult() == null || !t.getResult().exists()) return null;
            return t.getResult().toObject(RideContact.class);
        });
    }

    /** Re-publish my contact card for a ride (e.g. after editing my phone). */
    public Task<Void> publishMyContact(String rideId, String name, String phone) {
        String uid = FirebaseProvider.uidOrNull();
        if (uid == null) return Tasks.forException(new DomainException(DomainException.Reason.NOT_SIGNED_IN));
        return contactRef(rideId, uid).set(contactMap(name, phone));
    }

    // ---------------- lifecycle ----------------

    private Task<Void> checkOwnerTransition(Ride ride, RideStatus to) {
        String uid = FirebaseProvider.uidOrNull();
        if (uid == null) return Tasks.forException(new DomainException(DomainException.Reason.NOT_SIGNED_IN));
        if (!ride.isOwner(uid)) return Tasks.forException(new DomainException(DomainException.Reason.NOT_OWNER));
        if (!RideStateMachine.canManuallyTransition(ride.statusEnum(), to)) {
            return Tasks.forException(new DomainException(DomainException.Reason.INVALID_TRANSITION));
        }
        return null;
    }

    public Task<Void> startRide(Ride ride) {
        Task<Void> err = checkOwnerTransition(ride, RideStatus.IN_PROGRESS);
        if (err != null) return err;
        Map<String, Object> m = new HashMap<>();
        m.put("status", RideStatus.IN_PROGRESS.name());
        m.put("startedAt", FieldValue.serverTimestamp());
        m.put("updatedAt", FieldValue.serverTimestamp());
        return rides().document(ride.id).update(m).addOnSuccessListener(v ->
                notifications.sendToAll(others(ride), Constants.N_RIDE_STARTED, ride.id, ride.ownerName, ride.dropAddress));
    }

    /**
     * Completes the ride and increments every member's completedRides in ONE batch; rules accept
     * the counter writes only because the same batch moves the ride IN_PROGRESS → COMPLETED.
     */
    public Task<Void> completeRide(Ride ride) {
        Task<Void> err = checkOwnerTransition(ride, RideStatus.COMPLETED);
        if (err != null) return err;
        List<Task<DocumentSnapshot>> userGets = new ArrayList<>();
        for (String m : ride.memberIds) {
            userGets.add(FirebaseProvider.db().collection(FirestorePaths.USERS).document(m).get());
        }
        return Tasks.whenAllComplete(userGets).onSuccessTask(all -> {
            WriteBatch b = FirebaseProvider.db().batch();
            Map<String, Object> m = new HashMap<>();
            m.put("status", RideStatus.COMPLETED.name());
            m.put("completedAt", FieldValue.serverTimestamp());
            m.put("updatedAt", FieldValue.serverTimestamp());
            b.update(rides().document(ride.id), m);
            for (Task<DocumentSnapshot> t : userGets) {
                if (!t.isSuccessful() || t.getResult() == null || !t.getResult().exists()) continue; // deleted account
                Map<String, Object> u = new HashMap<>();
                u.put("completedRides", FieldValue.increment(1));
                u.put("lastCompletedRideId", ride.id);
                b.update(t.getResult().getReference(), u);
            }
            return b.commit();
        }).addOnSuccessListener(v ->
                notifications.sendToAll(others(ride), Constants.N_RIDE_COMPLETED, ride.id, ride.ownerName, ride.dropAddress));
    }

    /** Cancels, declines all pending requests, and notifies participants and pending requesters. */
    public Task<Void> cancelRide(Ride ride, String reason) {
        Task<Void> err = checkOwnerTransition(ride, RideStatus.CANCELLED);
        if (err != null) return err;
        Map<String, Object> m = new HashMap<>();
        m.put("status", RideStatus.CANCELLED.name());
        m.put("cancelReason", reason == null ? "" : reason);
        m.put("updatedAt", FieldValue.serverTimestamp());
        return rides().document(ride.id).update(m).onSuccessTask(v -> declinePendingAndNotify(ride));
    }

    private Task<Void> declinePendingAndNotify(Ride ride) {
        return FirebaseProvider.db().collection(FirestorePaths.RIDE_REQUESTS)
                .whereEqualTo("rideId", ride.id)
                .whereEqualTo("rideOwnerId", ride.ownerId)
                .get()
                .continueWithTask(t -> {
            List<String> recipients = new ArrayList<>(others(ride));
            if (t.isSuccessful() && t.getResult() != null) {
                WriteBatch b = FirebaseProvider.db().batch();
                int n = 0;
                for (DocumentSnapshot d : t.getResult().getDocuments()) {
                    RideRequest r = d.toObject(RideRequest.class);
                    if (r == null || r.statusEnum() != RequestStatus.PENDING) continue;
                    Map<String, Object> u = new HashMap<>();
                    u.put("status", RequestStatus.REJECTED.name());
                    u.put("updatedAt", FieldValue.serverTimestamp());
                    b.update(d.getReference(), u);
                    if (!recipients.contains(r.requesterId)) recipients.add(r.requesterId);
                    n++;
                }
                notifications.sendToAll(recipients, Constants.N_RIDE_CANCELLED, ride.id, ride.ownerName, ride.dropAddress);
                if (n > 0) return b.commit();
            } else {
                notifications.sendToAll(recipients, Constants.N_RIDE_CANCELLED, ride.id, ride.ownerName, ride.dropAddress);
            }
            return Tasks.forResult(null);
        });
    }

    private static List<String> others(Ride ride) {
        String me = FirebaseProvider.uidOrNull();
        List<String> out = new ArrayList<>();
        if (ride.memberIds == null) return out;
        for (String m : ride.memberIds) if (!m.equals(me)) out.add(m);
        return out;
    }
}
