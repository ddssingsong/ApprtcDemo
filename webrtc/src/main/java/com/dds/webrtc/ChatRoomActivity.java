package com.dds.webrtc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.LinearLayout;

import com.dds.webrtc.callback.AppPeerConnectionEvents;
import com.dds.webrtc.callback.AppSignalingEvents;
import com.dds.webrtc.callback.ProxyRenderer;
import com.dds.webrtc.client.WebPeerConnManager;
import com.dds.webrtc.signal.SignalClient;

import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 视频会议界面
 */
public class ChatRoomActivity extends AppCompatActivity implements AppSignalingEvents, AppPeerConnectionEvents {
    private final static String TAG = "dds_ChatRoomActivity";

    private EglBase rootEglBase;
    private SignalClient signalClient;
    private LinearLayout render_content;
    private SurfaceViewRenderer render_local;

    private ProxyRenderer localRender;
    private List<ProxyRenderer> remoteRenders = new LinkedList<>();

    private WebPeerConnManager peerClients;

    private String host;
    private String roomId;
    private boolean initiator;
    private List<String> clients = new ArrayList<>();
    private int remoteCount = 0;
    private String clientId;

    public static void openActivity(Activity activity, String url, String roomId) {
        Intent intent = new Intent(activity, ChatRoomActivity.class);
        intent.putExtra("host", url);
        intent.putExtra("roomId", roomId);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_room);
        initView();
        initVar();
        startCall();

    }


    private void initView() {
        render_content = findViewById(R.id.render_content);
        render_local = findViewById(R.id.render_local);

    }

    private void initVar() {
        Intent intent = getIntent();
        host = intent.getStringExtra("host");
        roomId = intent.getStringExtra("roomId");

        rootEglBase = EglBase.create();
        signalClient = new SignalClient(this);


        render_local.init(rootEglBase.getEglBaseContext(), null);
        render_local.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        render_local.setEnableHardwareScaler(true);
        localRender = new ProxyRenderer();
        localRender.setTarget(render_local);

        peerClients = new WebPeerConnManager();
        peerClients.initPeerConnectionFactory(this, this);

    }


    private void startCall() {
        // 注册信令服务器
        signalClient.connectRoom(host, roomId);


    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    //====SignalClient================================================
    @Override
    public void onConnectedToRoom(final boolean initiator, final List<String> clients, final String clientId) {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.initiator = initiator;
        this.clients = clients;
        this.clientId = clientId;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (clients.size() > 1) {
                    //房间里有人，需要创建PeerConnection并发送offer
                    for (int i = 0; i < clients.size(); i++) {
                        String otherId = clients.get(i);
                        if (otherId.equals(clientId)) continue;
                        // 创建连接
                        ProxyRenderer renderer = getSurfaceRender(remoteCount);
                        WebPeerConnManager peerConnectionClient = WebPeerConnManager.getInstance();
                        peerConnectionClient.createPeerConnection(rootEglBase.getEglBaseContext(),
                                localRender, renderer, initiator, otherId, null);
                        remoteCount++;
                        break;
                    }

                }
            }
        });


    }


    private ProxyRenderer getSurfaceRender(int size) {
        if (size <= 3) {
            LinearLayout linearLayout = (LinearLayout) render_content.getChildAt(0);
            SurfaceViewRenderer viewRenderer = (SurfaceViewRenderer) linearLayout.getChildAt(size);
            viewRenderer.init(rootEglBase.getEglBaseContext(), null);
            viewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
            viewRenderer.setEnableHardwareScaler(true);
            ProxyRenderer proxyRenderer = new ProxyRenderer();
            proxyRenderer.setTarget(viewRenderer);
            return proxyRenderer;
        }

        return null;
    }


    @Override
    public void onRemoteDescription(final SessionDescription sdp, final String remoteId, String clientId, final boolean isOffer) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isOffer) {
                    for (int i = 0; i < peerClients.size(); i++) {
                        if (peerClients.get(i).getRemoteUserId().equals(remoteId)) {
                            return;
                        }
                    }
                    Log.e("dds_test", "有人进来了");
                    // 发起者收到了offer 需要创建peerConnection，并回复answer
                    ProxyRenderer renderer = getSurfaceRender(remoteCount);
                    WebPeerConnManager peerConnectionClient = WebPeerConnManager.getInstance();
                    peerConnectionClient.createPeerConnection(rootEglBase.getEglBaseContext(),
                            localRender, renderer, initiator, remoteId, sdp);
                    peerClients.add(peerConnectionClient);
                    remoteCount++;
                } else {
                    //收到了answer 需要setRemoteDescription
                    for (WebPeerConnManager webPeerClient : peerClients) {
                        if (webPeerClient.getRemoteUserId().equals(remoteId)) {
                            webPeerClient.setRemoteDescription(sdp);
                        }
                    }
                }
            }
        });


    }

    @Override
    public void onRemoteIceCandidate(final IceCandidate candidate, final String remoteUserId, String clientId) {
        if (!clientId.equals(this.clientId)) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (WebPeerConnManager webPeerClient : peerClients) {
                    if (webPeerClient.getRemoteUserId().equals(remoteUserId)) {
                        webPeerClient.addRemoteIceCandidate(candidate);
                    }
                }
            }
        });

    }

    @Override
    public void onRemoteIceCandidatesRemoved(final IceCandidate[] candidates, final String remoteUserId, String clientId) {
        if (!clientId.equals(this.clientId)) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (WebPeerConnManager webPeerClient : peerClients) {
                    if (webPeerClient.getRemoteUserId().equals(remoteUserId)) {
                        webPeerClient.removeRemoteIceCandidates(candidates);
                    }
                }
            }
        });
    }

    @Override
    public void onChannelClose() {

    }

    @Override
    public void onChannelError(String description) {

    }


    //==================================AppPeerConnectionEvents=====================================
    @Override
    public void onLocalDescription(final SessionDescription sdp, final String remoteId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!initiator) {
                    signalClient.sendOfferSdp(sdp, remoteId, clientId);
                } else {
                    signalClient.sendAnswerSdp(sdp, remoteId, clientId);
                }

            }
        });


    }

    @Override
    public void onIceCandidate(final IceCandidate candidate, final String remoteUserId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (signalClient != null) {
                    signalClient.sendLocalIceCandidate(candidate, remoteUserId, clientId);
                }
            }
        });
    }

    @Override
    public void onIceCandidatesRemoved(final IceCandidate[] candidates, final String remoteUserId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (signalClient != null) {
                    signalClient.sendLocalIceCandidateRemovals(candidates, remoteUserId, clientId);
                }
            }
        });
    }

    @Override
    public void onIceConnected() {

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

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }
}
