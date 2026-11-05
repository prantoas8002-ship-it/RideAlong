package com.example.ridealong.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.example.ridealong.data.model.Block;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.remote.FirestoreLiveData;
import com.example.ridealong.data.remote.FirestorePaths;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Block and report. Rules also enforce blocks on requests and notifications. */
public class SafetyRepository {
    private volatile Set<String> hidden = Collections.emptySet();
    private MediatorLiveData<Set<String>> hiddenLive;
    private String hiddenFor;

    private CollectionReference blocks() { return FirebaseProvider.db().collection(FirestorePaths.BLOCKS); }

    public Task<Void> block(String otherId, String otherName) {
        String me = FirebaseProvider.uidOrNull();
        if (me == null) return Tasks.forException(new DomainException(DomainException.Reason.NOT_SIGNED_IN));
        Map<String, Object> m = new HashMap<>();
        m.put("blockerId", me);
        m.put("blockedId", otherId);
        m.put("blockedName", otherName);
        m.put("createdAt", FieldValue.serverTimestamp());
        return blocks().document(FirestorePaths.blockId(me, otherId)).set(m);
    }

    public Task<Void> unblock(String otherId) {
        String me = FirebaseProvider.uidOrNull();
        if (me == null) return Tasks.forException(new DomainException(DomainException.Reason.NOT_SIGNED_IN));
        return blocks().document(FirestorePaths.blockId(me, otherId)).delete();
    }

    /** true if either user blocked the other. */
    public Task<Boolean> isBlockedBetween(String a, String b) {
        Task<DocumentSnapshot> t1 = blocks().document(FirestorePaths.blockId(a, b)).get();
        Task<DocumentSnapshot> t2 = blocks().document(FirestorePaths.blockId(b, a)).get();
        return Tasks.whenAllComplete(t1, t2).continueWith(t ->
                (t1.isSuccessful() && t1.getResult().exists()) || (t2.isSuccessful() && t2.getResult().exists()));
    }

    public LiveData<Resource<List<Block>>> observeMyBlocks(String uid) {
        return FirestoreLiveData.query(blocks().whereEqualTo("blockerId", uid), Block.class);
    }

    /** Ids of users I blocked or who blocked me: their rides and requests are hidden. */
    public synchronized LiveData<Set<String>> observeHiddenUserIds(String uid) {
        if (hiddenLive != null && uid.equals(hiddenFor)) return hiddenLive;
        hiddenFor = uid;
        hiddenLive = new MediatorLiveData<>();
        final Set<String>[] parts = new Set[]{new HashSet<>(), new HashSet<>()};
        LiveData<Resource<List<Block>>> mine = FirestoreLiveData.query(blocks().whereEqualTo("blockerId", uid), Block.class);
        LiveData<Resource<List<Block>>> theirs = FirestoreLiveData.query(blocks().whereEqualTo("blockedId", uid), Block.class);
        hiddenLive.addSource(mine, r -> {
            if (r == null || r.data == null) return;
            Set<String> s = new HashSet<>();
            for (Block b : r.data) s.add(b.blockedId);
            parts[0] = s;
            publish(parts);
        });
        hiddenLive.addSource(theirs, r -> {
            if (r == null || r.data == null) return;
            Set<String> s = new HashSet<>();
            for (Block b : r.data) s.add(b.blockerId);
            parts[1] = s;
            publish(parts);
        });
        return hiddenLive;
    }

    private void publish(Set<String>[] parts) {
        Set<String> all = new HashSet<>(parts[0]);
        all.addAll(parts[1]);
        hidden = Collections.unmodifiableSet(all);
        hiddenLive.setValue(hidden);
    }

    /** Snapshot of hidden ids for synchronous filtering. */
    public Set<String> hiddenUserIds() { return hidden; }

    public Task<Void> report(String reportedId, String reason, String details, String rideId) {
        String me = FirebaseProvider.uidOrNull();
        if (me == null) return Tasks.forException(new DomainException(DomainException.Reason.NOT_SIGNED_IN));
        Map<String, Object> m = new HashMap<>();
        m.put("reporterId", me);
        m.put("reportedId", reportedId);
        m.put("reason", reason);
        m.put("details", details == null ? "" : details);
        m.put("rideId", rideId);
        m.put("createdAt", FieldValue.serverTimestamp());
        return FirebaseProvider.db().collection(FirestorePaths.REPORTS).add(m).continueWith(t -> {
            if (!t.isSuccessful() && t.getException() != null) throw t.getException();
            return null;
        });
    }
}
