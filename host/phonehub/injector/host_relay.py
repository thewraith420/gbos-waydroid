#!/usr/bin/env python3
# host_relay.py — stream the Pixel's live screen (H.264) to the GBOS container.
# The container reaches us via `adb reverse tcp:8099 tcp:8099` (connects to 127.0.0.1:8099).
#
# Two SOURCE modes:
#
#   pull (default)  We run `adb exec-out screenrecord` on the Pixel and pipe its output.
#                   Restarts screenrecord each time it hits the 180 s cap (each restart re-emits
#                   SPS/PPS/IDR, which feed_live.js re-caches). One client at a time.
#                   Needs the Pixel's adb to stay connected for the whole session.
#
#   --listen        The PHONE pushes to us (phone_agent.sh). Nothing here touches the Pixel's adb,
#                   so wireless debugging can be switched off after the agent starts. The agent
#                   loops screenrecord too, so the phone reconnects every ~170 s; we just accept
#                   the next connection and keep feeding the same container client.
#
#   python3 host_relay.py [PIXEL_SERIAL] [WxH] [bitrate] [--listen [port]]
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.join(_os.path.dirname(_os.path.abspath(__file__)), "..", "lib"))
import gbos_env  # noqa: E402  shared runtime config: private run dir, container/LAN discovery
import json, os, re, select, socket, struct, subprocess, sys, threading

argv = [a for a in sys.argv[1:]]
LISTEN = "--listen" in argv
PHONE_PORT = int(os.environ.get("GB_PHONE_VIDEO_PORT", "8098"))
if LISTEN:
    i = argv.index("--listen")
    if i + 1 < len(argv) and argv[i + 1].isdigit():
        PHONE_PORT = int(argv[i + 1])
        del argv[i:i + 2]
    else:
        del argv[i:i + 1]

PIXEL = argv[0] if len(argv) > 0 else ""
if not LISTEN and not PIXEL:
    sys.exit("usage: host_relay.py PIXEL_SERIAL [WxH] [bitrate]   |   host_relay.py --listen [port]")
SIZE  = argv[1] if len(argv) > 1 else "1376x736"
BR    = argv[2] if len(argv) > 2 else "6000000"
# GB_RELAY_BIND: see input_relay.py — the container can reach the laptop on the LAN, which survives
# the adb reverse tunnel breaking in one direction.
HOST, PORT = os.environ.get("GB_RELAY_BIND", "127.0.0.1"), 8099
# Where the accepted phone's LAN address is published (see serve_listen).
PHONE_STATE = os.environ.get("GB_PHONE_STATE", gbos_env.run_path("gb-appcast-phone-ip"))


def screenrecord():
    return subprocess.Popen(
        ["adb", "-s", PIXEL, "exec-out", "screenrecord",
         "--output-format=h264", "--size", SIZE, "--bit-rate", BR,
         "--time-limit", "180", "-"],
        stdout=subprocess.PIPE)


APPS_PORT = int(os.environ.get("GB_APPS_PORT", "8094"))
APPS_DIR = os.environ.get("GB_APPS_DIR", os.path.join(gbos_env.STATE, "apps"))
APPS_TOKEN = os.environ.get("GB_APPS_TOKEN", "gbos-cast-v1")     # = CaptureService.INPUT_TOKEN
_PKG_OK = re.compile(r"^[A-Za-z0-9_.]{1,200}$")                  # becomes a filename: no separators


def _read_exact(conn, n):
    buf = bytearray()
    while len(buf) < n:
        chunk = conn.recv(min(65536, n - len(buf)))
        if not chunk:
            raise EOFError("short read")
        buf += chunk
    return bytes(buf)


def _read_int(conn):
    return struct.unpack(">i", _read_exact(conn, 4))[0]


def _recv_apps(conn):
    """One bundle from the phone (AppSync.java): apps.json + icons/<pkg>.png under APPS_DIR."""
    conn.settimeout(20)
    line = bytearray()
    while not line.endswith(b"\n"):
        c = conn.recv(1)
        if not c or len(line) > 128:
            raise ValueError("bad header")
        line += c
    if line.decode("utf-8", "replace").strip() != f"GBAPPS1 {APPS_TOKEN}":
        raise PermissionError("bad token")
    apps, total = [], 0
    while True:
        plen = _read_int(conn)
        if plen == 0:
            break
        if not 0 < plen <= 256 or len(apps) >= 600:
            raise ValueError("bad package length / too many apps")
        pkg = _read_exact(conn, plen).decode("utf-8")
        llen = _read_int(conn)
        if not 0 <= llen <= 512:
            raise ValueError("bad label length")
        label = _read_exact(conn, llen).decode("utf-8", "replace")
        ilen = _read_int(conn)
        total += ilen
        if not 0 < ilen <= 256 * 1024 or total > 48 * 1024 * 1024:
            raise ValueError("bad icon size")
        png = _read_exact(conn, ilen)
        if not _PKG_OK.match(pkg) or not png.startswith(b"\x89PNG"):
            raise ValueError(f"rejected entry {pkg!r}")
        apps.append((pkg, label, png))
    icons = os.path.join(APPS_DIR, "icons")
    os.makedirs(icons, exist_ok=True)
    keep = set()
    for pkg, _label, png in apps:
        keep.add(pkg + ".png")
        tmp = os.path.join(icons, pkg + ".png.tmp")
        with open(tmp, "wb") as f:
            f.write(png)
        os.replace(tmp, os.path.join(icons, pkg + ".png"))
    for stale in os.listdir(icons):
        if stale.endswith(".png") and stale not in keep:
            os.unlink(os.path.join(icons, stale))
    tmp = os.path.join(APPS_DIR, "apps.json.tmp")
    with open(tmp, "w") as f:
        json.dump([{"pkg": p, "label": l} for p, l, _ in apps], f, ensure_ascii=False)
    os.replace(tmp, os.path.join(APPS_DIR, "apps.json"))
    conn.sendall(f"OK {len(apps)}\n".encode())
    return len(apps), total


def serve_apps():
    """Accept the phone's app-list push on its own port (8094), independent of the video path."""
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", APPS_PORT))
    srv.listen(2)
    print(f"app-list receiver on 0.0.0.0:{APPS_PORT} -> {APPS_DIR}", flush=True)
    while True:
        conn, addr = srv.accept()
        try:
            n, nbytes = _recv_apps(conn)
            print(f"app list from {addr[0]}: {n} apps, {nbytes} icon bytes", flush=True)
        except Exception as e:
            print(f"app list from {addr[0]} rejected: {e}", flush=True)
        finally:
            try:
                conn.close()
            except OSError:
                pass


def client_gone(conn):
    """True once the container client has closed (peek, never consume)."""
    try:
        r, _, _ = select.select([conn], [], [], 0)
        if not r:
            return False
        return not conn.recv(1, socket.MSG_PEEK)
    except OSError:
        return True


def serve_listen(srv):
    """Phone-initiated push: the phone connects out to us, so its adb can be off.

    The two sockets have independent lifetimes on purpose. The App Cast receiver recreates its
    player whenever the window is re-established, so the container client disconnects and
    reconnects mid-session; MEASURED: tying the phone socket to the client (closing it on client
    disconnect) left the window frozen on its last frame forever, because the phone's pipeline was
    torn down with it and the replacement client found nothing to read. So the phone stream is
    kept alive and simply discarded while no client is attached.
    """
    threading.Thread(target=serve_apps, name="gb-apps", daemon=True).start()
    psrv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    psrv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    psrv.bind(("0.0.0.0", PHONE_PORT))
    psrv.listen(1)
    psrv.setblocking(False)
    srv.setblocking(False)
    # A learned address from a previous run must not outlive it: if DHCP has since moved the phone,
    # a stale value here points the gesture relay and the Re-arm server at nothing.
    try:
        os.unlink(PHONE_STATE)
    except OSError:
        pass
    print(f"waiting for the phone to push on 0.0.0.0:{PHONE_PORT}", flush=True)

    phone = None
    conn = None
    total = 0
    # The container's client appears a moment AFTER the phone's stream starts (and reappears when
    # the receiver rebuilds its player). Anything read in that window must not be thrown away:
    # feed_live.js needs the stream's SPS/PPS and a keyframe to initialise its decoder, and a
    # static phone screen may not produce another keyframe for a long time — MEASURED: dropping
    # that head left the window black forever with the transport perfectly healthy. So keep a
    # rolling window of recent bytes and replay it to the next client.
    pending = bytearray()
    KEEP = 512 * 1024
    while True:
        watch = [psrv, srv] + ([phone] if phone else [])
        r, _, _ = select.select(watch, [], [], 1.0)

        if phone is None and psrv in r:
            try:
                phone, paddr = psrv.accept()
                print(f"phone push connected from {paddr}", flush=True)
                # The phone dials US, so its LAN address is free here — and it is the only source
                # that survives a DHCP change or wireless debugging being off. Publish it so the
                # supervisor (tools/appcast_up.sh) and the Re-arm server can reach the phone again
                # without anyone typing an IP: gestures are dead until something knows this.
                try:
                    tmp = PHONE_STATE + ".tmp"
                    with open(tmp, "w") as f:
                        f.write(paddr[0])
                    os.replace(tmp, PHONE_STATE)
                except OSError as e:
                    print(f"could not record the phone address: {e}", flush=True)
            except OSError:
                phone = None

        if srv in r:
            try:
                new, addr = srv.accept()
                print("client connected:", addr, flush=True)
                if conn is not None:
                    try:
                        conn.close()
                    except OSError:
                        pass
                conn = new
                total = 0  # blocking sendall: backpressure beats silently dropping frames
                if pending:
                    try:
                        conn.sendall(bytes(pending))
                    except OSError:
                        pass
            except OSError:
                pass

        if phone in r:
            try:
                b = phone.recv(65536)
            except (BlockingIOError, InterruptedError):
                b = None
            except OSError:
                b = b""
            if b == b"":
                # The agent's screenrecord hit its 170 s cap and will reconnect shortly; the
                # container client keeps its decoder across this, so it is a normal cycle.
                print("phone push cycle ended — waiting for the next one", flush=True)
                try:
                    phone.close()
                except OSError:
                    pass
                phone = None
            elif b:
                pending += b
                if len(pending) > KEEP:
                    del pending[:len(pending) - KEEP]
                if conn is not None:
                    try:
                        conn.sendall(b)
                        total += len(b)
                    except OSError:
                        print(f"client disconnected ({total} bytes)", flush=True)
                        try:
                            conn.close()
                        except OSError:
                            pass
                        conn = None


def serve_pull(srv):
    while True:
        conn, addr = srv.accept()
        print("client connected:", addr, flush=True)
        alive = True
        while alive:
            p = screenrecord()
            print("screenrecord started, pid", p.pid, flush=True)
            total = 0
            fd = p.stdout.fileno()
            while True:
                b = os.read(fd, 65536)   # return whatever's available (don't block for a full buffer)
                if not b:
                    break
                try:
                    conn.sendall(b); total += len(b)
                except OSError:
                    alive = False; break
            try:
                p.terminate(); p.wait(timeout=2)
            except Exception:
                pass
            print(f"screenrecord ended ({total} bytes){' — restarting' if alive else ''}", flush=True)
        try: conn.close()
        except Exception: pass
        print("client disconnected", flush=True)


srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
srv.bind((HOST, PORT)); srv.listen(1)
if LISTEN:
    print(f"relay listening on {HOST}:{PORT}, source = phone push ({SIZE}@{BR}), no Pixel adb needed", flush=True)
    serve_listen(srv)
else:
    print(f"relay listening on {HOST}:{PORT}, source Pixel {PIXEL} {SIZE}@{BR}", flush=True)
    serve_pull(srv)
