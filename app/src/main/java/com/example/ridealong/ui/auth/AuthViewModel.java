package com.example.ridealong.ui.auth;

import android.content.Intent;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.repository.AuthRepository;
import com.example.ridealong.utils.Event;

/** Shared by the auth fragments (activity-scoped). */
public class AuthViewModel extends ViewModel {
    private final AuthRepository auth = ServiceLocator.get().auth();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Event<Throwable>> error = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> signedIn = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> resetSent = new MutableLiveData<>();

    public LiveData<Boolean> busy() { return busy; }

    public LiveData<Event<Throwable>> error() { return error; }

    public LiveData<Event<Boolean>> signedIn() { return signedIn; }

    public LiveData<Event<String>> resetSent() { return resetSent; }

    public void signIn(String email, String password) {
        busy.setValue(true);
        auth.signIn(email, password).addOnCompleteListener(t -> {
            busy.setValue(false);
            if (t.isSuccessful()) signedIn.setValue(new Event<>(true));
            else error.setValue(new Event<>(t.getException()));
        });
    }

    public void register(String name, String email, String password) {
        busy.setValue(true);
        auth.register(name, email, password).addOnCompleteListener(t -> {
            busy.setValue(false);
            if (t.isSuccessful()) signedIn.setValue(new Event<>(true));
            else error.setValue(new Event<>(t.getException()));
        });
    }

    public void googleResult(Intent data) {
        busy.setValue(true);
        auth.signInWithGoogle(data).addOnCompleteListener(t -> {
            busy.setValue(false);
            if (t.isSuccessful()) signedIn.setValue(new Event<>(true));
            else error.setValue(new Event<>(t.getException()));
        });
    }

    /** Firebase never reveals whether the account exists; we always show the same neutral message. */
    public void sendReset(String email) {
        busy.setValue(true);
        auth.sendPasswordReset(email).addOnCompleteListener(t -> {
            busy.setValue(false);
            if (t.isSuccessful() || !(t.getException() instanceof com.google.firebase.FirebaseNetworkException)) {
                resetSent.setValue(new Event<>(email.trim()));
            } else {
                error.setValue(new Event<>(t.getException()));
            }
        });
    }
}