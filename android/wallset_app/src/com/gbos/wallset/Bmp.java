package com.gbos.wallset;

import android.graphics.*;

/** Set a generated static wallpaper (dark blue gradient with a soft orange glow) for a user: Bmp <userId> [1=system|2=lock|3=both]. Cosmetic; used for user 0 (the login screen),
 *  whose live wallpaper does not render there. Run as root with app_process like Main. */
public class Bmp {
    public static void main(String[] a) throws Exception {
        System.out.println("start"); int user = Integer.parseInt(a[0]); int which = a.length > 1 ? Integer.parseInt(a[1]) : 3;
        int w = 1920, h = 1080;
        System.out.println("pre-bitmap");
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); Canvas cv = new Canvas(b); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, new int[]{0xFF050A1E, 0xFF0B1F5A, 0xFF1A2E7A}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP)); cv.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.62f, h * 0.02f, w * 0.38f, new int[]{0xCCFF8A1F, 0x55E05A1A, 0x00000000}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP)); cv.drawRect(0, 0, w, h, p);
        System.out.println("bitmap drawn");
        int flags = which == 1 || which == 2 ? which : 3;   // FLAG_SYSTEM=1, FLAG_LOCK=2
        android.os.IBinder bd = (android.os.IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "wallpaper");
        Object svc = Class.forName("android.app.IWallpaperManager$Stub").getMethod("asInterface", android.os.IBinder.class).invoke(null, bd);
        for (java.lang.reflect.Method m : svc.getClass().getMethods()) {
            if (!m.getName().equals("setWallpaper") || m.getParameterTypes().length != 8) continue;
            android.os.Bundle out = new android.os.Bundle();
            android.os.ParcelFileDescriptor pfd = (android.os.ParcelFileDescriptor) m.invoke(svc, "claude", "android", null, true, out, flags, null, user);
            System.out.println("got pfd " + pfd);
            if (pfd == null) { System.out.println("setWallpaper returned null"); return; }
            java.io.OutputStream os = new android.os.ParcelFileDescriptor.AutoCloseOutputStream(pfd);
            b.compress(Bitmap.CompressFormat.PNG, 100, os); os.close();
            System.out.println("wallpaper written for user " + user + " which=" + flags); return;
        }
        System.out.println("setWallpaper not found");
    }
}
