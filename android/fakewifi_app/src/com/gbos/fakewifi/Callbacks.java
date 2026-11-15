package com.gbos.fakewifi;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Client-side stand-ins for the HAL's event-callback interfaces (system_server implements them; the shaded copy of the AIDL classes
 * has no client Proxy for them). Only IWifiEventCallback's one-way calls are really sent (onFailure=1, onStart=2, onStop=3,
 * onSubsystemRestart=4); the chip and station callbacks are accepted and ignored.
 */
final class Callbacks {
    private Callbacks() {}

    @SuppressWarnings("unchecked")
    static <T> T wrap(Class<T> iface, final IBinder b) {
        final String simple = iface.getSimpleName();
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, new InvocationHandler() {
            public Object invoke(Object proxy, Method m, Object[] a) throws Throwable {
                String n = m.getName();
                if (n.equals("asBinder")) return b;
                if (n.equals("getInterfaceVersion")) return 4;
                if (n.equals("getInterfaceHash")) return "fake";
                if (n.equals("hashCode")) return System.identityHashCode(proxy);
                if (n.equals("equals")) return proxy == a[0];
                if (n.equals("toString")) return simple + "Proxy";
                int code = 0;
                if (simple.equals("IWifiEventCallback")) {
                    if (n.equals("onFailure")) code = 1; else if (n.equals("onStart")) code = 2;
                    else if (n.equals("onStop")) code = 3; else if (n.equals("onSubsystemRestart")) code = 4;
                }
                if (code != 0) {
                    Parcel d = Parcel.obtain();
                    try {
                        d.writeInterfaceToken("android.hardware.wifi." + simple);
                        if (a != null && a.length == 1 && a[0] instanceof Integer) d.writeInt((Integer) a[0]);
                        b.transact(code, d, null, IBinder.FLAG_ONEWAY);
                        Log.d(Main.TAG, simple + "." + n + " sent");
                    } finally { d.recycle(); }
                } else {
                    Log.d(Main.TAG, simple + "." + n + " ignored");
                }
                Class<?> r = m.getReturnType();
                if (r == boolean.class) return false;
                if (r == int.class) return 0;
                if (r == long.class) return 0L;
                return null;
            }
        });
    }
}
