#!/usr/bin/env python3
# OS bringup: first libselinux patch (run BEFORE patch-libselinux-ctx.py), in place.
#
# The host kernel has no SELinux (it runs AppArmor), so libselinux's own checks must report "SELinux is
# off" and the "set the next file-creation context" call must succeed as a no-op:
#   is_selinux_enabled()  -> return 0      (48 8d 0d ..   ->  xor eax,eax ; ret)
#   setfscreatecon()      -> return 0      (55 48 89 ..   ->  xor eax,eax ; ret)
#   one conditional jump made unconditional (74 -> eb) so a failed context lookup is skipped.
#
# Offsets are for the Googlebook libselinux.so of build 17.CL3B.260622.271.R1 (151920 bytes); every byte is
# checked before it is changed, so a different build stops here instead of being patched blindly.
import sys

PATCHES = [
    (0xE2D0, bytes.fromhex("488d0d"), bytes.fromhex("31c0c3")),
    (0x17570, bytes.fromhex("554889"), bytes.fromhex("31c0c3")),
    (0x1A50B, bytes.fromhex("74"), bytes.fromhex("eb")),
]

path = sys.argv[1]
d = bytearray(open(path, "rb").read())
if len(d) != 151920:
    sys.exit("unexpected libselinux.so size %d (this patch is for the build in sources.lock)" % len(d))
for off, old, new in PATCHES:
    if bytes(d[off:off + len(old)]) != old:
        sys.exit("libselinux.so: bytes at %#x are %s, expected %s" % (off, d[off:off + len(old)].hex(), old.hex()))
for off, old, new in PATCHES:
    d[off:off + len(new)] = new
open(path, "wb").write(d)
print("libselinux.so base patch applied (%d sites)" % len(PATCHES))
