package com.example.bts_app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Pair;

import com.orhanobut.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class BandwidthTest {

    Context context;

    final static private int ConnectTimeout = 8000;                    // connect timeout
    final static private int TestTimeout = 10000;               // fixed test duration
    final static private int SamplingWindow = 100;               // Sampling overlap


    public String bandwidth_Mbps = "0";
    public String duration_s = "0";
    public String traffic_MB = "0";
    public String networkType;

    BandwidthTest(Context context) {
        this.context = context;
    }

    static class DownloadThread extends Thread {
        URL url;
        long size; // byte

        DownloadThread(String url) {
            try {
                this.url = new URL(url);
            } catch (MalformedURLException e) {
//                e.printStackTrace();
            }
        }

        public void run() {
            try {
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(ConnectTimeout);
                conn.setReadTimeout(ConnectTimeout);
                if (conn.getResponseCode() == 200) {
                    InputStream inStream = conn.getInputStream();
                    byte[] buffer = new byte[10240]; // download 10k every loop
                    long byteRead = 0;
                    while ((byteRead = inStream.read(buffer)) != -1) {
                        size += byteRead;
                        if (Thread.interrupted()) {
                            inStream.close();
                            conn.disconnect();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
//                e.printStackTrace();
            }
        }
    }

    private Double calcSpeed(ArrayList<Double> speedSamples) {
        double avg = 0;
        for (double sample : speedSamples) {
            avg += sample;
        }
        avg /= 20;
        ArrayList<Double> interval = new ArrayList<>();
        double sum = 0;
        int num = 0;
        for (double sample : speedSamples) {
            sum += sample;
            num ++;
            if (sum>=avg) {
                interval.add(sum/num);
                sum = 0;
                num = 0;
            }
        }
        interval.add(sum/num);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            interval.sort(Comparator.naturalOrder());
        }
        avg = 0;
        int tot = 0;
        for (double sample : speedSamples.subList(5,18)) {
            avg += sample;
            tot ++;
        }
        avg /= tot;
        return avg;
    }

    public void SpeedTest() throws InterruptedException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.d("No permission:", "ACCESS_FINE_LOCATION");
                return;
            }
        }

        networkType = getNetworkType();

        ArrayList<String> ipList = new ArrayList<>();
        ipList.add("49.233.50.165");
        ipList.add("49.232.129.114");
        ipList.add("1.116.117.183");
        ipList.add("1.15.30.244");

        ArrayList<DownloadThread> downloadThread = new ArrayList<>();
        for (String ip : ipList) {
            downloadThread.add(new DownloadThread("http://" + ip + "/datafile?" + Math.floor(Math.random() * 100000)));
        }

        for (DownloadThread t : downloadThread) {
            t.start();
        }

        ArrayList<Double> speedSample = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        ArrayList<Double> sizeRecord = new ArrayList<>();
        ArrayList<Long> timeRecord = new ArrayList<>();
        int posRecord = -1;
        while (true) {
            Thread.sleep(SamplingWindow);

            long downloadSize = 0;
            for (DownloadThread t : downloadThread)
                downloadSize += t.size;
            double downloadSizeMBits = (double) (downloadSize) / 1024 / 1024 * 8;
            long nowTime = System.currentTimeMillis();

            sizeRecord.add(downloadSizeMBits);
            timeRecord.add(nowTime);

            if (timeRecord.size() >= 2) {
                speedSample.add((downloadSizeMBits - sizeRecord.get(posRecord)) * 1000.0 / (nowTime - timeRecord.get(posRecord)));
            }
            posRecord++;

            if (nowTime - startTime >= TestTimeout) {
                Log.d("Bandwidth Test", "Test succeed.");
                break;
            }
        }


        bandwidth_Mbps = String.format(Locale.CHINA, "%.2f", calcSpeed(speedSample));
        duration_s = String.format(Locale.CHINA, "%.1f", (double) (System.currentTimeMillis() - startTime) / 1000);
        traffic_MB = String.format(Locale.CHINA, "%.2f", sizeRecord.get(sizeRecord.size() - 1) / 8);

        Log.d("bandwidth_Mbps", bandwidth_Mbps);
        Log.d("duration_s", duration_s);
        Log.d("traffic_MB", traffic_MB);
        Logger.d(speedSample);
    }

    String getNetworkType() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isAvailable())
            return "NONE";
        int connectionType = networkInfo.getType();
        if (connectionType == ConnectivityManager.TYPE_WIFI)
            return "WiFi";
        if (connectionType == ConnectivityManager.TYPE_MOBILE) {
            int cellType = networkInfo.getSubtype();
            switch (cellType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:     // api< 8: replace by 11
                case TelephonyManager.NETWORK_TYPE_GSM:      // api<25: replace by 16
                    return "2G";
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:   // api< 9: replace by 12
                case TelephonyManager.NETWORK_TYPE_EHRPD:    // api<11: replace by 14
                case TelephonyManager.NETWORK_TYPE_HSPAP:    // api<13: replace by 15
                case TelephonyManager.NETWORK_TYPE_TD_SCDMA: // api<25: replace by 17
                    return "3G";
                case TelephonyManager.NETWORK_TYPE_LTE:      // api<11: replace by 13
                case TelephonyManager.NETWORK_TYPE_IWLAN:    // api<25: replace by 18
                case 19: // LTE_CA
                    return "4G";
                case TelephonyManager.NETWORK_TYPE_NR:       // api<29: replace by 20
                    return "5G";
                default:
                    return "unknown";
            }
        }
        return "unknown";
    }
}