package com.dds.webrtc.callback;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.List;

/**
 * Created by dds on 2018/11/12.
 * android_shuai@163.com
 */
public interface AppSignalingEvents {

    void onConnectedToRoom(final boolean initiator, List<String> clients, String clientId);

    void onRemoteDescription(final SessionDescription sdp, String userId, String remoteUserId, boolean isReceive);

    void onRemoteIceCandidate(final IceCandidate candidate, String userId, String remoteUserId);

    void onRemoteIceCandidatesRemoved(final IceCandidate[] candidates, String userId, String remoteUserId);

    void onRemoteDisconnect(String remoteId);

    void onChannelClose();

    void onChannelError(final String description);
}
