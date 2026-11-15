#!/usr/bin/env python3
"""Add FLAG_MAIN (0x4000) to an Android user's record in the binary-XML (ABX) file <data>/system/users/<id>.xml.
usage: set-main-user.py <path-to-<id>.xml> [--apply]   (default: dry run, prints what would change)
The flags value is stored as a 4-byte big-endian int after the 'flags' attribute name; this refuses to touch the file unless exactly one
candidate is found and the current flags are ADMIN|FULL|INITIALIZED (0x412) without MAIN. Run only while the container is STOPPED."""
import struct, sys, shutil
p = sys.argv[1]; apply = "--apply" in sys.argv
d = bytearray(open(p, "rb").read())
assert d[:4] == b"ABX\x00", "not an ABX file"
i = d.find(b"flags"); assert i > 0, "no flags attribute"
cur = struct.pack(">i", 0x412)
j = d.find(cur, i, i + 16)
assert j > 0 and d.count(cur) == 1, "expected exactly one 0x412 flags value (already patched, or a different layout)"
new = struct.pack(">i", 0x412 | 0x4000)
print("flags 0x%x -> 0x%x at offset %d" % (0x412, 0x412 | 0x4000, j))
if apply:
    shutil.copy2(p, p + ".bak-before-main")
    d[j:j+4] = new; open(p, "wb").write(d); print("patched (backup: %s)" % (p + ".bak-before-main"))
else:
    print("dry run only")
