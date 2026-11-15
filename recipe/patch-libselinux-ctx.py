#!/usr/bin/env python3
# OS bringup: make SELinux file-context reads/writes succeed harmlessly on a kernel with no SELinux.
#
# This host runs AppArmor, so files carry no security.selinux xattr and getfilecon()/lgetfilecon()
# return ENODATA. Android components that read a file's context and LOG(FATAL) on failure then abort
# (zygote app fork at Zygote.cpp:1290, vold_prepare_subdirs, etc.). We already fake is_selinux_enabled
# ->0 and setfscreatecon->0 in this libselinux, but callers that read contexts directly still fail.
#
# Patch libselinux so the whole class is non-fatal:
#   getfilecon(path, con)  -> *con = strdup(path); return 1     (non-null, freeable stand-in context)
#   lgetfilecon(path, con) -> same
#   setfilecon(path, con)  -> return 0                          (no-op success)
#   lsetfilecon(path, con) -> return 0
# The context *value* is irrelevant because the setters no-op and nothing on this host enforces SELinux;
# returning strdup(path) just gives callers a valid, freeable C string so their get/compare/free/set
# flow completes. freecon() is left intact (the string is a real malloc'd copy).
import sys, struct

LSE = sys.argv[1]
d = bytearray(open(LSE, 'rb').read())

STRDUP_PLT = 0x1bab0
GETFILECON  = 0xed90
LGETFILECON = 0x168d0
SETFILECON  = 0x18670
LSETFILECON = 0x16bc0

def getter_stub(fn_off):
    # 56                 push rsi            (save con**, and 16-align the stack for the call)
    # e8 <rel32>         call strdup@plt     (rdi still = path on entry)
    # 5e                 pop rsi
    # 48 89 06           mov [rsi], rax      (*con = strdup(path))
    # b8 01 00 00 00     mov eax, 1          (return >0 = success)
    # c3                 ret
    call_site = fn_off + 1
    rel = (STRDUP_PLT - (call_site + 5)) & 0xffffffff
    return b'\x56' + b'\xe8' + struct.pack('<I', rel) + b'\x5e' + b'\x48\x89\x06' + b'\xb8\x01\x00\x00\x00' + b'\xc3'

RET0 = b'\x31\xc0\xc3'   # xor eax,eax ; ret

# sanity: each target must currently start with the standard prologue 'push rbp; mov rsp,rbp' (55 48 89 e5)
for name, off in [('getfilecon', GETFILECON), ('lgetfilecon', LGETFILECON),
                  ('setfilecon', SETFILECON), ('lsetfilecon', LSETFILECON)]:
    if bytes(d[off:off+4]) != bytes.fromhex('554889e5'):
        sys.exit(f'{name} @0x{off:x}: unexpected prologue {bytes(d[off:off+4]).hex()} (offset!=vaddr?)')

g = getter_stub(GETFILECON);  d[GETFILECON:GETFILECON+len(g)]   = g
l = getter_stub(LGETFILECON); d[LGETFILECON:LGETFILECON+len(l)] = l
d[SETFILECON:SETFILECON+3]   = RET0
d[LSETFILECON:LSETFILECON+3] = RET0

open(LSE, 'wb').write(d)
print(f'patched getfilecon/lgetfilecon -> strdup(path)+success, setfilecon/lsetfilecon -> 0 in {LSE}')
