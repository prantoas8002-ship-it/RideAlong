package com.example.ridealong.service.call;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Decline action on the incoming-call notification. */
public class CallActionReceiver extends BroadcastReceiver {
    public static final String ACTION_DECLINE = "com.example.ridealong.action.DECLINE_CALL";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_DECLINE.equals(intent.getAction())) {
            CallManager.get(context).decline();
            IncomingCallWatcher.cancelIncomingNotification(context);
        }
    }
}