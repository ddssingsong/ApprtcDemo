package com.dds.webrtc.bean;

/**
 * Created by dds on 2018/11/7.
 * android_shuai@163.com
 */
public class RoomConnectionParameters {
    public final String roomUrl;
    public final String roomId;
    public final String urlParameters;

    public RoomConnectionParameters(String roomUrl, String roomId, String urlParameters) {
        this.roomUrl = roomUrl;
        this.roomId = roomId;
        this.urlParameters = urlParameters;
    }

}
