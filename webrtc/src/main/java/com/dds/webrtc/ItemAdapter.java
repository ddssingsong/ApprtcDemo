package com.dds.webrtc;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.dds.webrtc.bean.Item;
import com.dds.webrtc.callback.AppPeerConnectionEvents;
import com.dds.webrtc.callback.ProxyRenderer;
import com.dds.webrtc.utils.Util;

import org.webrtc.EglBase;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;

import java.util.List;

/**
 * Created by dds on 2018/11/7.
 * android_shuai@163.com
 */
public class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ViewHolder> {

    private Context mContext;
    private List<Item> mDatas;
    private int width;
    private EglBase rootEglBase;
    private VideoCapturer videoCapturer;
    private AppPeerConnectionEvents events;

    public ItemAdapter(Context context, List<Item> mDatas, EglBase rootEglBase, VideoCapturer videoCapturer, AppPeerConnectionEvents events) {
        this.mContext = context;
        this.mDatas = mDatas;
        width = Util.getScreenWidth(context);
        this.rootEglBase = rootEglBase;
        this.videoCapturer = videoCapturer;
        this.events = events;

    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.wr_room_item, parent, false);
        ViewHolder holder = new ViewHolder(view);
        setListener(parent, holder, viewType);
        return holder;
    }

    private void setListener(ViewGroup parent, ViewHolder holder, int viewType) {

    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Item item = mDatas.get(position);
        holder.renderer.requestLayout();
        holder.renderer.init(rootEglBase.getEglBaseContext(), null);
        holder.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        holder.renderer.setEnableHardwareScaler(true);
        holder.renderer.setLayoutParams(new RelativeLayout.LayoutParams(width / 3, Util.dip2px(mContext, 100)));
        ProxyRenderer proxyRenderer = new ProxyRenderer();
        proxyRenderer.setTarget(holder.renderer);

        if (!item.initiator) {
            if(item.peerClient!=null){

            }


        }
    }


    @Override
    public int getItemCount() {
        return mDatas == null ? 0 : mDatas.size();
    }


    class ViewHolder extends RecyclerView.ViewHolder {
        SurfaceViewRenderer renderer;

        ViewHolder(View itemView) {
            super(itemView);
            renderer = itemView.findViewById(R.id.pip_video_view);
        }
    }


}
