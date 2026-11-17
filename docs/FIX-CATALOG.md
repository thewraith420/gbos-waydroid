# Fix catalog

Everything that did not work when Googlebook OS first ran in a Waydroid container, why, and what fixed it. It is a
reference for anyone debugging the same system, so it is organized by **symptom**. Each entry gives the cause, the fix, where
the fix lives in this repository, and how far it has been checked.

**Status words.** *Verified*: seen working on the developer's machine, including across a reboot unless noted.
*Written, not verified*: the fix exists in the recipe but nobody has recorded it working. *Workaround*: it avoids the
problem without removing the cause. Nothing here has been reproduced on a second machine. Android internals are
quoted from the Android 17 Googlebook build `17.CL3B.260622.271.R1`; class and method names in Google's apps are
obfuscated and change between versions.

| I see... | Entry |
|---|---|
| Black screen after the boot animation; `system_server` dies every few seconds | [2](#2-black-screen-system_server-crash-loop-profcollect), [3](#3-android-never-boots-after-waydroid-upgrade) |
| Google sign-in: "Something went wrong", Next does nothing, typed text invisible | [4](#4-google-sign-in-never-completes) |
| Play Store error 1047, Settings, Storage crashes | [5](#5-play-store-error-1047-and-the-storage-crash) |
| Task Manager / Diagnostics crash | [6](#6-task-manager-and-diagnostics-crash) |
| Linux Terminal: "Failed to start VM" | [7](#7-linux-terminal-failed-to-start-vm) |
| No sound, or silence after boot | [8](#8-no-sound) |
| Every install leaves a `dex2oat` tombstone; apps are slow to start | [9](#9-dex2oat-tombstones-on-every-install) |
| Fullscreen (hiding the host panel) kills Android | [10](#10-fullscreen-crashes-android) |
| Quick Share / Nearby find nothing; Bluetooth scans return nothing | [11](#11-bluetooth-simulated-controller-suspended-radio), [13](#13-android-has-no-wi-fi) |
| Android shows no Wi-Fi, or "Networks unavailable" after a restart | [13](#13-android-has-no-wi-fi), [14](#14-wi-fi-does-not-join-after-a-restart) |
| Phones, Chromecasts or printers on the LAN cannot find Android | [15](#15-the-android-side-is-invisible-to-the-lan) |
| Phone Hub: "Looking for your nearby devices", link drops, no app streaming | [16](#16-phone-hub-never-starts-discovery), [17](#17-link-will-not-reconnect-after-a-restart), [18](#18-link-stays-on-ble-and-drops), [19](#19-app-streaming-is-refused-by-the-phone) |
| Hardware services crash-loop or spam the log | [20](#20-dead-hardware-services) |
| Waydroid starts but no window ever appears; container RUNNING then STOPPED within a second | [21](#21-the-first-process-dies-with-sigill-cpu-too-old) |
| After `try.sh` the system is the old Android, or VINTF `manifest.version` errors appear | [22](#22-waydroid-init-ignores-the-images-you-installed) |
| Keyboard/mouse/touch dead after restarting the input bridge; `dumpsys input` shows only "Virtual" | [24](#24-after-restarting-gb-inputbridge-keyboardmousetouch-all-stop-working) |
| Bare wallpaper, no shelf; `pm list users` shows only `0:Owner`; Login screen is the home app | [25](#25-desktop-shows-only-a-wallpaper-no-shelf-no-launcher-owner-user-login-screen-stuck-as-home) |
| Google sign-in: email accepted, **Next does nothing**; `fix-ui.out` shows `reading 'Ea'` | [26](#26-sign-in-next-never-reaches-the-password-screen-on-a-slow-machine-typeerror--reading-ea) |
| Stuck at the boot animation / black screen; `odsign` aborts in a loop; kernel log: `failed to get security context` | [23](#23-boot-stops-at-the-boot-animation-odsign-aborts-failed-to-get-security-context) |
| Fresh install: Wi-Fi on but never connects; `gb-fakeap.log` repeats `hostapd started`; `adb` stays `unauthorized` | [27](#27-fresh-install-no-wi-fi-and-adb-unauthorized) |
| Apps cannot play video/audio (empty codec list, `MediaCodecList generated and serialized (56 bytes)`); host journal flooded with `SELinux: Multiple same specifications` / face `virtualhal` lines | [28](#28-fresh-install-no-codecs-for-apps-and-a-flood-in-the-host-journal) |
| Phone link never connects; the keeper logs `Gate forced 0 time(s)` every round (after the cross-device app updated) | [29](#29-phone-link-never-comes-up-after-the-cross-device-app-updated-itself) |

---

## Boot and platform

### 1. Why the container needs a donor `init`
*Symptom:* Android 17's stock `init` aborts in first stage inside an LXC container.
*Cause and fix:* the Android 16 `init`, `servicemanager`, `hwservicemanager` and `netbpfload` from the WayDroid-ATV
image, with their library closure under a private path (`/system/lib64/wdinit`, via RUNPATH) replace them; `bionic` stays
Googlebook's. `hwservicemanager` matters: Googlebook's is a dangling link, so without it HIDL looks unsupported and every
HIDL vendor HAL fails. ATV's `netbpfload` tolerates the container's read-only `/proc/sys` and honours
`ro.bpf.kver_override`; Googlebook's rejects non-LTS kernels.
*Where:* `recipe/prep-donor.sh`, `recipe/try.sh`, `recipe/build-system-img.sh`. *Status:* verified.

### 2. Black screen, `system_server` crash loop (profcollect)
*Symptom:* boot animation, then black; `system_server` dies every ~5 s; no network, no adb.
*Cause:* Google's flag sync set `DeviceConfig profcollect_native_boot/enabled = true`. Then
`ProfcollectForwardingService.onBootPhase(500)` calls `UsbManager.getCurrentFunctions()`, `UsbService` has no device
manager because `/sys/class/android_usb` does not exist in a container, and the resulting `IllegalStateException` kills
`system_server`. The flag arrives from Google at any time, so any later reboot can trigger it.
*Fix:* `device_config set_sync_disabled_for_tests persistent` (Google stops pushing flags) and
`device_config put profcollect_native_boot enabled false`, both at every boot. Side effect: Google apps stop receiving
server-side flag updates (`device_config set_sync_disabled_for_tests none` undoes it).
*Recovery if it already happened:* with the container stopped, the flag is in the data directory,
`system/users/0/settings_config.xml` (Android binary XML); the value `true` is length-prefixed and can be flipped to `false`.
*Where:* `recipe/gb-firstboot.sh`. *Status:* verified.

### 3. Android never boots after `waydroid upgrade`
*Symptom:* boot animation, black screen, `netd` aborting, `init` killing the zygotes.
*Cause:* `waydroid init` / `waydroid upgrade` regenerate the container config and properties, dropping four edits the
image needs: a dummy file bind-mounted over the read-only `/proc/sys/kernel/unprivileged_bpf_disabled` (the Android 17 `netbpfload`
treats the failed write as fatal and reboots), `cgroup:rw` (Android 17 creates its own cgroups), a writable tmpfs over
`/metadata`, and the `bpf` capability (`netd` needs CAP_BPF). `ro.debuggable=1` and `ro.build.type=userdebug` are also lost.
*Fix:* `restore-container-tweaks.sh` re-applies exactly those, keeping backups; `installer/gbos-doctor.py` detects it.
*Where:* `recipe/restore-container-tweaks.sh`, `recipe/try.sh`. *Status:* verified.

---

## Accounts, Play and storage

### 4. Google sign-in never completes
*Symptoms, in the order they appeared:* "Checking info... Something went wrong", then Next never advancing, then typed text
invisible, then Next doing nothing again. Several independent causes.
1. **Google's own WebView builds (149/155/156) never complete `fetch()`/XHR in this container.** The upstream Chromium
   158 `AndroidDesktop_x64` snapshot (package `com.android.webview`) does. The recipe shadows the factory
   `/product/app/WebViewGoogle/WebViewGoogle.apk` with it and selects it at every boot (Google's is first in the provider order).
2. **WebView command-line flags** (the build is `userdebug`, so WebView reads `/data/local/tmp/webview-command-line`):
   `--no-sandbox --enable-features=NetworkServiceInProcess --disable-features=WebViewSandboxedRenderer,BackgroundResourceFetch,ScriptStreaming,ConsumeCompileHints --blink-settings=scriptStreamingEnabled=false`.
   Never add `--single-process` (it makes some Chromium-based apps abort: `render_process_host_impl.cc InSameStoragePartition`),
   and never `--disable-gpu` (WebView `CHECK`s against it). Which of `--no-sandbox` / `NetworkServiceInProcess` is the necessary one is untested.
3. **The sign-in page needs help.** `unlock-signin` (a root daemon) injects `fix-ui.js` over the WebView DevTools
   socket: dismisses the "Checking info" overlay (`window.mm.showView()`), enables the native Next button, handles Enter, and works around
   a focused `<input>` that WebView 158 does not repaint here (`will-change: transform` gives each input its own compositing layer). It only injects scripts that
   are missing from the page's resource-timing entries after 8 s; earlier versions re-ran every script and broke the page's module system.
   Rule learned: never open a DevTools WebSocket while the page title is empty.
*Where:* `recipe/bake-apps.sh`, `recipe/gb-firstboot.sh`, `recipe/fix-ui.js`, `native/unlock-signin.c`. *Status:* verified. On a slow machine see also [26](#26-sign-in-next-never-reaches-the-password-screen-on-a-slow-machine-typeerror--reading-ea) (the grace period mattered).

### 5. Play Store error 1047 and the storage crash
*Symptoms:* every Play install failed with `InstallerException 1047` (reads like "not enough space") with hundreds of GB free;
Settings, Storage crashed.
*Two causes:*
1. **Storage totals.** vold had no fstab (`ro.hardware=unknown`), so `getTotalBytes()` threw; pointed at a small loop
   device it reported 1 GB. *Fix:* `ro.boot.fstab_suffix=unknown`, a `/vendor/etc/fstab.unknown` mapping `/data` to `/dev/block/loop63`, and an init
   service that attaches a sparse image to it before vold starts, so vold reports a realistic ~1 TB.
2. **The real cause of 1047.** Play requires `Environment.getExternalStorageState()` to be `"mounted"`; otherwise it computes free space as -1.
   Android reports "unmounted, system locked" until `StorageManagerService` unlocks users, and that thread was blocked forever inside
   `MediaCodecList -> IOmxStore::getService`: the vendor VINTF manifest *declares* the OMX codec-store HAL, whose binary is 32-bit only and cannot
   run on this 64-bit image, so the HIDL lookup waits indefinitely. *Fix:* the manifest without that one `<hal>` block.
*Where:* `recipe/fix-storage-persist.sh`, `recipe/vintf-manifest.no-omx.xml`, `recipe/gb-storagemount.sh`. *Status:* verified.
*Dead ends:* FUSE `ENOTCONN` and project-quota/`allocateBytes` theories were tested and rejected.

### 6. Task Manager and Diagnostics crash
*Cause:* both bind `vendor.google.desktop.serviceability.VendorServiceabilityService`, a vendor service for hardware (EC/VPD) that does not exist here.
*Fix:* a stub APK of the same package that implements all 18 AIDL methods from `/proc` and `/sys` (battery from the host's power supply, storage type, ...), so the
apps show real data. It is built without the Android SDK (`javac` + R8 `d8` + `aapt2` + `apksig`). A separate native stub, `vpd-stub`, registers the missing
`IVpdExecutor` service so `DesktopRepairModeService.onBootPhase()` stops blocking `system_server`'s main thread.
*Where:* `android/svc_stub_apk`, `native/vpd-stub.c`. *Status:* verified.

### 7. Linux Terminal: "Failed to start VM"
The app hides the real error. Four stacked causes, found one at a time:

| # | Cause | Fix |
|---|---|---|
| 1 | the vendor image does not declare `android.software.virtualization_framework`, so `VirtualMachineManager` is not registered and the app dereferences null | `android.software.virtualization_framework.xml` in `/vendor/etc/permissions/` |
| 2 | `virtualizationservice` runs as `system` with no capabilities and no SELinux to grant any; `virtmgr` panics on the memlock rlimit | redefine the service as `user root` |
| 3 | the host module `vhost_vsock` is not loaded, so there is no `/dev/vhost-vsock` | `modprobe vhost_vsock` and persist it in `/etc/modules-load.d/` |
| 4 | `createVm` fails with *getprevcon failed: Invalid argument*: `getprevcon()` reads `/proc/thread-self/attr/prev`, which AppArmor always answers with `EINVAL` | patch one instruction of a copy of `libselinux.so` so `getprevcon` reads the existing string `current` (the AppArmor label) instead of `prev` |

*How the real error was found:* run the same config through the CLI (`/apex/com.android.virt/bin/vm run --name dbg --console ... config.json`), which prints the
service-specific error text. *Where:* `recipe/bake-apps.sh` (patch 4, in-line; the base patches are `recipe/patch-libselinux-*.py`),
`recipe/zzz-googlebook-waydroid.rc`. *Status:* VM boots and guest networking works (verified); graphical guest apps, the shared folder and VM restart are untested.
*Gotcha:* never `rm` a file from inside the container that also exists in the lower overlay layer: the whiteout hides both.

---

## Hardware

### 8. No sound
*Symptom:* nothing reaches the host's PipeWire although Android believes audio works; a 3 s tone is consumed in 0.7 s.
*Cause, part 1:* the audio HAL runs **in-process** in `audioserver` (the 32-bit standalone service cannot run here, so the manifest declares it passthrough). Android 17's
`libaudiohal` asks a HAL 7.x stream for its process id through `IBase::getDebugInfo()` after `prepareForWriting()` and returns `NAME_NOT_FOUND` when the answer
is `NO_PID (-1)`, which is always the answer for an in-process object. The failure is silent; every later write then fails with `prepareForWriting twice (-61)`.
*Fix 1:* NOP the five bytes of that compare-and-jump in `libaudiohal@7.1.so` (`83 f8 ff 74 07`). The pid is only used for an optional priority request that is off by default.
*Cause, part 2:* `libasound` has `/vendor/lib/hw/` (32-bit) compiled in as its plugin directory. *Fix 2:* `ALSA_PLUGIN_DIR=/vendor/lib64/hw` for `audioserver`.
*Cause, part 3 (the one that fooled the first verification):* AudioFlinger's output threads start with **master mute on** at boot, while `AudioService` believes it is off, so
`setMasterMute(false)` is a no-op. Cycling **true then false** makes AudioFlinger rewrite every thread. A one-shot at boot completion ran too early; the `gb-audiofix` service polls
`dumpsys media.audio_flinger` every 5 s and cycles whenever it still reads `Master mute: on`.
*Lesson:* a PipeWire stream only proves the connection. Verify by ear or by a non-zero signal.
*Where:* `recipe/bake-apps.sh` (patch), `recipe/gb-audiofix.sh`, `android/audioctl_app`, `recipe/zzz-googlebook-waydroid.rc`. *Status:* verified, including across a reboot. The microphone path was
verified with a level-only probe. Unexplained log lines left alone: `invalid volume index range in the curve`, `no device found for strategy 8`.

### 9. `dex2oat` tombstones on every install
*Cause:* the system is 64-bit only but `dalvik.vm.dex2oat64.enabled` is unset, so ART picks the 32-bit `dex2oat32`, which cannot run. Apps stay at `status=verify` (JIT only).
*Fix:* `setprop dalvik.vm.dex2oat64.enabled true` at `early-init`. An ordinary property, so no reboot is needed. Already-installed apps stay `verify` until compiled
(`cmd package compile -m speed-profile -a`). *Where:* `recipe/zzz-googlebook-waydroid.rc`. *Status:* verified.

### 10. Fullscreen crashes Android
*Symptom:* hiding the host's top panel so the window goes fullscreen kills the Android session (`system_server` SIGABRT).
*Cause:* Waydroid's `input-bridge` presents the host pointer as a pen that is held "in range" forever (`BTN_TOOL_PEN` is set once and never released), so Android keeps a permanent hover. When the
window resizes, the touch mapper is reconfigured and forgets the hover without sending `HOVER_EXIT`; the next `HOVER_ENTER` trips `LOG(FATAL) Expected ACTION_HOVER_MOVE`
(`InputDispatcher.cpp:741`).
*Fix:* the check is only a sanity assertion and the code after it already synthesizes the `HOVER_EXIT`s, so one `LogMessage` severity argument in a copy of `libinputflinger.so` is changed from FATAL
to ERROR (`mov ecx,r12d` -> `push 4; pop rcx`). Separately, `input-bridge` hard-coded a 1045-pixel Y range, so with the panel hidden the pointer vanished in the bottom strip; the
source now uses 1080.
*Where:* `recipe/bake-apps.sh`, `native/input-bridge.c`. *Status:* written, not verified (no recorded confirmation after the bake).

### 11. Bluetooth: simulated controller, suspended radio
*Symptoms:* Quick Share / Fast Pair find nothing; scans return nothing.
*Causes and fixes, in order of discovery:*
1. **The container cannot open the host's Bluetooth adapter** (`AF_BLUETOOTH` sockets only work in the initial network namespace), so Android ran AOSP's simulated controller. A host helper
   (`gb-bt-bridge`) takes the adapter exclusively through the kernel HCI *user channel*, and a Java HAL in the container forwards raw H4 packets to it over TCP on the Waydroid bridge. While Android
   Bluetooth is on, the host's Bluetooth is unavailable; turning it off hands the adapter back to BlueZ.
2. **`android.hardware.bluetooth_le` was not declared**, so the Nearby/Companion components did not initialise. `android.hardware.bluetooth_le.xml`.
3. **The stack was permanently "suspended" because the container's lid is "closed"** (no lid sensor, committed device state `CLOSED`), so `AdapterSuspend` dropped ACLs, disabled scan mode and paused advertising.
   *Fix:* `bluetooth.power.suspend.{disconnect_acl,pause_advertisement,scan_mode_none,stop_le_scan}.enabled=false` at `early-init`.
4. **The HAL launcher ran before the container had a network** and fell back to the simulation: it now retries its probe. The host helper also learned to find the adapter by itself (it
   moves between `hci0` and `hci1` when the dongle re-enumerates) and to take it over in 0.03 s instead of 33 s. A slow takeover makes Android's HCI client give up after ~3 s and crash-loop
   to "Reached maximum retry to restart Bluetooth".
*Where:* `host/system/gb-bt-bridge.py`, `android/bthal_app`, `recipe/gb-bthal.sh`, `recipe/zzz-googlebook-waydroid.rc`. *Status:* verified (a real adapter works, Quick Share confirmed). A cold boot can still lose the race
described in 4; restarting `vendor.bluetooth-1-1` after boot picks the real adapter.

### 12. USB host
*Cause:* the container sees the host's USB bus, but `android.hardware.usb.host` is not declared, so `UsbService` never creates a host manager. *Fix:* `android.hardware.usb.host.xml`. A missing USB HAL cannot crash
`system_server` this way (unlike the gadget-mode path in entry 2). *Status:* host support verified; an app opening a device and passthrough into the Terminal VM are untested. Devices the host is using (camera, Bluetooth) are
claimed by host drivers.

---

## Networking

### 13. Android has no Wi-Fi
*Cause:* the image has no `android.hardware.wifi` HAL and no working `wificond`, so Android's Wi-Fi service never reaches "enabled" (`WifiHal: mWifiHal is null`) and Quick Share refuses to send.
*Fix:* a virtual radio (`mac80211_hwsim`) moved into the container's network namespace as `wlan0`, plus a stub Wi-Fi HAL (one Java app: `IWifi`, one chip in station mode, one interface) declared in the VINTF
manifest. Android 17 does not use `wificond` for this (it uses an in-process `Nl80211Native`), so the stubbed `wificond` is registered but never called.
*Gotchas that crashed `system_server` on the way:* the stub HAL must never return a null parcelable (`getApfPacketFilterCapabilities` returned `null` and every connect attempt restarted all of Android's userspace);
the debug method that found it was capturing `logcat` to a file and reading `dumpsys dropbox --print system_server_crash`, which survives the restart.
*Where:* `host/system/wifi-hwsim.sh`, `android/fakewifi_app`, `recipe/gb-wifi.sh`. *Status:* verified.

### 14. Wi-Fi does not join after a restart
*Cause:* a second virtual radio acts as an access point named like the host's network, so Android reports "connected to <your network>" (the Phone Hub setup insists on "the same Wi-Fi network"). The container's
LAN interface (`eth1`) is *labelled* as Wi-Fi so apps see a Wi-Fi network; when it comes up before `wlan0` has joined, it already satisfies Android's Wi-Fi request, the Wi-Fi stack gets no request and never joins.
*Fix:* `gb-fakeap.sh` takes `eth1` down, connects `wlan0`, waits for "connected", brings `eth1` back up. By hand: `ip link set eth1 down`, `cmd wifi connect-network "<ssid>" open`, wait, `ip link set eth1 up`.
*Related gotchas:* hairpin NAT through the virtual radio does not work (`mac80211_hwsim` copies skbs *with* their conntrack), so traffic is routed by firewall-mark policy routing out the real interfaces. `hostapd` drops to the `wifi` user;
a root-owned `ctrl_interface` directory makes it exit silently and the supervisor restarts it every 5 s.
*Where:* `recipe/fakeap/gb-fakeap.sh`, `recipe/gb-wifi.sh`. *Status:* verified.

### 15. The Android side is invisible to the LAN
*Goal:* make the container a peer on the home network so mDNS, Cast, Quick Share's Wi-Fi mediums and direct phone connections work.
*Design:* an `ipvlan` (mode l2) interface on the host's uplink, moved into the container as `eth1`. ipvlan reuses the parent's MAC, which is why it works on a Wi-Fi station (macvlan does not). No DHCP: sharing the MAC would
make the router hand back the host's lease, so the host daemon picks a free static address (ARP probe from the top of the subnet down) and the container applies it through `IEthernetManager`.
*Gotchas:* Android tears down a network that no request needs, and the validated NAT interface wins every request, so the address was cleared 5 s after being set. *Fix:* a root `app_process` holds one explicit
`requestNetwork` for `eth1` (`ethcfg hold`). The container's `adbd` listens on all interfaces, so `gb-lan.sh` drops tcp/5555 arriving on `eth1`. The host cannot reach the container's LAN address (an ipvlan parent
limitation); it uses the NAT address instead.
*Where:* `host/system/gb-lan.py`, `recipe/gb-lan.sh`, `android/ethcfg_app`. *Status:* verified.
*Also needed for Nearby to see the Wi-Fi name:* Android hides the SSID from every app when location is off for user 0 (`no_share_location` restriction, `location_mode 0`). `host/phonehub/gb-userfixes.sh` clears it.

---

## Phone Hub

### 16. Phone Hub never starts discovery
*Symptom:* the Cross-device popup stays on the QR code; `ForegroundDiscoveryService` is stopped by the app itself within ~50 ms.
*Causes, peeled one at a time:*
1. A server-side rollout flag (`45815550`, default false) gates discovery. The `FLAG_OVERRIDE` broadcast is refused by Play services' security gate (allowlist is `com.google.android.play.games`; the build must be a test
   build), so **the override rows are written directly into `phenotype.db`**.
2. Even then the flag stayed false in the live process. The app reads flags from a snapshot kept by **Play services in its own shared storage, tried before the app's local snapshot**, and that one predates the
   override. Editing the app's own snapshot changes nothing. *Fix:* move the shadowing shared snapshot aside, `am force-stop`, and refresh the app's local snapshot with a `phenotype.UPDATE` broadcast
   carrying `PACKAGE_NAME`.
3. One of the seven inputs of the discovery gate stays false on this device and was never identified (not lid state, doze, keyguard, network or feature 7). *Workaround:* start the service under `jdb` with a
   breakpoint at the gate and set it true each time it is evaluated, **for the ~15 s handshake only, then detach**. A debugger left attached freezes every thread on each evaluation, starves Nearby sockets, and
   produces GATT write timeouts and UKEY2 aborts: the link drops and reconnects in a loop.
*Where:* `host/phonehub/gb-userfixes.sh` (steps 3), `host/phonehub/gb-hub-link.sh`. *Status:* verified. The gate is still unidentified.

### 17. Link will not reconnect after a restart
*Symptom:* "Looking for your nearby devices" forever, although radios, mDNS and BLE advertisements all work.
*Cause:* Play services holds a stale view of the account's cross-device state and answers `DtdiService getApiAvailability: false` to every poll, so no presence exchange is ever started.
*Fix, order matters:* toggle *Cross-device services* **on the phone first**, then restart the cross-device app and Play services in the container. A Play services restart *before* the account-side change leaves availability
false. After a Play services restart wait about a minute before kicking the gate: kicking at 15 s lands in the same trap.
*Automated:* the keeper refreshes Play services when a gate kick yields no link (at most every 10 minutes) and backs off 4, 8, 16, 30 minutes after failed kicks. A stale cast lock file makes the keeper stand down silently,
which looks identical from outside. *Where:* `host/phonehub/gb-hub-link.sh`. *Status:* verified.

### 18. Link stays on BLE and drops
*Symptom:* connects but drops every few minutes; bandwidth stays LOW; the phone logs a failed read of the `ConnectionRequestFrame` from sockets that come from the *laptop*, not the container.
*Cause:* after a Play services update the container pins Play services (and Messages) to the NAT interface with a per-app network preference (`ip rule ... fwmark ... lookup eth0`), so Nearby's Wi-Fi LAN upgrade
leaves through the host's NAT and the phone hangs up.
*Fix:* `ip rule add to <LAN subnet> uidrange <gms uid> lookup eth1` plus an exception for the host's own address (the ipvlan interface cannot reach its parent). Re-applied every loop (the rules live in the container's
kernel and vanish on restart) **and before every connect the keeper triggers**; applying it after a reconnect left that connection BLE-only.
*Where:* `host/phonehub/gb-hub-link.sh` (`ensure_gms_lan_route`). *Status:* verified.

### 19. App streaming is refused by the phone
*Symptom:* the link and notifications work, but the App Cast popup shows only "Cast your phone's apps / Get started".
*Cause:* the **phone** logs `Attestation verification failed` for GBOS and never advertises `AppCastService`. The phone verifies GBOS's key-attestation chain against Google's trust anchors; a software KeyMint with no verified-boot state
cannot produce one. The decision is made on the phone, and a stock phone's flags cannot be overridden (no root, the override receiver is allowlisted), so no GBOS-side change helps.
*Workaround, the route this project takes:* do not use the official stream. A companion app on the phone records its screen as H.264 and pushes it to the PC; a relay hands it to the container, where a frida script injected into
the cross-device app feeds it to the app's own cast window through an NDK `AMediaCodec` decoder created from native code (the app's own decoder path loses its surface and freezes). Input goes back to a small agent on the phone.
*Where:* `host/phonehub/` (all of it), `android/gbcast_app`, `android/touch_injector`. *Status:* works; latency is noticeable and bounded by the phone-side encoder.

---

## Hardware-less services

### 20. Dead hardware services
Several vendor and system services exist for hardware that is not there and either crash-loop or poll:
* **32-bit vendor HALs** (`android.hardware.cas@1.2-service`, `...media.omx@1.0-service`, `...camera.provider@2.7-external-service`): the system is 64-bit only, so they cannot exec; replaced by disabled stubs.
* **`udsattestation`** polled the security chip every second; **`gscd`, `pinweaver_storaged`, the Trusty VM launchers**: replaced by no-ops. The first-run setup wizard is disabled because it blocks forever on the
  security-chip HAL `IGsc`; a logging stand-in showed its only client is `udsattestation` and that `IGsc.transmit` is a chip-command passthrough, so a real wizard flow is not feasible.
* **Wake alarms** (`CLOCK_REALTIME_ALARM`/`CLOCK_BOOTTIME_ALARM` timers) need CAP_WAKE_ALARM: a patched `libalarm_jni` makes AlarmManager's native driver open as plain realtime/boottime timers (otherwise it falls back to a buggy
  Java handler that throws an NPE), and a patched `libbluetooth_jni` stops the Bluetooth app crash-looping into a RescueParty reboot.
* **Memory-pressure kills:** `gb-oomguard` keeps `system_server` and `surfaceflinger` at OOM score -1000; `host/phonehub/gb-userfixes.sh` tunes `lmkd`.
* **Hardware composer:** two null dereferences in Waydroid's `hwcomposer.waydroid.so` (`get_buffer_metadata_cros`, `create_buffer_cros`) took the container down ("crash to boot logo"); both are byte-patched with a null check.
* **GPU memory maps:** `getGpuTotalUsageKb`/`getGpuPrivateMemoryKb`, `ReadPerProcessGpuMem` and `isVmapStack` all abort `system_server` (minutes after boot, or ~14 s after the first draw) when a BPF map or `/proc/config.gz` is missing; each is stubbed.
*Where:* `recipe/PATCHES.md` lists every binary patch, its input and its status; `recipe/hal-stubs/`, `recipe/zzz-googlebook-waydroid.rc`. *Status:* verified.

---

## Found on a second machine

### 21. The first process dies with SIGILL: CPU too old
*Symptom:* `try.sh` finishes, Waydroid reports the container RUNNING, then STOPPED within about 0.3 s. No window, empty `logcat`, no
tombstones (nothing is up long enough to write them).
*Cause:* the Googlebook userland is compiled for a recent CPU baseline and uses GFNI, VAES, VPCLMULQDQ, AVX-VNNI and SHA-NI in its first
processes. Running the dynamic linker under `gdb` on the failing machine stops at `vgf2p8affineqb` (a GFNI instruction) inside
`ElfReader::Load`. The image's binaries contain these in bulk (`linker64`, `init`, `surfaceflinger`, `libcrypto`, `libhwui`).
The development laptop (13th-gen Intel) has all of them; an 8th-gen Intel (Amber Lake) has none of the five.
*Fix:* a CPU that has them, or emulating the instructions with the `gbos_vexemu` module (see [kernel/README](../kernel/README.md)).
`gbos-install.py check` fails fast on this. *Where:* `installer/gbos-install.py` (`CPU_NEED`), `kernel/gbos-vexemu.py`. *Status:* detection verified;
the module passes its QEMU tests and booted GBOS to the desktop on the second machine (a Pixel Slate), slowly.

### 22. `waydroid init` ignores the images you installed
*Symptom:* after `try.sh`, Android 13 or an A17/A13 hybrid boots: VINTF errors such as `Unrecognized manifest.version 9.0`, repeating HAL aborts.
*Cause:* `try.sh` copied the new images to `/etc/waydroid-extra/images/` and ran `waydroid init -f` **without `-i`**. Without it Waydroid
takes `/var/lib/waydroid/images`, which on a machine that already ran Waydroid holds the *old* images, and `bake-apps.sh` then laid Android 17
overlays over an Android 13 base. The development laptop's `waydroid.cfg` already pointed at `waydroid-extra`, which hid the bug.
Two related bugs hid the same way: `/etc/waydroid-extra/images` did not exist on a fresh machine, and `lxc_tweaks` (the cgroup, bpf-sysctl,
`/metadata` and capability settings) was defined but never called.
*Fix:* `waydroid init -f -i /etc/waydroid-extra/images`, `mkdir -p`, and the call. *Where:* `recipe/try.sh`. *Status:* verified
(the slate then initialised from the right images).

### 23. Boot stops at the boot animation: odsign aborts, `failed to get security context`
*Symptom:* Android reaches `zygote`-less boot; `waydroid logcat` shows `odsign` receiving SIGABRT over and over (`Binder transaction failure ... BR_FAILED_REPLY, error: -22`)
and the host kernel log says `binder: ... failed to get security context`. Also `vold` aborts once, early.
*Cause:* keystore2 sets `FLAT_BINDER_FLAG_TXN_SECURITY_CTX`, so binder must turn the caller's secid into a context string. With AppArmor as the only LSM
nothing implements `cred_getsecid`, so the secid is always 0, which AppArmor never allocates, and `apparmor_secid_to_secctx(0)` returns -EINVAL. The development
laptop's kernel carries a patch for this; the second machine's kernel was built before it existed.
*Fix:* the kernel patch, or `kernel/gbos-secctx.py` (no rebuild). *Where:* `kernel/gbos-secctx.py`, `kernel/secctx/`, `kernel/patches/`. *Status:* verified on the second machine
(`zygote`, `system_server` and `surfaceflinger` start after loading it).

### 24. After restarting `gb-inputbridge`, keyboard/mouse/touch all stop working
*Symptom:* `setprop ctl.restart gb-inputbridge` (as the docs suggest after changing `persist.gbos.display`) leaves Android with no input; `dumpsys input` lists only the
built-in "Virtual" device, although the bridge logs `created uinput device`.
*Cause:* the old instance's `/dev/input/event16..18` nodes stay behind; the new uinput devices get the same numbers, `mknod` fails with EEXIST (unchecked), so no inotify
`IN_CREATE` reaches EventHub and it never registers the devices. A container restart hides it because `/dev` is recreated.
*Fix:* `unlink()` the node before `mknod()`. Until a rebuilt binary is in use: `rm /dev/input/event1[6-8]` inside the container, then restart the service. *Where:* `native/input-bridge.c`.
*Status:* diagnosed and worked around on the second machine. The fixed bridge is rebuilt (NDK r27c, reproducible, pinned in `prebuilt.sha256`) but has not yet been run on a device.

### 25. Desktop shows only a wallpaper: no shelf, no launcher, "Owner" user, Login screen stuck as home
*Symptom:* Android boots and Settings works, but the home screen is a bare wallpaper behind `com.google.android.desktop.identity.login/.LoginLauncherActivity`.
`pm list users` shows a single full user `0:Owner`; `dumpsys user` says `Is headless-system mode: false` and `Type: full.SYSTEM`, although `ro.fw.mu.headless_system_user=true`.
A working install shows `0:System User` (HEADLESS) plus `10:<name>`.
*Cause:* the machine had run stock Waydroid before. `waydroid init -f` does not touch `~/.local/share/waydroid/data` (it is per-user, not under `/var/lib/waydroid`), so GBOS booted on
the old Android 13 data and kept its full-Owner user layout. Googlebook creates its headless user 0 and real user 10 only on a fresh `/data`.
*Fix:* move the old data aside before the first GBOS boot (`recipe/try.sh` now does: renamed to `data.before-gbos-<time>`, never deleted). To repair an existing install: stop Waydroid, rename that directory,
start again, wait for the first boot, then set the main user and sign in (INSTALL, section 6). *Where:* `recipe/try.sh` (`set_aside_data`). *Status:* diagnosed on the second machine;
the repair is being applied there. The docs previously claimed `waydroid init -f` wipes the data; it does not.

### 26. Sign-in: Next never reaches the password screen on a slow machine (`TypeError ... reading 'Ea'`)
*Symptom:* the Google sign-in page loads and renders, the email can be typed, **Next stays pale and does nothing**. `/data/local/tmp/fix-ui.out` (the injector's page report)
shows `TypeError: Cannot read properties of undefined (reading 'Ea')` from `boq-identity.AccountsSignInUi`, then `cancel: cc...` errors, and `injectedCount: 1`; `/data/local/tmp/fetched.js` (about 1.3 MB) exists.
*Cause:* `fix-ui.js` decided what to inject by looking at which scripts had finished loading. The page's own `base-js` was not "loaded" yet at the injector's first poll (on a
slow CPU it has barely started), so the injector fetched `base-js` and ran a **second copy** on top of the page's own, which breaks Google's module system. Entry 4 already warned that running
scripts twice breaks the page, but its 8-second grace period covered only the secondary scripts; `base-js` had none, and a loop over `<script>` elements injected anything unfinished at once.
*Fix:* one grace period (`GRACE`, default 45 s, `window._graceMs` to change) applied to **every** script including `base-js`, and a script that is only slow to load is no longer treated as failed. WebView 158 loads and runs all of
Google's scripts itself, so on a healthy page the injector now injects nothing. `gb-firstboot.sh` also refreshes `/data/local/tmp/fix-ui.js` when the image's copy differs (it used to copy only if absent, so an updated image kept a stale script).
To repair a running install without rebooting: push the new `fix-ui.js` to `/data/local/tmp/fix-ui.js` (the daemon rereads it every cycle), then **restart the sign-in page**: the damaged page must be thrown away
(kill the `com.google.android.gms.ui` process, reopen Settings, tap Sign in), because fixing the script does not repair a page that already ran twice.
*Where:* `recipe/fix-ui.js`, `recipe/gb-firstboot.sh`. *Status:* verified on the second machine (a Pixel Slate): sign-in completed and the account registered, with nothing injected.
Note for reproducibility: the `unlock-signin` binary on that machine is a different build from the laptop's (different sha256); the script is byte-identical on both.

### 27. Fresh install: no Wi-Fi, and adb "unauthorized"
Two bugs found by the first clean end-to-end install. Both were hidden on the developer's machine by state left over from earlier hand work in its
long-lived Android `/data`, which a fresh install does not have.

*Symptom A:* Wi-Fi is switched on but never connects; `wlan1` has no `10.77.0.1`; `/data/local/tmp/gb-fakeap.log` repeats `hostapd started (pid N)` /
`join requested` every few seconds and `hostapd.out` is empty. Running hostapd by hand also prints nothing; logcat says
`hostapd: Could not open configuration file '/data/misc/wifi/gb-ap.conf' for reading`.
*Cause:* `gb-fakeap.sh` (root) wrote `gb-ap.conf` as `root 0600`; hostapd switches to the `wifi` user before reading it. The old `/data` had a copy
made world-readable by hand, and `cat >` keeps an existing file's mode, so it never showed.
*Fix:* `gb-fakeap.sh` sets `root:wifi 0640` on the file before every start. By hand: `chown root:wifi /data/misc/wifi/gb-ap.conf; chmod 640` it; the
script's loop starts hostapd and joins within a minute.

*Symptom B:* `adb connect` to the container answers `unauthorized`, no "Allow USB debugging?" prompt ever appears, and every Phone Hub script stops
with "not reachable or not booted".
*Cause:* a fresh `/data` has no trusted keys **and** `adb_enabled=0`; Android only shows the authorization prompt while debugging is on.
*Fix:* `recipe/gb-adb-authorize.sh` writes the sudo user's `~/.android/adbkey.pub` into `/data/misc/adb/adb_keys` and sets `adb_enabled=1`;
`try.sh` runs it after the first boot. Run `sudo ./gb-adb-authorize.sh` from the work folder after any other reset of Android's data.
*Where:* `recipe/fakeap/gb-fakeap.sh`, `recipe/gb-adb-authorize.sh`, `recipe/try.sh`. *Status:* both verified on a clean install.

### 28. Fresh install: no codecs for apps, and a flood in the host journal
Found by reading the logs of the first clean install. The developer's install had hand-made fixes for all three in Waydroid's writable
layer (`overlay_rw`), which `waydroid init -f` throws away; they had never been written into the recipe.

*Symptom A:* apps find no video or audio codec at all (`MediaCodecList generated and serialized (56 bytes)`, i.e. an empty list).
*Cause:* on a desktop GPU (gralloc `gbm`) Waydroid writes `debug.stagefright.ccodec=0` into `waydroid.prop`/`waydroid_base.prop`, which
switches Codec2 off and leaves only OMX codecs. This image has no usable OMX (removed with the 32-bit OMX HAL, see the VINTF entry), so
nothing is left. *Fix:* `try.sh` sets it to `4` (Android's default: Codec2 for everything) and `bake-apps.sh` links
`/vendor/etc/media_codecs_c2.xml` to the vendor's software Codec2 list. Result: 96 codecs; YouTube plays through
`c2.ffmpeg.av1.decoder` (hardware path) with sound. `gbos-doctor.py` fails if `ccodec=0` comes back (`waydroid init` or an upgrade
rewrites the prop files).

*Symptom B:* the host's kernel journal (`journalctl -k`) grows by roughly 800 lines a minute at idle: `servicemanager ... Tried to start
aidl service android.hardware.biometrics.face.virtualhal.IVirtualHal/virtual` and `SELinux: Multiple same specifications for ...`.
Android writes these to `/dev/kmsg`, which is the host's.
*Cause:* the image declares the face-unlock feature, so `FaceService` registers face sensors at boot; on a userdebug build it also asks
for a "virtual" test HAL with `waitForService`, which never returns: a `BiometricServiceRegistry` thread in system_server stays blocked
and retries every second. Each retry also reloads the service contexts, and `system_ext_service_contexts` repeats six entries from
`plat_service_contexts`, each reported every time.
*Fix:* `build-system-img.sh` removes `/product/etc/permissions/android.hardware.biometrics.face.xml` (there is no face hardware) and drops
system_ext service-context lines that exactly duplicate a platform line. Verified: 0 such lines afterwards, the blocked thread is gone.

Not fixable here (expected noise): `IptablesRestoreController ... IDLETIMER ... --send_nl_msg` (only Android's kernel implements that
option; mainline refuses it), `Failed to enable fs-verity` (the host filesystem backing `/data` has no fs-verity), `keystore2 ... rkpd`
(no remote key provisioning), `WifiDataStall ... newStats: null` (the virtual radio has no link statistics), and Play services'
`Failed to find update_engine` (no A/B system updates in a container).
*Where:* `recipe/try.sh`, `recipe/bake-apps.sh`, `recipe/build-system-img.sh`, `installer/gbos-doctor.py`. *Status:* A verified on a clean
install (YouTube); B verified with the same two changes applied through the writable layer (the rebuilt image itself not booted yet).

### 29. Phone link never comes up after the cross-device app updated itself
*Symptom:* the Cross-device link stays down for good, also after Reconnect, a phone-side toggle and Play services restarts. Every
round of the keeper logs `Gate forced 0 time(s)`; GBOS's discovery service shows "Waiting for connections"; the phone is reachable.
*Cause:* the keeper forces the discovery gate (entry 16, step 3) with a debugger breakpoint on the gate's class, and that class name is
R8-obfuscated. Play Store updated `com.google.ambient.streaming` to 1.0.1387 on 2026-11-15, which renamed `lin` to `myr` (same fields,
same method, same log strings). jdb answered `Unable to set deferred breakpoint lin.b : No method b in lin`, nothing was forced, and
the link stayed down. The last forced gate in the journal is the evening before the update; every round after it forced 0. The App
Cast feeder's three obfuscated classes were renamed too (`kjq`, `kkp`, `xil` became `map`, `mbo` plus a new variant `lyy`, and `zlf`).
*Fix:* the names are variables now (`GATE` / `GB_GATE_CLASS` in the keeper, `N_UNIT`, `N_STALL`, `N_CONN` in `feed_live.js`), the keeper
warns when the app's versionCode is not the one they are known for, and TROUBLESHOOTING has the table of known names and how to
find new ones (baksmali, search for each class's log string). Verified: with `myr` the next kick forced the gate 3 times and the link
connected 3 s later.
*App Cast, the same update:* `appcast_loopback.js` used four more renamed classes (`jwi`, `kan`, `llz`, `lmb` became `ljx`, `lnp`, `ncd`,
`ncf`). With `llz` wrong the cast window never established (`llz.a DISARMED — armed 45s with no Establish`), the supervisor saw 0
frames, restarted the cross-device app, and that dropped the link: the two services fought each other. With the class names fixed the
cast played, then the app died 13 s after its moving GC resumed: `GC tried to mark invalid reference 0x80008` in
`MarkCompact::MarkRoots` → `ArtMethod::VisitRoots`. Cause: the codec helper class also renamed its *methods* (`f`→`e`, `g`→`f`, `h`→`g`),
so the script's string-returning replacements for `h`, `f`, `g` landed on different methods, one of them returning boolean. Hooks are
now addressed by role and pinned to argument types with `.overload(...)`, so a future rename fails with a warning instead.
Verified: cast running with the link connected, no crash for 5 minutes after the GC resumed (it had crashed at 13 s).
*Where:* `host/phonehub/gb-hub-link.sh`, `host/phonehub/hub_try.sh`, `host/phonehub/injector/feed_live.js`,
`host/phonehub/injector/appcast_loopback.js`, `docs/TROUBLESHOOTING.md`.

---

## Things that did not work (and why they are worth knowing)

* **Overriding a Google flag with the documented broadcast** (`FLAG_OVERRIDE`): always "Successfully set flag overrides? false", on any package name. The security gate rejects it on a `release-keys` build.
* **Editing the cross-device app's own flag snapshot** while Play services' shared snapshot exists: no effect (entry 16, step 2).
* **Hairpin NAT through the virtual Wi-Fi radio:** broken by `mac80211_hwsim` copying conntrack state.
* **A debugger left attached to the cross-device app:** freezes all threads on each breakpoint evaluation and kills the link (entry 16, step 3). A suspend of more than ~15 s drops the phone link as well; batch reads and `resume`.
* **Labelling the LAN interface as Wi-Fi without the fake access point:** Android decided the Wi-Fi request was satisfied and never joined anything (entry 14).
* **Swapping `libfmq` to fix audio:** the duplicate `libfmq`/`libhidlbase` (system 17 vs older vendor copies) looked guilty and was not.
* **The physical `base-state` override** (`cmd device_state` with a base state) crashes Android's userspace. The "lid" is closed at every start because there is no lid sensor; `cmd device_state state 2` marks it open but flips
  the master mute as a side effect.
* **A framework-only restart** (`setprop ctl.restart zygote`) exposed latent crashes and took the whole UI down once. Stop and start the container instead. Restarting only `audioserver` is safe.
* **`input tap`** is often dropped by the input dispatcher on these windows ("dropping inconsistent event"); a short `input swipe X Y X Y 120` works more often. Reading an app's own log is more reliable than clicking.
* **Plain `strace` from the host** cannot attach to the container; root inside the container can `ptrace`, so push a static tracer. Detaching can kill the tracee (`PTRACE_O_EXITKILL`).
* **Trusting "the stream exists" as proof of sound,** or "the notification text says Connected" as proof of a link: the keeper's first version matched mirrored phone notifications and took healthy links apart. Match the app's own
  ongoing record (`id=1`, channel `device_link_channel_id`).
