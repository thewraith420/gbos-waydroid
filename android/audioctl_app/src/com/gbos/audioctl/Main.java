package com.gbos.audioctl;

import android.os.IBinder;
import android.os.ServiceManager;
import java.lang.reflect.Method;

/** Root CLI for the audio service via reflection.  app_process -cp <apk> /system/bin com.gbos.audioctl.Main <cmd> [args] */
public class Main {
    public static void main(String[] a) throws Exception {
        IBinder b = ServiceManager.getService("audio");
        Class<?> stub = Class.forName("android.media.IAudioService$Stub");
        Object svc = stub.getMethod("asInterface", IBinder.class).invoke(null, b);
        String cmd = a.length > 0 ? a[0] : "info";
        if (cmd.equals("methods")) {
            String f = a.length > 1 ? a[1].toLowerCase() : "";
            for (Method m : svc.getClass().getMethods()) {
                if (m.getName().toLowerCase().contains(f) && m.getDeclaringClass() != Object.class) {
                    StringBuilder s = new StringBuilder(m.getName() + "(");
                    for (Class<?> p : m.getParameterTypes()) s.append(p.getSimpleName()).append(",");
                    System.out.println(s + ")");
                }
            }
            return;
        }
        if (cmd.equals("info")) {
            System.out.println("isMasterMute=" + svc.getClass().getMethod("isMasterMute").invoke(svc));
            return;
        }
        if (cmd.equals("setmastermute")) {
            boolean v = Boolean.parseBoolean(a[1]);
            for (Method m : svc.getClass().getMethods()) {
                if (!m.getName().equals("setMasterMute")) continue;
                Class<?>[] t = m.getParameterTypes();
                Object[] args = new Object[t.length];
                int ints = 0;
                for (int i = 0; i < t.length; i++) {
                    if (t[i] == boolean.class) args[i] = v;
                    else if (t[i] == int.class) args[i] = (ints++ == 0) ? 0 : 10;   // flags, then userId
                    else if (t[i] == String.class) args[i] = (i == t.length - 1 && t.length == 5) ? null : "android";
                    else args[i] = null;
                }
                m.invoke(svc, args);
                System.out.println("setMasterMute(" + v + ") invoked via " + m);
                System.out.println("isMasterMute=" + svc.getClass().getMethod("isMasterMute").invoke(svc));
                return;
            }
            System.out.println("no setMasterMute method");
        }
    }
}
