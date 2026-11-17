# Troubleshooting

Every entry below was a real failure on the developer's machine, with the fix that worked. For the cause behind each fix, and the dead ends, see the [fix catalog](FIX-CATALOG.md). Commands starting `adb`
assume `adb connect <container address>:5555` (see [the adb tutorial](TUTORIAL-adb.md)) and `adb root`.

## First, run the doctor

```sh
installer/gbos-doctor.py          # also in your work folder; read-only
```

It checks the host, Waydroid, the container settings that `waydroid upgrade` rewrites, the baked overlay (including
files in the writable layer that shadow it), the host services and, if Android is up, its boot state, main user and
the Cross-device link, and prints the fix next to everything it flags. Exit status 0 / 1 / 2 = good / warnings / broken.
`--offline` only looks at files.

## Then, three habits

* **Check what changed before blaming the last change.** One "black screen after my change" turned out to be a Google
  flag that had arrived hours earlier; reverting the change did nothing, which was the clue.
* **Compare checksums.** A stale file in the upper overlay silently shadows a baked fix (see
  [ARCHITECTURE](ARCHITECTURE.md#persistence-where-changes-live)).
* **Do not restart only the Android framework** (`setprop ctl.restart zygote`). It once took the whole UI down. Stop and
  start the container instead.

## Android does not boot

| Symptom | Cause | Fix |
|---|---|---|
| Boot animation, then black; `netd` aborts, `init` keeps killing the zygotes | Waydroid rewrote its config (after `waydroid upgrade` / `waydroid init`) and the container tweaks are gone | `sudo ./restore-container-tweaks.sh`, then `sudo waydroid container stop`, `waydroid session stop`, start again. Backups are kept |
| Boots, then everything restarts a few minutes in | a crash in `system_server` or the hardware composer | read `waydroid log` and `adb logcat -b crash`. The known causes are patched by the recipe; if you see a new one, the build is probably not the supported one |
| Hybrid GPU stopped working | same cause, for the optional GPU mode | `sudo gpu/gbos-gpu apply` |
| `waydroid session start` succeeds but no window ever appears; the container is RUNNING then STOPPED within a second; `logcat` is empty | the CPU lacks instruction sets the image's first process uses (SIGILL) | `grep -o -w -E 'gfni\|vaes\|vpclmulqdq\|avx_vnni\|sha_ni' /proc/cpuinfo \| sort -u` must list all five; `gbos-install.py check` says so. No fix on the host side: see [HARDWARE](HARDWARE.md#what-gbos-needs-from-the-pc) |
| The first boot after `try.sh` shows VINTF errors like `Unrecognized manifest.version 9.0`, or runs Android 13 | Waydroid initialised from the OLD images in `/var/lib/waydroid/images`: `waydroid init` was run without `-i` | fixed in `try.sh` (it passes `-i /etc/waydroid-extra/images`); check `grep images_path /var/lib/waydroid/waydroid.cfg` |

## Sign-in, Play Store, storage

| Symptom | Fix |
|---|---|
| The Google sign-in page never loads | the Google-signed WebView does not work in this container; the recipe replaces it with an upstream Chromium WebView. Check `adb shell dumpsys webviewupdate` (provider should be `com.android.webview`) and `adb shell pm path com.google.android.webview` (should print nothing). If an old data copy is listed: `adb shell pm uninstall com.google.android.webview` |
| Play Store error 1047, or Settings, Storage crashes | the vendor manifest still declares a 32-bit codec service that cannot run, which stalls storage setup. `sudo ./bake-apps.sh` installs the corrected manifest and the storage fix |
| An app that needs the camera or a sensor crashes | the container has none of the hardware; not a bug |

## Network

| Symptom | Cause | Fix |
|---|---|---|
| Wi-Fi tile says "Networks unavailable" after a restart; `cmd wifi status` not connected | the LAN interface is labelled as Wi-Fi and came up first, hiding Android's Wi-Fi request | automated in `gb-fakeap`. By hand: `ip link set eth1 down`, `cmd wifi connect-network "<your Wi-Fi name>" open`, wait for "connected", `ip link set eth1 up` |
| Android has no LAN address | the host `gb-lan` service is not running | `systemctl status gb-lan`; `sudo gb-lan.py --plan` shows what it would do |
| No Wi-Fi at all | the host `gb-wifi-host` service or the `mac80211_hwsim` module is missing | `systemctl status gb-wifi-host`; `lsmod \| grep hwsim` |

## Phone Hub

First stop: `~/.local/share/gbos/phonehub/appcast_up.sh status`. It shows the container, Android's boot state, frida,
the Cross-device link, the cast session and the listening ports in one screen.

| Symptom | Cause | Fix |
|---|---|---|
| Cross-device stays on "Looking for your nearby devices..." after a restart | Play services holds a stale view of the account's cross-device state and answers "not available" | on the phone, toggle *Cross-device services* **first**; then in the container `am force-stop com.google.ambient.streaming` and `am force-stop com.google.android.gms` (about 40 s to come back). The keeper now does the second half itself |
| Connects but drops every few minutes, bandwidth stays LOW, log shows BLE only | Play services' LAN traffic was routed out of the wrong interface, so the Wi-Fi upgrade never completes | the keeper installs an `ip rule` for it (`adb shell ip rule \| grep 15030`); if it is missing, check `journalctl --user -u gb-hub-link` |
| The keeper seems to do nothing | a stale lock file says a cast is live | delete `$XDG_RUNTIME_DIR/gbos/gb-appcast.lock`; `appcast_up.sh status` reports "keeper standing down" when it exists |
| App Cast window opens but no frames arrive | the phone is not casting | open GBOS Cast on the phone and press *Start casting*. The supervisor stops after five empty launches rather than loop; then `systemctl --user restart gb-appcast` |
| Picture works, clicks do nothing | the phone-side gesture agent died (the phone rebooted) | press *Re-arm* in the phone app (wireless debugging on), or restart the cast |
| Everything stalls while a host job runs | memory pressure: Android's killer removes Play services and the cast app | stop heavy host jobs while casting; see Memory below |
| Sound from the phone is missing | `gb-audio` not running, or the phone app's audio is off | `systemctl --user status gb-audio` (port 8101) |
| Cross-device never connects; the keeper's log repeats `Gate forced 0 time(s)` | the Cross-Device Services app (`com.google.ambient.streaming`) updated itself and its obfuscated class names changed, so the keeper's debugger breakpoint lands on nothing (`$XDG_RUNTIME_DIR/gbos/hub-link/out` says `Unable to set deferred breakpoint`) | find the new names as below and set them; the keeper also logs a warning when the app's version is not the one the names are known for |

### When an app update renames the classes

The keeper and the App Cast feeder hook four classes inside the Cross-Device Services app. Google's build obfuscates their names,
so an update renames them. The code inside stays the same, and each class has log strings that identify it. Known names:

| Role | Where it is used | Before 2026-11-15 | 1.0.1387 (versionCode 100143140) | Identifying string |
|---|---|---|---|---|
| discovery gate | `gb-hub-link.sh` `GATE` (or `GB_GATE_CLASS`) | `lin` | `myr` | `Discovery is not paused, starting discoverDevices.` |
| stall/launch watchdog | `injector/feed_live.js` `N_STALL` | `kkp` | `mbo`, `lyy` | `Stream stalled for %d ms` |
| connection attempt | `injector/feed_live.js` `N_CONN` | `kjq` | `map` | `SecurityException during connection` |
| Kotlin `Unit` | `injector/feed_live.js` `N_UNIT` | `xil` | `zlf` | `kotlin.Unit` |

To find them for a new version, pull the app and disassemble it with [baksmali](https://github.com/baksmali/smali/releases)
(light on memory, unlike a full decompile), then search for the strings:

```sh
adb shell pm path --user 10 com.google.ambient.streaming      # the /data/app/... base.apk
adb pull <that path> cds.apk
mkdir cds && cd cds && unzip -q ../cds.apk 'classes*.dex'
for d in classes*.dex; do java -Xmx768m -jar baksmali.jar d "$d" -o smali; done
grep -rlF 'Discovery is not paused, starting discoverDevices.' smali     # -> smali/<name>.smali
```

Check that each match has the same shape as before (for the gate: a class with a `synthetic b:Z` field and a method
`b(Ljava/lang/Object;)Ljava/lang/Object;`). Then set the names, restart `gb-hub-link` and `gb-appcast`, and please open
an issue with the version and the names so they can be added here.

## Sound, memory, shutdown

* **No sound after boot:** `adb shell dumpsys media.audio_flinger | grep "Master mute"` should read `off` shortly after boot;
  the `gb-audiofix` service clears it. If it does not, `adb shell logcat -d | grep audiofix`.
* **Memory:** on a machine with 8 GB or less, Android's low-memory killer and the host's OOM killer both show up under load. The
  recipe keeps `system_server` and `surfaceflinger` out of the OOM killer (`gb-oomguard`). Big compiles or decompiles on the host
  while casting will still hurt: cap their heap or wait.
* **Waydroid still "running" after Android shut itself down:** the `gb-autostop` user service finishes the shutdown. If you
  did not install it: `waydroid session stop`.
* **Hiding the host's top panel used to crash Android** (an input hover assertion after a display resize). The recipe contains
  the fix; no verification is recorded, so report a crash here if you see one.

## Digging deeper

* A root shell: `adb root` (the plain `adb shell` user cannot do most of this). The container address is in `waydroid status`.
* `adb logcat` echoes your own commands back; filter with `grep -v adbd` or you will count your own grep.
* Live look inside a WebView: `/data/local/tmp/eval-js` talks to its DevTools socket (email step only, never a password screen).
* Java without source: `am set-debug-app -w <pkg>`, `adb forward tcp:8700 jdwp:<pid>`, `jdb -attach localhost:8700`; the first
  command must be `suspend`.
* Host side logs: `journalctl --user -u 'gb-*'`, `/run/user/$UID/gbos/*.log`.
* **Never:** run `try.sh` or `waydroid init -f` unless you mean to reset Waydroid; run `adb kill-server` while services are using adb.
