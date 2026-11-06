package com.example.ridealong.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.ridealong.data.remote.FirebaseProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Online / last-seen presence on the Firebase <b>Realtime Database</b>: {@code /status/{uid}} =
 * {@code {state: "online"|"offline", lastChanged: <server time>}}.
 * <p>
 * Uses the RTDB-only features Firestore lacks: the special {@code .info/connected} path tells the
 * client when its socket is up, and {@code onDisconnect()} makes the SERVER write "offline" if the
 * app is killed or loses its connection, so presence never gets stuck on "online".
 */
public class PresenceRepository {
    public static final String STATUS = "status";

    public static final class Presence {
        public final boolean online;
        /** Server time in ms of the last change; 0 if unknown. */
        public final long lastChanged;

        Presence(boolean online, long lastChanged) {
            this.online = online;
            this.lastChanged = lastChanged;
        }
    }

    private DatabaseReference connectedRef;
    private ValueEventListener connectedListener;
    private DatabaseReference myStatus;

    /** Call when the app comes to the foreground with a signed-in user. */
    public synchronized void goOnline(@NonNull String uid) {
        FirebaseDatabase db = FirebaseProvider.rtdb();
        if (db == null) return;
        if (myStatus != null && myStatus.getKey() != null && myStatus.getKey().equals(uid) && connectedListener != null) return;
        stopListening();
        db.goOnline();
        myStatus = db.getReference(STATUS).child(uid);
        connectedRef = db.getReference(".info/connected");
        DatabaseReference ref = myStatus;
        connectedListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                if (!Boolean.TRUE.equals(snap.getValue(Boolean.class))) return;
                // Register the server-side "offline" write first, then mark online.
                ref.onDisconnect().setValue(status(false))
                        .addOnSuccessListener(v -> ref.setValue(status(true)));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        connectedRef.addValueEventListener(connectedListener);
    }

    /** Call when the app goes to the background or the user signs out. */
    public synchronized void goOffline() {
        DatabaseReference ref = myStatus;
        stopListening();
        myStatus = null;
        if (ref == null) return;
        ref.setValue(status(false));
        ref.onDisconnect().cancel();
    }

    private void stopListening() {
        if (connectedRef != null && connectedListener != null) connectedRef.removeEventListener(connectedListener);
        connectedRef = null;
        connectedListener = null;
    }

    private static Map<String, Object> status(boolean online) {
        Map<String, Object> m = new HashMap<>();
        m.put("state", online ? "online" : "offline");
        m.put("lastChanged", ServerValue.TIMESTAMP);
        return m;
    }

    /** Live presence of another user; emits null when unknown or presence is unavailable. */
    public LiveData<Presence> observe(@Nullable String uid) {
        return new PresenceLiveData(uid);
    }

    private static final class PresenceLiveData extends LiveData<Presence> {
        private final DatabaseReference ref;
        private final ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) {
                    setValue(null);
                    return;
                }
                String state = snap.child("state").getValue(String.class);
                Long changed = snap.child("lastChanged").getValue(Long.class);
                setValue(new Presence("online".equals(state), changed == null ? 0 : changed));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setValue(null);
            }
        };

        PresenceLiveData(@Nullable String uid) {
            FirebaseDatabase db = FirebaseProvider.rtdb();
            ref = db == null || uid == null || uid.isEmpty() ? null : db.getReference(STATUS).child(uid);
        }

        @Override
        protected void onActive() {
            if (ref != null) ref.addValueEventListener(listener);
        }

        @Override
        protected void onInactive() {
            if (ref != null) ref.removeEventListener(listener);
        }
    }
}
