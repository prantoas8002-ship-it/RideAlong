package com.example.ridealong.ui.auth;

import android.app.Activity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.ui.common.Ui;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;

/** Launches Google Sign-In from a fragment and forwards the result to AuthViewModel. */
final class GoogleSignInHelper {
    private final Fragment fragment;
    private final ActivityResultLauncher<android.content.Intent> launcher;

    GoogleSignInHelper(Fragment fragment, AuthViewModel vm) {
        this.fragment = fragment;
        launcher = fragment.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) vm.googleResult(result.getData());
            else Ui.snack(fragment.getView(), R.string.error_google_failed);
        });
    }

    void launch() {
        GoogleSignInClient client = ServiceLocator.get().auth().googleClient(fragment.requireActivity());
        if (client == null) {
            Ui.snack(fragment.getView(), R.string.error_google_unavailable);
            return;
        }
        // Sign out first so the account chooser is always shown.
        client.signOut().addOnCompleteListener(t -> launcher.launch(client.getSignInIntent()));
    }
}