#!/usr/bin/env python3
"""gb-bt-bridge: share the host Bluetooth adapter with the Waydroid container (GBOS_WAYDROID_FIXES.md, section 3.13).

The container cannot open Bluetooth sockets itself (AF_BLUETOOTH only works in the initial network namespace), so this root
helper runs on the HOST.  When the container's Bluetooth HAL (bthal_app, Java) connects over TCP, the helper

  1. powers the adapter off (so BlueZ lets go of it),
  2. takes it exclusively through the kernel HCI *user channel* (HCI_CHANNEL_USER),
  3. forwards raw H4 HCI packets both ways (framing on the TCP side: 2-byte big-endian length + H4 packet),

and when the container disconnects (Android Bluetooth turned off, or Waydroid stopped) it closes the channel and asks BlueZ to
power the adapter back on, so host Bluetooth works again.  While the container holds the adapter, host Bluetooth is unavailable.

  sudo ./gb-bt-bridge.py                  run the bridge (normally started by the systemd unit gb-bt-bridge.service)
  sudo ./gb-bt-bridge.py --selftest       take the adapter, send HCI_Reset + Read_Local_Version, release it, print results
        ./gb-bt-bridge.py --fake          no hardware: a tiny fake controller (answers HCI commands with Command Complete)
                                          to test the container <-> host packet path without root
"""
import argparse
import ctypes
import errno
import ipaddress
import logging
import os
import select
import signal
import socket
import struct
import subprocess
import sys
import threading
import time

AF_BLUETOOTH = 31
BTPROTO_HCI = 1
HCI_CHANNEL_USER = 1
HCIDEVUP = 0x400448C9            # _IOW('H', 201, int)
HCIDEVDOWN = 0x400448CA          # _IOW('H', 202, int)
H4_CMD, H4_ACL, H4_SCO, H4_EVT, H4_ISO = 1, 2, 3, 4, 5

log = logging.getLogger("gb-bt-bridge")
_libc = ctypes.CDLL("libc.so.6", use_errno=True)


class SockaddrHci(ctypes.Structure):
    _fields_ = [("hci_family", ctypes.c_ushort), ("hci_dev", ctypes.c_ushort), ("hci_channel", ctypes.c_ushort)]


def _run(cmd, timeout=4):
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, stdin=subprocess.DEVNULL)
        log.debug("%s -> rc=%s %s%s", " ".join(cmd), r.returncode, r.stdout.strip(), r.stderr.strip())
        return r.returncode == 0
    except (OSError, subprocess.SubprocessError) as e:
        log.debug("%s failed: %s", " ".join(cmd), e)
        return False


class RealHci:
    """The host adapter through the exclusive HCI user channel."""

    def __init__(self, dev=None):
        # dev=None means "whatever adapter is present right now". The index is NOT stable: the Realtek comes back as a
        # different hciN after a suspend or a USB re-enumeration (hci1 yesterday, hci0 after the 2026-10-30 lid close), so
        # a value resolved once at daemon start silently points at nothing until the service is restarted.
        self.dev = dev
        self.sock = None

    def _ioctl_power(self, on):
        """Bring the interface up/down straight through the kernel (no external tools, cannot hang)."""
        try:
            import fcntl
            s = socket.socket(AF_BLUETOOTH, socket.SOCK_RAW | socket.SOCK_CLOEXEC, BTPROTO_HCI)
            try:
                fcntl.ioctl(s, HCIDEVUP if on else HCIDEVDOWN, self.dev)
                return True
            finally:
                s.close()
        except OSError as e:
            if e.errno == errno.EALREADY or (on and e.errno == errno.EALREADY) or (not on and e.errno in (errno.EALREADY, errno.ENETDOWN)):
                return True                      # already in the wanted state
            log.debug("ioctl power %s failed: %s", "on" if on else "off", e)
            return False

    def _power(self, on, fast=False):
        if self._ioctl_power(on):
            return True
        if fast:
            return False                    # hot path: never block on btmgmt/bluetoothctl (they can each take 4 s)
        word = "on" if on else "off"
        return (_run(["btmgmt", "--index", str(self.dev), "power", word])
                or _run(["bluetoothctl", "power", word]))

    def open(self):
        # Android's HCI HAL only waits ~3 s for initialize() to complete, so the whole takeover has to fit in that window.
        # One ioctl power-down plus a tight bind loop is ~1-2 s; the old 6 x (0.5 s + btmgmt/4 s + 1.0 s) took ~33 s.
        last = None
        deadline = time.time() + 2.0
        attempt = 0
        while True:
            attempt += 1
            if self.dev is None:
                self.dev = detect_hci()
                if self.dev is None:
                    last = errno.ENODEV
                    if time.time() >= deadline:
                        break
                    time.sleep(0.05)
                    continue
            self._power(False, fast=True)
            s = socket.socket(AF_BLUETOOTH, socket.SOCK_RAW | socket.SOCK_CLOEXEC, BTPROTO_HCI)
            addr = SockaddrHci(AF_BLUETOOTH, self.dev, HCI_CHANNEL_USER)
            if _libc.bind(s.fileno(), ctypes.byref(addr), ctypes.sizeof(addr)) == 0:
                self.sock = s
                log.info("hci%d: user channel opened (attempt %d)", self.dev, attempt)
                return
            last = ctypes.get_errno()
            s.close()
            log.debug("hci%d: bind(user channel) failed: %s (attempt %d)", self.dev, os.strerror(last), attempt)
            if last in (errno.ENODEV, errno.EADDRNOTAVAIL):
                self.dev = None         # the adapter moved; re-detect on the next attempt instead of failing outright
            if time.time() >= deadline:
                break
            time.sleep(0.05)
        raise OSError(last or errno.EBUSY, "cannot take hci%d exclusively: %s" % (self.dev, os.strerror(last or errno.EBUSY)))

    def fileno(self):
        return self.sock.fileno()

    def read(self):
        return self.sock.recv(4096)          # one H4 packet per read

    def write(self, pkt):
        self.sock.send(pkt)

    def close(self):
        if self.sock is not None:
            try:
                self.sock.close()
            finally:
                self.sock = None
            log.info("hci%d: user channel closed", self.dev)
            time.sleep(0.5)
            self._power(True)               # hand the adapter back to BlueZ


class FakeHci:
    """No hardware: answers every HCI command with Command Complete (status 0) so the packet path can be tested without root."""

    def __init__(self, dev=0):
        self.r, self.w = os.pipe()
        self.lock = threading.Lock()

    def open(self):
        log.info("fake controller opened")

    def fileno(self):
        return self.r

    def read(self):
        return os.read(self.r, 4096)

    def write(self, pkt):
        if pkt and pkt[0] == H4_CMD and len(pkt) >= 4:
            opcode = pkt[1:3]
            evt = bytes([H4_EVT, 0x0E, 4, 1]) + opcode + b"\x00"
            with self.lock:
                os.write(self.w, evt)
        else:
            log.info("fake controller dropped packet type %s (%d bytes)", pkt[:1].hex(), len(pkt))

    def close(self):
        for fd in (self.r, self.w):
            try:
                os.close(fd)
            except OSError:
                pass
        log.info("fake controller closed")


def _read_frame(conn, buf):
    """Blocking read of one 2-byte-length-prefixed frame; returns (frame_or_None, remaining_buf)."""
    while True:
        if len(buf) >= 2:
            n = struct.unpack(">H", buf[:2])[0]
            if len(buf) >= 2 + n:
                return buf[2:2 + n], buf[2 + n:]
        data = conn.recv(8192)
        if not data:
            return None, buf
        buf += data


def _send_frame(conn, payload):
    conn.sendall(struct.pack(">H", len(payload)) + payload)


def serve(conn, hci):
    """Forward packets between the container (TCP, framed) and the controller until either side goes away.

    Handshake: the container's first frame is a 0x00 'hello'.  Only then is the adapter taken (a bare port probe, as done by the
    container's boot script, never touches the adapter).  The reply is a one-byte frame: 0x00 = adapter obtained, 0xFF = failed."""
    conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    conn.settimeout(15)
    try:
        frame, buf = _read_frame(conn, b"")
    except socket.timeout:
        frame, buf = None, b""
    if not frame or frame[0] != 0x00:
        log.info("no hello from client (port probe?), adapter not touched")
        return
    conn.settimeout(None)
    try:
        hci.open()
    except Exception as e:
        log.error("cannot take the adapter: %s", e)
        try:
            hci.close()
        finally:
            _send_frame(conn, b"\xff")
        return
    _send_frame(conn, b"\x00")
    try:
        while True:
            r, _, _ = select.select([conn, hci], [], [], 30)
            for x in r:
                if x is conn:
                    data = conn.recv(8192)
                    if not data:
                        return
                    buf += data
                    while True:
                        frame, buf = _split(buf)
                        if frame is None:
                            break
                        hci.write(frame)
                else:
                    pkt = hci.read()
                    if not pkt:
                        return
                    _send_frame(conn, pkt)
    finally:
        hci.close()


def _split(buf):
    if len(buf) >= 2:
        n = struct.unpack(">H", buf[:2])[0]
        if len(buf) >= 2 + n:
            return buf[2:2 + n], buf[2 + n:]
    return None, buf


def run_bridge(args, make_hci):
    net = ipaddress.ip_network(args.allow)
    srv = None
    while srv is None:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            s.bind((args.listen, args.port))
            s.listen(1)
            srv = s
        except OSError as e:
            log.info("waiting to listen on %s:%d (%s)", args.listen, args.port, e)
            time.sleep(2)
    log.info("listening on %s:%d (adapter is only taken while a client is connected)", args.listen, args.port)
    while True:
        conn, peer = srv.accept()
        if ipaddress.ip_address(peer[0]) not in net:
            log.warning("rejected connection from %s", peer[0])
            conn.close()
            continue
        log.info("container connected from %s", peer[0])
        hci = make_hci()
        try:
            serve(conn, _Wrap(hci))
        except Exception as e:                 # keep the daemon alive; the adapter was released in serve()'s finally
            log.error("session ended with error: %s", e)
        finally:
            conn.close()
            log.info("container disconnected")


class _Wrap:
    """Lets select() treat the controller like a file object while delegating the real work."""

    def __init__(self, hci):
        self._h = hci

    def fileno(self):
        return self._h.fileno()

    def open(self):
        self._h.open()

    def read(self):
        return self._h.read()

    def write(self, p):
        self._h.write(p)

    def close(self):
        self._h.close()


def detect_hci():
    """Index of the first registered host adapter.

    The index is not stable: the Realtek re-enumerated and came back as hci1, at which point the old hardcoded default of 0
    pointed at nothing and every takeover spent ~33 s failing (Android's HAL init gives up after 3 s).
    """
    try:
        devs = [int(n[3:]) for n in os.listdir("/sys/class/bluetooth") if n.startswith("hci") and n[3:].isdigit()]
    except OSError:
        return None
    return min(devs) if devs else None


def selftest(dev):
    hci = RealHci(dev)
    hci.open()
    try:
        def txrx(cmd, label):
            hci.write(cmd)
            deadline = time.time() + 5
            while time.time() < deadline:
                r, _, _ = select.select([hci.fileno()], [], [], 1)
                if r:
                    pkt = hci.read()
                    if pkt[:1] == bytes([H4_EVT]) and pkt[1:2] == b"\x0e":
                        print("%-22s -> Command Complete, status=%d, %d bytes: %s" % (label, pkt[6], len(pkt), pkt.hex()))
                        return pkt
            print("%-22s -> NO RESPONSE" % label)
        txrx(bytes([H4_CMD, 0x03, 0x0C, 0]), "HCI_Reset")
        pkt = txrx(bytes([H4_CMD, 0x01, 0x10, 0]), "Read_Local_Version")
        if pkt and len(pkt) >= 15:
            hci_ver, hci_rev, lmp_ver, manu, lmp_sub = struct.unpack("<BHBHH", pkt[7:15])
            print("   HCI version %d (rev %d), LMP version %d, manufacturer %d, LMP subversion 0x%04x" % (hci_ver, hci_rev, lmp_ver, manu, lmp_sub))
        print("SELFTEST OK" if pkt else "SELFTEST FAILED")
    finally:
        hci.close()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--listen", default="192.168.240.1", help="address to listen on (the Waydroid bridge IP)")
    ap.add_argument("--port", type=int, default=6611)
    ap.add_argument("--allow", default="192.168.240.0/24", help="only accept clients from this network")
    ap.add_argument("--hci", type=int, default=-1, help="adapter index; -1 (default) uses the first adapter present")
    ap.add_argument("--fake", action="store_true", help="fake controller, no hardware, no root needed")
    ap.add_argument("--selftest", action="store_true", help="open the real adapter, run two HCI commands, release it")
    ap.add_argument("-v", "--verbose", action="store_true")
    a = ap.parse_args()
    logging.basicConfig(level=logging.DEBUG if a.verbose else logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))
    auto_hci = a.hci < 0            # -1: follow whatever adapter exists, re-resolved at every takeover
    if auto_hci:
        a.hci = detect_hci()
        if a.hci is None:
            log.error("no Bluetooth adapter present under /sys/class/bluetooth (will retry at each takeover)")
        else:
            log.info("auto adapter: hci%d present now (re-resolved at each takeover)", a.hci)
    if a.selftest:
        if os.geteuid() != 0:
            sys.exit("--selftest needs root (HCI user channel): run with sudo")
        selftest(a.hci)
        return
    if not a.fake and os.geteuid() != 0:
        sys.exit("needs root to take the adapter (or use --fake): run with sudo")
    run_bridge(a, (lambda: FakeHci()) if a.fake else (lambda: RealHci(None if auto_hci else a.hci)))


if __name__ == "__main__":
    main()
