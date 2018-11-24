package com.dds.webrtc;

import android.content.Context;
import android.util.Log;

import com.dds.webrtc.bean.SignalingParameters;
import com.dds.webrtc.callback.PeerConnectionEvents;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;

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
 * Created by dds on 2018/11/2.
 * android_shuai@163.com
 */
public class PeerConnectionClient {
    private static final String TAG = "PeerConnectionClient";
    private static final PeerConnectionClient instance = new PeerConnectionClient();
    private PeerConnectionFactory factory;
    private final ExecutorService executor;

    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private VideoCapturer videoCapturer;
    private SignalingParameters signalingParameters;

    private MediaConstraints audioConstraints;
    private MediaConstraints sdpMediaConstraints;
    private LinkedList<IceCandidate> queuedRemoteCandidates;
    private PeerConnection peerConnection;
    private MediaStream mediaStream;
    private final PCObserver pcObserver = new PCObserver();
    private final SDPObserver sdpObserver = new SDPObserver();
    private boolean isInitiator;
    private PeerConnectionEvents events;
    //    对视屏进行限制
    private MediaConstraints pcConstraints;
    private static final int HD_VIDEO_WIDTH = 1280;
    private static final int HD_VIDEO_HEIGHT = 720;
    private static int videoFps = 30;
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";

    private Context mContext;
    public List<PeerConnection.IceServer> iceServers;

    public static PeerConnectionClient getInstance() {
        return instance;
    }

    private PeerConnectionClient() {
        executor = Executors.newSingleThreadExecutor();
    }

    public void initPeerConnectionFactory(final Context context, final PeerConnectionEvents events) {
        this.mContext = context;
        this.events = events;
        iceServers = new LinkedList<>();
        iceServers.add(new PeerConnection.IceServer("stun:47.254.34.146:3478"));
        // iceServers.add(new PeerConnection.IceServer("turn:47.254.34.146:3478", "dds", "123456"));

    }


    public void createPeerConnection(final EglBase.Context eglBaseContext, VideoRenderer.Callbacks localRender,
                                     VideoRenderer.Callbacks remoteRenderers, VideoCapturer videoCapturer,
                                     SignalingParameters signalingParameters) {

        this.localRender = localRender;
        this.remoteRender = remoteRenderers;
        this.videoCapturer = videoCapturer;
        this.signalingParameters = signalingParameters;

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    PeerConnectionFactory.initializeInternalTracer();
                    //设置传送数据的编码
                    PeerConnectionFactory.initializeFieldTrials("WebRTC-IntelVP8/Enabled/");
                    //如果设备支持opensl Es 如果设置true则只是用AudioTracle 不使用opensles
                    WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
                    //                //允许自动曝光控制
                    WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(false);
                    WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(false);
                    PeerConnectionFactory.initializeAndroidGlobals(mContext, true,true,true);
                    PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
                    options.networkIgnoreMask = 0;
                    factory = new PeerConnectionFactory(options);
                    createMediaConstraintsInternal();
                    createPeerConnectionInternal(eglBaseContext);
                } catch (Exception e) {
                    throw e;
                }
            }
        });


    }


    private void createMediaConstraintsInternal() {
        pcConstraints = new MediaConstraints();
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        // Create audio constraints.
        audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("levelControl", "true"));

        // Create SDP constraints.
        sdpMediaConstraints = new MediaConstraints();
//        接受音频邀请
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
//        接受视频邀请
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

    }

    private void createPeerConnectionInternal(EglBase.Context eglBaseContext) {
        if (factory == null) {
            Log.e(TAG, "Peerconnection factory is not created");
            return;
        }
        queuedRemoteCandidates = new LinkedList<>();
        // 允许视频加速
        factory.setVideoHwAccelerationOptions(eglBaseContext, eglBaseContext);
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

        peerConnection = factory.createPeerConnection(rtcConfig, pcConstraints, pcObserver);

        mediaStream = factory.createLocalMediaStream("ARDAMS");
        //设置视频
        mediaStream.addTrack(createVideoTrack(videoCapturer));
        // 设置音频
        mediaStream.addTrack(createAudioTrack());

        peerConnection.addStream(mediaStream);


    }

    public void createOffer() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection != null) {
                    isInitiator = true;
                    peerConnection.createOffer(sdpObserver, sdpMediaConstraints);
                }
            }
        });
    }

    public void createAnswer() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection != null) {
                    isInitiator = false;
                    peerConnection.createAnswer(sdpObserver, sdpMediaConstraints);
                }
            }
        });
    }


    public void setRemoteDescription(final SessionDescription sdp) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection == null) {
                    return;
                }
                String sdpDescription = sdp.description;
                sdpDescription = preferCodec(sdpDescription, "ISAC", true);
               // sdpDescription = preferCodec(sdpDescription, "VP8", false);
                Log.d(TAG, "Set remote SDP.");
                SessionDescription sdpRemote = new SessionDescription(sdp.type, sdpDescription);
                peerConnection.setRemoteDescription(sdpObserver, sdpRemote);
            }
        });
    }

    public void addRemoteIceCandidate(final IceCandidate candidate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection != null) {
                    if (queuedRemoteCandidates != null) {
                        queuedRemoteCandidates.add(candidate);
                    } else {
                        peerConnection.addIceCandidate(candidate);
                    }
                }
            }
        });
    }

    public void removeRemoteIceCandidates(final IceCandidate[] candidates) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection == null) {
                    return;
                }
                drainCandidates();
                peerConnection.removeIceCandidates(candidates);
            }
        });
    }

    private void drainCandidates() {
        if (queuedRemoteCandidates != null) {
            Log.d(TAG, "Add " + queuedRemoteCandidates.size() + " remote candidates");
            for (IceCandidate candidate : queuedRemoteCandidates) {
                peerConnection.addIceCandidate(candidate);
            }
            queuedRemoteCandidates = null;
        }
    }

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
        // Reconstruct the line with |preferredPayloadTypes| moved to the beginning of the payload
        // types.
        final List<String> newLineParts = new ArrayList<String>();
        newLineParts.addAll(header);
        newLineParts.addAll(preferredPayloadTypes);
        newLineParts.addAll(unpreferredPayloadTypes);
        return joinString(newLineParts, " ", false /* delimiterAtEnd */);
    }

    private AudioSource audioSource;
    private AudioTrack localAudioTrack;

    private VideoSource videoSource;
    private VideoTrack localVideoTrack;


    private AudioTrack createAudioTrack() {
        audioSource = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        localAudioTrack.setEnabled(true);
        return localAudioTrack;
    }

    private VideoTrack createVideoTrack(VideoCapturer capturer) {
        videoSource = factory.createVideoSource(capturer);
        capturer.startCapture(HD_VIDEO_WIDTH, HD_VIDEO_HEIGHT, videoFps);
        localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        localVideoTrack.setEnabled(true);
        localVideoTrack.addRenderer(new VideoRenderer(localRender));
        return localVideoTrack;
    }

    private VideoTrack remoteVideoTrack;

    private class PCObserver implements PeerConnection.Observer {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d(TAG, "SignalingState: " + signalingState);
        }

        @Override
        public void onIceConnectionChange(final PeerConnection.IceConnectionState newState) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "IceConnectionState: " + newState);
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
            Log.d(TAG, "IceConnectionReceiving changed to " + receiving);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
            Log.d(TAG, "IceGatheringState: " + newState);
        }

        @Override
        public void onIceCandidate(final IceCandidate iceCandidate) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    events.onIceCandidate(iceCandidate);
                }
            });
        }

        @Override
        public void onIceCandidatesRemoved(final IceCandidate[] iceCandidates) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    events.onIceCandidatesRemoved(iceCandidates);
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
                        //reportError("Weird-looking stream: " + stream);
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
            Log.d(TAG, "onDataChannel: " + dataChannel);
        }

        @Override
        public void onRenegotiationNeeded() {

        }

    }


    private SessionDescription localSdp; // either offer or answer SDP

    private class SDPObserver implements SdpObserver {

        @Override
        public void onCreateSuccess(SessionDescription origSdp) {
            String sdpDescription = origSdp.description;
            sdpDescription = preferCodec(sdpDescription, "ISAC", true);
          //  sdpDescription = preferCodec(sdpDescription, "VP8", false);
            final SessionDescription sdp = new SessionDescription(origSdp.type, sdpDescription);
            localSdp = sdp;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (peerConnection != null) {
                        Log.d(TAG, "Set local SDP from " + sdp.type);
                        peerConnection.setLocalDescription(sdpObserver, sdp);
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
                    if (isInitiator) {
                        if (peerConnection.getRemoteDescription() == null) {
                            Log.d(TAG, "Local SDP set succesfully");
                            events.onLocalDescription(localSdp);
                        } else {
                            Log.d(TAG, "Remote SDP set succesfully");
                            drainCandidates();
                        }
                    } else {
                        if (peerConnection.getLocalDescription() != null) {
                            Log.d(TAG, "Local SDP set succesfully");
                            events.onLocalDescription(localSdp);
                            drainCandidates();
                        } else {
                            Log.d(TAG, "Remote SDP set succesfully");
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


}
