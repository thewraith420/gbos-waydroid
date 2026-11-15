#!/usr/bin/env python3
# Stop the Waydroid hwcomposer SIGSEGV that reboots the desktop during compositing (mouse/scroll).
#
# Crash (tombstone): composer@2.1-service, SIGSEGV code 1 fault 0x54, in
#   get_buffer_metadata_cros(display*, hwc_layer_1* layer, ulong) +4  (hwcomposer.waydroid.so)
#   <- get_wl_buffer <- apply_hwc_layer_to_window <- hwc_set
# Disassembly of get_buffer_metadata_cros @ 0x38820:
#   38820: 48 8b 4e 10   mov 0x10(%rsi),%rcx    ; rcx = layer->buffer handle
#   38824: 8b 41 54      mov 0x54(%rcx),%eax    ; CRASH when rcx (buffer) is NULL: reads [null+0x54]
#   38827: 8b 71 58      mov 0x58(%rcx),%esi
#   3882a: 48 8b 51 78   mov 0x78(%rcx),%rdx
#   3882e: 48 c1 e0 20   shl $0x20,%rax
#   38832: 48 09 f0      or  %rsi,%rax
#   38835: c3            ret                    ; returns packed metadata in rax (:rdx)
#   38836..3883f: cc*10  (int3 padding)
# A layer with no backing buffer (seen during scroll/cursor compositing) has a null handle, and this
# function dereferences it without a null check -> crash -> whole container reboots.
#
# Fix: insert a null check. If the buffer handle is null, return 0 (rax=rdx=0 -> "no metadata") so the
# caller skips that layer, instead of crashing. The rewrite fits exactly in the function's 22 bytes plus
# its 10 bytes of int3 padding (32 bytes total):
#   mov 0x10(%rsi),%rcx ; test %rcx,%rcx ; je ret0 ; <original reads/pack/ret> ; ret0: xor eax,eax; xor edx,edx; ret
import sys

FN = 0x38820
ORIG = bytes.fromhex(
    "488b4e10"            # mov 0x10(%rsi),%rcx
    "8b4154"              # mov 0x54(%rcx),%eax
    "8b7158"              # mov 0x58(%rcx),%esi
    "488b5178"            # mov 0x78(%rcx),%rdx
    "48c1e020"            # shl $0x20,%rax
    "4809f0"              # or  %rsi,%rax
    "c3"                  # ret
    "cccccccccccccccccccc"  # 10x int3 padding
)
PATCH = bytes.fromhex(
    "488b4e10"            # mov 0x10(%rsi),%rcx
    "4885c9"              # test %rcx,%rcx
    "7412"                # je  +0x12  -> ret0 (xor eax,eax at block offset 0x1b)
    "8b4154"              # mov 0x54(%rcx),%eax
    "8b7158"              # mov 0x58(%rcx),%esi
    "488b5178"            # mov 0x78(%rcx),%rdx
    "48c1e020"            # shl $0x20,%rax
    "4809f0"              # or  %rsi,%rax
    "c3"                  # ret
    "31c0"               # ret0: xor %eax,%eax
    "31d2"               # xor %edx,%edx
    "c3"                 # ret
)

def main(path):
    assert len(ORIG) == len(PATCH) == 32, (len(ORIG), len(PATCH))
    with open(path, "rb") as f:
        b = bytearray(f.read())
    cur = bytes(b[FN:FN+32])
    if cur == PATCH:
        print("already patched"); return 0
    if cur != ORIG:
        print(f"UNEXPECTED bytes at 0x{FN:x}: {cur.hex()}\n     expected: {ORIG.hex()}"); return 1
    b[FN:FN+32] = PATCH
    with open(path, "wb") as f:
        f.write(b)
    print(f"patched get_buffer_metadata_cros @ 0x{FN:x} (null-buffer check added)")
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
