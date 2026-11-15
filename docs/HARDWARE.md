# Hardware and software requirements

## What it has been tested on

One machine, one phone. Everything in this project was developed there, so treat anything outside this list as
untested rather than unsupported.

| | Tested |
|---|---|
| PC | a Lenovo laptop, Intel Core i5-13420H (13th gen), **7.6 GB RAM**, NVMe SSD |
| GPUs | Intel integrated (`i915`) for the desktop; an NVIDIA RTX 3050 6 GB Laptop GPU (proprietary driver) used only by the optional [GPU mode](../gpu/README.md) |
| OS | Debian 13 "trixie", GNOME on **Wayland**, AppArmor (no SELinux on the host) |
| Kernel | a custom-built 7.2 kernel. The stock Debian 13 kernel has the options GBOS needs (as modules), but **has not been tried** |
| Waydroid | 1.6.3 |
| Phone | Google Pixel 7 |
| Image | Lenovo Googlebook 15 recovery build `17.CL3B.260622.271.R1`; every binary patch is gated on its exact contents |

## What GBOS needs from the PC

* **Linux with a Wayland session.** Waydroid draws into a Wayland window.
* **Waydroid installed and working** (it brings LXC with it) before you start. `try.sh` re-initialises it with the
  GBOS images.
* **x86-64, and a recent one.** The Googlebook image is x86-64 Android compiled for a recent CPU baseline, and there is no ARM
  host path. It uses these instruction sets **unconditionally in the first processes Android starts** (counted by
  disassembling the image's own binaries: `linker64` 24 GFNI / 38 VAES / 16 AVX-VNNI, `init` 27 / 38 / 14, `surfaceflinger`
  44 GFNI / 28 VNNI, `libcrypto` 81 GFNI / 1,215 VAES / 128 SHA-NI):

  | `/proc/cpuinfo` flag | Instruction set |
  |---|---|
  | `gfni` | Galois-field instructions |
  | `vaes` | 256-bit AES |
  | `vpclmulqdq` | 256-bit carry-less multiply |
  | `avx_vnni` | AVX-VNNI dot products |
  | `sha_ni` | SHA extensions |
  | `avx2` `bmi2` `fma` `aes` `pclmulqdq` `adx` | the older baseline |

  The machine this was developed on (Intel Core i5-13420H, 13th gen) has all of them. **An Intel Core i5-8200Y (8th gen, the
  Pixel Slate) lacks the first five**, and there Android's very first process dies with `SIGILL` after about 0.3 s: the
  container shows RUNNING and then STOPPED within a second, no window ever appears, and `logcat` stays empty.
  `installer/gbos-install.py check` tests all of these before you download anything. Intel 12th generation (Alder Lake) or newer
  is what has been run; other CPUs are untested. Do not assume AMD or older Intel works.

  *Running on an older CPU:* those instructions can be emulated by a loadable kernel module, `kernel/gbos-vexemu.py` (no kernel rebuild;
  see [kernel/README.md](../kernel/README.md)). It passes its QEMU tests and has booted GBOS to the desktop on one Pixel Slate (8th-gen Intel), but
  every emulated instruction costs a trap, so expect it to be slow (the first boot took ~10 minutes there). It also needs `kernel/gbos-secctx.py` on kernels without the AppArmor fix. `gbos-install.py check --ignore-cpu` downgrades the CPU check to a warning.
* **Kernel features** (`installer/gbos-install.py check` reads them from `/proc/config.gz` or `/boot/config-*`):
  binder and binderfs, `mac80211_hwsim` (virtual Wi-Fi), `uinput`, TUN, VETH, bridge, nftables, eBPF with
  cgroup BPF, PSI, userfaultfd, SysV IPC. Optional: `ipvlan` (LAN presence), `bt vhci` (virtual Bluetooth), dummy.
  Options built as modules must load (`binder_linux`, `mac80211_hwsim`, `uinput`, ...).
* **Legacy iptables tables.** Android's `netd` uses the *legacy* iptables API, not nftables. Distribution kernels have
  it; **a trimmed custom kernel may not**. Kernels since 6.12 hide the tables behind `NETFILTER_XTABLES_LEGACY`,
  `IP_NF_IPTABLES_LEGACY` and `IP6_NF_IPTABLES_LEGACY`, so a config can have the base `ip_tables` module and none of the
  `filter`/`nat`/`mangle`/`raw` tables. Without them Android boots but has no working network. `check` tests all eleven
  options; `try.sh` loads the modules and persists that in `/etc/modules-load.d/`.

### Building your own kernel for this

```sh
installer/gbos-install.py kernel-fragment --base /path/to/your/.config     > gbos.fragment   # only what your config lacks
# in the kernel tree, with the SAME toolchain you build with (LLVM=1 etc.):
scripts/kconfig/merge_config.sh -m /path/to/your/.config gbos.fragment && make LLVM=1 olddefconfig
```

`--base` makes it print only what is missing, so it never downgrades a built-in option to a module. Resolve the config
with the compiler you actually build with: resolving with a different one flips toolchain-dependent options (LTO mode,
compiler version) and is the usual way a merge goes wrong. Then rebuild, install and reboot, and run `check` again.

**If you cannot rebuild the kernel**, the missing legacy iptables tables can be added as modules to the kernel you already
run, when you still have the tree it was built in: see [`kernel/README.md`](../kernel/README.md).
* **adb** on the host ([tutorial](TUTORIAL-adb.md)); a **JDK** (`jdb` is used by the Phone Hub link keeper);
  `python3-venv`, `curl`, `xz-utils`, `erofs-utils`, `patchelf`, `e2fsprogs`, `unzip`, `make`, `gcc`.
* **Memory.** The developer's 7.6 GB is tight: Android's low-memory killer and the host's OOM killer both appear in
  the troubleshooting history. Close heavy host jobs while casting. More RAM is better; fewer is untested.
* **Disk.** About 25 GB free for downloads, extracted partitions and the work folder, plus Waydroid's own data.

## What the Phone Hub part needs

* An Android phone that supports Google's **Cross-device services**, signed in with the **same Google account** as GBOS
  (developed against a Pixel 7).
* Phone and PC on the **same network**, Bluetooth on both. The PC's Bluetooth adapter is shared with the container
  by the optional `gb-bt-bridge` service.
* The `lan` and `wifi` host services (see [host/README](../host/README.md)): Cross-device services want to see Wi-Fi
  and a LAN address.
* For touch in the cast window, **Wireless debugging** on the phone, once per phone boot ([adb tutorial](TUTORIAL-adb.md)).

A second machine, a Google Pixel Slate (i5-8200Y, 8th gen, 7.8 GB, AppArmor-only 7.2.8 kernel) reached the desktop with touch working,
using the add-on modules in [`kernel/`](../kernel/README.md); see [UNSUPPORTED-HARDWARE](UNSUPPORTED-HARDWARE.md) for what is and is not verified there.

## Screens that are not 1920x1080

The input bridge declares a 1920x1080 pointer and touch range. On another resolution, clicks land in the wrong place
until you set the real size (from `adb shell wm size`):

```sh
adb shell setprop persist.gbos.display 1280x800
adb shell 'rm -f /dev/input/event1[0-9]; setprop ctl.restart gb-inputbridge'
```

Use the effective size: the `Override size` line of `wm size` if there is one, otherwise `Physical size`. The value lives in Android's
data, so it has to be set again after a fresh `/data`. The `rm` guards against [fix 24](FIX-CATALOG.md): an older bridge restarted without removing its old device nodes left Android with no keyboard, mouse or
touch. The current source removes them itself, so there the `rm` is harmless.

`gbos-doctor.py` warns when the two disagree. Orientation changes, on-screen keyboards and suspend on tablets have not
been tried.

## Different hardware

* **AMD or NVIDIA-only PC:** the desktop path is hardware-independent in principle (Waydroid picks the GPU), but it
  has not been tried. The hybrid-GPU mode will tell you it does not apply.
* **Stock distribution kernel:** try it; run `check` first and report what breaks.
* **Other Googlebook builds:** the patch scripts will stop with a clear message on a build they do not recognise. Most
  find their target by byte pattern; a few are pinned to offsets, see [`recipe/PATCHES.md`](../recipe/PATCHES.md).
