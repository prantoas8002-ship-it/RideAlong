package com.example.ridealong.ui.call;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.CallSession;
import com.example.ridealong.databinding.ActivityCallBinding;
import com.example.ridealong.service.call.CallManager;
import com.example.ridealong.service.call.IncomingCallWatcher;
import com.example.ridealong.ui.common.Avatars;

/** Full-screen call UI (outgoing, incoming, in-call). Shows over the lock screen for incoming calls. */
public class CallActivity extends AppCompatActivity {
    public static final String EXTRA_AUTO_ACCEPT = "auto_accept";
    private static final String EXTRA_PEER_ID = "peer_id";
    private static final String EXTRA_PEER_NAME = "peer_name";
    private static final String EXTRA_RIDE_ID = "ride_id";
    private static final String EXTRA_CHAT_ID = "chat_id";

    private ActivityCallBinding b;
    private CallManager calls;
    private PowerManager.WakeLock proximity;
    /** What to do once RECORD_AUDIO is granted. */
    private Runnable afterPermission;

    private final ActivityResultLauncher<String> micPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                Runnable r = afterPermission;
                afterPermission = null;
                if (Boolean.TRUE.equals(granted)) {
                    if (r != null) r.run();
                } else {
                    Toast.makeText(this, R.string.call_mic_needed, Toast.LENGTH_LONG).show();
                    if (calls.state().getValue() != null && calls.state().getValue().phase == CallManager.Phase.INCOMING) calls.decline();
                    finish();
                }
            });

    /** Start an in-app voice call to another passenger (no phone number involved). */
    public static void startOutgoing(Context c, String peerId, String peerName, String rideId, @Nullable String chatId) {
        c.startActivity(new Intent(c, CallActivity.class)
                .putExtra(EXTRA_PEER_ID, peerId)
                .putExtra(EXTRA_PEER_NAME, peerName)
                .putExtra(EXTRA_RIDE_ID, rideId)
                .putExtra(EXTRA_CHAT_ID, chatId == null ? "" : chatId));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        b = ActivityCallBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        calls = CallManager.get(this);
        IncomingCallWatcher.cancelIncomingNotification(this);

        b.accept.setOnClickListener(v -> withMic(calls::accept));
        b.decline.setOnClickListener(v -> calls.decline());
        b.hangUp.setOnClickListener(v -> calls.hangUp());
        b.mute.setOnClickListener(v -> calls.toggleMute());
        b.speaker.setOnClickListener(v -> calls.toggleSpeaker());

        calls.state().observe(this, this::render);
        if (savedInstanceState == null) handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        String peerId = i.getStringExtra(EXTRA_PEER_ID);
        if (peerId != null && !calls.isBusy()) {
            String name = i.getStringExtra(EXTRA_PEER_NAME);
            String ride = i.getStringExtra(EXTRA_RIDE_ID);
            String chat = i.getStringExtra(EXTRA_CHAT_ID);
            showPeer(peerId, name);
            withMic(() -> {
                if (!calls.startOutgoing(peerId, name, ride, chat)) finish();
            });
        } else if (i.getBooleanExtra(EXTRA_AUTO_ACCEPT, false)) {
            withMic(calls::accept);
        }
    }

    private void withMic(Runnable r) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            r.run();
        } else {
            afterPermission = r;
            micPermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void showPeer(@Nullable String peerId, @Nullable String name) {
        b.name.setText(name == null || name.isEmpty() ? getString(R.string.unknown_user) : name);
        if (peerId != null) {
            ServiceLocator.get().users().getUser(peerId).addOnSuccessListener(u -> {
                if (u != null && b != null) Avatars.load(b.avatar, u.photoBase64);
            });
        }
    }

    private void render(CallManager.State s) {
        if (s == null) return;
        if (s.phase == CallManager.Phase.IDLE) {
            if (afterPermission == null) finish();
            return;
        }
        showPeer(s.peerId, s.peerName);
        boolean incoming = s.phase == CallManager.Phase.INCOMING;
        b.incomingControls.setVisibility(incoming ? View.VISIBLE : View.GONE);
        b.callControls.setVisibility(incoming || s.phase == CallManager.Phase.ENDED ? View.GONE : View.VISIBLE);
        b.mute.setSelected(s.muted);
        b.mute.setImageResource(s.muted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        b.speaker.setSelected(s.speaker);
        b.speaker.setAlpha(s.speaker ? 1f : 0.7f);
        b.timer.stop();
        b.timer.setVisibility(View.GONE);
        switch (s.phase) {
            case OUTGOING:
                b.status.setText(R.string.call_status_calling);
                break;
            case INCOMING:
                b.status.setText(R.string.call_incoming_title);
                break;
            case CONNECTING:
                b.status.setText(R.string.call_status_connecting);
                break;
            case CONNECTED:
                b.status.setText(R.string.call_status_connected);
                b.timer.setVisibility(View.VISIBLE);
                b.timer.setBase(SystemClock.elapsedRealtime() - (System.currentTimeMillis() - s.connectedAt));
                b.timer.start();
                break;
            case ENDED:
                b.status.setText(endText(s.endReason));
                b.getRoot().postDelayed(this::finish, 1200);
                break;
            default:
                break;
        }
        updateProximity(s.phase == CallManager.Phase.CONNECTED && !s.speaker);
    }

    private int endText(@Nullable String reason) {
        if (CallSession.REJECTED.equals(reason)) return R.string.call_end_declined;
        if (CallSession.BUSY.equals(reason)) return R.string.call_end_busy;
        if (CallManager.END_NO_ANSWER.equals(reason) || CallSession.MISSED.equals(reason)) return R.string.call_end_no_answer;
        if (CallManager.END_FAILED.equals(reason)) return R.string.call_end_failed;
        return R.string.call_end_ended;
    }

    /** Screen off when the phone is held to the ear. */
    private void updateProximity(boolean on) {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (on && proximity == null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "RideAlong:proximity");
            proximity.acquire(2 * 60 * 60 * 1000L);
        } else if (!on && proximity != null) {
            if (proximity.isHeld()) proximity.release();
            proximity = null;
        }
    }

    @Override
    protected void onDestroy() {
        updateProximity(false);
        b.timer.stop();
        super.onDestroy();
    }
}