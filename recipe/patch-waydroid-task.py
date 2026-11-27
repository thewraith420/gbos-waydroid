#!/usr/bin/env python3
"""Patch Android 16 donor vendor.waydroid.task@1.0-service for Android 17.

In Googlebook Android 17's IActivityTaskManager.Stub, three transaction codes
used by Waydroid's ATV Android 16 task HAL shifted by -1 or -2:
  - setFocusedTask:              0x15 (21) -> 0x13 (19) at 0x40fe
  - removeTask:                  0x16 (22) -> 0x15 (21) at 0x4286
  - removeAllVisibleRecentTasks: 0x17 (23) -> 0x16 (22) at 0x43f6

Without this patch, focusing any multi-window app calls setFocusedTask(), which
invokes removeTask() on Android 17 and closes the window immediately.
"""
import os
import sys

PATCHES = [
    (
        "setFocusedTask",
        bytes.fromhex("8b07488d9560ffffff488d8de0feffffbe150000004531c0ff502885c0751348"),
        bytes.fromhex("8b07488d9560ffffff488d8de0feffffbe130000004531c0ff502885c0751348"),
    ),
    (
        "removeTask",
        bytes.fromhex("8b07488d9550ffffff488d8dd0feffffbe160000004531c0ff502885c0751348"),
        bytes.fromhex("8b07488d9550ffffff488d8dd0feffffbe150000004531c0ff502885c0751348"),
    ),
    (
        "removeAllVisibleRecentTasks",
        bytes.fromhex("8b07488d9560ffffff488d8de0feffffbe170000004531c0ff502885c0751348"),
        bytes.fromhex("8b07488d9560ffffff488d8de0feffffbe160000004531c0ff502885c0751348"),
    ),
]


def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <input_binary> [output_binary]")
        sys.exit(1)

    in_path = sys.argv[1]
    out_path = sys.argv[2] if len(sys.argv) > 2 else in_path

    with open(in_path, "rb") as f:
        data = bytearray(f.read())

    changed = []
    already_patched = 0

    for name, target, replacement in PATCHES:
        t_cnt = data.count(target)
        r_cnt = data.count(replacement)
        if t_cnt == 1 and r_cnt == 0:
            idx = data.index(target)
            data[idx:idx + len(replacement)] = replacement
            changed.append((name, idx))
        elif t_cnt == 0 and r_cnt == 1:
            already_patched += 1
        else:
            print(
                f"ERROR: unexpected match counts for {name} in {in_path} "
                f"(orig={t_cnt}, patched={r_cnt})",
                file=sys.stderr,
            )
            sys.exit(1)

    parent = os.path.dirname(os.path.abspath(out_path))
    if parent:
        os.makedirs(parent, exist_ok=True)

    if already_patched == len(PATCHES) and not changed:
        print("Already patched.")
        if in_path != out_path:
            with open(out_path, "wb") as f:
                f.write(data)
            os.chmod(out_path, 0o755)
        return

    with open(out_path, "wb") as f:
        f.write(data)
    os.chmod(out_path, 0o755)

    summary = ", ".join(f"{n}@{hex(i)}" for n, i in changed)
    print(f"Successfully patched vendor.waydroid.task@1.0-service ({summary}) -> {out_path}")


if __name__ == "__main__":
    main()
