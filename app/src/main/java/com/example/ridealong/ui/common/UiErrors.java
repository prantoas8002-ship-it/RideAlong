package com.example.ridealong.ui.common;

import android.content.Context;

import com.example.ridealong.R;
import com.example.ridealong.data.repository.DomainException;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.firestore.FirebaseFirestoreException;

/** Maps exceptions to user-facing, localised messages. */
public final class UiErrors {
    private UiErrors() { }

    public static String message(Context c, Throwable t) {
        Throwable e = t;
        while (e != null) {
            if (e instanceof DomainException) return c.getString(forReason(((DomainException) e).reason));
            if (e instanceof FirebaseNetworkException) return c.getString(R.string.error_network);
            if (e instanceof FirebaseTooManyRequestsException) return c.getString(R.string.error_too_many_requests);
            if (e instanceof FirebaseAuthUserCollisionException) return c.getString(R.string.error_email_in_use);
            if (e instanceof FirebaseAuthInvalidCredentialsException || e instanceof FirebaseAuthInvalidUserException) {
                return c.getString(R.string.error_wrong_credentials);
            }
            if (e instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
                if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) return c.getString(R.string.error_permission_denied);
                if (code == FirebaseFirestoreException.Code.UNAVAILABLE) return c.getString(R.string.error_network);
                if (code == FirebaseFirestoreException.Code.UNAUTHENTICATED) return c.getString(R.string.error_not_signed_in);
                if (code == FirebaseFirestoreException.Code.FAILED_PRECONDITION
                        && e.getMessage() != null && e.getMessage().contains("index")) {
                    return c.getString(R.string.error_generic) + " (missing Firestore index: deploy firestore.indexes.json)";
                }
            }
            if (e.getCause() == e) break;
            e = e.getCause();
        }
        return c.getString(R.string.error_generic);
    }

    public static int forReason(DomainException.Reason r) {
        switch (r) {
            case NOT_SIGNED_IN: return R.string.error_not_signed_in;
            case SELF_JOIN: return R.string.error_self_join;
            case RIDE_FULL: return R.string.error_ride_full;
            case ALREADY_REQUESTED: return R.string.error_already_requested;
            case REQUEST_REJECTED: return R.string.request_rejected_note;
            case BLOCKED: return R.string.error_blocked;
            case RIDE_CLOSED: return R.string.error_ride_closed;
            case REQUEST_CHANGED: return R.string.error_request_changed;
            case NOT_MEMBER: return R.string.chat_not_member;
            case ALREADY_RATED: return R.string.rating_already;
            case NOT_OWNER:
            case INVALID_TRANSITION:
                return R.string.error_permission_denied;
            default: return R.string.error_generic;
        }
    }
}