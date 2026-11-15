# How it fits together

## The pieces

```
 PC (Linux, Wayland)                                                     Phone (Android)
┌────────────────────────────────────────────────────────────┐          ┌─────────────────────┐
│ Waydroid (LXC) ── container: GBOS Android 17               │          │ Cross-device        │
│   system  = Googlebook system_a + system_ext_a + product_a │          │ services (Google)   │
│   vendor  = Lineage 23.2 vendor image (patched copy)       │◄─ BLE ──►│                     │
│   init/libs = Android 16 donor (WayDroid-ATV)              │◄─ LAN ──►│ GBOS Cast app       │
│   + overlay (our scripts, apps, patched libs)              │  video   │  (screen → PC)      │
│                                                            │          │  gesture agent      │
│ host services: lan, virtual Wi-Fi, Bluetooth bridge,       │          └─────────────────────┘
│   link keeper, App Cast supervisor + relays, audio         │
└────────────────────────────────────────────────────────────┘
```

GBOS is the unmodified Googlebook userland running on a different kernel and without Google's hardware. Almost
everything in this project is the glue that makes that survive: a Linux kernel instead of ChromeOS's, a container instead of
a VM, a virtual Wi-Fi radio and a LAN interface so Google's networking features have something to talk to, and stand-ins
for the security hardware that does not exist.

## Building the system image

Inputs are listed and pinned in [`sources.lock`](../sources.lock); [`installer/gbos-install.py`](../installer/gbos-install.py)
fetches and extracts them.

1. **Googlebook partitions.** The recovery image is a raw GPT disk. Its `super` partition holds Android *dynamic
   partitions* described by LP metadata. [`extract_recovery.py`](../installer/extract_recovery.py) parses the GPT and the LP
   metadata (checking both checksums) and writes `system_a`, `system_ext_a` and `product_a`. Nothing is hard-coded.
2. **Patched libraries.** Nine files are taken from your `system_a` (some from APEX packages inside it) and patched by
   scripts that check the original bytes at every site; [`derive_patched.py`](../installer/derive_patched.py) pins the
   sha256 of each result. [`recipe/PATCHES.md`](../recipe/PATCHES.md) lists what each patch does and why:
   mostly turning calls that need a real device (wake-alarm timers, SELinux contexts, GPU memory maps, an eBPF loader) into
   harmless no-ops inside a container.
3. **Donor.** Android 17's `init` aborts in an LXC container, so the Android 16 `init`, `servicemanager`,
   `hwservicemanager` and `netbpfload` from the WayDroid-ATV image, with their library closure under a private path, replace
   them. `bionic` stays Googlebook's.
4. **`build-system-img.sh`** merges the three partitions into one system-as-root erofs image, applies the file list that
   `try.sh` assembles, disables the setup wizard and turns on `ro.debuggable`.
5. **Vendor.** Waydroid's vendor image comes from Lineage 23.2 (Android 16). `try.sh` edits a *copy*: 64-bit-only
   ABI properties, the audio HAL as passthrough, and two null-dereference fixes in the hardware composer.

## Persistence: where changes live

This decides whether something survives a rebuild or a reset.

| Layer | Host path | Survives | Written by |
|---|---|---|---|
| system image | `/etc/waydroid-extra/images/system.img` | until `try.sh` | `try.sh` |
| **lower overlay** | `/var/lib/waydroid/overlay` | reboots, `try.sh`, data resets | `bake-apps.sh` |
| **upper overlay** | `/var/lib/waydroid/overlay_rw` | normal reboots only | Android, at run time |
| **`/data`** | `~/.local/share/waydroid/data` | normal reboots | Android, accounts, apps |

A file in the upper overlay **shadows** the same file in the lower one. `bake-apps.sh` deletes stale upper copies of
the files it installs for exactly that reason; when a baked fix "does nothing", compare the file's checksum on the device
with the repository's.

## Inside the container

`zzz-googlebook-waydroid.rc` (installed under `/system_ext/etc/init/`) overrides init services so the image can boot, and
adds ours:

| Service | Purpose |
|---|---|
| `gb-firstboot` | provisioning at `boot_completed`: marks setup done, chooses the WebView, sets flags |
| `gb-inputbridge` | copies Waydroid's host input streams into real `uinput` devices that Android's input stack accepts |
| `gb-wifi`, `gb-fakeap` | a stub Wi-Fi HAL and an access point named like your host network, so Android reports "connected" |
| `gb-lan` | puts the container on your LAN through an ipvlan interface (`eth1`) with its own address |
| `vendor.bluetooth-1-1` (`gb-bthal`) | Bluetooth HAL that talks to the host adapter through the bridge, or a simulated one |
| `gb-audiofix`, `gb-storagemount`, `gb-oomguard`, `gb-wallsync` | clear the boot-time master mute; keep per-user storage mounted; keep `system_server` out of the OOM killer; mirror the wallpaper to the login screen |
| `gb-fridaserver` | keeps `frida-server` running, used by the App Cast stack |
| `gb-portrelay` | forwards a loopback port to the Terminal app's VM |
| `vpd-stub`, `unlock-signin` | a stand-in for a missing vital-product-data service; a root helper for the sign-in WebView |
| no-op replacements | the security chip, Trusty and similar hardware-only services |

SELinux is disabled in the container (the host runs AppArmor), and `adbd` is started as root on TCP 5555.

## Phone Hub

Google's Cross-device services app (`com.google.ambient.streaming`) links GBOS to the phone over Bluetooth for discovery and
upgrades to Wi-Fi on the LAN. It only wants to run on real hardware, so:

* **The link keeper** (`gb-hub-link`) starts the discovery service under a debugger just long enough to force one
  unidentified "is this a real device" gate to true, detaches, and then stays hands-off; it repairs a dropped link in the least
  disruptive way (restart Play services, never the app while a cast is live) and routes Play services' LAN traffic out
  of the LAN interface so the Wi-Fi upgrade works.
* **App Cast.** The official stream is refused because the phone checks a hardware attestation. Instead the **GBOS Cast app**
  on the phone records its screen as H.264 and pushes it to the PC (port 8098). `host_relay.py` hands it to the container;
  `feed_live.js`, injected into the Cross-device app with frida, feeds it into the app's own cast window through an
  Android NDK `AMediaCodec` decoder we created ourselves. Clicks in the window go the other way through `input_relay.py` (port
  8100) to a small agent on the phone; phone audio arrives on port 8101; the phone finds the PC through a UDP
  discovery responder (8096).
* **The supervisor** (`appcast_up.sh`) waits for Android, `frida-server` and a stable link, attaches exactly one session,
  and re-attaches if Android or the Cross-device app restarts. Ports and trust model: [host/README.md](../host/README.md).

The reason behind each patch and workaround, in the order a person debugging would meet them, is in the [fix catalog](FIX-CATALOG.md).

## Design rules

* **Bring your own image.** No third-party binary is stored here; inputs are pinned by sha256.
* **Patch by pattern, assert before writing.** A patch that does not find exactly the bytes it expects refuses to run.
* **Reversible.** `bake-apps.sh --remove`, `install-host.sh --remove`, `gbos-gpu disable` each undo their own changes;
  the optional GPU mode restores the exact previous values.
* **Optional means optional.** The GPU mode and the Bluetooth bridge are separate installs that nothing else depends on.
