package com.example.ridealong.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

/** Receives geofence ENTER transitions and posts pickup / arrival notifications. */
public class GeofenceBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError() || event.getGeofenceTransition() != Geofence.GEOFENCE_TRANSITION_ENTER) return;
        if (event.getTriggeringGeofences() == null) return;
        for (Geofence g : event.getTriggeringGeofences()) {
            String[] parts = g.getRequestId().split("\\|");
            if (parts.length != 2) continue;
            GeofenceManager.notifyOnce(context, parts[0], parts[1], GeofenceManager.label(context, parts[0], parts[1]));
        }
    }
}