package com.example.ridealong.data.repository;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.example.ridealong.data.local.AppDatabase;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.remote.FirestorePaths;
import com.example.ridealong.service.LocationShareService;
import com.example.ridealong.service.RealtimeSyncService;
import com.example.ridealong.utils.AppExecutors;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.WriteBatch;

/** Email/password + Google sign-in, password reset (email link), logout and account deletion. */
public class AuthRepository {
    /** Firebase requires a sign-in within roughly the last 5 minutes before deleting an account. */
    private static final long RECENT_LOGIN_MS = 5 * 60_000L;

    private final Context app;
    private final UserRepository users;
    private final AppDatabase db;
    private final SettingsRepository settings;

    public AuthRepository(Context app, UserRepository users, AppDatabase db, SettingsRepository settings) {
        this.app = app;
        this.users = users;
        this.db = db;
        this.settings = settings;
    }

    @Nullable
    public FirebaseUser currentUser() {
        return FirebaseProvider.isAvailable() ? FirebaseProvider.auth().getCurrentUser() : null;
    }

    @Nullable
    public String uid() { return FirebaseProvider.uidOrNull(); }

    public boolean isSignedIn() { return currentUser() != null; }

    public Task<AuthResult> signIn(String email, String password) {
        return FirebaseProvider.auth().signInWithEmailAndPassword(email.trim(), password);
    }

    public Task<AuthResult> register(String fullName, String email, String password) {
        return FirebaseProvider.auth().createUserWithEmailAndPassword(email.trim(), password)
                .onSuccessTask(result -> {
                    FirebaseUser u = result.getUser();
                    if (u == null) return Tasks.forResult(result);
                    return u.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(fullName.trim()).build())
                            .continueWith(t -> result);
                });
    }

    /** Password reset is a LINK emailed by Firebase (not a code). */
    public Task<Void> sendPasswordReset(String email) {
        return FirebaseProvider.auth().sendPasswordResetEmail(email.trim());
    }

    /**
     * The web client id is generated from google-services.json as R.string.default_web_client_id.
     * It is looked up dynamically so the project compiles before Firebase is configured.
     */
    @Nullable
    public String webClientId() {
        int id = app.getResources().getIdentifier("default_web_client_id", "string", app.getPackageName());
        return id == 0 ? null : app.getString(id);
    }

    @Nullable
    public GoogleSignInClient googleClient(Activity activity) {
        String clientId = webClientId();
        if (clientId == null) return null;
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(clientId)
                .requestEmail()
                .build();
        return GoogleSignIn.getClient(activity, gso);
    }

    public Task<AuthResult> signInWithGoogle(Intent data) {
        return GoogleSignIn.getSignedInAccountFromIntent(data).onSuccessTask(account -> {
            AuthCredential cred = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            return FirebaseProvider.auth().signInWithCredential(cred);
        });
    }

    public boolean isPasswordUser() {
        FirebaseUser u = currentUser();
        if (u == null) return false;
        for (com.google.firebase.auth.UserInfo info : u.getProviderData()) {
            if (EmailAuthProvider.PROVIDER_ID.equals(info.getProviderId())) return true;
        }
        return false;
    }

    public Task<Void> reauthenticateWithPassword(String password) {
        FirebaseUser u = currentUser();
        if (u == null || u.getEmail() == null) return Tasks.forException(new DomainException(DomainException.Reason.NOT_SIGNED_IN));
        return u.reauthenticate(EmailAuthProvider.getCredential(u.getEmail(), password));
    }

    public void signOut() {
        stopServices();
        // Mark offline while still signed in (RTDB rules only let a user write their own status).
        com.example.ridealong.ServiceLocator.get().presence().goOffline();
        com.example.ridealong.service.push.PushTokens.unregisterCurrentDevice();
        FirebaseUser u = currentUser();
        if (u != null) {
            try {
                GoogleSignInClient c = GoogleSignIn.getClient(app, GoogleSignInOptions.DEFAULT_SIGN_IN);
                c.signOut();
            } catch (Exception ignored) {
                // Google Play services unavailable: nothing to sign out of.
            }
            FirebaseProvider.auth().signOut();
        }
        settings.clearUserScoped();
        users.clearMemoryCache();
        AppExecutors.io().execute(db::clearAllTables);
    }

    private void stopServices() {
        try {
            app.stopService(new Intent(app, LocationShareService.class));
            app.stopService(new Intent(app, RealtimeSyncService.class));
        } catch (Exception ignored) {
            // services were not running
        }
    }

    /**
     * Deletes users/{uid}/private/info and users/{uid}, then the Auth account. Requires a recent
     * sign-in; otherwise fails with FirebaseAuthRecentLoginRequiredException BEFORE deleting data.
     */
    public Task<Void> deleteAccount() {
        FirebaseUser u = currentUser();
        if (u == null) return Tasks.forException(new DomainException(DomainException.Reason.NOT_SIGNED_IN));
        long last = u.getMetadata() == null ? 0 : u.getMetadata().getLastSignInTimestamp();
        if (System.currentTimeMillis() - last > RECENT_LOGIN_MS) {
            return Tasks.forException(new FirebaseAuthRecentLoginRequiredException("recent-login", "Recent login required"));
        }
        String uid = u.getUid();
        WriteBatch batch = FirebaseProvider.db().batch();
        batch.delete(FirebaseProvider.db().collection(FirestorePaths.USERS).document(uid)
                .collection(FirestorePaths.PRIVATE).document(FirestorePaths.PRIVATE_INFO));
        batch.delete(FirebaseProvider.db().collection(FirestorePaths.USERS).document(uid));
        return batch.commit()
                .onSuccessTask(v -> u.delete())
                .addOnSuccessListener(v -> {
                    stopServices();
                    settings.clearUserScoped();
                    users.clearMemoryCache();
                    AppExecutors.io().execute(db::clearAllTables);
                });
    }
}
