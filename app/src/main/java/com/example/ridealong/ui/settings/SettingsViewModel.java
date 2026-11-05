package com.example.ridealong.ui.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.Block;
import com.example.ridealong.data.repository.Resource;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.ui.rides.SearchSession;
import com.example.ridealong.utils.Event;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;

import java.util.List;

public class SettingsViewModel extends ViewModel {
    public enum AccountEvent { SIGNED_OUT, DELETED, NEEDS_REAUTH }

    private LiveData<Resource<List<Block>>> blocks;
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Event<AccountEvent>> account = new MutableLiveData<>();
    private final MutableLiveData<Event<Throwable>> error = new MutableLiveData<>();

    public LiveData<Resource<List<Block>>> blocks() {
        if (blocks == null) blocks = ServiceLocator.get().safety().observeMyBlocks(Me.uid());
        return blocks;
    }

    public LiveData<Boolean> busy() { return busy; }

    public LiveData<Event<AccountEvent>> account() { return account; }

    public LiveData<Event<Throwable>> error() { return error; }

    public void unblock(String userId) {
        ServiceLocator.get().safety().unblock(userId).addOnFailureListener(e -> error.setValue(new Event<>(e)));
    }

    public void logout() {
        SearchSession.clear();
        ServiceLocator.get().auth().signOut();
        account.setValue(new Event<>(AccountEvent.SIGNED_OUT));
    }

    public void deleteAccount() {
        busy.setValue(true);
        ServiceLocator.get().auth().deleteAccount().addOnCompleteListener(t -> {
            busy.setValue(false);
            if (t.isSuccessful()) {
                SearchSession.clear();
                account.setValue(new Event<>(AccountEvent.DELETED));
            } else if (t.getException() instanceof FirebaseAuthRecentLoginRequiredException) {
                account.setValue(new Event<>(AccountEvent.NEEDS_REAUTH));
            } else {
                error.setValue(new Event<>(t.getException()));
            }
        });
    }

    public void reauthAndDelete(String password) {
        busy.setValue(true);
        ServiceLocator.get().auth().reauthenticateWithPassword(password).addOnCompleteListener(t -> {
            if (t.isSuccessful()) {
                deleteAccount();
            } else {
                busy.setValue(false);
                error.setValue(new Event<>(t.getException()));
            }
        });
    }
}