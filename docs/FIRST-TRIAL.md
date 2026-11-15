# First trial on a second machine

You are about to run GBOS on a machine it has never run on, replacing a Waydroid that already works. Everything was
developed and checked on one laptop, and the full sequence has never been run end to end anywhere. So treat this as a
trial: go one milestone at a time, check each before moving on, and **stop at the first thing that is wrong**.

## What "update" means here

GBOS is a different Android (17, from the Googlebook image) from the one in a stock Waydroid (LineageOS, Android 11 to
16). `try.sh` replaces the system and **moves Waydroid's data aside** (renamed, kept): the apps, accounts and files inside the old Android do
not carry over, because they belong to a different Android version. What you can keep is a way back: `gbos-backup.py`
saves the old install so you can restore it.

## 0. Check the machine (10 minutes, changes nothing)

```sh
uname -m            # must say x86_64. On aarch64/armv7 the Googlebook image cannot run at all: stop here.
grep -o -w -E 'gfni|vaes|vpclmulqdq|avx_vnni|sha_ni' /proc/cpuinfo | sort -u   # all FIVE must appear, or Android dies instantly (SIGILL)
free -g | head -2   # developed on 7.6 GiB; under ~6 is untested and likely painful
echo $XDG_SESSION_TYPE    # wayland
waydroid --version        # tested with 1.6.3
waydroid status
adb shell wm size 2>/dev/null; # or, with the old Waydroid running: adb connect <ip>:5555 && adb shell wm size
df -h ~ | tail -1         # about 25 GB free
```

Then the repository's own checks (they need only the repo and Python):

```sh
installer/gbos-install.py check --downloads ~/gbos/dl --work ~/gbos/work
```

It tests architecture, memory, tools, disk, whether your folders are RAM-backed, and the kernel options. Everything it
marks `MISSING` or `FAIL` has to be fixed first. A slate's kernel is the likeliest problem: it needs binder, `mac80211_hwsim`,
`uinput`, **the legacy iptables tables** and the other options listed in [HARDWARE](HARDWARE.md#what-gbos-needs-from-the-pc).
If `check` reports any option missing, do not go on: `gbos-install.py kernel-fragment --base <your .config>` prints exactly what to add, and for
the legacy iptables tables there is also an add-on that needs no kernel rebuild: [`kernel/README.md`](../kernel/README.md). If your slate is an
ARM device or a Chromebook-class tablet on a vendor kernel, `check` will tell you here instead of an hour in.

**The screen.** The input bridge assumes a 1920x1080 display. On any other resolution clicks and touches land in the
wrong place until you tell it the real size (step 3 below). Write down the output of `wm size`.

## 1. Get the files (saves hours if the laptop already has them)

The inputs are about 10 GB and are checksum-verified, so copying them from a machine that already downloaded them is
safe; the installer re-checks every file:

```sh
rsync -av other-machine:/path/to/dl/  ~/gbos/dl/       # or just let `fetch` download them
installer/gbos-install.py fetch   --downloads ~/gbos/dl --with-ndk
installer/gbos-install.py prepare --downloads ~/gbos/dl --work ~/gbos/work --ndk auto --phone-hub
```

**Milestone 1:** `prepare` finishes with "work folder ready". Nothing outside `~/gbos` has changed. If a patch refuses
(`does not match the pinned`), you have a different Googlebook build than the one supported: stop and report which.

## 2. Save the old Waydroid, then replace it

```sh
waydroid session stop && sudo waydroid container stop
cd ~/gbos/work
sudo ./gbos-backup.py backup --out /a/disk/folder --with overlay,images,data --user $USER
./gbos-backup.py verify /a/disk/folder/gbos-backup-*       # every tier must say ok
sudo ./try.sh                                              # type "reset" when asked
```

If the slate is short of disk, back up to an external drive; `images` is the part you can skip if you can re-download
the stock image later. `try.sh` takes a few minutes and waits three for Android to boot.

**Milestone 2:** a Waydroid window opens and shows the Googlebook desktop. Then run `./gbos-doctor.py`; the
"container" section should be all `ok`.

If it does not boot, do not retry blindly: collect the files in "If something fails" below, then roll back.

## 3. Make it yours

```sh
waydroid session stop && sudo waydroid container stop
sudo ./bake-apps.sh
waydroid session start
./gbos-doctor.py
```

**Milestone 3:** the doctor shows the baked files present and nothing shadowed; the desktop is back; **keyboard, mouse
and touch work and land where you point.** If the pointer is offset or cannot reach an edge, set the display size
(the numbers from `wm size`):

```sh
adb connect $(waydroid status | awk '/^IP address/{print $NF}'):5555
adb shell setprop persist.gbos.display 1280x800     # your numbers
adb shell setprop ctl.restart gb-inputbridge
```

The doctor warns about exactly this mismatch. Rotation, on-screen keyboard and suspend/resume on a tablet have not been tried.

**Milestone 4:** sign in (Settings, Add account), then install something from Play. Sound: play a video and listen.

## 4. Network, then Phone Hub (optional, last)

Only after the desktop is solid:

```sh
sudo host/install-host.sh system lan wifi             # LAN presence and a virtual Wi-Fi radio
host/install-host.sh phonehub --work ~/gbos/work
```

then restart Waydroid and follow [INSTALL, steps 6 and 7](INSTALL.md#6-first-boot-the-main-user-then-sign-in)
(main user, `gb-userfixes.sh`, the phone app). **Milestone 5:** `./gbos-doctor.py` shows the Wi-Fi and LAN services active.
**Milestone 6:** the Cross-device link to the phone shows connected. These two steps change the host's networking
(a virtual radio, an ipvlan interface on your Wi-Fi), so do them on purpose, not in a hurry.

## If something fails

Stop at the first failing milestone and collect, **before** changing anything:

```sh
./gbos-doctor.py --json > doctor.json
tail -n 300 ~/gbos/work/logcat.txt  ~/gbos/work/dmesg.txt  ~/gbos/work/waydroid.log  ~/gbos/work/status.txt
```

and the exact output of the failing command. These can contain account names, network names and addresses: read
them before you share them.

Then either ask for help with those files, or roll back:

```sh
waydroid session stop && sudo waydroid container stop
sudo ./gbos-backup.py restore /a/disk/folder/gbos-backup-<time>      # moves what it replaces aside, never deletes
waydroid session start
```

and, if you installed host services, `host/install-host.sh phonehub --remove` and
`sudo host/install-host.sh system lan wifi bt --remove`.

## What is most likely to go wrong on a new machine

* The kernel lacks something (`check` should have said so).
* The display is not 1920x1080 (step 3).
* Less RAM than the 7.6 GiB it was developed on: Android's memory killer removes Play services and the cast app.
* `try.sh`'s hardware-composer patch step has never been run from a fresh work folder: look for "hwcomposer patch failed" in its output.
* A different Waydroid version rewrites config the recipe depends on: `restore-container-tweaks.sh` re-applies it.
