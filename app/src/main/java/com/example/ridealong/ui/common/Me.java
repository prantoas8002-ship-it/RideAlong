package com.example.ridealong.ui.common;

import androidx.annotation.Nullable;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.User;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.google.firebase.auth.FirebaseUser;

/** The signed-in user's id and display name (used in notifications, chat and requests). */
public final class Me {
    private Me() { }

    @Nullable
    public static String uid() { return FirebaseProvider.uidOrNull(); }

    public static String name() {
        String uid = uid();
        if (uid != null) {
            User u = ServiceLocator.get().users().peekCached(uid);
            if (u != null && !u.displayName().isEmpty()) return u.displayName();
        }
        FirebaseUser fu = ServiceLocator.get().auth().currentUser();
        return fu != null && fu.getDisplayName() != null ? fu.getDisplayName() : "";
    }

    /** Warm the profile cache so name() is accurate. */
    public static void preload() {
        String uid = uid();
        if (uid != null) ServiceLocator.get().users().getUser(uid);
    }
}