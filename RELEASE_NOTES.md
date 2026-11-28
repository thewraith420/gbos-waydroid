# GBOS on Waydroid — v0.2.0-pre

Preview release. Run the Android 17 "Googlebook" system image inside Waydroid on an ordinary
Linux PC, integrate it into your Linux desktop with multi-window mode and the GNOME Shell Phone Hub
extension, and link it to your Android phone with Phone Hub / App Cast.

**Preview quality.** Core upgrades in this release were built and exercised on the developer's
installed laptop + Pixel setup, while a clean install was verified end to end through `gbos-doctor.py`
in earlier releases. Expect to read the docs and do some troubleshooting. Back up first: `try.sh`
resets Waydroid.

**Prerequisite:** a working Waydroid install, binder included. GBOS builds on it and does not
install Waydroid for you.

## What's in this release

- **Source** (`gbos-0.2.0-pre-src.tar.gz`): the whole repository, which includes the installer,
  recipe, native helpers, Android app sources, host services, GNOME Shell extension, the optional
  hybrid-GPU mode, the optional kernel add-ons, and the docs.
- **Prebuilt Android binaries** (`input-bridge`, `vpd-stub`, `libgb-sf-waydroid.so`,
  `libgb-hwc-waydroid.so`, `vulkan.hybrid64.so`). `libgb-sf-waydroid.so` and
  `libgb-hwc-waydroid.so` are new in this release for Waydroid multi-window mode; the other three
  are unchanged from `v0.1.4-pre`. The four core Android binaries are reproducible NDK r27c builds;
  the GPU shim is the exact binary tested on-device. All five match the committed
  [`prebuilt.sha256`](prebuilt.sha256), and the installer checks every download against it.

```sh
installer/gbos-install.py fetch-prebuilt \
  --url https://github.com/thewraith420/gbos-waydroid/releases/download/v0.2.0-pre --dest DIR
```

If you'd rather not use them, build from source. `prepare --ndk auto` fetches the NDK and
compares what it builds against the published checksums.

## New since v0.1.4-pre

- **Waydroid multi-window mode and host app drawer integration.** Open GBOS and App Cast windows
  directly on the Linux desktop alongside native apps instead of only inside the full-screen GBOS
  desktop. New native shims (`libgb-sf-waydroid.so`, `libgb-hwc-waydroid.so`), system service glue
  (`waydroid-platform.jar`), task HAL patching (`patch-waydroid-task.py`), and a toggle in the
  graphical installer support per-app windows and host desktop integration.
- **GNOME Shell Phone Hub extension (`host/gnome-extension/gbos-phonehub@thewraith420`).** Adds a
  top-bar phone status indicator (`gb-link-daemon.py`) with live color state (connected, live cast,
  amber **Cast Off** when GBOS Cast is disabled on the phone, disconnected), a searchable phone app
  grid with Recents, and standalone App Cast launches on the active workspace even when the full
  GBOS window lives on another workspace. Install with `host/install-host.sh gnome-extension`.
- **Native Android 17 Task Continuity ("Continue On") bridge.** `gb-continuity.jar` in the
  container and `gb_continuity.py` on the host bridge (`:8095`) watch the phone's foreground app and
  surface it in GBOS's native taskbar **Continue On** pill; clicking hands off directly into a live
  App Cast window, and closing the App Cast window closes/backgrounds the phone app to stay in sync.
- **No-red-pill ShellCast (`gb-touch.jar` `v7` + `GBOS Cast` `v1.4`).** Instead of holding a
  permanent user-app `MediaProjection` session (which triggered Android's red status-bar timer pill
  and hid notification contents whenever linked), the privileged `com.android.shell` daemon captures
  display `0` via `DisplayManager.createVirtualDisplay` at native portrait `720×1600 @ 12 Mbps`
  High Profile (with automatic H.264 SPS scaling in `feed_live.js`) and streams internal audio via
  `REMOTE_SUBMIX` only while an App Cast window is open on GBOS — switching sound automatically
  between the PC speakers and the phone. Both `GBOS Cast` and its Quick Settings tile now control
  ShellCast cleanly without silently falling back to `MediaProjection`, and long-pressing the Quick
  Settings tile opens the app (`QS_TILE_PREFERENCES`).
- **Smoother App Cast input, physical keyboard, scrolling, and cast-off gating.** Added full
  physical keyboard forwarding (`K`/`KC`), native mouse/trackpad `ACTION_SCROLL` (`S`), sub-pixel
  `TCP_NODELAY` batched live touch (`D`/`M`/`U`), live freeform App Cast window geometry tracking,
  and cast-coupled input gating so turning GBOS Cast off disconnects touch forwarding and ignores
  stale window input until casting resumes.

### Upgrading from v0.1.4-pre

You don't need to reinstall, and don't re-run `try.sh`: it resets Waydroid. Instead:

```sh
installer/gbos-install.py fetch-prebuilt --url https://github.com/thewraith420/gbos-waydroid/releases/download/v0.2.0-pre --dest DIR
installer/gbos-install.py prepare --downloads D --work WORK --prebuilt DIR --phone-hub
host/install-host.sh phonehub --work WORK
host/install-host.sh gnome-extension          # optional, for GNOME Shell
waydroid session stop && sudo waydroid container stop
cd WORK && sudo ./bake-apps.sh
```

Then start Waydroid (`waydroid session start`), run `host/phonehub/gb-phone-setup.sh` over wireless
debugging (or USB) to upgrade the phone to `GBOS Cast` `v1.4`, and tap **Re-arm touch forwarding**
once to refresh `gb-touch.jar` (`v7`). Log out and back in on GNOME Wayland after installing or
updating the GNOME extension.

## Earlier releases

### v0.1.4-pre

- **Phone Hub connects without a debugger.** `input-bridge` adds a virtual lid switch (`waydroid_lid`)
  that reports open so Cross-Device Services finds the phone without `jdb`.
- **Automatic Cross-Device Services symbol resolution.** `resolve_cds_names.py` discovers obfuscated
  class/method names per app version (`~/.cache/gbos/cds-names-<version>.json`).
- **Reliable phone reconnection & input udev rules.** `phone_rearm.py` probes mDNS before scanning
  and reports `ERR adb-unpaired`; `65-waydroid-input-ignore.rules` stops `systemd-logind` from
  reacting to Waydroid virtual buttons.

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
