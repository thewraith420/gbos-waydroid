#!/usr/bin/env python3
# OS bringup: stub three functions in libandroid_runtime.so, in place.
#
#   0x136230  isVmapStack()              -> return true   (b8 01 00 00 00 c3)
#       Android reads /proc/config.gz for CONFIG_VMAP_STACK and LOG(FATAL)s when it is missing.
#       x86_64 does use vmap stacks, so "true" is accurate.
#   0x212970  getGpuTotalUsageKb()       -> return 0      (48 31 c0 c3)
#   0x212fa0  getGpuPrivateMemoryKb()    -> return 0      (48 31 c0 c3)
#       Both read the gpu_mem BPF map, which is not loaded in the container, and abort when it is missing.
#
# Offsets are for the Googlebook libandroid_runtime.so of build 17.CL3B.260622.271.R1 (3143656 bytes);
# the original bytes are checked at every site before anything is written.
import sys

PATCHES = [
    (0x136230, bytes.fromhex("554889e54157"), bytes.fromhex("b801000000c3")),
    (0x212970, bytes.fromhex("554889e5"), bytes.fromhex("4831c0c3")),
    (0x212FA0, bytes.fromhex("554889e5"), bytes.fromhex("4831c0c3")),
]

path = sys.argv[1]
d = bytearray(open(path, "rb").read())
if len(d) != 3143656:
    sys.exit("unexpected libandroid_runtime.so size %d (patch is for the build in sources.lock)" % len(d))
for off, old, new in PATCHES:
    if bytes(d[off:off + len(old)]) != old:
        sys.exit("libandroid_runtime.so: bytes at %#x are %s, expected %s" % (off, d[off:off + len(old)].hex(), old.hex()))
for off, old, new in PATCHES:
    d[off:off + len(new)] = new
open(path, "wb").write(d)
print("libandroid_runtime.so stubs applied (%d sites)" % len(PATCHES))
