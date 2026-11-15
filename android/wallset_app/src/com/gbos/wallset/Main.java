package com.gbos.wallset;

import android.content.ComponentName;
import android.os.IBinder;
import java.lang.reflect.Method;

/** Set the live wallpaper component of an Android user (cosmetic). Run as root:
 *    CLASSPATH=/data/local/tmp/wallset.apk app_process /system/bin com.gbos.wallset.Main <userId> <package/class> [which]
 *  which: 1 = system, 2 = lock, 3 = both (default). */
public class Main {
    public static void main(String[] a) throws Exception {
        if (a[0].equals("list")) { Object sv = Class.forName("android.app.IWallpaperManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, (IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "wallpaper")); for (Method m : sv.getClass().getMethods()) if (m.getName().toLowerCase().contains("wallpaper") && (m.getName().contains("Component") || m.getName().contains("Description"))) System.out.println(m.getName() + " " + java.util.Arrays.toString(m.getParameterTypes())); return; }
        if (a[0].equals("desc")) {   // desc <user> <id> <lightUrl> <darkUrl> [which]: apply the DarkLight wallpaper WITH its content (the Spark image urls)
            int du = Integer.parseInt(a[1]); ComponentName dc = ComponentName.unflattenFromString("com.google.android.desktop.adaptivewallpaper/.livewallpaper.DarkLightWallpaperService");
            android.system.Os.setuid(du * 100000 + 1000);
            android.os.PersistableBundle pb = new android.os.PersistableBundle();
            pb.putString("picker_metadata_collection_id", "com.google.android.desktop.adaptivewallpaper"); pb.putString("picker_metadata_unique_id", "com.google.android.desktop.adaptivewallpaper.livewallpaper.DarkLightWallpaperService");
            pb.putInt("picker_metadata_placeholder_color", 0); pb.putString("light_url", a[3]); pb.putString("dark_url", a[4]);
            Class<?> bc = Class.forName("android.app.wallpaper.WallpaperDescription$Builder"); Object bld = bc.getConstructor().newInstance();
            bc.getMethod("setComponent", ComponentName.class).invoke(bld, dc); bc.getMethod("setId", String.class).invoke(bld, a[2]); bc.getMethod("setContent", android.os.PersistableBundle.class).invoke(bld, pb);
            Object d = bc.getMethod("build").invoke(bld);
            Object sv = Class.forName("android.app.IWallpaperManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, (IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "wallpaper"));
            int wh = a.length > 5 ? Integer.parseInt(a[5]) : 3;
            sv.getClass().getMethod("setWallpaperComponentChecked", d.getClass(), String.class, int.class, int.class).invoke(sv, d, "android", wh, du); System.out.println("description applied for user " + du); return;
        }
        int user = Integer.parseInt(a[0]); ComponentName cn = ComponentName.unflattenFromString(a[1]); int which = (a.length > 2 && !a[2].equals("clearlock")) ? Integer.parseInt(a[2]) : 3;
        android.system.Os.setuid(user * 100000 + 1000);
        IBinder b = (IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "wallpaper");
        Object svc = Class.forName("android.app.IWallpaperManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, b);
        if (a.length > 2 && (a[2].equals("clearlock") || a[2].equals("clear1"))) {      // clear the lock-screen (2) or system (1) wallpaper of the user
            int cw = a[2].equals("clear1") ? 1 : 2;
            for (Method m : svc.getClass().getMethods()) {
                if (!m.getName().equals("clearWallpaper")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && p[0] == String.class) { m.invoke(svc, "android", cw, user); System.out.println("wallpaper cleared: " + cw); return; }
            }
            System.out.println("clearWallpaper not found"); return;
        }
        for (Method m : svc.getClass().getMethods()) {
            if (!m.getName().equals("setWallpaperComponentChecked") && !m.getName().equals("setWallpaperComponent")) continue;
            Class<?>[] p = m.getParameterTypes();
            System.out.println("trying " + m.getName() + " " + java.util.Arrays.toString(p));
            try {
                if (p.length == 4 && p[0] == ComponentName.class) { m.invoke(svc, cn, "android", which, user); System.out.println("ok"); return; }
                if (p.length == 1 && p[0] == ComponentName.class) { m.invoke(svc, cn); System.out.println("ok (current user only)"); return; }
            } catch (Throwable t) { System.out.println("failed: " + (t.getCause() != null ? t.getCause() : t)); }
        }
        System.out.println("no usable method found");
    }
}
