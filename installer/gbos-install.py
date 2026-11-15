#!/usr/bin/env python3
"""GBOS installer driver (bring your own image).

    gbos-install.py check                                  preflight: tools, disk, kernel options, kernel add-ons this machine needs
    gbos-install.py addons  --tree KERNEL_TREE --out DIR   build the kernel add-on modules THIS machine needs (no root, no reboot of a build)
    gbos-install.py fetch   --downloads DIR                download + verify every pinned input (resumable)
    gbos-install.py prepare --downloads DIR --work DIR     extract, patch and assemble the build folder
                            [--prebuilt DIR | --ndk DIR]

`prepare` leaves DIR ready for the recipe scripts (try.sh, bake-apps.sh), which run as root and are started
separately so nothing is changed on the host without you asking for it.

Nothing downloaded or derived is ever written inside this repository. Scratch data goes under --work (disk),
never /tmp: on many systems /tmp is RAM-backed and a multi-GB extraction would exhaust memory.

Python 3.8+, standard library only. External tools: erofs-utils, unzip, patchelf, e2fsprogs, make, gcc.
"""
import argparse
import gzip
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
LOCK = os.path.join(ROOT, "sources.lock")

CORE = ["googlebook_recovery", "waydroid_atv_system", "waydroid_atv_vendor", "chromium_webview"]
TOOLS = ["tool_aapt2", "tool_apksig", "tool_r8", "tool_android_all"]
NVIDIA = ["waydroid_nvidia_host", "waydroid_nvidia_guest"]

# Rough scratch need in GiB (downloads ~9.6, extracted partitions ~8, donor tree ~3, headroom).
NEED_DOWNLOAD_GIB, NEED_WORK_GIB = 10, 25

# CONFIG option, required?, what breaks without it. 'y' or 'm' both count.
KERNEL = [
    ("ANDROID_BINDER_IPC", True, "Android IPC (binder)"),
    ("ANDROID_BINDERFS", True, "Android IPC (binderfs)"),
    ("MAC80211_HWSIM", True, "virtual Wi-Fi"),
    ("INPUT_UINPUT", True, "keyboard / mouse / touch bridge"),
    ("TUN", True, "container networking"),
    ("VETH", True, "container networking"),
    ("BRIDGE", True, "container networking"),
    ("NF_TABLES", True, "container firewall rules"),
    ("DUMMY", False, "virtual network devices"),
    ("IPVLAN", False, "LAN presence (phone and PC on the same network)"),
    ("BT_HCIVHCI", False, "virtual Bluetooth adapter"),
    # Android's netd talks to the LEGACY iptables tables, not nftables. Kernels since 6.12 put them behind *_LEGACY
    # switches, and a trimmed custom config can have the base module but none of the tables.
    ("NETFILTER_XTABLES_LEGACY", True, "legacy iptables core (netd)"),
    ("IP_NF_IPTABLES_LEGACY", True, "legacy iptables, IPv4 (netd)"),
    ("IP6_NF_IPTABLES_LEGACY", True, "legacy iptables, IPv6 (netd)"),
    ("IP_NF_FILTER", True, "iptables filter table, IPv4 (netd)"),
    ("IP_NF_NAT", True, "iptables nat table, IPv4 (netd)"),
    ("IP_NF_MANGLE", True, "iptables mangle table, IPv4 (netd)"),
    ("IP_NF_RAW", True, "iptables raw table, IPv4 (netd)"),
    ("IP6_NF_FILTER", True, "iptables filter table, IPv6 (netd)"),
    ("IP6_NF_NAT", True, "iptables nat table, IPv6 (netd)"),
    ("IP6_NF_MANGLE", True, "iptables mangle table, IPv6 (netd)"),
    ("IP6_NF_RAW", True, "iptables raw table, IPv6 (netd)"),
    ("EROFS_FS", True, "mounting the Googlebook system image"),
    ("BLK_DEV_LOOP", True, "mounting images"),
    ("BPF_SYSCALL", True, "Android networking (eBPF)"),
    ("CGROUP_BPF", True, "Android networking (eBPF)"),
    ("PSI", True, "memory-pressure monitor"),
    ("USERFAULTFD", True, "Android runtime"),
    ("SYSVIPC", True, "shared memory"),
    ("MEMFD_CREATE", False, "shared memory (the syscall is always present on current kernels)"),
]


# ---------------------------------------------------------------- helpers
def die(msg):
    sys.exit("error: " + msg)


def say(msg):
    print(msg, flush=True)


def find_tool(name):
    for d in os.environ.get("PATH", "").split(os.pathsep) + ["/sbin", "/usr/sbin"]:
        p = os.path.join(d, name)
        if os.path.isfile(p) and os.access(p, os.X_OK):
            return p
    return None


def run(cmd, cwd=None, quiet=False):
    """Run a command at low CPU and I/O priority so the desktop stays usable."""
    prefix = ["nice", "-n", "10"]
    if shutil.which("ionice"):
        prefix = ["ionice", "-c3"] + prefix
    r = subprocess.run(prefix + cmd, cwd=cwd, stdout=subprocess.PIPE if quiet else None,
                       stderr=subprocess.STDOUT if quiet else None, text=True)
    if r.returncode != 0:
        die("command failed (%d): %s%s" % (r.returncode, " ".join(cmd), ("\n" + r.stdout) if quiet and r.stdout else ""))
    return r


def parse_lock():
    entries = {}
    with open(LOCK) as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = [p.strip() for p in line.split("|")]
            if len(parts) < 5:
                die("malformed line in sources.lock: %r" % line)
            name, url, size, sha, note = parts[0], parts[1], int(parts[2]), parts[3], parts[4]
            entries[name] = dict(name=name, url=url, size=size, sha=sha, note=note, file=os.path.basename(url))
    return entries


def sha256_file(path, progress=None):
    h = hashlib.sha256()
    done = 0
    t0 = time.time()
    with open(path, "rb") as f:
        for blk in iter(lambda: f.read(8 << 20), b""):
            h.update(blk)
            done += len(blk)
            if progress and time.time() - t0 > 2:
                t0 = time.time()
                sys.stderr.write("\r  hashing %s  %5.1f GiB" % (progress, done / 2**30))
                sys.stderr.flush()
    if progress:
        sys.stderr.write("\r" + " " * 60 + "\r")
    return h.hexdigest()


def verified(path, entry):
    """True if PATH matches the pinned size and sha256. A sidecar remembers a past success for the same
    size+mtime so a 7.8 GB file is not re-hashed on every run."""
    if not os.path.isfile(path) or os.path.getsize(path) != entry["size"]:
        return False
    st = os.stat(path)
    side = path + ".verified"
    try:
        with open(side) as f:
            j = json.load(f)
        if j.get("sha256") == entry["sha"] and j.get("size") == st.st_size and j.get("mtime_ns") == st.st_mtime_ns:
            return True
    except (OSError, ValueError):
        pass
    if sha256_file(path, entry["file"]) != entry["sha"]:
        return False
    try:
        with open(side, "w") as f:
            json.dump({"sha256": entry["sha"], "size": st.st_size, "mtime_ns": st.st_mtime_ns}, f)
    except OSError:
        pass
    return True


# ---------------------------------------------------------------- check
def kernel_options():
    """Return {OPTION: 'y'|'m'|None} for the running kernel, or None if its config is unavailable."""
    cands = ["/proc/config.gz", "/boot/config-" + os.uname().release]
    for c in cands:
        if os.path.exists(c):
            op = gzip.open if c.endswith(".gz") else open
            try:
                with op(c, "rt") as f:
                    out = {}
                    for line in f:
                        if line.startswith("CONFIG_") and "=" in line:
                            k, v = line.strip().split("=", 1)
                            out[k[7:]] = v
                    return out
            except OSError:
                continue
    return None


# CPU instruction sets the Googlebook userland needs. The image is compiled for a recent CPU baseline and uses these
# unconditionally in boot-critical binaries (disassembled: linker64 24 GFNI / 38 VAES / 16 AVX-VNNI, init 27/38/14,
# surfaceflinger 44 GFNI / 28 VNNI, libcrypto 81 GFNI / 1215 VAES / 128 SHA-NI). On a CPU without them Android's very
# first process dies with SIGILL after ~0.3 s. The flag names are those of /proc/cpuinfo. Present on the machine this
# was developed on (Intel Core i5-13420H, 13th gen); absent on an Intel Core i5-8200Y (8th gen), where it fails.
CPU_NEED = [
    ("gfni", "Galois-field instructions (linker64, init, surfaceflinger, libcrypto ...)"),
    ("vaes", "256-bit AES (linker64, init, libcrypto)"),
    ("vpclmulqdq", "256-bit carry-less multiply"),
    ("avx_vnni", "AVX-VNNI dot products (linker64, init, surfaceflinger, libhwui)"),
    ("sha_ni", "SHA extensions (libcrypto)"),
    ("avx2", "AVX2"), ("bmi2", "BMI2"), ("fma", "FMA"), ("aes", "AES-NI"), ("pclmulqdq", "PCLMULQDQ"), ("adx", "ADX"),
]


def cpu_flags():
    try:
        with open("/proc/cpuinfo") as f:
            for line in f:
                if line.startswith("flags"):
                    return set(line.split(":", 1)[1].split())
    except OSError:
        pass
    return None


BOOL_OPTIONS = {"ANDROID_BINDER_IPC", "ANDROID_BINDERFS", "BPF_SYSCALL", "CGROUP_BPF", "PSI", "USERFAULTFD", "SYSVIPC",
                "MEMFD_CREATE", "NETFILTER_XTABLES_LEGACY"}      # yes/no options; everything else is a tristate (m by default)


def cmd_kernel_fragment(a):
    """Print the kernel config fragment with every option GBOS needs. Merge it into your own kernel's .config
    (scripts/kconfig/merge_config.sh), run `make olddefconfig`, rebuild, install, reboot."""
    have = {}
    if a.base:
        try:
            with open(a.base) as f:
                for line in f:
                    if line.startswith("CONFIG_") and "=" in line:
                        k, v = line.strip().split("=", 1)
                        have[k[7:]] = v
        except OSError as e:
            die("cannot read --base %s: %s" % (a.base, e))
    say("# GBOS kernel requirements (generated by `gbos-install.py kernel-fragment`; the same list `check` tests).")
    if a.base:
        say("# Only what %s lacks: options it already has as y or m are left alone (a fragment must never downgrade =y to =m)." % os.path.basename(a.base))
    n = 0
    for name, required, why in KERNEL:
        if not (required or a.all) or have.get(name) in ("y", "m"):
            continue
        n += 1
        say("CONFIG_%s=%s    # %s%s" % (name, "y" if name in BOOL_OPTIONS or a.builtin else "m", why, "" if required else " (optional)"))
    if a.base and not n:
        say("# nothing to add: %s already satisfies every option" % os.path.basename(a.base))
    return 0


# ---------------------------------------------------------------- kernel add-ons
# Three loadable-module add-ons let GBOS run on a kernel/CPU it was not built for, without rebuilding the kernel. They were
# needed on a second machine (a Pixel Slate); see docs/UNSUPPORTED-HARDWARE.md. Each is detected from what the running kernel
# reports, so `check` can say which apply and `addons` can build exactly those.
KERNEL_DIR = os.path.join(ROOT, "kernel")
CPU_EMULATED = ("gfni", "vaes", "vpclmulqdq", "avx_vnni", "sha_ni")
LEGACY_NET = ["NETFILTER_XTABLES_LEGACY", "IP_NF_IPTABLES_LEGACY", "IP6_NF_IPTABLES_LEGACY", "IP_NF_FILTER", "IP_NF_NAT", "IP_NF_MANGLE",
              "IP_NF_RAW", "IP6_NF_FILTER", "IP6_NF_NAT", "IP6_NF_MANGLE", "IP6_NF_RAW"]


def module_loaded(name):
    return os.path.isdir("/sys/module/" + name)


def lsm_list():
    try:
        with open("/sys/kernel/security/lsm") as f:
            return f.read().strip().split(",")
    except OSError:
        return None


def kallsyms_has(name):
    """True/False whether the running kernel has this symbol (names are visible without root; addresses are not). None = cannot tell."""
    try:
        with open("/proc/kallsyms") as f:
            for line in f:
                if line.rstrip().endswith(" " + name):
                    return True
        return False
    except OSError:
        return None


def addons_plan(opts):
    """[(key, script, why, prerequisites_problem)] for the add-ons this machine needs and does not have yet."""
    plan = []
    flags = cpu_flags()
    if flags is not None:
        miss = [f for f in CPU_EMULATED if f not in flags]
        if miss and not module_loaded("gbos_vexemu"):
            prob = ""
            if opts is not None:
                if opts.get("MODULES") != "y":
                    prob = "the kernel has no module support"
                elif opts.get("PREEMPT_RCU") != "y":
                    prob = "the module needs CONFIG_PREEMPT_RCU (it sleeps on user page faults inside the trap handler)"
                elif opts.get("MODULE_SIG_FORCE") == "y":
                    prob = "the kernel only loads signed modules"
            plan.append(("vexemu", "gbos-vexemu.py", "this CPU lacks %s; Android's first process dies with SIGILL without emulation" % ", ".join(miss), prob))
    lsm = lsm_list()
    if lsm is not None and "apparmor" in lsm and "selinux" not in lsm and not module_loaded("gbos_secctx"):
        fixed = kallsyms_has("apparmor_cred_getsecid")
        if fixed is False:
            prob = ""
            if opts is not None and opts.get("KPROBES") != "y":
                prob = "the module needs CONFIG_KPROBES; apply kernel/patches/apparmor-cred-getsecid.patch and rebuild instead"
            plan.append(("secctx", "gbos-secctx.py", "AppArmor is the only security module and this kernel cannot give binder a caller security context; "
                         "Android's odsign then aborts in a loop and boot never gets past the boot animation", prob))
    if opts is not None:
        addon_dir = "/lib/modules/%s/updates/gbos-netfilter" % os.uname().release
        if not os.path.isdir(addon_dir) and any(opts.get(n) not in ("y", "m") for n in LEGACY_NET):
            prob = "x_tables is built into this kernel, so only a rebuild works (kernel-fragment)" if opts.get("NETFILTER_XTABLES") == "y" else ""
            plan.append(("netfilter", "gbos-netfilter-legacy.py", "the legacy iptables tables Android's netd needs are missing from this kernel", prob))
    return plan


def cmd_addons(a):
    """Build the kernel add-on modules this machine needs (the build is unprivileged); print the root commands to install them."""
    opts = kernel_options()
    plan = addons_plan(opts)
    if not plan:
        say("nothing to add: this machine's CPU, security modules and netfilter tables need no GBOS kernel add-on "
            "(or they are already installed and loaded).")
        return 0
    say("add-ons this machine needs (%s):" % os.uname().release)
    for key, script, why, prob in plan:
        say("  %-10s %s" % (key, why))
        if prob:
            say("             CANNOT be built as a module here: %s" % prob)
    todo = [p for p in plan if not p[3]]
    if not a.build:
        say("")
        say("to build them (no root needed; --tree is the tree or linux-headers your RUNNING kernel was built from):")
        say("    gbos-install.py addons --tree /path/to/kernel/tree --out ~/gbos/addons --build")
        return 0
    if not a.tree or not a.out:
        die("--build needs --tree and --out")
    out = os.path.abspath(a.out)
    if out.startswith(ROOT + os.sep):
        die("--out must be outside the repository")
    built = []
    for key, script, why, prob in todo:
        dest = os.path.join(out, key)
        cmd = [sys.executable, os.path.join(KERNEL_DIR, script), "build", "--tree", os.path.abspath(a.tree), "--out", dest]
        if a.allow_other:
            cmd.append("--allow-other")
        say("")
        say("== building %s" % key)
        r = subprocess.run(cmd)
        if r.returncode == 0:
            built.append((key, script, dest))
        else:
            say("   %s did not build (see the messages above)" % key)
    if built:
        say("")
        say("built and self-verified. Install them with root (each verifies again against the running kernel and refuses a mismatch):")
        for key, script, dest in built:
            say("    sudo %s install --out %s%s" % (os.path.join(KERNEL_DIR, script), dest,
                                                     "      # then REBOOT: x_tables is in use" if key == "netfilter" else ""))
        say("")
        say("then run `gbos-install.py check` again. Order does not matter, except that the netfilter add-on needs its reboot before Waydroid starts.")
    return 0 if len(built) == len(todo) and not [p for p in plan if p[3]] else 1


def cmd_check(a):
    bad = 0
    say("machine:")
    import platform
    arch = platform.machine()
    if arch in ("x86_64", "AMD64"):
        say("  cpu architecture   %s  ok" % arch)
    else:
        say("  cpu architecture   %s  FAIL: the Googlebook image is x86-64 Android and cannot run on this CPU" % arch)
        bad += 1
    flags = cpu_flags()
    if flags is None:
        say("  cpu features       unknown (cannot read /proc/cpuinfo)")
    else:
        missing = [(f, why) for f, why in CPU_NEED if f not in flags]
        if not missing:
            say("  cpu features       ok (%s)" % " ".join(f for f, _ in CPU_NEED[:5]))
        elif os.path.isdir("/sys/module/gbos_vexemu"):
            say("  cpu features       emulated: this CPU lacks %s; the gbos_vexemu module is loaded (slow, see kernel/README.md)"
                % ", ".join(f for f, _ in missing))
        else:
            level = "WARNING (--ignore-cpu)" if a.ignore_cpu else "FAIL"
            say("  cpu features       %s: this CPU lacks %s" % (level, ", ".join(f for f, _ in missing)))
            say("                     The Googlebook image is built for a recent CPU and uses these instructions in its first")
            say("                     boot processes; without them Android dies within a second (SIGILL) and nothing is visible.")
            say("                     Intel 12th gen (Alder Lake) or newer is what it has been run on. See docs/HARDWARE.md.")
            say("                     Otherwise the instructions can be emulated by a loadable module, no kernel rebuild:")
            say("                     kernel/gbos-vexemu.py (slow; QEMU-tested, see kernel/README.md).")
            if not a.ignore_cpu:
                bad += 1
    try:
        with open("/proc/meminfo") as f:
            kb = int(next(l for l in f if l.startswith("MemTotal:")).split()[1])
        gib = kb / 2**20
        if gib >= 7:
            say("  memory             %.1f GiB  ok (developed on 7.6)" % gib)
        else:
            say("  memory             %.1f GiB  WARNING: developed on 7.6 GiB, which was already tight; Android's memory "
                "killer will be aggressive and Phone Hub may be unusable" % gib)
    except (OSError, StopIteration, ValueError):
        say("  memory             unknown")
    say("  /dev/kvm           %s" % ("present" if os.path.exists("/dev/kvm") else "absent (only the Linux Terminal VM needs it)"))
    say("tools:")
    for t, why in [("fsck.erofs", "erofs-utils"), ("dump.erofs", "erofs-utils"), ("unzip", "unzip"),
                   ("patchelf", "patchelf"), ("debugfs", "e2fsprogs"), ("e2fsck", "e2fsprogs"),
                   ("resize2fs", "e2fsprogs"), ("make", "make"), ("gcc", "gcc"), ("javac", "default-jdk-headless"),
                   ("keytool", "default-jdk-headless"), ("waydroid", "waydroid"),
                   ("adb", "adb; older releases: android-tools-adb (needed for the Phone Hub part)")]:
        p = find_tool(t)
        say("  %-10s %s" % (t, p or "MISSING  (package: %s)" % why))
        if not p and t not in ("adb",):
            bad += 1
    for d, gib in [(a.downloads, NEED_DOWNLOAD_GIB), (a.work, NEED_WORK_GIB)]:
        if d:
            probe = d
            while probe and not os.path.exists(probe):
                probe = os.path.dirname(probe)
            free = shutil.disk_usage(probe or "/").free / 2**30
            ok = free >= gib
            say("disk: %s has %.0f GiB free (need about %d)%s" % (d, free, gib, "" if ok else "  TOO LITTLE"))
            bad += 0 if ok else 1
    for d in (a.downloads, a.work):
        if d:
            probe = d
            while probe and not os.path.exists(probe):
                probe = os.path.dirname(probe)
            r = subprocess.run(["findmnt", "-no", "FSTYPE", "-T", probe or "/"], stdout=subprocess.PIPE, text=True)
            if r.stdout.strip() in ("tmpfs", "ramfs"):
                say("  %s is on %s, i.e. RAM. Pick a directory on disk." % (d, r.stdout.strip()))
                bad += 1
    say("kernel %s:" % os.uname().release)
    opts = kernel_options()
    if opts is None:
        say("  cannot read /proc/config.gz or /boot/config-*; cannot verify (install the kernel config or load the 'configs' module)")
    else:
        # The legacy iptables tables can come from the module add-on (kernel/gbos-netfilter-legacy.py) instead of the config.
        addon_dir = "/lib/modules/%s/updates/gbos-netfilter" % os.uname().release
        addon = {"NETFILTER_XTABLES_LEGACY": "x_tables", "IP_NF_IPTABLES_LEGACY": "ip_tables", "IP6_NF_IPTABLES_LEGACY": "ip6_tables",
                 "IP_NF_FILTER": "iptable_filter", "IP_NF_NAT": "iptable_nat", "IP_NF_MANGLE": "iptable_mangle", "IP_NF_RAW": "iptable_raw",
                 "IP6_NF_FILTER": "ip6table_filter", "IP6_NF_NAT": "ip6table_nat", "IP6_NF_MANGLE": "ip6table_mangle", "IP6_NF_RAW": "ip6table_raw"}
        satisfied = set()
        for name, required, why in KERNEL:
            v = opts.get(name)
            ok = v in ("y", "m")
            if not ok and name in addon and os.path.isfile(os.path.join(addon_dir, addon[name] + ".ko")):
                v, ok, why = "add-on", True, why + " (from the module add-on)"
            if ok:
                satisfied.add(name)
            tag = "ok" if ok else ("MISSING" if required else "missing (optional)")
            say("  %-18s %-3s %s  %s" % (name, v or "-", tag, why))
            if required and not ok:
                bad += 1
        say("  note: options set to 'm' need their module to load (modprobe binder_linux, mac80211_hwsim, uinput ...).")
        legacy = [n for n, req, _ in KERNEL if req and ("IPTABLES_LEGACY" in n or n.startswith(("IP_NF_", "IP6_NF_")) or n == "NETFILTER_XTABLES_LEGACY")
                  and n not in satisfied]
        if legacy:
            say("")
            say("  The legacy iptables tables Android's netd needs are missing from this kernel (%d options). Either:" % len(legacy))
            say("    a) rebuild the kernel:  gbos-install.py kernel-fragment --base <your .config>   (prints exactly what to add), or")
            say("    b) keep your kernel and add them as modules:  kernel/gbos-netfilter-legacy.py build --tree <the tree it was built in>")
            say("       (see kernel/README.md; works when x_tables is a module and you still have the build tree)")
    say("")
    say("kernel add-ons (loadable modules; no kernel rebuild):")
    plan = addons_plan(opts)
    lsm = lsm_list()
    say("  security modules   %s" % (",".join(lsm) if lsm else "unknown"))
    if lsm is not None and "apparmor" in lsm and "selinux" not in lsm:
        if module_loaded("gbos_secctx"):
            say("  binder context     ok: AppArmor-only kernel, gbos_secctx module loaded")
        elif kallsyms_has("apparmor_cred_getsecid"):
            say("  binder context     ok: this kernel has the AppArmor cred_getsecid fix")
        else:
            say("  binder context     WARNING: AppArmor is the only security module and the kernel lacks the cred_getsecid fix,")
            say("                     so binder cannot attach a caller security context: Android 17's odsign aborts in a loop and the")
            say("                     boot stops at the boot animation. Fix: kernel/gbos-secctx.py (module) or kernel/patches/")
            say("                     apparmor-cred-getsecid.patch (kernel rebuild). See docs/FIX-CATALOG.md entry 23.")
    elif lsm is not None:
        say("  binder context     ok: SELinux is active, which provides it")
    if plan:
        say("  needed here:       %s" % ", ".join(p[0] for p in plan))
        say("                     build them in one go:  gbos-install.py addons --tree <your kernel's build tree> --out ~/gbos/addons --build")
    elif opts is not None:
        say("  needed here:       none (or already installed)")
    home = os.path.expanduser("~" + os.environ.get("SUDO_USER", "")) if os.environ.get("SUDO_USER") else os.path.expanduser("~")
    wd = os.path.join(home, ".local/share/waydroid/data")
    if os.path.isdir(wd):
        say("  waydroid data      %s exists: try.sh will MOVE it aside (data.before-gbos-<time>, not deleted); the new Android must start on clean"
            % wd)
        say("                     data, or it keeps the old Android's user layout and shows only a bare wallpaper (docs/FIX-CATALOG.md entry 25).")
    say("")
    say("optional hybrid-GPU mode (Intel desktop + NVIDIA Vulkan): not part of the normal install; to see whether this")
    say("machine could use it, run  gpu/gbos-gpu detect")
    say("")
    say("RESULT: %s" % ("ready" if not bad else "%d problem(s) above" % bad))
    return 1 if bad else 0


# ---------------------------------------------------------------- fetch
def download(entry, dest_dir):
    os.makedirs(dest_dir, exist_ok=True)
    dest = os.path.join(dest_dir, entry["file"])
    if verified(dest, entry):
        say("have   %-34s ok" % entry["file"])
        return dest
    part = dest + ".part"
    for attempt in range(1, 9):
        have = os.path.getsize(part) if os.path.exists(part) else 0
        if have > entry["size"]:
            os.unlink(part)
            have = 0
        if have < entry["size"]:
            req = urllib.request.Request(entry["url"], headers={"User-Agent": "gbos-installer"})
            if have:
                req.add_header("Range", "bytes=%d-" % have)
            try:
                with urllib.request.urlopen(req, timeout=60) as r:
                    if have and r.status != 206:
                        have = 0                              # server ignored Range: start over
                    mode = "ab" if have else "wb"
                    t0, b0 = time.time(), have
                    with open(part, mode) as f:
                        while True:
                            chunk = r.read(1 << 20)
                            if not chunk:
                                break
                            f.write(chunk)
                            have += len(chunk)
                            if time.time() - t0 > 1:
                                rate = (have - b0) / (time.time() - t0) / 2**20
                                sys.stderr.write("\r  %-34s %5.1f%% of %.2f GiB  %.1f MiB/s " % (
                                    entry["file"], 100.0 * have / entry["size"], entry["size"] / 2**30, rate))
                                sys.stderr.flush()
                                t0, b0 = time.time(), have
                    sys.stderr.write("\r" + " " * 78 + "\r")
            except (urllib.error.URLError, OSError, TimeoutError) as e:
                wait = min(60, 2 ** attempt)
                say("  %s: %s; retry %d/8 in %ds" % (entry["file"], e, attempt, wait))
                time.sleep(wait)
                continue
        if os.path.getsize(part) == entry["size"]:
            break
    if not os.path.exists(part) or os.path.getsize(part) != entry["size"]:
        die("%s: download incomplete; run fetch again to resume" % entry["file"])
    got = sha256_file(part, entry["file"])
    if got != entry["sha"]:
        os.unlink(part)
        die("%s: sha256 %s does not match the pinned %s. The file was deleted; the source may have changed." %
            (entry["file"], got, entry["sha"]))
    os.replace(part, dest)
    verified(dest, entry)                                     # writes the sidecar
    say("fetched %-34s ok" % entry["file"])
    return dest


def cmd_fetch(a):
    lock = parse_lock()
    names = CORE + TOOLS + (NVIDIA if a.with_nvidia else []) + (["android_ndk_r27c"] if a.with_ndk else [])
    for n in names:
        if n not in lock:
            die("sources.lock has no entry %r" % n)
        if lock[n]["sha"] == "TODO":
            die("sources.lock entry %r has no pinned hash yet" % n)
    for n in names:
        download(lock[n], a.downloads)
    say("all inputs present and verified")
    return 0


# ---------------------------------------------------------------- prepare
def have_file(downloads, lock, name):
    e = lock[name]
    p = os.path.join(downloads, e["file"])
    if not verified(p, e):
        die("%s is missing or does not match sources.lock; run: gbos-install.py fetch --downloads %s" % (e["file"], downloads))
    return p


class Stamps:
    """Marks finished stages so a re-run resumes instead of redoing hours of extraction."""

    def __init__(self, work):
        self.dir = os.path.join(work, ".gbos-stamps")
        os.makedirs(self.dir, exist_ok=True)

    def done(self, stage, key, outputs):
        try:
            with open(os.path.join(self.dir, stage)) as f:
                return f.read() == key and all(os.path.exists(o) for o in outputs)
        except OSError:
            return False

    def mark(self, stage, key):
        with open(os.path.join(self.dir, stage), "w") as f:
            f.write(key)


def unzip_member(zpath, member, dest):
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    tmp = dest + ".part"
    with zipfile.ZipFile(zpath) as z, z.open(member) as src, open(tmp, "wb") as out:
        shutil.copyfileobj(src, out, 8 << 20)
    os.replace(tmp, dest)


def cat_erofs(image, path, dest):
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "wb") as out:
        r = subprocess.run(["dump.erofs", "--cat", "--path=" + path, image], stdout=out, stderr=subprocess.PIPE)
    if r.returncode != 0 or os.path.getsize(dest) == 0:
        die("could not read %s from %s: %s" % (path, image, r.stderr.decode(errors="replace").strip()))


def copy_tree(src, dst):
    """Copy files of SRC into DST (dirs merged), never following links out of the tree."""
    for dp, dns, fns in os.walk(src):
        rel = os.path.relpath(dp, src)
        out = dst if rel == "." else os.path.join(dst, rel)
        os.makedirs(out, exist_ok=True)
        for fn in fns:
            shutil.copy2(os.path.join(dp, fn), os.path.join(out, fn))


NATIVE_NDK = ["input-bridge", "vpd-stub"]
NATIVE_HOST = {"unlock-signin": "unlock-signin", "eval-js": "eval-js",
               "gb-dhcpd": "fakeap/gb-dhcpd", "gb-portrelay": "portrelay/gb-portrelay"}


PREBUILT_LOCK = os.path.join(ROOT, "prebuilt.sha256")


def load_prebuilt_lock():
    """{name: {sha, size, note}} from prebuilt.sha256 (written by tools/make-release.sh), or {} if absent."""
    out = {}
    try:
        with open(PREBUILT_LOCK) as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#"):
                    sha, size, name, *note = line.split(None, 3)
                    out[name] = {"sha": sha, "size": int(size), "note": note[0] if note else ""}
    except OSError:
        pass
    return out


def check_prebuilt(path, name, pre, strict):
    """Compare a binary with the checksum the repository publishes for it."""
    if name not in pre:
        if strict:
            die("prebuilt.sha256 has no entry for %s; refusing an unverified binary" % name)
        return
    got = sha256_file(path)
    if got == pre[name]["sha"]:
        say("ok     %-18s matches the published checksum" % name)
    elif strict:
        die("%s (%s) does not match prebuilt.sha256 (%s). Rebuild it with --ndk, or fetch the release asset again." % (
            path, got[:16], pre[name]["sha"][:16]))
    else:
        say("note   %-18s differs from the published asset (%s vs %s): a different NDK, or a different source?" % (
            name, got[:12], pre[name]["sha"][:12]))


def cmd_fetch_prebuilt(a):
    """Download the prebuilt Android binaries from a release and verify them against prebuilt.sha256."""
    pre = load_prebuilt_lock()
    if not pre:
        die("prebuilt.sha256 is missing or empty; this checkout does not name any release assets")
    base = a.url.rstrip("/")
    for name, e in pre.items():
        if name == "vulkan.hybrid64.so" and not a.with_gpu_shim:
            continue
        download(dict(file=name, url=base + "/" + name, size=e["size"], sha=e["sha"]), a.dest)
    say("prebuilt binaries verified in %s" % a.dest)
    say("use them with:  gbos-install.py prepare --downloads D --work W --prebuilt %s" % a.dest)
    return 0


def stage_native(a, W):
    out = os.path.join(W, ".native")
    pre = load_prebuilt_lock()
    if a.ndk == "auto":
        zpath = have_file(os.path.abspath(a.downloads), parse_lock(), "android_ndk_r27c")
        tc = os.path.join(W, ".toolchain")
        a.ndk = os.path.join(tc, "android-ndk-r27c")
        if not os.path.isdir(a.ndk):
            say("unpack the NDK")
            os.makedirs(tc, exist_ok=True)
            run(["unzip", "-q", zpath, "-d", tc])
    if not shutil.which("make") or not shutil.which("gcc"):
        die("make and gcc are needed to build the native helpers")
    if a.prebuilt:
        # Only the two Android binaries need the NDK; the host helpers are plain C and build with gcc.
        for n in NATIVE_NDK:
            p = os.path.join(a.prebuilt, n)
            if not os.path.isfile(p):
                die("--prebuilt %s lacks %s" % (a.prebuilt, n))
            check_prebuilt(p, n, pre, strict=True)
        run(["make", "-C", os.path.join(ROOT, "native"), "OUT=" + out, "host"])
        ndk_src = a.prebuilt
    elif a.ndk:
        run(["make", "-C", os.path.join(ROOT, "native"), "OUT=" + out, "NDK=" + a.ndk, "all"])
        ndk_src = out
        for n in NATIVE_NDK:
            check_prebuilt(os.path.join(out, n), n, pre, strict=False)
    else:
        die("input-bridge and vpd-stub run on Android and need either the NDK (--ndk DIR|auto) or the prebuilt "
            "release assets (--prebuilt DIR, from `gbos-install.py fetch-prebuilt`).")
    for n in NATIVE_NDK:
        shutil.copy2(os.path.join(ndk_src, n), os.path.join(W, n))
    for n, rel in NATIVE_HOST.items():
        os.makedirs(os.path.dirname(os.path.join(W, rel)), exist_ok=True)
        shutil.copy2(os.path.join(out, n), os.path.join(W, rel))
    for n in NATIVE_NDK + list(NATIVE_HOST.values()):
        os.chmod(os.path.join(W, n), 0o755)


# App build order matters: svc_stub_apk creates the signing key that the others sign with.
APPS = ["svc_stub_apk", "audioctl_app", "bthal_app", "ethcfg_app", "fakewifi_app", "lan_wifi_overlay",
        "dbexec_app", "userrestrict_app", "wallset_app"]           # the last three are run by host/provision/gb-userfixes.sh
APPS_PHONE_HUB = ["gbcast_app", "gbhub_widget", "touch_injector"]
APK_OUT = {"svc_stub_apk": "serviceability-stub.apk", "audioctl_app": "audioctl.apk", "bthal_app": "bthal.apk",
           "ethcfg_app": "ethcfg.apk", "fakewifi_app": "fakewifi.apk", "lan_wifi_overlay": "lan-wifi-overlay.apk",
           "dbexec_app": "dbexec.apk", "userrestrict_app": "userrestrict.apk", "wallset_app": "wallset.apk"}


def stage_apps(a, W, D, lock, st):
    """Lay out the toolchain where the apps' build.sh expect it and build + sign the system apps."""
    if not shutil.which("javac") or not shutil.which("keytool"):
        die("a JDK (javac, keytool) is needed to build the apps: install default-jdk-headless")
    tools = os.path.join(W, "svc_analysis", "tools")
    sp = os.path.join(W, "storprobe")
    os.makedirs(tools, exist_ok=True)
    os.makedirs(sp, exist_ok=True)
    jar = lambda n: have_file(D, lock, n)
    # aapt2 is inside its Maven jar
    aapt2 = os.path.join(tools, "aapt2")
    if not os.path.exists(aapt2):
        with zipfile.ZipFile(jar("tool_aapt2")) as z, z.open("aapt2") as src, open(aapt2, "wb") as out:
            shutil.copyfileobj(src, out)
        os.chmod(aapt2, 0o755)
    for name, dst in [("tool_apksig", os.path.join(tools, "apksig-8.13.2.jar")),
                      ("tool_r8", os.path.join(sp, "r8-8.5.35.jar")),
                      ("tool_android_all", os.path.join(sp, "android-all-16-robolectric-13921718.jar"))]:
        if not os.path.exists(dst):
            shutil.copyfile(jar(name), dst)
    # framework-res.apk comes from the user's own image, not from a download
    fr = os.path.join(tools, "framework-res.apk")
    if not os.path.exists(fr):
        cat_erofs(os.path.join(W, "system_a.img"), "/system/framework/framework-res.apk", fr)
    todo = list(APPS) + (APPS_PHONE_HUB if getattr(a, "phone_hub", False) else [])
    for app in todo:
        d = os.path.join(W, app)
        out = APK_OUT.get(app)
        if out and os.path.exists(os.path.join(d, "build", out)):
            say("skip   %s (built)" % app)
            continue
        say("build  %s" % app)
        run(["bash", "build.sh"], cwd=d, quiet=True)
        if out and not os.path.exists(os.path.join(d, "build", out)):
            die("%s did not produce build/%s" % (app, out))
    key = os.path.join(W, "svc_stub_apk", "stub.p12")
    say("signing key: %s  (keep it: apps you rebuild later must be signed with the same key)" % key)


def cmd_prepare(a):
    for t in ("fsck.erofs", "dump.erofs", "unzip", "patchelf"):
        if not find_tool(t):
            die("%s not found; run: gbos-install.py check" % t)
    if not find_tool("debugfs"):
        die("debugfs (e2fsprogs) not found; run: gbos-install.py check")
    lock = parse_lock()
    W = os.path.abspath(a.work)
    D = os.path.abspath(a.downloads)
    os.makedirs(W, exist_ok=True)
    st = Stamps(W)

    rec = have_file(D, lock, "googlebook_recovery")
    atv_sys = have_file(D, lock, "waydroid_atv_system")
    atv_ven = have_file(D, lock, "waydroid_atv_vendor")
    wv = have_file(D, lock, "chromium_webview")
    say("inputs verified")

    # 1) the Googlebook partitions
    parts = ["system_a", "system_ext_a", "product_a"]
    outs = [os.path.join(W, p + ".img") for p in parts]
    key = lock["googlebook_recovery"]["sha"]
    if st.done("recovery", key, outs):
        say("skip   recovery partitions (already extracted)")
    else:
        say("extract recovery partitions (one pass over the 19 GB disk image; several minutes)")
        run([sys.executable, os.path.join(HERE, "extract_recovery.py"), rec, "--extract"] + parts + ["--out", W])
        st.mark("recovery", key)

    # 2) the Android 16 donor, Lineage vendor image and the upstream WebView
    atv16 = os.path.join(W, "atv16", "system.img")
    key = lock["waydroid_atv_system"]["sha"]
    if st.done("atv-system", key, [atv16]):
        say("skip   ATV system.img")
    else:
        say("unpack ATV Android 16 system.img")
        unzip_member(atv_sys, "system.img", atv16)
        st.mark("atv-system", key)
    ven = os.path.join(W, "vendor.img")
    key = lock["waydroid_atv_vendor"]["sha"]
    if st.done("atv-vendor", key, [ven]):
        say("skip   vendor.img")
    else:
        say("unpack Lineage vendor.img")
        unzip_member(atv_ven, "vendor.img", ven)
        st.mark("atv-vendor", key)
    wvapk = os.path.join(W, "wv_test", "chrome-android-desktop", "apks", "SystemWebView.apk")
    key = lock["chromium_webview"]["sha"]
    if st.done("webview", key, [wvapk]):
        say("skip   SystemWebView.apk")
    else:
        say("unpack SystemWebView.apk")
        unzip_member(wv, "chrome-android-desktop/apks/SystemWebView.apk", wvapk)
        st.mark("webview", key)

    # 3) recipe, app sources and native helpers into the flat work folder the scripts expect
    say("copy recipe and app sources")
    copy_tree(os.path.join(ROOT, "recipe"), W)
    copy_tree(os.path.join(ROOT, "android"), W)
    stage_native(a, W)
    for tool in ("gbos-backup.py", "gbos-doctor.py"):          # next to try.sh, so the destructive step can use them
        shutil.copy2(os.path.join(HERE, tool), os.path.join(W, tool))
        os.chmod(os.path.join(W, tool), 0o755)

    # 4) patched libraries and binaries derived from the user's own system_a
    run([sys.executable, os.path.join(HERE, "derive_patched.py"), "--system-a", os.path.join(W, "system_a.img"),
         "--out", W, "--recipe", os.path.join(ROOT, "recipe")])
    # bake-apps.sh applies its own in-line patch on top of libselinux-gb.so (see recipe/PATCHES.md)
    shutil.copyfile(os.path.join(W, "libselinux-gb.so"), os.path.join(W, "libselinux.orig.so"))

    # 5) the Waydroid task HAL, taken from the same ATV image
    dt = os.path.join(W, "donor-task")
    for img_path, name in [("/system/bin/hw/vendor.waydroid.task@1.0-service", "vendor.waydroid.task@1.0-service"),
                           ("/system/etc/init/vendor.waydroid.task@1.0-service.rc", "vendor.waydroid.task@1.0-service.rc"),
                           ("/system/system_ext/lib64/vendor.waydroid.task@1.0.so", "vendor.waydroid.task@1.0.so")]:
        cat_erofs(atv16, img_path, os.path.join(dt, name))

    # 6) the library closure of the donor binaries
    if os.path.isdir(os.path.join(W, "donor-a16", "lib")) and st.done("donor", "1", [os.path.join(W, "donor-a16")]):
        say("skip   donor-a16")
    else:
        say("build donor-a16 (extracts the ATV image; a few GB on disk)")
        run(["bash", "prep-donor.sh"], cwd=W)
        st.mark("donor", "1")

    # 7) the system apps
    stage_apps(a, W, D, lock, st)

    say("")
    say("work folder ready: %s" % W)
    say("next (as root, from that folder):  sudo ./try.sh   then   sudo ./bake-apps.sh")
    say("then the host services:  host/install-host.sh phonehub --touch-jar %s/touch_injector/build/gb-touch.jar" % W)
    say("                          sudo host/install-host.sh system lan wifi")
    return 0


# ---------------------------------------------------------------- optional: hybrid GPU stage directory
# sha256 of the patched waydroid-init that results from patch-waydroid-init-vulkan.py on the pinned vendor image
WAYDROID_INIT_PATCHED_SHA256 = "5aeda6b29fe44eaebde001a32b1e2a05aa56011dc24fb8fcd9e4ce6bb96c5efe"


def cmd_gpu_prepare(a):
    """Assemble the directory `gbos-gpu enable --stage-dir` installs from. Entirely optional: nothing else needs it."""
    for t in ("zstd", "tar", "debugfs"):
        if not find_tool(t):
            die("%s not found (needed to unpack the optional GPU module's inputs)" % t)
    lock = parse_lock()
    W = os.path.abspath(a.work)
    D = os.path.abspath(a.downloads)
    vendor = os.path.join(W, "vendor.img")
    if not os.path.isfile(vendor):
        die("%s is missing: run `gbos-install.py prepare` first (it unpacks the vendor image)" % vendor)
    host_tar = have_file(D, lock, "waydroid_nvidia_host")
    guest_tar = have_file(D, lock, "waydroid_nvidia_guest")
    stage = os.path.join(W, "gpu-stage")
    work = os.path.join(W, ".gpu-extract")
    for sub in ("host", "guest/lib64", "guest/lib", "guest/vendor/bin", work):
        os.makedirs(os.path.join(stage, sub) if not sub.startswith(W) else sub, exist_ok=True)

    def untar(tarball, dest):
        os.makedirs(dest, exist_ok=True)
        with open(tarball, "rb") as src:                 # via stdin: zstd refuses to open symlinks itself
            p1 = subprocess.Popen(["zstd", "-dc"], stdin=src, stdout=subprocess.PIPE)
            r = subprocess.run(["tar", "-xf", "-", "-C", dest], stdin=p1.stdout)
            p1.stdout.close()
            if p1.wait() != 0 or r.returncode != 0:
                die("could not unpack %s" % tarball)

    hx, gx = os.path.join(work, "host"), os.path.join(work, "guest")
    untar(host_tar, hx)
    untar(guest_tar, gx)
    for n in ("virgl_test_server", "virgl_render_server", "libvirglrenderer.so.1"):
        shutil.copyfile(os.path.join(hx, n), os.path.join(stage, "host", n))
    # the upstream Venus guest driver; the shim (built below) takes the name vulkan.virtio.so and loads this one
    shutil.copyfile(os.path.join(gx, "vendor/lib64/hw/vulkan.virtio.so"), os.path.join(stage, "guest/lib64/vulkan.pastel.so"))
    shutil.copyfile(os.path.join(gx, "vendor/lib/hw/vulkan.virtio.so"), os.path.join(stage, "guest/lib/vulkan.virtio.so"))

    # the routing shim: our own code. A prebuilt (the tested binary, a release asset) or built from source with the NDK.
    shim = os.path.join(stage, "guest/lib64/vulkan.virtio.so")
    if a.prebuilt_shim:
        check_prebuilt(a.prebuilt_shim, "vulkan.hybrid64.so", load_prebuilt_lock(), strict=True)
        shutil.copyfile(a.prebuilt_shim, shim)
    else:
        if not a.ndk:
            die("the Vulkan shim needs either --prebuilt-shim FILE (release asset) or --ndk DIR|auto to build it")
        if a.ndk == "auto":
            a.downloads = D
            tc = os.path.join(W, ".toolchain", "android-ndk-r27c")
            if not os.path.isdir(tc):
                zpath = have_file(D, lock, "android_ndk_r27c")
                os.makedirs(os.path.dirname(tc), exist_ok=True)
                run(["unzip", "-q", zpath, "-d", os.path.dirname(tc)])
            a.ndk = tc
        out = os.path.join(W, ".native")
        run(["make", "-C", os.path.join(ROOT, "native"), "OUT=" + out, "NDK=" + a.ndk, "gpu-shim"])
        shutil.copyfile(os.path.join(out, "vulkan.hybrid64.so"), shim)

    # waydroid-init: from the user's own vendor image, with the one-byte property rename
    stock = os.path.join(work, "waydroid-init.stock")
    r = subprocess.run([find_tool("debugfs"), "-R", "dump /bin/waydroid-init " + stock, vendor],
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if not os.path.isfile(stock) or os.path.getsize(stock) == 0:
        die("could not read /bin/waydroid-init from %s" % vendor)
    patched = os.path.join(stage, "guest/vendor/bin/waydroid-init")
    run([sys.executable, os.path.join(ROOT, "gpu", "patch-waydroid-init-vulkan.py"), stock, patched])
    got = sha256_file(patched)
    if got != WAYDROID_INIT_PATCHED_SHA256:
        die("patched waydroid-init is %s, expected %s: this vendor image is not the supported build" % (got, WAYDROID_INIT_PATCHED_SHA256))

    shutil.copyfile(os.path.join(ROOT, "gpu", "run-venus-daemon.sh"), os.path.join(stage, "run-venus-daemon.sh"))
    shutil.copyfile(os.path.join(ROOT, "gpu", "units", "gbos-venus.service"), os.path.join(stage, "gbos-venus.service"))
    shutil.rmtree(work)
    say("")
    say("GPU stage directory ready: %s" % stage)
    say("check this machine:  %s detect" % os.path.join(ROOT, "gpu", "gbos-gpu"))
    say("then, only if it says 'supported':  sudo %s enable --stage-dir %s --user $USER" % (os.path.join(ROOT, "gpu", "gbos-gpu"), stage))
    return 0


# ---------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("check", help="preflight")
    c.add_argument("--downloads")
    c.add_argument("--work")
    c.add_argument("--ignore-cpu", action="store_true", help="only warn about missing CPU instruction sets (you are emulating them)")
    f = sub.add_parser("fetch", help="download and verify the pinned inputs")
    f.add_argument("--downloads", required=True)
    f.add_argument("--with-nvidia", action="store_true", help="also fetch the inputs of the OPTIONAL hybrid-GPU module (host + guest archives)")
    f.add_argument("--with-ndk", action="store_true", help="also fetch the Android NDK (to build the native helpers; see prepare --ndk auto)")
    p = sub.add_parser("prepare", help="extract, patch and assemble the build folder")
    p.add_argument("--downloads", required=True)
    p.add_argument("--work", required=True)
    p.add_argument("--phone-hub", action="store_true", help="also build the Phone Hub apps")
    g = p.add_mutually_exclusive_group()
    g.add_argument("--prebuilt", help="directory with the prebuilt Android binaries (input-bridge, vpd-stub) from `fetch-prebuilt`")
    g.add_argument("--ndk", help="Android NDK r27c directory, or 'auto' to unpack the one fetched with --with-ndk")
    ad = sub.add_parser("addons", help="build the kernel add-on modules this machine needs (CPU emulation, binder security context, legacy iptables)")
    ad.add_argument("--tree", help="the tree (or linux-headers) the RUNNING kernel was built from")
    ad.add_argument("--out", help="where to put the builds (outside the repository)")
    ad.add_argument("--build", action="store_true", help="build them (default: only say which are needed)")
    ad.add_argument("--allow-other", action="store_true", help="build for a kernel that is not the one running here")
    kf = sub.add_parser("kernel-fragment", help="print the kernel config options GBOS needs, for building your own kernel")
    kf.add_argument("--all", action="store_true", help="include the optional ones (ipvlan, virtual Bluetooth, ...)")
    kf.add_argument("--base", help="your current .config: print only the options it is missing")
    kf.add_argument("--builtin", action="store_true", help="build everything in (=y) instead of as modules")
    fp = sub.add_parser("fetch-prebuilt", help="download the prebuilt Android binaries of a release and verify them")
    fp.add_argument("--url", required=True, help="release download URL, e.g. https://github.com/OWNER/REPO/releases/download/vX")
    fp.add_argument("--dest", required=True)
    fp.add_argument("--with-gpu-shim", action="store_true", help="also fetch the optional hybrid-GPU Vulkan shim")
    gp = sub.add_parser("gpu-prepare", help="OPTIONAL: assemble the hybrid-GPU module's install directory")
    gp.add_argument("--downloads", required=True)
    gp.add_argument("--work", required=True)
    gp.add_argument("--prebuilt-shim", help="the prebuilt Vulkan shim (release asset)")
    gp.add_argument("--ndk", help="NDK r27c directory or 'auto', to build the shim from source")
    a = ap.parse_args()
    for d in ("downloads", "work"):
        if getattr(a, d, None) and os.path.abspath(getattr(a, d)).startswith(ROOT + os.sep):
            die("--%s must be outside the repository" % d)
    return {"check": cmd_check, "fetch": cmd_fetch, "prepare": cmd_prepare, "gpu-prepare": cmd_gpu_prepare, "fetch-prebuilt": cmd_fetch_prebuilt,
            "kernel-fragment": cmd_kernel_fragment, "addons": cmd_addons}[a.cmd](a)


if __name__ == "__main__":
    sys.exit(main())
