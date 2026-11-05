package com.example.ridealong.data.repository;

import androidx.lifecycle.LiveData;

import com.example.ridealong.data.local.AppDatabase;
import com.example.ridealong.data.local.CacheMapper;
import com.example.ridealong.data.local.CachedMessageEntity;
import com.example.ridealong.data.model.ChatMessage;
import com.example.ridealong.data.model.DirectChat;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.remote.FirestoreLiveData;
import com.example.ridealong.data.remote.FirestorePaths;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.utils.AppExecutors;
import com.example.ridealong.utils.Constants;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Private 1-to-1 chat between a ride poster and a requester (directChats/{rideId_requesterId}).
 * Rules allow it only for those two people and only while the join request exists.
 */
public class DirectChatRepository {
    private static final int PREVIEW = 120;
    private final AppDatabase db;
    private final NotificationRepository notifications;

    public DirectChatRepository(AppDatabase db, NotificationRepository notifications) {
        this.db = db;
        this.notifications = notifications;
    }

    public static String chatId(String rideId, String requesterId) { return FirestorePaths.requestId(rideId, requesterId); }

    private CollectionReference col() { return FirebaseProvider.db().collection(FirestorePaths.DIRECT_CHATS); }

    public DocumentReference ref(String chatId) { return col().document(chatId); }

    private CollectionReference messages(String chatId) { return ref(chatId).collection(FirestorePaths.MESSAGES); }

    static String preview(String text) {
        String t = text == null ? "" : text.trim();
        return t.length() > PREVIEW ? t.substring(0, PREVIEW - 1) + "…" : t;
    }

    /** Fields for a new conversation (lastMessageAt set so it sorts into the list immediately). */
    public static Map<String, Object> newChat(String rideId, String ownerId, String ownerName, String requesterId,
                                              String requesterName, String rideDropAddress) {
        Map<String, Object> m = new HashMap<>();
        m.put("rideId", rideId);
        m.put("ownerId", ownerId);
        m.put("ownerName", ownerName == null ? "" : ownerName);
        m.put("requesterId", requesterId);
        m.put("requesterName", requesterName == null ? "" : requesterName);
        m.put("rideDropAddress", rideDropAddress == null ? "" : rideDropAddress);
        m.put("participants", Arrays.asList(ownerId, requesterId));
        m.put("lastMessage", "");
        m.put("lastSenderId", "");
        m.put("lastMessageAt", FieldValue.serverTimestamp());
        m.put("lastReadAt", new HashMap<String, Object>());
        m.put("createdAt", FieldValue.serverTimestamp());
        return m;
    }

    /** Adds a first message (e.g. the join-request note) to a batch that also creates the chat. */
    public void addFirstMessage(WriteBatch b, String chatId, Map<String, Object> chatMap, String me, String myName, String text) {
        b.set(messages(chatId).document(), messageMap(me, myName, text, Constants.MSG_TEXT));
        chatMap.put("lastMessage", preview(text));
        chatMap.put("lastSenderId", me);
    }

    /** Opens (creating if needed) the conversation for this ride + requester. */
    public Task<String> ensureChat(String rideId, String ownerId, String ownerName, String requesterId, String requesterName,
                                   String rideDropAddress) {
        String id = chatId(rideId, requesterId);
        return ref(id).get().continueWithTask(t -> {
            if (t.isSuccessful() && t.getResult() != null && t.getResult().exists()) return Tasks.forResult(id);
            return ref(id).set(newChat(rideId, ownerId, ownerName, requesterId, requesterName, rideDropAddress))
                    .continueWith(s -> {
                        if (!s.isSuccessful() && s.getException() != null) throw s.getException();
                        return id;
                    });
        });
    }

    public LiveData<Resource<List<DirectChat>>> observeMyChats(String uid) {
        Query q = col().whereArrayContains("participants", uid)
                .orderBy("lastMessageAt", Query.Direction.DESCENDING).limit(100);
        return FirestoreLiveData.query(q, DirectChat.class);
    }

    public LiveData<Resource<DirectChat>> observeChat(String chatId) {
        return FirestoreLiveData.doc(ref(chatId), DirectChat.class);
    }

    public LiveData<Resource<List<ChatMessage>>> observeMessages(String chatId) {
        Query q = messages(chatId).orderBy("createdAt").limitToLast(AppConfig.get().chatPageSize);
        return FirestoreLiveData.query(q, ChatMessage.class, list -> AppExecutors.io().execute(() -> {
            List<CachedMessageEntity> e = new ArrayList<>();
            for (ChatMessage m : list) if (m.id != null) e.add(CacheMapper.toEntity(m, cacheKey(chatId)));
            db.messages().upsertAll(e);
        }));
    }

    /** Offline history (call off the main thread). */
    public List<ChatMessage> cachedMessages(String chatId) {
        List<ChatMessage> out = new ArrayList<>();
        for (CachedMessageEntity e : db.messages().getForRide(cacheKey(chatId))) out.add(CacheMapper.toMessage(e));
        return out;
    }

    private static String cacheKey(String chatId) { return "dm:" + chatId; }

    public Task<Void> send(DirectChat chat, String text, String myName) {
        return write(chat, text, myName, Constants.MSG_TEXT, true);
    }

    /** "Zed called Rafi" entry in the private chat history. */
    public Task<Void> logCall(DirectChat chat, String renderedText, String myName) {
        return write(chat, renderedText, myName, Constants.MSG_CALL, false);
    }

    private Task<Void> write(DirectChat chat, String text, String myName, String type, boolean notify) {
        String me = FirebaseProvider.uidOrNull();
        if (me == null) return Tasks.forException(new DomainException(DomainException.Reason.NOT_SIGNED_IN));
        if (chat.participants == null || !chat.participants.contains(me)) {
            return Tasks.forException(new DomainException(DomainException.Reason.NOT_MEMBER));
        }
        String trimmed = text.trim();
        WriteBatch b = FirebaseProvider.db().batch();
        b.set(messages(chat.id).document(), messageMap(me, myName, trimmed, type));
        Map<String, Object> u = new HashMap<>();
        u.put("lastMessage", preview(trimmed));
        u.put("lastSenderId", me);
        u.put("lastMessageAt", FieldValue.serverTimestamp());
        u.put("lastReadAt." + me, FieldValue.serverTimestamp());
        b.update(ref(chat.id), u);
        Task<Void> commit = b.commit();
        if (notify) {
            // "X sent you a message" only; never the text.
            notifications.send(chat.otherId(me), Constants.N_DIRECT_MESSAGE, chat.rideId, myName, "", chat.id,
                    chat.rideDropAddress == null ? "" : chat.rideDropAddress);
        }
        return commit;
    }

    private static Map<String, Object> messageMap(String me, String myName, String text, String type) {
        Map<String, Object> m = new HashMap<>();
        m.put("senderId", me);
        m.put("senderName", myName == null ? "" : myName);
        m.put("text", text);
        m.put("type", type);
        m.put("createdAt", FieldValue.serverTimestamp());
        return m;
    }

    public Task<Void> markRead(String chatId, String uid) {
        Map<String, Object> u = new HashMap<>();
        u.put("lastReadAt." + uid, FieldValue.serverTimestamp());
        return ref(chatId).update(u);
    }
}