package com.example.ridealong.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;

import androidx.annotation.Nullable;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

/** One-shot current location (fresh fix, falling back to the last known location). */
public final class LocationHelper {
    public interface Callback {
        void onLocation(@Nullable Location location);
    }

    private LocationHelper() { }

    @SuppressLint("MissingPermission")
    public static void current(Context c, Callback cb) {
        if (!PermissionUtils.hasAnyLocation(c)) {
            cb.onLocation(null);
            return;
        }
        Context app = c.getApplicationContext();
        CancellationTokenSource cts = new CancellationTokenSource();
        try {
            LocationServices.getFusedLocationProviderClient(app)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                    .addOnCompleteListener(t -> {
                        if (t.isSuccessful() && t.getResult() != null) {
                            cb.onLocation(t.getResult());
                        } else {
                            LocationServices.getFusedLocationProviderClient(app).getLastLocation()
                                    .addOnCompleteListener(l -> cb.onLocation(l.isSuccessful() ? l.getResult() : null));
                        }
                    });
            AppExecutors.mainDelayed(cts::cancel, 15_000);
        } catch (SecurityException e) {
            cb.onLocation(null);
        }
    }
}
