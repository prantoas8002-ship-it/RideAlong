package com.example.ridealong.ui.profile;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.PrivateInfo;
import com.example.ridealong.data.model.User;
import com.example.ridealong.data.repository.UserRepository;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.utils.AppExecutors;
import com.example.ridealong.utils.Event;
import com.example.ridealong.utils.ImageUtils;
import com.google.android.gms.tasks.Tasks;

public class EditProfileViewModel extends AndroidViewModel {
    public static final class Loaded {
        public final User user;
        public final PrivateInfo info;

        Loaded(User user, PrivateInfo info) {
            this.user = user;
            this.info = info;
        }
    }

    private final UserRepository users = ServiceLocator.get().users();
    private final MutableLiveData<Loaded> loaded = new MutableLiveData<>();
    private final MutableLiveData<String> photo = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Event<Throwable>> error = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> saved = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> photoFailed = new MutableLiveData<>();
    private boolean started;

    public EditProfileViewModel(@NonNull Application app) { super(app); }

    public LiveData<Loaded> loaded() { return loaded; }

    public LiveData<String> photo() { return photo; }

    public LiveData<Boolean> busy() { return busy; }

    public LiveData<Event<Throwable>> error() { return error; }

    public LiveData<Event<Boolean>> saved() { return saved; }

    public LiveData<Event<Boolean>> photoFailed() { return photoFailed; }

    public void load() {
        if (started) return;
        started = true;
        String uid = ServiceLocator.get().auth().uid();
        if (uid == null) return;
        busy.setValue(true);
        com.google.android.gms.tasks.Task<User> tu = users.getUser(uid);
        com.google.android.gms.tasks.Task<PrivateInfo> tp = users.getPrivate(uid);
        Tasks.whenAllComplete(tu, tp).addOnCompleteListener(t -> {
            busy.setValue(false);
            User u = tu.isSuccessful() ? tu.getResult() : null;
            PrivateInfo p = tp.isSuccessful() ? tp.getResult() : null;
            if (u != null && photo.getValue() == null) photo.setValue(u.photoBase64);
            loaded.setValue(new Loaded(u, p));
        });
    }
    /** Crop / compress off the main thread. */
    public void processPhoto(Uri uri) {
        busy.setValue(true);
        AppExecutors.io().execute(() -> {
            try {
                String b64 = ImageUtils.uriToAvatarBase64(getApplication(), uri,
                        AppConfig.get().avatarSizePx, AppConfig.get().avatarMaxBytes);
                AppExecutors.main(() -> {
                    busy.setValue(false);
                    if (b64 == null) photoFailed.setValue(new Event<>(true));
                    else photo.setValue(b64);
                });
            } catch (Exception e) {
                AppExecutors.main(() -> {
                    busy.setValue(false);
                    photoFailed.setValue(new Event<>(true));
                });
            }
        });
    }

    public void removePhoto() { photo.setValue(""); }

    public void save(UserRepository.ProfileInput in) {
        String uid = ServiceLocator.get().auth().uid();
        if (uid == null) return;
        String p = photo.getValue();
        in.photoBase64 = p == null || p.isEmpty() ? null : p;
        busy.setValue(true);
        users.saveProfile(uid, in).addOnCompleteListener(t -> {
            busy.setValue(false);
            if (t.isSuccessful()) saved.setValue(new Event<>(true));
            else error.setValue(new Event<>(t.getException()));
        });
    }
}