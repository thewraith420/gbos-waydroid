package com.gbos.cast;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Brings the cast back by itself after a phone reboot.
 *
 * The capture pipeline lives in a foreground service, but MediaProjection can only be obtained from
 * an Activity result, so this cannot simply start the service: it starts MainActivity with the
 * auto-start flag, and that activity runs the normal createScreenCaptureIntent() path and then
 * pushes itself to the back.
 *
 * Two things have to be true for this to be gapless, and both are set once over adb (see
 * APPCAST_HANDOFF.md §6.8):
 *
 *   appops set com.gbos.cast PROJECT_MEDIA allow        -> the consent dialog is auto-accepted
 *   appops set com.gbos.cast SYSTEM_ALERT_WINDOW allow  -> Android 10+ lets us start an activity
 *                                                          from a boot broadcast
 *
 * Without the second, the activity start is silently dropped by the background-activity-start
 * restriction (`ActivityTaskManager: Abort background activity starts`). Without the first, the
 * consent dialog appears at boot and needs one tap — still useful, just not hands-free.
 *
 * Because that overlay grant cannot be made over adb on this phone (`cmd appops set …
 * SYSTEM_ALERT_WINDOW allow` leaves it at `default`), the receiver also posts a notification. If
 * Android does block the start — measured here — the notification is the fallback, and it needs
 * exactly one tap instead of "open the app, tap Start casting, dismiss the consent dialog". If the
 * overlay permission is granted in Settings, the notification is cancelled the moment capture
 * starts and the whole thing really is hands-free.
 *
 * "com.gbos.cast.TEST_BOOT" exists so this path can be exercised over adb without rebooting:
 *   adb shell am broadcast -a com.gbos.cast.TEST_BOOT -n com.gbos.cast/.BootReceiver
 */
public class BootReceiver extends BroadcastReceiver {
    static final String ACTION_TEST = "com.gbos.cast.TEST_BOOT";

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !ACTION_TEST.equals(action)) return;

        SharedPreferences p = c.getSharedPreferences("gbos", Context.MODE_PRIVATE);
        if (!p.getBoolean("autoStart", true)) {
            Log.i(CaptureService.TAG, "boot receiver: auto-start is off, doing nothing");
            return;
        }

        // Always arm the one-tap fallback first: if the activity start is blocked, this is the only
        // thing that tells the user the cast is waiting for them.
        notifyTapToResume(c);

        Intent ui = new Intent(c, MainActivity.class);
        ui.putExtra(MainActivity.EXTRA_AUTOSTART, true);
        ui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            c.startActivity(ui);
            Log.i(CaptureService.TAG, "boot receiver: starting capture via MainActivity");
        } catch (Exception e) {
            // Background activity starts are restricted on Android 10+; without SYSTEM_ALERT_WINDOW
            // this throws and the phone just stays idle until the app is opened.
            Log.w(CaptureService.TAG, "boot receiver: could not start the activity: " + e);
        }
    }

    /**
     * "Tap to resume casting"—the fallback when Android refuses the background activity start.
     * MainActivity cancels it as soon as capture is running, so it only lingers when it is needed.
     */
    static final String CHANNEL = "gbos_cast_boot";
    static final int NOTIF_ID = 42;

    private void notifyTapToResume(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Cast after reboot",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Resume casting to the Googlebook");
            nm.createNotificationChannel(ch);
        }
        Intent ui = new Intent(c, MainActivity.class);
        ui.putExtra(MainActivity.EXTRA_AUTOSTART, true);
        ui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, ui,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setContentTitle("Casting is off")
                .setContentText("Tap to resume casting to the Googlebook")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIF_ID, n);
    }
}
