package com.dds.webrtc.bean;

import com.dds.webrtc.client.WebPeerConnManager;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

/**
 * Created by dds on 2018/11/7.
 * android_shuai@163.com
 */
public class Item {
    public String clientId;
    public String roomId;
    public String host;
    public boolean initiator;
    public SessionDescription sdp;
    public IceCandidate candidate;
    public WebPeerConnManager peerClient;
}
