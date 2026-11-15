#!/bin/bash
# Test gbos_vexemu without touching real hardware: boot YOUR kernel image in QEMU with software emulation (TCG) as a
# CPU that lacks the instructions (Skylake-Client), and run two test programs as init:
#   vextest  every emulated instruction vs the validated arithmetic (9000+ cases)
#   addr     addressing modes (SIB, RIP-relative, high registers), page not yet resident
# Each runs twice: WITHOUT the module (must die with SIGILL: proves the CPU really lacks the instructions) and WITH it.
#   test-qemu.sh KERNEL_IMAGE MODULE.ko [PATCH]      PATCH: the pinned vex-emulate.patch (see gbos-vexemu.py); default ../../out/vex-emulate.patch
set -euo pipefail
K=${1:?kernel image (vmlinuz/bzImage) of the SAME build the module was built for}; KO=${2:?module .ko}
H=$(cd "$(dirname "$0")" && pwd); PATCH=${3:-$H/../vex-emulate.patch}
W=$(mktemp -d "${TMPDIR:-$PWD}/vexemu-test.XXXXXX"); trap 'rm -rf "$W"' EXIT       # on disk, not in RAM-backed /tmp, if you can
python3 "$H/../../gbos-vexemu.py" math --patch "$PATCH" --out "$W/math.inc"
cp "$H"/ops.h "$H"/math.c "$W"/
cc -O2 -c -o "$W/math.o" "$W/math.c" -I"$W"
cc -O2 -static -o "$W/vextest" "$H/vextest.c" "$W/math.o" -I"$W"
cc -O1 -static -o "$W/addr" "$H/addr.c"
cc -O2 -static -o "$W/ratest" "$H/ratest.c"
cc -O2 -static -o "$W/init" "$H/init.c"
command -v cpio >/dev/null || { echo "need cpio (apt install cpio)"; exit 1; }
mkdir -p "$W/root/dev" "$W/root/proc" "$W/root/sys"
mknod -m 600 "$W/root/dev/console" c 5 1 2>/dev/null || true   # without it the kernel prints to ttyS0 anyway
cp "$W/init" "$W/vextest" "$W/addr" "$W/ratest" "$W/root/"; cp "$(readlink -f "$KO")" "$W/root/gbos_vexemu.ko"
( cd "$W/root" && find . | cpio -o -H newc --quiet -R 0:0 ) | gzip -9 > "$W/initramfs.gz"
rc=0
for prog in vextest addr; do for mode in nomod mod; do
  extra=$prog; [ $mode = nomod ] && extra="nomod $prog"
  timeout "${TMO:-900}" qemu-system-x86_64 -accel tcg -cpu Skylake-Client -smp 1 -m 256 -nographic -no-reboot \
    -kernel "$K" -initrd "$W/initramfs.gz" -append "console=ttyS0 panic=1 loglevel=4 rdinit=/init $extra" > "$W/$prog-$mode.log" 2>&1 || true
  if [ $mode = nomod ]; then
    if grep -q "SIGILL" "$W/$prog-$mode.log"; then echo "ok   $prog without module: SIGILL (the CPU lacks the instructions, as intended)"
    else echo "FAIL $prog without module did not SIGILL - the test CPU is not missing the instructions"; rc=1; fi
  else
    if grep -qE "RESULT: [0-9]+ passed, 0 failed|ADDR: [0-9]+ passed, 0 failed" "$W/$prog-$mode.log" && ! grep -q "FAIL" "$W/$prog-$mode.log"; then
      echo "ok   $prog with module: $(grep -E 'RESULT|ADDR' "$W/$prog-$mode.log" | tail -1)"
    else echo "FAIL $prog with module:"; grep -E "FAIL|BUG|WARN|Oops|INIT" "$W/$prog-$mode.log" | head; rc=1; fi
  fi
done; done
# run-ahead: real SHA-NI / AVX-VNNI loops must give identical results with it on and off, and on must need far fewer traps
traps() { awk -F= '/^(vp|gf|sha|vaes)/ && $1 !~ /runahead/ {s+=$2} END{print s+0}' "$1"; }
for mode in ra0 ra; do
  timeout "${TMO:-900}" qemu-system-x86_64 -accel tcg -cpu Skylake-Client -smp 1 -m 256 -nographic -no-reboot \
    -kernel "$K" -initrd "$W/initramfs.gz" -append "console=ttyS0 panic=1 loglevel=4 rdinit=/init ratest $([ $mode = ra0 ] && echo ra0)" > "$W/ratest-$mode.log" 2>&1 || true
  tr -d '\r' < "$W/ratest-$mode.log" > "$W/ratest-$mode.txt"
  if grep -q "RATEST: all passed" "$W/ratest-$mode.txt"; then echo "ok   ratest (run-ahead $([ $mode = ra0 ] && echo off || echo on)): all results correct, $(traps "$W/ratest-$mode.txt") traps, $(grep runahead_instructions "$W/ratest-$mode.txt" | cut -d= -f2) run-ahead instructions"
  else echo "FAIL ratest ($mode):"; grep -E "FAIL|RATEST|BUG|WARN|Oops|INIT" "$W/ratest-$mode.txt" | head; rc=1; fi
done
exit $rc
