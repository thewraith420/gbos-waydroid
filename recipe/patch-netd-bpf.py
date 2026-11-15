#!/usr/bin/env python3
# Make netd's BPF programs (netd.o from Googlebook's tethering APEX) work inside the Waydroid
# container's own network namespace.
#
# Each affected program compares bpf_get_netns_cookie(sk) with bpf_get_netns_cookie(NULL)
# (the host's initial netns) and does nothing for sockets in any other namespace. In the
# container every socket is in another namespace, so netd's self-tests fail. The programs are
# only attached to the container's cgroup, so treating its namespace as "ours" is correct.
#
# Patch: right after the second netns-cookie call, the first register-register conditional
# jump compares a register with itself instead (offset unchanged):
#   jeq rA, rB, +off  ->  jeq rA, rA, +off   (always true: "same namespace" path)
#   jne rA, rB, +off  ->  jne rA, rA, +off   (never true: never take the exit)
# Keeping the jump conditional matters: the kernel verifier rejects programs with
# unreachable instructions, which an unconditional jump would create.
#
# usage: patch-netd-bpf.py IN.o OUT.o
import sys

CALL_NETNS_COOKIE = bytes.fromhex('850000007a000000')   # call bpf_get_netns_cookie (helper 122)
JEQ_REG, JNE_REG = 0x1d, 0x5d

src, dst = sys.argv[1], sys.argv[2]
d = bytearray(open(src, 'rb').read())

calls, i = [], d.find(CALL_NETNS_COOKIE)
while i != -1:
    calls.append(i)
    i = d.find(CALL_NETNS_COOKIE, i + 8)

patched = 0
# Calls come in pairs within a program: cookie(sk), then cookie(NULL) a few insns later.
for first, second in zip(calls[0::2], calls[1::2]):
    assert 0 < second - first <= 6 * 8 and (second - first) % 8 == 0, (first, second)
    for k in range(1, 5):
        o = second + 8 * k
        op = d[o]
        if op in (JEQ_REG, JNE_REG):
            reg = d[o + 1] & 0x0f
            d[o + 1] = (reg << 4) | reg      # src register := dst register
            patched += 1
            break
        if op == 0x95:      # exit: program has no check (just returns)
            break

open(dst, 'wb').write(d)
print(f'{len(calls) // 2} netns-cookie checks found, {patched} patched -> {dst}')
