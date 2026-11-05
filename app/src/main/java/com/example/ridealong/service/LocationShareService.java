package com.example.ridealong.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.data.model.User;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.remote.FirestorePaths;
import com.example.ridealong.domain.algorithm.Haversine;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.domain.model.GeoCoord;
import com.example.ridealong.utils.Constants;
import com.example.ridealong.utils.PermissionUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Foreground service (type=location, Android 14 compliant) that shares the device location with
 * the members of ONE ride. Writes are throttled (time + displacement). It stops itself when the
 * ride is completed/cancelled or the user is no longer a member, and deletes its location doc.
 * While running it also performs in-app pickup/arrival distance checks, the fallback when
 * Geofencing cannot be used (no background location permission).
 */
public class LocationShareService extends LifecycleService {
    private static final String EXTRA_RIDE_ID = "ride_id";
    private static final MutableLiveData<String> SHARING_RIDE = new MutableLiveData<>(null);

    private FusedLocationProviderClient fused;
    private ListenerRegistration rideListener;
    private String rideId;
    private String uid;
    private String myName = "";
    private Ride ride;
    private long lastWriteAt;
    private Location lastWritten;

    public static void start(Context c, String rideId) {
        Intent i = new Intent(c, LocationShareService.class).putExtra(EXTRA_RIDE_ID, rideId);
        ContextCompat.startForegroundService(c, i);
    }

    public static void stop(Context c) {
        c.startService(new Intent(c, LocationShareService.class).setAction(Constants.ACTION_STOP_SHARING));
    }

    /** Ride id currently being shared, or null. */
    public static LiveData<String> sharingRideId() { return SHARING_RIDE; }

    private final LocationCallback callback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult result) {
            Location loc = result.getLastLocation();
            if (loc != null) onLocation(loc);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        fused = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (intent != null && Constants.ACTION_STOP_SHARING.equals(intent.getAction())) {
            stopSharing();
            return START_NOT_STICKY;
        }
        String newRide = intent == null ? null : intent.getStringExtra(EXTRA_RIDE_ID);
        uid = FirebaseProvider.uidOrNull();
        if (newRide == null || uid == null || !PermissionUtils.hasAnyLocation(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!startInForeground(newRide)) return START_NOT_STICKY;
        if (rideId != null && !rideId.equals(newRide)) removeMyLocation(rideId);
        rideId = newRide;
        SHARING_RIDE.setValue(rideId);
        loadName();
        watchRide();
        requestUpdates();
        return START_REDELIVER_INTENT;
    }

    private boolean startInForeground(String forRide) {
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, LocationShareService.class).setAction(Constants.ACTION_STOP_SHARING),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this, Constants.CHANNEL_LOCATION)
                .setSmallIcon(R.drawable.ic_navigation)
                .setColor(getColor(R.color.ra_primary))
                .setContentTitle(getString(R.string.notif_sharing_title))
                .setContentText(getString(R.string.notif_sharing_body))
                .setOngoing(true)
                .setContentIntent(NotificationHelper.openRideIntent(this, forRide, false, 3))
                .addAction(R.drawable.ic_stop, getString(R.string.action_stop_sharing), stop)
                .build();
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0;
        try {
            ServiceCompat.startForeground(this, Constants.NOTIF_ID_LOCATION_SERVICE, n, type);
            return true;
        } catch (Exception e) {
            // e.g. ForegroundServiceStartNotAllowedException or missing permission on Android 14
            stopSelf();
            return false;
        }
    }

    private void loadName() {
        User cached = ServiceLocator.get().users().peekCached(uid);
        if (cached != null) myName = cached.displayName();
        ServiceLocator.get().users().getUser(uid).addOnSuccessListener(u -> {
            if (u != null) myName = u.displayName();
        });
    }

    private void watchRide() {
        if (rideListener != null) rideListener.remove();
        rideListener = FirebaseProvider.db().collection(FirestorePaths.RIDES).document(rideId)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) return;
                    if (snap == null || !snap.exists()) {
                        stopSharing();
                        return;
                    }
                    ride = snap.toObject(Ride.class);
                    if (ride == null || ride.statusEnum().isTerminal() || !ride.isMember(uid)) {
                        stopSharing(); // auto-stop on COMPLETED / CANCELLED / removed
                    }
                });
    }

    @SuppressLint("MissingPermission") // checked in onStartCommand
    private void requestUpdates() {
        AppConfig cfg = AppConfig.get();
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, cfg.locationUpdateIntervalMs)
                .setMinUpdateIntervalMillis(cfg.locationFastestIntervalMs)
                .build();
        fused.removeLocationUpdates(callback);
        try {
            fused.requestLocationUpdates(req, callback, Looper.getMainLooper());
        } catch (SecurityException e) {
            stopSharing();
        }
    }

    private void onLocation(Location loc) {
        if (rideId == null || uid == null) return;
        AppConfig cfg = AppConfig.get();
        long now = System.currentTimeMillis();
        boolean timeOk = now - lastWriteAt >= cfg.locationMinWriteIntervalMs;
        boolean movedEnough = lastWritten == null || loc.distanceTo(lastWritten) >= cfg.locationMinWriteDisplacementM;
        if (timeOk && (movedEnough || now - lastWriteAt >= cfg.locationMinWriteIntervalMs * 6)) {
            lastWriteAt = now;
            lastWritten = loc;
            ServiceLocator.get().liveLocations().update(rideId, uid, myName, loc.getLatitude(), loc.getLongitude(),
                    loc.getAccuracy(), loc.getBearing());
        }
        checkProximity(new GeoCoord(loc.getLatitude(), loc.getLongitude()));
    }

    /** In-app fallback for pickup/arrival alerts (also de-duplicated against real geofence events). */
    private void checkProximity(GeoCoord me) {
        if (ride == null) return;
        AppConfig cfg = AppConfig.get();
        if (ride.isParticipant(uid)
                && Haversine.distanceKm(me, ride.pickup()) * 1000 <= cfg.pickupGeofenceRadiusM) {
            GeofenceManager.notifyOnce(this, ride.id, GeofenceManager.KIND_PICKUP, ride.pickupAddress);
        }
        if (ride.statusEnum() == com.example.ridealong.domain.model.RideStatus.IN_PROGRESS
                && Haversine.distanceKm(me, ride.drop()) * 1000 <= cfg.destinationGeofenceRadiusM) {
            GeofenceManager.notifyOnce(this, ride.id, GeofenceManager.KIND_DROP, ride.dropAddress);
        }
    }

    private void stopSharing() {
        if (fused != null) fused.removeLocationUpdates(callback);
        if (rideListener != null) {
            rideListener.remove();
            rideListener = null;
        }
        if (rideId != null) removeMyLocation(rideId);
        rideId = null;
        SHARING_RIDE.setValue(null);
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void removeMyLocation(String id) {
        if (uid != null) ServiceLocator.get().liveLocations().remove(id, uid);
    }

    @Override
    public void onDestroy() {
        if (fused != null) fused.removeLocationUpdates(callback);
        if (rideListener != null) rideListener.remove();
        SHARING_RIDE.setValue(null);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        super.onBind(intent);
        return null;
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("geofence_state", Context.MODE_PRIVATE);
    }
}