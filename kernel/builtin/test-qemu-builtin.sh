#!/bin/bash
# QEMU test bed (rule 5) for the BUILT-IN gbos_vexemu (CONFIG_GBOS_VEXEMU=y). Same programs and checks as
# ../vexemu/test/test-qemu.sh, but nothing is insmod'ed: the emulator must already be active when the first userland
# process (our init) starts, which is exactly what bare-metal GBOS needs.
#   * control run:  gbos_vexemu.enabled=0 on the command line  -> must die with SIGILL (the test CPU really lacks the instructions)
#   * test runs:    default                                     -> vextest (9000+ cases), addr (addressing modes), ratest (run-ahead)
#   test-qemu-builtin.sh KERNEL_IMAGE [PATCH]       PATCH: the pinned vex-emulate.patch (default ../../../gbos-release-work/... not assumed; pass it)
# TCG, -cpu Skylake-Client (no GFNI/VAES/VPCLMULQDQ/AVX-VNNI/SHA-NI), 256 MB, one vCPU. Works on a kernel built with -mno-... defaults.
set -euo pipefail
K=${1:?kernel image (bzImage) built with CONFIG_GBOS_VEXEMU=y}
H=$(cd "$(dirname "$0")" && pwd); T=$H/../vexemu/test; PATCH=${2:?pinned vex-emulate.patch (see gbos-vexemu.py)}
W=$(mktemp -d "${TMPDIR:-$PWD}/vexemu-bi-test.XXXXXX"); trap 'rm -rf "$W"' EXIT       # on disk, not in RAM-backed /tmp
python3 "$H/../gbos-vexemu.py" math --patch "$PATCH" --out "$W/math.inc"
cp "$T"/ops.h "$T"/math.c "$W"/
cc -O2 -c -o "$W/math.o" "$W/math.c" -I"$W"
cc -O2 -static -o "$W/vextest" "$T/vextest.c" "$W/math.o" -I"$W"
cc -O1 -static -o "$W/addr" "$T/addr.c"
cc -O2 -static -o "$W/ratest" "$T/ratest.c"
cc -O2 -static -o "$W/init" "$T/init.c"
command -v cpio >/dev/null || { echo "need cpio (apt install cpio)"; exit 1; }
mkdir -p "$W/root/dev" "$W/root/proc" "$W/root/sys"
mknod -m 600 "$W/root/dev/console" c 5 1 2>/dev/null || true
cp "$W/init" "$W/vextest" "$W/addr" "$W/ratest" "$W/root/"
( cd "$W/root" && find . | cpio -o -H newc --quiet -R 0:0 ) | gzip -9 > "$W/initramfs.gz"
rc=0
run() {   # name extra-kernel-args init-args
  timeout "${TMO:-900}" qemu-system-x86_64 -accel tcg -cpu Skylake-Client -smp 1 -m 256 -nographic -no-reboot \
    -kernel "$K" -initrd "$W/initramfs.gz" -append "console=ttyS0 panic=1 loglevel=7 rdinit=/init $2 -- nomod $3" > "$W/$1.log" 2>&1 || true
  tr -d '\r' < "$W/$1.log" > "$W/$1.txt"
}
grep_n() { grep -c "$1" "$2" || true; }

# 0. the emulator announced itself during boot, before init
run boot "" vextest
if grep -q "gbos_vexemu: loaded" "$W/boot.txt"; then echo "ok   built-in emulator registered at boot ($(grep -m1 'gbos_vexemu: loaded' "$W/boot.txt" | cut -c1-80))"
else echo "FAIL no 'gbos_vexemu: loaded' in the boot log (is CONFIG_GBOS_VEXEMU=y in this kernel?)"; rc=1; fi

for prog in vextest addr; do
  run "$prog-off" "gbos_vexemu.enabled=0" "$prog"
  if grep -q "SIGILL" "$W/$prog-off.txt"; then echo "ok   $prog with gbos_vexemu.enabled=0: SIGILL (the CPU lacks the instructions, as intended)"
  else echo "FAIL $prog with enabled=0 did not SIGILL - the test CPU is not missing the instructions"; rc=1; fi
  run "$prog-on" "" "$prog"
  if grep -qE "RESULT: [0-9]+ passed, 0 failed|ADDR: [0-9]+ passed, 0 failed" "$W/$prog-on.txt" && ! grep -q "FAIL" "$W/$prog-on.txt"; then
    echo "ok   $prog built-in: $(grep -E 'RESULT|ADDR' "$W/$prog-on.txt" | tail -1)"
  else echo "FAIL $prog built-in:"; grep -E "FAIL|BUG|WARN|Oops|INIT|SIGILL" "$W/$prog-on.txt" | head; rc=1; fi
done

traps() { awk -F= '/^(vp|gf|sha|vaes)/ && $1 !~ /runahead/ {s+=$2} END{print s+0}' "$1"; }
for mode in ra0 ra; do
  run "ratest-$mode" "$([ $mode = ra0 ] && echo gbos_vexemu.runahead=0)" ratest
  if grep -q "RATEST: all passed" "$W/ratest-$mode.txt"; then
    echo "ok   ratest (run-ahead $([ $mode = ra0 ] && echo off || echo on)): all results correct, $(traps "$W/ratest-$mode.txt") traps"
  else echo "FAIL ratest ($mode):"; grep -E "FAIL|RATEST|BUG|WARN|Oops|INIT|SIGILL" "$W/ratest-$mode.txt" | head; rc=1; fi
done
exit $rc
