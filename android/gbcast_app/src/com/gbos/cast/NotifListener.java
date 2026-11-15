package com.gbos.cast;

import android.service.notification.NotificationListenerService;
import android.util.Log;

/**
 * Lets GBOS Cast look up this phone's live notifications by key, so a notification tapped on GBOS
 * can fire the phone notification's own tap action (see OpenNotifActivity). It does nothing on its
 * own; the system binds it once Notification access is granted, either in Settings or with
 * `cmd notification allow_listener com.gbos.cast/com.gbos.cast.NotifListener`.
 */
public class NotifListener extends NotificationListenerService {
    private static final String TAG = "GBNotif";
    static volatile NotifListener instance;

    @Override
    public void onListenerConnected() {
        instance = this;
        Log.i(TAG, "notification listener connected");
    }

    @Override
    public void onListenerDisconnected() {
        if (instance == this) instance = null;
        Log.i(TAG, "notification listener disconnected");
    }
}
