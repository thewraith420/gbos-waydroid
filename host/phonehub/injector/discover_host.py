#!/usr/bin/env python3
"""discover_host.py — let the GBOS Cast app find this laptop without typing an IP.

WHY
---
The app used to require the laptop's address and port typed in by hand, and the laptop's LAN
address moves with DHCP. A wrong address fails in the worst way: the phone says "Casting to
192.0.2.10:8098" and nothing ever connects on either side.

HOW
---
The app broadcasts a one-line probe on the LAN; this answers it with the port it should push
video to. The reply's SOURCE ADDRESS is the laptop's current IP, so the app never has to be told
an address, and a DHCP change needs no edit on the phone.

    phone  ->  UDP broadcast :8096   "GBOSCAST?"
    laptop ->  UDP unicast back      "GBOSCAST <video_port> <input_port>"

No mDNS: this host has no working mDNS (`adb mdns services` lists nothing), and a plain
broadcast has no daemon dependency on either side.
"""
import os
import socket
import sys
import time

PORT = int(os.environ.get("GB_DISCOVER_PORT", "8096"))
VIDEO_PORT = int(os.environ.get("GB_PHONE_VIDEO_PORT", "8098"))
INPUT_PORT = int(os.environ.get("GB_PHONE_INPUT_PORT", "8100"))


def log(msg):
    print(f"[{time.strftime('%H:%M:%S')}][discover] {msg}", flush=True)


def main():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    if hasattr(socket, "SO_REUSEPORT"):
        try:
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
        except OSError:
            pass
    try:
        s.bind(("0.0.0.0", PORT))
    except OSError as e:
        log(f"bind 0.0.0.0:{PORT} failed ({e}) — already running?")
        return 0
    log(f"listening on 0.0.0.0:{PORT} — replying with video={VIDEO_PORT} input={INPUT_PORT}")
    while True:
        try:
            data, addr = s.recvfrom(512)
        except OSError as e:
            log(f"recvfrom: {e}")
            continue
        msg = data.decode("utf-8", "replace").strip()
        if "GBOSCAST" not in msg.upper():
            continue
        reply = f"GBOSCAST {VIDEO_PORT} {INPUT_PORT}\n".encode()
        try:
            s.sendto(reply, addr)
            log(f"probe from {addr[0]} -> replied video={VIDEO_PORT}")
        except OSError as e:
            log(f"reply to {addr}: {e}")


if __name__ == "__main__":
    sys.exit(main())
