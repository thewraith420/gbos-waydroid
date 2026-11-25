#!/usr/bin/env python3
"""gbos-backup: back up and restore a GBOS / Waydroid install, so that `try.sh` (which resets Waydroid) can be undone.

    sudo gbos-backup.py backup  --out DIR [--with overlay,images,data] [--user NAME]
    sudo gbos-backup.py restore BACKUP [--tiers config,data] [--no-verify]
         gbos-backup.py list    DIR
         gbos-backup.py verify  BACKUP

A backup is a folder holding one archive per tier and a manifest with sha256 checksums:

    config   always, ~100 KB   Waydroid's container config and properties, the host rules/units/daemons GBOS installed
    overlay  --with overlay    /var/lib/waydroid/overlay and overlay_rw (rebuilt by bake-apps.sh, but overlay_rw is runtime state)
    images   --with images     the system.img / vendor.img that try.sh built (several GB; rebuildable with the installer)
    data     --with data       Android's /data: accounts, apps and their data (the part try.sh destroys; several GB)

Restore never deletes anything: files and folders it replaces are moved aside to a `.pre-restore-<time>` name next to
them. Waydroid must be stopped for restore, and for backing up overlay, images or data (a running Android is writing
to them). Ownership is kept by number, so run both as root.

Python 3.8+, standard library only. Uses tar, zstd (or gzip) and nice/ionice when present.
"""
import argparse
import configparser
import hashlib
import json
import os
import pwd
import shlex
import shutil
import subprocess
import sys
import time

WAYDROID = "/var/lib/waydroid"
ROOT = "/"                                   # --root DIR: act on DIR as if it were /  (tests)


def P(p):
    return p if ROOT == "/" else os.path.join(ROOT, p.lstrip("/"))


def die(msg):
    sys.exit("gbos-backup: " + msg)


def say(msg):
    print(msg, flush=True)


# ------------------------------------------------------------------------------------------ what each tier holds
def images_path():
    cp = configparser.ConfigParser()
    try:
        cp.read(P(WAYDROID + "/waydroid.cfg"))
        return cp.get("waydroid", "images_path", fallback="/etc/waydroid-extra/images")
    except configparser.Error:
        return "/etc/waydroid-extra/images"


def tier_paths(tier, user):
    try:
        home = pwd.getpwnam(user).pw_dir if user else None
    except KeyError:
        die("no such user: %s" % user)
    if tier == "config":
        p = [WAYDROID + n for n in ("/waydroid.cfg", "/waydroid.prop", "/waydroid_base.prop", "/lxc", "/fake-sysctl",
                                    "/host-permissions", "/gbos-gpu.json")]
        p += ["/etc/udev/rules.d/65-waydroid-input-ignore.rules", "/etc/udev/rules.d/99-waydroid-input-ignore.rules", "/etc/modules-load.d/waydroid-iptables.conf",
              "/etc/gb-lan.conf", "/usr/local/sbin/gb-lan.py", "/usr/local/sbin/gb-bt-bridge.py",
              "/usr/local/sbin/wifi-hwsim.sh"]
        p += ["/etc/systemd/system/" + u for u in ("gb-lan.service", "gb-wifi-host.service", "gb-bt-bridge.service")]
        if home:
            p += [home + "/.config/gbos"]
            p += [home + "/.config/systemd/user/" + u + ".service" for u in (
                "gb-hub-link", "gb-appcast", "gb-audio", "gb-discover", "gb-link-daemon", "gb-autostop", "gbos-venus")]
        return p
    if tier == "overlay":
        return [WAYDROID + "/overlay", WAYDROID + "/overlay_rw"]
    if tier == "images":
        return [images_path()]
    if tier == "data":
        return [home + "/.local/share/waydroid/data"] if home else []
    die("unknown tier %s" % tier)


# ------------------------------------------------------------------------------------------ helpers
def tool(name):
    return shutil.which(name)


def prefix():
    pre = []
    if tool("ionice"):
        pre += ["ionice", "-c3"]
    if tool("nice"):
        pre += ["nice", "-n", "10"]
    return pre


def is_ram_backed(path):
    probe = path
    while probe and not os.path.exists(probe):
        probe = os.path.dirname(probe)
    r = subprocess.run(["findmnt", "-no", "FSTYPE", "-T", probe or "/"], capture_output=True, text=True)
    return r.stdout.strip() in ("tmpfs", "ramfs")


def du(paths):
    """Bytes actually allocated on disk. NOT the apparent size: Android's /data holds huge sparse files (the Linux
    Terminal VM disk is ~950 GB apparent / ~17 GB used, gb-datadisk.img ~1 TB apparent / 0 used), and tar --sparse
    only stores the allocated part, so the apparent size would refuse a backup that fits easily."""
    total = 0
    for p in paths:
        r = subprocess.run(["du", "-s", "--block-size=1", P(p)], capture_output=True, text=True)
        try:
            total += int(r.stdout.split()[0])
        except (IndexError, ValueError):
            pass
    return total


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for blk in iter(lambda: f.read(8 << 20), b""):
            h.update(blk)
    return h.hexdigest()


def waydroid_running():
    if ROOT != "/":
        return False
    r = subprocess.run(["pgrep", "-f", "lxc-start.*waydroid"], capture_output=True)
    if r.returncode == 0:
        return True
    r = subprocess.run(["waydroid", "status"], capture_output=True, text=True) if tool("waydroid") else None
    return bool(r and "RUNNING" in r.stdout)


def need_root():
    if ROOT == "/" and os.geteuid() != 0:
        die("this needs root (file ownership is kept by number): sudo %s" % " ".join(shlex.quote(a) for a in sys.argv))


def compressor():
    if tool("zstd"):
        return ["-I", "zstd -T1 -3"], ".tar.zst"
    return ["-z"], ".tar.gz"


def run_tar(args):
    r = subprocess.run(prefix() + ["tar"] + args)
    if r.returncode not in (0, 1):                  # 1 = "file changed as we read it": not fatal for a live config tier
        die("tar failed (%d)" % r.returncode)


# ------------------------------------------------------------------------------------------ backup
def cmd_backup(a):
    need_root()
    tiers = ["config"] + [t for t in (a.with_.split(",") if a.with_ else []) if t]
    for t in tiers:
        if t not in ("config", "overlay", "images", "data"):
            die("unknown tier %r (use overlay, images, data)" % t)
    user = a.user or os.environ.get("SUDO_USER") or None
    if user == "root":
        user = None
    if "data" in tiers and not user:
        die("which user's Waydroid data? pass --user NAME (the desktop user, not root)")
    out_root = os.path.abspath(a.out)
    if is_ram_backed(out_root):
        die("%s is on a RAM-backed filesystem; pick a folder on disk" % out_root)
    if any(t in tiers for t in ("overlay", "images", "data")) and waydroid_running() and not a.force_running:
        die("Waydroid is running. Stop it first (waydroid session stop; sudo waydroid container stop), "
            "or pass --force-running to take an inconsistent copy")
    stamp = time.strftime("%Y%m%d-%H%M%S")
    dest = os.path.join(out_root, "gbos-backup-" + stamp)
    comp, ext = compressor()
    plan = {}
    for t in tiers:
        existing = [p for p in tier_paths(t, user) if os.path.lexists(P(p))]
        plan[t] = (existing, du(existing))
        if not existing and t != "config":
            die("tier %s: nothing to back up (looked at %s)" % (t, ", ".join(tier_paths(t, user)) or "no path"))
    need = sum(sz for _, sz in plan.values())
    os.makedirs(out_root, exist_ok=True)
    free = shutil.disk_usage(out_root).free
    say("backup of: %s   (%.1f MB to read; %.1f GB free at the destination)" % (
        ", ".join(tiers), need / 2**20, free / 2**30))
    if free < need and not a.force:
        die("not enough free space at %s for %.1f GB (pass --force to try anyway)" % (out_root, need / 2**30))
    os.makedirs(dest)
    manifest = {"format": 1, "created": stamp, "user": user, "root": ROOT, "tiers": {}}
    try:
        manifest["waydroid"] = subprocess.run(["waydroid", "--version"], capture_output=True, text=True).stdout.strip()
    except OSError:
        manifest["waydroid"] = ""
    for t in tiers:
        paths, size = plan[t]
        name = t + (".tar" if t == "images" else ext)
        target = os.path.join(dest, name)
        rel = [p.lstrip("/") for p in paths]
        if not rel:
            say("  %-8s nothing present, skipped" % t)
            continue
        say("  %-8s %s (%.1f MB)" % (t, name, size / 2**20))
        args = ["--numeric-owner", "--xattrs", "--acls", "--sparse"]
        args += ([] if t == "images" else comp)
        run_tar(args + ["-cf", target, "-C", P("/")] + rel)
        manifest["tiers"][t] = {"file": name, "paths": paths, "bytes": os.path.getsize(target),
                                "source_bytes": size, "sha256": sha256(target)}
    with open(os.path.join(dest, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=2)
    say("done: %s" % dest)
    say("check it:  %s verify %s" % (sys.argv[0], dest))
    return 0


# ------------------------------------------------------------------------------------------ verify / list / restore
def load_manifest(b):
    try:
        with open(os.path.join(b, "manifest.json")) as f:
            return json.load(f)
    except (OSError, ValueError) as e:
        die("%s is not a gbos backup (%s)" % (b, e))


def cmd_verify(a):
    m = load_manifest(a.backup)
    bad = 0
    for t, info in m["tiers"].items():
        p = os.path.join(a.backup, info["file"])
        ok = os.path.isfile(p) and os.path.getsize(p) == info["bytes"] and sha256(p) == info["sha256"]
        say("  %-8s %s" % (t, "ok" if ok else "DAMAGED OR MISSING"))
        bad += 0 if ok else 1
    say("verified" if not bad else "%d tier(s) damaged" % bad)
    return 1 if bad else 0


def cmd_list(a):
    rows = []
    for n in sorted(os.listdir(a.dir)):
        mf = os.path.join(a.dir, n, "manifest.json")
        if os.path.isfile(mf):
            m = json.load(open(mf))
            size = sum(i["bytes"] for i in m["tiers"].values())
            rows.append((n, ",".join(m["tiers"]), size, m.get("waydroid", "")))
    if not rows:
        say("no backups in %s" % a.dir)
    for n, t, s, w in rows:
        say("%-30s %-28s %9.1f MB  %s" % (n, t, s / 2**20, w))
    return 0


def aside(path, stamp):
    """Move PATH out of the way (never delete). Returns the new name or None."""
    if not os.path.lexists(path):
        return None
    new = "%s.pre-restore-%s" % (path.rstrip("/"), stamp)
    os.rename(path, new)
    return new


def cmd_restore(a):
    need_root()
    m = load_manifest(a.backup)
    want = a.tiers.split(",") if a.tiers else list(m["tiers"])
    for t in want:
        if t not in m["tiers"]:
            die("this backup has no %r tier (it has: %s)" % (t, ", ".join(m["tiers"])))
    if waydroid_running():
        die("Waydroid is running; stop it first (waydroid session stop; sudo waydroid container stop)")
    if not a.no_verify:
        say("verifying checksums first (use --no-verify to skip)")
        for t in want:
            p = os.path.join(a.backup, m["tiers"][t]["file"])
            if sha256(p) != m["tiers"][t]["sha256"]:
                die("%s is damaged; refusing to restore from it" % p)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    moved = []
    for t in want:
        info = m["tiers"][t]
        paths = info["paths"]
        say("restoring %s (%d path(s))" % (t, len(paths)))
        for p in paths:                       # park what is there next to itself, then extract
            n = aside(P(p), stamp)
            if n:
                moved.append((p, n))
        args = ["-p", "--numeric-owner", "--xattrs", "--acls", "--sparse"]      # -p: keep setuid etc. even when not root
        args += ([] if info["file"].endswith(".tar") else (["-I", "zstd"] if info["file"].endswith(".zst") else ["-z"]))
        run_tar(args + ["-xf", os.path.join(a.backup, info["file"]), "-C", P("/")])
    say("")
    say("restored: %s" % ", ".join(want))
    if moved:
        say("what was in the way was moved aside, not deleted:")
        for orig, new in moved:
            say("  %s  ->  %s" % (orig, os.path.relpath(new, P("/")) if ROOT != "/" else new))
        say("delete those once you are happy with the result.")
    say("Start Waydroid again:  waydroid session start")
    return 0


def main():
    global ROOT
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", default="/", help=argparse.SUPPRESS)
    sub = ap.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("backup")
    b.add_argument("--out", required=True)
    b.add_argument("--with", dest="with_", default="", help="extra tiers: overlay,images,data")
    b.add_argument("--user")
    b.add_argument("--force", action="store_true")
    b.add_argument("--force-running", action="store_true")
    b.set_defaults(fn=cmd_backup)
    r = sub.add_parser("restore")
    r.add_argument("backup")
    r.add_argument("--tiers")
    r.add_argument("--no-verify", action="store_true")
    r.set_defaults(fn=cmd_restore)
    l = sub.add_parser("list")
    l.add_argument("dir")
    l.set_defaults(fn=cmd_list)
    v = sub.add_parser("verify")
    v.add_argument("backup")
    v.set_defaults(fn=cmd_verify)
    a = ap.parse_args()
    ROOT = os.path.abspath(a.root)
    return a.fn(a)


if __name__ == "__main__":
    sys.exit(main())
