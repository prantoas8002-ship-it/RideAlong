package com.example.ridealong.domain.config;

import com.example.ridealong.domain.model.TransportType;

import java.util.Map;

/**
 * Central configuration: every tunable number lives here (or in MatchingConfig / FareConfig),
 * never scattered in UI code. Values can be overridden remotely by the Firestore document
 * {@code config/app}; see {@link #applyOverrides(Map)}. Pure Java.
 */
public final class AppConfig {
    private static volatile AppConfig instance = new AppConfig();

    public final MatchingConfig matching = new MatchingConfig();
    public final FareConfig fare = new FareConfig();

    // In-app voice calls
    public long callRingTimeoutMs = 45_000;
    public long callReconnectGraceMs = 8_000;
    /** Ignore RINGING calls older than this (stale docs after a crash). */
    public long callStaleAfterMs = 60_000;
    /** Camera capture for video calls (640x480 @ 24 fps keeps mobile data use moderate). */
    public int callVideoWidth = 640;
    public int callVideoHeight = 480;
    public int callVideoFps = 24;
    /** Free public STUN servers; TURN comes from local.properties (TURN_URLS / TURN_USERNAME / TURN_PASSWORD). */
    public final java.util.List<String> callStunUrls = java.util.Arrays.asList(
            "stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302", "stun:stun2.l.google.com:19302");

    // Live location
    public long locationUpdateIntervalMs = 10_000;
    public long locationFastestIntervalMs = 5_000;
    public long locationMinWriteIntervalMs = 10_000;
    public float locationMinWriteDisplacementM = 15f;
    /** Members may start sharing this long before departure. */
    public long sharingWindowBeforeDepartureMs = 2 * 3600_000L;

    // Geofencing
    public float pickupGeofenceRadiusM = 200f;
    public float destinationGeofenceRadiusM = 250f;
    public long geofenceExpirationMs = 12 * 3600_000L;

    // Discovery / search
    public int feedQueryLimitPerCell = 50;
    public long searchDebounceMs = 600;
    public long nominatimMinIntervalMs = 1100;
    public int geocodingResultLimit = 8;
    /** Rides that departed more than this long ago are hidden from the feed. */
    public long feedGraceAfterDepartureMs = 15 * 60_000L;

    // Chat / lists
    /** How long a sent message stays editable (firestore.rules enforces the same 15 minutes). */
    public long messageEditWindowMs = 15 * 60_000L;
    public int chatPageSize = 200;
    public int notificationPageSize = 100;
    public int historyPageSize = 100;

    // Emergency: national emergency number in Bangladesh (police, fire, ambulance).
    public String emergencyNumber = "999";

    // Profile
    public int avatarSizePx = 256;
    public int avatarMaxBytes = 100 * 1024;
    public int maxEmergencyContacts = 5;

    // Post-ride form starting values (the user can change all of them)
    public int postDefaultSeats = 2;
    public double postDefaultMaxDetourKm = 2.0;

    // Map UI: initial camera when the device location is unknown (Dhaka city centre by default).
    public double defaultMapCenterLat = 23.8103;
    public double defaultMapCenterLng = 90.4125;
    public double defaultMapZoom = 13;
    public double pickedLocationZoom = 16;

    // Routing
    public int routeMaxStoredPoints = 300;
    public double routeSimplifyToleranceKm = 0.01;
    public int httpTimeoutMs = 12_000;

    public static AppConfig get() { return instance; }

    /** Replace config (used by tests). */
    public static void set(AppConfig c) { instance = c; }

    /**
     * Applies overrides from {@code config/app}. Unknown keys are ignored; wrong types are ignored.
     * Expected shape: { matching: {weightPickup: 0.25, ...}, fareRates: {CNG: {baseFare, perKm, minimumFare}},
     *                   locationUpdateIntervalMs: 10000, ... }
     */
    @SuppressWarnings("unchecked")
    public void applyOverrides(Map<String, Object> data) {
        if (data == null) return;
        Object m = data.get("matching");
        if (m instanceof Map) {
            Map<String, Object> mm = (Map<String, Object>) m;
            matching.weightPickup = num(mm, "weightPickup", matching.weightPickup);
            matching.weightTime = num(mm, "weightTime", matching.weightTime);
            matching.weightRoute = num(mm, "weightRoute", matching.weightRoute);
            matching.weightDestination = num(mm, "weightDestination", matching.weightDestination);
            matching.maxPickupDistanceKm = num(mm, "maxPickupDistanceKm", matching.maxPickupDistanceKm);
            matching.maxDestinationDistanceKm = num(mm, "maxDestinationDistanceKm", matching.maxDestinationDistanceKm);
            matching.maxTimeDifferenceMinutes = (int) num(mm, "maxTimeDifferenceMinutes", matching.maxTimeDifferenceMinutes);
            matching.defaultSearchRadiusKm = num(mm, "defaultSearchRadiusKm", matching.defaultSearchRadiusKm);
            matching.maxSearchRadiusKm = num(mm, "maxSearchRadiusKm", matching.maxSearchRadiusKm);
        }
        Object f = data.get("fareRates");
        if (f instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) f).entrySet()) {
                if (!(e.getValue() instanceof Map)) continue;
                TransportType t = TransportType.fromName(e.getKey());
                FareConfig.Rate r = fare.rate(t);
                Map<String, Object> rm = (Map<String, Object>) e.getValue();
                r.baseFare = num(rm, "baseFare", r.baseFare);
                r.perKm = num(rm, "perKm", r.perKm);
                r.minimumFare = num(rm, "minimumFare", r.minimumFare);
                r.capacity = (int) num(rm, "capacity", r.capacity);
            }
        }
        locationUpdateIntervalMs = (long) num(data, "locationUpdateIntervalMs", locationUpdateIntervalMs);
        locationMinWriteIntervalMs = (long) num(data, "locationMinWriteIntervalMs", locationMinWriteIntervalMs);
        pickupGeofenceRadiusM = (float) num(data, "pickupGeofenceRadiusM", pickupGeofenceRadiusM);
        destinationGeofenceRadiusM = (float) num(data, "destinationGeofenceRadiusM", destinationGeofenceRadiusM);
    }

    private static double num(Map<String, Object> m, String key, double fallback) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : fallback;
    }
}
