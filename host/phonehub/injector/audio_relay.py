#!/usr/bin/env python3
"""Plays the phone's cast audio on this laptop.

The GBOS Cast app (AudioStreamer.java) captures the phone's own playback audio and connects here:
    "GBAUD1 <token> <rate> <channels>\n"  then raw signed 16-bit little-endian interleaved PCM.
Each connection is piped into `pw-cat --playback --raw` (PipeWire). One client at a time: a new connection
replaces the old one, so a restarted cast never has to wait for a stale socket to time out.

Runs on its own (see gb-audio.service in the repo), independent of the cast session, so a session restart
never touches audio and audio never touches the session.

Audio follows the GBOS cast window: the phone keeps streaming whether or not a window is open, so this relay
watches AppCastActivity come and go in the container (activity events from logcat, event-driven and cheap)
and only plays while one is open. A connection that arrives with no window is closed without an "OK";
an open window answers "OK\n" after the header, which is the phone's cue to mute its own speaker. When the
last window closes, the connection is dropped, which un-mutes the phone.

Env: GB_AUDIO_PORT (8101) · GB_AUDIO_LATENCY_MS (60: PipeWire node latency; raise if it crackles)
     GB_AUDIO_DELAY_MS (0: silence prepended on each connection, to line the sound up with the video)
"""
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.join(_os.path.dirname(_os.path.abspath(__file__)), "..", "lib"))
import gbos_env  # noqa: E402  shared runtime config: private run dir, container/LAN discovery
import os
import re
import socket
import subprocess
import sys
import threading
import time

PORT = int(os.environ.get("GB_AUDIO_PORT", "8101"))
LATENCY_MS = int(os.environ.get("GB_AUDIO_LATENCY_MS", "60"))
DELAY_MS = int(os.environ.get("GB_AUDIO_DELAY_MS", "0"))
TOKEN = "gbos-cast-v1"          # same value as CaptureService.INPUT_TOKEN
ACT = "com.google.ambient.streaming/.multidevicesync.appcast.receiver.AppCastActivity"

lock = threading.Lock()
current = {"conn": None, "proc": None}
windows = {"n": 0}              # live AppCastActivity instances (a re-skin briefly has two)


def adb(*args, timeout=15):
    return subprocess.run(["adb", "-s", gbos_env.container(), *args], capture_output=True, text=True, timeout=timeout).stdout


def drop_current(why):
    with lock:
        conn, proc = current["conn"], current["proc"]
    if conn is not None:
        log(f"dropping the audio link: {why}")
        try:
            conn.shutdown(socket.SHUT_RDWR)
        except Exception:
            pass


def set_windows(n, why):
    was = windows["n"]
    windows["n"] = max(0, n)
    if (was > 0) != (windows["n"] > 0):
        log(f"cast window {'OPEN' if windows['n'] else 'CLOSED'} ({why})")
        if not windows["n"]:
            drop_current("no cast window")


def initial_windows():
    try:
        out = adb("shell", "dumpsys activity activities")
        return sum(1 for ln in out.splitlines() if "ActivityRecord" in ln and "AppCastActivity" in ln and "{" in ln)
    except Exception:
        return 0


def watch_windows():
    """Follow activity lifecycle events in the container; reconnects if adb or the container goes away."""
    ev = re.compile(r"(wm_create_activity|wm_destroy_activity|am_proc_died)\b.*")
    while True:
        try:
            set_windows(initial_windows(), "initial state")
            p = subprocess.Popen(
                ["adb", "-s", gbos_env.container(), "logcat", "-b", "events", "-T", "1", "-v", "brief",
                 "wm_create_activity:I", "wm_destroy_activity:I", "am_proc_died:I", "*:S"],
                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, bufsize=1)
            for line in p.stdout:
                m = ev.search(line)
                if not m:
                    continue
                if "com.google.ambient.streaming" not in line:
                    continue
                if m.group(1) == "wm_create_activity" and "AppCastActivity" in line:
                    set_windows(windows["n"] + 1, "activity created")
                elif m.group(1) == "wm_destroy_activity" and "AppCastActivity" in line:
                    set_windows(windows["n"] - 1, "activity destroyed")
                elif m.group(1) == "am_proc_died" and "com.google.ambient.streaming," in line:
                    set_windows(0, "ambient process died")
            p.wait()
        except Exception as e:
            log(f"window watcher: {type(e).__name__}: {e}")
        time.sleep(5)


def log(msg):
    print(f"[{time.strftime('%H:%M:%S')}][audio] {msg}", flush=True)


def private_peer(ip):
    if ip.startswith(("10.", "192.168.", "127.")):
        return True
    return ip.startswith("172.") and 16 <= int(ip.split(".")[1]) <= 31


def read_header(conn):
    buf = b""
    conn.settimeout(5)
    while b"\n" not in buf and len(buf) < 256:
        chunk = conn.recv(1)
        if not chunk:
            return None
        buf += chunk
    return buf.decode("ascii", "replace").strip()


def serve(conn, addr):
    ip = addr[0]
    proc = None
    try:
        if not private_peer(ip):
            log(f"refused {ip}: not a LAN address")
            return
        hdr = read_header(conn)
        parts = (hdr or "").split()
        if len(parts) != 4 or parts[0] != "GBAUD1" or parts[1] != TOKEN:
            log(f"refused {ip}: bad header {hdr!r}")
            return
        rate, channels = int(parts[2]), int(parts[3])
        if not (8000 <= rate <= 192000 and 1 <= channels <= 8):
            log(f"refused {ip}: odd format {rate} Hz x{channels}")
            return
        if windows["n"] <= 0:
            return                      # no cast window: close silently (no "OK"), the phone keeps its own sound
        proc = subprocess.Popen(
            ["pw-cat", "--playback", "--raw", "--format", "s16", "--rate", str(rate),
             "--channels", str(channels), "--latency", f"{LATENCY_MS}ms",
             "--media-role", "Movie", "-"],
            stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        with lock:
            old_conn, old_proc = current["conn"], current["proc"]
            current.update(conn=conn, proc=proc)
        # a new cast replaces the old one at once
        for obj, how in ((old_conn, "close"), (old_proc, "kill")):
            if obj is not None:
                try:
                    getattr(obj, how)()
                except Exception:
                    pass
        conn.sendall(b"OK\n")            # the phone mutes its own speaker only after this
        log(f"{ip} connected: {rate} Hz x{channels} -> pw-cat (latency {LATENCY_MS} ms, delay {DELAY_MS} ms)")
        if DELAY_MS:
            proc.stdin.write(b"\0" * (rate * channels * 2 * DELAY_MS // 1000))
        conn.settimeout(None)
        total = 0
        while True:
            data = conn.recv(8192)
            if not data:
                break
            total += len(data)
            proc.stdin.write(data)
            proc.stdin.flush()
        log(f"{ip} closed after {total // 1024} KiB")
    except (BrokenPipeError, ConnectionError, OSError) as e:
        log(f"{ip} ended: {type(e).__name__}: {e}")
    finally:
        try:
            conn.close()
        except Exception:
            pass
        with lock:
            if current["conn"] is conn:
                current.update(conn=None, proc=None)
        if proc is not None:
            try:
                proc.stdin.close()
                proc.wait(timeout=2)
            except Exception:
                proc.kill()


def main():
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", PORT))
    srv.listen(4)
    threading.Thread(target=watch_windows, daemon=True).start()
    log(f"listening on :{PORT} (pw-cat latency {LATENCY_MS} ms, delay {DELAY_MS} ms)")
    while True:
        conn, addr = srv.accept()
        conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        threading.Thread(target=serve, args=(conn, addr), daemon=True).start()


if __name__ == "__main__":
    sys.exit(main())
