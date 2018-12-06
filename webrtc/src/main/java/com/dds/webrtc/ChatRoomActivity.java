package com.dds.webrtc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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
    private SurfaceViewRenderer render_local;
    private SurfaceViewRenderer render_remote1;
    private SurfaceViewRenderer render_remote2;
    private SurfaceViewRenderer render_remote3;
    private Map<String, SurfaceViewRenderer> viewRendererMap = new HashMap<>();


    private ProxyRenderer localRenderProxy;
    private ProxyRenderer remoteRenderProxy1;
    private ProxyRenderer remoteRenderProxy2;
    private ProxyRenderer remoteRenderProxy3;
    private Map<String, ProxyRenderer> proxyRendererMap = new HashMap<>();


    private Button hung_up;

    private WebPeerClient peerClient;

    private String host;
    private String roomId;
    private boolean initiator;
    private List<String> clients = new ArrayList<>();
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
        render_local = findViewById(R.id.render_local);
        render_remote1 = findViewById(R.id.remote_view_render1);
        render_remote2 = findViewById(R.id.remote_view_render2);
        render_remote3 = findViewById(R.id.remote_view_render3);
        hung_up = findViewById(R.id.hung_up);
        hung_up.setOnClickListener(this);
    }

    private void initVar() {
        Intent intent = getIntent();
        host = intent.getStringExtra("host");
        roomId = intent.getStringExtra("roomId");

        ((TextView) findViewById(R.id.room)).setText(roomId);

        rootEglBase = EglBase.create();
        signalClient = new SignalClient(this);


        // 本地图像初始化
        render_local.init(rootEglBase.getEglBaseContext(), null);
        render_local.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        render_local.setEnableHardwareScaler(true);
        render_local.setZOrderMediaOverlay(true);
        localRenderProxy = new ProxyRenderer();
        localRenderProxy.setTarget(render_local);

        //远端图像初始化
        render_remote1.init(rootEglBase.getEglBaseContext(), null);
        render_remote1.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        render_remote1.setEnableHardwareScaler(true);
        remoteRenderProxy1 = new ProxyRenderer();
        remoteRenderProxy1.setTarget(render_remote1);

        render_remote2.init(rootEglBase.getEglBaseContext(), null);
        render_remote2.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        render_remote2.setEnableHardwareScaler(true);
        remoteRenderProxy2 = new ProxyRenderer();
        remoteRenderProxy2.setTarget(render_remote2);

        render_remote3.init(rootEglBase.getEglBaseContext(), null);
        render_remote3.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        render_remote3.setEnableHardwareScaler(true);
        remoteRenderProxy3 = new ProxyRenderer();
        remoteRenderProxy3.setTarget(render_remote3);


        peerClient = WebPeerClient.getInstance();
        peerClient.initPeerConnectionFactory(this, localRenderProxy, this);

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
        localRenderProxy.setTarget(null);
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
        Log.e("dds_test", "房间的其他人：" + clients.toString());
        Log.e("dds_test", "clientId：" + clientId);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int size = ChatRoomActivity.this.clients.size();
                //房间里有人，需要创建PeerConnection并发送offer
                for (int i = 0; i < size; i++) {
                    String remoteId = clients.get(i);
                    if (viewRendererMap.size() == 0) {
                        viewRendererMap.put(remoteId, render_remote1);
                    } else if (viewRendererMap.size() == 1) {
                        viewRendererMap.put(remoteId, render_remote2);
                    } else if (viewRendererMap.size() == 2) {
                        viewRendererMap.put(remoteId, render_remote3);
                    }
                    if (proxyRendererMap.size() == 0) {
                        proxyRendererMap.put(remoteId, remoteRenderProxy1);
                    } else if (proxyRendererMap.size() == 1) {
                        proxyRendererMap.put(remoteId, remoteRenderProxy2);
                    } else if (proxyRendererMap.size() == 2) {
                        proxyRendererMap.put(remoteId, remoteRenderProxy3);
                    }
                    // 创建连接
                    WebPeerClient peerConnectionClient = WebPeerClient.getInstance();
                    peerConnectionClient.createPeerConnection(rootEglBase.getEglBaseContext(),
                            proxyRendererMap.get(remoteId), initiator, remoteId, null);
                }
            }
        });


    }


    @Override
    public void onRemoteDescription(final SessionDescription sdp, final String remoteId, String clientId, final boolean isOffer) {
        if (!this.clientId.equals(clientId)) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isOffer) {
                    Log.e("dds_test", remoteId + ".进入了房间");
                    if (viewRendererMap.size() == 0) {
                        viewRendererMap.put(remoteId, render_remote1);
                    } else if (viewRendererMap.size() == 1) {
                        viewRendererMap.put(remoteId, render_remote2);
                    } else if (viewRendererMap.size() == 2) {
                        viewRendererMap.put(remoteId, render_remote3);
                    }
                    if (proxyRendererMap.size() == 0) {
                        proxyRendererMap.put(remoteId, remoteRenderProxy1);
                    } else if (proxyRendererMap.size() == 1) {
                        proxyRendererMap.put(remoteId, remoteRenderProxy2);
                    } else if (proxyRendererMap.size() == 2) {
                        proxyRendererMap.put(remoteId, remoteRenderProxy3);
                    }

                    // 发起者收到了offer 需要创建peerConnection，并回复answer
                    WebPeerClient peerConnectionClient = WebPeerClient.getInstance();
                    peerConnectionClient.createPeerConnection(rootEglBase.getEglBaseContext(), proxyRendererMap.get(remoteId), true, remoteId, sdp);
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
        Iterator item = viewRendererMap.entrySet().iterator();
        while (item.hasNext()) {
            Map.Entry entry = (Map.Entry) item.next();
            String key = (String) entry.getKey();
            if (key.equals(remoteId)) {
                ((SurfaceViewRenderer) entry.getValue()).release();
                viewRendererMap.remove(key);
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
