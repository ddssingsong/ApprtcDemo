package com.dds.webrtc.client;

import android.content.Context;

import com.dds.webrtc.callback.AppPeerConnectionEvents;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by dds on 2018/11/7.
 * android_shuai@163.com
 */
public class WebPeerClient {
    private static final String TAG = "dds_WebPeerClient";
    private static final WebPeerClient instance = new WebPeerClient();
    private final ExecutorService executor;
    private PeerConnectionFactory factory;
    private MediaStream mediaStream;
    public List<PeerConnection.IceServer> iceServers;


    VideoRenderer.Callbacks localRender;
    private VideoCapturer videoCapturer;
    private AppPeerConnectionEvents events;

    public static WebPeerClient getInstance() {
        return instance;
    }

    public WebPeerClient() {
        //初始化转发和穿透服务器
        iceServers = new LinkedList<>();
        iceServers.add(new PeerConnection.IceServer("stun:47.254.34.146:3478"));
        iceServers.add(new PeerConnection.IceServer("turn:47.254.34.146:3478", "dds", "123456"));
        // 初始化线程
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    public void initPeerConnectionFactory(Context context, AppPeerConnectionEvents events) {
        this.events = events;
        //初始化Factory
        PeerConnectionFactory.initializeInternalTracer();
        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true);
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.networkIgnoreMask = 0;
        factory = new PeerConnectionFactory(options);

        mediaStream = factory.createLocalMediaStream("ARDAMS");
        //设置视频
        videoCapturer = createVideoCapture(context);
        mediaStream.addTrack(createVideoTrack(videoCapturer));
        // 设置音频
        mediaStream.addTrack(createAudioTrack());


    }


    public void createPeerConnection(EglBase.Context eglBaseContext,
                                     VideoRenderer.Callbacks localRender,
                                     VideoRenderer.Callbacks remoteRender,
                                     boolean isInitiator,
                                     String remoteUserId,
                                     SessionDescription sdp) {
        this.localRender = localRender;
        this.remoteRender = remoteRender;
        this.isInitiator = isInitiator;
        executor.execute(new Runnable() {
            @Override
            public void run() {

            }
        });
    }


    public void setRemoteDescription(final SessionDescription sdp) {
        executor.execute(new Runnable() {
            @Override
            public void run() {

            }
        });
    }

    public void addRemoteIceCandidate(final IceCandidate candidate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
            }
        });
    }

    public void removeRemoteIceCandidates(final IceCandidate[] candidates) {
        executor.execute(new Runnable() {
            @Override
            public void run() {

            }
        });
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
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private AudioSource audioSource;
    private AudioTrack localAudioTrack;

    private VideoSource videoSource;
    private VideoTrack localVideoTrack;

    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";
    private static final int HD_VIDEO_WIDTH = 480;
    private static final int HD_VIDEO_HEIGHT = 640;
    private static int videoFps = 30;

    private MediaConstraints audioConstraints;

    private AudioTrack createAudioTrack() {
        audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("levelControl", "true"));
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


}
