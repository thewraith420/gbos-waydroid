package com.gbos.touch;

import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Process;

/**
 * Minimal Context representing com.android.shell (uid 2000) for AudioRecord creation from app_process.
 */
final class ShellContext extends ContextWrapper {
    private static final String SHELL_PKG = "com.android.shell";
    private final AttributionSource attributionSource;

    ShellContext() {
        super(null);
        this.attributionSource = new AttributionSource.Builder(Process.myUid())
                .setPackageName(SHELL_PKG)
                .build();
    }

    @Override
    public String getPackageName() {
        return SHELL_PKG;
    }

    @Override
    public String getOpPackageName() {
        return SHELL_PKG;
    }

    @Override
    public String getAttributionTag() {
        return null;
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    @Override
    public int getDeviceId() {
        return Context.DEVICE_ID_DEFAULT;
    }

    @Override
    public AttributionSource getAttributionSource() {
        return attributionSource;
    }
}
