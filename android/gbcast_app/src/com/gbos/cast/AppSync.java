package com.gbos.cast;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pushes this phone's launcher apps (package, real label, rendered icon) to the laptop so GBOS's
 * Phone Hub app drawer can show the phone's actual apps with their actual icons instead of the
 * hand-typed list and grey placeholders.
 *
 * Same direction as the video push: the phone dials the laptop, so nothing here needs the phone's
 * adb. Wire format (host side: host_relay.py serve_apps):
 *
 *   "GBAPPS1 <token>\n"
 *   repeated:  int32 pkgLen, pkg utf-8, int32 labelLen, label utf-8, int32 pngLen, png bytes
 *   int32 0                      (end)
 *   host answers "OK <n>\n"
 *
 * One-shot per cast start, retried until the laptop's relay is up (the cast can start before it).
 */
final class AppSync {
    private static final String TAG = "GBAppSync";
    static final int PORT = 8094;
    /** Final icon edge in px. Small on purpose: the whole set is embedded in the GBOS-side script. */
    private static final int ICON_PX = 96;
    private static final int TRIES = 12;
    private static final long RETRY_MS = 10_000;

    private static volatile boolean running;

    private AppSync() {}

    static void pushAsync(Context c) {
        if (running) return;
        running = true;
        final Context app = c.getApplicationContext();
        Thread t = new Thread(() -> {
            try {
                List<Entry> apps = collect(app);
                Log.i(TAG, "collected " + apps.size() + " launcher apps");
                for (int i = 1; i <= TRIES; i++) {
                    if (push(app, apps)) return;
                    try {
                        Thread.sleep(RETRY_MS);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                Log.w(TAG, "gave up after " + TRIES + " tries — is the laptop relay running?");
            } catch (Throwable e) {
                Log.e(TAG, "app sync failed", e);
            } finally {
                running = false;
            }
        }, "gbos-appsync");
        t.start();
    }

    private static final class Entry {
        final String pkg, label;
        final byte[] png;
        Entry(String pkg, String label, byte[] png) { this.pkg = pkg; this.label = label; this.png = png; }
    }

    private static List<Entry> collect(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        Set<String> seen = new HashSet<>();
        List<Entry> out = new ArrayList<>();
        for (ResolveInfo ri : ris) {
            String pkg = ri.activityInfo.packageName;
            if (!seen.add(pkg)) continue;               // one tile per app, first launcher activity wins
            try {
                CharSequence l = ri.loadLabel(pm);
                String label = (l == null || l.length() == 0) ? pkg : l.toString();
                byte[] png = render(ri.loadIcon(pm));
                if (png != null) out.add(new Entry(pkg, label, png));
            } catch (Throwable e) {
                Log.w(TAG, "skipping " + pkg + ": " + e);
            }
        }
        Collections.sort(out, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) { return a.label.compareToIgnoreCase(b.label); }
        });
        return out;
    }

    /**
     * Render to a square PNG. An adaptive icon draws its full 108dp canvas and the visible,
     * mask-shaped part is the centre 72/108, so draw big and crop that centre; a plain icon just
     * scales to fit.
     */
    private static byte[] render(Drawable d) {
        if (d == null) return null;
        Bitmap result;
        if (d instanceof AdaptiveIconDrawable) {
            int full = ICON_PX * 108 / 72;               // 144 for 96
            Bitmap big = Bitmap.createBitmap(full, full, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, full, full);
            d.draw(new Canvas(big));
            int off = (full - ICON_PX) / 2;
            result = Bitmap.createBitmap(big, off, off, ICON_PX, ICON_PX);
            if (result != big) big.recycle();
        } else {
            result = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, ICON_PX, ICON_PX);
            d.draw(new Canvas(result));
        }
        ByteArrayOutputStream bo = new ByteArrayOutputStream(8192);
        result.compress(Bitmap.CompressFormat.PNG, 100, bo);
        result.recycle();
        return bo.toByteArray();
    }

    private static boolean push(Context c, List<Entry> apps) {
        String host = CaptureService.host(c);
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, PORT), 3000);
            s.setSoTimeout(15000);
            DataOutputStream o = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), 65536));
            o.write(("GBAPPS1 " + CaptureService.INPUT_TOKEN + "\n").getBytes(StandardCharsets.UTF_8));
            for (Entry e : apps) {
                byte[] p = e.pkg.getBytes(StandardCharsets.UTF_8);
                byte[] l = e.label.getBytes(StandardCharsets.UTF_8);
                o.writeInt(p.length); o.write(p);
                o.writeInt(l.length); o.write(l);
                o.writeInt(e.png.length); o.write(e.png);
            }
            o.writeInt(0);
            o.flush();
            InputStream in = s.getInputStream();
            byte[] buf = new byte[32];
            int n = in.read(buf);
            String ack = n > 0 ? new String(buf, 0, n, StandardCharsets.UTF_8).trim() : "";
            Log.i(TAG, "pushed " + apps.size() + " apps to " + host + ":" + PORT + " -> " + ack);
            return ack.startsWith("OK");
        } catch (Exception e) {
            Log.w(TAG, "push to " + host + ":" + PORT + " failed: " + e);
            return false;
        }
    }
}
