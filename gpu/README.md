# Hybrid GPU mode (optional)

**You almost certainly do not need this. GBOS runs fine without it, and nothing in the normal install touches it.**

## What it is

Android inside Waydroid draws on the GPU Waydroid picks, normally your integrated GPU. This mode sends Android's
**Vulkan** work (games, benchmarks, 3D apps) to a second, faster GPU instead, through a host-side "Venus"
render server, while the desktop, video and everything that is not Vulkan stays on the first GPU. It is a fixed
split, not a per-app switch. It was measured at a Geekbench 7 GPU score of 56565 on the developer's machine.

## Who it is for

Exactly this combination, which is the only one it has been built and tested on:

| | Supported |
|---|---|
| GPU Waydroid renders on | **Intel** integrated (tested with the `i915` driver; `detect` accepts any Intel GPU) |
| Second GPU | **NVIDIA with the proprietary driver** (render node bound to `nvidia`, Vulkan ICD installed) |
| Waydroid | 1.6.x, initialised |

Not supported (the tool will say so and refuse):

* **AMD integrated GPU + NVIDIA.** The routing shim sends apps that must not use the NVIDIA GPU (Chrome, WebView,
  the system UI, Flutter apps, Play services) to Intel's Vulkan driver; with an AMD iGPU that fallback does not exist,
  so those apps' Vulkan would fail. Supporting it means making the shim's fallback driver configurable
  (`vulkan.radeon.so` is already in the vendor image). `--force` overrides the check if you want to try.
* **NVIDIA with `nouveau`**, AMD or Intel as the second GPU, a single GPU, or a machine where Waydroid already
  renders on the NVIDIA card: nothing to split, or no way to do it.

## Find out first (changes nothing)

```sh
gpu/gbos-gpu detect
```

It lists your GPUs, shows which one Waydroid renders on, and ends with `supported`, `not a tested configuration` or
`not applicable on this machine`. Only on `supported` is it worth continuing.

## Install

```sh
installer/gbos-install.py fetch       --downloads D --with-nvidia               # 2 more archives, sha256-pinned
installer/gbos-install.py gpu-prepare --downloads D --work W --ndk auto         # assembles W/gpu-stage
sudo gpu/gbos-gpu enable --stage-dir W/gpu-stage --user YOU --dry-run           # shows the exact diff, changes nothing
sudo gpu/gbos-gpu enable --stage-dir W/gpu-stage --user YOU
systemctl --user enable --now gbos-venus.service                                # as YOU
waydroid session stop && waydroid session start
```

`gpu-prepare` takes the Venus host/guest pieces from the pinned [waydroid-nvidia](https://github.com/Shiro836/waydroid-nvidia)
release (MIT), builds our Vulkan routing shim from `vulkan_hybrid.c` with the NDK, and makes the one-byte patch to
**your own** vendor image's `waydroid-init` (see below). Nothing from Google, Lineage or NVIDIA is redistributed.

## What it changes

Only this, and `disable` reverses all of it:

1. Installs files to `/usr/local/lib/gbos-gpu` (root-owned; they are mounted into the container as root, so they
   must not live in a directory ordinary users can write to).
2. Adds **one marked block** to Waydroid's `config_nodes`: the Venus socket directory, the NVIDIA render node, the
   shim as `vulkan.virtio.so`, the Venus driver as `vulkan.pastel.so`, and the patched `waydroid-init`.
3. Sets `ro.hardware.vulkan=virtio`, `mesa.vn.debug` and `mesa.vtest.socket.name` in `waydroid.prop` and
   `waydroid_base.prop`, recording the previous values.
4. Writes `~/.config/systemd/user/gbos-venus.service` (you enable it yourself).

The socket-directory mount is `optional`: if the Venus service is not running when Waydroid starts, Android still
boots and Vulkan runs on the Intel GPU.

```sh
sudo gpu/gbos-gpu disable [--purge]      # restores config_nodes and the three properties exactly
gpu/gbos-gpu status
```

**After `waydroid upgrade` or `waydroid init`** Waydroid rewrites those files and the mode silently turns off.
`gbos-gpu status` shows it; `sudo gbos-gpu apply` puts it back. Re-run `apply` after the normal `try.sh` too.

If you already have a hand-made setup of this kind, `enable` refuses and lists the lines; `--adopt` takes them over.

## The one binary patch

`waydroid-init` (inside the container) probes the host GPU at every boot and writes `ro.hardware.vulkan=intel`,
undoing our `virtio`. `patch-waydroid-init-vulkan.py` changes a single byte in the property-name string
(`ro.hardware.vulkan` to `ro.hardware.xulkan`) so that write lands on a property nobody reads. It finds the string by
pattern and refuses any file where it is not present exactly once.

## Known limits

* Only Intel + NVIDIA, see above.
* The Vulkan shim rebuilt from source with the NDK has the same exported symbols, dependencies and soname as the
  binary the mode was developed with, but it is not byte-identical (the original was linked with host `gcc`), and
  the rebuild has not been run on a device yet. The tested binary is a release asset (`vulkan.hybrid64.so`, checksum in
  [`prebuilt.sha256`](../prebuilt.sha256)); use it with `gpu-prepare --prebuilt-shim FILE`, which verifies it.
* `enable`, `status`, `apply` and `disable` were tested end to end against a fake filesystem built from real
  Waydroid files (including a byte-exact restore). The full path on a live machine, from `gpu-prepare` through a
  running Venus server and a Vulkan app, is the developer's setup and has not been repeated from a clean install.
* Hot-switching between GPUs, a desktop toggle and a passwordless-sudo helper existed in early experiments and are
  deliberately **not** included: the sudo rule would have given any process of the desktop user root.
* The Venus socket is created world-connectable (the server does that), and its directory is traversable by all
  users, so any local user can submit GPU work to the NVIDIA card while the service runs. Fine on a single-user PC;
  think twice on a shared machine.
