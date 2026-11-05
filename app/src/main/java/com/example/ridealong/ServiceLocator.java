package com.example.ridealong;

import android.content.Context;

import com.example.ridealong.data.local.AppDatabase;
import com.example.ridealong.data.repository.AuthRepository;
import com.example.ridealong.data.repository.ChatRepository;
import com.example.ridealong.data.repository.ConfigRepository;
import com.example.ridealong.data.repository.EmergencyRepository;
import com.example.ridealong.data.repository.LiveLocationRepository;
import com.example.ridealong.data.repository.NotificationRepository;
import com.example.ridealong.data.repository.RequestRepository;
import com.example.ridealong.data.repository.ReviewRepository;
import com.example.ridealong.data.repository.RideRepository;
import com.example.ridealong.data.repository.SafetyRepository;
import com.example.ridealong.data.repository.SettingsRepository;
import com.example.ridealong.data.repository.UserRepository;
import com.example.ridealong.domain.calculator.FareCalculator;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.map.MapProviderFactory;
import com.example.ridealong.utils.NetworkMonitor;

/**
 * Manual dependency injection: one lazily created instance of each repository. Keeps the
 * project free of a DI framework while letting ViewModels share repositories.
 */
public final class ServiceLocator {
    private static ServiceLocator instance;

    private final Context app;
    private AppDatabase db;
    private NetworkMonitor network;
    private SettingsRepository settings;
    private AuthRepository auth;
    private UserRepository users;
    private RideRepository rides;
    private RequestRepository requests;
    private ChatRepository chat;
    private NotificationRepository notifications;
    private ReviewRepository reviews;
    private LiveLocationRepository liveLocations;
    private EmergencyRepository emergency;
    private SafetyRepository safety;
    private ConfigRepository config;
    private MapProviderFactory maps;

    private ServiceLocator(Context app) { this.app = app.getApplicationContext(); }

    public static synchronized void init(Context context) {
        if (instance == null) instance = new ServiceLocator(context);
    }

    public static ServiceLocator get() {
        if (instance == null) throw new IllegalStateException("ServiceLocator.init() not called");
        return instance;
    }

    public Context context() { return app; }

    public synchronized AppDatabase db() {
        if (db == null) db = AppDatabase.get(app);
        return db;
    }

    public synchronized NetworkMonitor network() {
        if (network == null) network = new NetworkMonitor(app);
        return network;
    }

    public synchronized SettingsRepository settings() {
        if (settings == null) settings = new SettingsRepository(app);
        return settings;
    }

    public synchronized AuthRepository auth() {
        if (auth == null) auth = new AuthRepository(app, users(), db(), settings());
        return auth;
    }

    public synchronized UserRepository users() {
        if (users == null) users = new UserRepository(db());
        return users;
    }

    public synchronized NotificationRepository notifications() {
        if (notifications == null) notifications = new NotificationRepository(app);
        return notifications;
    }

    public synchronized SafetyRepository safety() {
        if (safety == null) safety = new SafetyRepository();
        return safety;
    }

    public synchronized RideRepository rides() {
        if (rides == null) rides = new RideRepository(db(), notifications(), safety());
        return rides;
    }

    public synchronized RequestRepository requests() {
        if (requests == null) requests = new RequestRepository(notifications(), chat());
        return requests;
    }

    public synchronized ChatRepository chat() {
        if (chat == null) chat = new ChatRepository(db(), notifications());
        return chat;
    }

    public synchronized ReviewRepository reviews() {
        if (reviews == null) reviews = new ReviewRepository();
        return reviews;
    }

    public synchronized LiveLocationRepository liveLocations() {
        if (liveLocations == null) liveLocations = new LiveLocationRepository();
        return liveLocations;
    }

    public synchronized EmergencyRepository emergency() {
        if (emergency == null) emergency = new EmergencyRepository(db());
        return emergency;
    }

    public synchronized ConfigRepository config() {
        if (config == null) config = new ConfigRepository();
        return config;
    }

    public synchronized MapProviderFactory maps() {
        if (maps == null) maps = new MapProviderFactory(app, settings());
        return maps;
    }

    public FareCalculator fareCalculator() { return new FareCalculator(AppConfig.get().fare); }
}
