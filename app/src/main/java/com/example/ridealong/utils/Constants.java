package com.example.ridealong.utils;

/** Non-tunable identifiers (keys, ids, actions). Tunable numbers live in domain/config/AppConfig. */
public final class Constants {
    private Constants() { }

    // Navigation arguments
    public static final String ARG_RIDE_ID = "rideId";
    public static final String ARG_USER_ID = "userId";
    public static final String ARG_SETUP_MODE = "setupMode";
    public static final String ARG_PICK_KIND = "pickKind";
    public static final String ARG_START_LAT = "startLat";
    public static final String ARG_START_LNG = "startLng";

    // Location picker results (SavedStateHandle keys)
    public static final String RESULT_PICKED_PICKUP = "picked_pickup";
    public static final String RESULT_PICKED_DROP = "picked_drop";
    public static final String PICK_KIND_PICKUP = "pickup";
    public static final String PICK_KIND_DROP = "drop";

    // Intent extras / actions
    public static final String EXTRA_RIDE_ID = "extra_ride_id";
    public static final String EXTRA_OPEN_CHAT = "extra_open_chat";
    public static final String EXTRA_OPEN_NOTIFICATIONS = "extra_open_notifications";
    public static final String ACTION_STOP_SHARING = "com.example.ridealong.action.STOP_SHARING";

    // Notification ids
    public static final int NOTIF_ID_LOCATION_SERVICE = 1001;
    public static final int NOTIF_ID_SYNC_SERVICE = 1002;
    public static final int NOTIF_ID_GEOFENCE_BASE = 2000;

    // Notification channels
    public static final String CHANNEL_RIDES = "rides";
    public static final String CHANNEL_MESSAGES = "messages";
    public static final String CHANNEL_LOCATION = "location";
    public static final String CHANNEL_GEOFENCE = "geofence";
    public static final String CHANNEL_SYNC = "sync";

    // Map providers
    public static final String MAP_PROVIDER_OSM = "OSM";
    public static final String MAP_PROVIDER_GOOGLE = "GOOGLE";

    // Message types
    public static final String MSG_TEXT = "TEXT";
    public static final String MSG_CALL = "CALL";
    public static final String MSG_SYSTEM = "SYSTEM";

    // Notification types (must match firestore.rules)
    public static final String N_JOIN_REQUEST = "JOIN_REQUEST";
    public static final String N_REQUEST_ACCEPTED = "REQUEST_ACCEPTED";
    public static final String N_REQUEST_REJECTED = "REQUEST_REJECTED";
    public static final String N_REQUEST_CANCELLED = "REQUEST_CANCELLED";
    public static final String N_PARTICIPANT_LEFT = "PARTICIPANT_LEFT";
    public static final String N_NEW_MESSAGE = "NEW_MESSAGE";
    public static final String N_RIDE_STARTED = "RIDE_STARTED";
    public static final String N_RIDE_COMPLETED = "RIDE_COMPLETED";
    public static final String N_RIDE_CANCELLED = "RIDE_CANCELLED";
    public static final String N_RIDE_UPDATED = "RIDE_UPDATED";

    // Emergency actions
    public static final String SOS_DIAL = "DIAL_OPENED";
    public static final String SOS_SMS = "SMS_OPENED";
    public static final String SOS_999 = "EMERGENCY_999_OPENED";

    public static final String USER_AGENT = "RideAlong/1.0 (Android; com.example.ridealong)";
}
