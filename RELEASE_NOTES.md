# GBOS on Waydroid — v0.1.1-pre

Preview release. Run the Android 17 "Googlebook" system image inside Waydroid on an ordinary
Linux PC, and link it to an Android phone with Phone Hub / App Cast.

**Preview quality.** This release has been run end to end from a clean Waydroid on one machine,
up to the point of pairing a phone (details below). Expect to read the docs and do some
troubleshooting. Back up first: `try.sh` resets Waydroid.

**Prerequisite:** a working Waydroid install, binder included. GBOS builds on it and does not
install Waydroid for you.

## What's in this release

- **Source** (`gbos-0.1.1-pre-src.tar.gz`): the whole repository, which includes the installer,
  recipe, native helpers, Android app sources, host services, the optional hybrid-GPU mode, the
  optional kernel add-ons, and the docs.
- **Prebuilt Android binaries** (`input-bridge`, `vpd-stub`, `vulkan.hybrid64.so`), byte-identical
  to v0.1.0-pre. These save the installer from building them. `input-bridge` and `vpd-stub` are
  reproducible NDK r27c builds. The GPU shim is the exact binary tested on-device. All three
  match the committed [`prebuilt.sha256`](prebuilt.sha256), and the installer checks every
  download against it.

```sh
installer/gbos-install.py fetch-prebuilt \
  --url https://github.com/thewraith420/gbos-waydroid/releases/download/v0.1.1-pre --dest DIR
```

If you'd rather not use them, build from source. `prepare --ndk auto` fetches the NDK and
compares what it builds against the published checksums.

## New since v0.1.0-pre

The first clean end-to-end install turned up the following, and all of it is fixed:

- **`check` reports everything it can find.** It prints one install command for every missing
  host package (apt, dnf, pacman or zypper), and `--json` gives the same report in a form other
  programs can read, as groundwork for a graphical installer.
- **Wi-Fi on a fresh install.** The simulated access point never started, because hostapd could
  not read its own config.
- **adb on a fresh install.** The authorization prompt never appeared. `try.sh` now authorizes
  your host key (`recipe/gb-adb-authorize.sh`).
- **Video and media apps.** The codec list was empty, because Waydroid turns Codec2 off when
  gralloc is gbm. It now lists 96 codecs.
- **Host journal flood.** A blocked face-unlock lookup and duplicated SELinux service contexts
  wrote about a million lines a day to the host's kernel log. The image recipe now removes both.
- **Phone Hub desktop widget.** It shows the phone link status and has a Reconnect button. It is
  now built into every install and follows the system light/dark theme.
- **Backups of large data folders.** `gbos-backup.py` now measures the disk space actually used
  by sparse files, not their nominal size.
- **Stock distro kernels.** Fixes from a trial run on Debian's 6.12 kernel.
- **Doctor checks the codec setting.** `gbos-doctor.py` reports when Codec2 is switched off and
  prints the fix.

## Verified from a clean install

`check` → `prepare` → `try.sh` → `bake-apps.sh` → host services → main user → Google sign-in
(via the "Finish Googlebook setup" notification) → `gb-userfixes.sh`. At the end,
`gbos-doctor.py` reported 0 broken. This verified working: the desktop, Play Store, YouTube with
sound, Wi-Fi, adb, and the widget.

## Not finished

- **Pairing a phone and App Cast have not been re-tested from a clean install.** Both work on
  the developer's long-running install.
- **The optional hybrid-GPU mode** was not part of the clean run.
- **Only one machine tested:** a 13th-gen Intel laptop with an NVIDIA hybrid GPU.

## Before you install

- **Recent CPU required.** The image uses GFNI, VAES, VPCLMULQDQ, AVX-VNNI and SHA-NI in its
  first boot processes. A 13th-gen Intel laptop works. An 8th-gen Pixel Slate failed immediately
  (there is an optional emulation module, but it is slow and barely tested). `gbos-install.py
  check` tells you before you download anything.
- **Kernel.** Distro kernels have what's needed. A trimmed or custom kernel, or one with only
  AppArmor, may need a config rebuild or one of the optional loadable modules under `kernel/`.
  `check` and `addons` detect what's missing and build it. See
  [kernel/README.md](kernel/README.md).
- **`try.sh` resets Waydroid** (`waydroid init -f`), which deletes existing accounts and apps.
  `installer/gbos-backup.py` saves and restores everything it would remove.

## Legal

No Google, Lenovo, Lineage or Chromium binaries are included. The installer fetches each input
from its original publisher and patches your own copies. You are responsible for the license
terms of what you download. Code is MIT, except the GPL-2.0 kernel modules under `kernel/`.
