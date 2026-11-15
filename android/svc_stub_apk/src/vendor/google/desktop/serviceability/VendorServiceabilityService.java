package vendor.google.desktop.serviceability;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/**
 * Stand-in for the vendor-partition VendorServiceabilityService that real Googlebook hardware ships.
 * Waydroid's generic vendor image does not include it, so Task Manager / Diagnostics crash on startup
 * (ServiceabilityManager cannot bind). This service answers the same AIDL interface using the Linux
 * /proc and /sys files that are visible inside the container, which expose the host machine's real
 * CPU, memory, thermal, battery, NVMe and PCI data. Everything is read-only and best-effort: any
 * unreadable source yields an empty/zero value instead of an exception so clients never crash.
 */
public class VendorServiceabilityService extends Service {
    private static final String TAG = "VendorServiceabilityStub";

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // ---------------------------------------------------------------- small file helpers

    private static String read(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String s = r.readLine();
            return s == null ? "" : s.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static long readLong(String path, long def) {
        String s = read(path);
        if (s.isEmpty()) return def;
        try {
            return Long.parseLong(s.split("\\s+")[0]);
        } catch (Exception e) {
            return def;
        }
    }

    private static List<String> readLines(String path) {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String s;
            while ((s = r.readLine()) != null) out.add(s);
        } catch (Exception ignored) {
        }
        return out;
    }

    private static List<File> sortedDirs(String parent, String prefix) {
        File[] f = new File(parent).listFiles();
        List<File> out = new ArrayList<>();
        if (f != null) {
            for (File x : f) if (x.getName().startsWith(prefix)) out.add(x);
        }
        // numeric-aware sort so cpu2 < cpu10
        Collections.sort(out, (a, b) -> {
            int na = trailingInt(a.getName()), nb = trailingInt(b.getName());
            return na != nb ? Integer.compare(na, nb) : a.getName().compareTo(b.getName());
        });
        return out;
    }

    private static int trailingInt(String s) {
        int i = s.length();
        while (i > 0 && Character.isDigit(s.charAt(i - 1))) i--;
        try {
            return i == s.length() ? -1 : Integer.parseInt(s.substring(i));
        } catch (Exception e) {
            return -1;
        }
    }

    private static int clampInt(long v) {
        return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (v < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) v);
    }

    // ---------------------------------------------------------------- data sources

    private static CpuUsageStats cpuStats() {
        CpuUsageStats s = new CpuUsageStats();
        for (String line : readLines("/proc/stat")) {
            if (!line.startsWith("cpu ")) continue;
            String[] p = line.trim().split("\\s+");
            long[] v = new long[10];
            for (int i = 1; i < p.length && i <= 10; i++) {
                try {
                    v[i - 1] = Long.parseLong(p[i]);
                } catch (Exception ignored) {
                }
            }
            s.userCpuTicks = v[0];
            s.niceCpuTicks = v[1];
            s.systemCpuTicks = v[2];
            s.idleCpuTicks = v[3];
            s.iowaitCpuTicks = v[4];
            s.irqCpuTicks = v[5];
            s.softirqCpuTicks = v[6];
            s.stealCpuTicks = v[7];
            s.userTotalCpuTicks = v[0] + v[1];
            s.systemTotalCpuTicks = v[2] + v[5] + v[6];
            s.totalCpuTicks = v[0] + v[1] + v[2] + v[3] + v[4] + v[5] + v[6] + v[7];
            break;
        }
        return s;
    }

    private static String cpuName() {
        for (String line : readLines("/proc/cpuinfo")) {
            if (line.startsWith("model name")) {
                int i = line.indexOf(':');
                if (i >= 0) return line.substring(i + 1).trim();
            }
        }
        return "";
    }

    private static List<PhysicalCpuInfo> physicalCpus() {
        // group logical CPUs by physical package, like cros_healthd does
        TreeMap<Integer, List<LogicalCpuInfo>> byPkg = new TreeMap<>();
        for (File cpu : sortedDirs("/sys/devices/system/cpu", "cpu")) {
            if (trailingInt(cpu.getName()) < 0 || !new File(cpu, "topology").exists()) continue;
            String base = cpu.getPath();
            LogicalCpuInfo l = new LogicalCpuInfo();
            l.coreId = clampInt(readLong(base + "/topology/core_id", trailingInt(cpu.getName())));
            l.currentFrequencyKhz = clampInt(readLong(base + "/cpufreq/scaling_cur_freq", 0));
            l.maxFrequencyKhz = clampInt(readLong(base + "/cpufreq/scaling_max_freq",
                    readLong(base + "/cpufreq/cpuinfo_max_freq", 0)));
            l.maxClockSpeedKhz = clampInt(readLong(base + "/cpufreq/cpuinfo_max_freq", l.maxFrequencyKhz));
            l.cStates = new ArrayList<>();
            for (File st : sortedDirs(base + "/cpuidle", "state")) {
                CpuCStateInfo c = new CpuCStateInfo();
                c.name = read(st.getPath() + "/name");
                c.timeUs = readLong(st.getPath() + "/time", 0);
                l.cStates.add(c);
            }
            int pkg = (int) readLong(base + "/topology/physical_package_id", 0);
            List<LogicalCpuInfo> list = byPkg.get(pkg);
            if (list == null) {
                list = new ArrayList<>();
                byPkg.put(pkg, list);
            }
            list.add(l);
        }
        List<PhysicalCpuInfo> out = new ArrayList<>();
        for (List<LogicalCpuInfo> logicals : byPkg.values()) {
            PhysicalCpuInfo p = new PhysicalCpuInfo();
            p.logicalCpus = logicals;
            out.add(p);
        }
        return out;
    }

    private static MemoryPerformanceStats memStats() {
        MemoryPerformanceStats m = new MemoryPerformanceStats();
        for (String line : readLines("/proc/vmstat")) {
            String[] p = line.split("\\s+");
            if (p.length < 2) continue;
            long v;
            try {
                v = Long.parseLong(p[1]);
            } catch (Exception e) {
                continue;
            }
            switch (p[0]) {
                case "pgfault": m.totalPageFaults = v; break;
                case "pgmajfault": m.majorPageFaults = v; break;
                case "pgpgin": m.pagesPagedIn = v; break;
                case "pgpgout": m.pagesPagedOut = v; break;
                case "pswpin": m.pagesSwappedIn = v; break;
                case "pswpout": m.pagesSwappedOut = v; break;
                default: break;
            }
        }
        return m;
    }

    private static List<ThermalInfo> thermal() {
        List<ThermalInfo> out = new ArrayList<>();
        for (File z : sortedDirs("/sys/class/thermal", "thermal_zone")) {
            String type = read(z.getPath() + "/type");
            String temp = read(z.getPath() + "/temp");
            if (type.isEmpty() || temp.isEmpty()) continue;
            try {
                ThermalInfo t = new ThermalInfo();
                t.sensorType = type;
                t.temperaturesMilliCelsius = Integer.parseInt(temp);
                out.add(t);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static BatteryInfo battery() {
        BatteryInfo b = new BatteryInfo();
        String base = null;
        for (File ps : sortedDirs("/sys/class/power_supply", "")) {
            if ("Battery".equals(read(ps.getPath() + "/type"))) {
                base = ps.getPath();
                break;
            }
        }
        b.manufacturer = "";
        b.modelName = "";
        if (base == null) return b;
        b.manufacturer = read(base + "/manufacturer");
        b.modelName = read(base + "/model_name");
        b.capacity = clampInt(readLong(base + "/capacity", 0));
        b.cycleCount = clampInt(readLong(base + "/cycle_count", 0));
        // sysfs exposes either charge_* (uAh / uA) or energy_* (uWh / uW). The AIDL fields are charge-based, so
        // convert energy to charge with the pack voltage when only energy is available.
        long voltageUv = readLong(base + "/voltage_now", readLong(base + "/voltage_min_design", 0));
        double volts = voltageUv / 1_000_000.0;
        long chargeNow = readLong(base + "/charge_now", -1);
        long chargeFull = readLong(base + "/charge_full", -1);
        long chargeDesign = readLong(base + "/charge_full_design", -1);
        long currentNow = readLong(base + "/current_now", Long.MIN_VALUE);
        if (volts > 0) {
            double designV = readLong(base + "/voltage_min_design", voltageUv) / 1_000_000.0;
            if (designV <= 0) designV = volts;
            if (chargeNow < 0) chargeNow = (long) (readLong(base + "/energy_now", 0) / designV);
            if (chargeFull < 0) chargeFull = (long) (readLong(base + "/energy_full", 0) / designV);
            if (chargeDesign < 0) chargeDesign = (long) (readLong(base + "/energy_full_design", 0) / designV);
            if (currentNow == Long.MIN_VALUE) currentNow = (long) (readLong(base + "/power_now", 0) / volts);
        }
        b.chargeNow = clampInt(Math.max(chargeNow, 0));
        b.chargeFull = clampInt(Math.max(chargeFull, 0));
        b.chargeFullDesign = clampInt(Math.max(chargeDesign, 0));
        b.currentNow = clampInt(currentNow == Long.MIN_VALUE ? 0 : currentNow);
        b.currentAvg = b.currentNow;
        return b;
    }

    private static List<StorageInfo> storage() {
        List<StorageInfo> out = new ArrayList<>();
        for (File n : sortedDirs("/sys/class/nvme", "nvme")) {
            StorageInfo s = new StorageInfo();
            s.storageType = "nvme";
            s.nvmeModel = read(n.getPath() + "/model");
            s.firmwareVersion = read(n.getPath() + "/firmware_rev");
            s.pciVendor = read(n.getPath() + "/device/vendor");
            s.pciDevice = read(n.getPath() + "/device/device");
            s.pciClass = read(n.getPath() + "/device/class");
            s.ufsModel = "";
            s.ufsVendor = "";
            out.add(s);
        }
        return out;
    }

    private static List<BlockDeviceInfo> blockDevices() {
        List<BlockDeviceInfo> out = new ArrayList<>();
        for (File d : sortedDirs("/sys/block", "nvme")) {
            BlockDeviceInfo b = new BlockDeviceInfo();
            b.name = d.getName();
            b.sizeBytes = readLong(d.getPath() + "/size", 0) * 512L;
            b.type = "nvme";
            out.add(b);
        }
        return out;
    }

    private static DiskIo diskIo() {
        DiskIo io = new DiskIo();
        for (String line : readLines("/proc/diskstats")) {
            String[] p = line.trim().split("\\s+");
            // major minor name reads-completed reads-merged sectors-read ms writes-completed writes-merged sectors-written ...
            if (p.length > 9 && p[2].matches("nvme\\d+n\\d+")) {
                try {
                    io.sectorsRead += Long.parseLong(p[5]);
                    io.sectorsWritten += Long.parseLong(p[9]);
                } catch (Exception ignored) {
                }
            }
        }
        return io;
    }

    private static WirelessInfo wireless() {
        WirelessInfo w = new WirelessInfo();
        w.pciClass = "";
        w.pciDevice = "";
        w.pciSubsystem = "";
        w.pciVendor = "";
        File[] devs = new File("/sys/bus/pci/devices").listFiles();
        if (devs != null) {
            Arrays.sort(devs);
            for (File d : devs) {
                if (read(d.getPath() + "/class").startsWith("0x0280")) {
                    w.pciClass = read(d.getPath() + "/class");
                    w.pciDevice = read(d.getPath() + "/device");
                    w.pciVendor = read(d.getPath() + "/vendor");
                    w.pciSubsystem = read(d.getPath() + "/subsystem_device");
                    break;
                }
            }
        }
        return w;
    }

    private static String soundCardName() {
        for (String line : readLines("/proc/asound/cards")) {
            // " 0 [PCH            ]: HDA-Intel - HDA Intel PCH"
            int i = line.indexOf("]:");
            if (i >= 0) {
                String rest = line.substring(i + 2).trim();
                int d = rest.indexOf(" - ");
                return d >= 0 ? rest.substring(d + 3).trim() : rest;
            }
        }
        return "";
    }

    // ---------------------------------------------------------------- the binder

    private final IVendorServiceabilityService.Stub mBinder = new IVendorServiceabilityService.Stub() {
        @Override
        public boolean checkAudioDevice(String name) throws RemoteException {
            return name != null && !soundCardName().isEmpty();
        }

        @Override
        public String generateUfsc() throws RemoteException {
            return "";
        }

        @Override
        public ApFlashChipInfo getApFlashChipInfo() throws RemoteException {
            ApFlashChipInfo a = new ApFlashChipInfo();
            // No SPI flash access from a container; report the BIOS identity from DMI instead.
            a.f0vendor = read("/sys/class/dmi/id/bios_vendor");
            a.name = read("/sys/class/dmi/id/bios_version");
            return a;
        }

        @Override
        public BatteryInfo getBatteryInfo() throws RemoteException {
            return battery();
        }

        @Override
        public List<BlockDeviceInfo> getBlockDevicesInfo() throws RemoteException {
            return blockDevices();
        }

        @Override
        public List<CardReaderInfo> getCardReaderInfo() throws RemoteException {
            return new ArrayList<>();
        }

        @Override
        public String getCpuName() throws RemoteException {
            return cpuName();
        }

        @Override
        public CpuUsageStats getCpuUsageInfo() throws RemoteException {
            return cpuStats();
        }

        @Override
        public CpuUsageStats getCpuUsageStats() throws RemoteException {
            return cpuStats();
        }

        @Override
        public DiskIo getDiskIo() throws RemoteException {
            return diskIo();
        }

        @Override
        public MemoryPerformanceStats getMemoryPerformanceStats() throws RemoteException {
            return memStats();
        }

        @Override
        public List<PhysicalCpuInfo> getPhysicalCpusInfo() throws RemoteException {
            return physicalCpus();
        }

        @Override
        public String getSoundCardName() throws RemoteException {
            return soundCardName();
        }

        @Override
        public List<StorageInfo> getStorageInfo() throws RemoteException {
            return storage();
        }

        @Override
        public List<ThermalInfo> getThermalInfo() throws RemoteException {
            return thermal();
        }

        @Override
        public WirelessInfo getWirelessInfo() throws RemoteException {
            return wireless();
        }

        @Override
        public boolean hasUfscCapability() throws RemoteException {
            return false;
        }

        @Override
        public void validateHardwareDescriptor(String descriptor) throws RemoteException {
            Log.i(TAG, "validateHardwareDescriptor ignored (no hardware descriptor in a container)");
        }
    };
}
