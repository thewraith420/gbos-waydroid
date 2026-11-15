#!/usr/bin/env python3
# Googlebook OS on Waydroid: make libservice-connectivity.so (tethering APEX) load on a
# host kernel that has no SELinux (this host runs AppArmor as its LSM).
#
# When system_server loads libservice-connectivity.so, JNI_OnLoad ->
# register_com_android_server_connectivity_ClatCoordinator() verifies the pinned clat BPF
# objects under /sys/fs/bpf/net_shared. For each one a helper lstat()s it and reads its
# SELinux label with lgetxattr("security.selinux"), comparing against
# "u:object_r:fs_bpf_net_shared:s0". On a kernel without SELinux the label does not exist,
# so lgetxattr fails with ENODATA (errno 61), the helper sets a global "verification failed"
# byte, and the register function then calls abort() -> system_server crash-loops ~64s into
# every boot (confirmed in the tombstone: abort <- register_...ClatCoordinator+683 <- JNI_OnLoad).
#
# That byte is READ in exactly one place (the cmpb right before the abort branch), so the fix
# is fully contained: NOP the conditional jump to abort so registration always proceeds and
# JNI_OnLoad returns success. The context check still runs and logs; its result is simply
# ignored, which is correct here -- there is no SELinux on this host to enforce, and clat
# (464XLAT) is never exercised under Waydroid's IPv4 host NAT anyway.
#
#   bbf0: 80 3d a5 05 01 00 01   cmpb $0x1, 0x1c19c(%rip)   ; any pin failed verification?
#   bbf7: 0f 84 1a 01 00 00      je   bd17  <- abort        ; -> replace with 6x nop
#   bbfd: ...                    (normal JNI RegisterNatives path, returns 0)
#
# usage: patch-clat-selinux.py IN.so OUT.so
import sys

CMP = bytes.fromhex('803da505010001')   # cmpb $0x1, 0x1c19c(%rip)
JE  = bytes.fromhex('0f841a010000')     # je bd17 (6-byte rel32 conditional jump to abort)
NOP6 = b'\x90' * 6

src, dst = sys.argv[1], sys.argv[2]
d = bytearray(open(src, 'rb').read())

needle = CMP + JE
hits = []
i = d.find(needle)
while i != -1:
    hits.append(i)
    i = d.find(needle, i + 1)

if len(hits) != 1:
    sys.exit(f'expected exactly 1 match of cmpb+je abort sequence, found {len(hits)}')

off = hits[0] + len(CMP)          # start of the `je`
assert d[off:off + 6] == JE
d[off:off + 6] = NOP6

open(dst, 'wb').write(d)
print(f'patched je->abort at file offset 0x{off:x} (6x nop) -> {dst}')
