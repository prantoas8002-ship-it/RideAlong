package com.example.ridealong.data.repository;

import com.example.ridealong.data.model.CallSession;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.remote.FirestorePaths;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.Map;

/** Firestore signaling for in-app calls: offer/answer on calls/{id}, ICE candidates in subcollections. */
public class CallRepository {
    public static final String CALLER_CANDIDATES = "callerCandidates";
    public static final String CALLEE_CANDIDATES = "calleeCandidates";

    private CollectionReference col() { return FirebaseProvider.db().collection(FirestorePaths.CALLS); }

    public DocumentReference newCall() { return col().document(); }

    public DocumentReference ref(String id) { return col().document(id); }

    public Task<Void> create(DocumentReference ref, String calleeId, String calleeName, String myName, String rideId,
                             String chatId, boolean video, String sdpType, String sdp) {
        Map<String, Object> m = new HashMap<>();
        m.put("callerId", FirebaseProvider.uidOrNull());
        m.put("callerName", myName == null ? "" : myName);
        m.put("calleeId", calleeId);
        m.put("calleeName", calleeName == null ? "" : calleeName);
        m.put("rideId", rideId);
        m.put("chatId", chatId == null ? "" : chatId);
        m.put("status", CallSession.RINGING);
        m.put("video", video);
        m.put("offer", sdpMap(sdpType, sdp));
        m.put("createdAt", FieldValue.serverTimestamp());
        return ref.set(m);
    }

    public Task<Void> answer(String callId, String sdpType, String sdp) {
        Map<String, Object> m = new HashMap<>();
        m.put("answer", sdpMap(sdpType, sdp));
        m.put("status", CallSession.ACCEPTED);
        m.put("answeredAt", FieldValue.serverTimestamp());
        return ref(callId).update(m);
    }

    public Task<Void> setStatus(String callId, String status) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("endedAt", FieldValue.serverTimestamp());
        return ref(callId).update(m);
    }

    public Task<Void> addCandidate(String callId, boolean asCaller, String sdpMid, int sdpMLineIndex, String candidate) {
        Map<String, Object> m = new HashMap<>();
        m.put("sdpMid", sdpMid);
        m.put("sdpMLineIndex", sdpMLineIndex);
        m.put("candidate", candidate);
        return ref(callId).collection(asCaller ? CALLER_CANDIDATES : CALLEE_CANDIDATES).add(m).continueWith(t -> null);
    }

    public CollectionReference candidates(String callId, boolean ofCaller) {
        return ref(callId).collection(ofCaller ? CALLER_CANDIDATES : CALLEE_CANDIDATES);
    }

    /** Calls currently ringing for me. */
    public Query incoming(String uid) {
        return col().whereEqualTo("calleeId", uid).whereEqualTo("status", CallSession.RINGING);
    }

    private static Map<String, String> sdpMap(String type, String sdp) {
        Map<String, String> m = new HashMap<>();
        m.put("type", type);
        m.put("sdp", sdp);
        return m;
    }
}