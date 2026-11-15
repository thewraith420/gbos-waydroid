#!/usr/bin/env python3
# input_relay.py — receive tap/swipe commands from feed_live (inside the GBOS container, via
# `adb reverse tcp:8100`) and replay them on the real Pixel.
# Protocol (newline-delimited text): "tap <x> <y>"  |  "swipe <x1> <y1> <x2> <y2> <ms>"  |  "key <code>"
#
# Two DELIVERY modes:
#   (default)             `adb -s <pixel> shell input …` — needs the Pixel's adb connected.
#   --phone-tcp IP PORT TOKEN
#                         send gestures to phone_agent.sh, which applies them on the phone. Uses
#                         plain LAN TCP and no adbd, so wireless debugging can be switched off.
#
#   --phone-touch IP PORT TOKEN
#                         ALSO stream live finger events ("touch D|M|U <id> <x> <y>", "touch C") to the
#                         persistent touch injector on the phone (touch_injector/), over ONE kept-open
#                         TCP connection. Without it, or whenever the injector is unreachable, a
#                         finished gesture is replayed as tap/swipe exactly as before, so touch never
#                         stops working.
#
#   python3 input_relay.py <PIXEL_SERIAL> [--phone-tcp IP PORT TOKEN] [--phone-touch IP PORT TOKEN]
import os, select, socket, subprocess, sys, time

_argv = list(sys.argv[1:])
PHONE = None
if "--phone-tcp" in _argv:
    _i = _argv.index("--phone-tcp")
    PHONE = (_argv[_i + 1], int(_argv[_i + 2]), _argv[_i + 3])
    del _argv[_i:_i + 4]

TOUCH = None
if "--phone-touch" in _argv:
    _i = _argv.index("--phone-touch")
    TOUCH = (_argv[_i + 1], int(_argv[_i + 2]), _argv[_i + 3])
    del _argv[_i:_i + 4]

PIXEL = _argv[0] if _argv else ""
if not PIXEL:
    sys.exit("usage: input_relay.py <PIXEL_SERIAL|lan> [--phone-tcp IP PORT TOKEN] [--phone-touch IP PORT TOKEN]")
# GB_RELAY_BIND: the container reaches this socket either through `adb reverse` (loopback) or
# directly on the LAN; the latter is needed because the reverse tunnel has been measured to accept
# connections and forward zero bytes after a while. GB_INPUT_ALLOW keeps a LAN-bound relay from
# letting any other host drive the phone.
HOST = os.environ.get("GB_RELAY_BIND", "0.0.0.0")
ALLOW = [a for a in os.environ.get("GB_INPUT_ALLOW", "").split(",") if a]
PORT = 8100
_psock = None


def _alive(s):
    """False when the peer has closed. A write to such a socket still succeeds, so without this
    check the first gesture after the agent is restarted (e.g. a Re-arm on the phone) is written
    into a dead socket and silently lost."""
    try:
        r, _, _ = select.select([s], [], [], 0)
        if not r:
            return True
        return s.recv(1, socket.MSG_PEEK) != b""
    except OSError:
        return False


def phone_send(line):
    """Forward one already-validated protocol line to the phone agent (token-prefixed).
    Connects per gesture and closes so nc flushes EOF and the line executes immediately.
    """
    err = None
    for attempt in range(8):
        s = None
        try:
            s = socket.create_connection(PHONE[:2], timeout=2)
            s.sendall((PHONE[2] + " " + line + "\n").encode())
            s.close()
            return
        except OSError as e:
            err = e
            if s:
                try:
                    s.close()
                except Exception:
                    pass
            time.sleep(0.1)
    print("phone input err:", err, flush=True)

_tsock = None
_tdown_until = 0.0        # after a failed connect, don't stall every finger event retrying
_gest = {}                # finger id -> [x0, y0, t0, live_ok] for the tap/swipe fallback


def touch_send(rest):
    """Send one live-touch line to the injector. Persistent connection, TCP_NODELAY, no per-event
    connect. Returns False if it could not be delivered (caller falls back)."""
    global _tsock, _tdown_until
    if TOUCH is None or time.time() < _tdown_until:
        return False
    data = (TOUCH[2] + " " + rest + "\n").encode()
    # The injector never writes unprompted, so "readable" can only mean the peer closed. A send to a
    # closed connection succeeds once before the reset arrives, which would swallow the gesture.
    if _tsock is not None and not _alive(_tsock):
        try:
            _tsock.close()
        except OSError:
            pass
        _tsock = None
    for _ in range(2):
        try:
            if _tsock is None:
                _tsock = socket.create_connection(TOUCH[:2], timeout=1.0)
                _tsock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                _tsock.settimeout(1.0)
                print("touch injector connected", flush=True)
            _tsock.sendall(data)
            return True
        except OSError:
            if _tsock is not None:
                try:
                    _tsock.close()
                except OSError:
                    pass
                _tsock = None
    _tdown_until = time.time() + 3.0
    print("touch injector unreachable — falling back to tap/swipe for 3 s", flush=True)
    return False


def handle_touch(p):
    """p = ["touch", verb, ...]. Live path first; if the gesture could not be streamed end to end,
    replay it on release as tap/swipe (the pre-injector behaviour)."""
    verb = p[1] if len(p) > 1 else ""
    if verb == "C":
        touch_send("C")
        _gest.clear()
        return
    if len(p) < 5 or verb not in ("D", "M", "U"):
        return
    fid = p[2]
    try:
        x, y = float(p[3]), float(p[4])
    except ValueError:
        return
    ok = touch_send(" ".join(p[1:]))
    if verb == "D":
        _gest[fid] = [x, y, time.time(), ok]
        print(f"touch D{fid} {int(x)},{int(y)} live={ok}", flush=True)
    elif verb == "U":
        g = _gest.pop(fid, None)
        print(f"touch U{fid} {int(x)},{int(y)} live={ok}", flush=True)
        if g and fid == "0" and not (ok and g[3]):
            x0, y0, t0 = g[0], g[1], g[2]
            if ((x - x0) ** 2 + (y - y0) ** 2) ** 0.5 < 18:
                handle(f"tap {int(x0)} {int(y0)}")
            else:
                dur = int(min(350, max(160, (time.time() - t0) * 600)))
                handle(f"swipe {int(x0)} {int(y0)} {int(x)} {int(y)} {dur}")


def run(args):
    try:
        subprocess.Popen(["adb", "-s", PIXEL, "shell", "input"] + args,
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception as e:
        print("input err:", e, flush=True)

def handle(line):
    p = line.split()
    if not p: return
    if p[0] == "touch":
        handle_touch(p)
        return
    if PHONE is not None:
        print(line, flush=True)
        phone_send(line)
        return
    if p[0] == "tap" and len(p) >= 3:
        print(f"tap {p[1]},{p[2]}", flush=True); run(["tap", p[1], p[2]])
    elif p[0] == "swipe" and len(p) >= 5:
        dur = p[5] if len(p) >= 6 else "150"
        print(f"swipe {p[1]},{p[2]}->{p[3]},{p[4]} {dur}ms", flush=True)
        run(["swipe", p[1], p[2], p[3], p[4], dur])
    elif p[0] == "key" and len(p) >= 2:
        print(f"key {p[1]}", flush=True); run(["keyevent", p[1]])
    elif p[0] == "launch" and len(p) >= 2:
        pkg = p[1]
        print(f"launch {pkg}", flush=True)
        try:
            subprocess.Popen(
                ["adb", "-s", PIXEL, "shell", f"input keyevent KEYCODE_WAKEUP; monkey -p {pkg} -c android.intent.category.LAUNCHER 1"],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
            )
        except Exception as e:
            print("launch err:", e, flush=True)

srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
srv.bind((HOST, PORT)); srv.listen(1)
print(f"input_relay listening {HOST}:{PORT} -> "
      f"{'phone agent ' + str(PHONE[:2]) if PHONE else 'Pixel ' + PIXEL}"
      f"{' + live touch ' + str(TOUCH[:2]) if TOUCH else ''}", flush=True)
while True:
    conn, addr = srv.accept()
    if ALLOW and addr[0] not in ALLOW:
        print("refused input client from", addr, flush=True)
        conn.close()
        continue
    print("input client connected:", addr, flush=True)
    buf = b""
    try:
        while True:
            b = conn.recv(4096)
            if not b: break
            buf += b
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                handle(line.decode("utf-8", "replace").strip())
    except OSError:
        pass
    finally:
        conn.close()
        print("input client disconnected", flush=True)
