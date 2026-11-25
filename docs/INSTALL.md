# Installing GBOS

Read the warnings in the [README](../README.md#read-before-installing) first. In particular, **step 3 resets Waydroid.**
Trying it on a machine that already runs Waydroid? [FIRST-TRIAL.md](FIRST-TRIAL.md) is the same sequence as a checklist with milestones and a way back.

Everything below is run from a checkout of this repository. `D` is the download folder and `W` the work folder; both
must be on disk (not RAM-backed) with about 25 GB free between them.

```sh
D=~/gbos/dl;  W=~/gbos/work
```

### The graphical installer

```sh
installer/gbos-installer-gui.py          # as your normal user, from the checkout
```

It is a window with the steps below in a sidebar, one page each, and a button that runs the step. It runs **exactly the
commands on this page**, prints each one in the step's output pane before running it, and keeps nothing secret.

* **Root:** steps that need root ask for your password through the system's polkit dialog (`pkexec`), once per step.
* **The reset:** the destructive step (step 3) asks you to type `reset` first.
* **Missing packages:** the check page shows the command to install them, with a button that runs it.
* **Kernel add-ons:** if the check says some are needed, the check page builds and installs them. If your kernel's
  headers are missing it offers to install them first (the package for the running kernel, plus the one that keeps them
  current with kernel updates), and it says up front when Secure Boot or enforced module signatures would refuse them.
* **Saved state:** your folders, choices and finished steps are kept in `~/.config/gbos/installer.json`, so you can close
  it and come back.

It needs GTK 4 and libadwaita 1.5 or newer with their Python bindings. On Debian/Ubuntu install them with
`sudo apt install python3-gi gir1.2-gtk-4.0 gir1.2-adw-1`. Debian 13 and Ubuntu 24.04 or newer are recent enough. The
command-line steps below need neither.

**How tested it is:** the GUI is new. Its read-only pages (the check, the health check) and the way it passes your user
to root have been tested. Its root steps run the same scripts as below, but nobody has yet clicked through a whole clean
install with it. If a step fails, its output pane shows the command, so you can run it in a terminal instead.

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

They are built against your **running** kernel's build files: `/lib/modules/$(uname -r)/build`, which the headers package
provides on a distribution kernel (Debian, Fedora and Arch do not install it by default; Ubuntu does). `addons` prints the
package to install if it is missing (`linux-headers-$(uname -r)` and `linux-headers-amd64` on Debian, `kernel-devel` on Fedora,
`linux-headers` on Arch). For a self-built kernel, pass `--tree` with the folder it was built in. Build whatever applies in one step
(no root), then install what it prints:

```sh
installer/gbos-install.py addons --out ~/gbos/addons --build         # --tree DIR for a self-built kernel
sudo kernel/gbos-vexemu.py   install --out ~/gbos/addons/vexemu      # whichever it built; they verify again and refuse a mismatch
sudo kernel/gbos-secctx.py   install --out ~/gbos/addons/secctx
sudo kernel/gbos-netfilter-legacy.py install --out ~/gbos/addons/netfilter   # then reboot
installer/gbos-install.py check                                      # should now say "ready"
```

Details, limits and licensing: [kernel/README.md](../kernel/README.md); a worked example on an unsupported machine: [UNSUPPORTED-HARDWARE.md](UNSUPPORTED-HARDWARE.md).
`try.sh` refuses to start on a CPU that needs `vexemu` before it is loaded, and warns about the binder problem.

**After a kernel update** the add-ons must be built again for the new kernel (run the check again; it lists them as needed).
**Secure Boot:** the add-ons are unsigned, so a kernel in lockdown or with enforced signatures refuses them; see
[kernel/README.md, "Secure Boot"](../kernel/README.md#secure-boot).

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

1. Set up the phone over wireless debugging. On the phone turn on Developer options and **Wireless debugging** (same
   Wi-Fi as the PC); its screen shows the phone's *IP address & Port*. The first time, also tap *Pair device with pairing
   code*, which shows a 6-digit code and a second IP:port (both change each time that dialog opens). Then:

   ```sh
   host/phonehub/gb-phone-setup.sh --connect IP:PORT --pair PAIR_IP:PAIR_PORT --code 123456 --work $W   # first time
   host/phonehub/gb-phone-setup.sh --connect IP:PORT --work $W                                          # afterwards
   ```

   `--check` only reports; `--no-accessibility` skips the optional part. A USB cable with USB debugging works too: leave
   out `--connect`/`--pair`. The installer GUI has the same step ("Set up your phone").

   It installs the phone app GBOS Cast (built into `W/gbcast_app/build/`, signed with your key), grants
   `WRITE_SECURE_SETTINGS` (for its Re-arm button), `RECORD_AUDIO` and `POST_NOTIFICATIONS`, allows its notification
   listener, optionally turns on its accessibility service (touch control; Advanced Protection blocks it, and the touch
   agent below does the same job), opens it, and reads every change back. An existing copy signed with a different key
   is kept, not removed. Then press **Re-arm touch forwarding** once in the app, with App Cast running on the PC; it
   starts the touch agent, and is needed again only after the phone reboots. Wireless debugging can be switched off
   afterwards; Re-arm turns it on by itself for a few seconds when it needs it.
2. On the phone, turn on *Cross-device services* for the account GBOS is signed in with.
3. In GBOS, open the Cross-device services popup and scan its QR code with the phone.
4. Start casting from the phone app (it finds your PC on its own), or open App Cast in GBOS.
5. Optional: put the **Phone Hub widget** on the GBOS desktop (right-click the desktop, Widgets, "Phone Hub"). It shows
   whether the Cross-Device link to your phone is up and has a **Reconnect** button. `bake-apps.sh` installs it as a system
   app when the work folder was prepared with `--phone-hub`; it talks to the `gb-link-daemon` host service.

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
sudo rm -f /etc/udev/rules.d/65-waydroid-input-ignore.rules /etc/udev/rules.d/99-waydroid-input-ignore.rules /etc/modules-load.d/waydroid-iptables.conf
```
