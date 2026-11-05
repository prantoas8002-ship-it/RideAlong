package com.example.ridealong.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.ridealong.R;
import com.example.ridealong.data.model.AppNotification;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.remote.FirestoreLiveData;
import com.example.ridealong.data.remote.FirestorePaths;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.utils.Constants;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-app notifications stored in Firestore (no Cloud Functions / FCM on the Spark plan).
 * Recipients receive them through a real-time listener and a local Android notification.
 */
public class NotificationRepository {
    private static final String TAG = "NotificationRepo";
    private final Context app;

    public NotificationRepository(Context app) { this.app = app; }

    private CollectionReference col() { return FirebaseProvider.db().collection(FirestorePaths.NOTIFICATIONS); }

    /** Sends one notification. Failures (e.g. recipient blocked the sender) are logged, never thrown at UI. */
    public Task<Void> send(String recipientId, String type, String rideId, String... args) {
        String me = FirebaseProvider.uidOrNull();
        if (me == null || recipientId == null || recipientId.equals(me)) return Tasks.forResult(null);
        String[] tb = render(app, type, Arrays.asList(args));
        Map<String, Object> m = new HashMap<>();
        m.put("userId", recipientId);
        m.put("senderId", me);
        m.put("type", type);
        m.put("title", truncate(tb[0], 120));
        m.put("body", truncate(tb[1], 400));
        m.put("rideId", rideId);
        m.put("args", Arrays.asList(args));
        m.put("read", false);
        m.put("createdAt", FieldValue.serverTimestamp());
        return col().add(m).continueWith(t -> {
            if (!t.isSuccessful()) Log.w(TAG, "notification not delivered: " + type, t.getException());
            return null;
        });
    }

    public void sendToAll(Collection<String> recipients, String type, String rideId, String... args) {
        if (recipients == null) return;
        for (String r : recipients) send(r, type, rideId, args);
    }

    public LiveData<Resource<List<AppNotification>>> observe(String uid) {
        Query q = col().whereEqualTo("userId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(AppConfig.get().notificationPageSize);
        return FirestoreLiveData.query(q, AppNotification.class);
    }

    public Query unreadQuery(String uid) {
        return col().whereEqualTo("userId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(AppConfig.get().notificationPageSize);
    }

    public Task<Void> markRead(String id) { return col().document(id).update("read", true); }

    public Task<Void> markAllRead(List<AppNotification> items) {
        WriteBatch b = FirebaseProvider.db().batch();
        int n = 0;
        for (AppNotification a : items) {
            if (a.read || a.id == null) continue;
            b.update(col().document(a.id), "read", true);
            if (++n >= 450) break;
        }
        return n == 0 ? Tasks.forResult(null) : b.commit();
    }

    public Task<Void> delete(String id) { return col().document(id).delete(); }

    /** Localised {title, body} for a notification type + args (the recipient renders its own language). */
    public static String[] render(Context c, String type, List<String> args) {
        String a0 = arg(args, 0), a1 = arg(args, 1);
        if (type == null) type = "";
        switch (type) {
            case Constants.N_JOIN_REQUEST:
                return new String[]{c.getString(R.string.n_join_request_title), c.getString(R.string.n_join_request_body, a0, a1)};
            case Constants.N_REQUEST_ACCEPTED:
                return new String[]{c.getString(R.string.n_accepted_title), c.getString(R.string.n_accepted_body, a0, a1)};
            case Constants.N_REQUEST_REJECTED:
                return new String[]{c.getString(R.string.n_rejected_title), c.getString(R.string.n_rejected_body, a1)};
            case Constants.N_REQUEST_CANCELLED:
                return new String[]{c.getString(R.string.n_request_cancelled_title), c.getString(R.string.n_request_cancelled_body, a0)};
            case Constants.N_PARTICIPANT_LEFT:
                return new String[]{c.getString(R.string.n_left_title), c.getString(R.string.n_left_body, a0, a1)};
            case Constants.N_NEW_MESSAGE:
            case Constants.N_DIRECT_MESSAGE:
                return new String[]{c.getString(R.string.n_message_title, a0), a1};
            case Constants.N_RIDE_STARTED:
                return new String[]{c.getString(R.string.n_started_title), c.getString(R.string.n_started_body, a1)};
            case Constants.N_RIDE_COMPLETED:
                return new String[]{c.getString(R.string.n_completed_title), c.getString(R.string.n_completed_body, a1)};
            case Constants.N_RIDE_CANCELLED:
                return new String[]{c.getString(R.string.n_cancelled_title), c.getString(R.string.n_cancelled_body, a1)};
            default:
                return new String[]{c.getString(R.string.app_name), a1};
        }
    }

    public static String[] render(Context c, AppNotification n) {
        if (n.args != null && !n.args.isEmpty()) return render(c, n.type, n.args);
        return new String[]{n.title == null ? "" : n.title, n.body == null ? "" : n.body};
    }

    private static String arg(List<String> args, int i) {
        return args != null && args.size() > i && args.get(i) != null ? args.get(i) : "";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public static List<String> argsOf(String... a) { return new ArrayList<>(Arrays.asList(a)); }
}
