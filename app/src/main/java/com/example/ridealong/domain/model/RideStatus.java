package com.example.ridealong.domain.model;

public enum RideStatus {
    OPEN, FULL, IN_PROGRESS, COMPLETED, CANCELLED;

    public static RideStatus fromName(String s) {
        if (s == null) return OPEN;
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return OPEN;
        }
    }

    public boolean isTerminal() { return this == COMPLETED || this == CANCELLED; }

    /** Ride can still accept join requests. */
    public boolean isJoinable() { return this == OPEN; }

    /** Ride is active (shown in "My active rides", chat open, tracking possible). */
    public boolean isActive() { return this == OPEN || this == FULL || this == IN_PROGRESS; }
}
