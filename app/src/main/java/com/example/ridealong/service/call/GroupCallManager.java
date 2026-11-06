package com.example.ridealong.service.call;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.GroupCall;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.repository.GroupCallRepository;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.service.push.PushClient;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.utils.AppExecutors;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Group video calls for the members of one ride: a full WebRTC mesh (one PeerConnection per other
 * person) signaled through Firestore. Only one call (1:1 or group) at a time; all state changes
 * happen on the main thread and are published through {@link #state()}.
 *
 * Joining: write my presence doc (members/{me}, new random session) → offer to everyone already
 * in the call → people who join after me offer to me. If two people offer to each other at the same
 * moment, the offer from the smaller uid wins on both phones.
 */
public final class GroupCallManager {
    private static final String TAG = "GroupCallManager";

    public enum Phase { IDLE, INVITED, JOINING, IN_CALL, ENDED }

    public static final String END_LEFT = "LEFT";
    public static final String END_ENDED = "ENDED";
    public static final String END_FAILED = "FAILED";
    public static final String END_FULL = "FULL";
    public static final String END_MISSED = "MISSED";
    public static final String END_DECLINED = "DECLINED";

    /** One other person in the call. */
    public static final class Peer {
        public final String uid;
        public final String name;
        public final boolean connected;
        public final boolean hasVideo;

        Peer(String uid, String name, boolean connected, boolean hasVideo) {
            this.uid = uid;
            this.name = name;
            this.connected = connected;
            this.hasVideo = hasVideo;
        }
    }

    /** Immutable snapshot for the UI. */
    public static final class State {
        public final Phase phase;
        @Nullable public final String callId;
        @Nullable public final String rideId;
        @Nullable public final String hostName;
        public final boolean muted;
        public final boolean speaker;
        public final boolean cameraOn;
        public final boolean frontCamera;
        public final long joinedAt;
        public final List<Peer> peers;
        @Nullable public final String endReason;

        State(Phase phase, String callId, String rideId, String hostName, boolean muted, boolean speaker, boolean cameraOn,
              boolean frontCamera, long joinedAt, List<Peer> peers, String endReason) {
            this.phase = phase;
            this.callId = callId;
            this.rideId = rideId;
            this.hostName = hostName;
            this.muted = muted;
            this.speaker = speaker;
            this.cameraOn = cameraOn;
            this.frontCamera = frontCamera;
            this.joinedAt = joinedAt;
            this.peers = Collections.unmodifiableList(peers);
            this.endReason = endReason;
        }

        static State idle() {
            return new State(Phase.IDLE, null, null, null, false, true, false, true, 0, new ArrayList<>(), null);
        }
    }

    /** Presence info from members/{uid}. */
    private static final class Member {
        String name;
        String session;
        long seenAt;
    }

    /** One peer connection to one other person. */
    private final class Link {
        final String uid;
        final String linkId;
        final boolean offerer;
        String name;
        PeerConnection pc;
        boolean remoteSet;
        boolean offerWritten;
        boolean connected;
        final List<IceCandidate> pendingRemote = new ArrayList<>();
        final List<IceCandidate> pendingLocal = new ArrayList<>();
        ListenerRegistration linkReg;
        ListenerRegistration candidateReg;
        VideoTrack remoteVideo;
        final Runnable disconnectGrace = () -> closeLink(this.uidRef());

        Link(String uid, String linkId, boolean offerer, String name) {
            this.uid = uid;
            this.linkId = linkId;
            this.offerer = offerer;
            this.name = name;
        }

        String uidRef() { return uid; }
    }

    private static GroupCallManager instance;

    private final Context app;
    private final GroupCallRepository repo = new GroupCallRepository();
    private final CallAudio audio;
    private final MutableLiveData<State> state = new MutableLiveData<>(State.idle());

    // Local media (shared by every link)
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private CameraVideoCapturer capturer;
    private SurfaceTextureHelper textureHelper;
    private VideoSource videoSource;
    private VideoTrack localVideo;
    private VideoSink localSink;
    private boolean capturing;

    private final Map<String, Link> links = new LinkedHashMap<>();
    private final Map<String, Member> members = new HashMap<>();
    private final Map<String, VideoSink> remoteSinks = new HashMap<>();

    private ListenerRegistration callReg;
    private ListenerRegistration membersReg;
    private ListenerRegistration offersReg;

    // Current call context
    private String callId;
    private String rideId;
    private String hostName;
    private String me;
    private String session;
    private GroupCall invite;
    private long callStartedAt;
    private long joinedAt;
    private boolean muted;
    private boolean speaker = true;
    private boolean cameraOn;
    private boolean frontCamera = true;
    private boolean firstMembersSnapshot;
    private int notifiedPeers = -1;

    private final Runnable heartbeat = this::onHeartbeat;
    private final Runnable inviteTimeout = () -> {
        if (current().phase == Phase.INVITED) finish(END_MISSED);
    };

    private GroupCallManager(Context c) {
        app = c.getApplicationContext();
        audio = new CallAudio(app);
    }

    public static synchronized GroupCallManager get(Context c) {
        if (instance == null) instance = new GroupCallManager(c);
        return instance;
    }

    public LiveData<State> state() { return state; }

    /** Ringing, joining or in a group call. */
    public boolean isBusy() {
        Phase p = current().phase;
        return p == Phase.INVITED || p == Phase.JOINING || p == Phase.IN_CALL;
    }

    /** Joining or in a group call (media in use). */
    public boolean isInCall() {
        Phase p = current().phase;
        return p == Phase.JOINING || p == Phase.IN_CALL;
    }

    private State current() {
        State s = state.getValue();
        return s == null ? State.idle() : s;
    }

    private void publish(Phase phase, @Nullable String endReason) {
        List<Peer> peers = new ArrayList<>();
        for (Link l : links.values()) peers.add(new Peer(l.uid, l.name, l.connected, l.remoteVideo != null));
        Collections.sort(peers, (a, b) -> String.valueOf(a.name).compareToIgnoreCase(String.valueOf(b.name)));
        state.setValue(new State(phase, callId, rideId, hostName, muted, speaker, cameraOn, frontCamera, joinedAt, peers,
                endReason));
        if (phase == Phase.IN_CALL && peers.size() != notifiedPeers) {
            notifiedPeers = peers.size();
            CallService.update(app); // "3 people" in the ongoing notification
        }
    }

    private void republish() { publish(current().phase, current().endReason); }

    public EglBase.Context eglContext() { return CallManager.get(app).eglContext(); }

    // ------------------------------------------------------------------ start / invite / join

    /**
     * Starts a new group video call for a ride and joins it. {@code invited} must be ride members
     * (me included). Caller must already hold RECORD_AUDIO (CAMERA optional).
     */
    public boolean start(String rideId, List<String> invited) {
        String uid = FirebaseProvider.uidOrNull();
        if (uid == null || isBusy() || CallManager.get(app).isBusy()) return false;
        reset();
        DocumentReference ref = repo.newCall();
        this.callId = ref.getId();
        this.rideId = rideId;
        this.hostName = Me.name();
        this.callStartedAt = System.currentTimeMillis();
        publish(Phase.JOINING, null);
        String id = callId;
        repo.create(ref, rideId, Me.name(), true, invited)
                .addOnSuccessListener(v -> {
                    if (!id.equals(callId)) return;
                    PushClient.send("groupCall", id);
                    ServiceLocator.get().chat().logCall(rideId, Me.name(), "", app.getString(R.string.group_call_log_started,
                            Me.name()));
                    joinNow();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "create group call failed", e);
                    finish(END_FAILED);
                });
        return true;
    }

    /** A group call I was invited to just started (live listener or push). */
    public void onInvite(GroupCall g, boolean ringInApp) {
        if (isBusy() || CallManager.get(app).isBusy()) return;
        reset();
        invite = g;
        callId = g.id;
        rideId = g.rideId;
        hostName = g.hostName;
        callStartedAt = g.createdAt == null ? System.currentTimeMillis() : g.createdAt.toDate().getTime();
        publish(Phase.INVITED, null);
        if (ringInApp) audio.startRinging();
        listenCallDoc();
        AppExecutors.mainDelayed(inviteTimeout, AppConfig.get().callRingTimeoutMs);
    }

    /** Stop ringing for a group invite (declined, or a 1:1 call took over). */
    public void declineInvite() {
        if (current().phase != Phase.INVITED) return;
        finish(END_DECLINED);
    }

    /** Join a running call (from the invite, or from the ride chat). Caller must already hold RECORD_AUDIO. */
    public boolean join(GroupCall g) {
        if (FirebaseProvider.uidOrNull() == null || CallManager.get(app).isBusy()) return false;
        if (current().phase == Phase.INVITED && g.id.equals(callId)) {
            AppExecutors.cancelMain(inviteTimeout);
            audio.stopTones();
            publish(Phase.JOINING, null);
            joinNow();
            return true;
        }
        if (isBusy()) return g.id.equals(callId);
        reset();
        callId = g.id;
        rideId = g.rideId;
        hostName = g.hostName;
        callStartedAt = g.createdAt == null ? System.currentTimeMillis() : g.createdAt.toDate().getTime();
        publish(Phase.JOINING, null);
        joinNow();
        return true;
    }

    /** Presence first, then media + signaling. */
    private void joinNow() {
        me = FirebaseProvider.uidOrNull();
        if (me == null || callId == null) {
            finish(END_FAILED);
            return;
        }
        String id = callId;
        // Room check: count fresh members before taking a seat.
        repo.members(id).get().addOnCompleteListener(t -> {
            if (!id.equals(callId) || current().phase != Phase.JOINING) return;
            int others = 0;
            if (t.isSuccessful() && t.getResult() != null) {
                long now = System.currentTimeMillis();
                for (DocumentSnapshot d : t.getResult().getDocuments()) {
                    if (d.getId().equals(me)) continue;
                    Timestamp seen = d.getTimestamp("seenAt");
                    if (seen != null && now - seen.toDate().getTime() < AppConfig.get().groupCallMemberStaleMs) others++;
                }
            }
            if (others >= AppConfig.get().groupCallMaxParticipants) {
                finish(END_FULL);
                return;
            }
            session = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            CallService.start(app);
            audio.enterCallMode(speaker);
            if (!createLocalMedia()) {
                finish(END_FAILED);
                return;
            }
            repo.heartbeat(id, me, Me.name(), session)
                    .addOnSuccessListener(v -> {
                        if (!id.equals(callId)) return;
                        joinedAt = System.currentTimeMillis();
                        publish(Phase.IN_CALL, null);
                        CallService.update(app);
                        if (callReg == null) listenCallDoc();
                        listenOffers();
                        listenMembers();
                        AppExecutors.cancelMain(heartbeat);
                        AppExecutors.mainDelayed(heartbeat, AppConfig.get().groupCallHeartbeatMs);
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "join failed", e);
                        finish(END_FAILED);
                    });
        });
    }

    private void listenCallDoc() {
        callReg = repo.ref(callId).addSnapshotListener((snap, err) -> {
            if (err != null || snap == null || !snap.exists()) return;
            GroupCall g = snap.toObject(GroupCall.class);
            if (g != null && !g.isActive()) finish(END_ENDED);
        });
    }

    private void onHeartbeat() {
        if (!isInCall() || callId == null || me == null) return;
        repo.heartbeat(callId, me, Me.name(), session);
        dropStaleMembers();
        AppExecutors.mainDelayed(heartbeat, AppConfig.get().groupCallHeartbeatMs);
    }

    private boolean isFresh(Member m) {
        return m != null && (m.seenAt == 0 || System.currentTimeMillis() - m.seenAt < AppConfig.get().groupCallMemberStaleMs);
    }

    private void dropStaleMembers() {
        for (String uid : new ArrayList<>(links.keySet())) {
            if (!isFresh(members.get(uid))) closeLink(uid);
        }
    }

    // ------------------------------------------------------------------ presence + offers

    private void listenMembers() {
        firstMembersSnapshot = true;
        String id = callId;
        membersReg = repo.members(id).addSnapshotListener((snap, err) -> {
            if (err != null || snap == null || !id.equals(callId)) return;
            onMembers(snap);
        });
    }

    private void onMembers(QuerySnapshot snap) {
        long now = System.currentTimeMillis();
        for (DocumentChange ch : snap.getDocumentChanges()) {
            String uid = ch.getDocument().getId();
            if (uid.equals(me)) continue;
            if (ch.getType() == DocumentChange.Type.REMOVED) {
                members.remove(uid);
                closeLink(uid);
                continue;
            }
            Member m = new Member();
            m.name = ch.getDocument().getString("name");
            m.session = ch.getDocument().getString("session");
            Timestamp seen = ch.getDocument().getTimestamp("seenAt");
            // First sighting: trust the server time (filters out people who crashed long ago). Later
            // heartbeats: use my own clock, so clock skew between phones can't drop anyone.
            m.seenAt = ch.getType() == DocumentChange.Type.MODIFIED ? now : seen == null ? 0 : seen.toDate().getTime();
            Member old = members.put(uid, m);
            Link l = links.get(uid);
            if (l != null && m.name != null && !m.name.equals(l.name)) {
                l.name = m.name;
                republish();
            }
            boolean rejoined = old != null && old.session != null && !old.session.equals(m.session);
            if (rejoined) closeLink(uid); // they left and came back: they will offer to me again
        }
        // I offer to everyone who was already here when I joined (first server snapshot). Newcomers offer to me.
        if (firstMembersSnapshot && !snap.getMetadata().isFromCache()) {
            firstMembersSnapshot = false;
            for (Map.Entry<String, Member> e : members.entrySet()) {
                if (isFresh(e.getValue()) && e.getValue().session != null) offerTo(e.getKey(), e.getValue());
            }
        }
    }

    private boolean hidden(String uid) {
        return ServiceLocator.get().safety().hiddenUserIds().contains(uid);
    }

    private void listenOffers() {
        String id = callId;
        offersReg = repo.offersTo(id, me, session).addSnapshotListener((snap, err) -> {
            if (err != null || snap == null || !id.equals(callId)) return;
            for (DocumentChange ch : snap.getDocumentChanges()) {
                if (ch.getType() != DocumentChange.Type.ADDED) continue;
                onOffer(ch.getDocument());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void onOffer(DocumentSnapshot d) {
        String from = d.getString("from");
        Object offerObj = d.get("offer");
        if (from == null || from.equals(me) || !(offerObj instanceof Map) || hidden(from)) return;
        Link existing = links.get(from);
        if (existing != null) {
            if (existing.linkId.equals(d.getId())) return; // already handling this offer
            // Glare: both of us offered. The offer from the smaller uid wins on both phones.
            if (existing.offerer && me.compareTo(from) < 0) return;
            closeLink(from);
        }
        if (links.size() >= AppConfig.get().groupCallMaxParticipants - 1) return;
        Member m = members.get(from);
        Link l = new Link(from, d.getId(), false, m != null && m.name != null ? m.name : "");
        links.put(from, l);
        if (!createPeer(l)) return;
        Map<String, String> offer = (Map<String, String>) offerObj;
        SessionDescription remote = new SessionDescription(SessionDescription.Type.fromCanonicalForm(offer.get("type")),
                offer.get("sdp"));
        l.candidateReg = listenCandidates(l, true);
        l.pc.setRemoteDescription(new SimpleSdp() {
            @Override
            public void onSetSuccess() {
                AppExecutors.main(() -> {
                    if (links.get(l.uid) != l) return;
                    l.remoteSet = true;
                    drainPending(l);
                    l.pc.createAnswer(new SimpleSdp() {
                        @Override
                        public void onCreateSuccess(SessionDescription sdp) {
                            AppExecutors.main(() -> {
                                if (links.get(l.uid) != l) return;
                                l.pc.setLocalDescription(new SimpleSdp() {
                                    @Override
                                    public void onSetSuccess() {
                                        AppExecutors.main(() -> {
                                            if (links.get(l.uid) != l) return;
                                            repo.answer(callId, l.linkId, sdp.type.canonicalForm(), sdp.description)
                                                    .addOnFailureListener(e -> closeLink(l.uid));
                                        });
                                    }
                                }, sdp);
                            });
                        }
                    }, new MediaConstraints());
                });
            }
        }, remote);
        republish();
    }

    private void offerTo(String uid, Member m) {
        if (links.containsKey(uid) || hidden(uid)) return;
        if (links.size() >= AppConfig.get().groupCallMaxParticipants - 1) return;
        Link l = new Link(uid, GroupCallRepository.linkId(me, uid, session), true, m.name == null ? "" : m.name);
        links.put(uid, l);
        if (!createPeer(l)) return;
        String id = callId;
        String toSession = m.session;
        l.pc.createOffer(new SimpleSdp() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                AppExecutors.main(() -> {
                    if (links.get(uid) != l) return;
                    l.pc.setLocalDescription(new SimpleSdp() {
                        @Override
                        public void onSetSuccess() {
                            AppExecutors.main(() -> {
                                if (links.get(uid) != l) return;
                                repo.offer(id, me, uid, session, toSession, sdp.type.canonicalForm(), sdp.description)
                                        .addOnSuccessListener(v -> {
                                            l.offerWritten = true;
                                            for (IceCandidate c : l.pendingLocal) sendCandidate(l, c);
                                            l.pendingLocal.clear();
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.w(TAG, "offer failed", e);
                                            closeLink(uid);
                                        });
                            });
                        }
                    }, sdp);
                });
            }
        }, offerConstraints());
        l.linkReg = repo.links(id).document(l.linkId).addSnapshotListener((snap, err) -> {
            if (err != null || snap == null || !snap.exists() || links.get(uid) != l || l.remoteSet) return;
            Object ans = snap.get("answer");
            if (!(ans instanceof Map)) return;
            @SuppressWarnings("unchecked") Map<String, String> a = (Map<String, String>) ans;
            l.remoteSet = true;
            l.pc.setRemoteDescription(new SimpleSdp() {
                @Override
                public void onSetSuccess() { AppExecutors.main(() -> drainPending(l)); }
            }, new SessionDescription(SessionDescription.Type.fromCanonicalForm(a.get("type")), a.get("sdp")));
        });
        l.candidateReg = listenCandidates(l, false);
        republish();
    }

    /** Listens to the OTHER side's ICE candidates of one link. */
    private ListenerRegistration listenCandidates(Link l, boolean ofOfferer) {
        return repo.candidates(callId, l.linkId, ofOfferer).addSnapshotListener((snap, err) -> {
            if (err != null || snap == null || links.get(l.uid) != l) return;
            for (DocumentChange ch : snap.getDocumentChanges()) {
                if (ch.getType() != DocumentChange.Type.ADDED) continue;
                String mid = ch.getDocument().getString("sdpMid");
                Long idx = ch.getDocument().getLong("sdpMLineIndex");
                String cand = ch.getDocument().getString("candidate");
                if (cand == null || idx == null) continue;
                IceCandidate c = new IceCandidate(mid, idx.intValue(), cand);
                if (l.pc != null && l.remoteSet) l.pc.addIceCandidate(c);
                else l.pendingRemote.add(c);
            }
        });
    }

    private void drainPending(Link l) {
        if (l.pc == null || links.get(l.uid) != l) return;
        for (IceCandidate c : l.pendingRemote) l.pc.addIceCandidate(c);
        l.pendingRemote.clear();
    }

    private void onLocalCandidate(Link l, IceCandidate c) {
        if (links.get(l.uid) != l) return;
        if (l.offerer && !l.offerWritten) {
            l.pendingLocal.add(c); // the link doc must exist before its candidates (rules)
            return;
        }
        sendCandidate(l, c);
    }

    private void sendCandidate(Link l, IceCandidate c) {
        if (callId != null) repo.addCandidate(callId, l.linkId, l.offerer, c.sdpMid, c.sdpMLineIndex, c.sdp);
    }

    // ------------------------------------------------------------------ in-call controls

    /** Leave the call. The last person to leave marks it ENDED. */
    public void leave() {
        Phase p = current().phase;
        if (p == Phase.INVITED) {
            declineInvite();
            return;
        }
        if (!isInCall() || callId == null) return;
        String id = callId;
        String uid = me;
        boolean anyoneElse = false;
        for (Member m : members.values()) if (isFresh(m)) anyoneElse = true;
        if (uid != null) repo.leave(id, uid);
        if (!anyoneElse && joinedAt > 0) {
            repo.end(id);
            long secs = Math.max(0, (System.currentTimeMillis() - callStartedAt) / 1000);
            String dur = String.format(Locale.US, "%d:%02d", secs / 60, secs % 60);
            if (rideId != null) {
                ServiceLocator.get().chat().logCall(rideId, Me.name(), "", app.getString(R.string.group_call_log_ended, dur));
            }
        }
        finish(END_LEFT);
    }

    public void toggleMute() {
        muted = !muted;
        if (audioTrack != null) audioTrack.setEnabled(!muted);
        republish();
    }

    public void toggleSpeaker() {
        speaker = !speaker;
        audio.setSpeaker(speaker);
        republish();
    }

    public void toggleCamera() {
        if (localVideo == null) return;
        cameraOn = !cameraOn;
        localVideo.setEnabled(cameraOn);
        if (cameraOn) startCapture();
        else stopCapture();
        republish();
    }

    public void switchCamera() {
        if (capturer == null) return;
        capturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
            @Override
            public void onCameraSwitchDone(boolean isFront) {
                AppExecutors.main(() -> {
                    frontCamera = isFront;
                    republish();
                });
            }

            @Override
            public void onCameraSwitchError(String error) { Log.w(TAG, "switch camera: " + error); }
        });
    }

    /** My camera preview. Pass null to detach. */
    public void attachLocal(@Nullable VideoSink sink) {
        if (localVideo != null && localSink != null) localVideo.removeSink(localSink);
        localSink = sink;
        if (localVideo != null && sink != null) localVideo.addSink(sink);
    }

    /** Renderer for one person's video. Pass null to detach. */
    public void attachRemote(String uid, @Nullable VideoSink sink) {
        Link l = links.get(uid);
        VideoSink old = remoteSinks.remove(uid);
        if (l != null && l.remoteVideo != null && old != null) l.remoteVideo.removeSink(old);
        if (sink == null) return;
        remoteSinks.put(uid, sink);
        if (l != null && l.remoteVideo != null) l.remoteVideo.addSink(sink);
    }

    /** The call screen was hidden/shown: pause the camera while nobody can see the preview. */
    public void onCallScreenVisible(boolean visible) {
        if (localVideo == null) return;
        if (visible && cameraOn) startCapture();
        else if (!visible) stopCapture();
    }

    // ------------------------------------------------------------------ WebRTC

    private boolean createLocalMedia() {
        try {
            PeerConnectionFactory f = CallManager.get(app).factory();
            MediaConstraints ac = new MediaConstraints();
            ac.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
            ac.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
            audioSource = f.createAudioSource(ac);
            audioTrack = f.createAudioTrack("ridealong-group-audio", audioSource);
            audioTrack.setEnabled(!muted);
            boolean camAllowed = ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
            capturer = camAllowed ? createCapturer() : null;
            if (capturer != null) {
                textureHelper = SurfaceTextureHelper.create("RideAlongGroupCapture", eglContext());
                videoSource = f.createVideoSource(false);
                capturer.initialize(textureHelper, app, videoSource.getCapturerObserver());
                localVideo = f.createVideoTrack("ridealong-group-video", videoSource);
                localVideo.setEnabled(true);
                if (localSink != null) localVideo.addSink(localSink);
                cameraOn = true;
                startCapture();
            }
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "local media failed", t);
            return false;
        }
    }

    private boolean createPeer(Link l) {
        try {
            PeerConnection.RTCConfiguration cfg = new PeerConnection.RTCConfiguration(CallManager.iceServers());
            cfg.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
            cfg.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            l.pc = CallManager.get(app).factory().createPeerConnection(cfg, new Observer(l));
            if (l.pc == null) throw new IllegalStateException("createPeerConnection returned null");
            l.pc.addTrack(audioTrack, Collections.singletonList("ridealong-group"));
            if (localVideo != null) {
                l.pc.addTrack(localVideo, Collections.singletonList("ridealong-group"));
            } else {
                l.pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                        new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY));
            }
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "peer connection failed", t);
            closeLink(l.uid);
            return false;
        }
    }

    @Nullable
    private CameraVideoCapturer createCapturer() {
        CameraEnumerator en = Camera2Enumerator.isSupported(app) ? new Camera2Enumerator(app) : new Camera1Enumerator(true);
        for (boolean wantFront : new boolean[]{true, false}) {
            for (String name : en.getDeviceNames()) {
                if (en.isFrontFacing(name) == wantFront) {
                    CameraVideoCapturer c = en.createCapturer(name, null);
                    if (c != null) {
                        frontCamera = wantFront;
                        return c;
                    }
                }
            }
        }
        return null;
    }

    private void startCapture() {
        if (capturer == null || capturing) return;
        AppConfig cfg = AppConfig.get();
        try {
            capturer.startCapture(cfg.groupCallVideoWidth, cfg.groupCallVideoHeight, cfg.groupCallVideoFps);
            capturing = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "startCapture", e);
        }
    }

    private void stopCapture() {
        if (capturer == null || !capturing) return;
        try {
            capturer.stopCapture();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        capturing = false;
    }

    private static MediaConstraints offerConstraints() {
        MediaConstraints c = new MediaConstraints();
        c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        return c;
    }

    private void onIceState(Link l, PeerConnection.IceConnectionState s) {
        if (links.get(l.uid) != l) return;
        switch (s) {
            case CONNECTED:
            case COMPLETED:
                AppExecutors.cancelMain(l.disconnectGrace);
                if (!l.connected) {
                    l.connected = true;
                    republish();
                }
                break;
            case DISCONNECTED:
                AppExecutors.cancelMain(l.disconnectGrace);
                AppExecutors.mainDelayed(l.disconnectGrace, AppConfig.get().callReconnectGraceMs);
                break;
            case FAILED:
                closeLink(l.uid);
                break;
            default:
                break;
        }
    }

    private void onRemoteVideo(Link l, VideoTrack t) {
        if (links.get(l.uid) != l) return;
        VideoSink sink = remoteSinks.get(l.uid);
        if (l.remoteVideo != null && sink != null) l.remoteVideo.removeSink(sink);
        l.remoteVideo = t;
        if (sink != null) t.addSink(sink);
        republish();
    }

    // ------------------------------------------------------------------ teardown

    private void closeLink(String uid) {
        Link l = links.remove(uid);
        if (l == null) return;
        AppExecutors.cancelMain(l.disconnectGrace);
        if (l.linkReg != null) l.linkReg.remove();
        if (l.candidateReg != null) l.candidateReg.remove();
        VideoSink sink = remoteSinks.get(uid);
        try {
            if (l.remoteVideo != null && sink != null) l.remoteVideo.removeSink(sink);
            if (l.pc != null) {
                l.pc.close();
                l.pc.dispose();
            }
        } catch (Throwable t) {
            Log.w(TAG, "close link", t);
        }
        l.pc = null;
        l.remoteVideo = null;
        if (isBusy()) republish();
    }

    private void finish(String reason) {
        Phase p = current().phase;
        if (p == Phase.IDLE || p == Phase.ENDED) return;
        AppExecutors.cancelMain(inviteTimeout);
        AppExecutors.cancelMain(heartbeat);
        IncomingCallWatcher.cancelGroupInviteNotification(app);
        boolean hadMedia = p == Phase.JOINING || p == Phase.IN_CALL;
        if (hadMedia && callId != null && me != null && !END_LEFT.equals(reason)) repo.leave(callId, me);
        releaseAll();
        audio.exitCallMode();
        if (hadMedia) CallService.stop(app);
        publish(Phase.ENDED, reason);
        AppExecutors.mainDelayed(() -> {
            if (current().phase == Phase.ENDED) {
                reset();
                state.setValue(State.idle());
            }
        }, 1500);
    }

    private void releaseAll() {
        if (callReg != null) callReg.remove();
        if (membersReg != null) membersReg.remove();
        if (offersReg != null) offersReg.remove();
        callReg = null;
        membersReg = null;
        offersReg = null;
        for (String uid : new ArrayList<>(links.keySet())) closeLink(uid);
        links.clear();
        members.clear();
        try {
            if (localVideo != null && localSink != null) localVideo.removeSink(localSink);
            stopCapture();
            if (capturer != null) capturer.dispose();
            if (localVideo != null) localVideo.dispose();
            if (videoSource != null) videoSource.dispose();
            if (textureHelper != null) textureHelper.dispose();
            if (audioTrack != null) audioTrack.dispose();
            if (audioSource != null) audioSource.dispose();
        } catch (Throwable t) {
            Log.w(TAG, "release", t);
        }
        capturer = null;
        localVideo = null;
        videoSource = null;
        textureHelper = null;
        audioTrack = null;
        audioSource = null;
        capturing = false;
    }

    private void reset() {
        releaseAll();
        remoteSinks.clear();
        callId = null;
        rideId = null;
        hostName = null;
        me = null;
        session = null;
        invite = null;
        callStartedAt = 0;
        joinedAt = 0;
        muted = false;
        speaker = true;
        cameraOn = false;
        frontCamera = true;
        notifiedPeers = -1;
    }

    /** The invite currently ringing, if any. */
    @Nullable
    public GroupCall pendingInvite() { return current().phase == Phase.INVITED ? invite : null; }

    // ------------------------------------------------------------------ WebRTC callbacks

    private final class Observer implements PeerConnection.Observer {
        private final Link link;

        Observer(Link link) { this.link = link; }

        @Override
        public void onIceCandidate(IceCandidate c) { AppExecutors.main(() -> onLocalCandidate(link, c)); }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState s) { AppExecutors.main(() -> onIceState(link, s)); }

        @Override
        public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
            MediaStreamTrack t = receiver.track();
            if (t instanceof VideoTrack) AppExecutors.main(() -> onRemoteVideo(link, (VideoTrack) t));
        }

        @Override public void onSignalingChange(PeerConnection.SignalingState s) { }

        @Override public void onIceConnectionReceivingChange(boolean b) { }

        @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) { }

        @Override public void onIceCandidatesRemoved(IceCandidate[] c) { }

        @Override public void onAddStream(MediaStream s) { }

        @Override public void onRemoveStream(MediaStream s) { }

        @Override public void onDataChannel(DataChannel d) { }

        @Override public void onRenegotiationNeeded() { }
    }

    private abstract static class SimpleSdp implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sdp) { }

        @Override public void onSetSuccess() { }

        @Override public void onCreateFailure(String error) { Log.w(TAG, "SDP create failed: " + error); }

        @Override public void onSetFailure(String error) { Log.w(TAG, "SDP set failed: " + error); }
    }
}
