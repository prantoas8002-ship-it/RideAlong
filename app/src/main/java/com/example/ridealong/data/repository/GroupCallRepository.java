package com.example.ridealong.data.repository;

import com.example.ridealong.data.model.GroupCall;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.remote.FirestorePaths;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore signaling for group video calls (see firestore.rules → groupCalls):
 * <pre>
 * groupCalls/{id}                         rideId, hostId, invited[], status ACTIVE|ENDED
 *   members/{uid}                         name, session, seenAt (heartbeat)
 *   links/{from}_{to}_{session}           offer (from), answer (to)
 *     fromCandidates / toCandidates       ICE candidates of each side
 * </pre>
 */
public class GroupCallRepository {
    public static final String MEMBERS = "members";
    public static final String LINKS = "links";
    public static final String FROM_CANDIDATES = "fromCandidates";
    public static final String TO_CANDIDATES = "toCandidates";

    private CollectionReference col() { return FirebaseProvider.db().collection(FirestorePaths.GROUP_CALLS); }

    public DocumentReference ref(String id) { return col().document(id); }

    public DocumentReference newCall() { return col().document(); }

    public Task<Void> create(DocumentReference ref, String rideId, String hostName, boolean video, List<String> invited) {
        Map<String, Object> m = new HashMap<>();
        m.put("rideId", rideId);
        m.put("hostId", FirebaseProvider.uidOrNull());
        m.put("hostName", hostName == null ? "" : hostName);
        m.put("status", GroupCall.ACTIVE);
        m.put("video", video);
        m.put("invited", invited);
        m.put("createdAt", FieldValue.serverTimestamp());
        return ref.set(m);
    }

    public Task<Void> end(String callId) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", GroupCall.ENDED);
        m.put("endedAt", FieldValue.serverTimestamp());
        return ref(callId).update(m);
    }

    /** Active group calls I was invited to (ringing + "join" banner in the ride chat). */
    public Query activeForMe(String uid) {
        return col().whereArrayContains("invited", uid).whereEqualTo("status", GroupCall.ACTIVE);
    }

    // ----- members (presence) -----

    public CollectionReference members(String callId) { return ref(callId).collection(MEMBERS); }

    /** Join, or refresh my heartbeat. */
    public Task<Void> heartbeat(String callId, String uid, String name, String session) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name == null ? "" : name);
        m.put("session", session);
        m.put("seenAt", FieldValue.serverTimestamp());
        return members(callId).document(uid).set(m);
    }

    public Task<Void> leave(String callId, String uid) { return members(callId).document(uid).delete(); }

    // ----- links (one peer connection per pair) -----

    public CollectionReference links(String callId) { return ref(callId).collection(LINKS); }

    public static String linkId(String from, String to, String session) { return from + "_" + to + "_" + session; }

    public Task<Void> offer(String callId, String from, String to, String session, String toSession, String type, String sdp) {
        Map<String, Object> m = new HashMap<>();
        m.put("from", from);
        m.put("to", to);
        m.put("session", session);
        m.put("toSession", toSession);
        m.put("offer", sdpMap(type, sdp));
        return links(callId).document(linkId(from, to, session)).set(m);
    }

    public Task<Void> answer(String callId, String linkId, String type, String sdp) {
        Map<String, Object> m = new HashMap<>();
        m.put("answer", sdpMap(type, sdp));
        return links(callId).document(linkId).update(m);
    }

    /** Offers addressed to this join session of mine. */
    public Query offersTo(String callId, String uid, String session) {
        return links(callId).whereEqualTo("to", uid).whereEqualTo("toSession", session);
    }

    public Task<Void> addCandidate(String callId, String linkId, boolean asOfferer, String sdpMid, int sdpMLineIndex, String candidate) {
        Map<String, Object> m = new HashMap<>();
        m.put("sdpMid", sdpMid);
        m.put("sdpMLineIndex", sdpMLineIndex);
        m.put("candidate", candidate);
        return links(callId).document(linkId).collection(asOfferer ? FROM_CANDIDATES : TO_CANDIDATES).add(m)
                .continueWith(t -> null);
    }

    public CollectionReference candidates(String callId, String linkId, boolean ofOfferer) {
        return links(callId).document(linkId).collection(ofOfferer ? FROM_CANDIDATES : TO_CANDIDATES);
    }

    private static Map<String, String> sdpMap(String type, String sdp) {
        Map<String, String> m = new HashMap<>();
        m.put("type", type);
        m.put("sdp", sdp);
        return m;
    }
}
