package com.dds.webrtc.bean;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.List;

/**
 * Created by dds on 2018/11/7.
 * android_shuai@163.com
 */
public class SignalingParameters {
    public final List<PeerConnection.IceServer> iceServers;
    public final boolean initiator;
    public final String clientId;
    public final String wssUrl;
    public final String wssPostUrl;
    public final SessionDescription offerSdp;//对方的sdp信息描述符
    public final List<IceCandidate> iceCandidates;//信令服务器列表
    public List<String> clients;

    public SignalingParameters(List<PeerConnection.IceServer> iceServers, boolean initiator,
                               String clientId, String wssUrl, String wssPostUrl, SessionDescription offerSdp,
                               List<IceCandidate> iceCandidates, List<String> clients) {
        this.iceServers = iceServers;
        this.initiator = initiator;
        this.clientId = clientId;
        this.wssUrl = wssUrl;
        this.wssPostUrl = wssPostUrl;
        this.offerSdp = offerSdp;
        this.iceCandidates = iceCandidates;
        this.clients = clients;
    }
}
