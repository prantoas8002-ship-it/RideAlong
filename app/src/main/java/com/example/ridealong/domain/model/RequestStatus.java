package com.example.ridealong.domain.model;

/** Join-request lifecycle: PENDING → ACCEPTED | REJECTED | CANCELLED; ACCEPTED → LEFT; CANCELLED → PENDING. */
public enum RequestStatus {
    PENDING, ACCEPTED, REJECTED, CANCELLED, LEFT;

    public static RequestStatus fromName(String s) {
        if (s == null) return PENDING;
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }

    public boolean canTransitionTo(RequestStatus to) {
        switch (this) {
            case PENDING: return to == ACCEPTED || to == REJECTED || to == CANCELLED;
            case ACCEPTED: return to == LEFT;
            case CANCELLED: return to == PENDING;
            default: return false;
        }
    }
}
