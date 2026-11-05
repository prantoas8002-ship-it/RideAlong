package com.example.ridealong.service;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.ridealong.R;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.domain.model.RideStatus;
import com.example.ridealong.utils.Constants;
import com.example.ridealong.utils.PermissionUtils;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Android Geofencing API for pickup proximity (participants) and destination arrival (everyone).
 * Requires fine + background location (Android 10+). Without them, {@link #register} returns
 * false and the app relies on LocationShareService's in-app distance checks instead.
 */
public final class GeofenceManager {
    public static final String KIND_PICKUP = "PICKUP";
    public static final String KIND_DROP = "DROP";

    private GeofenceManager() { }

    public static boolean canUseGeofencing(Context c) {
        return PermissionUtils.hasFineLocation(c) && PermissionUtils.hasBackgroundLocation(c);
    }

    private static PendingIntent pendingIntent(Context c) {
        Intent i = new Intent(c, GeofenceBroadcastReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        // Geofencing fills in extras, so the PendingIntent must be mutable on Android 12+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
        return PendingIntent.getBroadcast(c, 42, i, flags);
    }

    /** @return true if geofences were requested, false if permissions don't allow it. */
    @SuppressLint("MissingPermission")
    public static boolean register(Context c, Ride ride, String uid) {
        if (!canUseGeofencing(c) || ride.statusEnum().isTerminal()) return false;
        AppConfig cfg = AppConfig.get();
        List<Geofence> fences = new ArrayList<>();
        if (ride.isParticipant(uid) && ride.statusEnum() != RideStatus.IN_PROGRESS) {
            fences.add(new Geofence.Builder()
                    .setRequestId(ride.id + "|" + KIND_PICKUP)
                    .setCircularRegion(ride.pickupLat, ride.pickupLng, cfg.pickupGeofenceRadiusM)
                    .setExpirationDuration(cfg.geofenceExpirationMs)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .build());
        }
        fences.add(new Geofence.Builder()
                .setRequestId(ride.id + "|" + KIND_DROP)
                .setCircularRegion(ride.dropLat, ride.dropLng, cfg.destinationGeofenceRadiusM)
                .setExpirationDuration(cfg.geofenceExpirationMs)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build());
        GeofencingRequest req = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(fences)
                .build();
        try {
            GeofencingClient client = LocationServices.getGeofencingClient(c);
            client.addGeofences(req, pendingIntent(c));
            saveLabels(c, ride);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    public static Task<Void> unregister(Context c, String rideId) {
        try {
            return LocationServices.getGeofencingClient(c)
                    .removeGeofences(Arrays.asList(rideId + "|" + KIND_PICKUP, rideId + "|" + KIND_DROP));
        } catch (Exception e) {
            return Tasks.forResult(null);
        }
    }

    private static void saveLabels(Context c, Ride ride) {
        LocationShareService.prefs(c).edit()
                .putString("label_" + ride.id + "_" + KIND_PICKUP, ride.pickupAddress)
                .putString("label_" + ride.id + "_" + KIND_DROP, ride.dropAddress)
                .apply();
    }

    static String label(Context c, String rideId, String kind) {
        return LocationShareService.prefs(c).getString("label_" + rideId + "_" + kind, "");
    }

    /** Shows the pickup/arrival notification at most once per ride and kind. */
    public static synchronized void notifyOnce(Context c, String rideId, String kind, String placeLabel) {
        String key = "fired_" + rideId + "_" + kind;
        if (LocationShareService.prefs(c).getBoolean(key, false)) return;
        LocationShareService.prefs(c).edit().putBoolean(key, true).apply();
        boolean pickup = KIND_PICKUP.equals(kind);
        String title = c.getString(pickup ? R.string.geofence_near_pickup : R.string.geofence_arrived);
        String body = c.getString(pickup ? R.string.geofence_near_pickup_body : R.string.geofence_arrived_body,
                placeLabel == null ? "" : placeLabel);
        int id = Constants.NOTIF_ID_GEOFENCE_BASE + (rideId + kind).hashCode() % 1000;
        NotificationHelper.show(c, Constants.CHANNEL_GEOFENCE, id, title, body,
                NotificationHelper.openRideIntent(c, rideId, false, id));
    }
}