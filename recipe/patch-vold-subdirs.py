#!/usr/bin/env python3
# OS bringup: make /system/bin/vold_prepare_subdirs tolerate missing SELinux file contexts.
#
# When HSUM (Googlebook is multi-user desktop) prepares the system user's data, vold runs
# vold_prepare_subdirs, which for each subdir reads the existing dir's SELinux context with
# lgetfilecon() to replicate it onto the new ".new" dir. This host kernel has no SELinux, so
# lgetfilecon returns ENODATA ("Unable to read secontext for: /data/user_de/0"), the helper exits 255,
# UserDataPreparer fails, and its failure handler tries to reboot-to-recovery-and-wipe -> that reboot
# fails in a container -> system_server FATAL. (Same root cause as the libselinux/ClatCoordinator/installd
# no-SELinux-xattr issues.)
#
# The restorecon-replicate helper at 0x6500:
#   653b  call lgetfilecon
#   6540  test eax,eax
#   6542  js   65bf                 ; lgetfilecon < 0 -> failure path
#   ...   strcmp expected vs read, lsetfilecon if different ...
#   65bf  xor  r14d,r14d  (= false) ; <-- failure path sets result 0, logs "Unable to read secontext"
#   65c2  mov  edi,4; ...           ;     then freecon tail -> returns false
#   64ee  xor edi,edi; setfscreatecon(NULL); mov r14d,eax; ret   ; clean return tail
#
# Patch the failure path to return SUCCESS (result 1) and jump to the clean tail at 64ee, skipping the
# freecon of the (unset) context pointer. On a no-SELinux system "ensure this file has context X" is a
# no-op, so success is the correct result.
#   65bf: 45 31 f6  bf 04 00 00 00   (xor r14d,r14d ; mov edi,4)
#   ->    41 b6 01  e9 27 ff ff ff   (mov r14b,1    ; jmp 64ee)   rel32 = 64ee-(65c2+5) = -0xD9
import sys

# anchor: the preceding `jmp 64e6` (from the adjacent success path at 65ba) + the failure-path head,
# to pin the exact lgetfilecon-failure location (the 8-byte head alone is not unique).
ANCHOR = bytes.fromhex('e9 27 ff ff ff 45 31 f6 bf 04 00 00 00'.replace(' ', ''))
NEW_PREFIX = bytes.fromhex('41 b6 01 e9 27 ff ff ff'.replace(' ', ''))  # mov r14b,1; jmp rel32(-0xD9)
PFX = 5  # bytes of the leading jmp before the 8 bytes we overwrite

src, dst = sys.argv[1], sys.argv[2]
d = bytearray(open(src, 'rb').read())

hits = []
i = d.find(ANCHOR)
while i != -1:
    hits.append(i)
    i = d.find(ANCHOR, i + 1)
if len(hits) != 1:
    sys.exit(f'expected exactly 1 match of the lgetfilecon-failure sequence, found {len(hits)}')

off = hits[0] + PFX
d[off:off + 8] = NEW_PREFIX          # keep the trailing '31 f6' (now unreachable dead code)
open(dst, 'wb').write(d)
print(f'patched lgetfilecon-failure -> success at file offset 0x{off:x} -> {dst}')
