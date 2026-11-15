#!/usr/bin/env python3
# OS bringup: stop com.google.android.bluetooth crash-looping in the container.
#
# bluetooth::os::Alarm creates its timer with timerfd_create(CLOCK_BOOTTIME_ALARM, ...). The *_ALARM
# clocks arm timers that can wake the machine from suspend, which the kernel only permits with
# CAP_WAKE_ALARM in the INITIAL user namespace -- a container can't hold that. So timerfd_create fails
# EPERM and the BT stack aborts (system/gd/os/linux_generic/alarm.cc:50). The BT app is persistent, so
# it crash-loops; after 5 crashes RescueParty reboots the whole container (the "~5 times then stays
# down" boot loop). The container never suspends, so a plain CLOCK_BOOTTIME timer behaves identically
# and needs no capability. Patch both call sites in libbluetooth_jni.so to clock id 7 (CLOCK_BOOTTIME):
#   cc508c  lea 0x7(,%r14,2),%edi   (= 7 + 2*is_wake -> 7 or 9)  -> mov $0x7,%edi ; nop*3
#   cd513c  mov $0x9,%edi           (CLOCK_BOOTTIME_ALARM)         -> mov $0x7,%edi
import sys

LBJ = sys.argv[1]
dst = sys.argv[2]
d = bytearray(open(LBJ, 'rb').read())

def patch_one(anchor, new, name):
    hits = []
    i = d.find(anchor)
    while i != -1:
        hits.append(i); i = d.find(anchor, i + 1)
    if len(hits) != 1:
        sys.exit(f'{name}: expected 1 match, found {len(hits)}')
    d[hits[0]:hits[0] + len(new)] = new
    print(f'{name}: patched at 0x{hits[0]:x}')

# 1) lea 0x7(,%r14,2),%edi  (42 8d 3c 75 07 00 00 00)  -> mov $0x7,%edi ; nop ; nop ; nop
patch_one(bytes.fromhex('428d3c7507000000'),
          bytes.fromhex('bf07000000909090'), 'Alarm is_wake lea')

# 2) mov $0x9,%edi ; xor %esi,%esi ; call  (bf 09 00 00 00 31 f6 e8) -> mov $0x7,%edi ...
patch_one(bytes.fromhex('bf090000003 1f6e8'.replace(' ', '')),
          bytes.fromhex('bf070000003 1f6e8'.replace(' ', '')), 'hardcoded CLOCK_BOOTTIME_ALARM')

open(dst, 'wb').write(d)
print(f'wrote {dst}')
