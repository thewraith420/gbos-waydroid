"""Shared runtime configuration for the GBOS Phone Hub host scripts (stdlib only).

Mirror of lib/gbos-env.sh; the two must agree. Values can be overridden from the environment or
~/.config/gbos/gbos.env (KEY=VALUE lines).

    RUN        private runtime dir for locks, pid files, logs, the phone-agent token  ($XDG_RUNTIME_DIR/gbos)
    STATE      persistent cache                                                       (~/.cache/gbos)
    container()  adb address of the Waydroid container
    lan_iface(), lan_ip()   how the phone is reached from this host
    input_allow()           addresses allowed to inject input into the phone

Nothing is kept in /tmp: it is world-writable, and one of the files used to hold the phone-agent token.
"""
import os
import re
import socket
import subprocess
import sys
import time


def _load_config():
    path = os.environ.get("GB_CONFIG") or os.path.expanduser("~/.config/gbos/gbos.env")
    try:
        with open(path) as f:
            for line in f:
                m = re.match(r"\s*([A-Z_][A-Z0-9_]*)=(.*?)\s*$", line)
                if m and not line.lstrip().startswith("#"):
                    os.environ.setdefault(m.group(1), m.group(2).strip("'\""))
    except OSError:
        pass


_load_config()


def _run_dir():
    d = os.environ.get("GB_RUN")
    if not d:
        x = os.environ.get("XDG_RUNTIME_DIR")
        d = os.path.join(x, "gbos") if x else "/tmp/gbos-%d" % os.getuid()
    os.makedirs(d, mode=0o700, exist_ok=True)
    if os.stat(d).st_uid != os.getuid():
        sys.exit("gbos: %s is not owned by you; refusing to use it" % d)
    return d


RUN = _run_dir()
STATE = os.environ.get("GB_STATE") or os.path.join(
    os.environ.get("XDG_CACHE_HOME") or os.path.expanduser("~/.cache"), "gbos")
BRIDGE_IP = os.environ.get("GB_BRIDGE_IP", "192.168.240.1")
FALLBACK_CONTAINER = "192.168.240.112:5555"      # the address Waydroid's documentation uses; last resort only


def run_path(name):
    """Path of a file inside the private runtime dir."""
    return os.path.join(RUN, name)


_container_memo = (0.0, None)


def container():
    """adb address of the container, re-asked at most every 20 s so a long-running daemon follows Waydroid
    restarts (the address is handed out by DHCP) without running `waydroid status` on every call."""
    global _container_memo
    if os.environ.get("GB_CONTAINER"):
        return os.environ["GB_CONTAINER"]
    now = time.monotonic()
    if _container_memo[1] and now - _container_memo[0] < 20:
        return _container_memo[1]
    found = FALLBACK_CONTAINER
    try:
        out = subprocess.run(["waydroid", "status"], capture_output=True, text=True, timeout=10).stdout
        m = re.search(r"^IP address:\s*(\d+\.\d+\.\d+\.\d+)", out, re.M)
        if m:
            found = m.group(1) + ":5555"
    except (OSError, subprocess.SubprocessError):
        pass
    _container_memo = (now, found)
    return found


def _route_field(field):
    try:
        out = subprocess.run(["ip", "-4", "route", "get", "8.8.8.8"], capture_output=True, text=True,
                             timeout=5).stdout.split()
        return out[out.index(field) + 1]
    except (OSError, subprocess.SubprocessError, ValueError, IndexError):
        return None


def lan_iface():
    return os.environ.get("GB_LAN_IFACE") or _route_field("dev")


def lan_ip():
    ip = _route_field("src")
    if ip:
        return ip
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return None
    finally:
        s.close()


def container_addresses():
    """Every IPv4 address the container may connect from: eth0 on the bridge, plus eth1 when the LAN presence
    (gb-lan) is on. Asked from the running container; empty entries are dropped."""
    cont = container()
    addrs = {cont.split(":")[0]}
    try:
        out = subprocess.run(["adb", "-s", cont, "shell", "ip -4 -o addr show"], capture_output=True, text=True,
                             timeout=8).stdout
        # only the container's real NICs; loopback and its internal virtual addresses never connect to us
        for m in re.finditer(r"^\d+:\s+eth\d+\s+inet (\d+\.\d+\.\d+\.\d+)/", out, re.M):
            addrs.add(m.group(1))
    except (OSError, subprocess.SubprocessError):
        pass
    return addrs


def input_allow():
    """Peers allowed to inject input into the phone: this host, the container, and anything listed in
    GB_INPUT_ALLOW_EXTRA. Returned as the comma-separated string input_relay.py expects."""
    peers = {"127.0.0.1"} | container_addresses()
    ip = lan_ip()
    if ip:
        peers.add(ip)
    peers |= {a for a in os.environ.get("GB_INPUT_ALLOW_EXTRA", "").split(",") if a}
    return ",".join(sorted(peers))
