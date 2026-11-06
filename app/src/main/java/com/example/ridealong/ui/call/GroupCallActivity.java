package com.example.ridealong.ui.call;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.GroupCall;
import com.example.ridealong.data.repository.GroupCallRepository;
import com.example.ridealong.databinding.ActivityGroupCallBinding;
import com.example.ridealong.service.call.GroupCallManager;
import com.example.ridealong.service.call.IncomingCallWatcher;
import com.example.ridealong.ui.common.Avatars;
import com.example.ridealong.ui.common.Me;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Group video call screen for ride members: invite (Join / Decline), then a grid with my camera and
 * one tile per other person (avatar while their video is not live yet).
 */
public class GroupCallActivity extends AppCompatActivity {
    public static final String EXTRA_CALL_ID = "group_call_id";
    public static final String EXTRA_AUTO_JOIN = "auto_join";
    private static final String EXTRA_RIDE_ID = "ride_id";
    private static final String EXTRA_INVITED = "invited";

    private ActivityGroupCallBinding b;
    private GroupCallManager calls;
    /** uid → tile; "" is me. */
    private final Map<String, Tile> tiles = new LinkedHashMap<>();
    private String layoutKey = "";
    private boolean loading;
    private Runnable afterPermission;
    private boolean finishing;

    private final ActivityResultLauncher<String[]> permissions =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissions);

    /** Start a new group video call for a ride ({@code invited} = ride members, me included). */
    public static void start(Context c, String rideId, ArrayList<String> invited) {
        c.startActivity(new Intent(c, GroupCallActivity.class)
                .putExtra(EXTRA_RIDE_ID, rideId)
                .putStringArrayListExtra(EXTRA_INVITED, invited));
    }

    /** Join a group call that is already running (from the ride chat). */
    public static void join(Context c, String callId) {
        c.startActivity(new Intent(c, GroupCallActivity.class)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_AUTO_JOIN, true));
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
        b = ActivityGroupCallBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        calls = GroupCallManager.get(this);
        IncomingCallWatcher.cancelGroupInviteNotification(this);

        b.join.setOnClickListener(v -> {
            GroupCall g = calls.pendingInvite();
            if (g != null) withPermissions(() -> calls.join(g));
        });
        b.decline.setOnClickListener(v -> calls.declineInvite());
        b.leave.setOnClickListener(v -> calls.leave());
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
        String rideId = i.getStringExtra(EXTRA_RIDE_ID);
        ArrayList<String> invited = i.getStringArrayListExtra(EXTRA_INVITED);
        String callId = i.getStringExtra(EXTRA_CALL_ID);
        boolean autoJoin = i.getBooleanExtra(EXTRA_AUTO_JOIN, false);
        GroupCallManager.State s = calls.state().getValue();
        if (rideId != null && invited != null) {
            if (calls.isBusy()) return; // already showing a call
            withPermissions(() -> {
                if (!calls.start(rideId, invited)) {
                    Toast.makeText(this, R.string.group_call_busy, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
            return;
        }
        if (callId == null) return;
        if (s != null && callId.equals(s.callId) && calls.isBusy()) {
            GroupCall g = calls.pendingInvite();
            if (autoJoin && g != null) withPermissions(() -> calls.join(g));
            return;
        }
        // Not ringing here (joined from the chat, or the app process was restarted): load the call.
        loading = true;
        new GroupCallRepository().ref(callId).get().addOnCompleteListener(t -> {
            loading = false;
            GroupCall g = t.isSuccessful() && t.getResult() != null ? t.getResult().toObject(GroupCall.class) : null;
            if (g == null || !g.isActive()) {
                Toast.makeText(this, R.string.call_end_ended, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            if (autoJoin) {
                withPermissions(() -> {
                    if (!calls.join(g)) {
                        Toast.makeText(this, R.string.group_call_busy, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            } else {
                calls.onInvite(g, true);
            }
        });
    }

    // ----- permissions: microphone required; camera optional (others still see my avatar) -----

    private boolean granted(String p) {
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED;
    }

    private void withPermissions(Runnable r) {
        List<String> need = new ArrayList<>();
        if (!granted(Manifest.permission.RECORD_AUDIO)) need.add(Manifest.permission.RECORD_AUDIO);
        if (!granted(Manifest.permission.CAMERA)) need.add(Manifest.permission.CAMERA);
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
            calls.declineInvite();
            finish();
            return;
        }
        if (Boolean.FALSE.equals(result.get(Manifest.permission.CAMERA))) {
            Toast.makeText(this, R.string.call_camera_denied, Toast.LENGTH_LONG).show();
        }
        if (r != null) r.run();
    }

    @Override
    protected void onStart() {
        super.onStart();
        calls.onCallScreenVisible(true);
    }

    @Override
    protected void onStop() {
        // Privacy + battery: no camera capture while the call screen is not visible.
        calls.onCallScreenVisible(false);
        super.onStop();
    }

    // ----- rendering -----

    private void render(GroupCallManager.State s) {
        if (s == null) return;
        if (s.phase == GroupCallManager.Phase.IDLE) {
            if (afterPermission == null && !loading && !finishing) finish();
            return;
        }
        boolean invited = s.phase == GroupCallManager.Phase.INVITED;
        b.invite.setVisibility(invited ? View.VISIBLE : View.GONE);
        if (invited) {
            String host = s.hostName == null || s.hostName.isEmpty() ? getString(R.string.unknown_user) : s.hostName;
            b.inviteText.setText(getString(R.string.group_call_incoming_body, host));
            GroupCall g = calls.pendingInvite();
            if (g != null && g.hostId != null) loadAvatar(b.inviteAvatar, g.hostId);
            return;
        }
        boolean live = s.phase == GroupCallManager.Phase.JOINING || s.phase == GroupCallManager.Phase.IN_CALL;
        b.callControls.setVisibility(live ? View.VISIBLE : View.INVISIBLE);
        b.mute.setImageResource(s.muted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        b.speaker.setAlpha(s.speaker ? 1f : 0.6f);
        b.cameraToggle.setImageResource(s.cameraOn ? R.drawable.ic_videocam : R.drawable.ic_videocam_off);
        b.switchCamera.setEnabled(s.cameraOn);
        b.switchCamera.setAlpha(s.cameraOn ? 1f : 0.4f);

        b.timer.stop();
        b.timer.setVisibility(View.GONE);
        if (s.phase == GroupCallManager.Phase.JOINING) {
            b.subtitle.setText(R.string.group_call_joining);
        } else if (s.phase == GroupCallManager.Phase.IN_CALL) {
            int people = s.peers.size() + 1;
            b.subtitle.setText(s.peers.isEmpty() ? getString(R.string.group_call_waiting)
                    : getResources().getQuantityString(R.plurals.group_call_people, people, people));
            if (s.joinedAt > 0) {
                b.timer.setVisibility(View.VISIBLE);
                b.timer.setBase(SystemClock.elapsedRealtime() - (System.currentTimeMillis() - s.joinedAt));
                b.timer.start();
            }
        }
        if (live) syncTiles(s);
        if (s.phase == GroupCallManager.Phase.ENDED) {
            finishing = true;
            b.ended.setVisibility(View.VISIBLE);
            b.ended.setText(endText(s.endReason));
            b.getRoot().postDelayed(this::finish, 1200);
        }
    }

    private int endText(@Nullable String reason) {
        if (GroupCallManager.END_LEFT.equals(reason)) return R.string.group_call_end_left;
        if (GroupCallManager.END_FULL.equals(reason)) return R.string.group_call_end_full;
        if (GroupCallManager.END_MISSED.equals(reason)) return R.string.group_call_end_missed;
        if (GroupCallManager.END_FAILED.equals(reason)) return R.string.call_end_failed;
        return R.string.call_end_ended;
    }

    // ----- tiles -----

    private final class Tile {
        final String uid;
        final boolean mine;
        final FrameLayout root;
        final SurfaceViewRenderer video;
        final ShapeableImageView avatar;
        final TextView name;
        final TextView status;

        Tile(String uid, boolean mine) {
            this.uid = uid;
            this.mine = mine;
            Context c = GroupCallActivity.this;
            int pad = getResources().getDimensionPixelSize(R.dimen.space_s);
            root = new FrameLayout(c);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#26FFFFFF"));
            bg.setCornerRadius(getResources().getDimension(R.dimen.radius_m));
            root.setBackground(bg);
            root.setClipToOutline(true);

            avatar = new ShapeableImageView(c);
            avatar.setShapeAppearanceModel(ShapeAppearanceModel.builder().setAllCornerSizes(new RelativeCornerSize(0.5f)).build());
            avatar.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            avatar.setBackgroundColor(ContextCompat.getColor(c, R.color.ra_primary_container));
            avatar.setContentDescription(getString(R.string.cd_avatar));
            int size = getResources().getDimensionPixelSize(R.dimen.avatar_l);
            root.addView(avatar, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));

            video = new SurfaceViewRenderer(c);
            video.init(calls.eglContext(), null);
            video.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
            video.setEnableHardwareScaler(true);
            video.setVisibility(View.GONE);
            root.addView(video, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout label = new LinearLayout(c);
            label.setOrientation(LinearLayout.VERTICAL);
            label.setPadding(pad, pad, pad, pad);
            name = new TextView(c);
            name.setTextColor(Color.WHITE);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            name.setShadowLayer(4f, 0f, 1f, Color.BLACK);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            status = new TextView(c);
            status.setTextColor(Color.parseColor("#CCFFFFFF"));
            status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            status.setShadowLayer(4f, 0f, 1f, Color.BLACK);
            label.addView(name);
            label.addView(status);
            root.addView(label, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));

            if (mine) {
                name.setText(getString(R.string.group_call_you));
                calls.attachLocal(video);
                loadAvatar(avatar, Me.uid());
            } else {
                calls.attachRemote(uid, video);
                loadAvatar(avatar, uid);
            }
        }

        void release() {
            if (mine) calls.attachLocal(null);
            else calls.attachRemote(uid, null);
            video.release();
        }
    }

    private void loadAvatar(android.widget.ImageView view, @Nullable String uid) {
        if (uid == null) return;
        ServiceLocator.get().users().getUser(uid).addOnSuccessListener(u -> {
            if (u != null && b != null) Avatars.load(view, u.photoBase64);
        });
    }

    private void syncTiles(GroupCallManager.State s) {
        Set<String> wanted = new HashSet<>();
        wanted.add("");
        for (GroupCallManager.Peer p : s.peers) wanted.add(p.uid);
        for (String uid : new ArrayList<>(tiles.keySet())) {
            if (!wanted.contains(uid)) {
                Tile t = tiles.remove(uid);
                if (t != null) {
                    detach(t.root);
                    t.release();
                }
            }
        }
        if (!tiles.containsKey("")) tiles.put("", new Tile("", true));
        for (GroupCallManager.Peer p : s.peers) if (!tiles.containsKey(p.uid)) tiles.put(p.uid, new Tile(p.uid, false));

        // Me first, then everyone in the manager's (name) order.
        List<Tile> ordered = new ArrayList<>();
        ordered.add(tiles.get(""));
        for (GroupCallManager.Peer p : s.peers) ordered.add(tiles.get(p.uid));
        StringBuilder key = new StringBuilder();
        for (Tile t : ordered) key.append(t.uid).append('|');
        if (!key.toString().equals(layoutKey)) {
            layoutKey = key.toString();
            layoutGrid(ordered);
        }

        Tile mine = tiles.get("");
        mine.video.setMirror(s.frontCamera);
        mine.video.setVisibility(s.cameraOn ? View.VISIBLE : View.GONE);
        mine.status.setText(R.string.group_call_muted);
        mine.status.setVisibility(s.muted ? View.VISIBLE : View.GONE);
        for (GroupCallManager.Peer p : s.peers) {
            Tile t = tiles.get(p.uid);
            t.name.setText(p.name == null || p.name.isEmpty() ? getString(R.string.unknown_user) : p.name);
            t.video.setVisibility(p.connected && p.hasVideo ? View.VISIBLE : View.GONE);
            t.status.setText(R.string.group_call_connecting_peer);
            t.status.setVisibility(p.connected ? View.GONE : View.VISIBLE);
        }
    }

    /** 1 → full screen, 2 → stacked, 3–4 → 2×2, 5–6 → 3 rows of 2. */
    private void layoutGrid(List<Tile> ordered) {
        for (Tile t : ordered) detach(t.root);
        b.grid.removeAllViews();
        int n = ordered.size();
        int cols = n <= 2 ? 1 : 2;
        int margin = getResources().getDimensionPixelSize(R.dimen.space_xs);
        for (int i = 0; i < n; i += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            b.grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            for (int j = i; j < Math.min(n, i + cols); j++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                lp.setMargins(margin, margin, margin, margin);
                row.addView(ordered.get(j).root, lp);
            }
        }
    }

    private static void detach(View v) {
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    @Override
    protected void onDestroy() {
        b.timer.stop();
        for (Tile t : tiles.values()) t.release();
        tiles.clear();
        super.onDestroy();
    }
}
