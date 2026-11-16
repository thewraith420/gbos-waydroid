#!/usr/bin/env python3
"""gbos-doctor: read-only health check of a GBOS install, with a concrete next step for everything it flags.

    gbos-doctor.py [--user NAME] [--offline] [--json]

It changes nothing. It looks at the host (modules, rules), Waydroid (state, images, the container settings that
`waydroid upgrade` silently rewrites), the baked overlay (including files in the writable layer that shadow it), the
host services, and, if the container is up, Android itself (boot, main user, frida, the Cross-device link).

    --offline   only look at files; do not run systemctl, waydroid or adb
    --user      whose user services and Waydroid data to look at (default: you, or $SUDO_USER)

Exit status: 0 all good, 1 warnings, 2 something is broken. Python 3.8+, standard library only.
"""
import argparse
import hashlib
import json
import os
import pwd
import re
import shutil
import subprocess
import sys

WAYDROID = "/var/lib/waydroid"
ROOT = "/"
RESULTS = []          # (level, area, text, hint)


def P(p):
    return p if ROOT == "/" else os.path.join(ROOT, p.lstrip("/"))


def add(level, area, text, hint=""):
    RESULTS.append((level, area, text, hint))


def ok(area, text, hint=""):
    add("ok", area, text)


def warn(area, text, hint=""):
    add("warn", area, text, hint)


def fail(area, text, hint=""):
    add("fail", area, text, hint)


def info(area, text, hint=""):
    add("info", area, text)


def sh(cmd, timeout=10):
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return r.returncode, r.stdout
    except (OSError, subprocess.SubprocessError):
        return 127, ""


def read(path):
    try:
        with open(P(path), errors="replace") as f:
            return f.read()
    except OSError:
        return None


def loaded_module(name):
    mods = read("/proc/modules") or ""
    return any(l.split()[0] == name for l in mods.splitlines()) or os.path.isdir(P("/sys/module/" + name))


# ------------------------------------------------------------------------------------------ checks
def cpu_flags():
    for l in (read("/proc/cpuinfo") or "").splitlines():
        if l.startswith("flags"):
            return set(l.split(":", 1)[1].split())
    return None


def kallsyms_has(name):
    txt = read("/proc/kallsyms")
    if txt is None:
        return None
    return any(l.rstrip().endswith(" " + name) for l in txt.splitlines())


def check_addons(a):
    """The kernel add-on modules (kernel/): needed on a CPU without the instructions the image uses, and on an AppArmor-only kernel."""
    flags = cpu_flags()
    if flags is not None:
        miss = [f for f in ("gfni", "vaes", "vpclmulqdq", "avx_vnni", "sha_ni") if f not in flags]
        if not miss:
            ok("host", "CPU has the instruction sets the image uses")
        elif loaded_module("gbos_vexemu"):
            enabled = (read("/sys/module/gbos_vexemu/parameters/enabled") or "Y").strip()
            (ok if enabled in ("Y", "1") else fail)("host", "CPU lacks %s; gbos_vexemu (instruction emulation) is %s" % (", ".join(miss), "loaded" if enabled in ("Y", "1") else "loaded but DISABLED"),
                                                      "" if enabled in ("Y", "1") else "echo 1 | sudo tee /sys/module/gbos_vexemu/parameters/enabled")
        else:
            fail("host", "CPU lacks %s and gbos_vexemu is not loaded: Android's first process dies with SIGILL" % ", ".join(miss),
                 "gbos-install.py addons --tree <kernel build tree> --out ~/gbos/addons --build   (docs/UNSUPPORTED-HARDWARE.md)")
    lsm = (read("/sys/kernel/security/lsm") or "").strip().split(",")
    if "apparmor" in lsm and "selinux" not in lsm:
        if loaded_module("gbos_secctx"):
            ok("host", "AppArmor-only kernel: gbos_secctx (binder security context) loaded")
        elif kallsyms_has("apparmor_cred_getsecid"):
            ok("host", "AppArmor-only kernel with the cred_getsecid fix")
        elif kallsyms_has("apparmor_cred_getsecid") is False:
            warn("host", "AppArmor-only kernel without the cred_getsecid fix: binder cannot give keystore2 a caller context and odsign aborts in a loop (boot stops at the animation)",
                 "kernel/gbos-secctx.py, or the patch in kernel/patches/ (docs/FIX-CATALOG.md entry 23)")


def check_host(a):
    check_addons(a)
    for t in ("waydroid", "adb"):
        (ok if shutil.which(t) else (fail if t == "waydroid" else warn))(
            "host", "%s %s" % (t, "found" if shutil.which(t) else "not found"),
            "install it (adb: docs/TUTORIAL-adb.md)")
    if not shutil.which("jdb"):
        warn("host", "jdb not found: the Phone Hub link keeper needs a JDK", "sudo apt install default-jdk-headless")
    st = os.environ.get("XDG_SESSION_TYPE")
    if st and st != "wayland":
        warn("host", "session type is %s; Waydroid draws into a Wayland window" % st, "log in to a Wayland session")
    if not (os.path.exists(P("/dev/binderfs")) or loaded_module("binder_linux") or loaded_module("binder") or
            any(n.startswith("binder") for n in (os.listdir(P("/dev")) if os.path.isdir(P("/dev")) else []))):
        warn("host", "no binder device found", "sudo modprobe binder_linux  (or load your kernel's binder support)")
    rule = "/etc/udev/rules.d/99-waydroid-input-ignore.rules"
    (ok if os.path.exists(P(rule)) else warn)("host", "udev rule that hides Waydroid's virtual input devices from the desktop: " +
                                              ("present" if os.path.exists(P(rule)) else "missing"),
                                              "it is installed by try.sh")
    conf = read("/etc/modules-load.d/waydroid-iptables.conf")
    if conf is None:
        warn("host", "legacy iptables modules are not set to load at boot", "try.sh writes /etc/modules-load.d/waydroid-iptables.conf")
    else:
        miss = [m for m in conf.split() if not loaded_module(m)]
        (warn if miss else ok)("host", "legacy iptables modules: " + (("not loaded: " + ", ".join(miss)) if miss else "loaded"),
                               "sudo modprobe " + " ".join(miss) if miss else "")


def waydroid_status():
    rc, out = sh(["waydroid", "status"])
    d = {}
    for ln in out.splitlines():
        if ":" in ln:
            k, v = ln.split(":", 1)
            d[k.strip()] = v.strip()
    return d


def check_waydroid(a):
    cfg = read(WAYDROID + "/waydroid.cfg")
    if cfg is None:
        fail("waydroid", "Waydroid is not initialised (%s/waydroid.cfg missing)" % WAYDROID, "run the installer's try.sh")
        return
    m = re.search(r"^images_path\s*=\s*(\S+)", cfg, re.M)
    ip = m.group(1) if m else "/etc/waydroid-extra/images"
    for img in ("system.img", "vendor.img"):
        p = P(os.path.join(ip, img))
        if os.path.isfile(p) and os.path.getsize(p) > 0:
            ok("waydroid", "%s present (%.0f MB)" % (img, os.path.getsize(p) / 2**20))
        else:
            fail("waydroid", "%s missing in %s" % (img, ip), "re-run try.sh (or restore a backup: installer/gbos-backup.py)")
    if not a.offline:
        st = waydroid_status()
        if st:
            info("waydroid", "session %s, container %s%s" % (st.get("Session", "?"), st.get("Container", "?"),
                                                              (", address " + st["IP address"]) if st.get("IP address") else ""))
    # the settings `waydroid upgrade` / `waydroid init` regenerate away (restore-container-tweaks.sh)
    conf = read(WAYDROID + "/lxc/waydroid/config") or ""
    fix = "sudo ./restore-container-tweaks.sh, then restart Waydroid"
    checks = [
        (re.search(r"^lxc\.mount\.auto\s*=\s*cgroup:rw", conf, re.M) is not None, "the container may write its own cgroups"),
        ("fake-sysctl/unprivileged_bpf_disabled" in conf, "a dummy file stands in for the read-only bpf sysctl"),
        ("tmpfs metadata tmpfs" in conf, "a writable /metadata"),
        (re.search(r"^lxc\.cap\.keep\s*=.*\bbpf\b", conf, re.M) is not None, "the bpf capability is kept (netd needs it)"),
    ]
    if conf:
        for good, what in checks:
            (ok if good else fail)("container", what + (": ok" if good else ": MISSING"), "" if good else fix)
    else:
        fail("container", "%s/lxc/waydroid/config not found" % WAYDROID, "try.sh creates it")
    for pf in ("waydroid.prop", "waydroid_base.prop"):
        t = read(WAYDROID + "/" + pf)
        if t is None:
            continue
        dbg = re.search(r"^ro\.debuggable=1$", t, re.M) is not None
        typ = re.search(r"^ro\.build\.type=userdebug$", t, re.M) is not None
        (ok if dbg and typ else fail)("container", "%s: ro.debuggable=1 and userdebug %s" % (pf, "set" if dbg and typ else "NOT set"),
                                      "" if dbg and typ else fix)
        # Waydroid writes debug.stagefright.ccodec=0 on desktop GPUs; with this image's OMX removed that leaves apps NO codecs
        if re.search(r"^debug\.stagefright\.ccodec=0$", t, re.M):
            fail("container", "%s: debug.stagefright.ccodec=0 (Codec2 off: apps have no video/audio codecs)" % pf,
                 "sudo sed -i 's/^debug.stagefright.ccodec=.*/debug.stagefright.ccodec=4/' %s/%s, then restart Waydroid "
                 "(try.sh does this; `waydroid init`/upgrade rewrites it)" % (WAYDROID, pf))
        else:
            ok("container", "%s: Codec2 codecs enabled for apps" % pf)
    # the optional hybrid GPU mode
    if read(WAYDROID + "/gbos-gpu.json"):
        nodes = read(WAYDROID + "/lxc/waydroid/config_nodes") or ""
        on = ">>> gbos-gpu" in nodes
        (ok if on else warn)("gpu", "hybrid GPU mode is configured and its block is " + ("present" if on else "MISSING"),
                             "" if on else "Waydroid rewrote config_nodes: sudo gpu/gbos-gpu apply")


BAKED = [
    "product/app/WebViewGoogle/WebViewGoogle.apk", "system_ext/app/ServiceabilityStub/ServiceabilityStub.apk",
    "vendor/etc/vintf/manifest.xml", "system_ext/etc/init/zzz-googlebook-waydroid.rc", "system/bin/gb-firstboot.sh",
    "system/bin/unlock-signin", "system/bin/input-bridge", "system/lib64/libselinux.so",
    "system/lib64/libaudiohal@7.1.so", "system/lib64/libinputflinger.so", "system/etc/audioctl.apk",
    "system/bin/gb-wifi.sh", "system/etc/fakewifi.apk", "system/etc/bthal.apk", "product/overlay/GbLanWifiOverlay.apk",
]


def md5_small(path, limit=64 << 20):
    try:
        if os.path.getsize(path) > limit:
            return None
        h = hashlib.sha256()
        with open(path, "rb") as f:
            for blk in iter(lambda: f.read(1 << 20), b""):
                h.update(blk)
        return h.hexdigest()
    except OSError:
        return None


def check_overlay(a):
    lower = P(WAYDROID + "/overlay")
    upper = P(WAYDROID + "/overlay_rw/system")
    if not os.path.isdir(lower):
        warn("overlay", "nothing has been baked yet (%s/overlay missing)" % WAYDROID, "stop Waydroid, then sudo ./bake-apps.sh")
        return
    missing = [f for f in BAKED if not os.path.isfile(os.path.join(lower, f))]
    if missing:
        warn("overlay", "%d baked file(s) missing, e.g. %s" % (len(missing), missing[0]), "stop Waydroid, then sudo ./bake-apps.sh")
    else:
        ok("overlay", "all %d baked files are present" % len(BAKED))
    # A copy of a baked file in the writable layer WINS over the baked one: a "fix" that does nothing.
    shadows = []
    for dp, _, fs in os.walk(lower):
        for fn in fs:
            lp = os.path.join(dp, fn)
            up = os.path.join(upper, os.path.relpath(lp, lower))
            if os.path.isfile(up):
                same = os.path.getsize(lp) == os.path.getsize(up)
                if same:
                    h1, h2 = md5_small(lp), md5_small(up)
                    same = h1 is None or h1 == h2
                if not same:
                    shadows.append(os.path.relpath(lp, lower))
    if shadows:
        warn("overlay", "%d baked file(s) are shadowed by a different copy in the writable layer: %s%s" % (
            len(shadows), ", ".join(shadows[:3]), " ..." if len(shadows) > 3 else ""),
            "stop Waydroid; sudo ./bake-apps.sh deletes the stale copies")
    else:
        ok("overlay", "nothing in the writable layer shadows a baked file")


SYSTEM_UNITS = ["gb-lan", "gb-wifi-host", "gb-bt-bridge"]
USER_UNITS = ["gb-hub-link", "gb-appcast", "gb-audio", "gb-discover", "gb-link-daemon", "gb-autostop", "gbos-venus"]


def check_services(a, home):
    if a.offline:
        return
    for u in SYSTEM_UNITS:
        if not os.path.exists(P("/etc/systemd/system/%s.service" % u)):
            info("services", "%s not installed" % u)
            continue
        rc, out = sh(["systemctl", "is-active", u])
        st = out.strip() or "unknown"
        (ok if st == "active" else warn)("services", "%s: %s" % (u, st), "" if st == "active" else "sudo systemctl status %s" % u)
    for u in USER_UNITS:
        if not home or not os.path.exists(os.path.join(P(home), ".config/systemd/user/%s.service" % u)):
            continue
        rc, out = sh(["systemctl", "--user", "is-active", u])
        st = out.strip() or "unknown"
        (ok if st == "active" else warn)("services", "%s (user): %s" % (u, st), "" if st == "active" else "journalctl --user -u %s" % u)
    run = os.environ.get("GB_RUN") or (os.path.join(os.environ["XDG_RUNTIME_DIR"], "gbos") if os.environ.get("XDG_RUNTIME_DIR") else "")
    lock = os.path.join(run, "gb-appcast.lock") if run else ""
    if lock and os.path.exists(lock):
        rc, out = sh(["pgrep", "-f", "appcast_session.py"])
        if rc != 0:
            warn("services", "a cast lock file exists but no cast session is running (the link keeper stands down while it exists)",
                 "rm %s" % lock)


def check_live(a):
    if a.offline:
        return
    st = waydroid_status()
    if st.get("Container") != "RUNNING":
        info("android", "container is not running; Android checks skipped")
        return
    addr = (st.get("IP address") or "").strip()
    if not addr:
        warn("android", "container is running but has no IP address yet")
        return
    cont = addr + ":5555"
    rc, out = sh(["adb", "devices"])
    if cont not in out:
        sh(["adb", "connect", cont], 8)
    A = lambda cmd, t=10: sh(["adb", "-s", cont, "shell", cmd], t)[1].replace("\r", "").strip()
    boot = A("getprop sys.boot_completed")
    if boot != "1":
        warn("android", "Android has not finished booting (or adb is not authorised)", "docs/TUTORIAL-adb.md; wait, or check `adb devices`")
        return
    ok("android", "Android booted, current user %s" % A("am get-current-user"))
    main = A("cmd user get-main-user")
    (ok if main == "10" else warn)("android", "main user: %s" % (main or "unknown"),
                                   "" if main == "10" else "with Waydroid stopped: phonehub/set-main-user.py <data>/system/users/10.xml --apply")
    hs = A("dumpsys user | grep -i 'headless-system mode'")
    if "true" in hs.lower():
        ok("android", "user layout: headless system user 0 plus the real user (as on a Googlebook)")
    elif hs:
        fail("android", "user layout: %s, so Android runs a full 'Owner' user 0 and the desktop shows only a wallpaper" % hs.strip(),
             "an older Android's data was reused: stop Waydroid, rename ~/.local/share/waydroid/data, start again (docs/FIX-CATALOG.md entry 25)")
    inp = A("dumpsys input | grep -c 'waydroid_touchscreen'")
    if inp.isdigit() and int(inp) > 0:
        ok("android", "the input bridge's virtual keyboard/mouse/touch devices are registered")
    elif inp.isdigit():
        fail("android", "the input bridge's devices are not registered with Android: no keyboard, mouse or touch",
             "adb shell 'rm -f /dev/input/event1[0-9]; setprop ctl.restart gb-inputbridge'  (docs/FIX-CATALOG.md entry 24)")
    fp = A("pidof frida-server")
    (ok if fp else info)("android", "frida-server %s" % ("running" if fp else "not running (starts with the App Cast supervisor)"))
    notif = A("dumpsys notification --noredact | grep -A 30 'pkg=com.google.ambient.streaming .*id=1 tag=null' | grep 'android.title=String (Connected)'", 20)
    (ok if notif else warn)("android", "Cross-device link to the phone: %s" % ("connected" if notif else "not connected"),
                            "" if notif else "docs/TROUBLESHOOTING.md#phone-hub")
    # The input bridge declares a fixed pointer/touch range (default 1920x1080). On any other screen the cursor and
    # touches land in the wrong place until persist.gbos.display matches `wm size`.
    wm = A("wm size")
    sizes = re.findall(r"(Override|Physical) size:\s*(\d+)x(\d+)", wm)
    if sizes:
        real = next((f"{w}x{h}" for k, w, h in sizes if k == "Override"), None) or f"{sizes[0][1]}x{sizes[0][2]}"
        prop = A("getprop persist.gbos.display") or "1920x1080"
        if real == prop:
            ok("android", "pointer/touch range matches the display (%s)" % real)
        else:
            warn("android", "the display is %s but the input bridge's pointer/touch range is %s, so clicks and touches land in the wrong place" % (real, prop),
                 "adb shell setprop persist.gbos.display %s; adb shell 'rm -f /dev/input/event1[0-9]; setprop ctl.restart gb-inputbridge'" % real)
    fw = A("iptables -S INPUT 2>/dev/null | grep -- '--dport 5555 -j DROP'")
    eth1 = A("ip -4 -o addr show eth1 2>/dev/null")
    if eth1:
        (ok if fw else fail)("android", "adb port 5555 is %s on the LAN-facing interface" % ("blocked" if fw else "OPEN"),
                             "" if fw else "root adb would be reachable from your network: restart Waydroid so gb-lan.sh re-adds the rule")
    else:
        info("android", "no LAN interface (gb-lan is not active)")


def main():
    global ROOT
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", default="/", help=argparse.SUPPRESS)
    ap.add_argument("--user")
    ap.add_argument("--offline", action="store_true")
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()
    ROOT = os.path.abspath(a.root)
    try:
        home = pwd.getpwnam(a.user or os.environ.get("SUDO_USER") or pwd.getpwuid(os.getuid()).pw_name).pw_dir
    except KeyError:
        home = None
    for fn in (check_host, check_waydroid, check_overlay, lambda x: check_services(x, home), check_live):
        try:
            fn(a)
        except Exception as e:                                  # a diagnostic tool must not die half-way
            warn("doctor", "a check crashed: %s" % e)
    if a.json:
        print(json.dumps([dict(level=l, area=ar, text=t, hint=h) for l, ar, t, h in RESULTS], indent=2))
    else:
        tag = {"ok": "ok  ", "warn": "warn", "fail": "FAIL", "info": "info"}
        area = None
        for l, ar, t, h in RESULTS:
            if ar != area:
                print("\n%s" % ar)
                area = ar
            print("  [%s] %s" % (tag[l], t))
            if h and l in ("warn", "fail"):
                print("         -> %s" % h)
        nf = sum(1 for r in RESULTS if r[0] == "fail")
        nw = sum(1 for r in RESULTS if r[0] == "warn")
        print("\n%s" % ("RESULT: all good" if not nf and not nw else "RESULT: %d broken, %d warning(s)" % (nf, nw)))
    return 2 if any(r[0] == "fail" for r in RESULTS) else (1 if any(r[0] == "warn" for r in RESULTS) else 0)


if __name__ == "__main__":
    sys.exit(main())
