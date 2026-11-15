# Proof of concept: GBOS on hardware it was not built for

The Googlebook image targets a recent Intel/Google-class CPU and a distribution kernel with SELinux-free Android assumptions
baked in. The question this page answers: *can it be reproduced on an ordinary, older, unsupported machine?* It has been,
once, on a **Google Pixel Slate** (codename `nocturne`, Core i5-8200Y, 5 W, fanless, 7.8 GB RAM, Intel UHD 615) running a
custom 7.2.8 kernel with AppArmor and no SELinux. It boots to the full Googlebook desktop and the touchscreen works.
It is slow. This is a proof that it can be done, not a recommendation.

## What had to change, and what did not

| Problem on the slate | Cause | Fix | Needs a kernel rebuild? |
|---|---|---|---|
| Container goes RUNNING then STOPPED in a second, nothing on screen | the image uses GFNI, VAES, VPCLMULQDQ, AVX-VNNI and SHA-NI unconditionally; the CPU has none | [`kernel/gbos-vexemu.py`](../kernel/README.md): emulates them on the invalid-opcode trap | no (module) |
| Android has no working network | the kernel lacks the legacy iptables tables Android's `netd` uses | [`kernel/gbos-netfilter-legacy.py`](../kernel/README.md) | no (modules, one reboot) |
| Boot stops at the boot animation; `odsign` aborts in a loop | binder cannot fetch a security context on an AppArmor-only kernel | [`kernel/gbos-secctx.py`](../kernel/README.md) (or the AppArmor patch) | no (module) |
| Desktop is a bare wallpaper, no shelf, "Owner" user | the old stock-Waydroid `/data` was reused | `recipe/try.sh` now moves it aside ([fix 25](FIX-CATALOG.md)) | no |
| Touches ignored | the input bridge assumed a 1920x1080 panel | set `persist.gbos.display` to the real size ([HARDWARE](HARDWARE.md)) | no |
| Google sign-in: Next does nothing | the sign-in injector ran Google's main script a second time because the page is slow to start here | longer grace period for every script ([fix 26](FIX-CATALOG.md)) | no |

Everything above is a loadable module, a script or a setting. **No kernel was rebuilt and no kernel patch was needed.**
The two kernel features that are not modules (binder, AppArmor) were already in the running kernel.

## The order that worked

1. `installer/gbos-install.py check` on the machine. It reports the missing CPU features, the missing netfilter tables,
   and anything else, before you download or build anything.
2. Build the kernel add-ons against the tree (or headers) of the **running** kernel and install them. Each one
   verifies itself against that kernel's symbol checksums first and refuses to install on a mismatch:
   ```sh
   kernel/gbos-netfilter-legacy.py build --tree TREE --out OUT1 && sudo kernel/gbos-netfilter-legacy.py install --out OUT1   # then reboot
   kernel/gbos-vexemu.py           build --tree TREE --out OUT2 && sudo kernel/gbos-vexemu.py install --out OUT2
   kernel/gbos-secctx.py           build --tree TREE --out OUT3 && sudo kernel/gbos-secctx.py install --out OUT3
   ```
3. `recipe/try.sh` (it asks before changing anything and moves any previous Waydroid data aside, never deleting it).
4. **Be patient on the first boot.** About 10 minutes here: the screen is black while Android verifies every preinstalled
   app and compiles them, and every emulated instruction in that work costs a kernel trap. Later boots are faster.
5. Set the touch range once, from the display size Android reports (`adb shell wm size`; use the *Override* line if there is one).
   It is stored inside Android's data, so a fresh data directory needs it again:
   ```sh
   adb shell setprop persist.gbos.display 2901x1866
   adb shell 'rm -f /dev/input/event1[0-9]; setprop ctl.restart gb-inputbridge'   # the rm matters: see fix 24
   ```
6. Set the main user and sign in ([INSTALL, section 6](INSTALL.md)). Sign-in is slow to start on this CPU; give each page up to a minute, and see [fix 26](FIX-CATALOG.md) if Next does nothing.

## What was measured, and what each tuning step did

First boot to desktop took about 10 minutes. Trap rates are the module's `stats` counters; "kernel" is the `sy` share of CPU from `top`;
load is the 1-minute load average on 4 hardware threads (2 cores). These are single observations on one machine, taken while
Android was still doing first-run work (Play services, package verification, Play Protect), so read them as direction, not benchmarks.

| Step | What changed | Observed |
|---|---|---|
| 0. Plain emulation | every emulated instruction is a trap | about 1.1M traps/s while booting; `system_server` pinned a core hashing SHA-256 |
| 1. Run-ahead v1 | after an emulated instruction, interpret the plain vector code that follows in the same trap | `sha256rnds2` traps 370k/s to 79k/s; `sha256msg1/2` no longer trap; about 6M instructions/s interpreted |
| 2. Profile-driven fixes | a system-wide `perf` showed about 20% of all CPU in the kernel's hardened-usercopy checks (`check_stack_object`, `__check_object_size`) on every instruction fetch; also a shared atomic counter and eager loading of all 16 vector registers. Fixed: direct `unsafe_get_user` reads, a 64-byte instruction window, lazy register loads, per-CPU counters | kernel share of CPU about 80% to 43%; load 24 to 17; same workload class |
| 3. Run-ahead v2 | run-ahead now also executes the emulated instructions themselves (VAES, VPCLMULQDQ, SHA, GFNI, VNNI), adds `pxor/por/pand/pandn/paddq`, `vpshufb/vpalignr/vpshufd/vpunpck*`, and vector stores | `vaesenc` 220k/s to 48/s; `sha1msg1` 290k/s to 34/s; load 2.4, CPU 66% idle. Not a clean A/B: first-run background work was finishing at the same time |

What the profile of step 2 did and did not show about the host mitigations (PTI, MDS buffer clearing, IBRS are active on that kernel): the
kernel's trap entry/exit code (`error_entry`, `iret`) was only about 3% of samples, but how much of the trap cost the mitigations account for
cannot be read from that profile. `mitigations=off` was not tried; it would be an A/B test, and it trades away host security.

Every step is checked by `kernel/vexemu/test/test-qemu.sh`: the same results with run-ahead on and off for real SHA-256, SHA-1,
Adler-32 (AVX-VNNI), AES (VAES) and carry-less-multiply loops, plus 9,002 per-instruction checks against hardware-validated arithmetic.
The user's description of the slate after step 3: "not bad feeling" (no number; subjective).

### Frame times during use (after step 3)

Measured with `adb shell dumpsys gfxinfo <package>` after a reset, over about 30 seconds of the user opening Settings, scrolling,
opening the launcher and apps, and dragging a window. Display 2901x1866, rendered by Mesa on the Intel UHD 615 (not software).
CPU was 77% idle and CPU pressure low during this; the limit at this point is the GPU, not the emulation.

| App | Frames | Median | 90th pct | Janky | Median GPU time |
|---|---|---|---|---|---|
| Launcher | 245 | 15 ms | 38 ms | 21% | 9 ms |
| Settings | 145 | 19 ms | 34 ms | 23% | 13 ms |
| SystemUI (shelf, status bar, window chrome) | 61 | 30 ms | 117 ms | 46% | 15 ms |

(16.7 ms is one frame at 60 Hz.) SystemUI is the heaviest, and Android's window blur (`KawaseDualFilterV2`) is active.

Other facts: the clock sits near 1.7 GHz under load (5 W fanless part), so some lag is the hardware.

## What has been verified on this machine

Boot to the desktop; touch (taps, the Settings and launcher apps); Google account sign-in (the account registered in the real user);
the desktop's frame times during normal use (below). Everything else on the list that follows is untried.

## What has not been tried on this machine

Play Store, sound, Bluetooth, Wi-Fi presence, Phone Hub, the keyboard, stylus, rotation, and anything
GPU-accelerated beyond the plain desktop. Suspend is untested too: the container was found stopped once after the slate
had been on and off during the day, and the cause was not investigated. One machine is one data point.

## Reproducing it elsewhere

Expect to hit different things. The checks in step 1 and the self-verification in step 2 are there so that the failures
are loud and early rather than silent. If something here does not match what you see, the symptom index in
[FIX-CATALOG](FIX-CATALOG.md) is organized to find it, and new findings belong there.
