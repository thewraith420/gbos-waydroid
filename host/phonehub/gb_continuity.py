#!/usr/bin/env python3
"""gb_continuity.py — Host-side Android 17 Task Continuity ("Continue on") bridge.

Accepts a RawTransport TCP connection from com.gbos.continuity.Main inside the
Waydroid container (port 8095 on the bridge interface), polls the paired phone's
live foreground app via gb-touch.jar's `fg` verb, broadcasts TaskStackBroadcastMessage
frames into GBOS's native TaskContinuityManagerService, and handles incoming
HandoffRequestMessage clicks by opening that exact phone app live in App Cast
(`OPEN_APP <pkg>`) and clearing the Continue pill so it doesn't duplicate the
active App Cast taskbar icon.
"""
import os
import re
import shlex
import socket
import struct
import threading
import time
from pathlib import Path

MSG_ONEWAY_TASK_CONTINUITY = 0x43678884

IGNORED_PACKAGES = {
    "",
    "none",
    "android",
    "com.android.systemui",
    "com.android.launcher3",
    "com.google.android.apps.nexuslauncher",
    "com.gbos.cast",
    "com.android.permissioncontroller",
    "com.google.android.permissioncontroller",
    "com.google.android.packageinstaller",
    "com.android.intentresolver",
}

EMPTY_PERSISTABLE_BUNDLE = b"<bundle></bundle>\n"


# ── Minimal protobuf encoder/decoder for TaskContinuityMessage ───────────────

def _varint(n):
    out = bytearray()
    n = int(n)
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def _pb_varint(field, val):
    return _varint((field << 3) | 0) + _varint(val)


def _pb_bytes(field, data):
    if isinstance(data, str):
        data = data.encode("utf-8")
    return _varint((field << 3) | 2) + _varint(len(data)) + data


def _read_varint(buf, pos):
    val = 0
    shift = 0
    while pos < len(buf):
        b = buf[pos]
        pos += 1
        val |= (b & 0x7F) << shift
        if not (b & 0x80):
            return val, pos
        shift += 7
    raise ValueError("truncated varint")


def _parse_fields(buf):
    pos = 0
    fields = []
    while pos < len(buf):
        key, pos = _read_varint(buf, pos)
        field_num = key >> 3
        wire_type = key & 7
        if wire_type == 0:
            val, pos = _read_varint(buf, pos)
            fields.append((field_num, wire_type, val))
        elif wire_type == 2:
            ln, pos = _read_varint(buf, pos)
            val = buf[pos : pos + ln]
            pos += ln
            fields.append((field_num, wire_type, val))
        elif wire_type == 1:
            val = buf[pos : pos + 8]
            pos += 8
            fields.append((field_num, wire_type, val))
        elif wire_type == 5:
            val = buf[pos : pos + 4]
            pos += 4
            fields.append((field_num, wire_type, val))
        else:
            break
    return fields


def build_task_stack_broadcast(task_id=None, package_name=None):
    if not task_id or not package_name:
        stack_msg = _pb_varint(1, 1)  # is_stack_empty = true
    else:
        # HandoffOptions: is_handoff_enabled=true, require_package_installed=false
        handoff_opts = _pb_varint(1, 1) + _pb_varint(2, 0)
        now_ms = int(time.time() * 1000)
        remote_task = (
            _pb_varint(1, task_id)
            + _pb_bytes(2, package_name)
            + _pb_varint(3, 1)  # is_in_foreground = true
            + _pb_varint(4, now_ms)
            + _pb_bytes(5, handoff_opts)
        )
        stack_msg = _pb_varint(1, 0) + _pb_bytes(2, remote_task)
    # TaskContinuityMessage.TASK_STACK_BROADCAST = tag 1
    return _pb_bytes(1, stack_msg)


def build_handoff_result(task_id, component_name=None, status_code=0):
    res = _pb_varint(1, status_code) + _pb_varint(2, task_id)
    if component_name:
        # Note: tag 3 (EXTRAS) MUST be present (valid XML PersistableBundle) or Android 17
        # HandoffActivityDataMessage$Builder.build() throws NPE in system_server.
        activity = (
            _pb_bytes(1, component_name)
            + _pb_bytes(3, EMPTY_PERSISTABLE_BUNDLE)
        )
        res += _pb_bytes(3, activity)
    # TaskContinuityMessage.HANDOFF_REQUEST_RESULT = tag 3
    return _pb_bytes(3, res)


def parse_handoff_request_task_id(payload):
    """If payload is a TaskContinuityMessage containing HandoffRequest (tag 2), return task_id."""
    try:
        for fnum, wtype, val in _parse_fields(payload):
            if fnum == 2 and wtype == 2:
                for sub_f, sub_w, sub_v in _parse_fields(val):
                    if sub_f == 1 and sub_w == 0:
                        return int(sub_v)
    except Exception:
        pass
    return None


def _recv_exact(conn, n):
    buf = bytearray()
    while len(buf) < n:
        chunk = conn.recv(n - len(buf))
        if not chunk:
            return None
        buf.extend(chunk)
    return bytes(buf)


# ── Phone foreground app poller (via gb-touch.jar `fg` verb) ─────────────────

class _PhoneFgClient:
    def __init__(self, gbos_env):
        self.gbos_env = gbos_env
        self._sock = None
        self._port = None
        self._ip = None
        self._token = None
        self._has_close = False
        self._no_fg_until = 0.0
        self._warned_no_fg = False

    def _close(self):
        if self._sock is not None:
            try:
                self._sock.close()
            except OSError:
                pass
            self._sock = None

    def _load_phone_creds(self):
        ip = ""
        try:
            p = Path(self.gbos_env.run_path("gb-appcast-phone-ip"))
            if p.exists():
                ip = p.read_text().strip()
        except OSError:
            pass
        tok = ""
        try:
            tf = Path(self.gbos_env.STATE) / "agent-token"
            if tf.exists():
                tok = tf.read_text().strip()
        except OSError:
            pass
        return ip, tok

    def _connect(self, ip, tok):
        self._close()
        ports = ([self._port] if self._port else []) + [
            p for p in range(8110, 8130) if p != self._port
        ]
        for port in ports:
            s = None
            try:
                s = socket.create_connection((ip, port), timeout=0.8)
                s.settimeout(1.5)
                s.sendall(f"{tok} ping\n".encode())
                reply = s.recv(128).decode("utf-8", "replace").strip()
                if reply.startswith("pong"):
                    if "fg" not in reply.split():
                        s.close()
                        self._no_fg_until = time.monotonic() + 30.0
                        if not self._warned_no_fg:
                            print("[gb-continuity] phone gb-touch.jar lacks 'fg' verb; re-arm phone to enable live sync", flush=True)
                            self._warned_no_fg = True
                        return False
                    self._sock = s
                    self._port = port
                    self._ip = ip
                    self._token = tok
                    self._has_close = "close" in reply.split()
                    self._warned_no_fg = False
                    print(f"[gb-continuity] connected to phone fg sensor at {ip}:{port} (close={self._has_close})", flush=True)
                    return True
                s.close()
            except OSError:
                if s:
                    try:
                        s.close()
                    except OSError:
                        pass
        return False

    def poll(self):
        """Return foreground package name (str) or None if unavailable/home/locked."""
        if time.monotonic() < self._no_fg_until:
            return None
        ip, tok = self._load_phone_creds()
        if not ip or not tok:
            self._close()
            return None
        if self._sock is None or ip != self._ip or tok != self._token:
            if not self._connect(ip, tok):
                return None
        try:
            self._sock.settimeout(1.5)
            self._sock.sendall(f"{tok} fg\n".encode())
            data = b""
            while b"\n" not in data:
                chunk = self._sock.recv(256)
                if not chunk:
                    raise OSError("eof")
                data += chunk
            line = data.split(b"\n", 1)[0].decode("utf-8", "replace").strip()
            if line.startswith("fg "):
                pkg = line[3:].strip()
                if pkg in IGNORED_PACKAGES:
                    return None
                if re.fullmatch(r"[A-Za-z0-9._-]{1,256}", pkg):
                    return pkg
            return None
        except OSError:
            self._close()
            return None

    def close_app(self, pkg):
        """Close the foreground app on the phone when its App Cast window is closed.
        Uses `close <pkg>` (Home + force-stop) if supported by gb-touch.jar, otherwise
        falls back to injecting KEYCODE_HOME so the phone returns cleanly to home."""
        if not pkg or pkg in IGNORED_PACKAGES:
            return False
        ip, tok = self._load_phone_creds()
        if not ip or not tok:
            return False
        if self._sock is None or ip != self._ip or tok != self._token:
            if not self._connect(ip, tok):
                return False
        try:
            self._sock.settimeout(1.5)
            if self._has_close:
                self._sock.sendall(f"{tok} close {pkg}\n".encode())
                data = b""
                while b"\n" not in data:
                    chunk = self._sock.recv(64)
                    if not chunk:
                        break
                    data += chunk
                print(f"[gb-continuity] App Cast closed -> closed phone app {pkg}", flush=True)
            else:
                # Fallback when phone has v3 gb-touch.jar (before close verb upgrade):
                # send KEYCODE_HOME (code 3) DOWN + UP.
                self._sock.sendall(f"{tok} K 0 3 0\n{tok} K 1 3 0\n".encode())
                print(f"[gb-continuity] App Cast closed -> sent Home to phone (was {pkg})", flush=True)
            return True
        except OSError:
            self._close()
            return False


# ── Continuity Bridge Service ────────────────────────────────────────────────

class ContinuityBridge:
    def __init__(self, gbos_env, run_container_shell, open_app_cb, port=8095):
        self.gbos_env = gbos_env
        self.run_container_shell = run_container_shell
        self.open_app_cb = open_app_cb
        self.host = gbos_env.BRIDGE_IP
        self.port = port

        self._lock = threading.Lock()
        self._conn = None
        self._seq = 1
        self._next_task_id = 100
        self._task_map = {}          # task_id -> package_name
        self._current_pkg = None     # last package broadcast to GBOS
        self._handed_off_pkg = None  # suppress duplicate pill while App Cast is already showing this pkg
        self._appcast_open = False   # tracks AppCastActivity window presence on GBOS
        self._override_pkg = None    # optional manual override for testing
        self._override_until = 0.0
        self._fg_client = _PhoneFgClient(gbos_env)

    def status(self):
        with self._lock:
            return {
                "transport": "attached" if self._conn is not None else "waiting",
                "current_pkg": self._current_pkg,
            }

    def inject_test_pkg(self, pkg, ttl_sec=60.0):
        with self._lock:
            self._override_pkg = pkg if pkg and pkg != "none" else None
            self._override_until = time.monotonic() + ttl_sec if self._override_pkg else 0.0
        self._push_pkg(self._override_pkg)

    def _send_frame_locked(self, msg_type, payload):
        if self._conn is None:
            return False
        seq = self._seq
        self._seq += 1
        hdr = struct.pack(">III", msg_type, seq, len(payload))
        try:
            self._conn.sendall(hdr + payload)
            return True
        except OSError:
            try:
                self._conn.close()
            except OSError:
                pass
            self._conn = None
            return False

    def _push_pkg(self, pkg):
        with self._lock:
            if self._conn is None:
                self._current_pkg = None
                return
            if pkg == self._current_pkg:
                return
            if pkg:
                tid = self._next_task_id
                self._next_task_id += 1
                if self._next_task_id > 9999:
                    self._next_task_id = 100
                self._task_map[tid] = pkg
                if len(self._task_map) > 32:
                    oldest = sorted(self._task_map.keys())[:-16]
                    for k in oldest:
                        self._task_map.pop(k, None)
                payload = build_task_stack_broadcast(tid, pkg)
                if self._send_frame_locked(MSG_ONEWAY_TASK_CONTINUITY, payload):
                    self._current_pkg = pkg
                    print(f"[gb-continuity] broadcast remote task {tid}: {pkg}", flush=True)
            else:
                payload = build_task_stack_broadcast(None, None)
                if self._send_frame_locked(MSG_ONEWAY_TASK_CONTINUITY, payload):
                    self._current_pkg = None
                    print("[gb-continuity] cleared remote task stack", flush=True)

    def _resolve_gbos_activity(self, pkg):
        """Return 'pkg/Activity' if pkg has a launcher activity on GBOS User 10, else None."""
        try:
            r = self.run_container_shell(
                f"pm resolve-activity --brief --user 10 "
                f"-a android.intent.action.MAIN -c android.intent.category.LAUNCHER {shlex.quote(pkg)}"
            )
            if r.returncode != 0:
                return None
            out = r.stdout.decode("utf-8", "replace") if isinstance(r.stdout, (bytes, bytearray)) else str(r.stdout)
            lines = [ln.strip() for ln in out.splitlines() if ln.strip()]
            if lines and "/" in lines[-1] and not lines[-1].startswith("No activity"):
                return lines[-1]
        except Exception as e:
            print(f"[gb-continuity] resolve error for {pkg}: {e}", flush=True)
        return None

    def _handle_handoff(self, task_id):
        with self._lock:
            pkg = self._task_map.get(task_id) or self._current_pkg
        if not pkg:
            print(f"[gb-continuity] handoff request for unknown task {task_id}", flush=True)
            with self._lock:
                self._send_frame_locked(MSG_ONEWAY_TASK_CONTINUITY, build_handoff_result(task_id, None, status_code=1))
            return

        print(f"[gb-continuity] handoff task {task_id} ({pkg}) -> App Cast live session", flush=True)
        # Finish the native handoff cleanly without launching a blank local GBOS app,
        # clear the Continue pill so it doesn't sit next to the running App Cast icon,
        # and open the phone's live app state in App Cast.
        with self._lock:
            self._handed_off_pkg = pkg
            self._send_frame_locked(MSG_ONEWAY_TASK_CONTINUITY, build_handoff_result(task_id, None, status_code=1))
        self._push_pkg(None)
        try:
            self.open_app_cb(pkg)
        except Exception as e:
            print(f"[gb-continuity] App Cast handoff failed for {pkg}: {e}", flush=True)

    def _reader_loop(self, conn):
        try:
            while True:
                hdr = _recv_exact(conn, 12)
                if not hdr:
                    break
                mtype, _mseq, mlen = struct.unpack(">III", hdr)
                body = _recv_exact(conn, mlen) if mlen else b""
                if body is None:
                    break
                if mtype == MSG_ONEWAY_TASK_CONTINUITY:
                    tid = parse_handoff_request_task_id(body)
                    if tid is not None:
                        threading.Thread(
                            target=self._handle_handoff, args=(tid,), daemon=True
                        ).start()
        except OSError:
            pass
        finally:
            with self._lock:
                if self._conn is conn:
                    self._conn = None
                    self._current_pkg = None
            try:
                conn.close()
            except OSError:
                pass
            print("[gb-continuity] container transport disconnected", flush=True)

    def _is_appcast_open(self):
        """True if an AppCastActivity window is currently open on GBOS."""
        try:
            r = self.run_container_shell("dumpsys window windows | grep -q AppCastActivity")
            return r.returncode == 0
        except Exception:
            return self._appcast_open

    def _poll_loop(self):
        while True:
            time.sleep(2.0)
            with self._lock:
                attached = self._conn is not None
                ov_pkg = self._override_pkg if time.monotonic() < self._override_until else None
                if ov_pkg is None:
                    self._override_pkg = None
            if not attached:
                continue

            # Detect App Cast window close (open -> closed): if the phone isn't already on its
            # home screen, close the cast app on the phone and clear handed-off suppression.
            now_open = self._is_appcast_open()
            if self._appcast_open and not now_open:
                with self._lock:
                    self._handed_off_pkg = None
                fg_now = self._fg_client.poll()
                if fg_now is not None:
                    self._fg_client.close_app(fg_now)
                    self._push_pkg(None)
                self._appcast_open = False
                continue
            self._appcast_open = now_open

            if ov_pkg is not None:
                self._push_pkg(ov_pkg)
                continue
            pkg = self._fg_client.poll()
            with self._lock:
                if self._handed_off_pkg is not None:
                    if pkg == self._handed_off_pkg:
                        continue
                    self._handed_off_pkg = None
            self._push_pkg(pkg)

    def _accept_loop(self):
        srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            srv.bind((self.host, self.port))
        except OSError as e:
            print(f"[gb-continuity] bind {self.host}:{self.port} failed: {e}", flush=True)
            return
        srv.listen(4)
        print(f"[gb-continuity] listening on {self.host}:{self.port} for container RawTransport", flush=True)

        bridge_prefix = self.host.rsplit(".", 1)[0]
        while True:
            try:
                conn, addr = srv.accept()
                if addr[0].rsplit(".", 1)[0] != bridge_prefix:
                    conn.close()
                    continue
                conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                print(f"[gb-continuity] container transport connected from {addr[0]}:{addr[1]}", flush=True)
                with self._lock:
                    old = self._conn
                    self._conn = conn
                    self._current_pkg = None
                if old is not None:
                    try:
                        old.close()
                    except OSError:
                        pass
                threading.Thread(target=self._reader_loop, args=(conn,), daemon=True).start()
            except OSError:
                time.sleep(0.5)

    def start(self):
        threading.Thread(target=self._accept_loop, daemon=True, name="gb-continuity-accept").start()
        threading.Thread(target=self._poll_loop, daemon=True, name="gb-continuity-poll").start()
