package com.example.ridealong.data.repository;

/**
 * Business-rule failure detected client-side (rules enforce the same server-side). Unchecked so
 * it can be thrown from Firestore transaction functions.
 */
public class DomainException extends RuntimeException {
    public enum Reason {
        NOT_SIGNED_IN, SELF_JOIN, RIDE_FULL, ALREADY_REQUESTED, REQUEST_REJECTED, BLOCKED, RIDE_CLOSED,
        REQUEST_CHANGED, NOT_MEMBER, NOT_OWNER, INVALID_TRANSITION, ALREADY_RATED, NOT_FOUND
    }

    public final Reason reason;

    public DomainException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }
}
