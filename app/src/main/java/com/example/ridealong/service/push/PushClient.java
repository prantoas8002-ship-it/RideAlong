package com.example.ridealong.service.push;

import android.util.Log;

import com.example.ridealong.BuildConfig;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.map.HttpClient;
import com.example.ridealong.utils.AppExecutors;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

/**
 * Asks the push relay (push-relay/, a free Cloudflare Worker) to wake the recipient's phone for a
 * call or notification document I just created. The relay re-checks everything server-side, so this
 * only sends {kind, id}. Does nothing when PUSH_RELAY_URL is not configured.
 */
public final class PushClient {
    private static final String TAG = "PushClient";

    private PushClient() { }

    public static boolean isConfigured() { return !BuildConfig.PUSH_RELAY_URL.trim().isEmpty(); }

    public static void send(String kind, String id) {
        if (!isConfigured() || id == null || !FirebaseProvider.isAvailable()) return;
        FirebaseUser u = FirebaseProvider.auth().getCurrentUser();
        if (u == null) return;
        u.getIdToken(false).addOnSuccessListener(result -> AppExecutors.io().execute(() -> {
            try {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + result.getToken());
                HttpClient.post(BuildConfig.PUSH_RELAY_URL.trim(), "{\"kind\":\"" + kind + "\",\"id\":\"" + id + "\"}", h);
            } catch (Exception e) {
                Log.w(TAG, "push relay failed (" + kind + ")", e); // best effort: in-app listeners still deliver
            }
        }));
    }
}
