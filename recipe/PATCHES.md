# Binary patches: what each one changes and where its input comes from

Everything here is applied to files **extracted from the user's own downloaded images** at install time.
Nothing patched is ever stored in this repository. Each script checks the original bytes at every site before
writing, so an image of a different build stops the install with a clear message instead of being patched blindly.

Base build: Lenovo Googlebook 15 recovery `17.CL3B.260622.271.R1` (see `sources.lock`).

Status key: **exact** = running the script on the file from the image produced a byte-identical copy of the
file the working system uses (checked 2026-11-04). **pattern** = the script finds its target by byte pattern,
so it does not depend on an exact build.

| Output (what the image gets) | Input file, from | Script(s), in order | Status |
|---|---|---|---|
| `libselinux.so` | Googlebook `system_a`: `/system/lib64/libselinux.so` | `patch-libselinux-base.py`, then `patch-libselinux-ctx.py`; `bake-apps.sh` applies a third, in-line `getprevcon` patch | **exact** (base + ctx) |
| `libalarm_jni.so` | `system_a`: `/system/lib64/libalarm_jni.so` | `patch-alarm-jni.py SRC DST` | **exact** |
| `libmeminfo.so` | `system_a`: `/system/lib64/libmeminfo.so` | `patch-libmeminfo-gpumem.py FILE` (in place) | **exact** |
| `libandroid_runtime.so` | `system_a`: `/system/lib64/libandroid_runtime.so` | `patch-androidruntime-stubs.py FILE` (in place) | **exact** (script reconstructed from the diff, 2026-11-04) |
| `libservice-connectivity.so` | `system_a`: `/system/apex/com.google.android.tethering.apex` -> `apex_payload.img` (erofs) -> `/lib64/libservice-connectivity.so` | `patch-clat-selinux.py SRC DST` | **exact** |
| `netd.o` | same apex payload: `/etc/bpf/mainline/netd.o` | `patch-netd-bpf.py SRC DST`, then `patch-netd-bpf-extra.py FILE` (in place) | **exact** (the extra script was reconstructed from the diff, 2026-11-04) |
| `vold_prepare_subdirs` | `system_a`: `/system/bin/vold_prepare_subdirs` | `patch-vold-subdirs.py SRC DST` | **exact** (compared with the file in the running container; the copy in the old work tree is root-only) |
| `bpfloader` | `system_a`: `/system/bin/bpfloader` | `patch-bpfloader.py IN [OUT]` | **functionally identical**. All loaded segments match the working copy byte for byte; only the ELF section-header metadata after the loaded data (3,246 bytes, plus `e_shstrndx`) differs, which the kernel loader ignores. The script finds its target by byte pattern. |
| `libbluetooth_jni.so` | `system_a`: `/system/apex/com.google.android.bt.apex` -> `apex_payload.img` (erofs) -> `/lib64/libbluetooth_jni.so` | `patch-bt-alarm.py SRC DST` | **exact** |
| `hwcomposer.waydroid.so` | Lineage 23.2 vendor image: `/lib64/hw/hwcomposer.waydroid.so` | `patch-hwcomposer-nullbuf.py`, `patch-hwcomposer-createbuf.py` (both run inside `try.sh`) | **pattern/offset**, runs in `try.sh` |
| `libaudiohal@7.1.so`, `libinputflinger.so` | `system_a`: `/system/lib64/` | in-line Python in `bake-apps.sh` | the originals are byte-identical to the `.orig` files the working system uses |
| `input-bridge` | built from `native/input-bridge.c` | none (source fixed, default 1920x1080, `persist.gbos.display` overrides) | the earlier rebuild with the same default was instruction-identical to the working binary; the override is new and has not been run on a device |

## Why `libselinux` needed a new script

`libselinux-gb.so` (the working system's copy) differs from the stock file at 45 bytes. The old
`patch-libselinux-ctx.py` covers 38 of them. The other 7 bytes are three earlier hand edits that never had a
script: `is_selinux_enabled()` returns 0, `setfscreatecon()` returns 0, and one conditional jump is made
unconditional. They are now `patch-libselinux-base.py`, and base + ctx reproduces the working file exactly.

Note on naming: `bake-apps.sh` reads `libselinux.orig.so`, but that file is **not** the stock library. Its md5
(`2be75f3e…`) is that of the base + ctx output (`libselinux-gb.so`), so the in-line `getprevcon` patch is the
third layer on top of the other two. The installer must hand it the derived `libselinux-gb.so` under that name.

## Why `libandroid_runtime` needed a new script

Same story: three stubs (`isVmapStack` -> true, `getGpuTotalUsageKb` -> 0, `getGpuPrivateMemoryKb` -> 0) were
applied by hand and never scripted. `patch-androidruntime-stubs.py` reproduces the working file exactly.

## Why `netd.o` needed a second script

`patch-netd-bpf.py` reproduces 8 of the working file's changed instructions. The other 11 (one rewritten program
tail at `0x1b688..0x1b6c8` and two register-compare tweaks) were hand edits. `patch-netd-bpf-extra.py` replays
them with the original 8 bytes checked at each site. Its comment is honest that the semantics were not re-derived.

For the symptom each patch removes, see [docs/FIX-CATALOG.md](../docs/FIX-CATALOG.md).

## Open items

- `hwcomposer.waydroid.so` patches run inside `try.sh` against the Lineage vendor image; the installer prepares
  `vendor.img` but the patch step itself has not been run from a fresh work folder yet.
- `bpfloader`: if the install should be byte-reproducible, strip section headers the same way; not needed to run.

## Closed

- The in-line `bake-apps.sh` patches (`libselinux` getprevcon, `libaudiohal@7.1`, `libinputflinger`) get their inputs from
  the installer: `libaudiohal-7.1.orig.so` and `libinputflinger.orig.so` are stock `system_a` files (sha256-pinned in
  `derive_patched.py`); `libselinux.orig.so` is a copy of the derived `libselinux-gb.so`.
- `input-bridge`: the release `bake-apps.sh` installs the rebuilt binary instead of byte-patching an old one.
- `vendor.waydroid.task@1.0-*` (the Waydroid task HAL): taken from the ATV Android 16 `system.img`
  (`/system/bin/hw/...-service`, `/system/etc/init/...rc`, `/system/system_ext/lib64/...so`); md5-identical to the
  files the working system uses.
- End-to-end check (2026-11-04): `system.img` built from the fresh work folder vs. the one in use: 6 of ~4,700 files
  differ, all explained (4 scripts edited after the old image was built, `bpfloader` section metadata,
  `input-bridge` = the intended 4-byte pointer-range fix).
