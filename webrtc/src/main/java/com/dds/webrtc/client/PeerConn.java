package com.dds.webrtc.client;

import android.util.Log;

import com.dds.webrtc.callback.AppPeerConnectionEvents;
import com.dds.webrtc.callback.ProxyRenderer;

import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by dds on 2018/11/22.
 * android_shuai@163.com
 */
public class PeerConn {
    private PeerConnection peerConnection;
    private String remoteUserId;
    private AppPeerConnectionEvents events;
    private final ExecutorService executor;
    private VideoRenderer.Callbacks remoteRender;
    private MediaConstraints pcConstraints;
    private MediaConstraints sdpMediaConstraints;
    private PCObserver pcObserver = new PCObserver();
    private SDPObserver sdpObserver = new SDPObserver();
    private boolean isInitiator;
    private MediaStream mediaStream;

    public PeerConn(String remoteUserId, AppPeerConnectionEvents events) {
        this.events = events;
        this.remoteUserId = remoteUserId;
        executor = Executors.newSingleThreadScheduledExecutor();

    }

    public String getRemoteId() {
        return remoteUserId;

    }

    public void createPeerConnection(List<PeerConnection.IceServer> iceServers, PeerConnectionFactory factory,
                                     ProxyRenderer remoteRender, AudioTrack audioTrack, VideoTrack videoTrack, boolean isInitiator) {

        this.remoteRender = remoteRender;
        this.isInitiator = isInitiator;

        createMediaConstraintsInternal();

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

        peerConnection = factory.createPeerConnection(rtcConfig, pcConstraints, pcObserver);
        mediaStream = factory.createLocalMediaStream("ARDAMS");
        //设置视频

        mediaStream.addTrack(audioTrack);
        // 设置音频
        mediaStream.addTrack(videoTrack);


        peerConnection.addStream(mediaStream);
        Log.e("dds_test", "createPeerConnection");

    }


    private void createMediaConstraintsInternal() {
        pcConstraints = new MediaConstraints();
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

    }

    public void createOffer() {
        if (peerConnection != null) {
            Log.e("dds_test", "createOffer...");
            peerConnection.createOffer(sdpObserver, sdpMediaConstraints);
        }

    }

    public void createAnswer() {
        if (peerConnection != null) {
            Log.e("dds_test", "createAnswer...");
            peerConnection.createAnswer(sdpObserver, sdpMediaConstraints);
        }
    }

    public void setRemoteDescription(final SessionDescription sdp) {
        if (peerConnection == null) {
            return;
        }
        Log.e("dds_test", "setRemoteDescription...");
        String sdpDescription = sdp.description;
        //sdpDescription = preferCodec(sdpDescription, "ISAC", true);
        sdpDescription = preferCodec(sdpDescription, "VP8", false);
        SessionDescription sdpRemote = new SessionDescription(sdp.type, sdpDescription);
        peerConnection.setRemoteDescription(sdpObserver, sdpRemote);
    }

    public void addRemoteIceCandidate(final IceCandidate candidate) {
        if (peerConnection != null) {
            Log.e("dds_test", "addRemoteIceCandidate...");
            if (queuedRemoteCandidates != null) {
                queuedRemoteCandidates.add(candidate);
            } else {
                peerConnection.addIceCandidate(candidate);
            }
        }
    }

    public void removeRemoteIceCandidates(final IceCandidate[] candidates) {
        if (peerConnection == null) {
            return;
        }
        Log.e("dds_test", "removeRemoteIceCandidates...");
        drainCandidates();
        peerConnection.removeIceCandidates(candidates);
    }

    public void disconnect() {
        if (peerConnection != null) {
            peerConnection.close();
        }

    }


    //============================================================================================

    private VideoTrack remoteVideoTrack;

    private class PCObserver implements PeerConnection.Observer {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        }

        @Override
        public void onIceConnectionChange(final PeerConnection.IceConnectionState newState) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                        events.onIceConnected();
                    } else if (newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                        events.onIceDisconnected();
                    } else if (newState == PeerConnection.IceConnectionState.FAILED) {
                        //  reportError("ICE connection failed.");
                    }
                }
            });
        }

        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
        }

        @Override
        public void onIceCandidate(final IceCandidate iceCandidate) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    events.onIceCandidate(iceCandidate, remoteUserId);
                }
            });
        }

        @Override
        public void onIceCandidatesRemoved(final IceCandidate[] iceCandidates) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    events.onIceCandidatesRemoved(iceCandidates, remoteUserId);
                }
            });

        }

        @Override
        public void onAddStream(final MediaStream stream) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (peerConnection == null) {
                        return;
                    }
                    if (stream.audioTracks.size() > 1 || stream.videoTracks.size() > 1) {
                        return;
                    }
                    if (stream.videoTracks.size() == 1) {
                        remoteVideoTrack = stream.videoTracks.get(0);
                        remoteVideoTrack.setEnabled(true);
                        remoteVideoTrack.addRenderer(new VideoRenderer(remoteRender));
                    }
                }
            });
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    remoteVideoTrack = null;
                }
            });
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
        }

        @Override
        public void onRenegotiationNeeded() {

        }


    }

    private SessionDescription localSdp;

    private class SDPObserver implements SdpObserver {

        @Override
        public void onCreateSuccess(SessionDescription origSdp) {
            if (localSdp != null) return;
            String sdpDescription = origSdp.description;
            sdpDescription = preferCodec(sdpDescription, "VP8", false);
            //sdpDescription = preferCodec(sdpDescription, "ISAC", true);
            final SessionDescription sdp = new SessionDescription(origSdp.type, sdpDescription);
            localSdp = sdp;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (peerConnection != null) {
                        Log.e("dds_test", "setLocalDescription");
                        peerConnection.setLocalDescription(sdpObserver, localSdp);
                    }
                }
            });
        }

        @Override
        public void onSetSuccess() {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (peerConnection == null) {
                        return;
                    }
                    Log.e("dds_test", "onSetSuccess");
                    if (isInitiator) {
                        // 先入房间的人
                        if (peerConnection.getLocalDescription() != null) {
                            Log.e("dds_test", "onLocalDescription,开始发送answer");
                            events.onLocalDescription(localSdp, remoteUserId);
                            drainCandidates();
                        } else {
                            Log.d("dds_test", "Remote SDP set succesfully");
                        }
                    } else {
                        // 后入房间的人需要发送offer
                        if (peerConnection.getRemoteDescription() == null) {
                            Log.e("dds_test", "onLocalDescription,开始发送offer");
                            events.onLocalDescription(localSdp, remoteUserId);
                        } else {
                            drainCandidates();
                        }
                    }
                }
            });
        }

        @Override
        public void onCreateFailure(String s) {

        }

        @Override
        public void onSetFailure(String s) {

        }
    }


    //===========================================================================================

    public static String preferCodec(String sdpDescription, String codec, boolean isAudio) {
        final String[] lines = sdpDescription.split("\r\n");
        final int mLineIndex = findMediaDescriptionLine(isAudio, lines);
        if (mLineIndex == -1) {
            return sdpDescription;
        }
        // A list with all the payload types with name |codec|. The payload types are integers in the
        // range 96-127, but they are stored as strings here.
        final List<String> codecPayloadTypes = new ArrayList<String>();
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        final Pattern codecPattern = Pattern.compile("^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$");
        for (int i = 0; i < lines.length; ++i) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecPayloadTypes.add(codecMatcher.group(1));
            }
        }
        if (codecPayloadTypes.isEmpty()) {
            return sdpDescription;
        }

        final String newMLine = movePayloadTypesToFront(codecPayloadTypes, lines[mLineIndex]);
        if (newMLine == null) {
            return sdpDescription;
        }
        lines[mLineIndex] = newMLine;
        return joinString(Arrays.asList(lines), "\r\n", true /* delimiterAtEnd */);
    }

    private static int findMediaDescriptionLine(boolean isAudio, String[] sdpLines) {
        final String mediaDescription = isAudio ? "m=audio " : "m=video ";
        for (int i = 0; i < sdpLines.length; ++i) {
            if (sdpLines[i].startsWith(mediaDescription)) {
                return i;
            }
        }
        return -1;
    }

    private static String joinString(
            Iterable<? extends CharSequence> s, String delimiter, boolean delimiterAtEnd) {
        Iterator<? extends CharSequence> iter = s.iterator();
        if (!iter.hasNext()) {
            return "";
        }
        StringBuilder buffer = new StringBuilder(iter.next());
        while (iter.hasNext()) {
            buffer.append(delimiter).append(iter.next());
        }
        if (delimiterAtEnd) {
            buffer.append(delimiter);
        }
        return buffer.toString();
    }

    private static String movePayloadTypesToFront(List<String> preferredPayloadTypes, String mLine) {
        // The format of the media description line should be: m=<media> <port> <proto> <fmt> ...
        final List<String> origLineParts = Arrays.asList(mLine.split(" "));
        if (origLineParts.size() <= 3) {
            return null;
        }
        final List<String> header = origLineParts.subList(0, 3);
        final List<String> unpreferredPayloadTypes =
                new ArrayList<String>(origLineParts.subList(3, origLineParts.size()));
        unpreferredPayloadTypes.removeAll(preferredPayloadTypes);

        final List<String> newLineParts = new ArrayList<String>();
        newLineParts.addAll(header);
        newLineParts.addAll(preferredPayloadTypes);
        newLineParts.addAll(unpreferredPayloadTypes);
        return joinString(newLineParts, " ", false /* delimiterAtEnd */);
    }


    private LinkedList<IceCandidate> queuedRemoteCandidates;

    private void drainCandidates() {
        if (queuedRemoteCandidates != null) {
            for (IceCandidate candidate : queuedRemoteCandidates) {
                peerConnection.addIceCandidate(candidate);
            }
            queuedRemoteCandidates = null;
        }
    }


}
