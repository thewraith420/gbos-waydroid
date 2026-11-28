package com.gbos.touch;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent touch & key injector for the GBOS cast: started once as the adb-shell user
 * (app_process, no root, the same trick scrcpy uses) and kept running, so a finger dragged or a key
 * pressed on GBOS applies in REAL TIME on the phone.
 *
 * Wire format, one line per event, "<token> <verb> [args]":
 *   D <id> <x> <y>                    finger id goes down
 *   M <id> <x> <y>                    finger id moves
 *   U <id> <x> <y>                    finger id goes up
 *   C                                 cancel every finger
 *   K <action> <keyCode> <meta> [rep] inject KeyEvent (action 0=DOWN, 1=UP)
 *   KC                                release every held key (focus lost / disconnect)
 *   ping                              answers "pong v2 key" (capability & RTT probe)
 * Coordinates are phone pixels. Allowed peers: loopback (adb forward), anyone on the same subnet as one of
 * the phone's own addresses (so it follows home/work/any Wi-Fi with no restart; the token is the real
 * credential), and the explicit allowedIp argument if given.
 *
 * Safety: if the laptop vanishes or goes quiet with a finger or key down, it is released — a stuck
 * finger or stuck modifier key on a phone is much worse than a dropped gesture/keystroke.
 *
 *   CLASSPATH=gb-touch.jar app_process /system/bin com.gbos.touch.Main <port> <token> [allowedIp]
 */
public class Main {
    private static Object inputManager;
    private static Method inject;

    private static final int MAX_FINGERS = 10;
    private static final long STUCK_MS = 4000;
    private static final long STUCK_KEY_MS = 10000;

    // Active fingers, in the order they went down (that order IS the MotionEvent pointer index).
    private static final List<float[]> fingers = new ArrayList<float[]>();   // {id, x, y}
    private static long downTime;
    private static long lastInjectTime;
    private static volatile long lastEventAt;

    // Held physical keys mapped keyCode -> {downTimeMs, metaState}.
    private static final Map<Integer, long[]> heldKeys = new HashMap<Integer, long[]>();
    private static volatile long lastKeyAt;

    private static long injected;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) { System.out.println("usage: Main <port> <token> [allowedIp]"); System.exit(1); }
        int port = Integer.parseInt(args[0]);
        String token = args[1];
        String allowed = args.length > 2 ? args[2] : null;

        if (!setup()) { System.out.println("TOUCH_FAIL no injection entry point"); System.exit(3); }

        ServerSocket srv;
        try {
            srv = new ServerSocket(port);
        } catch (Exception e) {
            System.out.println("TOUCH_FAIL bind " + port + ": " + e);
            System.exit(2);
            return;
        }
        System.out.println("TOUCH_READY port=" + port + " uid=" + android.os.Process.myUid());

        Thread guard = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try { Thread.sleep(500); } catch (InterruptedException e) { return; }
                    synchronized (fingers) {
                        if (!fingers.isEmpty() && SystemClock.uptimeMillis() - lastEventAt > STUCK_MS) {
                            System.out.println("stuck finger: no events for " + STUCK_MS + " ms — cancelling");
                            cancelAll();
                        }
                    }
                    synchronized (heldKeys) {
                        if (!heldKeys.isEmpty() && SystemClock.uptimeMillis() - lastKeyAt > STUCK_KEY_MS) {
                            System.out.println("stuck key: no events for " + STUCK_KEY_MS + " ms — cancelling");
                            cancelAllKeys();
                        }
                    }
                }
            }
        }, "gb-touch-guard");
        guard.setDaemon(true);
        guard.start();

        while (true) {
            final Socket s = srv.accept();
            String peer = s.getInetAddress().getHostAddress();
            boolean ok = s.getInetAddress().isLoopbackAddress() || (allowed != null && allowed.equals(peer))
                    || onLocalSubnet(s.getInetAddress());
            if (!ok) { System.out.println("refused " + peer); s.close(); continue; }
            System.out.println("client " + peer);
            // One client at a time is the contract; a new one replaces the old.
            new Thread(new Runnable() { public void run() { serve(s, token); } }, "gb-touch-client").start();
        }
    }

    /** True when peer shares a subnet with any address this phone currently has (re-read per connection). */
    private static boolean onLocalSubnet(InetAddress peer) {
        byte[] p = peer.getAddress();
        try {
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();) {
                NetworkInterface ni = e.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    byte[] a = ia.getAddress().getAddress();
                    int bits = ia.getNetworkPrefixLength();
                    if (a.length != p.length || bits <= 0) continue;
                    boolean same = true;
                    for (int i = 0; i < bits && same; i++) {
                        int m = 0x80 >> (i & 7);
                        if ((a[i >> 3] & m) != (p[i >> 3] & m)) same = false;
                    }
                    if (same) return true;
                }
            }
        } catch (Throwable t) { System.out.println("subnet check failed: " + t); }
        return false;
    }

    private static boolean setup() {
        String[][] candidates = {
            {"android.hardware.input.InputManagerGlobal"},   // Android 14+
            {"android.hardware.input.InputManager"},         // older
        };
        for (String[] c : candidates) {
            try {
                Class<?> k = Class.forName(c[0]);
                Object inst = k.getMethod("getInstance").invoke(null);
                Method m = k.getMethod("injectInputEvent", InputEvent.class, int.class);
                inputManager = inst; inject = m;
                System.out.println("injecting via " + c[0]);
                return true;
            } catch (Throwable t) {
                System.out.println("no " + c[0] + ": " + t);
            }
        }
        return false;
    }

    private static void serve(Socket s, String token) {
        try {
            s.setTcpNoDelay(true);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            OutputStream out = s.getOutputStream();
            String line;
            while ((line = in.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 2 || !token.equals(p[0])) continue;
                String v = p[1];
                try {
                    if (v.equals("ping")) { out.write("pong v6 key fg close scroll\n".getBytes()); out.flush(); }
                    else if (v.equals("fg")) { out.write(("fg " + queryForegroundPackage() + "\n").getBytes()); out.flush(); }
                    else if (v.equals("close") && p.length >= 3) { closePackage(p[2]); out.write("ok\n".getBytes()); out.flush(); }
                    else if (v.equals("D") && p.length >= 5) down(Integer.parseInt(p[2]), f(p[3]), f(p[4]));
                    else if (v.equals("M") && p.length >= 5) move(Integer.parseInt(p[2]), f(p[3]), f(p[4]));
                    else if (v.equals("U") && p.length >= 5) up(Integer.parseInt(p[2]), f(p[3]), f(p[4]));
                    else if (v.equals("S") && p.length >= 6) injectScroll(f(p[2]), f(p[3]), f(p[4]), f(p[5]));
                    else if (v.equals("C")) synchronized (fingers) { cancelAll(); }
                    else if (v.equals("K") && p.length >= 5) injectKey(Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), p.length >= 6 ? Integer.parseInt(p[5]) : 0);
                    else if (v.equals("KC")) synchronized (heldKeys) { cancelAllKeys(); }
                } catch (Throwable t) {
                    System.out.println("bad line '" + line + "': " + t);
                }
            }
        } catch (Throwable t) {
            System.out.println("client error: " + t);
        } finally {
            // The laptop went away mid-gesture or mid-key: never leave a finger or modifier down.
            synchronized (fingers) { cancelAll(); }
            synchronized (heldKeys) { cancelAllKeys(); }
            try { s.close(); } catch (Throwable ignored) {}
            System.out.println("client gone");
        }
    }

    private static void injectKey(int action, int keyCode, int metaState, int repeat) {
        if ((action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) || keyCode <= 0) return;
        synchronized (heldKeys) {
            long now = SystemClock.uptimeMillis();
            lastKeyAt = now;
            long down;
            if (action == KeyEvent.ACTION_DOWN) {
                if (repeat == 0 || !heldKeys.containsKey(keyCode)) {
                    down = now;
                    heldKeys.put(keyCode, new long[]{now, metaState});
                } else {
                    long[] info = heldKeys.get(keyCode);
                    down = info[0];
                    info[1] = metaState;
                }
            } else {
                long[] info = heldKeys.remove(keyCode);
                down = (info != null) ? info[0] : now;
            }
            KeyEvent ev = new KeyEvent(down, now, action, keyCode, repeat, metaState,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
            try {
                inject.invoke(inputManager, ev, 0 /* INJECT_INPUT_EVENT_MODE_ASYNC */);
                injected++;
            } catch (Throwable t) {
                System.out.println("key inject failed: " + t);
            }
        }
    }

    /** Caller holds the heldKeys lock. */
    private static void cancelAllKeys() {
        if (heldKeys.isEmpty()) return;
        long now = SystemClock.uptimeMillis();
        for (Map.Entry<Integer, long[]> e : heldKeys.entrySet()) {
            long[] info = e.getValue();
            KeyEvent ev = new KeyEvent(info[0], now, KeyEvent.ACTION_UP, e.getKey(), 0, (int) info[1],
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_CANCELED, InputDevice.SOURCE_KEYBOARD);
            try {
                inject.invoke(inputManager, ev, 0 /* INJECT_INPUT_EVENT_MODE_ASYNC */);
                injected++;
            } catch (Throwable t) {
                System.out.println("key cancel failed: " + t);
            }
        }
        heldKeys.clear();
    }

    private static float f(String s) { return Float.parseFloat(s); }

    private static int indexOf(int id) {
        for (int i = 0; i < fingers.size(); i++) if ((int) fingers.get(i)[0] == id) return i;
        return -1;
    }

    /** Caller holds the fingers lock. Never mutates downTime mid-gesture; keeps eventTime monotonic. */
    private static long resolveEventTime(boolean isDown) {
        long now = SystemClock.uptimeMillis();
        if (isDown) {
            downTime = now;
            lastInjectTime = now;
            return now;
        }
        long t = Math.max(lastInjectTime, now);
        lastInjectTime = t;
        return t;
    }

    private static void down(int id, float x, float y) {
        synchronized (fingers) {
            lastEventAt = SystemClock.uptimeMillis();
            if (indexOf(id) >= 0) { move(id, x, y); return; }
            if (fingers.size() >= MAX_FINGERS) return;
            fingers.add(new float[]{id, x, y});
            int idx = fingers.size() - 1;
            int action;
            long evT;
            if (idx == 0) {
                evT = resolveEventTime(true);
                action = MotionEvent.ACTION_DOWN;
            } else {
                evT = resolveEventTime(false);
                action = MotionEvent.ACTION_POINTER_DOWN | (idx << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            }
            send(action, evT);
        }
    }

    private static void move(int id, float x, float y) {
        synchronized (fingers) {
            lastEventAt = SystemClock.uptimeMillis();
            int i = indexOf(id);
            if (i < 0) return;
            float[] fg = fingers.get(i);
            if (fg[1] == x && fg[2] == y) return;
            fg[1] = x; fg[2] = y;
            send(MotionEvent.ACTION_MOVE, resolveEventTime(false));
        }
    }

    private static void up(int id, float x, float y) {
        synchronized (fingers) {
            long now = SystemClock.uptimeMillis();
            lastEventAt = now;
            int i = indexOf(id);
            if (i < 0) return;
            float[] fg = fingers.get(i);
            boolean moved = (fg[1] != x || fg[2] != y);
            if (moved && now > lastInjectTime) {
                fg[1] = x; fg[2] = y;
                lastInjectTime = now;
                send(MotionEvent.ACTION_MOVE, now);
            }
            fg[1] = x; fg[2] = y;
            long evT = resolveEventTime(false);
            int action = (fingers.size() == 1)
                ? MotionEvent.ACTION_UP
                : (MotionEvent.ACTION_POINTER_UP | (i << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
            send(action, evT);
            fingers.remove(i);
        }
    }

    /** Caller holds the fingers lock. */
    private static void cancelAll() {
        if (fingers.isEmpty()) return;
        send(MotionEvent.ACTION_CANCEL, resolveEventTime(false));
        fingers.clear();
    }

    private static void injectScroll(float x, float y, float vs, float hs) {
        long now = SystemClock.uptimeMillis();
        PointerProperties[] props = new PointerProperties[1];
        PointerProperties pp = new PointerProperties();
        pp.id = 0;
        pp.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        props[0] = pp;
        PointerCoords[] coords = new PointerCoords[1];
        PointerCoords pc = new PointerCoords();
        pc.x = x;
        pc.y = y;
        pc.pressure = 0f;
        pc.size = 0f;
        pc.setAxisValue(MotionEvent.AXIS_VSCROLL, vs);
        pc.setAxisValue(MotionEvent.AXIS_HSCROLL, hs);
        coords[0] = pc;
        MotionEvent ev = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, props, coords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        try {
            inject.invoke(inputManager, ev, 0 /* INJECT_INPUT_EVENT_MODE_ASYNC */);
            injected++;
        } catch (Throwable t) {
            System.out.println("scroll inject failed: " + t);
        } finally {
            ev.recycle();
        }
    }

    /** Caller holds the fingers lock. Builds one MotionEvent holding every active finger. */
    private static void send(int action, long eventTime) {
        int n = fingers.size();
        PointerProperties[] props = new PointerProperties[n];
        PointerCoords[] coords = new PointerCoords[n];
        for (int i = 0; i < n; i++) {
            float[] fg = fingers.get(i);
            PointerProperties pp = new PointerProperties();
            pp.id = (int) fg[0];
            pp.toolType = MotionEvent.TOOL_TYPE_FINGER;
            props[i] = pp;
            PointerCoords pc = new PointerCoords();
            pc.x = fg[1]; pc.y = fg[2];
            pc.pressure = 1f; pc.size = 1f;
            coords[i] = pc;
        }
        MotionEvent ev = MotionEvent.obtain(downTime, eventTime, action, n, props, coords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try {
            inject.invoke(inputManager, ev, 0 /* INJECT_INPUT_EVENT_MODE_ASYNC */);
            injected++;
        } catch (Throwable t) {
            System.out.println("inject failed: " + t);
        } finally {
            ev.recycle();
        }
    }

    /**
     * Return the current foreground package name on the phone, or "none" if the screen is off,
     * the keyguard is locked, or no app activity is focused. Uses binder reflection directly
     * (no subprocess fork) so 2 s polling has negligible CPU/battery cost.
     */
    private static String queryForegroundPackage() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Class<?> ib = Class.forName("android.os.IBinder");
            Method getSvc = sm.getMethod("getService", String.class);

            // Screen off -> none
            try {
                Object pb = getSvc.invoke(null, "power");
                if (pb != null) {
                    Object pm = Class.forName("android.os.IPowerManager$Stub")
                            .getMethod("asInterface", ib).invoke(null, pb);
                    if (!(Boolean) pm.getClass().getMethod("isInteractive").invoke(pm)) {
                        return "none";
                    }
                }
            } catch (Throwable ignored) {}

            // Keyguard locked -> none
            try {
                Object wb = getSvc.invoke(null, "window");
                if (wb != null) {
                    Object wm = Class.forName("android.view.IWindowManager$Stub")
                            .getMethod("asInterface", ib).invoke(null, wb);
                    if ((Boolean) wm.getClass().getMethod("isKeyguardLocked").invoke(wm)) {
                        return "none";
                    }
                }
            } catch (Throwable ignored) {}

            // Focused root task -> topActivity packageName
            Object ab = getSvc.invoke(null, "activity_task");
            if (ab != null) {
                Object atm = Class.forName("android.app.IActivityTaskManager$Stub")
                        .getMethod("asInterface", ib).invoke(null, ab);
                Object info = atm.getClass().getMethod("getFocusedRootTaskInfo").invoke(atm);
                if (info != null) {
                    Object top = info.getClass().getField("topActivity").get(info);
                    if (top != null) {
                        String pkg = (String) top.getClass().getMethod("getPackageName").invoke(top);
                        if (pkg != null && !pkg.isEmpty()) return pkg;
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("fg query failed: " + t);
        }
        return "none";
    }

    /**
     * Close a foreground app on the phone when its App Cast window is closed:
     * send KEYCODE_HOME first (smooth return to home screen), then force-stop the package
     * via IActivityManager (skipping system UI / launchers / com.gbos.cast).
     */
    private static void closePackage(String pkg) {
        if (pkg == null || !pkg.matches("[A-Za-z0-9._-]{1,256}")) return;
        if (pkg.equals("android") || pkg.equals("com.android.systemui")
                || pkg.equals("com.gbos.cast")
                || pkg.equals("com.google.android.apps.nexuslauncher")
                || pkg.equals("com.android.launcher3")) {
            return;
        }
        try {
            injectKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME, 0, 0);
            injectKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, 0, 0);
        } catch (Throwable ignored) {}
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Class<?> ib = Class.forName("android.os.IBinder");
            Object ab = sm.getMethod("getService", String.class).invoke(null, "activity");
            if (ab != null) {
                Object am = Class.forName("android.app.IActivityManager$Stub")
                        .getMethod("asInterface", ib).invoke(null, ab);
                // userId = -2 (UserHandle.USER_CURRENT)
                am.getClass().getMethod("forceStopPackage", String.class, int.class)
                        .invoke(am, pkg, -2);
                System.out.println("closed package " + pkg);
            }
        } catch (Throwable t) {
            System.out.println("closePackage failed for " + pkg + ": " + t);
        }
    }
}
