package com.example.ridealong;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.service.NotificationHelper;
import com.example.ridealong.service.RealtimeNotificationListener;
import com.example.ridealong.utils.Constants;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;

import java.io.File;

public class RideAlongApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ServiceLocator.init(this);
        com.example.ridealong.data.repository.SettingsRepository.applyTheme(ServiceLocator.get().settings().getThemeMode());
        FirebaseProvider.init(this);
        NotificationHelper.createChannels(this);
        configureOsmdroid();

        if (FirebaseProvider.isAvailable()) {
            FirebaseProvider.auth().addAuthStateListener(auth -> {
                RealtimeNotificationListener listener = RealtimeNotificationListener.get(this);
                if (auth.getCurrentUser() != null) {
                    listener.start();
                    if (isInForeground()) ServiceLocator.get().presence().goOnline(auth.getCurrentUser().getUid());
                    com.example.ridealong.service.push.PushTokens.registerCurrentDevice();
                    ServiceLocator.get().config().loadOnce();
                    ServiceLocator.get().emergency().syncPending();
                } else {
                    listener.stop();
                }
            });
            // Realtime Database presence: online while any screen of the app is visible.
            ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
                @Override
                public void onStart(@NonNull LifecycleOwner owner) {
                    String uid = FirebaseProvider.uidOrNull();
                    if (uid != null) ServiceLocator.get().presence().goOnline(uid);
                }

                @Override
                public void onStop(@NonNull LifecycleOwner owner) {
                    ServiceLocator.get().presence().goOffline();
                }
            });
            // Re-upload anything logged offline once connectivity returns.
            ServiceLocator.get().network().online().observeForever(online -> {
                if (Boolean.TRUE.equals(online)) ServiceLocator.get().emergency().syncPending();
            });
        }
    }

    private static boolean isInForeground() {
        return ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    /** OSM tile policy: identify the app with a User-Agent; cache tiles in app-private storage. */
    private void configureOsmdroid() {
        IConfigurationProvider cfg = Configuration.getInstance();
        cfg.setUserAgentValue(Constants.USER_AGENT);
        File base = new File(getCacheDir(), "osmdroid");
        cfg.setOsmdroidBasePath(base);
        cfg.setOsmdroidTileCache(new File(base, "tiles"));
    }
}
