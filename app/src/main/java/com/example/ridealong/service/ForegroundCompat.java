package com.example.ridealong.service;

import android.app.Notification;
import android.app.Service;
import android.os.Build;

/** startForeground with a service type on Android 10+ (required for location on Android 14). */
final class ForegroundCompat {
    private ForegroundCompat() { }

    static void start(Service service, int id, Notification notification, int type) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(id, notification, type);
        } else {
            service.startForeground(id, notification);
        }
    }
}