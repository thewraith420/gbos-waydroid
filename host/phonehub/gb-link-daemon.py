#!/usr/bin/env python3
"""gb-link-daemon.py — local control daemon for GBOS Waydroid widget and shortcuts.
Listens strictly on the Waydroid bridge address (default 192.168.240.1) port 8092.
NOTE: unauthenticated by design; any app in the container can ask it to reconnect or open a cast window.
Allows the Waydroid home screen widget to trigger gb-reconnect.sh and query status.
"""
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.join(_os.path.dirname(_os.path.abspath(__file__)), "lib"))
import gbos_env  # noqa: E402  shared runtime config: private run dir, container/LAN discovery
import json
import os
import socket
import subprocess
import threading
import time
from pathlib import Path

HOST = gbos_env.BRIDGE_IP
PORT = 8092
RECONNECT_SCRIPT = Path(__file__).resolve().parent / "gb-reconnect.sh"


def get_status():
    status = {"cds": "disconnected", "phone": "none", "appcast": "inactive"}
    # Check CDS
    try:
        r = subprocess.run(
            ["adb", "-s", gbos_env.container(), "shell", "dumpsys notification --noredact"],
            capture_output=True, text=True, timeout=3
        )
        if "pkg=com.google.ambient.streaming" in r.stdout and "android.title=String (Connected)" in r.stdout:
            status["cds"] = "connected"
    except Exception:
        pass

    # Check Phone
    try:
        phone_file = Path(gbos_env.run_path("gb-appcast-phone-ip"))
        if phone_file.exists():
            status["phone"] = phone_file.read_text().strip()
    except Exception:
        pass

    # Check AppCast session
    try:
        sess_log = Path(gbos_env.run_path("gb-appcast-session.log"))
        if sess_log.exists():
            lines = sess_log.read_text(errors="ignore").splitlines()[-20:]
            for line in reversed(lines):
                if "frame#" in line:
                    status["appcast"] = "live"
                    break
                if "stream ended" in line:
                    status["appcast"] = "idle"
                    break
    except Exception:
        pass

    return status


def handle_client(conn, addr):
    try:
        conn.settimeout(15)
        raw = conn.recv(1024).decode("utf-8", "replace").strip()
        cmd = raw.upper()

        if cmd == "STATUS":
            st = get_status()
            conn.sendall((json.dumps(st) + "\n").encode())
        elif cmd.startswith("RECONNECT") or cmd.startswith("FIX"):
            # Trigger reconnect in background thread so caller gets instant ack
            def run_rec():
                subprocess.run(["bash", str(RECONNECT_SCRIPT)], capture_output=True)
            threading.Thread(target=run_rec, daemon=True).start()
            conn.sendall(b'{"status":"reconnecting"}\n')
        elif cmd.startswith("LAUNCH_APPCAST"):
            subprocess.run([
                "adb", "-s", gbos_env.container(), "shell",
                "am start --user 10 -n com.google.ambient.streaming/.multidevicesync.appcast.receiver.launcher.RemoteAppsLauncherActivity"
            ], capture_output=True)
            conn.sendall(b'{"status":"launched"}\n')
        elif cmd.startswith("OPEN_PHONE_HOME"):
            # The widget's Open button: a cast window on the phone's home screen. Same intent the Phone Hub
            # sends for an app tap (CLEAR_TASK so onCreate sees OUR extras); the "app" is the home sentinel
            # that feed_live.js turns into wake + Home on the phone.
            subprocess.run([
                "adb", "-s", gbos_env.container(), "shell",
                "am start --user 10 -f 0x10008000 -n com.google.ambient.streaming/.multidevicesync.appcast.receiver.AppCastActivity"
                " --es deviceId b1c2d3e4-0000-4a4a-b4b4-c5c5c5c5c5c5 --es packageName gbos.phone.home --el sessionId 12345"
            ], capture_output=True)
            conn.sendall(b'{"status":"opened"}\n')
        else:
            conn.sendall(b'{"error":"unknown_command"}\n')
    except Exception as e:
        try:
            conn.sendall(json.dumps({"error": str(e)}).encode() + b"\n")
        except Exception:
            pass
    finally:
        conn.close()


def main():
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        srv.bind((HOST, PORT))
    except OSError as e:
        print(f"[gb-link-daemon] bind {HOST}:{PORT} failed: {e}")
        return 1

    srv.listen(5)
    print(f"[gb-link-daemon] listening on {HOST}:{PORT} for GBOS widget requests")

    while True:
        try:
            conn, addr = srv.accept()
            # Only accept connections from the container subnet
            if addr[0].rsplit(".", 1)[0] != HOST.rsplit(".", 1)[0]:     # same /24 as the bridge
                conn.close()
                continue
            threading.Thread(target=handle_client, args=(conn, addr), daemon=True).start()
        except Exception as e:
            time.sleep(0.5)


if __name__ == "__main__":
    main()

