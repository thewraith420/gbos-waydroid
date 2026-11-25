# GBOS on Waydroid — v0.1.4-pre

Preview release. Run the Android 17 "Googlebook" system image inside Waydroid on an ordinary
Linux PC, and link it to an Android phone with Phone Hub / App Cast.

**Preview quality.** This release has been run end to end from a clean Waydroid on one machine,
up to the point of pairing a phone (details below). Expect to read the docs and do some
troubleshooting. Back up first: `try.sh` resets Waydroid.

**Prerequisite:** a working Waydroid install, binder included. GBOS builds on it and does not
install Waydroid for you.

## What's in this release

- **Source** (`gbos-0.1.4-pre-src.tar.gz`): the whole repository, which includes the installer,
  recipe, native helpers, Android app sources, host services, the optional hybrid-GPU mode, the
  optional kernel add-ons, and the docs.
- **Prebuilt Android binaries** (`input-bridge`, `vpd-stub`, `vulkan.hybrid64.so`). `input-bridge`
  is new in this release (it adds the virtual lid switch, below); the other two are byte-identical to every earlier
  release. These save the installer from building them. `input-bridge` and `vpd-stub` are
  reproducible NDK r27c builds. The GPU shim is the exact binary tested on-device. All three
  match the committed [`prebuilt.sha256`](prebuilt.sha256), and the installer checks every
  download against it.

```sh
installer/gbos-install.py fetch-prebuilt \
  --url https://github.com/thewraith420/gbos-waydroid/releases/download/v0.1.4-pre --dest DIR
```

If you'd rather not use them, build from source. `prepare --ndk auto` fetches the NDK and
compares what it builds against the published checksums.

## New since v0.1.3-pre

- **Phone Hub connects without a debugger.** Cross-Device Services only searches for the phone when Android thinks the
  device is open, and under Waydroid it always read as closed, so the link keeper had to attach `jdb` and force that
  check every time. `input-bridge` now adds a virtual lid switch (`waydroid_lid`) that reports open, so the check passes
  on its own. On the developer's machine the link has needed no forcing since. The `jdb` route is kept for older images, and a JDK is
  still needed to build the apps.
- **New Cross-Device Services versions are handled automatically.** `resolve_cds_names.py` reads the app's code and
  finds the renamed classes and methods the keeper and App Cast use, caching the result per version
  (`~/.cache/gbos/cds-names-<version>.json`). It matches every name found by hand for the last two versions, and knows
  1.0.1392 (2026-11) out of the box. No more waiting for a GBOS update after the app updates itself.
- **Reconnecting the phone is more reliable.** `phone_rearm.py` finds the phone's wireless debugging port by mDNS before
  scanning, scans the whole range when it has to, and says so plainly when the phone has stopped trusting this computer
  (`ERR adb-unpaired`: pair again) instead of retrying forever.
- **The host no longer treats Waydroid's virtual devices as its own lid and power buttons.** The udev rule is now
  `65-waydroid-input-ignore.rules` (was `99-`); the old one could not stop `systemd-logind` watching them. `try.sh`
  installs the new one and removes the old one, and `gbos-doctor.py` flags the old name.
- `gbos-doctor.py` checks Android's base device state, and its Cross-Device check is stricter. `TROUBLESHOOTING.md`
  and `FIX-CATALOG.md` cover the new route.

### Upgrading from v0.1.3-pre

You don't need to reinstall, and don't re-run `try.sh`: it resets Waydroid. Instead:

```sh
installer/gbos-install.py fetch-prebuilt --url https://github.com/thewraith420/gbos-waydroid/releases/download/v0.1.4-pre --dest DIR
installer/gbos-install.py prepare --downloads D --work WORK --prebuilt DIR   # same folders and options as your install;
                                                              # finished steps are skipped, the new files land in WORK
sudo install -m 644 recipe/65-waydroid-input-ignore.rules /etc/udev/rules.d/
sudo rm -f /etc/udev/rules.d/99-waydroid-input-ignore.rules
host/install-host.sh phonehub                                 # the new keeper and resolver
cd WORK && sudo ./bake-apps.sh                                # installs the new input-bridge and gb-firstboot.sh into the image
```

Then restart Waydroid. `gbos-doctor.py` should report the base device state as OPEN.

## Earlier releases

### v0.1.3-pre

- **The CPU-emulation add-on (`vexemu`) works again, and on distribution kernels.** Testing it on a stock Debian kernel found
  three bugs, each of which stopped it for anyone not on the developer's own kernel:
  - the upstream patch it downloads moved on 2026-11-12, so the download failed with 404. It is now pinned to a commit and
    still checked against the same sha256;
  - it included a header that only exists from kernel 6.17, so it did not compile on Debian 13 (6.12) or Ubuntu 24.04 (6.8);
  - on kernels before 6.15 the task's FPU state is in a different place, and the module's own safety check refused to load.
  It now passes its full emulation suite (`kernel/vexemu/test/test-qemu.sh`: 9002 instruction cases, 18 addressing cases,
  run-ahead on and off) under QEMU as a CPU without those instructions, on Debian's stock 6.12.115 kernel and on 7.3.5.
- **Kernel add-ons from the graphical installer.** When the check finds add-ons are needed, the check page builds and
  installs them. If your kernel's headers are missing, it offers to install them first: the package for the running kernel,
  plus the one that keeps them current with kernel updates (`linux-headers-amd64` on Debian, `kernel-devel` on Fedora,
  `linux-headers` on Arch). It says up front when Secure Boot, lockdown or enforced module signatures would refuse the
  unsigned modules, instead of failing halfway.
- **`gbos-install.py addons`** finds the build files itself (`/lib/modules/$(uname -r)/build`), so `--tree` is only needed for
  a self-built kernel, prints the headers package to install when they are missing, and has `--json` for front-ends.
  `check --json` reports the same.
- `kernel/README.md` has a Secure Boot section. `tools/test-addons-plan.sh` no longer depends on the test machine's own
  kernel modules (3 cases failed on machines that have legacy iptables), and covers Debian's layout.

### v0.1.2-pre


- **Phone Hub works again after Google's Cross-Device Services update.** The app (`com.google.ambient.streaming`)
  updated itself to 1.0.1387 on 2026-11-15, which renamed the obfuscated classes the link keeper and App Cast depend on.
  The link never came up (the keeper logged `Gate forced 0 time(s)`), and App Cast either never opened its window or
  crashed the app's garbage collector about 13 seconds into a cast. All names are updated; hooks are now pinned to their
  argument types so a future rename fails with a warning instead of a crash; the keeper warns when the app's version is
  not one the names are known for. TROUBLESHOOTING lists the known names and how to find new ones.
- **A graphical installer** (`installer/gbos-installer-gui.py`, GTK 4 / libadwaita): one page per step, runs exactly
  the documented commands and prints each one first; root steps ask for your password through polkit; the reset asks you
  to type "reset". The README is now a screenshot tutorial of it.
- **Phone setup in one step** (`host/phonehub/gb-phone-setup.sh`, and a page in the installer): over wireless debugging
  (or USB) it installs the GBOS Cast companion app, grants its permissions, allows its notification access, optionally
  turns on touch control, and reads every change back. New, not yet run against a phone.
- **Codecs:** with `debug.stagefright.ccodec=0` (Waydroid's default on a desktop GPU, and on installs restored from
  before v0.1.1) the codec list is empty, and casting from YouTube made Play services crash looking for an H.264 encoder,
  which also took the phone link down. `try.sh` already sets it to 4; `gbos-doctor.py` flags it.

### v0.1.1-pre

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
  the developer's long-running install (App Cast verified there after the Cross-Device Services fix).
- **The graphical installer's root steps** have not been clicked through on a clean install; its read-only pages are
  tested. The phone setup step has not been run against a phone. The kernel add-on buttons were checked in staged
  screenshots; the commands they run were tested from the command line.
- **The kernel add-ons on real old hardware:** the only such test machine is retired. The emulation is tested under QEMU
  (above); how fast GBOS runs on an old CPU is not known.
- **The optional hybrid-GPU mode** was not part of the clean run.
- **Only one machine tested:** a 13th-gen Intel laptop with an NVIDIA hybrid GPU.

## Before you install

- **Recent CPU required.** The image uses GFNI, VAES, VPCLMULQDQ, AVX-VNNI and SHA-NI in its
  first boot processes. A 13th-gen Intel laptop works. An 8th-gen Pixel Slate failed immediately
  (the optional `vexemu` emulation module makes it start; it is slow, and its emulation is tested under QEMU). `gbos-install.py
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
