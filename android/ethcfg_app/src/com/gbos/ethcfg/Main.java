package com.gbos.ethcfg;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.EthernetNetworkSpecifier;
import android.net.IpConfiguration;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Looper;
import android.net.LinkAddress;
import android.net.StaticIpConfiguration;
import android.os.IBinder;
import java.net.InetAddress;
import java.util.ArrayList;
import android.os.ServiceManager;
import java.lang.reflect.Method;

/** Root CLI for Android's Ethernet manager (reflection). app_process -cp <apk> /system/bin com.gbos.ethcfg.Main methods [filter] */
public class Main {
    public static void main(String[] a) throws Exception {
        IBinder b = ServiceManager.getService("ethernet");
        Class<?> stub = Class.forName("android.net.IEthernetManager$Stub");
        Object svc = stub.getMethod("asInterface", IBinder.class).invoke(null, b);
        String cmd = a.length > 0 ? a[0] : "methods";
        if (cmd.equals("holdwifi")) {        // ethcfg holdwifi : keep ONE generic Wi-Fi request alive (blocks forever). With a validated Ethernet as the default network Android
            Looper.prepareMainLooper();      // sends the Wi-Fi stack no request at all ("CMD_START_CONNECT but no requests and not connected, bailing") and never joins the fake AP.
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("systemMain").invoke(null);
            Context ctx = (Context) at.getMethod("getSystemContext").invoke(thread);
            ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
            NetworkRequest req = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
            cm.requestNetwork(req, new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) { System.out.println("holdwifi: available " + n); }
                @Override public void onLost(Network n) { System.out.println("holdwifi: lost " + n); }
            });
            System.out.println("holding a Wi-Fi network request");
            Looper.loop();
            return;
        }
        if (cmd.equals("hold")) {            // ethcfg hold eth1 : keep Android's network for that interface alive (blocks forever)
            Looper.prepareMainLooper();
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("systemMain").invoke(null);
            Context ctx = (Context) at.getMethod("getSystemContext").invoke(thread);
            ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
            final String iface = a[1];
            // setNetworkSpecifier needs exactly one transport, so hold one request per possible label of the interface
            // (plain Ethernet, or Wi-Fi when the lan-wifi overlay relabels it); only the matching one ever gets a network.
            for (int tr : new int[] { NetworkCapabilities.TRANSPORT_ETHERNET, NetworkCapabilities.TRANSPORT_WIFI }) {
                final int t = tr;
                NetworkRequest req = new NetworkRequest.Builder()
                        .addTransportType(t)
                        .setNetworkSpecifier(new EthernetNetworkSpecifier(iface)).build();
                cm.requestNetwork(req, new ConnectivityManager.NetworkCallback() {
                    @Override public void onAvailable(Network n) { System.out.println("hold " + iface + " (transport " + t + "): available " + n); }
                    @Override public void onLost(Network n) { System.out.println("hold " + iface + " (transport " + t + "): lost " + n); }
                });
            }
            System.out.println("holding a network request for " + iface);
            Looper.loop();
            return;
        }
        if (cmd.equals("list")) {
            String[] ifs = (String[]) svc.getClass().getMethod("getAvailableInterfaces").invoke(svc);
            for (String i : ifs) System.out.println(i);
            return;
        }
        if (cmd.equals("get")) {
            Object cfg = svc.getClass().getMethod("getConfiguration", String.class).invoke(svc, a[1]);
            System.out.println(cfg);
            return;
        }
        if (cmd.equals("dhcp")) {            // ethcfg dhcp eth1
            Object cfg = IpConfiguration.class.getConstructor().newInstance();
            svc.getClass().getMethod("setConfiguration", String.class, IpConfiguration.class).invoke(svc, a[1], cfg);
            System.out.println("eth: " + a[1] + " set to DHCP");
            return;
        }
        if (cmd.equals("set")) {             // ethcfg set eth1 192.0.2.250/24 192.0.2.1 192.0.2.1[,8.8.8.8]
            String[] ipp = a[2].split("/");
            StaticIpConfiguration.Builder sb = new StaticIpConfiguration.Builder();
            sb.setIpAddress(new LinkAddress(InetAddress.getByName(ipp[0]), Integer.parseInt(ipp[1])));
            if (a.length > 3 && !a[3].isEmpty() && !a[3].equals("-")) sb.setGateway(InetAddress.getByName(a[3]));
            ArrayList<InetAddress> dns = new ArrayList<>();
            if (a.length > 4) for (String d : a[4].split(",")) if (!d.isEmpty()) dns.add(InetAddress.getByName(d));
            sb.setDnsServers(dns);
            IpConfiguration cfg = new IpConfiguration();
            cfg.setStaticIpConfiguration(sb.build());
            cfg.setIpAssignment(IpConfiguration.IpAssignment.STATIC);
            svc.getClass().getMethod("setConfiguration", String.class, IpConfiguration.class).invoke(svc, a[1], cfg);
            System.out.println("eth: " + a[1] + " set to " + cfg);
            return;
        }
        if (cmd.equals("methods")) {
            String f = a.length > 1 ? a[1].toLowerCase() : "";
            for (Method m : svc.getClass().getMethods()) {
                if (m.getDeclaringClass() == Object.class || !m.getName().toLowerCase().contains(f)) continue;
                StringBuilder s = new StringBuilder(m.getName() + "(");
                for (Class<?> p : m.getParameterTypes()) s.append(p.getName()).append(", ");
                System.out.println(s + ") -> " + m.getReturnType().getSimpleName());
            }
        }
    }
}
