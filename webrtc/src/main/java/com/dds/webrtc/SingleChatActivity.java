package com.dds.webrtc;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.dds.webrtc.bean.RoomConnectionParameters;
import com.dds.webrtc.bean.SignalingParameters;
import com.dds.webrtc.callback.PeerConnectionEvents;
import com.dds.webrtc.callback.ProxyRenderer;
import com.dds.webrtc.callback.SignalingEvents;

import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;


/**
 * 单聊界面
 */
public class SingleChatActivity extends AppCompatActivity implements SignalingEvents, PeerConnectionEvents {

    private SurfaceViewRenderer fullscreenView;
    private SurfaceViewRenderer pipView;
    private final ProxyRenderer remoteProxyRenderer = new ProxyRenderer();
    private final ProxyRenderer localProxyRenderer = new ProxyRenderer();


    private EglBase rootEglBase;
    private PeerConnectionClient peerConnectionClient = null;
    private AppRtcClient rtcClient;
    private SignalingParameters signalingParameters;
    private String url;
    private String roomId;


    public static void openActivity(Activity activity, String url, String roomId) {
        Intent intent = new Intent(activity, SingleChatActivity.class);
        intent.putExtra("url", url);
        intent.putExtra("roomId", roomId);
        activity.startActivity(intent);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());
        setContentView(R.layout.wr_activity_single_chat);
        initView();
        initVar();
    }


    private void initView() {
        fullscreenView = findViewById(R.id.fullscreen_video_view);
        pipView = findViewById(R.id.pip_video_view);
    }

    private void initVar() {
        Intent intent = getIntent();
        url = intent.getStringExtra("url");
        roomId = intent.getStringExtra("roomId");

        rootEglBase = EglBase.create();
        //设置小窗口显示属性
        pipView.init(rootEglBase.getEglBaseContext(), null);
        pipView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        pipView.setZOrderMediaOverlay(true);
        pipView.setEnableHardwareScaler(true);

        //设置大窗口的属性
        fullscreenView.init(rootEglBase.getEglBaseContext(), null);
        fullscreenView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        fullscreenView.setEnableHardwareScaler(true);

        setSwappedFeeds(true);

        //初始化
        peerConnectionClient = PeerConnectionClient.getInstance();
        peerConnectionClient.initPeerConnectionFactory(getApplicationContext(), this);
        startCall();
    }

    private void startCall() {
        rtcClient = new AppRtcClient(this);
        RoomConnectionParameters parameters = new
                RoomConnectionParameters(url, roomId, null);
        rtcClient.connectRoom(parameters);
    }


    private boolean isSwappedFeeds;  //是否本地显示大屏

    private void setSwappedFeeds(boolean isSwappedFeeds) {
        this.isSwappedFeeds = isSwappedFeeds;
        localProxyRenderer.setTarget(isSwappedFeeds ? fullscreenView : pipView);
        remoteProxyRenderer.setTarget(isSwappedFeeds ? pipView : fullscreenView);
        fullscreenView.setMirror(isSwappedFeeds);
        pipView.setMirror(!isSwappedFeeds);
    }


    //=========================================AppRtcClient=========================================

    private void onConnectedToRoomInternal(SignalingParameters params) {
        signalingParameters = params;
        VideoCapturer videoCapturer = createVideoCapturer();
        peerConnectionClient.createPeerConnection(rootEglBase.getEglBaseContext(), localProxyRenderer,
                remoteProxyRenderer, videoCapturer, signalingParameters);

        if (signalingParameters.initiator) {
            logAndToast("Creating OFFER...");
            peerConnectionClient.createOffer();
        } else {
            if (params.offerSdp != null) {
                peerConnectionClient.setRemoteDescription(params.offerSdp);
                logAndToast("Creating ANSWER...");
                peerConnectionClient.createAnswer();
            }
            if (params.iceCandidates != null) {
                for (IceCandidate iceCandidate : params.iceCandidates) {
                    peerConnectionClient.addRemoteIceCandidate(iceCandidate);
                }
            }
        }

    }

    @Override
    public void onConnectedToRoom(final SignalingParameters params) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onConnectedToRoomInternal(params);
            }
        });


    }


    @Override
    public void onRemoteDescription(final SessionDescription sdp) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
                    return;
                }
                peerConnectionClient.setRemoteDescription(sdp);
                if (!signalingParameters.initiator) {
                    logAndToast("Creating ANSWER...");
                    peerConnectionClient.createAnswer();
                }
            }
        });
    }

    @Override
    public void onRemoteIceCandidate(final IceCandidate candidate) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG, "Received ICE candidate for a non-initialized peer connection.");
                    return;
                }
                peerConnectionClient.addRemoteIceCandidate(candidate);
            }
        });
    }

    @Override
    public void onRemoteIceCandidatesRemoved(final IceCandidate[] candidates) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG, "Received ICE candidate removals for a non-initialized peer connection.");
                    return;
                }
                peerConnectionClient.removeRemoteIceCandidates(candidates);
            }
        });
    }

    @Override
    public void onChannelClose() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("Remote end hung up; dropping PeerConnection");
                disconnect();
            }
        });
    }

    @Override
    public void onChannelError(String description) {
        disconnectWithErrorMessage(description);
    }

    //=======================================PeerConnectionClient==================================
    @Override
    public void onLocalDescription(SessionDescription sdp) {
        if (rtcClient != null) {
            if (signalingParameters.initiator) {
                rtcClient.sendOfferSdp(sdp);
            } else {
                rtcClient.sendAnswerSdp(sdp);
            }
        }
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (rtcClient != null) {
                    rtcClient.sendLocalIceCandidate(candidate);
                }
            }
        });
    }

    @Override
    public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (rtcClient != null) {
                    rtcClient.sendLocalIceCandidateRemovals(candidates);
                }
            }
        });
    }

    @Override
    public void onIceConnected() {
        setSwappedFeeds(false /* isSwappedFeeds */);
    }

    @Override
    public void onIceDisconnected() {

    }

    @Override
    public void onPeerConnectionClosed() {

    }

    @Override
    public void onPeerConnectionStatsReady(StatsReport[] reports) {

    }

    @Override
    public void onPeerConnectionError(String description) {

    }


    // ============================================tools==========================================

    private VideoCapturer createVideoCapturer() {
        VideoCapturer videoCapturer = null;
        if (useCamera2()) {
            videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
        } else {
            videoCapturer = createCameraCapturer(new Camera1Enumerator(true));
        }
        if (videoCapturer == null) {
            reportError("Failed to open camera");
            return null;
        }
        return videoCapturer;
    }

    private boolean useCamera2() {
        return Camera2Enumerator.isSupported(this);
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

    @TargetApi(19)
    private static int getSystemUiVisibility() {
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        return flags;
    }

    private Toast logToast;
    private boolean isError;
    private boolean activityRunning;
    private static final String TAG = SingleChatActivity.class.getSimpleName();

    private void reportError(final String description) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isError) {
                    isError = true;
                    disconnectWithErrorMessage(description);
                }
            }
        });
    }

    private void disconnectWithErrorMessage(final String errorMessage) {
        if (!activityRunning) {
            Log.e(TAG, "Critical error: " + errorMessage);
            disconnect();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("错误提示")
                    .setMessage(errorMessage)
                    .setCancelable(false)
                    .setNeutralButton("OK",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                    disconnect();
                                }
                            })
                    .create()
                    .show();
        }
    }

    private void logAndToast(String msg) {
        if (logToast != null) {
            logToast.cancel();
        }
        logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        logToast.show();
    }


    private void disconnect() {
        activityRunning = false;
        finish();

    }

}
