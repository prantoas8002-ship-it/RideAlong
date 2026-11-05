package com.example.ridealong.data.repository;

import androidx.lifecycle.LiveData;

import com.example.ridealong.data.local.AppDatabase;
import com.example.ridealong.data.local.CacheMapper;
import com.example.ridealong.data.local.CachedMessageEntity;
import com.example.ridealong.data.model.ChatMessage;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.remote.FirestoreLiveData;
import com.example.ridealong.data.remote.FirestorePaths;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.utils.AppExecutors;
import com.example.ridealong.utils.Constants;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One chat per ride (members only, enforced by rules). History is kept and cached in Room. */
public class ChatRepository {
    /** System message markers rendered locally in the reader's language. */
    public static final String SYSTEM_JOINED = "joined";

    private final AppDatabase db;
    private final NotificationRepository notifications;

    public ChatRepository(AppDatabase db, NotificationRepository notifications) {
        this.db = db;
        this.notifications = notifications;
    }

    private CollectionReference messages(String rideId) {
        return FirebaseProvider.db().collection(FirestorePaths.CHATS).document(rideId).collection(FirestorePaths.MESSAGES);
    }

    private DocumentReference readState(String rideId, String uid) {
        return FirebaseProvider.db().collection(FirestorePaths.CHATS).document(rideId)
                .collection(FirestorePaths.READ_STATES).document(uid);
    }

    public LiveData<Resource<List<ChatMessage>>> observeMessages(String rideId) {
        Query q = messages(rideId).orderBy("createdAt").limitToLast(AppConfig.get().chatPageSize);
        return FirestoreLiveData.query(q, ChatMessage.class, list -> AppExecutors.io().execute(() -> {
            List<CachedMessageEntity> e = new ArrayList<>();
            for (ChatMessage m : list) if (m.id != null) e.add(CacheMapper.toEntity(m, rideId));
            db.messages().upsertAll(e);
        }));
    }

    /** Offline fallback (call off the main thread). */
    public List<ChatMessage> cachedMessages(String rideId) {
        List<ChatMessage> out = new ArrayList<>();
        for (CachedMessageEntity e : db.messages().getForRide(rideId)) out.add(CacheMapper.toMessage(e));
        return out;
    }

    public Task<Void> send(Ride ride, String text, String myName) {
        String uid = FirebaseProvider.uidOrNull();
        if (uid == null) return Tasks.forException(new DomainException(DomainException.Reason.NOT_SIGNED_IN));
        if (!ride.isMember(uid)) return Tasks.forException(new DomainException(DomainException.Reason.NOT_MEMBER));
        String trimmed = text.trim();
        Task<Void> write = write(ride.id, uid, myName, trimmed, Constants.MSG_TEXT);
        markRead(ride.id, uid);
        // Firestore writes are queued offline, so notify optimistically once the write is accepted locally.
        String preview = trimmed.length() > 120 ? trimmed.substring(0, 119) + "…" : trimmed;
        for (String m : ride.memberIds) {
            if (!m.equals(uid)) notifications.send(m, Constants.N_NEW_MESSAGE, ride.id, myName, preview);
        }
        return write;
    }

    /** "Zed called Rafi" system-style entry in the chat history. */
    public Task<Void> logCall(String rideId, String callerName, String calleeName, String renderedText) {
        String uid = FirebaseProvider.uidOrNull();
        if (uid == null) return Tasks.forResult(null);
        return write(rideId, uid, callerName, renderedText, Constants.MSG_CALL);
    }

    /** System message, e.g. "<name> joined the ride". The text is the subject name; rendered per reader. */
    public Task<Void> sendSystem(String rideId, String subjectName, String marker) {
        String uid = FirebaseProvider.uidOrNull();
        if (uid == null) return Tasks.forResult(null);
        return write(rideId, uid, subjectName, marker + ":" + subjectName, Constants.MSG_SYSTEM);
    }

    private Task<Void> write(String rideId, String uid, String senderName, String text, String type) {
        Map<String, Object> m = new HashMap<>();
        m.put("senderId", uid);
        m.put("senderName", senderName == null ? "" : senderName);
        m.put("text", text);
        m.put("type", type);
        m.put("createdAt", FieldValue.serverTimestamp());
        return messages(rideId).add(m).continueWith(t -> {
            if (!t.isSuccessful() && t.getException() != null) throw t.getException();
            return null;
        });
    }

    public Task<Void> markRead(String rideId, String uid) {
        Map<String, Object> m = new HashMap<>();
        m.put("lastReadAt", FieldValue.serverTimestamp());
        return readState(rideId, uid).set(m);
    }

    /** Messages newer than my last read marker (aggregate count query: one cheap read). */
    public Task<Long> unreadCount(String rideId, String uid) {
        return readState(rideId, uid).get().onSuccessTask(snap -> {
            Timestamp last = snap != null && snap.exists() ? snap.getTimestamp("lastReadAt") : null;
            Query q = messages(rideId);
            if (last != null) q = q.whereGreaterThan("createdAt", last);
            return q.count().get(AggregateSource.SERVER);
        }).continueWith(t -> t.isSuccessful() && t.getResult() != null ? t.getResult().getCount() : 0L);
    }
}
