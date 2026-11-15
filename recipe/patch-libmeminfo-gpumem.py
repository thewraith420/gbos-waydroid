#!/usr/bin/env python3
# OS bringup: stop the GPU-memory BPF abort in libmeminfo.so.
#
# StatsPullAtomService periodically pulls the GPU_MEM_PER_UID atom, which calls
# android::KernelAllocationStats_getGpuAllocations (libandroid_runtime) ->
# android::meminfo::ReadPerProcessGpuMem (libmeminfo) -> BpfMapRO::abortOnMismatch -> abort(),
# because the gpu_mem BPF map isn't loaded in the container. That SIGABRTs system_server a few
# minutes after the desktop appears (the post-boot crash loop). This is the same gpu_mem family
# as getGpuTotalUsageKb/getGpuPrivateMemoryKb, already stubbed in libandroid_runtime-gb.so, but
# getGpuAllocations reaches the BPF map through a THIRD path, inside libmeminfo.
#
# Fix: make ReadPerProcessGpuMem return true immediately, before it constructs the BpfMapRO, so
# the out-map stays empty and nothing touches the BPF map. The JNI caller then builds empty
# int[]/long[] arrays (zero GPU-using processes) instead of aborting. We overwrite the function
# prologue (push %rbp; ...) with `mov eax,1; ret`; on entry rsp still points at the return
# address, so ret returns cleanly with no frame set up and no callee-saved register clobbered.
import sys

# android::meminfo::ReadPerProcessGpuMem(std::unordered_map<uint,ulong>*)  @ file offset 0x276d0
FN = 0x276d0
ORIG = bytes.fromhex("554889e54157")      # push %rbp; mov %rsp,%rbp; push %r15
PATCH = bytes.fromhex("b801000000c3")     # mov $0x1,%eax; ret   (return true)

def main(path):
    with open(path, "rb") as f:
        b = bytearray(f.read())
    cur = bytes(b[FN:FN+len(ORIG)])
    if cur == PATCH:
        print("already patched")
        return 0
    if cur != ORIG:
        print(f"UNEXPECTED bytes at 0x{FN:x}: {cur.hex()} (expected {ORIG.hex()})")
        return 1
    b[FN:FN+len(PATCH)] = PATCH
    with open(path, "wb") as f:
        f.write(b)
    print(f"patched ReadPerProcessGpuMem @ 0x{FN:x}: {ORIG.hex()} -> {PATCH.hex()}")
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
