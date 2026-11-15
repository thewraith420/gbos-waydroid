# GBOS: Googlebook OS on Waydroid

Run the Android 17 "Googlebook" system image from a Lenovo Googlebook 15 recovery image inside
[Waydroid](https://waydro.id) on an ordinary Linux PC, and link it to your Android phone with Phone Hub.

**Unofficial and experimental.** This is a hobby bring-up, developed and tested on one laptop and one phone. It is not
affiliated with or endorsed by Google, Lenovo or the Waydroid project. "Googlebook", "Android", "Pixel" and
"Chromium" belong to their owners.

## Bring your own image

This repository contains **no Google, Lenovo, Lineage or Chromium binaries.** The installer downloads each input
from its original publisher, checks its sha256 against [`sources.lock`](sources.lock), and then patches **your own
copies** with small scripts that assert the exact bytes they expect and stop on any mismatch. You are responsible for
complying with the license terms of what you download. The code here is [MIT](LICENSE), except the kernel modules in
[`kernel/`](kernel/README.md) (`vexemu/`, `secctx/`) and the patch in `kernel/patches/`, which are GPL-2.0 because they link
against GPL-only kernel symbols; the CPU-emulation arithmetic is not in this repository at all (it is fetched at build time
from a pinned upstream patch and checked by sha256).

## What works

All of this was verified on the developer's machine (see [HARDWARE](docs/HARDWARE.md)). A second, older and unsupported
machine (a Pixel Slate with an 8th-gen CPU) has reached the desktop with working touch, with no kernel rebuild; see
[UNSUPPORTED-HARDWARE](docs/UNSUPPORTED-HARDWARE.md). Everything else in the table is verified on the first machine only.

| Area | State |
|---|---|
| Boot to the Googlebook desktop in a Wayland window; keyboard, mouse, touch | works |
| Google account sign-in, Play Store installs and updates | works (with the upstream WebView the installer fetches) |
| Sound out; real Bluetooth (the host adapter is shared; Quick Share works) | works |
| USB host support | present; actual device passthrough is untested |
| Hiding the host panel for true fullscreen (used to crash Android) | fix is in the recipe; no verification is recorded |
| Android sees Wi-Fi (virtual radio named after your real network) and sits on your LAN | works |
| Linux Terminal (Debian VM through Android's virtualization framework) | VM boots, networking works |
| Phone Hub: link to a Pixel, notification sync, Do Not Disturb sync, Quick Access to phone files | works |
| Phone Hub **app streaming** (App Cast): phone apps in a window, clicks and typing sent back, phone audio | works through our own relay and decoder; latency is noticeable and bounded by the phone-side encoder |
| First-run setup wizard | disabled on purpose (it needs a Google security-chip service that does not exist here) |

Why app streaming takes our own route: the official stream is refused because the phone verifies a hardware
attestation GBOS cannot provide. Instead a companion app on the phone sends the screen to a relay on your PC, which
feeds GBOS's own streaming window. See [ARCHITECTURE](docs/ARCHITECTURE.md).

## Not finished

* **Phone Hub first-run provisioning is ported but unproven from a clean install.** `host/phonehub/gb-userfixes.sh`
  applies the settings Phone Hub needs inside Play services' data. Its target state matches the working system and its
  SQL was tested on the real database schema, but it has not been run on a freshly initialised Waydroid.
* The full `try.sh` + `bake-apps.sh` + host-services + optional GPU sequence has been checked piece by piece against the
  working system, but **not run end to end on a clean machine**.
* The prebuilt Android binaries (`input-bridge`, `vpd-stub`, the optional GPU shim) are not uploaded yet. Their checksums
  are already committed ([`prebuilt.sha256`](prebuilt.sha256)); until a release exists, build them with the Android NDK,
  which the installer can fetch for you. The build is reproducible.

## Quick path

```sh
installer/gbos-install.py check   --downloads ~/gbos/dl --work ~/gbos/work
installer/gbos-install.py fetch   --downloads ~/gbos/dl --with-ndk          # about 10 GB, resumable, sha256-checked
installer/gbos-install.py prepare --downloads ~/gbos/dl --work ~/gbos/work --ndk auto --phone-hub
cd ~/gbos/work && sudo ./try.sh                                              # DESTRUCTIVE, read INSTALL first
```

Then the rest of [docs/INSTALL.md](docs/INSTALL.md): bake the overlay, install the host services, set up adb, pair a phone.

## Read before installing

* **A working Waydroid is a prerequisite.** GBOS replaces the Android *image* inside Waydroid; it does **not** install
  or patch Waydroid's binder layer. If Waydroid doesn't already run on your machine, get it working first — on a distro
  whose stock kernel ships no binderfs (e.g. Debian) that usually means the binder DKMS module a normal Waydroid setup
  adds. `gbos-install.py check` flags it if binder/binderfs isn't there.
* **It needs a recent CPU.** The image uses GFNI, VAES, VPCLMULQDQ, AVX-VNNI and SHA-NI in its first boot processes. It has been
  run on an Intel 13th-gen laptop; an Intel 8th-gen chip (a Pixel Slate) failed immediately. `gbos-install.py check` tells you before
  you download anything. See [HARDWARE](docs/HARDWARE.md).
* **`try.sh` resets Waydroid** (`waydroid init -f`): it deletes Waydroid's data, i.e. any accounts and apps already in
  it. It asks you to type `reset` first, and `installer/gbos-backup.py` can save and restore everything it would delete.
  Updates afterwards use `bake-apps.sh`, which never touches your data.
* The Android side is a **userdebug build with root `adb`**. Authentication is on, and the LAN-facing network
  interface drops the adb port, but treat the container as a development environment.
* The Phone Hub part **edits Play services' local databases** to switch on features Google has not rolled out to this
  device. It changes nothing on Google's servers, but it is not something Google supports.
* Scratch space: allow about **25 GB** free. Do not point the installer at a RAM-backed directory such as `/tmp` on
  systems where that is tmpfs; `check` warns about it.

## Layout

| Path | What it is |
|---|---|
| [`installer/`](installer) | `gbos-install.py` (check / fetch / prepare), `extract_recovery.py` (reads the recovery image), `derive_patched.py` (builds the nine patched libraries from your image), **`gbos-backup.py`** (back up / restore Waydroid and its data), **`gbos-doctor.py`** (read-only health check) |
| [`recipe/`](recipe) | the scripts that build and install the system: `try.sh`, `bake-apps.sh`, the patch scripts, the in-container services. [`PATCHES.md`](recipe/PATCHES.md) says what every binary patch is and where its input comes from |
| [`native/`](native) | C sources built into the container (input bridge, signin helper, stubs) |
| [`android/`](android) | sources of the small Android apps (build and signing are done by the installer) |
| [`host/`](host) | services on the PC: LAN, virtual Wi-Fi, Bluetooth bridge, and the Phone Hub / App Cast stack. [README](host/README.md) |
| [`gpu/`](gpu) | **optional** Intel + NVIDIA hybrid-GPU mode. Most machines should skip it. [README](gpu/README.md) |
| [`docs/`](docs) | [INSTALL](docs/INSTALL.md), [ARCHITECTURE](docs/ARCHITECTURE.md), [HARDWARE](docs/HARDWARE.md), [TROUBLESHOOTING](docs/TROUBLESHOOTING.md), [FIRST-TRIAL](docs/FIRST-TRIAL.md) (a checklist for trying it on a machine that already runs Waydroid), [FIX-CATALOG](docs/FIX-CATALOG.md) (why each fix exists), [adb tutorial](docs/TUTORIAL-adb.md), [RELEASING](docs/RELEASING.md) |
| [`tools/scrub.sh`](tools/scrub.sh) | run before any commit: fails if the tree contains keys, tokens, personal paths or third-party binaries |
