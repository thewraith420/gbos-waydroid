package com.gbos.cast;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.nio.charset.StandardCharsets;

/**
 * Opens one of this phone's notifications exactly as tapping it would: fires its contentIntent (the
 * app's own deep link, e.g. the specific post) and dismisses it if it is auto-cancel.
 *
 * Started by the gesture agent (`am start ... --es keyhex <hex of the StatusBarNotification key>`)
 * when a mirrored notification is tapped on GBOS. It is an activity, not a receiver, because Android
 * blocks background activity starts: a PendingIntent sent by a background app is refused, but one
 * sent by the foreground activity with the BAL opt-in below is allowed. The activity is translucent
 * and finishes immediately. If the notification is gone (dismissed on the phone, or no listener
 * access), it falls back to launching the notification's app.
 */
public class OpenNotifActivity extends Activity {
    private static final String TAG = "GBNotif";
    private boolean done;

    @Override
    protected void onResume() {
        super.onResume();
        if (done) return;
        done = true;
        try {
            open(decodeKey(getIntent().getStringExtra("keyhex")));
        } catch (Throwable e) {
            Log.e(TAG, "open failed", e);
        }
        finish();
        overridePendingTransition(0, 0);
    }

    private static String decodeKey(String hex) {
        if (hex == null || hex.length() == 0 || (hex.length() & 1) != 0) return null;
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return new String(b, StandardCharsets.UTF_8);
    }

    private void open(String key) {
        if (key == null) return;
        // key = "user|pkg|id|tag|uid"
        String[] parts = key.split("\\|", 5);
        String pkg = parts.length > 1 ? parts[1] : null;

        NotifListener l = NotifListener.instance;
        if (l == null) {
            Log.w(TAG, "no notification listener (grant Notification access to GBOS Cast) — launching " + pkg);
        } else {
            StatusBarNotification sbn = null;
            try {
                StatusBarNotification[] found = l.getActiveNotifications(new String[]{key});
                if (found != null && found.length > 0) sbn = found[0];
            } catch (Exception e) {
                Log.w(TAG, "lookup failed: " + e);
            }
            if (sbn != null) {
                Notification n = sbn.getNotification();
                PendingIntent pi = n.contentIntent;
                if (pi != null) {
                    try {
                        ActivityOptions o = ActivityOptions.makeBasic();
                        o.setPendingIntentBackgroundActivityStartMode(
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                        pi.send(this, 0, null, null, null, null, o.toBundle());
                        Log.i(TAG, "fired notification " + key);
                        if ((n.flags & Notification.FLAG_AUTO_CANCEL) != 0) {
                            try { l.cancelNotification(key); } catch (Exception ignored) {}
                        }
                        return;
                    } catch (Exception e) {
                        Log.w(TAG, "contentIntent send failed for " + key + ": " + e);
                    }
                } else {
                    Log.i(TAG, "notification " + key + " has no tap action — launching " + pkg);
                }
            } else {
                Log.i(TAG, "notification " + key + " no longer active — launching " + pkg);
            }
        }
        if (pkg != null) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
            }
        }
    }
}
