package com.dds.webrtc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.dds.webrtc.callback.AppPeerConnectionEvents;
import com.dds.webrtc.callback.AppSignalingEvents;
import com.dds.webrtc.callback.ProxyRenderer;
import com.dds.webrtc.client.WebPeerClient;
import com.dds.webrtc.signal.SignalClient;

import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 视频会议界面
 */
public class ChatRoomActivity extends AppCompatActivity implements AppSignalingEvents, AppPeerConnectionEvents, View.OnClickListener {

    private EglBase rootEglBase;
    private SignalClient signalClient;
    private LinearLayout render_content;
    private SurfaceViewRenderer render_local;
    private Button hung_up;
    private ProxyRenderer localRender;
    private WebPeerClient peerClient;

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
        hung_up = findViewById(R.id.hung_up);
        hung_up.setOnClickListener(this);
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

        peerClient = WebPeerClient.getInstance();
        peerClient.initPeerConnectionFactory(this, localRender, this);

    }


    private void startCall() {
        // 注册信令服务器
        signalClient.connectRoom(host, roomId);


    }


    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.hung_up) {
            disconnect();
        }

    }

    private void disconnect() {
        // 关闭通道
        localRender.setTarget(null);
        if (render_local != null) {
            render_local.release();
            render_local = null;
        }
        if (signalClient != null) {
            signalClient.sendBye(clientId);
        }

        peerClient.disconnect();
        finish();


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
        Log.e("dds_test", "房间：" + clients.toString());
        Log.e("dds_test", "clientId：" + clientId);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (clients.size() > 1) {
                    //房间里有人，需要创建PeerConnection并发送offer
                    for (int i = 0; i < clients.size(); i++) {
                        String otherId = clients.get(i);
                        if (otherId.equals(clientId)) continue;
                        // 创建连接

                        ProxyRenderer renderer = getSurfaceRender(remoteCount, otherId);
                        WebPeerClient peerConnectionClient = WebPeerClient.getInstance();
                        peerConnectionClient.createPeerConnection(rootEglBase.getEglBaseContext(),
                                renderer, initiator, otherId, null);
                        remoteCount++;
                        break;
                    }

                }
            }
        });


    }


    private HashMap<String, SurfaceViewRenderer> surfaceViewRendererMap = new HashMap<>();

    private ProxyRenderer getSurfaceRender(int size, String otherId) {
        if (size <= 3) {
            LinearLayout linearLayout = (LinearLayout) render_content.getChildAt(0);
            SurfaceViewRenderer viewRenderer = (SurfaceViewRenderer) linearLayout.getChildAt(size);
            viewRenderer.init(rootEglBase.getEglBaseContext(), null);
            viewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
            viewRenderer.setEnableHardwareScaler(true);
            surfaceViewRendererMap.put(otherId, viewRenderer);
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
                    Log.e("dds_test", "有人进来了");
                    // 发起者收到了offer 需要创建peerConnection，并回复answer
                    ProxyRenderer renderer = getSurfaceRender(remoteCount, remoteId);
                    WebPeerClient peerConnectionClient = WebPeerClient.getInstance();
                    peerConnectionClient.createPeerConnection(rootEglBase.getEglBaseContext(), renderer, initiator, remoteId, sdp);
                    remoteCount++;
                } else {
                    // 后进来的收到了answer 需要setRemoteDescription
                    peerClient.setRemoteDescription(sdp, remoteId);
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
                peerClient.addRemoteIceCandidate(candidate, remoteUserId);
            }
        });

    }

    @Override
    public void onRemoteIceCandidatesRemoved(final IceCandidate[] candidates, final String remoteUserId, String clientId) {
        if (!clientId.equals(this.clientId)) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                peerClient.removeRemoteIceCandidates(candidates, remoteUserId);
            }
        });
    }

    @Override
    public void onRemoteDisconnect(String remoteId) {
        peerClient.removeRemoteUser(remoteId);
        // 关闭该人通道
        Iterator item = surfaceViewRendererMap.entrySet().iterator();
        while (item.hasNext()) {
            Map.Entry entry = (Map.Entry) item.next();
            String key = (String) entry.getKey();
            if (key.equals(remoteId)) {
                ((SurfaceViewRenderer) entry.getValue()).release();
                surfaceViewRendererMap.remove(key);
            }

        }

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
