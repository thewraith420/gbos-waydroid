package com.gbos.cast;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Quick Settings tile: start or stop casting to the Googlebook.
 *
 * Starting goes through MainActivity's autostart path (the same one the boot receiver uses), because only an
 * activity may ask for the screen-capture consent; with PROJECT_MEDIA allowed that consent is auto-accepted,
 * so a tap is all it takes. Stopping just stops CaptureService, which tears down and restores the volume.
 */
public class CastTileService extends TileService {

    /** Ask Android to re-read both tiles (cast started/stopped, audio link changed, mute toggled). */
    static void refreshTiles(Context c) {
        try {
            TileService.requestListeningState(c, new ComponentName(c, CastTileService.class));
            TileService.requestListeningState(c, new ComponentName(c, SoundTileService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = CaptureService.ACTIVE;
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("GBOS Cast");
        t.setSubtitle(on ? "Casting" : "Off");
        t.updateTile();
    }

    @Override
    public void onClick() {
        if (CaptureService.ACTIVE) {
            stopService(new Intent(this, CaptureService.class));
            Tile t = getQsTile();
            if (t != null) {
                t.setState(Tile.STATE_INACTIVE);
                t.setSubtitle("Off");
                t.updateTile();
            }
            return;
        }
        Intent ui = new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_AUTOSTART, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 2, ui,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        startActivityAndCollapse(pi);
    }
}
