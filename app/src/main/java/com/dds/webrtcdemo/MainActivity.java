package com.dds.webrtcdemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.appspot.apprtc.CallActivity;


public class MainActivity extends AppCompatActivity {
    private EditText edit_room;
    private EditText edit_room_id;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        edit_room = findViewById(R.id.edit_room);
        edit_room_id = findViewById(R.id.edit_room_id);

        checkPermission();



    }

    private int REQUEST_CODE = 1000;

    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                Toast.makeText(MainActivity.this, "您已禁止该权限，需要重新开启。", Toast.LENGTH_SHORT).show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.CAMERA
                        },
                        REQUEST_CODE);
            }
        }
    }

    public void call(View view) {
//        SingleChatActivity.openActivity(this,
//                edit_room.getText().toString().trim(),
//                edit_room_id.getText().toString().trim());
    }

    public void room(View view) {
//        ChatRoomActivity.openActivity(this,
//                edit_room.getText().toString().trim(),
//                edit_room_id.getText().toString().trim());
    }

    public void webrtc(View view) {
//        Intent intent = new Intent(this, ConnectActivity.class);
//        startActivity(intent);

        call();

    }

    private void call() {
        Uri uri = Uri.parse(edit_room.getText().toString().trim());
        Intent intent = new Intent(this, CallActivity.class);
        intent.setData(uri);
        intent.putExtra(CallActivity.EXTRA_ROOMID, edit_room_id.getText().toString());
        intent.putExtra(CallActivity.EXTRA_LOOPBACK, false);
        intent.putExtra(CallActivity.EXTRA_VIDEO_CALL, true);
        intent.putExtra(CallActivity.EXTRA_SCREENCAPTURE, false);
        intent.putExtra(CallActivity.EXTRA_CAMERA2, true);
        intent.putExtra(CallActivity.EXTRA_VIDEO_WIDTH, 0);
        intent.putExtra(CallActivity.EXTRA_VIDEO_HEIGHT, 0);
        intent.putExtra(CallActivity.EXTRA_VIDEO_FPS, 0);
        intent.putExtra(CallActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED, false);
        intent.putExtra(CallActivity.EXTRA_VIDEO_BITRATE, 0);
        intent.putExtra(CallActivity.EXTRA_VIDEOCODEC, "VP8");
        intent.putExtra(CallActivity.EXTRA_HWCODEC_ENABLED, true);
        intent.putExtra(CallActivity.EXTRA_CAPTURETOTEXTURE_ENABLED, true);
        intent.putExtra(CallActivity.EXTRA_FLEXFEC_ENABLED, false);
        intent.putExtra(CallActivity.EXTRA_NOAUDIOPROCESSING_ENABLED, false);
        intent.putExtra(CallActivity.EXTRA_AECDUMP_ENABLED, false);
        intent.putExtra(CallActivity.EXTRA_OPENSLES_ENABLED, true);
        intent.putExtra(CallActivity.EXTRA_DISABLE_BUILT_IN_AEC, false);
        intent.putExtra(CallActivity.EXTRA_DISABLE_BUILT_IN_AGC, false);
        intent.putExtra(CallActivity.EXTRA_DISABLE_BUILT_IN_NS, false);
        intent.putExtra(CallActivity.EXTRA_ENABLE_LEVEL_CONTROL, false);
        intent.putExtra(CallActivity.EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, false);
        intent.putExtra(CallActivity.EXTRA_AUDIO_BITRATE, 0);
        intent.putExtra(CallActivity.EXTRA_AUDIOCODEC, "OPUS");
        intent.putExtra(CallActivity.EXTRA_DISPLAY_HUD, false);
        intent.putExtra(CallActivity.EXTRA_TRACING, false);
        intent.putExtra(CallActivity.EXTRA_CMDLINE, false);
        intent.putExtra(CallActivity.EXTRA_RUNTIME, 0);
        intent.putExtra(CallActivity.EXTRA_DATA_CHANNEL_ENABLED, true);
        intent.putExtra(CallActivity.EXTRA_ORDERED, true);
        intent.putExtra(CallActivity.EXTRA_MAX_RETRANSMITS_MS, -1);
        intent.putExtra(CallActivity.EXTRA_MAX_RETRANSMITS, -1);
        intent.putExtra(CallActivity.EXTRA_PROTOCOL, "");
        intent.putExtra(CallActivity.EXTRA_NEGOTIATED, false);
        intent.putExtra(CallActivity.EXTRA_ID, -1);
        startActivity(intent);
    }
}
