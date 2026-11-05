package com.example.ridealong.data.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.model.RideRequest;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.remote.FirestoreLiveData;
import com.example.ridealong.data.remote.FirestorePaths;
import com.example.ridealong.domain.model.RequestStatus;
import com.example.ridealong.domain.model.RideStateMachine;
import com.example.ridealong.domain.model.RideStatus;
import com.example.ridealong.domain.validator.SeatValidator;
import com.example.ridealong.utils.Constants;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Join requests and participant management. Accept and leave run in Firestore TRANSACTIONS that
 * update the request and the ride's seats/members together; firestore.rules validate the pair.
 */
public class RequestRepository {
    private final NotificationRepository notifications;
    private final ChatRepository chat;
    private final DirectChatRepository directChats;

    public RequestRepository(NotificationRepository notifications, ChatRepository chat, DirectChatRepository directChats) {
        this.notifications = notifications;
        this.chat = chat;
        this.directChats = directChats;
    }

    /** Optional match details stored with the request so the poster sees compatibility. */
    public static final class MatchInfo {
        public double compatibility;
        public double detourKm;
        public double sharedKm;
    }

    private CollectionReference requests() { return FirebaseProvider.db().collection(FirestorePaths.RIDE_REQUESTS); }

    private DocumentReference rideRef(String rideId) {
        return FirebaseProvider.db().collection(FirestorePaths.RIDES).document(rideId);
    }

    private DocumentReference contactRef(String rideId, String uid) {
        return rideRef(rideId).collection(FirestorePaths.CONTACTS).document(uid);
    }

    public Task<Void> sendRequest(Ride ride, int seats, String message, @Nullable MatchInfo match,
                                  String myName, String myPhone, SafetyRepository safety) {
        String uid = FirebaseProvider.uidOrNull();
        if (uid == null) return fail(DomainException.Reason.NOT_SIGNED_IN);
        if (ride.isOwner(uid)) return fail(DomainException.Reason.SELF_JOIN);
        if (!ride.statusEnum().isJoinable()) return fail(DomainException.Reason.RIDE_CLOSED);
        if (ride.isMember(uid)) return fail(DomainException.Reason.ALREADY_REQUESTED);
        if (!SeatValidator.canReserve(ride.seatsAvailable, seats)) return fail(DomainException.Reason.RIDE_FULL);

        String reqId = FirestorePaths.requestId(ride.id, uid);
        DocumentReference ref = requests().document(reqId);
        return safety.isBlockedBetween(uid, ride.ownerId).onSuccessTask(blocked -> {
            if (Boolean.TRUE.equals(blocked)) return fail(DomainException.Reason.BLOCKED);
            return Tasks.whenAllComplete(ref.get(), directChats.ref(reqId).get());
        }).onSuccessTask(parts -> {
            DocumentSnapshot existing = ((Task<DocumentSnapshot>) parts.get(0)).isSuccessful()
                    ? ((Task<DocumentSnapshot>) parts.get(0)).getResult() : null;
            Task<DocumentSnapshot> chatGet = (Task<DocumentSnapshot>) parts.get(1);
            boolean chatExists = chatGet.isSuccessful() && chatGet.getResult() != null && chatGet.getResult().exists();
            WriteBatch batch = FirebaseProvider.db().batch();
            if (existing != null && existing.exists()) {
                RequestStatus st = RequestStatus.fromName(existing.getString("status"));
                if (st == RequestStatus.PENDING || st == RequestStatus.ACCEPTED) return fail(DomainException.Reason.ALREADY_REQUESTED);
                if (st == RequestStatus.REJECTED) return fail(DomainException.Reason.REQUEST_REJECTED);
                if (st != RequestStatus.CANCELLED) return fail(DomainException.Reason.REQUEST_CHANGED);
                Map<String, Object> m = new HashMap<>();
                m.put("status", RequestStatus.PENDING.name());
                m.put("seats", seats);
                m.put("message", message == null ? "" : message.trim());
                m.put("createdAt", FieldValue.serverTimestamp());
                m.put("updatedAt", FieldValue.serverTimestamp());
                putMatch(m, match);
                batch.update(ref, m);
            } else {
                Map<String, Object> m = new HashMap<>();
                m.put("rideId", ride.id);
                m.put("rideOwnerId", ride.ownerId);
                m.put("requesterId", uid);
                m.put("requesterName", myName);
                m.put("seats", seats);
                m.put("status", RequestStatus.PENDING.name());
                m.put("message", message == null ? "" : message.trim());
                m.put("ridePickupAddress", ride.pickupAddress);
                m.put("rideDropAddress", ride.dropAddress);
                m.put("rideDepartureTime", ride.departureTime);
                m.put("createdAt", FieldValue.serverTimestamp());
                m.put("updatedAt", FieldValue.serverTimestamp());
                putMatch(m, match);
                batch.set(ref, m);
            }
            // The poster needs my phone to call me; rules allow this write only together with my request.
            batch.set(contactRef(ride.id, uid), RideRepository.contactMap(myName, myPhone));
            // Private chat with the poster, available immediately (before acceptance). The request note
            // becomes its first message so the poster can reply from the Messages screen.
            String note = message == null ? "" : message.trim();
            if (!chatExists) {
                Map<String, Object> chatMap = DirectChatRepository.newChat(ride.id, ride.ownerId, ride.ownerName, uid, myName,
                        ride.dropAddress);
                if (!note.isEmpty()) directChats.addFirstMessage(batch, reqId, chatMap, uid, myName, note);
                batch.set(directChats.ref(reqId), chatMap);
            }
            return batch.commit();
        }).addOnSuccessListener(v ->
                notifications.send(ride.ownerId, Constants.N_JOIN_REQUEST, ride.id, myName, ride.dropAddress,
                        message != null && !message.trim().isEmpty() ? "1" : ""));
    }

    private static void putMatch(Map<String, Object> m, @Nullable MatchInfo match) {
        if (match == null) return;
        m.put("compatibility", match.compatibility);
        m.put("detourKm", match.detourKm);
        m.put("sharedKm", match.sharedKm);
    }

    public Task<Void> cancelRequest(RideRequest r, String myName) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", RequestStatus.CANCELLED.name());
        m.put("updatedAt", FieldValue.serverTimestamp());
        return requests().document(r.id).update(m).addOnSuccessListener(v ->
                notifications.send(r.rideOwnerId, Constants.N_REQUEST_CANCELLED, r.rideId, myName, r.rideDropAddress));
    }

    /** Owner accepts: transaction re-reads ride and request so concurrent accepts can't overbook. */
    public Task<Void> accept(RideRequest request, String ownerName) {
        String uid = FirebaseProvider.uidOrNull();
        if (uid == null) return fail(DomainException.Reason.NOT_SIGNED_IN);
        DocumentReference reqRef = requests().document(request.id);
        DocumentReference rideRef = rideRef(request.rideId);
        final String[] dropAddress = {""};
        return FirebaseProvider.db().runTransaction(tx -> {
            Ride ride = tx.get(rideRef).toObject(Ride.class);
            RideRequest req = tx.get(reqRef).toObject(RideRequest.class);
            if (ride == null || req == null) throw new DomainException(DomainException.Reason.NOT_FOUND);
            if (!ride.isOwner(uid)) throw new DomainException(DomainException.Reason.NOT_OWNER);
            if (req.statusEnum() != RequestStatus.PENDING) throw new DomainException(DomainException.Reason.REQUEST_CHANGED);
            if (ride.statusEnum() != RideStatus.OPEN) throw new DomainException(DomainException.Reason.RIDE_CLOSED);
            if (ride.isMember(req.requesterId)) throw new DomainException(DomainException.Reason.ALREADY_REQUESTED);
            if (!SeatValidator.canReserve(ride.seatsAvailable, req.seats)) throw new DomainException(DomainException.Reason.RIDE_FULL);

            int seatsLeft = SeatValidator.seatsAfterAccept(ride.seatsAvailable, req.seats);
            List<String> members = new ArrayList<>(ride.memberIds);
            members.add(req.requesterId);
            RideStatus newStatus = RideStateMachine.statusForSeats(ride.statusEnum(), seatsLeft);

            Map<String, Object> r = new HashMap<>();
            r.put("status", RequestStatus.ACCEPTED.name());
            r.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(reqRef, r);

            Map<String, Object> rm = new HashMap<>();
            rm.put("seatsAvailable", seatsLeft);
            rm.put("memberIds", members);
            rm.put("status", newStatus.name());
            rm.put("lastRequestId", request.id);
            rm.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(rideRef, rm);
            dropAddress[0] = ride.dropAddress;
            return null;
        }).continueWith(t -> {
            if (!t.isSuccessful() && t.getException() != null) throw unwrap(t.getException());
            return (Void) null;
        }).addOnSuccessListener(v -> {
            notifications.send(request.requesterId, Constants.N_REQUEST_ACCEPTED, request.rideId, ownerName, dropAddress[0]);
            chat.sendSystem(request.rideId, request.requesterName == null ? "" : request.requesterName, ChatRepository.SYSTEM_JOINED);
        });
    }

    public Task<Void> reject(RideRequest r, String ownerName) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", RequestStatus.REJECTED.name());
        m.put("updatedAt", FieldValue.serverTimestamp());
        return requests().document(r.id).update(m).addOnSuccessListener(v ->
                notifications.send(r.requesterId, Constants.N_REQUEST_REJECTED, r.rideId, ownerName, r.rideDropAddress));
    }

    /** Accepted participant leaves: request ACCEPTED→LEFT and seats released atomically. */
    public Task<Void> leaveRide(Ride ride, String myName) {
        String uid = FirebaseProvider.uidOrNull();
        if (uid == null) return fail(DomainException.Reason.NOT_SIGNED_IN);
        String reqId = FirestorePaths.requestId(ride.id, uid);
        DocumentReference reqRef = requests().document(reqId);
        DocumentReference rideRef = rideRef(ride.id);
        return FirebaseProvider.db().runTransaction(tx -> {
            Ride cur = tx.get(rideRef).toObject(Ride.class);
            RideRequest req = tx.get(reqRef).toObject(RideRequest.class);
            if (cur == null || req == null) throw new DomainException(DomainException.Reason.NOT_FOUND);
            if (req.statusEnum() != RequestStatus.ACCEPTED || !cur.isParticipant(uid)) {
                throw new DomainException(DomainException.Reason.NOT_MEMBER);
            }
            if (cur.statusEnum() != RideStatus.OPEN && cur.statusEnum() != RideStatus.FULL) {
                throw new DomainException(DomainException.Reason.RIDE_CLOSED);
            }
            int seats = SeatValidator.seatsAfterLeave(cur.seatsAvailable, req.seats, cur.totalSeats);
            List<String> members = new ArrayList<>(cur.memberIds);
            members.remove(uid);

            Map<String, Object> r = new HashMap<>();
            r.put("status", RequestStatus.LEFT.name());
            r.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(reqRef, r);

            Map<String, Object> rm = new HashMap<>();
            rm.put("seatsAvailable", seats);
            rm.put("memberIds", members);
            rm.put("status", RideStatus.OPEN.name());
            rm.put("lastRequestId", reqId);
            rm.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(rideRef, rm);
            return null;
        }).continueWith(t -> {
            if (!t.isSuccessful() && t.getException() != null) throw unwrap(t.getException());
            return (Void) null;
        }).addOnSuccessListener(v ->
                notifications.send(ride.ownerId, Constants.N_PARTICIPANT_LEFT, ride.id, myName, ride.dropAddress));
    }

    public LiveData<Resource<List<RideRequest>>> observeRequestsForRide(String rideId, String ownerId) {
        Query q = requests().whereEqualTo("rideId", rideId).whereEqualTo("rideOwnerId", ownerId)
                .orderBy("createdAt", Query.Direction.DESCENDING);
        return FirestoreLiveData.query(q, RideRequest.class);
    }

    public LiveData<Resource<RideRequest>> observeMyRequest(String rideId, String uid) {
        return FirestoreLiveData.doc(requests().document(FirestorePaths.requestId(rideId, uid)), RideRequest.class);
    }

    public LiveData<Resource<List<RideRequest>>> observePendingForOwner(String uid) {
        Query q = requests().whereEqualTo("rideOwnerId", uid)
                .whereEqualTo("status", RequestStatus.PENDING.name())
                .orderBy("createdAt", Query.Direction.DESCENDING);
        return FirestoreLiveData.query(q, RideRequest.class);
    }

    public LiveData<Resource<List<RideRequest>>> observeMyRequests(String uid) {
        Query q = requests().whereEqualTo("requesterId", uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(50);
        return FirestoreLiveData.query(q, RideRequest.class);
    }

    private static <T> Task<T> fail(DomainException.Reason r) { return Tasks.forException(new DomainException(r)); }

    /** Transactions wrap thrown exceptions; surface our DomainException to the UI. */
    static Exception unwrap(Exception e) {
        Throwable c = e;
        while (c != null) {
            if (c instanceof DomainException) return (DomainException) c;
            c = c.getCause();
        }
        return e;
    }
}
