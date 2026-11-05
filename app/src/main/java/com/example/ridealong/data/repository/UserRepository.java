package com.example.ridealong.data.repository;

import android.util.LruCache;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.ridealong.data.local.AppDatabase;
import com.example.ridealong.data.local.CacheMapper;
import com.example.ridealong.data.local.CachedUserEntity;
import com.example.ridealong.data.model.EmergencyContact;
import com.example.ridealong.data.model.PrivateInfo;
import com.example.ridealong.data.model.User;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.remote.FirestoreLiveData;
import com.example.ridealong.data.remote.FirestorePaths;
import com.example.ridealong.utils.AppExecutors;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Public profiles (users/{uid}) and the owner-only private document; cached in Room + memory. */
public class UserRepository {
    private static final long MEMORY_TTL_MS = 5 * 60_000L;

    private final AppDatabase db;
    private final LruCache<String, User> memory = new LruCache<>(200);
    private final Map<String, Long> memoryTimes = new HashMap<>();

    public UserRepository(AppDatabase db) { this.db = db; }

    /** Profile form input. photoBase64 null = no photo. */
    public static final class ProfileInput {
        public String fullName;
        public String organization;
        public String gender;
        public String photoBase64;
        public String phone;
        public String email;
        public List<EmergencyContact> emergencyContacts = new ArrayList<>();
    }

    private DocumentReference userRef(String uid) {
        return FirebaseProvider.db().collection(FirestorePaths.USERS).document(uid);
    }

    private DocumentReference privateRef(String uid) {
        return userRef(uid).collection(FirestorePaths.PRIVATE).document(FirestorePaths.PRIVATE_INFO);
    }

    public LiveData<Resource<User>> observeUser(String uid, boolean isSelf) {
        return FirestoreLiveData.doc(userRef(uid), User.class, u -> {
            putMemory(u);
            if (isSelf) cacheSelf(u, null);
        });
    }

    public LiveData<Resource<PrivateInfo>> observePrivate(String uid) {
        return FirestoreLiveData.doc(privateRef(uid), PrivateInfo.class);
    }

    public LiveData<CachedUserEntity> observeCached(String uid) { return db.users().observe(uid); }

    /** Profile with a 5-minute memory cache (ride cards, chat headers). */
    public Task<User> getUser(String uid) {
        User cached = getMemory(uid);
        if (cached != null) return Tasks.forResult(cached);
        return userRef(uid).get().continueWith(t -> {
            if (!t.isSuccessful() || t.getResult() == null || !t.getResult().exists()) return null;
            User u = t.getResult().toObject(User.class);
            putMemory(u);
            return u;
        });
    }

    @Nullable
    public User peekCached(String uid) { return getMemory(uid); }

    public Task<PrivateInfo> getPrivate(String uid) {
        return privateRef(uid).get().continueWith(AppExecutors.io(), t -> {
            if (!t.isSuccessful() || t.getResult() == null || !t.getResult().exists()) {
                // Offline: fall back to Room
                CachedUserEntity e = db.users().get(uid);
                return e == null ? null : CacheMapper.toPrivate(e);
            }
            PrivateInfo p = t.getResult().toObject(PrivateInfo.class);
            // Keep phone + emergency contacts in Room so SOS works offline.
            CachedUserEntity e = db.users().get(uid);
            if (e != null && p != null) {
                e.phone = p.phone;
                e.email = p.email;
                e.emergencyContactsJson = CacheMapper.contactsToJson(p.emergencyContacts);
                db.users().upsert(e);
            }
            return p;
        });
    }

    /** true if the user document exists and the profile setup was finished. */
    public Task<Boolean> isProfileComplete(String uid) {
        return userRef(uid).get().continueWith(AppExecutors.io(), t -> {
            if (t.isSuccessful() && t.getResult() != null) {
                DocumentSnapshot d = t.getResult();
                if (!d.exists()) return false;
                Boolean done = d.getBoolean("profileComplete");
                User u = d.toObject(User.class);
                if (u != null) cacheSelf(u, null);
                return done != null && done;
            }
            // Offline: trust the Room cache
            CachedUserEntity e = db.users().get(uid);
            return e != null && e.profileComplete;
        });
    }

    /**
     * Creates or updates the profile. Creation writes zeroed counters (required by rules);
     * updates only touch the self-editable keys so rules' hasOnly() check passes.
     */
    public Task<Void> saveProfile(String uid, ProfileInput in) {
        DocumentReference ref = userRef(uid);
        return ref.get().onSuccessTask(snap -> {
            WriteBatch batch = FirebaseProvider.db().batch();
            String name = in.fullName.trim();
            if (snap == null || !snap.exists()) {
                Map<String, Object> m = new HashMap<>();
                m.put("uid", uid);
                m.put("fullName", name);
                m.put("fullNameLower", name.toLowerCase(Locale.ROOT));
                m.put("organization", emptyToNull(in.organization));
                m.put("gender", emptyToNull(in.gender));
                if (in.photoBase64 != null) m.put("photoBase64", in.photoBase64);
                m.put("ratingSum", 0);
                m.put("ratingCount", 0);
                m.put("completedRides", 0);
                m.put("profileComplete", true);
                m.put("createdAt", FieldValue.serverTimestamp());
                m.put("updatedAt", FieldValue.serverTimestamp());
                batch.set(ref, m);
            } else {
                Map<String, Object> m = new HashMap<>();
                m.put("fullName", name);
                m.put("fullNameLower", name.toLowerCase(Locale.ROOT));
                m.put("organization", emptyToNull(in.organization));
                m.put("gender", emptyToNull(in.gender));
                m.put("photoBase64", in.photoBase64 == null ? FieldValue.delete() : in.photoBase64);
                m.put("profileComplete", true);
                m.put("updatedAt", FieldValue.serverTimestamp());
                batch.update(ref, m);
            }
            Map<String, Object> p = new HashMap<>();
            p.put("phone", in.phone);
            p.put("email", in.email);
            List<Map<String, Object>> contacts = new ArrayList<>();
            for (EmergencyContact c : in.emergencyContacts) {
                Map<String, Object> cm = new HashMap<>();
                cm.put("name", c.name);
                cm.put("phone", c.phone);
                contacts.add(cm);
            }
            p.put("emergencyContacts", contacts);
            p.put("updatedAt", FieldValue.serverTimestamp());
            batch.set(privateRef(uid), p, SetOptions.merge());
            return batch.commit();
        }).addOnSuccessListener(v -> {
            memory.remove(uid);
            User u = new User();
            u.id = uid;
            u.uid = uid;
            u.fullName = in.fullName.trim();
            u.organization = in.organization;
            u.gender = in.gender;
            u.photoBase64 = in.photoBase64;
            u.profileComplete = true;
            CachedUserEntity old = null;
            PrivateInfo pi = new PrivateInfo();
            pi.phone = in.phone;
            pi.email = in.email;
            pi.emergencyContacts = in.emergencyContacts;
            AppExecutors.io().execute(() -> {
                CachedUserEntity prev = db.users().get(uid);
                if (prev != null) {
                    u.ratingSum = prev.ratingSum;
                    u.ratingCount = prev.ratingCount;
                    u.completedRides = prev.completedRides;
                }
                db.users().upsert(CacheMapper.toEntity(u, pi, true));
            });
        });
    }

    /** Writes the signed-in user's profile (and optionally private info) to Room. */
    public void cacheSelf(User u, @Nullable PrivateInfo p) {
        if (u == null) return;
        AppExecutors.io().execute(() -> {
            CachedUserEntity prev = db.users().get(u.uidOrId());
            CachedUserEntity e = CacheMapper.toEntity(u, p, true);
            if (p == null && prev != null) {
                e.phone = prev.phone;
                e.email = prev.email;
                e.emergencyContactsJson = prev.emergencyContactsJson;
            }
            db.users().upsert(e);
        });
    }

    public void clearMemoryCache() {
        memory.evictAll();
        synchronized (memoryTimes) {
            memoryTimes.clear();
        }
    }

    private void putMemory(User u) {
        if (u == null || u.uidOrId() == null) return;
        memory.put(u.uidOrId(), u);
        synchronized (memoryTimes) {
            memoryTimes.put(u.uidOrId(), System.currentTimeMillis());
        }
    }

    @Nullable
    private User getMemory(String uid) {
        User u = memory.get(uid);
        if (u == null) return null;
        Long t;
        synchronized (memoryTimes) {
            t = memoryTimes.get(uid);
        }
        if (t == null || System.currentTimeMillis() - t > MEMORY_TTL_MS) return null;
        return u;
    }

    private static Object emptyToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
