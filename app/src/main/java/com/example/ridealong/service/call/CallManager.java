package com.example.ridealong.service.call;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ridealong.BuildConfig;
import com.example.ridealong.R;
import com.example.ridealong.ServiceLocator;
import com.example.ridealong.data.model.CallSession;
import com.example.ridealong.data.model.DirectChat;
import com.example.ridealong.data.remote.FirebaseProvider;
import com.example.ridealong.data.repository.CallRepository;
import com.example.ridealong.domain.config.AppConfig;
import com.example.ridealong.ui.common.Me;
import com.example.ridealong.utils.AppExecutors;
import com.example.ridealong.utils.Constants;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.ListenerRegistration;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * In-app voice calls (Messenger-style, no phone number) over WebRTC, with Firestore as the
 * signaling channel. One call at a time. All state changes happen on the main thread and are
 * published through {@link #state()}.
 *
 * Caller: offer → calls/{id} (RINGING) → callee answers → ICE → CONNECTED.
 * Audio is peer-to-peer (DTLS-SRTP encrypted by WebRTC); it goes through a TURN relay only when
 * a direct path is impossible and TURN is configured.
 */
public final class CallManager {
    private static final String TAG = "CallManager";

    public enum Phase { IDLE, OUTGOING, INCOMING, CONNECTING, CONNECTED, ENDED }

    /** Immutable snapshot for the UI. */
    public static final class State {
        public final Phase phase;
        @Nullable public final String callId;
        @Nullable public final String peerId;
        @Nullable public final String peerName;
        public final boolean outgoing;
        public final boolean muted;
        public final boolean speaker;
        public final long connectedAt;
        /** Why the call ended: a CallSession status, or FAILED / NO_ANSWER. */
        @Nullable public final String endReason;

        State(Phase phase, String callId, String peerId, String peerName, boolean outgoing, boolean muted, boolean speaker,
              long connectedAt, String endReason) {
            this.phase = phase;
            this.callId = callId;
            this.peerId = peerId;
            this.peerName = peerName;
            this.outgoing = outgoing;
            this.muted = muted;
            this.speaker = speaker;
            this.connectedAt = connectedAt;
            this.endReason = endReason;
        }

        static State idle() { return new State(Phase.IDLE, null, null, null, false, false, false, 0, null); }
    }

    public static final String END_FAILED = "FAILED";
    public static final String END_NO_ANSWER = "NO_ANSWER";

    private static CallManager instance;

    private final Context app;
    private final CallRepository repo = new CallRepository();
    private final CallAudio audio;
    private final MutableLiveData<State> state = new MutableLiveData<>(State.idle());

    private PeerConnectionFactory factory;
    private PeerConnection pc;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private ListenerRegistration callReg;
    private ListenerRegistration candidateReg;
    private final List<IceCandidate> pendingRemote = new ArrayList<>();
    private boolean remoteSet;

    // Current call context
    private String callId;
    private String peerId;
    private String peerName;
    private String rideId;
    private String chatId;
    private boolean outgoing;
    private boolean muted;
    private boolean speaker;
    private long connectedAt;
    private CallSession incoming;

    private final Runnable ringTimeout = this::onRingTimeout;
    private final Runnable disconnectGrace = () -> finish(END_FAILED, true);

    private CallManager(Context c) {
        app = c.getApplicationContext();
        audio = new CallAudio(app);
    }

    public static synchronized CallManager get(Context c) {
        if (instance == null) instance = new CallManager(c);
        return instance;
    }

    public LiveData<State> state() { return state; }

    public boolean isBusy() {
        Phase p = current().phase;
        return p != Phase.IDLE && p != Phase.ENDED;
    }

    private State current() {
        State s = state.getValue();
        return s == null ? State.idle() : s;
    }

    private void publish(Phase phase, @Nullable String endReason) {
        state.setValue(new State(phase, callId, peerId, peerName, outgoing, muted, speaker, connectedAt, endReason));
    }

    // ------------------------------------------------------------------ outgoing

    /** Starts a call. Caller must already hold RECORD_AUDIO. */
    public boolean startOutgoing(String calleeId, String calleeName, String rideId, @Nullable String chatId) {
        if (isBusy() || FirebaseProvider.uidOrNull() == null) return false;
        reset();
        DocumentReference ref = repo.newCall();
        this.callId = ref.getId();
        this.peerId = calleeId;
        this.peerName = calleeName;
        this.rideId = rideId;
        this.chatId = chatId == null ? "" : chatId;
        this.outgoing = true;
        publish(Phase.OUTGOING, null);
        CallService.start(app);
        audio.enterCallMode(false);
        audio.startRingback();

        if (!createPeer(true)) return true;
        pc.createOffer(new SimpleSdp() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                pc.setLocalDescription(new SimpleSdp() {
                    @Override
                    public void onSetSuccess() {
                        AppExecutors.main(() -> repo.create(ref, calleeId, calleeName, Me.name(), rideId, CallManager.this.chatId,
                                        sdp.type.canonicalForm(), sdp.description)
                                .addOnFailureListener(e -> {
                                    Log.w(TAG, "call create failed", e);
                                    finish(END_FAILED, false);
                                }));
                    }
                }, sdp);
            }
        }, offerConstraints());

        callReg = ref.addSnapshotListener((snap, err) -> {
            if (err != null || snap == null || !snap.exists()) return;
            CallSession s = snap.toObject(CallSession.class);
            if (s == null) return;
            if (s.answer != null && !remoteSet && pc != null) {
                audio.stopTones();
                publish(Phase.CONNECTING, null);
                setRemote(s.answer);
            }
            if (isTerminal(s.status)) finish(s.status, false);
        });
        candidateReg = listenCandidates(callId, false);
        AppExecutors.mainDelayed(ringTimeout, AppConfig.get().callRingTimeoutMs);
        return true;
    }

    private void onRingTimeout() {
        if (current().phase == Phase.INCOMING) {
            finish(CallSession.MISSED, false); // caller is gone; stop ringing here
            return;
        }
        if (current().phase != Phase.OUTGOING || callId == null) return;
        repo.setStatus(callId, CallSession.MISSED);
        ServiceLocator.get().notifications().send(peerId, Constants.N_MISSED_CALL, rideId, Me.name(), "", chatId);
        finish(END_NO_ANSWER, false);
    }

    // ------------------------------------------------------------------ incoming

    /** Called by IncomingCallWatcher for a new RINGING call addressed to me. */
    public void onIncoming(CallSession s) {
        if (isBusy()) {
            if (!s.id.equals(callId)) repo.setStatus(s.id, CallSession.BUSY);
            return;
        }
        reset();
        incoming = s;
        callId = s.id;
        peerId = s.callerId;
        peerName = s.callerName;
        rideId = s.rideId;
        chatId = s.chatId == null ? "" : s.chatId;
        outgoing = false;
        publish(Phase.INCOMING, null);
        audio.startRinging();
        callReg = repo.ref(callId).addSnapshotListener((snap, err) -> {
            if (err != null || snap == null || !snap.exists()) return;
            CallSession cur = snap.toObject(CallSession.class);
            if (cur != null && isTerminal(cur.status)) finish(cur.status, false);
        });
        // Safety net: stop ringing if the caller's timeout never reached us.
        AppExecutors.mainDelayed(ringTimeout, AppConfig.get().callRingTimeoutMs + 5_000);
    }

    /** Accept the ringing call. Caller must already hold RECORD_AUDIO. */
    public void accept() {
        if (current().phase != Phase.INCOMING || incoming == null) return;
        AppExecutors.cancelMain(ringTimeout);
        audio.stopTones();
        publish(Phase.CONNECTING, null);
        CallService.start(app);
        audio.enterCallMode(false);
        if (!createPeer(false)) return;
        Map<String, String> offer = incoming.offer;
        SessionDescription remote = new SessionDescription(SessionDescription.Type.fromCanonicalForm(offer.get("type")), offer.get("sdp"));
        pc.setRemoteDescription(new SimpleSdp() {
            @Override
            public void onSetSuccess() {
                AppExecutors.main(() -> {
                    remoteSet = true;
                    drainPending();
                    pc.createAnswer(new SimpleSdp() {
                        @Override
                        public void onCreateSuccess(SessionDescription sdp) {
                            pc.setLocalDescription(new SimpleSdp() {
                                @Override
                                public void onSetSuccess() {
                                    AppExecutors.main(() -> repo.answer(callId, sdp.type.canonicalForm(), sdp.description)
                                            .addOnFailureListener(e -> finish(END_FAILED, false)));
                                }
                            }, sdp);
                        }
                    }, new MediaConstraints());
                });
            }
        }, remote);
        candidateReg = listenCandidates(callId, true);
    }

    public void decline() {
        if (current().phase != Phase.INCOMING || callId == null) return;
        repo.setStatus(callId, CallSession.REJECTED);
        finish(CallSession.REJECTED, false);
    }

    // ------------------------------------------------------------------ in-call controls

    public void hangUp() {
        Phase p = current().phase;
        if (callId == null || p == Phase.IDLE || p == Phase.ENDED) return;
        if (p == Phase.INCOMING) {
            decline();
            return;
        }
        String status = p == Phase.OUTGOING ? CallSession.CANCELLED : CallSession.ENDED;
        repo.setStatus(callId, status);
        finish(status, false);
    }

    public void toggleMute() {
        muted = !muted;
        if (audioTrack != null) audioTrack.setEnabled(!muted);
        publish(current().phase, null);
    }

    public void toggleSpeaker() {
        speaker = !speaker;
        audio.setSpeaker(speaker);
        publish(current().phase, null);
    }

    // ------------------------------------------------------------------ WebRTC

    private void ensureFactory() {
        if (factory != null) return;
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(app)
                .createInitializationOptions());
        JavaAudioDeviceModule adm = JavaAudioDeviceModule.builder(app)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule();
        factory = PeerConnectionFactory.builder().setAudioDeviceModule(adm).createPeerConnectionFactory();
    }

    private static List<PeerConnection.IceServer> iceServers() {
        List<PeerConnection.IceServer> list = new ArrayList<>();
        for (String stun : AppConfig.get().callStunUrls) list.add(PeerConnection.IceServer.builder(stun).createIceServer());
        String turn = BuildConfig.TURN_URLS.trim();
        if (!turn.isEmpty()) {
            List<String> urls = new ArrayList<>();
            for (String u : turn.split(",")) if (!u.trim().isEmpty()) urls.add(u.trim());
            list.add(PeerConnection.IceServer.builder(urls)
                    .setUsername(BuildConfig.TURN_USERNAME)
                    .setPassword(BuildConfig.TURN_PASSWORD)
                    .createIceServer());
        }
        return list;
    }

    private boolean createPeer(boolean asCaller) {
        try {
            ensureFactory();
            PeerConnection.RTCConfiguration cfg = new PeerConnection.RTCConfiguration(iceServers());
            cfg.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
            pc = factory.createPeerConnection(cfg, new Observer(asCaller));
            if (pc == null) throw new IllegalStateException("createPeerConnection returned null");
            MediaConstraints audioConstraints = new MediaConstraints();
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
            audioSource = factory.createAudioSource(audioConstraints);
            audioTrack = factory.createAudioTrack("ridealong-audio", audioSource);
            audioTrack.setEnabled(true);
            pc.addTrack(audioTrack, Collections.singletonList("ridealong"));
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "WebRTC init failed", t);
            if (callId != null) {
                repo.setStatus(callId, outgoing ? CallSession.CANCELLED : CallSession.REJECTED);
            }
            finish(END_FAILED, false);
            return false;
        }
    }

    private static MediaConstraints offerConstraints() {
        MediaConstraints c = new MediaConstraints();
        c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        return c;
    }

    private void setRemote(Map<String, String> desc) {
        SessionDescription sdp = new SessionDescription(SessionDescription.Type.fromCanonicalForm(desc.get("type")), desc.get("sdp"));
        remoteSet = true; // set eagerly so a duplicate snapshot doesn't apply it twice
        pc.setRemoteDescription(new SimpleSdp() {
            @Override
            public void onSetSuccess() { AppExecutors.main(CallManager.this::drainPending); }
        }, sdp);
    }

    /** Listens to the OTHER side's ICE candidates. */
    private ListenerRegistration listenCandidates(String id, boolean ofCaller) {
        return repo.candidates(id, ofCaller).addSnapshotListener((snap, err) -> {
            if (err != null || snap == null) return;
            for (DocumentChange ch : snap.getDocumentChanges()) {
                if (ch.getType() != DocumentChange.Type.ADDED) continue;
                String mid = ch.getDocument().getString("sdpMid");
                Long idx = ch.getDocument().getLong("sdpMLineIndex");
                String cand = ch.getDocument().getString("candidate");
                if (cand == null || idx == null) continue;
                IceCandidate c = new IceCandidate(mid, idx.intValue(), cand);
                if (pc != null && remoteSet) pc.addIceCandidate(c);
                else pendingRemote.add(c);
            }
        });
    }

    private void drainPending() {
        if (pc == null) return;
        for (IceCandidate c : pendingRemote) pc.addIceCandidate(c);
        pendingRemote.clear();
    }

    private void onIceState(PeerConnection.IceConnectionState s) {
        switch (s) {
            case CONNECTED:
            case COMPLETED:
                AppExecutors.cancelMain(disconnectGrace);
                if (connectedAt == 0) connectedAt = System.currentTimeMillis();
                publish(Phase.CONNECTED, null);
                CallService.update(app);
                break;
            case DISCONNECTED:
                // Network hiccup: give WebRTC a few seconds to recover before giving up.
                AppExecutors.cancelMain(disconnectGrace);
                AppExecutors.mainDelayed(disconnectGrace, AppConfig.get().callReconnectGraceMs);
                break;
            case FAILED:
                finish(END_FAILED, true);
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ teardown

    private static boolean isTerminal(String status) {
        return CallSession.REJECTED.equals(status) || CallSession.BUSY.equals(status) || CallSession.CANCELLED.equals(status)
                || CallSession.MISSED.equals(status) || CallSession.ENDED.equals(status);
    }

    /**
     * Ends the call locally. When {@code notifyPeer} is true the Firestore status is moved to ENDED so the
     * other phone stops too.
     */
    private void finish(String reason, boolean notifyPeer) {
        Phase p = current().phase;
        if (p == Phase.IDLE || p == Phase.ENDED) return;
        if (notifyPeer && callId != null) {
            repo.setStatus(callId, p == Phase.CONNECTING || p == Phase.CONNECTED ? CallSession.ENDED
                    : outgoing ? CallSession.CANCELLED : CallSession.REJECTED);
        }
        AppExecutors.cancelMain(ringTimeout);
        AppExecutors.cancelMain(disconnectGrace);
        logToChat(reason);
        releaseMedia();
        audio.exitCallMode();
        CallService.stop(app);
        publish(Phase.ENDED, reason);
        AppExecutors.mainDelayed(() -> {
            if (current().phase == Phase.ENDED) {
                reset();
                state.setValue(State.idle());
            }
        }, 1500);
    }

    /** The CALLER writes one history entry into the chat (avoids duplicates from both phones). */
    private void logToChat(String reason) {
        if (!outgoing || rideId == null) return;
        String text;
        if (connectedAt > 0) {
            long secs = (System.currentTimeMillis() - connectedAt) / 1000;
            text = app.getString(R.string.call_log_voice, String.format(Locale.US, "%d:%02d", secs / 60, secs % 60));
        } else if (END_NO_ANSWER.equals(reason) || CallSession.MISSED.equals(reason)) {
            text = app.getString(R.string.call_log_missed);
        } else if (CallSession.REJECTED.equals(reason) || CallSession.BUSY.equals(reason)) {
            text = app.getString(R.string.call_log_declined);
        } else {
            return; // cancelled before ringing completed / failed to start: nothing worth logging
        }
        String name = Me.name();
        if (chatId != null && !chatId.isEmpty()) {
            ServiceLocator.get().directChats().ref(chatId).get().addOnSuccessListener(snap -> {
                DirectChat c = snap == null ? null : snap.toObject(DirectChat.class);
                if (c != null) ServiceLocator.get().directChats().logCall(c, text, name);
            });
        } else {
            ServiceLocator.get().chat().logCall(rideId, name, peerName, text);
        }
    }

    private void releaseMedia() {
        if (callReg != null) callReg.remove();
        if (candidateReg != null) candidateReg.remove();
        callReg = null;
        candidateReg = null;
        try {
            if (pc != null) {
                pc.close();
                pc.dispose();
            }
            if (audioTrack != null) audioTrack.dispose();
            if (audioSource != null) audioSource.dispose();
        } catch (Throwable t) {
            Log.w(TAG, "release", t);
        }
        pc = null;
        audioTrack = null;
        audioSource = null;
        pendingRemote.clear();
        remoteSet = false;
    }

    private void reset() {
        releaseMedia();
        callId = null;
        peerId = null;
        peerName = null;
        rideId = null;
        chatId = null;
        incoming = null;
        outgoing = false;
        muted = false;
        speaker = false;
        connectedAt = 0;
    }

    // ------------------------------------------------------------------ WebRTC callbacks

    private final class Observer implements PeerConnection.Observer {
        private final boolean asCaller;

        Observer(boolean asCaller) { this.asCaller = asCaller; }

        @Override
        public void onIceCandidate(IceCandidate c) {
            String id = callId;
            if (id != null) AppExecutors.main(() -> repo.addCandidate(id, asCaller, c.sdpMid, c.sdpMLineIndex, c.sdp));
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState s) { AppExecutors.main(() -> onIceState(s)); }

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
