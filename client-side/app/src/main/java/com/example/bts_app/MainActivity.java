package com.example.bts_app;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {
    BandwidthTest bandwidthTest = new BandwidthTest(this);
    boolean isTesting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

        Logger.addLogAdapter(new AndroidLogAdapter());

        TextView bandwidth_text = findViewById(R.id.bandwidth);
        TextView duration_text = findViewById(R.id.duration);
        TextView traffic_text = findViewById(R.id.traffic);
        Button button = findViewById(R.id.start);
        button.setOnClickListener(view -> {
            if (!isTesting) {
                isTesting = true;
                button.setText(R.string.stop);
                bandwidth_text.setText(R.string.testing);
                duration_text.setText(R.string.testing);
                traffic_text.setText(R.string.testing);
                new Thread(() -> {
                    String bandwidth = "0";
                    String duration = "0";
                    String traffic = "0";
                    String network = "";
                    try {
                        bandwidthTest.SpeedTest();
                        bandwidth = bandwidthTest.bandwidth_Mbps;
                        duration = bandwidthTest.duration_s;
                        traffic = bandwidthTest.traffic_MB;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    String finalBandwidth = bandwidth + "  Mbps";
                    String finalDuration = duration + "  s";
                    String finalTraffic = traffic + "  MB";
                    String finalNetwork = network;
                    runOnUiThread(() -> {
                        isTesting = false;
                        button.setText(R.string.start);
                        bandwidth_text.setText(finalBandwidth);
                        duration_text.setText(finalDuration);
                        traffic_text.setText(finalTraffic);
                    });
                }).start();
            }
        });
    }
}