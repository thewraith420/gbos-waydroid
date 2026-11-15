#!/usr/bin/env python3
"""gb-lan: put the Waydroid container ON the host's LAN (GBOS_WAYDROID_FIXES.md, section 3.15).

The container normally sits behind the host's NAT (192.168.240.0/24), so phones/Chromecasts/printers on the home LAN cannot discover it
(no mDNS across NAT) and cannot open connections to it.  This root daemon gives it a second NIC directly on the LAN:

  * an **ipvlan (L2)** link on the host's uplink (Wi-Fi works: ipvlan reuses the parent's MAC; macvlan does not work on a Wi-Fi station)
    is created, moved into the container's network namespace and renamed ``eth1``;
  * because the MAC is shared with the host, **DHCP must not be used** (the router would hand back the host's lease).  The daemon
    picks a free static IPv4 address in the uplink's subnet (ARP probe, remembered in /var/lib/gb-lan/state.json) and writes
    /data/local/tmp/gb-lan.conf inside the container's data dir; the container service gb-lan.sh applies it to Android's Ethernet
    manager *before* eth1 exists, and writes /data/local/tmp/gb-lan.applied, which this daemon waits for before creating the link;
  * by default the NIC is **LAN-only** (no gateway -> no second default route; internet keeps using the NAT eth0).

It re-creates everything when Waydroid restarts or the host moves to another network, and removes eth1 when the uplink goes away.

  sudo ./gb-lan.py            run (normally via the systemd unit gb-lan.service)
  sudo ./gb-lan.py --plan     show what it would do right now (no changes)
  sudo ./gb-lan.py --down     remove eth1 from the running container and exit
Optional /etc/gb-lan.conf (KEY=VALUE lines): PARENT=wlan0  IP=192.0.2.250  GATEWAY_MODE=full|none (default none)  DATA_DIR=...
"""
import argparse
import ipaddress
import json
import logging
import os
import socket
import struct
import subprocess
import sys
import time

STATE_DIR = "/var/lib/gb-lan"
STATE = STATE_DIR + "/state.json"
CONF = "/etc/gb-lan.conf"
def _default_data():
    """Waydroid's data dir lives in the invoking user's home; this runs as root, so find that user."""
    import pwd
    u = os.environ.get("GBOS_USER") or os.environ.get("SUDO_USER")
    home = pwd.getpwnam(u).pw_dir if u else os.path.expanduser("~")
    return home + "/.local/share/waydroid/data/local/tmp"


DEFAULT_DATA = _default_data()
LINK = "gblan0"
CONTAINER_IF = "eth1"

log = logging.getLogger("gb-lan")


def sh(cmd, timeout=15):
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, stdin=subprocess.DEVNULL)
        return r.returncode, r.stdout.strip(), r.stderr.strip()
    except (OSError, subprocess.SubprocessError) as e:
        return 1, "", str(e)


def load_conf():
    c = {}
    try:
        for line in open(CONF):
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                c[k.strip()] = v.strip()
    except OSError:
        pass
    return c


def uplink(conf):
    """Return (parent_dev, ipv4, prefixlen, gateway) for the host's uplink, or None."""
    rc, out, _ = sh(["ip", "-4", "-j", "route", "show", "default"])
    parent, gw = conf.get("PARENT"), None
    try:
        routes = json.loads(out or "[]")
    except ValueError:
        routes = []
    for r in routes:
        if parent in (None, r.get("dev")):
            parent, gw = r.get("dev"), r.get("gateway")
            break
    if not parent or not gw:
        return None
    if parent.startswith(("waydroid", "lxc", "veth", "docker", "virbr", "gblan")):
        return None
    rc, out, _ = sh(["ip", "-4", "-j", "addr", "show", "dev", parent])
    try:
        for i in json.loads(out or "[]"):
            for a in i.get("addr_info", []):
                return parent, a["local"], int(a["prefixlen"]), gw
    except (ValueError, KeyError):
        pass
    return None


def container_pid():
    rc, out, _ = sh(["lxc-info", "-P", "/var/lib/waydroid/lxc", "-n", "waydroid", "-p", "-H"])
    try:
        pid = int(out.split()[0])
        return pid if pid > 1 and os.path.exists("/proc/%d/ns/net" % pid) else None
    except (ValueError, IndexError):
        return None


def in_ns(pid, cmd):
    return sh(["nsenter", "-t", str(pid), "-n"] + cmd)


def has_link(pid):
    return in_ns(pid, ["ip", "-o", "link", "show", CONTAINER_IF])[0] == 0


def mac_of(dev):
    try:
        return bytes.fromhex(open("/sys/class/net/%s/address" % dev).read().strip().replace(":", ""))
    except (OSError, ValueError):
        return None


def arp_in_use(dev, ip, wait=1.5):
    """ARP probe (sender 0.0.0.0): True if somebody answers for ip."""
    mac = mac_of(dev)
    if mac is None:
        return True
    try:
        s = socket.socket(socket.AF_PACKET, socket.SOCK_RAW, socket.htons(0x0806))
        s.bind((dev, 0))
        s.settimeout(0.3)
        tgt = socket.inet_aton(ip)
        pkt = b"\xff" * 6 + mac + b"\x08\x06" + struct.pack("!HHBBH", 1, 0x0800, 6, 4, 1) + mac + b"\x00" * 4 + b"\x00" * 6 + tgt
        deadline = time.time() + wait
        s.send(pkt)
        while time.time() < deadline:
            try:
                r = s.recv(256)
            except socket.timeout:
                continue
            if len(r) >= 42 and r[12:14] == b"\x08\x06" and struct.unpack("!H", r[20:22])[0] == 2 and r[28:32] == tgt:
                return True
        return False
    finally:
        s.close()


def choose_ip(parent, my_ip, prefix, gw, state, conf):
    net = ipaddress.ip_network("%s/%d" % (my_ip, prefix), strict=False)
    if conf.get("IP"):
        return conf["IP"]
    old = state.get("ip")
    if old and state.get("subnet") == str(net) and old not in (my_ip, gw):
        return old                                   # keep a stable address across restarts
    hosts = list(net.hosts())
    for cand in reversed(hosts[-60:]):               # from the top of the subnet downwards
        c = str(cand)
        if c in (my_ip, gw):
            continue
        if not arp_in_use(parent, c):
            return c
    raise RuntimeError("no free address found in %s" % net)


def load_state():
    try:
        return json.load(open(STATE))
    except (OSError, ValueError):
        return {}


def save_state(st):
    os.makedirs(STATE_DIR, exist_ok=True)
    tmp = STATE + ".tmp"
    json.dump(st, open(tmp, "w"))
    os.replace(tmp, STATE)


def write_container_conf(data_dir, ip, prefix, gw, dns, lan_only):
    body = "IFACE=%s\nADDR=%s/%d\nGW=%s\nDNS=%s\n" % (CONTAINER_IF, ip, prefix, "" if lan_only else gw, dns)
    path = data_dir + "/gb-lan.conf"
    tmp = path + ".tmp"
    with open(tmp, "w") as f:
        f.write(body)
    os.chmod(tmp, 0o644)
    os.replace(tmp, path)
    try:
        os.unlink(data_dir + "/gb-lan.applied")
    except OSError:
        pass
    return body


def applied(data_dir, body):
    import hashlib
    try:
        return open(data_dir + "/gb-lan.applied").read().strip() == hashlib.md5(body.encode()).hexdigest()
    except OSError:
        return False


def create_link(parent, pid):
    sh(["modprobe", "ipvlan"])
    sh(["ip", "link", "del", LINK])                                           # stale leftover on the host side
    rc, _, err = sh(["ip", "link", "add", "link", parent, "name", LINK, "type", "ipvlan", "mode", "l2"])
    if rc != 0:
        raise RuntimeError("ip link add ipvlan failed: %s" % err)
    rc, _, err = sh(["ip", "link", "set", LINK, "netns", str(pid)])
    if rc != 0:
        sh(["ip", "link", "del", LINK])
        raise RuntimeError("moving the link into the container failed: %s" % err)
    in_ns(pid, ["ip", "link", "set", LINK, "name", CONTAINER_IF])
    in_ns(pid, ["ip", "link", "set", CONTAINER_IF, "up"])
    log.info("ipvlan on %s is now %s inside the container (pid %d)", parent, CONTAINER_IF, pid)


def remove_link(pid):
    if pid:
        in_ns(pid, ["ip", "link", "del", CONTAINER_IF])
    sh(["ip", "link", "del", LINK])


def plan(conf):
    up = uplink(conf)
    pid = container_pid()
    print("uplink:", up or "none")
    print("container pid:", pid or "not running")
    if up:
        net = ipaddress.ip_network("%s/%d" % (up[1], up[2]), strict=False)
        print("subnet:", net, " state file:", load_state() or "none")
        print("DATA_DIR:", conf.get("DATA_DIR", DEFAULT_DATA), "exists" if os.path.isdir(conf.get("DATA_DIR", DEFAULT_DATA)) else "MISSING")
    print("ipvlan module:", "loaded" if os.path.isdir("/sys/module/ipvlan") else "not loaded (will be modprobe'd)")


def run(conf):
    data_dir = conf.get("DATA_DIR", DEFAULT_DATA)
    lan_only = conf.get("GATEWAY_MODE", "none") != "full"
    cur = None                                       # {"parent","ip","pid","body"} of the configuration in place
    while True:
        try:
            up, pid = uplink(conf), container_pid()
            if cur and (not up or not pid or pid != cur["pid"] or up[0] != cur["parent"] or not has_link(pid)):
                log.info("uplink/container changed or link gone: tearing down")
                remove_link(cur["pid"] if pid == cur["pid"] else None)
                for f in ("gb-lan.conf", "gb-lan.applied"):
                    try:
                        os.unlink(data_dir + "/" + f)
                    except OSError:
                        pass
                cur = None
            if cur is None and up and pid:
                parent, my_ip, prefix, gw = up
                st = load_state()
                ip = choose_ip(parent, my_ip, prefix, gw, st, conf)
                body = write_container_conf(data_dir, ip, prefix, gw, gw, lan_only)
                log.info("uplink %s %s/%d gw %s -> container address %s (%s)", parent, my_ip, prefix, gw, ip, "LAN-only" if lan_only else "with gateway")
                for _ in range(60):                  # wait for the container service to apply it before the NIC exists (avoids DHCP)
                    if applied(data_dir, body):
                        break
                    time.sleep(1)
                else:
                    log.warning("container has not applied %s yet (is gb-lan running there?); will retry", data_dir + "/gb-lan.conf")
                    time.sleep(10)
                    continue
                create_link(parent, pid)
                save_state({"parent": parent, "subnet": str(ipaddress.ip_network("%s/%d" % (my_ip, prefix), strict=False)), "ip": ip})
                cur = {"parent": parent, "ip": ip, "pid": pid, "body": body}
        except Exception as e:
            log.error("%s", e)
            cur = None
        time.sleep(5)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--plan", action="store_true")
    ap.add_argument("--down", action="store_true")
    ap.add_argument("-v", "--verbose", action="store_true")
    a = ap.parse_args()
    logging.basicConfig(level=logging.DEBUG if a.verbose else logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    conf = load_conf()
    if a.plan:
        plan(conf)
        return
    if os.geteuid() != 0:
        sys.exit("needs root: run with sudo")
    if a.down:
        remove_link(container_pid())
        print("eth1 removed")
        return
    run(conf)


if __name__ == "__main__":
    main()
