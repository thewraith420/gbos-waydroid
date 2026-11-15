package com.gbos.hub;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private TextView statusCds;
    private TextView statusPixel;
    private TextView statusAppcast;
    private Button btnReconnect;
    private Button btnLaunch;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusCds = findViewById(R.id.status_cds);
        statusPixel = findViewById(R.id.status_pixel);
        statusAppcast = findViewById(R.id.status_appcast);
        btnReconnect = findViewById(R.id.btn_reconnect);
        btnLaunch = findViewById(R.id.btn_launch);

        btnReconnect.setOnClickListener(v -> {
            btnReconnect.setEnabled(false);
            btnReconnect.setText("Reconnecting …");
            new Thread(() -> {
                HubWidgetProvider.sendDaemonCommand("RECONNECT");
                try { Thread.sleep(4000); } catch (InterruptedException ignored) {}
                fetchStatus();
                handler.post(() -> {
                    btnReconnect.setEnabled(true);
                    btnReconnect.setText("Reconnect & Fix Now");
                });
            }).start();
        });

        btnLaunch.setOnClickListener(v -> {
            new Thread(() -> HubWidgetProvider.sendDaemonCommand("LAUNCH_APPCAST")).start();
            Intent launchIntent = new Intent();
            launchIntent.setComponent(new ComponentName(
                    "com.google.ambient.streaming",
                    "com.google.ambient.streaming.multidevicesync.appcast.receiver.launcher.RemoteAppsLauncherActivity"
            ));
            try {
                startActivity(launchIntent);
            } catch (Exception ignored) {}
            finish();
        });

        Button btnPin = findViewById(R.id.btn_pin_widget);
        AppWidgetManager awm = getSystemService(AppWidgetManager.class);
        if (awm != null && awm.isRequestPinAppWidgetSupported()) {
            btnPin.setVisibility(View.VISIBLE);
            btnPin.setOnClickListener(v -> {
                ComponentName provider = new ComponentName(this, HubWidgetProvider.class);
                awm.requestPinAppWidget(provider, null, null);
            });
        } else {
            btnPin.setVisibility(View.GONE);
        }

        fetchStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchStatus();
    }

    private void fetchStatus() {
        new Thread(() -> {
            String reply = HubWidgetProvider.sendDaemonCommand("STATUS");
            handler.post(() -> {
                if (reply != null && !reply.isEmpty()) {
                    try {
                        JSONObject json = new JSONObject(reply);
                        String cds = json.optString("cds", "disconnected");
                        String phone = json.optString("phone", "none");
                        String appcast = json.optString("appcast", "inactive");

                        statusCds.setText("Cross-Device Services: " +
                                ("connected".equalsIgnoreCase(cds) ? "✔ CONNECTED" : "✘ DISCONNECTED"));
                        statusPixel.setText("Pixel 7: " + phone);
                        statusAppcast.setText("AppCast Screen: " + appcast.toUpperCase());
                    } catch (Exception e) {
                        statusCds.setText("Error reading status: " + e.getMessage());
                    }
                } else {
                    statusCds.setText("Link daemon unreachable at 192.168.240.1:8092");
                }
            });
        }).start();
    }
}
