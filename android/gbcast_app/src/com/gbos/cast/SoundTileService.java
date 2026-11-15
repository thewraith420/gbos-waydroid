package com.gbos.cast;

import android.content.Intent;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Quick Settings tile: "mute this phone while casting" (the same setting as the app's checkbox and the
 * notification button). ON = while a cast window is open the sound plays on the Googlebook only; OFF = it
 * plays on both. Applied at once to a running cast.
 */
public class SoundTileService extends TileService {

    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean mute = getSharedPreferences("gbos", MODE_PRIVATE).getBoolean("muteLocal", true);
        t.setState(mute ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Cast sound");
        t.setSubtitle(mute ? "Googlebook only" : "Phone too");
        t.updateTile();
    }

    @Override
    public void onClick() {
        SharedPreferences sp = getSharedPreferences("gbos", MODE_PRIVATE);
        sp.edit().putBoolean("muteLocal", !sp.getBoolean("muteLocal", true)).apply();
        if (CaptureService.ACTIVE) {
            startService(new Intent(this, CaptureService.class).setAction(CaptureService.ACTION_APPLY_MUTE));
        }
        onStartListening();
    }
}
