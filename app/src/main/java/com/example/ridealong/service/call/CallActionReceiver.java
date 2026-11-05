package com.example.ridealong.service.call;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.ridealong.data.model.CallSession;
import com.example.ridealong.data.repository.CallRepository;
import com.example.ridealong.ui.call.CallActivity;

/** Decline action on the incoming-call notification. */
public class CallActionReceiver extends BroadcastReceiver {
    public static final String ACTION_DECLINE = "com.example.ridealong.action.DECLINE_CALL";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_DECLINE.equals(intent.getAction())) return;
        CallManager cm = CallManager.get(context);
        if (cm.isBusy()) {
            cm.decline();
        } else {
            // App process was restarted by the push: decline straight in Firestore so the caller stops.
            String callId = intent.getStringExtra(CallActivity.EXTRA_CALL_ID);
            if (callId != null) new CallRepository().setStatus(callId, CallSession.REJECTED);
        }
        IncomingCallWatcher.cancelIncomingNotification(context);
    }
}
