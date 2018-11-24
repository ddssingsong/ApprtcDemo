package com.dds.webrtc.signal;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.dds.webrtc.bean.SignalingParameters;
import com.dds.webrtc.callback.AppSignalingEvents;
import com.dds.webrtc.room.AsyncHttpURLConnection;
import com.dds.webrtc.room.RoomParametersFetcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import de.tavendo.autobahn.WebSocket;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;

/**
 * Created by dds on 2018/11/7.
 * android_shuai@163.com
 */
public class SignalClient {
    private static final String TAG = "dds_SignalClient";
    private AppSignalingEvents events;
    private WebSocketConnection ws;
    private WebSocketObserver wsObserver;
    private String roomID;
    private static final String ROOM_JOIN = "join";
    private static final String ROOM_LEAVE = "leave";
    private String clientId;
    private List<String> clients;
    private boolean initiator;

    private String host;
    private String roomId;

    private final Handler handler;

    public SignalClient(AppSignalingEvents events) {
        this.events = events;
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    //连接房间服务器
    public void connectRoom(String host, final String roomId) {
        this.host = host;
        this.roomID = roomId;
        //创建房间并获取房间服务器信息
        RoomParametersFetcher fetcher = new RoomParametersFetcher(host + "/" + ROOM_JOIN + "/" + roomId, null, new RoomParametersFetcher.RoomParametersFetcherEvents() {
            @Override
            public void onSignalingParametersReady(final SignalingParameters params) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        String wsUrl = params.wssUrl;
                        clientId = params.clientId;
                        initiator = params.initiator;
                        clients = params.clients;
                        // 开始连接信令服务器
                        connect(wsUrl, roomId, clientId, initiator);


                    }
                });
            }

            @Override
            public void onSignalingParametersError(String description) {


            }
        });
        fetcher.makeRequest();
    }


    private void connect(String wsUrl, String roomId, String clientId, boolean initiator) {
        ws = new WebSocketConnection();
        wsObserver = new WebSocketObserver();
        try {
            // 连接信令服务器 注册自己
            ws.connect(new URI(wsUrl), wsObserver);
        } catch (WebSocketException | URISyntaxException e) {
            e.printStackTrace();
        }
        this.roomID = roomId;
        this.clientId = clientId;
        this.initiator = initiator;


    }

    private void register() {
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", "register");
            json.put("roomid", roomID);
            json.put("clientid", clientId);
            Log.d(TAG, "C->WSS: " + json.toString());
            ws.sendTextMessage(json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }


    }


    public void sendOfferSdp(final SessionDescription sdp, final String remoteId, final String clientId) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "fromId", clientId);
                jsonPut(json, "toId", remoteId);
                jsonPut(json, "type", "offer");
                send(json.toString()); // sendOfferSdp
            }
        });

    }

    public void sendAnswerSdp(final SessionDescription sdp, final String remoteId, final String clientId) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "fromId", clientId);
                jsonPut(json, "toId", remoteId);
                jsonPut(json, "type", "answer");
                send(json.toString());//sendAnswerSdp
            }
        });
    }

    public void sendLocalIceCandidate(final IceCandidate candidate, final String remoteUserId, final String clientId) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "type", "candidate");
                jsonPut(json, "toId", remoteUserId);
                jsonPut(json, "fromId", clientId);
                jsonPut(json, "label", candidate.sdpMLineIndex);
                jsonPut(json, "id", candidate.sdpMid);
                jsonPut(json, "candidate", candidate.sdp);
                send(json.toString()); //sendLocalIceCandidate
            }
        });
    }

    public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates, final String remoteUserId, final String clientId) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "type", "remove-candidates");
                jsonPut(json, "fromId", clientId);
                jsonPut(json, "toId", remoteUserId);
                JSONArray jsonArray = new JSONArray();
                for (final IceCandidate candidate : candidates) {
                    jsonArray.put(toJsonCandidate(candidate));
                }
                jsonPut(json, "candidates", jsonArray);
                send(json.toString());//sendLocalIceCandidateRemove
            }
        });
    }

    public void sendBye(final String clientId) {
        AsyncHttpURLConnection httpConnection =
                new AsyncHttpURLConnection("POST", host + "/" + ROOM_LEAVE + "/" + roomId + "/" + clientId, null, new AsyncHttpURLConnection.AsyncHttpEvents() {
                    @Override
                    public void onHttpError(String errorMessage) {
                    }

                    @Override
                    public void onHttpComplete(String response) {
                        Log.e("dds", response);

                    }
                });
        httpConnection.send();

        handler.post(new Runnable() {
            @Override
            public void run() {


                JSONObject json = new JSONObject();
                jsonPut(json, "type", "bye");
                jsonPut(json, "fromId", clientId);
                send(json.toString()); //sendBye
            }
        });
    }


    private class WebSocketObserver implements WebSocket.WebSocketConnectionObserver {

        @Override
        public void onOpen() {
            Log.d(TAG, "WebSocketObserver onOpen");
            handler.post(new Runnable() {
                @Override
                public void run() {
                    //进入房间
                    register();
                    // 回调身份信息
                    events.onConnectedToRoom(initiator, clients, clientId);
                }
            });
        }

        @Override
        public void onClose(WebSocketCloseNotification webSocketCloseNotification, String s) {
            Log.d(TAG, "WebSocketObserver onClose");
            events.onChannelClose();
        }

        @Override
        public void onTextMessage(String msg) {
            Log.d(TAG, "WebSocketObserver onTextMessage\n" + msg);
            try {
                JSONObject json = new JSONObject(msg);
                String msgText = json.getString("msg");
                if (msgText.length() > 0) {
                    json = new JSONObject(msgText);
                    String type = json.optString("type");
                    if (type.equals("candidate")) {
                        String remoteUserId = json.optString("fromId");
                        String clientId = json.optString("toId");
                        events.onRemoteIceCandidate(toJavaCandidate(json), remoteUserId, clientId);
                    } else if (type.equals("remove-candidates")) {
                        String remoteUserId = json.optString("fromId");
                        String clientId = json.optString("toId");
                        JSONArray candidateArray = json.getJSONArray("candidates");
                        IceCandidate[] candidates = new IceCandidate[candidateArray.length()];
                        for (int i = 0; i < candidateArray.length(); ++i) {
                            candidates[i] = toJavaCandidate(candidateArray.getJSONObject(i));
                        }
                        events.onRemoteIceCandidatesRemoved(candidates, remoteUserId, clientId);
                    } else if (type.equals("answer")) {
                        SessionDescription sdp = new SessionDescription(
                                SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp"));
                        String remoteUserId = json.getString("fromId");
                        String clientId = json.getString("toId");
                        events.onRemoteDescription(sdp, remoteUserId, clientId, false);

                    } else if (type.equals("offer")) {
                        SessionDescription sdp = new SessionDescription(
                                SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp"));
                        String remoteUserId = json.getString("fromId");
                        String clientId = json.getString("toId");
                        events.onRemoteDescription(sdp, remoteUserId, clientId, true);

                    } else if (type.equals("bye")) {
                        String remoteUserId = json.getString("fromId");
                        events.onRemoteDisconnect(remoteUserId);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onRawTextMessage(byte[] bytes) {

        }

        @Override
        public void onBinaryMessage(byte[] bytes) {

        }
    }

    private void send(String message) {
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", "send");
            json.put("msg", message);
            message = json.toString();
            Log.d("dds_test", "C->WSS: " + message);
            ws.sendTextMessage(message);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
    }

    private IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
        return new IceCandidate(
                json.getString("id"), json.getInt("label"), json.getString("candidate"));
    }

    private JSONObject toJsonCandidate(final IceCandidate candidate) {
        JSONObject json = new JSONObject();
        jsonPut(json, "label", candidate.sdpMLineIndex);
        jsonPut(json, "id", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);
        return json;
    }

    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
