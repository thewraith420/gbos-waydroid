#!/usr/bin/env python3
"""Pure-Python DEX resolver for R8-obfuscated symbols in com.google.ambient.streaming.

Parses classes*.dex from a base.apk (or pulls it from a live Waydroid container via adb),
walks Dalvik bytecode instructions to extract exact const-string references per class/method,
identifies the obfuscated R8 names by invariant log/type/signature fingerprints, and caches
the mapping per versionCode under GB_STATE (~/.cache/gbos).
"""

import argparse
import json
import os
import struct
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

PKG = "com.google.ambient.streaming"

# Ensure sibling helpers in host/phonehub/lib are importable when invoked as CLI or module.
_LIB_DIR = str(Path(__file__).resolve().parent.parent / "lib")
if _LIB_DIR not in sys.path:
    sys.path.insert(0, _LIB_DIR)
import gbos_env  # noqa: E402

# Dalvik opcode lengths in 16-bit code units (standard DEX format specification).
# Index 0..255 maps opcode byte -> length in 16-bit code units; 0 marks payload/unused opcodes.
_OP_LEN = (
    1, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 2, 3, 2, 2, 3, 5, 2, 2, 3, 2, 1, 1, 2,
    2, 1, 2, 2, 3, 3, 3, 1, 1, 2, 3, 3, 3, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 0, 0,
    0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3,
    3, 3, 3, 0, 3, 3, 3, 3, 3, 0, 0, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
    2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
    2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 4, 2, 2, 3, 3,
)

# Substrings uniquely identifying the target classes in com.google.ambient.streaming.
_NEEDLES = (
    "Discovery is not paused, starting discoverDevices.",
    "Stream stalled for %d ms",
    "SecurityException during connection",
    "kotlin.Unit",
    "c2.android.",
    ".low_latency",
    "Released display-specific wake lock and Wi-Fi lock",
    "DeviceLinkService#connectDevice: Failed to notify listener.",
    "Closing channel for device %s",
    "Failed to start MediaCodec video decoder",
)

REQUIRED_KEYS = (
    "gate",
    "stall",
    "conn",
    "unit",
    "codecs",
    "codecs_decname",
    "codecs_name",
    "codecs_mime",
    "display",
    "connect",
    "link",
    "jys",
)


def _uleb(buf, off):
    val = shift = 0
    while True:
        c = buf[off]
        off += 1
        val |= (c & 0x7F) << shift
        if not (c & 0x80):
            return val, off
        shift += 7


def _desc_to_cls(desc):
    if desc.startswith("L") and desc.endswith(";"):
        return desc[1:-1].replace("/", ".")
    return desc


class _Dex:
    """Lazy reader for a single DEX byte buffer."""

    def __init__(self, data):
        if data[:4] != b"dex\n":
            raise ValueError("not a DEX file")
        self.b = data
        self.mv = memoryview(data)
        (
            self.str_cnt, self.str_off,
            self.type_cnt, self.type_off,
            self.proto_cnt, self.proto_off,
            self.fld_cnt, self.fld_off,
            self.mth_cnt, self.mth_off,
            self.cls_cnt, self.cls_off,
        ) = struct.unpack_from("<12I", data, 56)
        self._strs = {}
        self._types = {}

    def s(self, idx):
        v = self._strs.get(idx)
        if v is not None:
            return v
        off = struct.unpack_from("<I", self.mv, self.str_off + 4 * idx)[0]
        _, off = _uleb(self.b, off)
        end = self.b.index(0, off)
        v = self.b[off:end].decode("utf-8", "replace")
        self._strs[idx] = v
        return v

    def t(self, idx):
        v = self._types.get(idx)
        if v is not None:
            return v
        s_idx = struct.unpack_from("<I", self.mv, self.type_off + 4 * idx)[0]
        v = self.s(s_idx)
        self._types[idx] = v
        return v

    def field_sig(self, fidx):
        _, type_i, name_i = struct.unpack_from("<HHI", self.mv, self.fld_off + 8 * fidx)
        return self.s(name_i), self.t(type_i)

    def method_sig(self, midx):
        _, proto_i, name_i = struct.unpack_from("<HHI", self.mv, self.mth_off + 8 * midx)
        _, ret_i, parms_off = struct.unpack_from("<III", self.mv, self.proto_off + 12 * proto_i)
        parms = ""
        if parms_off:
            cnt = struct.unpack_from("<I", self.mv, parms_off)[0]
            parms = "".join(
                self.t(struct.unpack_from("<H", self.mv, parms_off + 4 + 2 * k)[0])
                for k in range(cnt)
            )
        return self.s(name_i), f"({parms}){self.t(ret_i)}"

    def matching_string_ids(self):
        """Return {string_id: decoded_str} only for strings matching any target needle."""
        out = {}
        b = self.b
        mv = self.mv
        str_off = self.str_off
        for i in range(self.str_cnt):
            off = struct.unpack_from("<I", mv, str_off + 4 * i)[0]
            # Skip ULEB128 UTF-16 length (almost always 1-2 bytes).
            while b[off] & 0x80:
                off += 1
            off += 1
            end = b.index(0, off)
            # Fast ASCII-safe length prefilter before decoding.
            raw = b[off:end]
            if len(raw) < 11:
                continue
            text = raw.decode("utf-8", "replace")
            for needle in _NEEDLES:
                if needle in text:
                    out[i] = text
                    break
        return out

    def scan_const_strings(self, code_off, target_sids):
        """Exact Dalvik instruction walker returning matching const-string texts."""
        insns_sz = struct.unpack_from("<I", self.mv, code_off + 12)[0]
        base = code_off + 16
        mv = self.mv
        found = []
        pc = 0
        while pc < insns_sz:
            w0 = struct.unpack_from("<H", mv, base + 2 * pc)[0]
            op = w0 & 0xFF
            if w0 in (0x0100, 0x0200, 0x0300):
                if w0 == 0x0100:
                    cnt = struct.unpack_from("<H", mv, base + 2 * (pc + 1))[0]
                    pc += cnt * 2 + 4
                elif w0 == 0x0200:
                    cnt = struct.unpack_from("<H", mv, base + 2 * (pc + 1))[0]
                    pc += cnt * 4 + 2
                else:
                    ew = struct.unpack_from("<H", mv, base + 2 * (pc + 1))[0]
                    cnt = struct.unpack_from("<I", mv, base + 2 * (pc + 2))[0]
                    pc += (cnt * ew + 1) // 2 + 4
                continue
            if op == 0x1A:
                sidx = struct.unpack_from("<H", mv, base + 2 * (pc + 1))[0]
                if sidx in target_sids:
                    found.append(target_sids[sidx])
            elif op == 0x1B:
                sidx = struct.unpack_from("<I", mv, base + 2 * (pc + 1))[0]
                if sidx in target_sids:
                    found.append(target_sids[sidx])
            step = _OP_LEN[op]
            if step <= 0:
                raise ValueError(f"unexpected Dalvik opcode 0x{op:02x} at code_off=0x{code_off:x}")
            pc += step
        return found


def _inspect_dex(dex, acc):
    target_sids = dex.matching_string_ids()
    if not target_sids:
        return
    b = dex.b
    mv = dex.mv
    for idx in range(dex.cls_cnt):
        c_idx, _, _, _, _, _, cdata_off, _ = struct.unpack_from("<8I", mv, dex.cls_off + 32 * idx)
        if not cdata_off:
            continue
        desc = dex.t(c_idx)
        o = cdata_off
        sf_c, o = _uleb(b, o)
        if_c, o = _uleb(b, o)
        dm_c, o = _uleb(b, o)
        vm_c, o = _uleb(b, o)

        static_fields = []
        fidx = 0
        for _ in range(sf_c):
            d, o = _uleb(b, o)
            _, o = _uleb(b, o)
            fidx += d
            static_fields.append(dex.field_sig(fidx))

        inst_fields = []
        fidx = 0
        for _ in range(if_c):
            d, o = _uleb(b, o)
            _, o = _uleb(b, o)
            fidx += d
            inst_fields.append(dex.field_sig(fidx))

        methods = []
        for is_virtual, cnt in ((False, dm_c), (True, vm_c)):
            midx = 0
            for _ in range(cnt):
                d, o = _uleb(b, o)
                _, o = _uleb(b, o)
                code_off, o = _uleb(b, o)
                midx += d
                if not code_off:
                    continue
                hits = dex.scan_const_strings(code_off, target_sids)
                if hits:
                    mname, msig = dex.method_sig(midx)
                    methods.append((is_virtual, mname, msig, hits))

        if not methods:
            continue

        # Also inspect remaining methods of matching candidate class when codecs need mime(List) (no strings).
        def _all_method_sigs():
            out = []
            oo = cdata_off
            s0, oo = _uleb(b, oo)
            i0, oo = _uleb(b, oo)
            d0, oo = _uleb(b, oo)
            v0, oo = _uleb(b, oo)
            for n in (s0, i0):
                for _ in range(n):
                    _, oo = _uleb(b, oo)
                    _, oo = _uleb(b, oo)
            for n in (d0, v0):
                mi = 0
                for _ in range(n):
                    dd, oo = _uleb(b, oo)
                    _, oo = _uleb(b, oo)
                    _, oo = _uleb(b, oo)
                    mi += dd
                    out.append(dex.method_sig(mi))
            return out

        cls_name = _desc_to_cls(desc)
        for is_virt, mname, msig, hits in methods:
            if mname == "b" and msig == "(Ljava/lang/Object;)Ljava/lang/Object;":
                if any("Discovery is not paused, starting discoverDevices." in s for s in hits):
                    if any(fn == "b" and ft == "Z" for fn, ft in inst_fields):
                        acc["gate"].add(cls_name)
                if any("Stream stalled for %d ms" in s for s in hits):
                    acc["stall"].add(cls_name)
                if any("SecurityException during connection" in s for s in hits):
                    acc["conn"].add(cls_name)
            if mname == "toString" and msig == "()Ljava/lang/String;" and "kotlin.Unit" in hits:
                if any(fn == "a" and ft == desc for fn, ft in static_fields):
                    acc["unit"].add(cls_name)
            if any("c2.android." in s for s in hits) and msig == "(Ljava/lang/String;Z)Ljava/lang/String;":
                all_sigs = _all_method_sigs()
                dec = [mn for mn, ms in all_sigs if ms == "(Ljava/lang/String;ZZ)Ljava/lang/String;"]
                mim = [mn for mn, ms in all_sigs if ms == "(Ljava/util/List;)Ljava/lang/String;"]
                if len(dec) == 1 and len(mim) == 1:
                    acc["codecs"].add(cls_name)
                    acc["codecs_name"].add(mname)
                    acc["codecs_decname"].add(dec[0])
                    acc["codecs_mime"].add(mim[0])
            if any("Released display-specific wake lock and Wi-Fi lock" in s for s in hits):
                acc["display"].add(cls_name)
            if mname == "a" and msig.startswith("(Ljava/lang/Object;") and msig.endswith(")Ljava/lang/Object;"):
                if any("DeviceLinkService#connectDevice: Failed to notify listener." in s for s in hits):
                    acc["connect"].add(cls_name)
            if any("Closing channel for device %s" in s for s in hits):
                all_sigs = _all_method_sigs()
                if ("b", "(Ljava/lang/String;Ljava/lang/String;)V") in all_sigs and ("d", "(Ljava/lang/String;[B)V") in all_sigs:
                    acc["link"].add(cls_name)
            if any("Failed to start MediaCodec video decoder" in s for s in hits):
                acc["jys"].add(cls_name)


def resolve_apk(apk_path, version_code=None):
    """Resolve all R8 symbols from classes*.dex inside apk_path."""
    acc = {k: set() for k in REQUIRED_KEYS}
    with zipfile.ZipFile(apk_path, "r") as zf:
        dex_names = sorted(n for n in zf.namelist() if n.startswith("classes") and n.endswith(".dex"))
        if not dex_names:
            raise RuntimeError(f"no classes*.dex found in {apk_path}")
        for name in dex_names:
            _inspect_dex(_Dex(zf.read(name)), acc)

    result = {}
    if version_code is not None:
        result["versionCode"] = str(version_code)
    for key in REQUIRED_KEYS:
        vals = sorted(acc[key])
        if key == "stall":
            if not vals:
                raise RuntimeError("failed to resolve CDS 'stall' classes")
            result[key] = vals
        else:
            if len(vals) != 1:
                raise RuntimeError(f"expected unique match for '{key}', got {vals}")
            result[key] = vals[0]
    return result


def _cache_path(version_code):
    return Path(gbos_env.STATE) / f"cds-names-{version_code}.json"


def _adb_query_pkg(serial):
    out = subprocess.run(
        ["adb", "-s", serial, "shell", f"dumpsys package {PKG}; pm path {PKG}"],
        capture_output=True, text=True, timeout=15, check=True,
    ).stdout
    vcs = []
    for line in out.splitlines():
        if "versionCode=" in line:
            part = line.split("versionCode=", 1)[1].split()[0]
            if part.isdigit():
                vcs.append(part)
    vc = str(max(vcs, key=int)) if vcs else None
    apk_paths = [
        line.split("package:", 1)[1].strip()
        for line in out.splitlines()
        if line.startswith("package:")
    ]
    if not vc or not apk_paths:
        raise RuntimeError(f"could not inspect {PKG} on {serial} (vc={vc}, paths={apk_paths})")
    base = next((p for p in apk_paths if p.endswith("base.apk")), apk_paths[0])
    return vc, base


def _is_valid_cache(data, vc):
    if not isinstance(data, dict) or str(data.get("versionCode", "")) != str(vc):
        return False
    for k in REQUIRED_KEYS:
        if k == "stall":
            if not isinstance(data.get(k), list) or not data[k]:
                return False
        elif not isinstance(data.get(k), str) or not data[k]:
            return False
    return True


def resolve_for_container(serial=None):
    """Return cached or freshly DEX-resolved symbol dict for com.google.ambient.streaming."""
    serial = serial or gbos_env.container()
    vc, remote_apk = _adb_query_pkg(serial)
    cpath = _cache_path(vc)
    if cpath.is_file():
        try:
            cached = json.loads(cpath.read_text())
            if _is_valid_cache(cached, vc):
                return cached
        except (OSError, json.JSONDecodeError):
            pass

    os.makedirs(cpath.parent, exist_ok=True)
    with tempfile.NamedTemporaryFile(prefix="cds-", suffix=".apk", delete=False) as tf:
        tmp_apk = tf.name
    try:
        subprocess.run(
            ["adb", "-s", serial, "pull", remote_apk, tmp_apk],
            capture_output=True, text=True, timeout=30, check=True,
        )
        names = resolve_apk(tmp_apk, version_code=vc)
    finally:
        try:
            os.unlink(tmp_apk)
        except OSError:
            pass

    tmp_json = str(cpath) + f".tmp.{os.getpid()}"
    Path(tmp_json).write_text(json.dumps(names, indent=2, sort_keys=True) + "\n")
    os.replace(tmp_json, cpath)
    return names


def main(argv=None):
    ap = argparse.ArgumentParser(description="Resolve obfuscated symbols in com.google.ambient.streaming")
    src = ap.add_mutually_exclusive_group()
    src.add_argument("--apk", help="Path to local base.apk to resolve without adb")
    src.add_argument("--container", help="ADB serial (defaults to gbos_env.container())")
    ap.add_argument("--field", choices=REQUIRED_KEYS, help="Print a single resolved field instead of JSON")
    args = ap.parse_args(argv)

    data = resolve_apk(args.apk) if args.apk else resolve_for_container(args.container)
    if args.field:
        val = data[args.field]
        print(" ".join(val) if isinstance(val, list) else val)
    else:
        print(json.dumps(data, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
