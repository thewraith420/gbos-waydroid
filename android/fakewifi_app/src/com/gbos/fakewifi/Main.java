package com.gbos.fakewifi;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.net.wifi.nl80211.DeviceWiphyCapabilities;
import android.net.wifi.nl80211.IApInterface;
import android.net.wifi.nl80211.IClientInterface;
import android.net.wifi.nl80211.IScanEvent;
import android.net.wifi.nl80211.IWifiScannerImpl;
import android.net.wifi.nl80211.NativeScanResult;
import android.net.wifi.nl80211.SingleScanSettings;
import java.util.ArrayList;
import java.util.List;

import com.android.wifi.x.android.hardware.wifi.IWifi;
import com.android.wifi.x.android.hardware.wifi.IWifiChip;
import com.android.wifi.x.android.hardware.wifi.IWifiChipEventCallback;
import com.android.wifi.x.android.hardware.wifi.IWifiEventCallback;
import com.android.wifi.x.android.hardware.wifi.IWifiStaIface;
import com.android.wifi.x.android.hardware.wifi.IWifiStaIfaceEventCallback;
import com.android.wifi.x.android.hardware.wifi.StaBackgroundScanCapabilities;
import com.android.wifi.x.android.hardware.wifi.StaRoamingCapabilities;
import com.android.wifi.x.android.hardware.wifi.WifiChipCapabilities;

/**
 * Fake Wi-Fi HAL (android.hardware.wifi AIDL, version 4) for a container that has no Wi-Fi driver stack of its own.
 * It claims one chip in station mode with one interface, wlan0 (a mac80211_hwsim radio moved in by wifi-hwsim.sh), so that Android's
 * Wi-Fi service can reach the "enabled" state. Everything not needed for that returns zero values (see Defaults).
 * See GBOS_WAYDROID_FIXES.md, section 3.18.
 */
public class Main {
    static final String TAG = "FAKEWIFI";
    static final String IFACE = "wlan0";
    static final int CHIP_ID = 1;
    static final int MODE_ID = 1;
    static Handler H;

    static void post(final Runnable r) { H.post(r); }

    /** The MAC address of a network interface from sysfs ("aa:bb:cc:dd:ee:ff"), or null. */
    static byte[] readSysfsMac(String iface) {
        try {
            String t = new String(java.nio.file.Files.readAllBytes(new java.io.File("/sys/class/net/" + iface + "/address").toPath()),
                    java.nio.charset.StandardCharsets.US_ASCII).trim();
            String[] p = t.split(":");
            if (p.length != 6) return null;
            byte[] m = new byte[6];
            for (int i = 0; i < 6; i++) m[i] = (byte) Integer.parseInt(p[i], 16);
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    static class Sta extends Defaults.DefIWifiStaIface {
        @Override public String getName() { Log.d(TAG, "StaIface.getName"); return IFACE; }
        @Override public byte[] getFactoryMacAddress() {
            // the real address of the interface when it exists (bare metal: wlan0 of the PCIe/USB adapter), else a fixed locally administered one
            byte[] m = readSysfsMac(IFACE);
            Log.d(TAG, "StaIface.getFactoryMacAddress -> " + (m == null ? "fixed" : "sysfs"));
            return m != null ? m : new byte[] {2, 0, 0, 0, 0, 0};
        }
        @Override public int getFeatureSet() { Log.d(TAG, "StaIface.getFeatureSet"); return 0; }
        @Override public StaBackgroundScanCapabilities getBackgroundScanCapabilities() { Log.d(TAG, "StaIface.getBackgroundScanCapabilities"); return new StaBackgroundScanCapabilities(); }
        @Override public StaRoamingCapabilities getRoamingCapabilities() { Log.d(TAG, "StaIface.getRoamingCapabilities"); return new StaRoamingCapabilities(); }
        @Override public void registerEventCallback(IWifiStaIfaceEventCallback cb) { Log.d(TAG, "StaIface.registerEventCallback"); }
        public int getInterfaceVersion() { return 4; }
        public String getInterfaceHash() { return "fake"; }
    }

    static class Chip extends Defaults.DefIWifiChip {
        final Sta sta = new Sta();
        boolean staCreated;
        int mode = -1;

        @Override public int getId() { Log.d(TAG, "Chip.getId"); return CHIP_ID; }
        @Override public int getMode() { Log.d(TAG, "Chip.getMode -> " + mode); return mode < 0 ? MODE_ID : mode; }
        @Override public IWifiChip.ChipMode[] getAvailableModes() {
            Log.d(TAG, "Chip.getAvailableModes");
            IWifiChip.ChipConcurrencyCombinationLimit limit = new IWifiChip.ChipConcurrencyCombinationLimit();
            limit.types = new int[] {0};              // IfaceConcurrencyType.STA
            limit.maxIfaces = 1;
            IWifiChip.ChipConcurrencyCombination combo = new IWifiChip.ChipConcurrencyCombination();
            combo.limits = new IWifiChip.ChipConcurrencyCombinationLimit[] {limit};
            IWifiChip.ChipMode m = new IWifiChip.ChipMode();
            m.id = MODE_ID;
            m.availableCombinations = new IWifiChip.ChipConcurrencyCombination[] {combo};
            return new IWifiChip.ChipMode[] {m};
        }
        @Override public void configureChip(int modeId) { Log.d(TAG, "Chip.configureChip " + modeId); mode = modeId; }
        @Override public IWifiStaIface createStaIface() { Log.d(TAG, "Chip.createStaIface"); staCreated = true; return sta; }
        @Override public String[] getStaIfaceNames() { Log.d(TAG, "Chip.getStaIfaceNames"); return staCreated ? new String[] {IFACE} : new String[0]; }
        @Override public IWifiStaIface getStaIface(String name) { Log.d(TAG, "Chip.getStaIface " + name); return staCreated && IFACE.equals(name) ? sta : null; }
        @Override public void removeStaIface(String name) { Log.d(TAG, "Chip.removeStaIface " + name); staCreated = false; }
        @Override public int getFeatureSet() { Log.d(TAG, "Chip.getFeatureSet"); return 0; }
        @Override public WifiChipCapabilities getWifiChipCapabilities() { Log.d(TAG, "Chip.getWifiChipCapabilities"); return new WifiChipCapabilities(); }
        @Override public IWifiChip.ChipDebugInfo requestChipDebugInfo() {
            Log.d(TAG, "Chip.requestChipDebugInfo");
            IWifiChip.ChipDebugInfo d = new IWifiChip.ChipDebugInfo();
            d.driverDescription = "fake";
            d.firmwareDescription = "fake";
            return d;
        }
        @Override public void registerEventCallback(IWifiChipEventCallback cb) { Log.d(TAG, "Chip.registerEventCallback"); }
        public int getInterfaceVersion() { return 4; }
        public String getInterfaceHash() { return "fake"; }
    }

    static class Hal extends Defaults.DefIWifi {
        final Chip chip = new Chip();
        IWifiEventCallback cb;
        boolean started;

        @Override public void registerEventCallback(IWifiEventCallback c) { Log.d(TAG, "Wifi.registerEventCallback"); cb = c; }
        @Override public boolean isStarted() { Log.d(TAG, "Wifi.isStarted -> " + started); return started; }
        @Override public void start() {
            Log.d(TAG, "Wifi.start");
            started = true;
            post(new Runnable() { public void run() { try { if (cb != null) cb.onStart(); } catch (RemoteException e) { Log.w(TAG, "onStart", e); } } });
        }
        @Override public void stop() {
            Log.d(TAG, "Wifi.stop");
            started = false; chip.staCreated = false;
            post(new Runnable() { public void run() { try { if (cb != null) cb.onStop(); } catch (RemoteException e) { Log.w(TAG, "onStop", e); } } });
        }
        @Override public int[] getChipIds() { Log.d(TAG, "Wifi.getChipIds"); return new int[] {CHIP_ID}; }
        @Override public IWifiChip getChip(int id) { Log.d(TAG, "Wifi.getChip " + id); return id == CHIP_ID ? chip : null; }
        public int getInterfaceVersion() { return 4; }
        public String getInterfaceHash() { return "fake"; }
    }


    // ---- fake wificond (service "wifinl80211"): just enough for WifiNative to set up a client interface on wlan0 ----
    static class Scanner extends Defaults.DefIWifiScannerImpl {
        IScanEvent ev;
        @Override public void subscribeScanEvents(IScanEvent e) { Log.d(TAG, "Scanner.subscribeScanEvents"); ev = e; }
        @Override public void unsubscribeScanEvents() { Log.d(TAG, "Scanner.unsubscribeScanEvents"); ev = null; }
        @Override public NativeScanResult[] getScanResults() { Log.d(TAG, "Scanner.getScanResults"); return new NativeScanResult[0]; }
        @Override public NativeScanResult[] getPnoScanResults() { Log.d(TAG, "Scanner.getPnoScanResults"); return new NativeScanResult[0]; }
        @Override public int getMaxSsidsPerScan() { Log.d(TAG, "Scanner.getMaxSsidsPerScan"); return 16; }
        private void finishScan() {
            post(new Runnable() { public void run() { try { if (ev != null) ev.OnScanResultReady(); } catch (RemoteException e) { Log.w(TAG, "OnScanResultReady", e); } } });
        }
        @Override public boolean scan(SingleScanSettings s) { Log.d(TAG, "Scanner.scan"); finishScan(); return true; }
        @Override public int scanRequest(SingleScanSettings s) { Log.d(TAG, "Scanner.scanRequest"); finishScan(); return 0; }
        @Override public boolean startPnoScan(android.net.wifi.nl80211.PnoSettings s) { Log.d(TAG, "Scanner.startPnoScan"); return true; }
        @Override public boolean stopPnoScan() { Log.d(TAG, "Scanner.stopPnoScan"); return true; }
    }

    static class Client extends Defaults.DefIClientInterface {
        final String name;
        final Scanner scanner = new Scanner();
        Client(String n) { name = n; }
        @Override public String getInterfaceName() { Log.d(TAG, "Client.getInterfaceName"); return name; }
        @Override public byte[] getMacAddress() { Log.d(TAG, "Client.getMacAddress"); return new byte[] {2, 0, 0, 0, 0, 0}; }
        @Override public int[] getPacketCounters() { Log.d(TAG, "Client.getPacketCounters"); return new int[] {0, 0}; }
        @Override public int[] signalPoll() { Log.d(TAG, "Client.signalPoll"); return new int[] {-60, 0, 0, 0}; }
        @Override public IWifiScannerImpl getWifiScannerImpl() { Log.d(TAG, "Client.getWifiScannerImpl"); return scanner; }
    }

    static class Wificond extends Defaults.DefIWificond {
        final List<Client> clients = new ArrayList<Client>();
        @Override public IClientInterface createClientInterface(String name) { Log.d(TAG, "Wificond.createClientInterface " + name); Client c = new Client(name); clients.add(c); return c; }
        @Override public boolean tearDownClientInterface(String name) { Log.d(TAG, "Wificond.tearDownClientInterface " + name); clients.clear(); return true; }
        @Override public boolean tearDownApInterface(String name) { Log.d(TAG, "Wificond.tearDownApInterface " + name); return true; }
        @Override public void tearDownInterfaces() { Log.d(TAG, "Wificond.tearDownInterfaces"); clients.clear(); }
        @Override public int[] getAvailable2gChannels() { Log.d(TAG, "Wificond.getAvailable2gChannels"); return new int[] {2412, 2417, 2422, 2427, 2432, 2437, 2442, 2447, 2452, 2457, 2462}; }
        @Override public int[] getAvailable5gNonDFSChannels() { Log.d(TAG, "Wificond.getAvailable5gNonDFSChannels"); return new int[] {5180, 5200, 5220, 5240, 5745, 5765, 5785, 5805, 5825}; }
        @Override public DeviceWiphyCapabilities getDeviceWiphyCapabilities(String n) { Log.d(TAG, "Wificond.getDeviceWiphyCapabilities " + n); return new DeviceWiphyCapabilities(); }
        // present on the device's IWificond but not in the build-time jar (looked up by signature at run time)
        public List GetClientInterfaces() throws RemoteException { Log.d(TAG, "Wificond.GetClientInterfaces"); return new ArrayList<Client>(clients); }
        public List GetApInterfaces() throws RemoteException { Log.d(TAG, "Wificond.GetApInterfaces"); return new ArrayList<IApInterface>(); }
    }

    static void add(String name, IBinder b) {
        ServiceManager.addService(name, b, false, 8 /* DUMP_FLAG_PRIORITY_DEFAULT */);
        Log.i(TAG, "registered " + name);
    }

    static void dump(String[] args) throws Exception {
        for (int i = 1; i < args.length; i++) {
            Class<?> c = Class.forName(args[i]);
            System.out.println("== " + c.getName());
            java.lang.reflect.Method[] ms = c.getDeclaredMethods();
            java.util.Arrays.sort(ms, new java.util.Comparator<java.lang.reflect.Method>() {
                public int compare(java.lang.reflect.Method a, java.lang.reflect.Method b) { return a.toString().compareTo(b.toString()); }
            });
            for (java.lang.reflect.Method m : ms) System.out.println("  " + m);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("dump")) { dump(args); return; }
        Looper.prepareMainLooper();
        H = new Handler(Looper.getMainLooper());
        add(IWifi.DESCRIPTOR + "/default", new Hal());
        if (args.length == 0 || !args[0].equals("hal-only")) add("wifinl80211", new Wificond());
        Looper.loop();
    }
}
