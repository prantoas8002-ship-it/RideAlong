package com.example.ridealong.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.ridealong.BuildConfig;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.utils.Constants;

/** Local user preferences (SharedPreferences). */
public class SettingsRepository {
    private static final String PREFS = "ridealong_settings";
    private static final String K_ONBOARDING = "onboarding_done";
    private static final String K_MAP_PROVIDER = "map_provider";
    private static final String K_RADIUS = "search_radius_km";
    private static final String K_NOTIFY_REQUESTS = "notify_requests";
    private static final String K_NOTIFY_MESSAGES = "notify_messages";
    private static final String K_BACKGROUND_SYNC = "background_sync";
    private static final String K_LOCATION_CONSENT = "location_consent";
    private static final String K_LAST_NOTIF_SEEN = "last_notification_seen";
    private static final String K_ASKED_NOTIF_PERMISSION = "asked_notification_permission";
    private static final String K_THEME = "theme_mode";

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private final SharedPreferences prefs;

    public SettingsRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getThemeMode() { return prefs.getString(K_THEME, THEME_SYSTEM); }

    public void setThemeMode(String mode) {
        prefs.edit().putString(K_THEME, mode).apply();
        applyTheme(mode);
    }

    /** Applies Light / Dark / follow-system app-wide (AppCompat recreates visible activities). */
    public static void applyTheme(String mode) {
        int night = THEME_DARK.equals(mode) ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                : THEME_LIGHT.equals(mode) ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(night);
    }

    public boolean isOnboardingDone() { return prefs.getBoolean(K_ONBOARDING, false); }

    public void setOnboardingDone() { prefs.edit().putBoolean(K_ONBOARDING, true).apply(); }

    public static boolean isGoogleMapsAvailable() { return !BuildConfig.MAPS_API_KEY.isEmpty(); }

    /** Google is only honoured when a key exists; otherwise always OSM. */
    public String getMapProvider() {
        String p = prefs.getString(K_MAP_PROVIDER, Constants.MAP_PROVIDER_OSM);
        if (Constants.MAP_PROVIDER_GOOGLE.equals(p) && !isGoogleMapsAvailable()) return Constants.MAP_PROVIDER_OSM;
        return p;
    }

    public void setMapProvider(String provider) { prefs.edit().putString(K_MAP_PROVIDER, provider).apply(); }

    public double getSearchRadiusKm() {
        double def = AppConfig.get().matching.defaultSearchRadiusKm;
        double v = prefs.getFloat(K_RADIUS, (float) def);
        double min = AppConfig.get().matching.minSearchRadiusKm, max = AppConfig.get().matching.maxSearchRadiusKm;
        return Math.max(min, Math.min(max, v));
    }

    public void setSearchRadiusKm(double km) { prefs.edit().putFloat(K_RADIUS, (float) km).apply(); }

    public boolean notifyRequests() { return prefs.getBoolean(K_NOTIFY_REQUESTS, true); }

    public void setNotifyRequests(boolean v) { prefs.edit().putBoolean(K_NOTIFY_REQUESTS, v).apply(); }

    public boolean notifyMessages() { return prefs.getBoolean(K_NOTIFY_MESSAGES, true); }

    public void setNotifyMessages(boolean v) { prefs.edit().putBoolean(K_NOTIFY_MESSAGES, v).apply(); }

    public boolean backgroundSync() { return prefs.getBoolean(K_BACKGROUND_SYNC, false); }

    public void setBackgroundSync(boolean v) { prefs.edit().putBoolean(K_BACKGROUND_SYNC, v).apply(); }

    public boolean hasLocationConsent() { return prefs.getBoolean(K_LOCATION_CONSENT, false); }

    public void setLocationConsent(boolean v) { prefs.edit().putBoolean(K_LOCATION_CONSENT, v).apply(); }

    public long lastNotificationSeen(String uid) { return prefs.getLong(K_LAST_NOTIF_SEEN + "_" + uid, 0); }

    public void setLastNotificationSeen(String uid, long millis) {
        prefs.edit().putLong(K_LAST_NOTIF_SEEN + "_" + uid, millis).apply();
    }

    public boolean askedNotificationPermission() { return prefs.getBoolean(K_ASKED_NOTIF_PERMISSION, false); }

    public void setAskedNotificationPermission() { prefs.edit().putBoolean(K_ASKED_NOTIF_PERMISSION, true).apply(); }

    /** On logout: keep device-level settings (onboarding, map provider) but clear consent. */
    public void clearUserScoped() {
        prefs.edit().remove(K_LOCATION_CONSENT).apply();
    }
}
