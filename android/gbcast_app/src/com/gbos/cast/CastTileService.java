package com.gbos.cast;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Quick Settings tile: start or stop casting to the Googlebook.
 *
 * Prefers the ShellCast daemon (gb-touch.jar, no red status-bar timer pill and no notification
 * hiding) via the host's re-arm control channel, falling back to MainActivity only if the shell
 * agent is not yet armed on the phone.
 */
public class CastTileService extends TileService {

    /** Tracks live ShellCast state reported by phone_rearm CAST_STATUS / CAST_START / CAST_STOP. */
    static volatile boolean SHELL_ACTIVE;

    /** Ask Android to re-read both tiles (cast started/stopped, audio link changed, mute toggled). */
    static void refreshTiles(Context c) {
        try {
            TileService.requestListeningState(c, new ComponentName(c, CastTileService.class));
            TileService.requestListeningState(c, new ComponentName(c, SoundTileService.class));
        } catch (Exception ignored) {
        }
    }

    private void updateTileState(boolean active) {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("GBOS Cast");
        t.setSubtitle(active ? "Casting" : "Off");
        t.updateTile();
    }

    @Override
    public void onStartListening() {
        updateTileState(CaptureService.ACTIVE || SHELL_ACTIVE);
        final String host = CaptureService.host(this);
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String rep = MainActivity.askRearm(host, "CAST_STATUS", 1500);
            if (rep.startsWith("SHELL_CAST")) {
                boolean on = rep.contains("enabled=true");
                SHELL_ACTIVE = on;
                main.post(() -> updateTileState(CaptureService.ACTIVE || on));
            }
        }).start();
    }

    @Override
    public void onClick() {
        if (CaptureService.ACTIVE || SHELL_ACTIVE) {
            stopService(new Intent(this, CaptureService.class));
            SHELL_ACTIVE = false;
            updateTileState(false);
            final String host = CaptureService.host(this);
            new Thread(() -> MainActivity.askRearm(host, "CAST_STOP", 3000)).start();
            return;
        }
        stopService(new Intent(this, CaptureService.class));
        final String host = CaptureService.host(this);
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String rep = MainActivity.askRearm(host, "CAST_START", 3500);
            if (rep.startsWith("SHELL_CAST")) {
                SHELL_ACTIVE = true;
                AppSync.pushAsync(CastTileService.this);
                main.post(() -> {
                    updateTileState(true);
                    refreshTiles(CastTileService.this);
                });
                return;
            }
            if ("NO_SHELL_CAST".equals(rep)) {
                main.post(() -> {
                    Intent ui = new Intent(CastTileService.this, MainActivity.class)
                            .putExtra(MainActivity.EXTRA_AUTOSTART, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    PendingIntent pi = PendingIntent.getActivity(CastTileService.this, 2, ui,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                    startActivityAndCollapse(pi);
                });
                return;
            }
            main.post(() -> {
                SHELL_ACTIVE = false;
                Tile t = getQsTile();
                if (t != null) {
                    t.setState(Tile.STATE_INACTIVE);
                    t.setLabel("GBOS Cast");
                    t.setSubtitle("Laptop offline");
                    t.updateTile();
                }
            });
        }).start();
    }
}
