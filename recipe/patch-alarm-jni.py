#!/usr/bin/env python3
"""
Patch /system/lib64/libalarm_jni.so so AlarmManagerService's native alarm driver
uses CLOCK_REALTIME (0) and CLOCK_BOOTTIME (7) instead of CLOCK_REALTIME_ALARM (8)
and CLOCK_BOOTTIME_ALARM (9) in timerfd_create().

Inside an LXC container, timerfd_create(CLOCK_REALTIME_ALARM=8) fails with EPERM
("timerfd_create(8) failed: Operation not permitted"), which forces AlarmManagerService
into its broken Java fallback handler ("Failed to open alarm driver. Falling back to a
handler.") that crashes system_server with a NullPointerException in
AlarmManagerService.getAlarmOperationBundle(AlarmManagerService.java:4604) whenever an
OnAlarmListener alarm (operation == null) fires.
"""
import sys

# usage: patch-alarm-jni.py [SRC [DST]]   (defaults: ./libalarm_jni.so -> ./libalarm_jni-gb.so)
SRC = sys.argv[1] if len(sys.argv) > 1 else "libalarm_jni.so"
DST = sys.argv[2] if len(sys.argv) > 2 else "libalarm_jni-gb.so"

# 0x4888: bf 08 00 00 00 (mov $0x8, %edi) -> bf 00 00 00 00 (mov $0x0, %edi = CLOCK_REALTIME)
# 0x48c8: bf 09 00 00 00 (mov $0x9, %edi) -> bf 07 00 00 00 (mov $0x7, %edi = CLOCK_BOOTTIME)
PATCHES = [
    (0x4888, bytes.fromhex("bf08000000"), bytes.fromhex("bf00000000")),
    (0x48c8, bytes.fromhex("bf09000000"), bytes.fromhex("bf07000000")),
]

def main():
    with open(SRC, "rb") as f:
        b = bytearray(f.read())
    for off, orig, repl in PATCHES:
        cur = bytes(b[off:off+len(orig)])
        if cur != orig:
            print(f"UNEXPECTED bytes at 0x{off:x}: found {cur.hex()}, expected {orig.hex()}")
            return 1
        b[off:off+len(orig)] = repl
    with open(DST, "wb") as f:
        f.write(b)
    print(f"Wrote {DST} (CLOCK_REALTIME_ALARM->CLOCK_REALTIME, CLOCK_BOOTTIME_ALARM->CLOCK_BOOTTIME)")
    return 0

if __name__ == "__main__":
    sys.exit(main())
