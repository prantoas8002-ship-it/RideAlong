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

import org.webrtc.RendererCommon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Full-screen call UI for voice and video calls (outgoing, incoming, in-call). Shows over the lock
 * screen for incoming calls. Video: remote camera full screen, my camera as a small preview.
 */
public class CallActivity extends AppCompatActivity {
    public static final String EXTRA_AUTO_ACCEPT = "auto_accept";
    private static final String EXTRA_PEER_ID = "peer_id";
    private static final String EXTRA_PEER_NAME = "peer_name";
    private static final String EXTRA_RIDE_ID = "ride_id";
    private static final String EXTRA_CHAT_ID = "chat_id";
    private static final String EXTRA_VIDEO = "video";

    private ActivityCallBinding b;
    private CallManager calls;
    private PowerManager.WakeLock proximity;
    private boolean renderersReady;
    /** What to do once permissions are answered. */
    private Runnable afterPermission;

    private final ActivityResultLauncher<String[]> permissions =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissions);

    /** Start an in-app voice or video call to another passenger (no phone number involved). */
    public static void startOutgoing(Context c, String peerId, String peerName, String rideId, @Nullable String chatId,
                                     boolean video) {
        c.startActivity(new Intent(c, CallActivity.class)
                .putExtra(EXTRA_PEER_ID, peerId)
                .putExtra(EXTRA_PEER_NAME, peerName)
                .putExtra(EXTRA_RIDE_ID, rideId)
                .putExtra(EXTRA_CHAT_ID, chatId == null ? "" : chatId)
                .putExtra(EXTRA_VIDEO, video));
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

        b.accept.setOnClickListener(v -> {
            CallManager.State s = calls.state().getValue();
            withPermissions(s != null && s.video, calls::accept);
        });
        b.decline.setOnClickListener(v -> calls.decline());
        b.hangUp.setOnClickListener(v -> calls.hangUp());
        b.mute.setOnClickListener(v -> calls.toggleMute());
        b.speaker.setOnClickListener(v -> calls.toggleSpeaker());
        b.cameraToggle.setOnClickListener(v -> calls.toggleCamera());
        b.switchCamera.setOnClickListener(v -> calls.switchCamera());

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
            boolean video = i.getBooleanExtra(EXTRA_VIDEO, false);
            showPeer(peerId, name);
            withPermissions(video, () -> {
                if (!calls.startOutgoing(peerId, name, ride, chat, video)) finish();
            });
        } else if (i.getBooleanExtra(EXTRA_AUTO_ACCEPT, false)) {
            CallManager.State s = calls.state().getValue();
            withPermissions(s != null && s.video, calls::accept);
        }
    }

    // ----- permissions: microphone required; camera optional (video falls back to voice-only sending) -----

    private boolean granted(String p) {
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED;
    }

    private void withPermissions(boolean video, Runnable r) {
        List<String> need = new ArrayList<>();
        if (!granted(Manifest.permission.RECORD_AUDIO)) need.add(Manifest.permission.RECORD_AUDIO);
        if (video && !granted(Manifest.permission.CAMERA)) need.add(Manifest.permission.CAMERA);
        if (need.isEmpty()) {
            r.run();
            return;
        }
        afterPermission = r;
        permissions.launch(need.toArray(new String[0]));
    }

    private void onPermissions(Map<String, Boolean> result) {
        Runnable r = afterPermission;
        afterPermission = null;
        if (!granted(Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this, R.string.call_mic_needed, Toast.LENGTH_LONG).show();
            CallManager.State s = calls.state().getValue();
            if (s != null && s.phase == CallManager.Phase.INCOMING) calls.decline();
            finish();
            return;
        }
        if (Boolean.FALSE.equals(result.get(Manifest.permission.CAMERA))) {
            Toast.makeText(this, R.string.call_camera_denied, Toast.LENGTH_LONG).show();
        }
        if (r != null) r.run();
    }

    // ----- video renderers -----

    private void ensureRenderers() {
        if (renderersReady) return;
        renderersReady = true;
        b.remoteVideo.init(calls.eglContext(), null);
        b.remoteVideo.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        b.remoteVideo.setEnableHardwareScaler(true);
        b.localVideo.init(calls.eglContext(), null);
        b.localVideo.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        b.localVideo.setZOrderMediaOverlay(true); // draw the preview above the remote video
        b.localVideo.setEnableHardwareScaler(true);
        calls.attachVideo(b.localVideo, b.remoteVideo);
    }

    @Override
    protected void onStart() {
        super.onStart();
        CallManager.State s = calls.state().getValue();
        if (s != null && s.video) {
            ensureRenderers();
            calls.attachVideo(b.localVideo, b.remoteVideo);
        }
        calls.onCallScreenVisible(true);
    }

    @Override
    protected void onStop() {
        // Privacy + battery: no camera capture while the call screen is not visible.
        calls.onCallScreenVisible(false);
        super.onStop();
    }

    // ----- rendering -----

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
        if (s.video) ensureRenderers();
        boolean incoming = s.phase == CallManager.Phase.INCOMING;
        boolean live = s.phase == CallManager.Phase.CONNECTING || s.phase == CallManager.Phase.CONNECTED
                || s.phase == CallManager.Phase.OUTGOING;
        b.incomingControls.setVisibility(incoming ? View.VISIBLE : View.GONE);
        b.callControls.setVisibility(live ? View.VISIBLE : View.GONE);

        // Video surfaces
        boolean showRemote = s.video && s.remoteVideo && s.phase == CallManager.Phase.CONNECTED;
        b.remoteVideo.setVisibility(showRemote ? View.VISIBLE : View.GONE);
        b.videoScrim.setVisibility(showRemote ? View.VISIBLE : View.GONE);
        b.localVideo.setVisibility(s.video && s.cameraOn && live ? View.VISIBLE : View.GONE);
        b.localVideo.setMirror(s.frontCamera);
        b.avatar.setVisibility(showRemote ? View.GONE : View.VISIBLE);
        b.videoControls.setVisibility(s.video && live ? View.VISIBLE : View.GONE);
        b.cameraToggle.setImageResource(s.cameraOn ? R.drawable.ic_videocam : R.drawable.ic_videocam_off);
        b.switchCamera.setEnabled(s.cameraOn);
        b.switchCamera.setAlpha(s.cameraOn ? 1f : 0.4f);
        b.label.setText(s.video ? R.string.call_label_video : R.string.call_label);

        b.mute.setImageResource(s.muted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        b.speaker.setAlpha(s.speaker ? 1f : 0.6f);
        b.timer.stop();
        b.timer.setVisibility(View.GONE);
        switch (s.phase) {
            case OUTGOING:
                b.status.setText(R.string.call_status_calling);
                break;
            case INCOMING:
                b.status.setText(s.video ? R.string.call_incoming_video_title : R.string.call_incoming_title);
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
        // Ear-to-phone screen-off only makes sense for voice calls on the earpiece.
        updateProximity(s.phase == CallManager.Phase.CONNECTED && !s.speaker && !s.video);
    }

    private int endText(@Nullable String reason) {
        if (CallSession.REJECTED.equals(reason)) return R.string.call_end_declined;
        if (CallSession.BUSY.equals(reason)) return R.string.call_end_busy;
        if (CallManager.END_NO_ANSWER.equals(reason) || CallSession.MISSED.equals(reason)) return R.string.call_end_no_answer;
        if (CallManager.END_FAILED.equals(reason)) return R.string.call_end_failed;
        return R.string.call_end_ended;
    }

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
        if (renderersReady) {
            calls.attachVideo(null, null);
            b.localVideo.release();
            b.remoteVideo.release();
        }
        super.onDestroy();
    }
}
