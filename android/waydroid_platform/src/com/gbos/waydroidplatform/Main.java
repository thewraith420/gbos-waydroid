package com.gbos.waydroidplatform;

import android.app.ActivityManager;
import android.app.ActivityThread;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.system.Os;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standalone `waydroidplatform` (`lineageos.waydroid.IPlatform`) binder service and
 * `waydroidusermonitor` (`lineageos.waydroid.IUserMonitor`) notifier for GBOS.
 *
 * Runs via `app_process` so stock host Waydroid tools (`waydroid app list/launch/install/remove`,
 * `waydroid prop`, `user_manager.py` desktop entry sync, and `vendor.waydroid.task@1.0`) work
 * against GBOS user 10 without patching system_server.
 */
public class Main {
    private static final String TAG = "gb-waydroidplatform";
    private static final String SERVICE_NAME = "waydroidplatform";
    private static final String PLATFORM_IFACE = "lineageos.waydroid.IPlatform";
    private static final String USER_MONITOR_SERVICE = "waydroidusermonitor";
    private static final String USER_MONITOR_IFACE = "lineageos.waydroid.IUserMonitor";

    private static final int TX_GETPROP = 1;
    private static final int TX_SETPROP = 2;
    private static final int TX_GET_APPS_INFO = 3;
    private static final int TX_GET_APP_INFO = 4;
    private static final int TX_INSTALL_APP = 5;
    private static final int TX_REMOVE_APP = 6;
    private static final int TX_LAUNCH_APP = 7;
    private static final int TX_GET_APP_NAME = 8;
    private static final int TX_SETTINGS_PUT_STRING = 9;
    private static final int TX_SETTINGS_GET_STRING = 10;
    private static final int TX_SETTINGS_PUT_INT = 11;
    private static final int TX_SETTINGS_GET_INT = 12;
    private static final int TX_LAUNCH_INTENT = 13;

    private static final int UM_TX_USER_UNLOCKED = 1;
    private static final int UM_TX_PACKAGE_STATE_CHANGED = 2;
    private static final int PACKAGE_ADDED = 0;
    private static final int PACKAGE_REMOVED = 1;

    private static final File ICONS_DIR = new File("/data/icons");
    private static final Pattern PKG_PATTERN = Pattern.compile("[a-zA-Z0-9._]+");
    private static final Pattern USER_LINE_PATTERN = Pattern.compile("UserInfo\\{(\\d+):[^}]+\\}\\s+running");

    private static volatile Context sContext;

    public static void main(String[] args) {
        try {
            Looper.prepareMainLooper();
            sContext = initSystemContext();
            PlatformBinder binder = new PlatformBinder(sContext);
            registerPlatformService(binder);
            Log.i(TAG, "registered service " + SERVICE_NAME + " (" + PLATFORM_IFACE + "), targetUser=" + resolveTargetUser(sContext));
            startUserMonitorLoop(sContext);
            Looper.loop();
        } catch (Throwable t) {
            Log.e(TAG, "fatal startup error", t);
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static Context initSystemContext() throws Exception {
        Method systemMain = ActivityThread.class.getMethod("systemMain");
        ActivityThread at = (ActivityThread) systemMain.invoke(null);
        Context ctx = at.getSystemContext();
        if (ctx == null) {
            throw new IllegalStateException("ActivityThread.systemMain().getSystemContext() returned null");
        }
        return ctx;
    }

    private static void registerPlatformService(IBinder binder) throws Exception {
        try {
            ServiceManager.addService(SERVICE_NAME, binder);
            return;
        } catch (NoSuchMethodError | SecurityException e) {
            throw e;
        } catch (Throwable ignored) {
        }
        Method m = ServiceManager.class.getMethod("addService", String.class, IBinder.class);
        m.invoke(null, SERVICE_NAME, binder);
    }

    private static boolean isPropTrue(String key) {
        String v = SystemProperties.get(key, "");
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    private static int resolveTargetUser(Context ctx) {
        try {
            Method getCurrentUser = ActivityManager.class.getMethod("getCurrentUser");
            Object curObj = getCurrentUser.invoke(null);
            int cur = (curObj instanceof Integer) ? (Integer) curObj : 0;
            if (cur > 0) return cur;
        } catch (Throwable t) {
            Log.w(TAG, "ActivityManager.getCurrentUser failed: " + t);
        }

        // Check UserManager for running non-system user (prefer user 10 for GBOS).
        try {
            UserManager um = ctx.getSystemService(UserManager.class);
            if (um != null) {
                Method getUsers = UserManager.class.getMethod("getUsers", boolean.class);
                Object listObj = getUsers.invoke(um, true);
                if (listObj instanceof List<?>) {
                    int fallback = 0;
                    for (Object uo : (List<?>) listObj) {
                        int id = uo.getClass().getField("id").getInt(uo);
                        if (id > 0) {
                            boolean running = um.isUserRunning(UserHandle.of(id));
                            if (id == 10 && running) return 10;
                            if (running && fallback == 0) fallback = id;
                        }
                    }
                    if (fallback > 0) return fallback;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "UserManager user query failed: " + t);
        }

        // Last resort: `pm list users`
        try {
            Process p = new ProcessBuilder("pm", "list", "users").redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                int fallback = 0;
                String line;
                while ((line = br.readLine()) != null) {
                    Matcher m = USER_LINE_PATTERN.matcher(line);
                    if (m.find()) {
                        int id = Integer.parseInt(m.group(1));
                        if (id == 10) return 10;
                        if (id > 0 && fallback == 0) fallback = id;
                    }
                }
                p.waitFor();
                if (fallback > 0) return fallback;
            }
        } catch (Throwable t) {
            Log.w(TAG, "pm list users failed: " + t);
        }
        return 0;
    }

    private static List<ResolveInfo> queryLauncherActivities(Context ctx, int userId, String packageName) {
        PackageManager pm = ctx.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        if (packageName != null) {
            intent.setPackage(packageName);
        }
        int flags = PackageManager.MATCH_ALL;
        try {
            Method m = PackageManager.class.getMethod("queryIntentActivitiesAsUser", Intent.class, int.class, int.class);
            @SuppressWarnings("unchecked")
            List<ResolveInfo> res = (List<ResolveInfo>) m.invoke(pm, intent, flags, userId);
            if (res != null) return res;
        } catch (Throwable t) {
            Log.w(TAG, "queryIntentActivitiesAsUser failed (uid=" + userId + "): " + t);
        }
        try {
            Context userCtx = ctx.createPackageContextAsUser("android", 0, UserHandle.of(userId));
            return userCtx.getPackageManager().queryIntentActivities(intent, flags);
        } catch (Throwable t) {
            Log.w(TAG, "createPackageContextAsUser fallback failed: " + t);
        }
        return pm.queryIntentActivities(intent, flags);
    }

    private static Map<String, AppEntry> collectApps(Context ctx, int userId, String filterPkg) {
        PackageManager pm = ctx.getPackageManager();
        List<ResolveInfo> infos = queryLauncherActivities(ctx, userId, filterPkg);
        Map<String, AppEntry> out = new LinkedHashMap<>();
        if (infos == null) return out;
        for (ResolveInfo ri : infos) {
            if (ri == null || ri.activityInfo == null) continue;
            ActivityInfo ai = ri.activityInfo;
            String pkg = ai.packageName;
            if (pkg == null || pkg.isEmpty() || out.containsKey(pkg)) continue;
            CharSequence labelCs = ri.loadLabel(pm);
            String label = (labelCs != null && labelCs.length() > 0) ? labelCs.toString() : pkg;
            String cls = (ai.name != null) ? ai.name : "";
            out.put(pkg, new AppEntry(label, pkg, "android.intent.action.MAIN", "", pkg, cls, ri));
        }
        return out;
    }

    private static void ensureIconsDir() {
        if (!ICONS_DIR.exists()) {
            ICONS_DIR.mkdirs();
        }
        try {
            Os.chmod(ICONS_DIR.getAbsolutePath(), 0775);
        } catch (Throwable ignored) {
        }
    }

    private static void writeIconPng(PackageManager pm, ResolveInfo ri, String pkg, boolean force) {
        if (ri == null || !isValidPackage(pkg)) return;
        ensureIconsDir();
        File dst = new File(ICONS_DIR, pkg + ".png");
        if (!force && dst.exists() && dst.length() > 0) return;
        File tmp = new File(ICONS_DIR, "." + pkg + ".png.tmp");
        try {
            Drawable d = ri.loadIcon(pm);
            if (d == null) return;
            Bitmap bmp = drawableToBitmap(d, 96);
            if (bmp == null) return;
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.getFD().sync();
            }
            Os.chmod(tmp.getAbsolutePath(), 0644);
            if (!tmp.renameTo(dst)) {
                tmp.delete();
                return;
            }
            Os.chmod(dst.getAbsolutePath(), 0644);
        } catch (Throwable t) {
            Log.w(TAG, "writeIconPng failed for " + pkg + ": " + t);
            tmp.delete();
        }
    }

    private static Bitmap drawableToBitmap(Drawable d, int sizePx) {
        if (d instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            if (b != null) {
                if (b.getWidth() == sizePx && b.getHeight() == sizePx) return b;
                return Bitmap.createScaledBitmap(b, sizePx, sizePx, true);
            }
        }
        int w = d.getIntrinsicWidth() > 0 ? Math.min(d.getIntrinsicWidth(), sizePx) : sizePx;
        int h = d.getIntrinsicHeight() > 0 ? Math.min(d.getIntrinsicHeight(), sizePx) : sizePx;
        if (w <= 0) w = sizePx;
        if (h <= 0) h = sizePx;
        Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        d.setBounds(0, 0, sizePx, sizePx);
        d.draw(canvas);
        return out;
    }

    private static boolean isValidPackage(String pkg) {
        return pkg != null && !pkg.isEmpty() && PKG_PATTERN.matcher(pkg).matches() && !pkg.contains("..");
    }

    private static int runCmd(List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    Log.d(TAG, cmd.get(0) + ": " + line);
                }
            }
            return p.waitFor();
        } catch (Throwable t) {
            Log.e(TAG, "command failed " + cmd, t);
            return -1;
        }
    }

    private static String resolveAppLabel(Context ctx, int userId, String pkg) {
        if (!isValidPackage(pkg)) return "";
        PackageManager pm = ctx.getPackageManager();
        try {
            Method m = PackageManager.class.getMethod("getApplicationInfoAsUser", String.class, int.class, int.class);
            ApplicationInfo ai = (ApplicationInfo) m.invoke(pm, pkg, 0, userId);
            if (ai != null) {
                CharSequence cs = pm.getApplicationLabel(ai);
                if (cs != null && cs.length() > 0) return cs.toString();
            }
        } catch (Throwable ignored) {
        }
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence cs = pm.getApplicationLabel(ai);
            if (cs != null && cs.length() > 0) return cs.toString();
        } catch (Throwable ignored) {
        }
        Map<String, AppEntry> single = collectApps(ctx, userId, pkg);
        AppEntry e = single.get(pkg);
        return e != null ? e.name : "";
    }

    private static boolean isUserUnlocked(Context ctx, int uid) {
        if (uid <= 0) return false;
        try {
            UserManager um = ctx.getSystemService(UserManager.class);
            if (um != null) {
                Method m = UserManager.class.getMethod("isUserUnlocked", UserHandle.class);
                Object res = m.invoke(um, UserHandle.of(uid));
                if (res instanceof Boolean) return (Boolean) res;
            }
        } catch (Throwable t) {
            Log.w(TAG, "isUserUnlocked check failed for uid=" + uid + ": " + t);
        }
        return true;
    }

    private static void startUserMonitorLoop(final Context ctx) {
        Thread t = new Thread(() -> {
            IBinder lastMonitor = null;
            int lastNotifiedUid = -1;
            Set<String> knownPkgs = new HashSet<>();
            boolean hasBaseline = false;

            while (true) {
                try {
                    IBinder mon = ServiceManager.getService(USER_MONITOR_SERVICE);
                    if (mon != null && mon.isBinderAlive()) {
                        int uid = resolveTargetUser(ctx);
                        if (uid <= 0 || !isUserUnlocked(ctx, uid)) {
                            // Do not notify userUnlocked(0) or locked user 10 before PIN entry;
                            // host user_manager.py would otherwise wipe user-10 .desktop shortcuts.
                            Thread.sleep(2000L);
                            continue;
                        }
                        if (mon != lastMonitor || uid != lastNotifiedUid) {
                            lastMonitor = mon;
                            lastNotifiedUid = uid;
                            knownPkgs = new HashSet<>(collectApps(ctx, uid, null).keySet());
                            hasBaseline = true;
                            sendUserUnlocked(mon, uid);
                        } else if (hasBaseline) {
                            Set<String> curPkgs = new HashSet<>(collectApps(ctx, uid, null).keySet());
                            for (String pkg : curPkgs) {
                                if (!knownPkgs.contains(pkg)) {
                                    sendPackageStateChanged(mon, PACKAGE_ADDED, pkg, uid);
                                }
                            }
                            for (String pkg : knownPkgs) {
                                if (!curPkgs.contains(pkg)) {
                                    sendPackageStateChanged(mon, PACKAGE_REMOVED, pkg, uid);
                                }
                            }
                            knownPkgs = curPkgs;
                        }
                    } else {
                        lastMonitor = null;
                        lastNotifiedUid = -1;
                    }
                } catch (Throwable e) {
                    Log.w(TAG, "user monitor tick error", e);
                }

                try {
                    Thread.sleep(lastMonitor == null ? 2000L : 10000L);
                } catch (InterruptedException ignored) {
                }
            }
        }, "gb-usermonitor-watch");
        t.setDaemon(true);
        t.start();
    }

    private static void sendUserUnlocked(IBinder mon, int uid) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(USER_MONITOR_IFACE);
            data.writeInt(uid);
            boolean ok = mon.transact(UM_TX_USER_UNLOCKED, data, reply, 0);
            if (ok) {
                reply.readException();
                Log.i(TAG, "notified " + USER_MONITOR_SERVICE + " userUnlocked(" + uid + ")");
            } else {
                Log.w(TAG, "userUnlocked transact returned false");
            }
        } catch (Throwable t) {
            Log.w(TAG, "userUnlocked transact failed: " + t);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static void sendPackageStateChanged(IBinder mon, int mode, String pkg, int uid) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(USER_MONITOR_IFACE);
            data.writeInt(mode);
            data.writeString(pkg);
            data.writeInt(uid);
            boolean ok = mon.transact(UM_TX_PACKAGE_STATE_CHANGED, data, reply, 0);
            if (ok) {
                reply.readException();
                Log.i(TAG, "notified packageStateChanged(" + mode + ", " + pkg + ", " + uid + ")");
            }
        } catch (Throwable t) {
            Log.w(TAG, "packageStateChanged transact failed: " + t);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static final class AppEntry {
        final String name;
        final String packageName;
        final String action;
        final String launchIntent;
        final String componentPackageName;
        final String componentClassName;
        final ResolveInfo resolveInfo;

        AppEntry(String name, String packageName, String action, String launchIntent,
                 String componentPackageName, String componentClassName, ResolveInfo resolveInfo) {
            this.name = name;
            this.packageName = packageName;
            this.action = action;
            this.launchIntent = launchIntent;
            this.componentPackageName = componentPackageName;
            this.componentClassName = componentClassName;
            this.resolveInfo = resolveInfo;
        }

        void writeToParcel(Parcel reply) {
            reply.writeInt(1);
            reply.writeString(name);
            reply.writeString(packageName);
            reply.writeString(action);
            reply.writeString(launchIntent);
            reply.writeString(componentPackageName);
            reply.writeString(componentClassName);
            reply.writeInt(1);
            reply.writeString("android.intent.category.LAUNCHER");
        }
    }

    private static final class PlatformBinder extends Binder {
        private final Context mCtx;

        PlatformBinder(Context ctx) {
            mCtx = ctx;
            attachInterface(() -> this, PLATFORM_IFACE);
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(PLATFORM_IFACE);
                return true;
            }
            if (code < TX_GETPROP || code > TX_LAUNCH_INTENT) {
                return super.onTransact(code, data, reply, flags);
            }

            try {
                data.enforceInterface(PLATFORM_IFACE);
                int uid = resolveTargetUser(mCtx);

                switch (code) {
                    case TX_GETPROP: {
                        String key = data.readString();
                        String def = data.readString();
                        String val = (key != null && !key.isEmpty())
                                ? SystemProperties.get(key, def != null ? def : "")
                                : (def != null ? def : "");
                        reply.writeNoException();
                        reply.writeString(val);
                        return true;
                    }
                    case TX_SETPROP: {
                        String key = data.readString();
                        String val = data.readString();
                        if ("waydroid.active_apps".equals(key)
                                && val != null && !val.isEmpty() && !"none".equals(val)
                                && !isPropTrue("persist.waydroid.multi_windows")
                                && !isPropTrue("persist.gbos.sf_layer_names")) {
                            val = "Waydroid";
                        }
                        if (key != null && !key.isEmpty()) {
                            SystemProperties.set(key, val != null ? val : "");
                        }
                        reply.writeNoException();
                        return true;
                    }
                    case TX_GET_APPS_INFO: {
                        Map<String, AppEntry> apps = collectApps(mCtx, uid, null);
                        PackageManager pm = mCtx.getPackageManager();
                        for (AppEntry e : apps.values()) {
                            writeIconPng(pm, e.resolveInfo, e.packageName, false);
                        }
                        reply.writeNoException();
                        reply.writeInt(apps.size());
                        for (AppEntry e : apps.values()) {
                            e.writeToParcel(reply);
                        }
                        return true;
                    }
                    case TX_GET_APP_INFO: {
                        String pkg = data.readString();
                        Map<String, AppEntry> apps = isValidPackage(pkg)
                                ? collectApps(mCtx, uid, pkg)
                                : new HashMap<>();
                        AppEntry e = apps.get(pkg);
                        reply.writeNoException();
                        if (e != null) {
                            writeIconPng(mCtx.getPackageManager(), e.resolveInfo, e.packageName, true);
                            e.writeToParcel(reply);
                        } else {
                            reply.writeInt(0);
                        }
                        return true;
                    }
                    case TX_INSTALL_APP: {
                        String apkPath = data.readString();
                        int rc = -1;
                        if (apkPath != null && !apkPath.isEmpty()) {
                            List<String> cmd = new ArrayList<>();
                            cmd.add("pm");
                            cmd.add("install");
                            cmd.add("-r");
                            cmd.add("--user");
                            cmd.add(Integer.toString(uid));
                            cmd.add(apkPath);
                            rc = runCmd(cmd);
                        }
                        reply.writeNoException();
                        reply.writeInt(rc);
                        return true;
                    }
                    case TX_REMOVE_APP: {
                        String pkg = data.readString();
                        int rc = -1;
                        if (isValidPackage(pkg)) {
                            List<String> cmd = new ArrayList<>();
                            cmd.add("pm");
                            cmd.add("uninstall");
                            cmd.add("--user");
                            cmd.add(Integer.toString(uid));
                            cmd.add(pkg);
                            rc = runCmd(cmd);
                            if (rc == 0) {
                                new File(ICONS_DIR, pkg + ".png").delete();
                            }
                        }
                        reply.writeNoException();
                        reply.writeInt(rc);
                        return true;
                    }
                    case TX_LAUNCH_APP: {
                        String pkg = data.readString();
                        if (isValidPackage(pkg)) {
                            Map<String, AppEntry> apps = collectApps(mCtx, uid, pkg);
                            AppEntry e = apps.get(pkg);
                            int rc = -1;
                            if (e != null && e.componentClassName != null && !e.componentClassName.isEmpty()) {
                                ComponentName cn = new ComponentName(e.componentPackageName, e.componentClassName);
                                List<String> cmd = new ArrayList<>();
                                cmd.add("am");
                                cmd.add("start");
                                cmd.add("--user");
                                cmd.add(Integer.toString(uid));
                                cmd.add("-a");
                                cmd.add("android.intent.action.MAIN");
                                cmd.add("-c");
                                cmd.add("android.intent.category.LAUNCHER");
                                cmd.add("-n");
                                cmd.add(cn.flattenToShortString());
                                cmd.add("-f");
                                cmd.add("0x10200000");
                                rc = runCmd(cmd);
                            }
                            if (rc != 0) {
                                List<String> mcmd = new ArrayList<>();
                                mcmd.add("monkey");
                                mcmd.add("-p");
                                mcmd.add(pkg);
                                mcmd.add("-c");
                                mcmd.add("android.intent.category.LAUNCHER");
                                mcmd.add("1");
                                runCmd(mcmd);
                            }
                        }
                        reply.writeNoException();
                        return true;
                    }
                    case TX_GET_APP_NAME: {
                        String pkg = data.readString();
                        String label = resolveAppLabel(mCtx, uid, pkg);
                        reply.writeNoException();
                        reply.writeString(label != null ? label : "");
                        return true;
                    }
                    case TX_SETTINGS_PUT_STRING: {
                        int type = data.readInt();
                        String key = data.readString();
                        String val = data.readString();
                        putSettingString(uid, type, key, val);
                        reply.writeNoException();
                        return true;
                    }
                    case TX_SETTINGS_GET_STRING: {
                        int type = data.readInt();
                        String key = data.readString();
                        String val = getSettingString(uid, type, key);
                        reply.writeNoException();
                        reply.writeString(val != null ? val : "");
                        return true;
                    }
                    case TX_SETTINGS_PUT_INT: {
                        int type = data.readInt();
                        String key = data.readString();
                        int val = data.readInt();
                        putSettingInt(uid, type, key, val);
                        reply.writeNoException();
                        return true;
                    }
                    case TX_SETTINGS_GET_INT: {
                        int type = data.readInt();
                        String key = data.readString();
                        int val = getSettingInt(uid, type, key);
                        reply.writeNoException();
                        reply.writeInt(val);
                        return true;
                    }
                    case TX_LAUNCH_INTENT: {
                        String action = data.readString();
                        String uriStr = data.readString();
                        String resolvedPkg = launchExternalIntent(uid, action, uriStr);
                        reply.writeNoException();
                        reply.writeString(resolvedPkg);
                        return true;
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "onTransact error code=" + code, t);
                reply.setDataPosition(0);
                reply.writeException(t instanceof Exception ? (Exception) t : new RuntimeException(t));
                return true;
            }
            return false;
        }

        private static String settingsNamespace(int type) {
            if (type == 0) return "system";
            if (type == 1) return "secure";
            return "global";
        }

        private void putSettingString(int uid, int type, String key, String val) {
            if (key == null || key.isEmpty()) return;
            List<String> cmd = new ArrayList<>();
            cmd.add("settings");
            cmd.add("put");
            if (type != 2) {
                cmd.add("--user");
                cmd.add(Integer.toString(uid));
            }
            cmd.add(settingsNamespace(type));
            cmd.add(key);
            cmd.add(val != null ? val : "");
            runCmd(cmd);
        }

        private String getSettingString(int uid, int type, String key) {
            if (key == null || key.isEmpty()) return "";
            List<String> cmd = new ArrayList<>();
            cmd.add("settings");
            cmd.add("get");
            if (type != 2) {
                cmd.add("--user");
                cmd.add(Integer.toString(uid));
            }
            cmd.add(settingsNamespace(type));
            cmd.add(key);
            try {
                Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                String line;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    line = br.readLine();
                }
                int rc = p.waitFor();
                if (rc != 0 || line == null || "null".equals(line)) return "";
                return line;
            } catch (Throwable t) {
                Log.w(TAG, "settingsGetString CLI failed: " + t);
                return "";
            }
        }

        private void putSettingInt(int uid, int type, String key, int val) {
            putSettingString(uid, type, key, Integer.toString(val));
        }

        private int getSettingInt(int uid, int type, String key) {
            String s = getSettingString(uid, type, key);
            if (s.isEmpty()) return 0;
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private String launchExternalIntent(int uid, String action, String uriStr) {
            if (action == null || action.isEmpty()) return "";
            String resolvedPkg = "";
            try {
                Intent intent = new Intent(action);
                if (uriStr != null && !uriStr.isEmpty()) {
                    intent.setData(Uri.parse(uriStr));
                }
                PackageManager pm = mCtx.getPackageManager();
                ResolveInfo ri = null;
                try {
                    Method m = PackageManager.class.getMethod("resolveActivityAsUser", Intent.class, int.class, int.class);
                    ri = (ResolveInfo) m.invoke(pm, intent, PackageManager.MATCH_DEFAULT_ONLY, uid);
                } catch (Throwable ignored) {
                    ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
                }
                if (ri != null && ri.activityInfo != null && ri.activityInfo.packageName != null) {
                    resolvedPkg = ri.activityInfo.packageName;
                }
            } catch (Throwable t) {
                Log.w(TAG, "resolveActivity failed for " + action + ": " + t);
            }

            List<String> cmd = new ArrayList<>();
            cmd.add("am");
            cmd.add("start");
            cmd.add("--user");
            cmd.add(Integer.toString(uid));
            cmd.add("-a");
            cmd.add(action);
            if (uriStr != null && !uriStr.isEmpty()) {
                cmd.add("-d");
                cmd.add(uriStr);
            }
            cmd.add("-f");
            cmd.add("0x10000000");
            runCmd(cmd);
            return resolvedPkg;
        }
    }
}
