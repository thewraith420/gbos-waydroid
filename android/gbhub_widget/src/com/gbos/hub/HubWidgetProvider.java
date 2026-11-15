package com.gbos.hub;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class HubWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_RECONNECT = "com.gbos.hub.ACTION_RECONNECT";
    public static final String ACTION_LAUNCH = "com.gbos.hub.ACTION_LAUNCH";
    private static final String HOST_DAEMON = "192.168.240.1";
    private static final int DAEMON_PORT = 8092;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, id, null);
        }
        refreshStatusAsync(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();

        if (ACTION_RECONNECT.equals(action)) {
            // Immediately set UI to "Reconnecting..."
            RemoteViews views = buildViews(context, "Pixel 7 • Reconnecting …", "Running quick fix & gate kick …", false);
            updateAllWidgets(context, views);

            new Thread(() -> {
                sendDaemonCommand("RECONNECT");
                try { Thread.sleep(4000); } catch (InterruptedException ignored) {}
                refreshStatus(context);
            }).start();

        } else if (ACTION_LAUNCH.equals(action)) {
            // Open = a cast window on the phone's home screen. (The app drawer has its own button.)
            new Thread(() -> sendDaemonCommand("OPEN_PHONE_HOME")).start();
        }
    }

    private static void refreshStatusAsync(Context context) {
        new Thread(() -> refreshStatus(context)).start();
    }

    private static void refreshStatus(Context context) {
        String reply = sendDaemonCommand("STATUS");
        String title = "Pixel 7 • Disconnected";
        String sub = "Tap Reconnect to fix";
        boolean connected = false;

        if (reply != null && !reply.isEmpty()) {
            try {
                JSONObject json = new JSONObject(reply);
                String cds = json.optString("cds", "disconnected");
                String phone = json.optString("phone", "none");
                String appcast = json.optString("appcast", "inactive");

                if ("connected".equalsIgnoreCase(cds)) {
                    connected = true;
                    title = "Pixel 7 • Connected";
                    if ("live".equalsIgnoreCase(appcast)) {
                        sub = "AppCast: Live (" + phone + ")";
                    } else {
                        sub = "AppCast: Ready (" + phone + ")";
                    }
                } else {
                    title = "Pixel 7 • Disconnected";
                    sub = "CDS offline • Tap Reconnect";
                }
            } catch (Exception ignored) {}
        }

        RemoteViews views = buildViews(context, title, sub, connected);
        updateAllWidgets(context, views);
    }

    private static RemoteViews buildViews(Context context, String title, String sub, boolean connected) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        views.setTextViewText(R.id.widget_title, title);
        views.setTextViewText(R.id.widget_sub, sub);

        // Reconnect button PendingIntent
        Intent recIntent = new Intent(context, HubWidgetProvider.class);
        recIntent.setAction(ACTION_RECONNECT);
        PendingIntent piRec = PendingIntent.getBroadcast(
                context, 0, recIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_reconnect, piRec);

        // Open AppCast PendingIntent
        Intent openIntent = new Intent(context, HubWidgetProvider.class);
        openIntent.setAction(ACTION_LAUNCH);
        PendingIntent piOpen = PendingIntent.getBroadcast(
                context, 1, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_open, piOpen);

        // Widget body opens MainActivity
        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent piMain = PendingIntent.getActivity(
                context, 2, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, piMain);

        return views;
    }

    private static void updateAllWidgets(Context context, RemoteViews views) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName cn = new ComponentName(context, HubWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(cn);
        for (int id : ids) {
            manager.updateAppWidget(id, views);
        }
    }

    private static void updateAppWidget(Context context, AppWidgetManager manager, int widgetId, RemoteViews custom) {
        if (custom != null) {
            manager.updateAppWidget(widgetId, custom);
        } else {
            RemoteViews views = buildViews(context, "Pixel 7 • Checking …", "Contacting host link daemon …", false);
            manager.updateAppWidget(widgetId, views);
        }
    }

    public static String sendDaemonCommand(String cmd) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(HOST_DAEMON, DAEMON_PORT), 2500);
            OutputStream out = s.getOutputStream();
            out.write((cmd + "\n").getBytes("utf-8"));
            out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "utf-8"));
            return in.readLine();
        } catch (Exception e) {
            return null;
        }
    }
}

