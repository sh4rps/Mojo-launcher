package com.robin.riscord;

import android.content.Context;
import android.media.AudioManager;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Voice call (1:1 or small group mesh) over WebRTC, signalled through Firestore. */
public class Call {
    public interface Ui { void onPeers(List<String> names); void onState(String s); }

    private static boolean inited;
    private static PeerConnectionFactory factory;

    private final Context ctx;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String me, myName, callId;
    private final Ui ui;
    private final Map<String, PeerConnection> pcs = new HashMap<>();
    private final Map<String, List<IceCandidate>> pendingIce = new HashMap<>();
    private ListenerRegistration peersReg, sigReg;
    private AudioSource src;
    private AudioTrack track;
    private AudioManager am;
    private int lastCount = 1;
    private boolean left;

    public Call(Context c, String me, String myName, String callId, Ui ui) {
        this.ctx = c.getApplicationContext(); this.me = me; this.myName = myName; this.callId = callId; this.ui = ui;
    }

    private abstract static class Sdp implements SdpObserver {
        public void onCreateSuccess(SessionDescription d) { }
        public void onSetSuccess() { }
        public void onCreateFailure(String s) { }
        public void onSetFailure(String s) { }
    }

    public void join() {
        if (!inited) {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(ctx).createInitializationOptions());
            factory = PeerConnectionFactory.builder().createPeerConnectionFactory();
            inited = true;
        }
        am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        am.setSpeakerphoneOn(false);
        src = factory.createAudioSource(new MediaConstraints());
        track = factory.createAudioTrack("riscord_audio", src);

        Map<String, Object> m = new HashMap<>();
        m.put("name", myName);
        db.collection("calls").document(callId).collection("peers").document(me).set(m);

        peersReg = db.collection("calls").document(callId).collection("peers").addSnapshotListener((snap, e) -> {
            if (snap == null || left) return;
            List<String> names = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                ids.add(d.getId());
                String n = d.getString("name");
                names.add(d.getId().equals(me) ? "You" : (n == null ? "?" : n));
            }
            lastCount = ids.size();
            ui.onPeers(names);
            for (String id : ids) if (!id.equals(me) && me.compareTo(id) > 0 && !pcs.containsKey(id)) offer(id);
            for (String id : new ArrayList<>(pcs.keySet())) if (!ids.contains(id)) closePc(id);
        });
        sigReg = db.collection("calls").document(callId).collection("signals").whereEqualTo("to", me).addSnapshotListener((snap, e) -> {
            if (snap == null || left) return;
            for (DocumentChange ch : snap.getDocumentChanges()) {
                if (ch.getType() != DocumentChange.Type.ADDED) continue;
                DocumentSnapshot d = ch.getDocument();
                handle(d);
                d.getReference().delete();
            }
        });
        ui.onState("Connected to room, waiting for others...");
    }

    private PeerConnection pc(String peer) {
        PeerConnection p = pcs.get(peer);
        if (p != null) return p;
        List<PeerConnection.IceServer> ice = new ArrayList<>();
        ice.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        ice.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        PeerConnection.RTCConfiguration cfg = new PeerConnection.RTCConfiguration(ice);
        cfg.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        p = factory.createPeerConnection(cfg, new PeerConnection.Observer() {
            public void onSignalingChange(PeerConnection.SignalingState s) { }
            public void onIceConnectionChange(PeerConnection.IceConnectionState s) {
                if (s == PeerConnection.IceConnectionState.CONNECTED) ui.onState("In call");
                else if (s == PeerConnection.IceConnectionState.FAILED) ui.onState("Connection failed (network blocks direct calls)");
            }
            public void onIceConnectionReceivingChange(boolean b) { }
            public void onIceGatheringChange(PeerConnection.IceGatheringState s) { }
            public void onIceCandidate(IceCandidate c) {
                Map<String, Object> m = sig(peer, "ice");
                m.put("mid", c.sdpMid); m.put("idx", c.sdpMLineIndex); m.put("cand", c.sdp);
                db.collection("calls").document(callId).collection("signals").add(m);
            }
            public void onIceCandidatesRemoved(IceCandidate[] c) { }
            public void onAddStream(MediaStream s) { }
            public void onRemoveStream(MediaStream s) { }
            public void onDataChannel(DataChannel d) { }
            public void onRenegotiationNeeded() { }
            public void onAddTrack(RtpReceiver r, MediaStream[] s) { }
        });
        p.addTrack(track, Collections.singletonList("riscord"));
        pcs.put(peer, p);
        return p;
    }

    private Map<String, Object> sig(String to, String type) {
        Map<String, Object> m = new HashMap<>();
        m.put("from", me); m.put("to", to); m.put("type", type);
        return m;
    }

    private void offer(String peer) {
        final PeerConnection p = pc(peer);
        p.createOffer(new Sdp() {
            @Override public void onCreateSuccess(SessionDescription d) {
                p.setLocalDescription(new Sdp() {
                    @Override public void onSetSuccess() {
                        Map<String, Object> m = sig(peer, "offer");
                        m.put("sdp", d.description);
                        db.collection("calls").document(callId).collection("signals").add(m);
                    }
                }, d);
            }
        }, new MediaConstraints());
    }

    private void handle(DocumentSnapshot d) {
        String from = d.getString("from"), type = d.getString("type");
        if (from == null || type == null) return;
        final PeerConnection p = pc(from);
        switch (type) {
            case "offer": {
                SessionDescription off = new SessionDescription(SessionDescription.Type.OFFER, d.getString("sdp"));
                p.setRemoteDescription(new Sdp() {
                    @Override public void onSetSuccess() {
                        flushIce(from);
                        p.createAnswer(new Sdp() {
                            @Override public void onCreateSuccess(SessionDescription ans) {
                                p.setLocalDescription(new Sdp() {
                                    @Override public void onSetSuccess() {
                                        Map<String, Object> m = sig(from, "answer");
                                        m.put("sdp", ans.description);
                                        db.collection("calls").document(callId).collection("signals").add(m);
                                    }
                                }, ans);
                            }
                        }, new MediaConstraints());
                    }
                }, off);
                break;
            }
            case "answer": {
                SessionDescription ans = new SessionDescription(SessionDescription.Type.ANSWER, d.getString("sdp"));
                p.setRemoteDescription(new Sdp() { @Override public void onSetSuccess() { flushIce(from); } }, ans);
                break;
            }
            case "ice": {
                Long idx = d.getLong("idx");
                IceCandidate c = new IceCandidate(d.getString("mid"), idx == null ? 0 : idx.intValue(), d.getString("cand"));
                if (p.getRemoteDescription() == null) {
                    List<IceCandidate> l = pendingIce.get(from);
                    if (l == null) { l = new ArrayList<>(); pendingIce.put(from, l); }
                    l.add(c);
                } else p.addIceCandidate(c);
                break;
            }
            default: break;
        }
    }

    private void flushIce(String peer) {
        PeerConnection p = pcs.get(peer);
        List<IceCandidate> l = pendingIce.remove(peer);
        if (p != null && l != null) for (IceCandidate c : l) p.addIceCandidate(c);
    }

    private void closePc(String id) {
        PeerConnection p = pcs.remove(id);
        if (p != null) { try { p.close(); } catch (Exception ignored) { } }
    }

    public void setMuted(boolean m) { if (track != null) track.setEnabled(!m); }
    public void setSpeaker(boolean on) { if (am != null) am.setSpeakerphoneOn(on); }

    public void leave() {
        if (left) return;
        left = true;
        if (peersReg != null) peersReg.remove();
        if (sigReg != null) sigReg.remove();
        for (String id : new ArrayList<>(pcs.keySet())) closePc(id);
        try { if (track != null) track.dispose(); if (src != null) src.dispose(); } catch (Exception ignored) { }
        db.collection("calls").document(callId).collection("peers").document(me).delete();
        if (lastCount <= 2) {
            Map<String, Object> m = new HashMap<>(); m.put("active", false);
            db.collection("calls").document(callId).update(m);
        }
        if (am != null) { am.setSpeakerphoneOn(false); am.setMode(AudioManager.MODE_NORMAL); }
    }
}
