package com.dds.webrtcdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.dds.webrtc.ChatRoomActivity;
import com.dds.webrtc.SingleChatActivity;


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

        edit_room.setText("http://47.254.34.146:8080");
        edit_room_id.setText("12345678");


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
        SingleChatActivity.openActivity(this,
                edit_room.getText().toString().trim(),
                edit_room_id.getText().toString().trim());
    }

    public void room(View view) {
        ChatRoomActivity.openActivity(this,
                edit_room.getText().toString().trim(),
                edit_room_id.getText().toString().trim());
    }
}
