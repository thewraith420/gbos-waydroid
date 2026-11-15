#!/usr/bin/env python3
# OS bringup: second netd.o patch (run AFTER patch-netd-bpf.py), in place.
#
# patch-netd-bpf.py makes eight "is this socket in the host's netns?" checks accept the container's own
# network namespace. Two more checks use the same JEQ-register form but sit where that script's search
# window does not reach them, and one program tail (0x1b688..0x1b6c8) had to be rewritten by hand so it
# returns success without touching state the container does not have. These edits were originally made
# directly on the file; this script reproduces them from the recorded byte differences.
# (Instruction semantics were not re-derived; each 8-byte BPF instruction is checked before it is replaced.)
#
# Offsets are for netd.o from the tethering APEX of build 17.CL3B.260622.271.R1 (361088 bytes).
import sys

PATCHES = [  # (offset, original 8 bytes, replacement 8 bytes)
    (0x1b688, "5500010000000000", "b701000000000000"),
    (0x1b690, "0500160000000000", "7b18000000000000"),
    (0x1b698, "7901180000000000", "6316240000000000"),
    (0x1b6a0, "7b18000000000000", "b702000008000000"),
    (0x1b6a8, "b701000000000000", "6326200000000000"),
    (0x1b6b0, "7b10180000000000", "b700000001000000"),
    (0x1b6b8, "6316240000000000", "bf11000000000000"),
    (0x1b6c0, "b701000008000000", "bf11000000000000"),
    (0x1b6c8, "0500f4ff00000000", "9500000000000000"),
    (0x1b6e8, "1d01030000000000", "1d11030000000000"),
    (0x1bdc0, "1d01030000000000", "1d11030000000000"),
]

path = sys.argv[1]
d = bytearray(open(path, "rb").read())
if len(d) != 361088:
    sys.exit("unexpected netd.o size %d (patch is for the build in sources.lock)" % len(d))
for off, old, new in PATCHES:
    if bytes(d[off:off + 8]).hex() != old:
        sys.exit("netd.o: instruction at %#x is %s, expected %s" % (off, d[off:off + 8].hex(), old))
for off, old, new in PATCHES:
    d[off:off + 8] = bytes.fromhex(new)
open(path, "wb").write(d)
print("netd.o extra patch applied (%d instructions)" % len(PATCHES))
