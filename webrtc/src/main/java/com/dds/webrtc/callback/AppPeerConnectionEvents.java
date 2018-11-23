package com.dds.webrtc.callback;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;

/**
 * Created by dds on 2018/11/12.
 * android_shuai@163.com
 */
public interface AppPeerConnectionEvents {

    void onLocalDescription(final SessionDescription sdp, String remoteUserId);

    void onIceCandidate(final IceCandidate candidate, String clientId);

    void onIceCandidatesRemoved(final IceCandidate[] candidates, String clientId);

    void onIceConnected();

    void onIceDisconnected();

    void onPeerConnectionClosed();

    void onPeerConnectionStatsReady(final StatsReport[] reports);

    void onPeerConnectionError(final String description);
}
