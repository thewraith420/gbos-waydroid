#!/usr/bin/env python3
"""Make Waydroid's in-container init stop overwriting the Vulkan driver we choose.

    patch-waydroid-init-vulkan.py SRC DST

SRC is /vendor/bin/waydroid-init from the Lineage/Waydroid vendor image. At every boot it probes the host GPU and
writes the system property `ro.hardware.vulkan` (to e.g. `intel`), replacing the `virtio` value the hybrid-GPU mode
sets in waydroid.prop. This changes ONE byte, in the property-name string:

    ro.hardware.vulkan  ->  ro.hardware.xulkan

so the init writes a harmless property nobody reads and the `virtio` value (our shim) stays in force.
The string is located by pattern, not by offset, so it works on any build that contains it exactly once.
"""
import sys

OLD = b"ro.hardware.vulkan\x00"
NEW = b"ro.hardware.xulkan\x00"

if len(sys.argv) != 3:
    sys.exit(__doc__)
src, dst = sys.argv[1:]
data = open(src, "rb").read()
if data[:4] != b"\x7fELF":
    sys.exit("%s is not an ELF file" % src)
n = data.count(OLD)
if n != 1:
    sys.exit("expected the string %r exactly once in %s, found %d: not a waydroid-init this patch knows" % (OLD, src, n))
if NEW in data:
    sys.exit("%s already contains the patched string" % src)
out = data.replace(OLD, NEW)
assert len(out) == len(data) and sum(a != b for a, b in zip(data, out)) == 1
with open(dst, "wb") as f:
    f.write(out)
import os
os.chmod(dst, 0o755)
print("patched %s -> %s (1 byte changed)" % (src, dst))
