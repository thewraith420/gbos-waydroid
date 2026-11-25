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
import struct
import subprocess
import sys
import threading
import time
from pathlib import Path

APPDIR = Path(__file__).resolve().parent
AGENT = "/data/local/tmp/gb-phone-agent.sh"
TOUCH_JAR = Path(os.environ.get("GB_TOUCH_JAR") or APPDIR.parent / "touch" / "gb-touch.jar")
TOUCH_REMOTE = "/data/local/tmp/gb-touch.jar"


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


STATIC_BAD_PORTS = frozenset({33941})


def _skip_dns_name(pkt, off):
    jumps = 0
    n = len(pkt)
    while off < n:
        ln = pkt[off]
        if ln == 0:
            return off + 1
        if (ln & 0xC0) == 0xC0:
            return n if off + 1 >= n else off + 2
        off += 1 + ln
        jumps += 1
        if jumps > 64:
            return n
    return n


def _parse_mdns_reply(pkt):
    """Extract (srv_ports, a_ips) from DNS answer/authority/additional RRs."""
    if len(pkt) < 12:
        return [], set()
    _, _, qd, an, ns, ar = struct.unpack_from("!HHHHHH", pkt, 0)
    off = 12
    for _ in range(qd):
        off = _skip_dns_name(pkt, off) + 4
        if off > len(pkt):
            return [], set()
    srv_ports, a_ips = [], set()
    for _ in range(an + ns + ar):
        off = _skip_dns_name(pkt, off)
        if off + 10 > len(pkt):
            break
        rtype, _, _, rdlen = struct.unpack_from("!HHIH", pkt, off)
        off += 10
        rdata_end = off + rdlen
        if rdata_end > len(pkt):
            break
        if rtype == 33 and rdlen >= 6:       # SRV: priority, weight, port, target
            _, _, port = struct.unpack_from("!HHH", pkt, off)
            if 1 <= port <= 65535 and port not in srv_ports:
                srv_ports.append(port)
        elif rtype == 1 and rdlen == 4:      # A: IPv4 address
            a_ips.add(".".join(str(b) for b in pkt[off:off + 4]))
        off = rdata_end
    return srv_ports, a_ips


def mdns_adb_ports(phone_ip, timeout=1.0):
    """Query _adb-tls-connect._tcp.local via multicast and direct unicast to phone_ip."""
    def _enc_name(name):
        return b"".join(bytes([len(p)]) + p.encode() for p in name.split(".")) + b"\x00"

    pkt = (
        struct.pack("!HHHHHH", 0, 0, 1, 0, 0, 0)
        + _enc_name("_adb-tls-connect._tcp.local")
        + struct.pack("!HH", 12, 0x8001)
    )
    ports = []
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            s.sendto(pkt, ("224.0.0.251", 5353))
        except OSError:
            pass
        try:
            s.sendto(pkt, (phone_ip, 5353))
        except OSError:
            pass
        deadline = time.monotonic() + timeout
        while True:
            rem = deadline - time.monotonic()
            if rem <= 0:
                break
            s.settimeout(rem)
            try:
                data, peer = s.recvfrom(4096)
            except (socket.timeout, OSError):
                break
            srv_ports, a_ips = _parse_mdns_reply(data)
            if srv_ports and (peer[0] == phone_ip or phone_ip in a_ips):
                for p in srv_ports:
                    if p not in ports:
                        ports.append(p)
                # Keep listening briefly (~100ms) for any companion packet, then return fast.
                deadline = min(deadline, time.monotonic() + 0.1)
    finally:
        try:
            s.close()
        except OSError:
            pass
    return ports


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
    return 30000, 65535


def _adb_log_offset():
    p = Path(f"/tmp/adb.{os.getuid()}.log")
    try:
        return p, p.stat().st_size
    except OSError:
        return p, None


def _recent_cert_rejected(log_path, off, serial):
    if off is None:
        return False
    try:
        size = log_path.stat().st_size
        start = off if size >= off else max(0, size - 16384)
        with open(log_path, "rb") as f:
            f.seek(start)
            chunk = f.read(16384).decode("utf-8", "replace")
        return serial in chunk and "SSLV3_ALERT_CERTIFICATE_UNKNOWN" in chunk
    except OSError:
        return False


def _try_connect(serial, port, bad_ports):
    """Attempt `adb connect` on serial; return 'ok', 'cert', or 'fail'."""
    log_path, off = _adb_log_offset()
    r = adb("connect", serial, timeout=4)
    msg = (r.stdout or r.stderr).strip()
    log(f"adb connect {serial}: {msg}")
    if serial in connected_serials():
        return "ok"
    if _recent_cert_rejected(log_path, off, serial):
        return "cert"
    bad_ports.add(port)
    # Do not leave a bad candidate behind; it would slow every later attempt down.
    adb("disconnect", serial, timeout=3)
    return "fail"


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


def find_adbd(phone_ip, skip, lo, hi, bad_ports):
    """Return (serial, cert_rejected) for a working adb connection on phone_ip."""
    for s in connected_serials():
        if s.startswith(phone_ip + ":"):
            log(f"reusing already-connected adb transport {s}")
            return s, False
    drop_stale(phone_ip)

    m_ports = [p for p in mdns_adb_ports(phone_ip) if p not in skip and p not in bad_ports]
    if m_ports:
        log(f"mDNS adbd candidate port(s) on {phone_ip}: {m_ports}")
        for p in m_ports:
            res = _try_connect(f"{phone_ip}:{p}", p, bad_ports)
            if res == "ok":
                return f"{phone_ip}:{p}", False
            if res == "cert":
                return None, True

    log(f"scanning {phone_ip} {lo}-{hi} for adbd")
    t0 = time.time()
    ports = [p for p in open_ports(phone_ip, lo, hi) if p not in skip and p not in bad_ports]
    log(f"open ports: {ports or 'none'}  ({time.time() - t0:.1f}s)")
    for p in ports:
        res = _try_connect(f"{phone_ip}:{p}", p, bad_ports)
        if res == "ok":
            return f"{phone_ip}:{p}", False
        if res == "cert":
            return None, True
    return None, False


def start_agent(serial, token, want_port):
    """Push and start the gesture agent (+ live touch injector when available); return (agent_port, touch_port)."""
    adb("-s", serial, "push", str(APPDIR / "phone_agent.sh"), AGENT, timeout=60)
    adb("-s", serial, "shell", "chmod", "755", AGENT)
    if TOUCH_JAR.exists():
        adb("-s", serial, "push", str(TOUCH_JAR), TOUCH_REMOTE, timeout=60)
    else:
        log(f"WARN: {TOUCH_JAR} missing — live touch disabled (gestures use tap/swipe)")
    r = adb("-s", serial, "shell", "sh", AGENT, "start", token, str(want_port),
            _host_lan_ip() or "", timeout=90)
    out = (r.stdout or "").strip()
    for line in out.splitlines():
        log(f"agent: {line}")
    if r.stderr.strip():
        log(f"agent stderr: {r.stderr.strip()}")
    m_agent = re.search(r"AGENT_PORT=(\d+)\s+AGENT_READY", out)
    m_touch = re.search(r"TOUCH_PORT=(\d+)\s+TOUCH_READY", out)
    agent_port = int(m_agent.group(1)) if m_agent else None
    touch_port = int(m_touch.group(1)) if m_touch else None
    return agent_port, touch_port


def probe_touch_injector(phone_ip, token):
    """Port of a running live touch injector (8110..8129), or None."""
    for p in range(8110, 8130):
        s = None
        try:
            s = socket.create_connection((phone_ip, p), timeout=0.6)
            s.settimeout(0.4)
            s.sendall((token + " ping\n").encode())
            if s.recv(16).startswith(b"pong"):
                return p
        except OSError:
            pass
        finally:
            if s is not None:
                try:
                    s.close()
                except OSError:
                    pass
    return None


def _host_lan_ip():
    """This laptop's current LAN address (follows whatever network it is on; same trick the session uses)."""
    return gbos_env.lan_ip()


def restart_relay(serial, phone_ip, port, token, touch_port=None):
    """Point input_relay at the agent's port (and live touch injector when available), starting or replacing it as needed."""
    pidfile = Path(gbos_env.run_path("gb-input-relay.pid"))
    old = None
    try:
        old = int(pidfile.read_text().strip())
    except Exception:
        pass
    touch_args = ["--phone-touch", phone_ip, str(touch_port), token] if touch_port else []
    existing_touch = []
    if old:
        # Leave an already-correct relay untouched when STATUS polls land here, and preserve its
        # matching --phone-touch target if touch_port was not explicitly supplied by the caller.
        try:
            argv = Path(f"/proc/{old}/cmdline").read_bytes().decode().split("\0")
            if "--phone-touch" in argv:
                i = argv.index("--phone-touch")
                cand = argv[i + 1:i + 4]
                if len(cand) == 3 and cand[0] == phone_ip and cand[2] == token:
                    existing_touch = ["--phone-touch"] + cand
            if not touch_args and existing_touch:
                touch_args = existing_touch
            if "--phone-tcp" in argv:
                i = argv.index("--phone-tcp")
                tcp_match = argv[i + 1:i + 4] == [phone_ip, str(port), token]
                touch_match = (not touch_args) or (existing_touch == touch_args)
                if tcp_match and touch_match:
                    log(f"input_relay pid={old} already points at {phone_ip}:{port}"
                        f"{' + live touch :' + touch_args[2] if touch_args else ''} — leaving it running")
                    return
        except (OSError, ValueError):
            pass
        try:
            os.kill(old, signal.SIGTERM)
            log(f"stopped input_relay pid={old} (updating target)")
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
    log(f"input_relay restarted on {phone_ip}:{port}"
        f"{' + live touch :' + touch_args[2] if touch_args else ' (tap/swipe fallback)'} (pid={p.pid})")


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
        touch_port = probe_touch_injector(phone_ip, args.token)
        log(f"agent is ALREADY alive on {phone_ip}:{alive_port}"
            f"{f' (live touch :{touch_port})' if touch_port else ''} — touch forwarding is active")
        restart_relay(None, phone_ip, alive_port, args.token, touch_port=touch_port)
        return f"ALREADY_ARMED {alive_port}"

    if req_upper.startswith("STATUS") or req_upper.startswith("PROBE"):
        return "ERR not-armed"

    lo, hi = scan_range(args)
    skip = {args.listen_port, args.agent_port, 8097, 8098, 8099, 8100}
    bad_ports = set(STATIC_BAD_PORTS)
    serial = None
    for attempt in range(args.wait_tries):
        serial, cert_rejected = find_adbd(phone_ip, skip, lo, hi, bad_ports)
        if cert_rejected:
            log("adbd rejected this laptop's ADB key (SSLV3_ALERT_CERTIFICATE_UNKNOWN) — re-pair Wireless Debugging once and enable 'Disable adb authorization timeout'")
            return "ERR adb-unpaired"
        if serial:
            break
        log(f"no adbd yet (attempt {attempt + 1}/{args.wait_tries}); waiting for it to bind")
        time.sleep(2)
    if not serial:
        return "ERR no-adbd"

    port, touch_port = start_agent(serial, args.token, args.agent_port)
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
    restart_relay(serial, phone_ip, port, args.token, touch_port=touch_port)
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
    ap.add_argument("--scan-range", default=None, help="LO-HI; defaults to 30000-65535")
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
