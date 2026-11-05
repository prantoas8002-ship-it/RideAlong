package com.example.ridealong.utils;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

/** Runtime permission helpers (foreground/background location, notifications on Android 13+). */
public final class PermissionUtils {
    private PermissionUtils() { }

    public static final String[] FOREGROUND_LOCATION = {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
    };

    public static boolean hasFineLocation(Context c) {
        return granted(c, Manifest.permission.ACCESS_FINE_LOCATION);
    }

    public static boolean hasAnyLocation(Context c) {
        return granted(c, Manifest.permission.ACCESS_FINE_LOCATION)
                || granted(c, Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    /** Background location is implicit before Android 10. */
    public static boolean hasBackgroundLocation(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasFineLocation(c);
        return granted(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    public static boolean needsNotificationPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    public static boolean hasNotificationPermission(Context c) {
        if (!needsNotificationPermission()) return true;
        return granted(c, Manifest.permission.POST_NOTIFICATIONS);
    }

    public static Intent appSettingsIntent(Context c) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", c.getPackageName(), null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private static boolean granted(Context c, String p) {
        return ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED;
    }
}
