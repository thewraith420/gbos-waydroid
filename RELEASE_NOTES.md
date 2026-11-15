# GBOS on Waydroid — v0.1.0-pre

First preview release. Run the Android 17 "Googlebook" system image inside Waydroid on an
ordinary Linux PC, and link it to an Android phone with Phone Hub / App Cast.

**Preview quality.** Everything below was verified on the developer's machine; the full
install has **not** been run end to end on a clean machine yet. Expect to read the docs and
do some troubleshooting. Back up first — `try.sh` resets Waydroid.

## What's in this release

- **Source** (`gbos-0.1.0-pre-src.tar.gz`) — the whole repository: installer, recipe, native
  helpers, Android app sources, host services, optional hybrid-GPU mode, optional kernel
  add-ons, and docs.
- **Prebuilt Android binaries** (`input-bridge`, `vpd-stub`, `vulkan.hybrid64.so`) — so the
  installer doesn't have to build them. `input-bridge` and `vpd-stub` are reproducible NDK r27c
  builds; the GPU shim is the exact binary tested on-device. All three match the committed
  [`prebuilt.sha256`](prebuilt.sha256), and `SHA256SUMS` is attached. The installer verifies
  every download against these.

Fetch the binaries with:

```sh
installer/gbos-install.py fetch-prebuilt \
  --url https://github.com/thewraith420/gbos-waydroid/releases/download/v0.1.0-pre --dest DIR
```

Or skip them entirely and build from source — the installer can fetch the NDK and the builds
are reproducible (`prepare --ndk auto` compares its output against the published checksums).

## What works (developer's machine)

Boot to the Googlebook desktop in a Wayland window (keyboard/mouse/touch); Google sign-in and
Play Store; audio out and real Bluetooth; virtual Wi-Fi + LAN presence; the Linux Terminal VM;
Phone Hub (notification/DND sync, Quick Access to phone files); and Phone Hub **app streaming**
through our own relay + decoder (latency is noticeable, bounded by the phone's encoder).

## Not finished

- Full `try.sh` + `bake-apps.sh` + host-services + Phone-Hub provisioning has been verified
  piece by piece but **not run end to end on a clean machine**.
- Phone Hub first-run provisioning is ported but unproven from a fresh Waydroid init.

## Before you install

- **Recent CPU required** — the image uses GFNI/VAES/VPCLMULQDQ/AVX-VNNI/SHA-NI in its first
  boot processes. A 13th-gen Intel laptop works; an 8th-gen Pixel Slate failed immediately
  (there is an optional emulation module, but it's slow and barely tested). `gbos-install.py
  check` tells you before you download anything.
- **Kernel** — distro kernels have what's needed. A trimmed/custom or AppArmor-only kernel may
  need a config rebuild or one of the optional loadable modules under `kernel/`; `check` and
  `addons` handle detection and building. See [kernel/README.md](kernel/README.md).
- **`try.sh` resets Waydroid** (`waydroid init -f`) — it deletes existing accounts/apps.
  `installer/gbos-backup.py` saves and restores everything it would remove.

## Legal

No Google, Lenovo, Lineage or Chromium binaries are included. The installer fetches each input
from its original publisher and patches your own copies. You are responsible for the license
terms of what you download. Code is MIT, except the GPL-2.0 kernel modules under `kernel/`.
