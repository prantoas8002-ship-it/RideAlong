package com.example.ridealong.ui.common;

import android.view.View;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.Ride;
import com.example.ridealong.utils.IntentUtils;

/**
 * Call a co-rider: reads the member-only contact card, opens the phone dialer (ACTION_DIAL, no
 * CALL_PHONE permission) and logs "X called Y" into the ride chat history.
 */
public final class CallHelper {
    private CallHelper() { }

    public static void call(View anchor, Ride ride, String targetUid, String targetName) {
        ServiceLocator.get().rides().getContact(ride.id, targetUid).addOnCompleteListener(t -> {
            String phone = t.isSuccessful() && t.getResult() != null ? t.getResult().phone : null;
            if (phone == null || phone.trim().isEmpty()) {
                Ui.snack(anchor, R.string.no_phone_available);
                return;
            }
            if (!IntentUtils.openDialer(anchor.getContext(), phone)) {
                Ui.snack(anchor, R.string.sos_no_dialer);
                return;
            }
            String me = Me.name();
            String who = targetName == null || targetName.isEmpty() ? anchor.getContext().getString(R.string.unknown_user) : targetName;
            if (ride.isMember(Me.uid())) {
                ServiceLocator.get().chat().logCall(ride.id, me, who,
                        anchor.getContext().getString(R.string.chat_call_log, me, who));
            }
        });
    }
}