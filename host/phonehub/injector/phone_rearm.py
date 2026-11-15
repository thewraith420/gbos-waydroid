#!/usr/bin/env python3
"""phone_rearm.py — re-arm the phone's gesture agent on demand, without leaving adb enabled.

WHY THIS EXISTS
---------------
After a phone reboot the shell-uid gesture agent (`phone_agent.sh`) is gone: `setsid` survives adbd
going away, not a reboot. Re-starting it needs uid `shell`, and an ordinary app cannot start a
`shell` process — so a button *inside* the GBOS Cast app cannot do it directly.

What the app CAN do (with `WRITE_SECURE_SETTINGS`, granted once over adb) is toggle the developer
setting `adb_wifi_enabled`. So the split is:

    app button  ->  wireless debugging ON  ->  "REARM" over LAN to this server
    this server ->  find adbd's port, connect, start the agent, then wireless debugging OFF again

The exposure window is the few seconds the bootstrap takes, and nothing needs to stay on afterwards.

WHY IT SCANS FOR THE PORT
-------------------------
Wireless debugging picks a random port each time it starts, and normally you would find it with
mDNS. On this host mDNS does not work at all — `adb mdns check` prints nothing and `adb mdns
services` lists no services even with the phone connected — so a fresh port would be unreachable by
name. Instead we scan the phone's LAN address for open ports and let `adb connect` itself decide
which one is adbd (it does the TLS handshake and the key check, so a wrong port fails fast).

USAGE
    python3 phone_rearm.py --phone 192.0.2.99 --agent-port 8110 --token <hex> [--relay-pid FILE]

The app connects to TCP 8091 (--listen-port) and sends a line starting with "REARM". Only the
configured phone IP is accepted; anything else is logged and dropped.
"""
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.join(_os.path.dirname(_os.path.abspath(__file__)), "..", "lib"))
import gbos_env  # noqa: E402  shared runtime config: private run dir, container/LAN discovery
import argparse
import os
import re
import signal
import socket
import subprocess
import sys
import threading
import time
from pathlib import Path

APPDIR = Path(__file__).resolve().parent
AGENT = "/data/local/tmp/gb-phone-agent.sh"


def log(msg):
    print(f"[{time.strftime('%H:%M:%S')}][rearm] {msg}", flush=True)


def adb(*args, timeout=30):
    try:
        return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired as e:
        return subprocess.CompletedProcess(["adb", *args], returncode=-1, stdout="", stderr=f"TimeoutExpired: {e}")


def connected_serials():
    out = adb("devices").stdout
    return [l.split()[0] for l in out.splitlines()[1:] if l.strip().endswith("device")]


BAD_PORTS = {33941}


def open_ports(ip, lo, hi, workers=256, timeout=0.12):
    """Every TCP port on `ip` that accepts a connection. Refused comes back instantly on a LAN."""
    import queue

    found, q = [], queue.Queue()
    for p in range(lo, hi + 1):
        q.put(p)

    def worker():
        while True:
            try:
                p = q.get_nowait()
            except queue.Empty:
                return
            s = socket.socket()
            s.settimeout(timeout)
            try:
                if s.connect_ex((ip, p)) == 0:
                    found.append(p)
            finally:
                s.close()

    ts = [threading.Thread(target=worker, daemon=True) for _ in range(workers)]
    for t in ts:
        t.start()
    for t in ts:
        t.join()
    return sorted(found)


# Ports this rig owns or knows about; never an adbd candidate.
def scan_range(args):
    if args.scan_range:
        lo, hi = (int(x) for x in args.scan_range.split("-"))
        return lo, hi
    return 30000, 50000


def drop_stale(phone_ip):
    """Clear leftover `offline` transports for this phone before touching it.

    MEASURED: a stale serial (`192.0.2.99:38037 offline`, left over from an earlier pairing) makes
    `adb connect` sit out its full timeout — 10 s of the re-arm spent on a port that was never
    adbd. Dropping them first turns that into a no-op.
    """
    out = adb("devices").stdout
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[0].startswith(phone_ip + ":") and parts[1] != "device":
            log(f"dropping stale transport {parts[0]} ({parts[1]})")
            adb("disconnect", parts[0], timeout=10)


def find_adbd(phone_ip, skip, lo, hi):
    """Return the serial ("ip:port") of a working adb connection, or None."""
    drop_stale(phone_ip)
    log(f"scanning {phone_ip} {lo}-{hi} for adbd (mDNS is unavailable on this host)")
    t0 = time.time()
    ports = [p for p in open_ports(phone_ip, lo, hi) if p not in skip and p not in BAD_PORTS]
    log(f"open ports: {ports or 'none'}  ({time.time() - t0:.1f}s)")
    for p in ports:
        serial = f"{phone_ip}:{p}"
        r = adb("connect", serial, timeout=4)
        msg = (r.stdout or r.stderr).strip()
        log(f"adb connect {serial}: {msg}")
        if serial in connected_serials():
            return serial
        BAD_PORTS.add(p)
        # Do not leave a bad candidate behind; it would slow every later attempt down.
        adb("disconnect", serial, timeout=3)
    return None


def start_agent(serial, token, want_port):
    """Push and start the gesture agent; return the port it actually got."""
    adb("-s", serial, "push", str(APPDIR / "phone_agent.sh"), AGENT, timeout=60)
    adb("-s", serial, "shell", "chmod", "755", AGENT)
    r = adb("-s", serial, "shell", "sh", AGENT, "start", token, str(want_port), timeout=90)
    out = (r.stdout or "").strip()
    for line in out.splitlines():
        log(f"agent: {line}")
    if r.stderr.strip():
        log(f"agent stderr: {r.stderr.strip()}")
    m = re.search(r"AGENT_PORT=(\d+)\s+AGENT_READY", out)
    return int(m.group(1)) if m else None


def _host_lan_ip():
    """This laptop's current LAN address (follows whatever network it is on; same trick the session uses)."""
    return gbos_env.lan_ip()


def restart_relay(serial, phone_ip, port, token):
    """Point input_relay at the agent's port, starting it if it is not running yet.

    MEASURED (2026-10-30): the old version only repointed a relay that already existed and only
    when the agent landed on a different port. A session started with wireless debugging already
    off has no agent, so no relay was ever spawned; and an agent that kept the requested port left
    the old relay pointing nowhere. Both looked like "Re-arm succeeded but touch is dead".
    """
    pidfile = Path(gbos_env.run_path("gb-input-relay.pid"))
    old = None
    try:
        old = int(pidfile.read_text().strip())
    except Exception:
        pass
    touch_args = []
    if old:
        # MEASURED (2026-11-03): the app's STATUS poll lands here with serial=None while the agent is
        # healthy; the old code killed the working relay and then died building the new command line,
        # leaving NO relay (touch, app clicks and notification taps all dead until a session restart).
        # So: leave a relay alone if it already points at this agent, and carry its live-touch args over.
        try:
            argv = Path(f"/proc/{old}/cmdline").read_bytes().decode().split("\0")
            if "--phone-tcp" in argv:
                i = argv.index("--phone-tcp")
                if argv[i + 1:i + 3] == [phone_ip, str(port)]:
                    log(f"input_relay pid={old} already points at {phone_ip}:{port} — leaving it running")
                    return
            if "--phone-touch" in argv:
                i = argv.index("--phone-touch")
                touch_args = ["--phone-touch"] + argv[i + 1:i + 4]
        except (OSError, ValueError):
            pass
        try:
            os.kill(old, signal.SIGTERM)
            log(f"stopped input_relay pid={old} (it pointed at the old port)")
        except OSError:
            pass
    logf = open(gbos_env.run_path("appcast_input_relay.log"), "a")
    relay_env = dict(os.environ)
    relay_env["GB_RELAY_BIND"] = "0.0.0.0"
    relay_env["GB_INPUT_ALLOW"] = gbos_env.input_allow()
    p = subprocess.Popen(["python3", str(APPDIR / "input_relay.py"), serial or "lan",
                          "--phone-tcp", phone_ip, str(port), token] + touch_args,
                         stdout=logf, stderr=subprocess.STDOUT,
                         env=relay_env,
                         start_new_session=True, stdin=subprocess.DEVNULL)
    pidfile.write_text(str(p.pid))
    log(f"input_relay restarted on {phone_ip}:{port} (pid={p.pid})")


def check_alive_agent(phone_ip, token, preferred_port=None):
    """Port of the live input agent, or None.

    MEASURED (2026-11-03): the agent defaults to 8099 and walks up when that is busy (it landed on 8102),
    while the live touch injector owns 8110+. The old probe tried [8099, preferred, 8110], so it never
    found the agent and happily "found" the injector, which accepts any TCP connection: the relay was
    pointed at the injector and every tap, launch and notification click vanished. The injector answers
    "pong" to a ping and the agent never replies, which tells them apart.
    """
    candidates = [8099, preferred_port] + list(range(8099, 8110))
    seen = set()
    for p in candidates:
        if not p or p in seen or p == 8097 or p >= 8110:
            continue
        seen.add(p)
        try:
            s = socket.create_connection((phone_ip, p), timeout=0.8)
        except OSError:
            continue
        try:
            s.settimeout(0.4)
            s.sendall((token + " ping\n").encode())
            try:
                if s.recv(16).startswith(b"pong"):
                    continue          # that is the touch injector, not the agent
            except OSError:
                pass                  # silence / reset: the agent (or an unrelated listener)
            return p
        except OSError:
            pass
        finally:
            try:
                s.close()
            except OSError:
                pass
    return None


def handle(args, req):
    """REARM -> find adb, start the agent, report, then switch wireless debugging back off."""
    phone_ip = args.phone
    req_upper = req.strip().upper()
    if not (req_upper.startswith("REARM") or req_upper.startswith("STATUS") or req_upper.startswith("PROBE")):
        return "ERR unknown request"

    # Fast path: Is the gesture agent already alive and responding to our token?
    alive_port = check_alive_agent(phone_ip, args.token, args.agent_port)
    if alive_port:
        log(f"agent is ALREADY alive on {phone_ip}:{alive_port} — touch forwarding is active")
        restart_relay(None, phone_ip, alive_port, args.token)
        return f"ALREADY_ARMED {alive_port}"

    if req_upper.startswith("STATUS") or req_upper.startswith("PROBE"):
        return "ERR not-armed"

    lo, hi = scan_range(args)
    skip = {args.listen_port, args.agent_port, 8097, 8098, 8099, 8100}
    serial = None
    for attempt in range(args.wait_tries):
        serial = find_adbd(phone_ip, skip, lo, hi)
        if serial:
            break
        log(f"no adbd yet (attempt {attempt + 1}/{args.wait_tries}); waiting for it to bind")
        time.sleep(2)
    if not serial:
        return "ERR no-adbd"

    port = start_agent(serial, args.token, args.agent_port)
    if not port:
        return "ERR agent-failed"

    try:
        persist = Path(gbos_env.STATE) / "agent-token"   # survives reboots (see appcast_session.py)
        persist.parent.mkdir(parents=True, exist_ok=True)
        persist.write_text(args.token)
        os.chmod(persist, 0o600)                         # the token authorises input on the phone
    except Exception:
        pass

    # Always (re)start the relay: a session begun with adb already off has none at all, and an
    # agent that kept the requested port would leave the old relay pointing at a dead socket.
    restart_relay(serial, phone_ip, port, args.token)
    # Leave no debugging surface behind: the bootstrap is done, so switch it off again from the
    # shell we already have. This is the whole point of routing the button through the laptop.
    if args.disable_after:
        r = adb("-s", serial, "shell", "settings", "put", "global", "adb_wifi_enabled", "0")
        log(f"wireless debugging off again ({(r.stdout or r.stderr).strip() or 'ok'})")
        adb("disconnect", serial)
    return f"OK {port}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--phone", required=True, help="the phone's LAN IP")
    ap.add_argument("--token", required=True, help="token the re-armed agent must accept")
    ap.add_argument("--agent-port", type=int, default=8110,
                    help="port to ask for (the agent re-uses it if it is free)")
    ap.add_argument("--listen-port", type=int, default=8091)
    ap.add_argument("--scan-range", default=None, help="LO-HI; defaults to 30000-50000")
    ap.add_argument("--wait-tries", type=int, default=8,
                    help="how many times to rescan while adbd is still binding")
    ap.add_argument("--no-disable-after", dest="disable_after", action="store_false",
                    help="leave wireless debugging on afterwards (debugging only)")
    args = ap.parse_args()

    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", args.listen_port))
    srv.listen(2)
    log(f"listening 0.0.0.0:{args.listen_port}; only {args.phone} may re-arm")

    while True:
        conn, addr = srv.accept()
        peer, req = addr[0], ""
        try:
            conn.settimeout(5)
            req = conn.recv(256).decode("utf-8", "replace")
            log(f"request from {peer}: {req.strip()!r}")
            if peer != args.phone:
                try:
                    cached = Path(gbos_env.run_path("gb-appcast-phone-ip")).read_text().strip()
                except Exception:
                    cached = ""
                if peer == cached or peer.split(".")[:3] == args.phone.split(".")[:3]:
                    log(f"updating phone IP from {args.phone} to {peer}")
                    args.phone = peer
                else:
                    log(f"refused: peer {peer} != configured {args.phone}")
                    conn.sendall(b"ERR not-allowed\n")
                    continue
            reply = handle(args, req)
        except Exception as e:                      # never let one bad request kill the server
            reply = f"ERR {type(e).__name__}: {e}"
            log(f"failed: {reply}")
        finally:
            try:
                conn.sendall((reply + "\n").encode())
            except OSError:
                pass
            conn.close()
            log(f"replied {reply}")


if __name__ == "__main__":
    main()
