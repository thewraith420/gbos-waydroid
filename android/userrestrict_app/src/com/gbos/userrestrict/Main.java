package com.gbos.userrestrict;

import android.os.IBinder;

/** Query / set an Android user restriction. Run as root with app_process:
 *    CLASSPATH=/data/local/tmp/userrestrict.apk app_process /system/bin com.gbos.userrestrict.Main get <userId> <restriction>
 *    CLASSPATH=... com.gbos.userrestrict.Main set <userId> <restriction> true|false  */
public class Main {
    public static void main(String[] a) throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        IBinder b = (IBinder) sm.getMethod("getService", String.class).invoke(null, "user");
        Class<?> stub = Class.forName("android.os.IUserManager$Stub");
        Object um = stub.getMethod("asInterface", IBinder.class).invoke(null, b);
        Class<?> c = um.getClass();
        if (a[0].equals("get")) {
            System.out.println(a[2] + " user " + a[1] + " = " + c.getMethod("hasUserRestriction", String.class, int.class).invoke(um, a[2], Integer.parseInt(a[1])));
        } else {
            c.getMethod("setUserRestriction", String.class, boolean.class, int.class).invoke(um, a[2], Boolean.parseBoolean(a[3]), Integer.parseInt(a[1]));
            System.out.println("set " + a[2] + "=" + a[3] + " for user " + a[1]);
        }
    }
}
