package com.example.ridealong.data.remote;

/** Collection names and deterministic document ids. Must match firestore.rules. */
public final class FirestorePaths {
    public static final String USERS = "users";
    public static final String PRIVATE = "private";
    public static final String PRIVATE_INFO = "info";
    public static final String RIDES = "rides";
    public static final String CONTACTS = "contacts";
    public static final String RIDE_REQUESTS = "rideRequests";
    public static final String CHATS = "chats";
    public static final String MESSAGES = "messages";
    public static final String READ_STATES = "readStates";
    public static final String LIVE_LOCATIONS = "liveLocations";
    public static final String MEMBERS = "members";
    public static final String NOTIFICATIONS = "notifications";
    public static final String REVIEWS = "reviews";
    public static final String EMERGENCY_EVENTS = "emergencyEvents";
    public static final String BLOCKS = "blocks";
    public static final String REPORTS = "reports";
    public static final String CONFIG = "config";
    public static final String CONFIG_APP = "app";

    private FirestorePaths() { }

    /** One request per (ride, requester): makes duplicate requests impossible. */
    public static String requestId(String rideId, String requesterId) { return rideId + "_" + requesterId; }

    /** One review per (ride, reviewer, reviewee). */
    public static String reviewId(String rideId, String reviewerId, String revieweeId) {
        return rideId + "_" + reviewerId + "_" + revieweeId;
    }

    public static String blockId(String blockerId, String blockedId) { return blockerId + "_" + blockedId; }
}
