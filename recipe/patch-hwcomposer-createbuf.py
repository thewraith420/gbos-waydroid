#!/usr/bin/env python3
# Second Waydroid hwcomposer SIGSEGV that reboots the desktop during compositing (webview/scroll/cursor).
# Sibling of patch-hwcomposer-nullbuf.py (which fixed get_buffer_metadata_cros). disable_hwc_overlays=1
# does NOT prevent this path, so it still fires intermittently (confirmed: tombstones repeatedly show it).
#
# Crash (tombstone): composer@2.1-service, SIGSEGV code 1 fault 0x5c (read), in
#   create_buffer_cros(display*, buffer_metadata const&, native_handle const*)+12  (hwcomposer.waydroid.so)
#   <- get_wl_buffer+386 <- apply_hwc_layer_to_window+33 <- handle_layer <- hwc_set
# Disassembly of create_buffer_cros @ 0x38c20:
#   38c20: 55               push %rbp
#   38c21: 48 89 e5         mov %rsp,%rbp
#   38c24: 53               push %rbx
#   38c25: 50               push %rax
#   38c26: 48 89 c8         mov %rcx,%rax          ; rcx = buffer-metadata ptr (null for a buffer-less layer)
#   38c29: 48 89 fb         mov %rdi,%rbx          ; rbx = first arg = return value
#   38c2c: 44 8b 41 5c      mov 0x5c(%rcx),%r8d    ; CRASH when rcx is NULL: reads [null+0x5c]
#   38c30: 8b 49 0c         mov 0xc(%rcx),%ecx
#   38c33: 44 8b 48 20      mov 0x20(%rax),%r9d
#   38c37: 44 8b 50 30      mov 0x30(%rax),%r10d
#   38c3b: 48 83 ec 08      sub $0x8,%rsp
#   38c3f: 50               push %rax
#   38c40: ff 70 64         push 0x64(%rax)
#   38c43: 41 52            push %r10
#   38c45: e8 86 50 03 00   call create_dmabuf_wl_buffer@plt (0x6dcd0)
#   38c4a: 48 83 c4 20      add $0x20,%rsp
#   38c4e: 48 89 d8         mov %rbx,%rax          ; <-- epilogue: return first arg
#   38c51: 48 83 c4 08      add $0x8,%rsp
#   38c55: 5b               pop %rbx
#   38c56: 5d               pop %rbp
#   38c57: c3               ret
#   38c58..38c5f: cc*8       (int3 padding)
#
# Fix: insert `test %rcx,%rcx ; je epilogue` right after the prologue. If the metadata ptr is null, skip
# the derefs + create_dmabuf_wl_buffer and jump to the existing epilogue (mov %rbx,%rax; add $8; pop;ret),
# which correctly unwinds the 3 pushes and returns the first arg (no buffer created -> caller skips the
# layer). Inserting the 5-byte check shifts the body +5, so the relative call is recomputed and the whole
# function + its int3 pad (64 bytes) is rewritten in place (new function is 61 bytes, 3 int3 to spare).
import sys

FN = 0x38c20
ORIG = bytes.fromhex(
    "554889e553504889c84889fb"                                  # prologue (12)
    "448b415c8b490c448b4820448b50304883ec0850ff70644152"        # derefs + 3 arg pushes (25)
    "e886500300"                                                # call 0x6dcd0 (5)
    "4883c4204889d84883c4085b5dc3"                              # epilogue (14)
    "cccccccccccccccc"                                          # int3 padding (8)
)
OLD_PATCH = bytes.fromhex(
    "554889e553504889c84889fb"                                  # prologue (12)
    "4885c9"                                                    # test %rcx,%rcx          (3)
    "7422"                                                      # je  +0x22 -> epilogue   (2)
    "448b415c8b490c448b4820448b50304883ec0850ff70644152"        # derefs + 3 arg pushes (25)
    "e881500300"                                                # call 0x6dcd0 (rel recomputed for +5 shift)
    "4883c4204889d84883c4085b5dc3"                              # epilogue (14) ; je target = mov %rbx,%rax
    "cccccc"                                                    # int3 padding (3)
)
PATCH = bytes.fromhex(
    "554889e553504889c84889fb"                                  # prologue (12)
    "48890f"                                                    # mov %rcx,(%rdi) -> *sret = 0 when rcx is NULL (3)
    "4885c9"                                                    # test %rcx,%rcx          (3)
    "7422"                                                      # je  +0x22 -> epilogue   (2)
    "448b415c8b490c448b4820448b50304883ec0850ff70644152"        # derefs + 3 arg pushes (25)
    "e87e500300"                                                # call 0x6dcd0 (rel recomputed for +8 shift)
    "4883c4204889d84883c4085b5dc3"                              # epilogue (14) ; je target = mov %rbx,%rax
)

def main(path):
    assert len(ORIG) == len(OLD_PATCH) == len(PATCH) == 64, (len(ORIG), len(OLD_PATCH), len(PATCH))
    with open(path, "rb") as f:
        b = bytearray(f.read())
    cur = bytes(b[FN:FN+64])
    if cur == PATCH:
        print("already patched (v2 sret-zero)"); return 0
    if cur not in (ORIG, OLD_PATCH):
        print(f"UNEXPECTED bytes at 0x{FN:x}:\n  found:    {cur.hex()}\n  expected: {ORIG.hex()}"); return 1
    b[FN:FN+64] = PATCH
    with open(path, "wb") as f:
        f.write(b)
    print(f"patched create_buffer_cros @ 0x{FN:x} (null-metadata check + sret zeroed)")
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
