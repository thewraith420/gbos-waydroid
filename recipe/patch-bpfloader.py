#!/usr/bin/env python3
"""Patch bpfloader to prevent fatal abort on userdebug test BPF failures.

In Android 17's BpfLoader-rs (bpfloader), when ro.build.type != "user" (e.g. userdebug),
the loader tests bpfRingbufProg.bpf. If loading fails or encounters verifier issues,
it executes:
    f6 85 20 fb ff ff 01   testb $0x1, -0x4e0(%rbp)
    0f 85 bf 20 00 00       jne panic (0xccbd)

This patch changes 0x01 to 0x00 (testb $0x0), making the panic branch unreachable.
Failures are logged as normal errors instead of causing bpfloader to SIGABRT, which
would freeze init at wait_for_prop bpf.progs_loaded 1.
"""
import sys

def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <input_bpfloader> [output_bpfloader]")
        sys.exit(1)

    in_path = sys.argv[1]
    out_path = sys.argv[2] if len(sys.argv) > 2 else in_path

    with open(in_path, "rb") as f:
        data = bytearray(f.read())

    target = bytes.fromhex("f68520fbffff010f85bf200000")
    replacement = bytes.fromhex("f68520fbffff000f85bf200000")

    idx = data.find(target)
    if idx == -1:
        # Check if already patched
        if data.find(replacement) != -1:
            print("Already patched.")
            if in_path != out_path:
                with open(out_path, "wb") as f:
                    f.write(data)
            return
        print(f"ERROR: target pattern not found in {in_path}", file=sys.stderr)
        sys.exit(1)

    data[idx:idx + len(replacement)] = replacement
    with open(out_path, "wb") as f:
        f.write(data)

    print(f"Successfully patched bpfloader at offset {hex(idx)} -> {out_path}")

if __name__ == "__main__":
    main()
