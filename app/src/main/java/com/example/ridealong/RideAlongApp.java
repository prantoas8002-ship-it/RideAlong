package com.example.ridealong;

import android.app.Application;

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
                    ServiceLocator.get().config().loadOnce();
                    ServiceLocator.get().emergency().syncPending();
                } else {
                    listener.stop();
                }
            });
            // Re-upload anything logged offline once connectivity returns.
            ServiceLocator.get().network().online().observeForever(online -> {
                if (Boolean.TRUE.equals(online)) ServiceLocator.get().emergency().syncPending();
            });
        }
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
