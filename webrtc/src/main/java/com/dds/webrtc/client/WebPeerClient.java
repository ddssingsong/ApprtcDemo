package com.dds.webrtc.client;

import android.content.Context;
import android.util.Log;

import com.dds.webrtc.callback.AppPeerConnectionEvents;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by dds on 2018/11/7.
 * android_shuai@163.com
 */
public class WebPeerClient {
    private static final WebPeerClient instance = new WebPeerClient();
    private final ExecutorService executor;
    public static PeerConnectionFactory factory;
    private List<PeerConnection.IceServer> iceServers;
    private List<PeerConn> peerConns = Collections.synchronizedList(new ArrayList<>());


    private VideoRenderer.Callbacks localRender;
    private AppPeerConnectionEvents events;

    public static MediaStream mediaStream;
    private Context mContext;

    public static WebPeerClient getInstance() {
        return instance;
    }

    private WebPeerClient() {
        iceServers = new LinkedList<>();
        iceServers.add(new PeerConnection.IceServer("stun:47.254.34.146:3478"));
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    public void initPeerConnectionFactory(Context context, VideoRenderer.Callbacks localRender, AppPeerConnectionEvents events) {
        this.events = events;
        this.localRender = localRender;
        this.mContext = context;
    }


    public void createPeerConnection(EglBase.Context eglBaseContext,
                                     VideoRenderer.Callbacks remoteRender,
                                     boolean isInitiator,
                                     String remoteUserId,
                                     SessionDescription sdp) {

        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (factory == null) {
                    PeerConnectionFactory.initializeInternalTracer();
                    PeerConnectionFactory.initializeFieldTrials("WebRTC-IntelVP8/Enabled/");
                    WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
                    WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(false);
                    WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(false);
                    PeerConnectionFactory.initializeAndroidGlobals(mContext, true, true, true);
                    PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
                    options.networkIgnoreMask = 0;
                    factory = new PeerConnectionFactory(options);
                    factory.setVideoHwAccelerationOptions(eglBaseContext, eglBaseContext);


                }

                if (mediaStream == null) {
                    createAudioTrack();
                    createVideoTrack();
                    mediaStream = factory.createLocalMediaStream("ARDAMS");
                    //设置视频
                    mediaStream.addTrack(localAudioTrack);
                    // 设置音频
                    mediaStream.addTrack(localVideoTrack);
                }


                PeerConn peerConn = new PeerConn(remoteUserId, events);
                peerConn.createPeerConnection(iceServers, remoteRender, isInitiator);

                if (isInitiator) {
                    //发起者
                    peerConn.setRemoteDescription(sdp);
                    peerConn.createAnswer();
                } else {
                    // 后进来的发起通话
                    peerConn.createOffer();
                }
                WebPeerClient.this.peerConns.add(peerConn);

            }
        });
    }


    public void setRemoteDescription(final SessionDescription sdp, String remoteId) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (PeerConn peerConn : peerConns) {
                    if (peerConn.getRemoteId().equals(remoteId)) {
                        peerConn.setRemoteDescription(sdp);
                    }
                }


            }
        });
    }

    public void addRemoteIceCandidate(final IceCandidate candidate, String remoteUserId) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (PeerConn peerConn : peerConns) {
                    if (peerConn.getRemoteId().equals(remoteUserId)) {
                        peerConn.addRemoteIceCandidate(candidate);
                    }
                }
            }
        });
    }

    public void removeRemoteIceCandidates(final IceCandidate[] candidates, String remoteUserId) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (PeerConn peerConn : peerConns) {
                    if (peerConn.getRemoteId().equals(remoteUserId)) {
                        peerConn.removeRemoteIceCandidates(candidates);
                    }
                }
            }
        });
    }

    public void removeRemoteUser(String remoteUserId) {
        Log.e("dds_test", "removeRemoteUser:" + remoteUserId);
        for (PeerConn peerConn : peerConns) {
            if (peerConn.getRemoteId().equals(remoteUserId)) {
                peerConn.disconnect();
            }
        }
    }

    public void disconnect() {
        for (PeerConn peerConn : peerConns) {
            peerConn.disconnect();
        }

    }


    //============================================================================================

    private VideoCapturer createVideoCapture(Context context) {
        VideoCapturer videoCapturer;
        if (useCamera2(context)) {
            videoCapturer = createCameraCapturer(new Camera2Enumerator(context));
        } else {
            videoCapturer = createCameraCapturer(new Camera1Enumerator(true));
        }
        if (videoCapturer == null) {
            return null;
        }
        return videoCapturer;
    }

    private boolean useCamera2(Context context) {
        return Camera2Enumerator.isSupported(context);
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();
        // First, try to find front facing camera
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        // Front facing camera not found, try something else
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    //============================================================================================
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;

    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";
    private static final int HD_VIDEO_WIDTH = 1280;
    private static final int HD_VIDEO_HEIGHT = 720;
    private static int videoFps = 30;


    private AudioTrack createAudioTrack() {
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("levelControl", "true"));
        audioSource = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        localAudioTrack.setEnabled(true);
        return localAudioTrack;
    }

    private VideoTrack createVideoTrack() {
        VideoCapturer videoCapturer = createVideoCapture(mContext);
        if (videoCapturer != null) {
            videoSource = factory.createVideoSource(videoCapturer);
            videoCapturer.startCapture(HD_VIDEO_WIDTH, HD_VIDEO_HEIGHT, videoFps);
        }

        localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        localVideoTrack.setEnabled(true);
        localVideoTrack.addRenderer(new VideoRenderer(localRender));
        return localVideoTrack;
    }


}
