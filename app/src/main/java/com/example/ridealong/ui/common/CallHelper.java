package com.example.ridealong.ui.common;

import android.view.View;

import androidx.annotation.Nullable;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.utils.IntentUtils;

/**
 * Call another passenger: reads the ride's contact card (rules: members, or a requester calling the
 * poster), opens the phone dialer (ACTION_DIAL, no CALL_PHONE permission) and reports success so the
 * caller can log "X called Y" in the right chat history.
 */
public final class CallHelper {
    public interface OnDialed {
        /** @param logText localised "X called Y" text for the chat history */
        void onDialed(String logText);
    }

    private CallHelper() { }

    public static void dial(View anchor, String rideId, String targetUid, @Nullable String targetName, @Nullable OnDialed onDialed) {
        ServiceLocator.get().rides().getContact(rideId, targetUid).addOnCompleteListener(t -> {
            String phone = t.isSuccessful() && t.getResult() != null ? t.getResult().phone : null;
            if (phone == null || phone.trim().isEmpty()) {
                Ui.snack(anchor, R.string.no_phone_available);
                return;
            }
            if (!IntentUtils.openDialer(anchor.getContext(), phone)) {
                Ui.snack(anchor, R.string.sos_no_dialer);
                return;
            }
            String who = targetName == null || targetName.isEmpty() ? anchor.getContext().getString(R.string.unknown_user) : targetName;
            if (onDialed != null) onDialed.onDialed(anchor.getContext().getString(R.string.chat_call_log, Me.name(), who));
        });
    }

    /** Call from the ride screens; logged in the ride group chat when I'm a member. */
    public static void call(View anchor, Ride ride, String targetUid, String targetName) {
        dial(anchor, ride.id, targetUid, targetName, log -> {
            if (ride.isMember(Me.uid())) ServiceLocator.get().chat().logCall(ride.id, Me.name(), targetName, log);
        });
    }
}