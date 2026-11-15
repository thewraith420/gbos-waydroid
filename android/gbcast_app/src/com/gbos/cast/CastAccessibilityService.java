package com.gbos.cast;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Injects taps and swipes into whatever is on screen, without root and without adb.
 *
 * This is the only sanctioned way for a normal app to drive another app's UI: `input` lives in
 * the shell uid, and MediaProjection gives us pixels but no way to touch back. `dispatchGesture`
 * needs the user to switch this service on once (Settings → Accessibility), which is a far better
 * trade than leaving wireless debugging enabled forever.
 */
public class CastAccessibilityService extends AccessibilityService {
    static final String TAG = "GBOSCast";

    private static volatile CastAccessibilityService instance;

    /** Null until the user has enabled the service in Accessibility settings. */
    static CastAccessibilityService get() {
        return instance;
    }

    static boolean ready() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "accessibility connected — gesture injection available");
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Nothing to observe; this service exists only to perform gestures.
    }

    @Override
    public void onInterrupt() {
    }

    boolean tap(float x, float y) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 60))
                .build();
        return dispatchGesture(g, null, null);
    }

    boolean swipe(float x1, float y1, float x2, float y2, long durationMs) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(durationMs, 1)))
                .build();
        return dispatchGesture(g, null, null);
    }

    boolean global(int action) {
        return performGlobalAction(action);
    }
}
