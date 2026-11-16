# Installing GBOS

Read the warnings in the [README](../README.md#read-before-installing) first. In particular, **step 3 resets Waydroid.**
Trying it on a machine that already runs Waydroid? [FIRST-TRIAL.md](FIRST-TRIAL.md) is the same sequence as a checklist with milestones and a way back.

Everything below is run from a checkout of this repository. `D` is the download folder and `W` the work folder; both
must be on disk (not RAM-backed) with about 25 GB free between them.

```sh
D=~/gbos/dl;  W=~/gbos/work
```

## 1. Check the machine

```sh
installer/gbos-install.py check --downloads $D --work $W
```

It lists missing tools, your session type (Waydroid needs Wayland), free disk, whether your folders are RAM-backed, and
which kernel options are present. When packages are missing it ends with **one command that installs all of them** for
your package manager (apt, dnf, pacman or zypper), e.g. `sudo apt install erofs-utils patchelf default-jdk-headless`;
it prints that command but never runs it. Waydroid itself is never in it: it is a prerequisite you set up and get
working first. Fix everything it marks `MISSING` / `FAIL` before going on. adb is a prerequisite you set up yourself:
[TUTORIAL-adb.md](TUTORIAL-adb.md).

`check --json` prints the same result as JSON (every check with its status, the missing packages split into "build"
and "phonehub", the install command, and the kernel add-ons needed), for scripts and front-ends.

### Kernel add-ons (only if `check` says "needed here")

Some machines need something the stock kernel or CPU does not provide. `check` has a **kernel add-ons** section that says which of these
apply; each is a loadable module, **none needs a kernel rebuild**:

| `check` says | Add-on | Why |
|---|---|---|
| this CPU lacks gfni, vaes, vpclmulqdq, avx_vnni, sha_ni | `vexemu` | Android's first process dies with SIGILL; the module emulates those instructions (slow) |
| AppArmor is the only security module and the kernel lacks the cred_getsecid fix | `secctx` | binder cannot give keystore2 a caller context; boot stops at the boot animation |
| the legacy iptables tables are missing | `netfilter` | Android has no network |

Build whatever applies in one step (no root; `--tree` is the tree or linux-headers your **running** kernel was built from), then install what it prints:

```sh
installer/gbos-install.py addons --tree /path/to/kernel/tree --out ~/gbos/addons --build
sudo kernel/gbos-vexemu.py   install --out ~/gbos/addons/vexemu      # whichever it built; they verify again and refuse a mismatch
sudo kernel/gbos-secctx.py   install --out ~/gbos/addons/secctx
sudo kernel/gbos-netfilter-legacy.py install --out ~/gbos/addons/netfilter   # then reboot
installer/gbos-install.py check                                      # should now say "ready"
```

Details, limits and licensing: [kernel/README.md](../kernel/README.md); a worked example on an unsupported machine: [UNSUPPORTED-HARDWARE.md](UNSUPPORTED-HARDWARE.md).
`try.sh` refuses to start on a CPU that needs `vexemu` before it is loaded, and warns about the binder problem.

## 2. Download and prepare

```sh
installer/gbos-install.py fetch   --downloads $D --with-ndk
installer/gbos-install.py prepare --downloads $D --work $W --ndk auto --phone-hub
```

Two small programs run inside Android (`input-bridge`, `vpd-stub`) and need the Android NDK to build. `--with-ndk` /
`--ndk auto` fetch and use it (a 660 MB download). To skip that, take the prebuilt ones from the release instead:

```sh
installer/gbos-install.py fetch-prebuilt --url https://github.com/<owner>/<repo>/releases/download/<tag> --dest ~/gbos/prebuilt
installer/gbos-install.py prepare --downloads $D --work $W --prebuilt ~/gbos/prebuilt --phone-hub
```

Either way the result is the same bytes: the repository's [`prebuilt.sha256`](../prebuilt.sha256) lists the checksum of
each prebuilt, downloads are refused if they differ, and the NDK build is reproducible, so building it yourself
gives exactly the published file (`prepare` tells you when they match).

`fetch` downloads about 10 GB from the original publishers (the Googlebook recovery image from Google, the Android 16
donor and vendor images from the WayDroid-ATV project, an upstream Chromium WebView, the Android build toolchain from
Maven). Every file is resumable and verified against [`sources.lock`](../sources.lock); a file that does not match is
deleted. `--with-ndk` adds the Android NDK, needed to build two small native programs.

`prepare` (about a minute once the files are local) extracts the Googlebook partitions, derives the patched libraries
from them, builds the native helpers and the Android apps, and assembles `W` exactly as the scripts expect. It is
safe to re-run: finished stages are skipped. It also creates the signing key `W/svc_stub_apk/stub.p12`; **keep it**,
apps you rebuild later must be signed with the same key.

It changes nothing outside `D` and `W`.

## 3. Build the image and initialise Waydroid (destructive)

**If Waydroid already holds anything you want to keep, back it up first.** `prepare` put the backup tool in `W`.
Stop Waydroid, then:

```sh
waydroid session stop && sudo waydroid container stop
cd $W
sudo ./gbos-backup.py backup --out /a/disk/folder --with overlay,data --user $USER   # --with images too, if you like
./gbos-backup.py verify /a/disk/folder/gbos-backup-<time>
```

The small part (container config, properties, host rules and units) is saved automatically by `try.sh` into `W/backups`.
`gbos-backup.py restore <folder>` puts it back with Waydroid stopped; it moves what it replaces aside (never deletes).

```sh
sudo ./try.sh          # asks you to type "reset"; GBOS_YES=1 skips the question
```

This is the step to think about. `try.sh`:

* merges the Googlebook partitions into `system.img` and patches a copy of the vendor image, and copies both to
  `/etc/waydroid-extra/images/`;
* runs **`waydroid init -f`**, which deletes Waydroid's writable overlay, and **moves your existing Android data aside** (`~/.local/share/waydroid/data` is renamed to `data.before-gbos-<time>`, not deleted: the new Android has to start on a clean `/data`);
* edits `/var/lib/waydroid` (container config, properties) and installs a udev rule and a modules-load file for the
  legacy iptables modules, loading them now;
* installs `patchelf` through `apt` if it is missing;
* starts Waydroid and waits about three minutes for Android to boot, leaving logs in `W`.

Do not run it on a Waydroid install you care about without a backup of `~/.local/share/waydroid`.

## 4. Bake the overlay

`bake-apps.sh` writes everything persistent (the WebView, the stub services, the boot scripts, the apps, the patched
libraries) into Waydroid's lower overlay. It never touches your data, and it is also how you apply updates later.
Waydroid must be fully stopped:

```sh
waydroid session stop && sudo waydroid container stop
sudo ./bake-apps.sh
waydroid session start
```

The first boot afterwards is slow (it scans a large WebView). When it is up, run the health check:

```sh
./gbos-doctor.py          # read-only; says what is wrong and what to run
```

and then:
`adb connect $(waydroid status | awk '/^IP address/{print $NF}'):5555`.

`sudo ./bake-apps.sh --remove` undoes the baking.

## 5. Host services

The Wi-Fi and LAN services are what make Android believe it is on your network; Phone Hub needs both.

```sh
sudo host/install-host.sh system lan wifi            # add `bt` to share the host Bluetooth adapter
host/install-host.sh phonehub --work $W              # takes the helper apps and the touch jar from the work folder
```

Details, ports and the trust model: [host/README.md](../host/README.md). Restart Waydroid after the system part.

## 6. First boot: the main user, then sign in

Android creates the real user (user 10) on the first boot. Google services expect that user to be the device's
**main user**, and the flag can only be set while Waydroid is stopped:

```sh
waydroid session stop && sudo waydroid container stop
~/.local/share/gbos/phonehub/set-main-user.py ~/.local/share/waydroid/data/system/users/10.xml           # dry run
~/.local/share/gbos/phonehub/set-main-user.py ~/.local/share/waydroid/data/system/users/10.xml --apply   # keeps a .bak-before-main
waydroid session start
```

It refuses anything but the one layout it knows. After this restart GBOS shows a **user picker with one user, "Owner"**:
click it to log in (that is the main user you just set).

Then sign in with the Google account **you use on your phone** (Phone Hub only links a phone signed in to the same
account). Either sign in in the "Android Setup" window that may open on the first boot, or use the **"Finish Googlebook
setup"** notification; Settings → Add account works too. Only the sign-in step of the setup wizard runs here; the rest of
the wizard is disabled on purpose (it needs a security chip this machine does not have).

Three things that depend on your machine, all checked by `installer/gbos-doctor.py`:

* **Screen size.** The input bridge assumes a 1920x1080 panel. On any other screen set `persist.gbos.display` to the size `wm size` reports,
  or touches and clicks land in the wrong place ([HARDWARE](HARDWARE.md)). It is stored in Android's data, so set it again after a fresh `/data`.
* **A fresh `/data`.** If Waydroid ran on this machine before, `try.sh` moves the old data aside (renamed, not deleted). Without that the new Android keeps
  the old user layout and shows only a bare wallpaper ([FIX-CATALOG 25](FIX-CATALOG.md)).
* **A slow CPU.** The first boot and every Google sign-in page are slow; allow several minutes for the first boot and up to a minute per sign-in page
  ([FIX-CATALOG 26](FIX-CATALOG.md) if Next does nothing).

## 7. Phone Hub

Once you have signed in, apply the settings Phone Hub needs inside Android's data:

```sh
~/.local/share/gbos/phonehub/gb-userfixes.sh --dry-run      # shows the state and the SQL, changes nothing
~/.local/share/gbos/phonehub/gb-userfixes.sh
```

It unrestricts location for user 0 (otherwise Android hides the Wi-Fi name from every app), grants the cross-device
app the local-network permission, writes the Play services flag overrides (an edit of Play services' local database,
unsupported by Google), enables App Cast and Quick Access in the cross-device settings, and applies memory-killer
tuning. It is idempotent, backs up what it overwrites to `~/.cache/gbos/userfixes/<time>/`, and is the thing to run
again after any reset of Waydroid's data. Details are at the top of the script.

Then:

1. Install the phone app (built into `W/gbcast_app/build/`) on the phone with `adb install`.
2. On the phone, turn on *Cross-device services* for the account GBOS is signed in with.
3. In GBOS, open the Cross-device services popup and scan its QR code with the phone.
4. Start casting from the phone app (it finds your PC on its own), or open App Cast in GBOS.

Status and repair: `~/.local/share/gbos/phonehub/appcast_up.sh status`, `gb-reconnect.sh`. See
[TROUBLESHOOTING](TROUBLESHOOTING.md). Everything up to and including `gb-userfixes.sh` has been run on a clean install;
the phone steps 1-4 have not yet been re-tested from one.

## 8. Optional: hybrid GPU

Only for Intel + NVIDIA machines: [gpu/README.md](../gpu/README.md). Run `gpu/gbos-gpu detect` to find out.

## Updating

* Newer scripts or apps from this repository: `git pull`, re-run `prepare` (it skips finished stages), stop Waydroid,
  `sudo ./bake-apps.sh`, start Waydroid. **Do not** re-run `try.sh` for this.
* Re-run `try.sh` only for a clean rebuild or a new Googlebook build (it resets Waydroid, see step 3).
* If you (or an app) ran `waydroid upgrade` or `waydroid init`, Waydroid's container config was rewritten and Android
  will not boot cleanly: run `sudo ./restore-container-tweaks.sh` (non-destructive, it keeps backups), and
  `sudo gpu/gbos-gpu apply` if you use the GPU mode.
* To use Waydroid's normal Android again: `sudo ./bake-apps.sh --remove`, then `sudo waydroid init -f` with Waydroid's own
  images. The files `try.sh` put in `/etc/waydroid-extra/images/` must be removed for that.

## Uninstalling the host side

```sh
host/install-host.sh phonehub --remove
sudo host/install-host.sh system lan wifi bt --remove
sudo gpu/gbos-gpu disable --purge                     # if you enabled it
sudo rm /etc/udev/rules.d/99-waydroid-input-ignore.rules /etc/modules-load.d/waydroid-iptables.conf
```
