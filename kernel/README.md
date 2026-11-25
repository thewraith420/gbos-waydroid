# Legacy iptables for custom kernels

**Only needed if `gbos-install.py check` says the legacy iptables options are missing.** Distribution kernels have them.

Android's `netd` configures networking through the *legacy* iptables API (the filter, nat, mangle and raw tables), not
nftables. A trimmed custom kernel can have the base `ip_tables` module and none of the tables, and then Android boots
without a working network. There are two fixes:

| | Fix | Needs |
|---|---|---|
| a | rebuild the kernel with the missing options (`gbos-install.py kernel-fragment --base <your .config>` prints them) | your kernel build workflow; hours |
| b | **add the missing pieces as modules** with `gbos-netfilter-legacy.py` | the tree your running kernel was built in, its compiler, a reboot; minutes |

## Option b

```sh
# 1. build and self-verify (no root). The tree must be the very build you are running; --allow-other builds for another machine.
kernel/gbos-netfilter-legacy.py build --tree /path/to/built/kernel/tree --out ~/gbos/netfilter [--allow-other]

# 2. install on the machine that runs that kernel (root), then reboot
sudo kernel/gbos-netfilter-legacy.py install --out ~/gbos/netfilter

# 3. after the reboot
kernel/gbos-netfilter-legacy.py status
installer/gbos-install.py check        # the netfilter options should now be satisfied... by modules
```

`build` refuses to continue, and `install` refuses to install, unless all of this holds:

* the tree is the build that is running (release string **and** build stamp, `uname -v`);
* the kernel does not already have the switch, and `x_tables` is a loadable module (if it is built in, only option a works);
* every module's vermagic matches;
* every one of `x_tables`' existing exports keeps its exact symbol checksum (the rebuild may only *add* exports);
* every symbol the new modules import has the checksum the running kernel exports.

It rebuilds `x_tables` together with `ip_tables`, `ip6_tables` and the `iptable_*` / `ip6table_*` modules. The only
change to the kernel's own code is defining `CONFIG_NETFILTER_XTABLES_LEGACY` while compiling them; that switch compiles in
a few extra exported functions and changes no struct. Files go to `/lib/modules/<release>/updates/gbos-netfilter/`, which
`depmod` prefers over the original; **the original modules are never modified**.

Undo: `sudo kernel/gbos-netfilter-legacy.py uninstall`, then reboot.

## Things to know

* **Reboot needed.** `x_tables` is in use by the host firewall, so the new one is picked up at the next boot.
* **Taint.** The modules are unsigned. That works when signature enforcement is off (`CONFIG_MODULE_SIG_FORCE` unset, the usual
  custom-kernel setup) and marks the kernel as tainted. It will not work with Secure Boot or enforced signatures.
* **Compiler.** Kernels built with clang need clang and lld; use the same major version the kernel was built with (the tool says what it found).
* **Where this has been proven.** Built, verified and its install/uninstall mechanics tested for a 7.2.8 clang/full-LTO kernel.
  Booting a kernel on the new `x_tables` is the last step and is done by the person installing it; if the host's networking
  misbehaves after the reboot, uninstall from the console (or boot the previous kernel) and report what `status` shows.


---

**Not sure which of these your machine needs?** `installer/gbos-install.py check` has a "kernel add-ons" section that says, and
`installer/gbos-install.py addons --out <dir> --build` builds exactly those (the install commands are printed for you to run as root;
the graphical installer runs both from its check page). The build files default to `/lib/modules/$(uname -r)/build`; when they
are missing, `addons` names the headers package to install. The detection is covered by `tools/test-addons-plan.sh`.

## Secure Boot

All three add-ons are **unsigned** modules. A kernel refuses them when:

* it is in **lockdown** (`cat /sys/kernel/security/lockdown` shows `[integrity]` or `[confidentiality]`). Debian, Ubuntu and Fedora
  switch lockdown on automatically when the machine boots with Secure Boot;
* it enforces module signatures (`CONFIG_MODULE_SIG_FORCE=y`, or `module.sig_enforce=1` on the kernel command line).

`check`, `addons --json` and the graphical installer detect both and say so before building anything. The ways around it, from
simplest: turn Secure Boot off in the firmware settings; or sign the modules with your own key and enroll it (`mokutil --import`,
a reboot and the blue MOK screen), the way DKMS signs third-party drivers. GBOS does not automate signing yet.

**Tested builds:** `vexemu` and `secctx` build and pass their self-checks against Debian 13's 6.12 headers and a 7.3 custom kernel.
`vexemu` also passes its full emulation suite (`kernel/vexemu/test/test-qemu.sh`: 9002 instruction cases, 18 addressing cases,
run-ahead on and off, each first confirmed to fault without the module) under QEMU as a Skylake CPU, on Debian's stock
6.12.115 kernel and on 7.3.5. Kernels before 6.15 keep the task's FPU state in a different place; the module handles both.

---

# Running on a CPU that lacks GFNI / VAES / VPCLMULQDQ / AVX-VNNI / SHA-NI: `gbos-vexemu.py`

**Only needed if `gbos-install.py check` says `cpu features ... FAIL`.** The Googlebook userland uses those instructions in its very
first processes, so on an older CPU (for example an 8th-gen Intel) the container goes RUNNING, then STOPPED within a second.

`gbos_vexemu` is a loadable module that catches the invalid-opcode trap for 64-bit user code, emulates the instruction, and
resumes. **No kernel patch and no kernel rebuild**: it hooks the kernel's die-notifier chain using exported symbols only.

```sh
# 1. build and self-verify (no root; downloads the pinned patch and checks its sha256). Tree = the one your running kernel was built in.
kernel/gbos-vexemu.py build --tree /path/to/built/kernel/tree --out ~/gbos/vexemu [--allow-other]

# 2. on the machine that runs that kernel (root): installs into updates/, depmod, loads it NOW (no reboot) and at every boot
sudo kernel/gbos-vexemu.py install --out ~/gbos/vexemu

kernel/gbos-vexemu.py status           # shows which instructions were emulated so far
sudo kernel/gbos-vexemu.py uninstall
```

What was checked, and what was not:

* **Tested** (`kernel/vexemu/test/test-qemu.sh KERNEL_IMAGE MODULE.ko`): the 7.2.8 kernel image it was built for, booted in QEMU software
  emulation as a CPU without the instructions. Without the module the test program dies with SIGILL; with it 9,002 instruction checks
  against the hardware-validated arithmetic and 18 addressing/page-residency checks pass (all 21 instruction forms, SIB, RIP-relative,
  xmm/ymm 8-15, operand pages not yet mapped, other registers untouched).
* **Real hardware:** a Pixel Slate (Core i5-8200Y, Amber Lake: no GFNI, VAES, VPCLMULQDQ, AVX-VNNI or SHA-NI) booted GBOS to the Googlebook desktop with
  this module plus `gbos-secctx.py` below, on a stock-config 7.2.8 kernel that was not rebuilt. The first boot took roughly 10 minutes (package verification and
  dex2oat dominate); later boots and everyday speed have not been measured yet. One machine, one kernel: treat other CPUs and kernels as untested.
* **Slow, and tuned.** Every emulated instruction is a trap into the kernel (microseconds). To keep hot loops usable, after each emulated
  instruction the module keeps interpreting the code that follows in the same trap: vector loads and stores, the common bitwise/shuffle/add
  ops, `lea`, nops, and the emulated instructions themselves; it stops at anything else (a branch, an integer/flag-setting instruction, an unknown
  opcode, an unmapped or misaligned operand), where the CPU takes over, so stopping is always correct. A SHA-256 block costs about one trap
  instead of ~56. It is tested for identical results on and off (`ratest`: SHA-256, SHA-1, Adler-32, AES, carry-less multiply), and
  `echo 0 > /sys/module/gbos_vexemu/parameters/runahead` switches it off. A first boot is still very slow (Android hashes and verifies every
  preinstalled app); the measured effect of each tuning step is in [UNSUPPORTED-HARDWARE](../docs/UNSUPPORTED-HARDWARE.md).
* **Limits.** A memory operand the kernel cannot read gives SIGILL (hardware would give SIGSEGV). EVEX/AVX-512 forms are not emulated.
  Unsigned module: taints the kernel; refused under Secure Boot / enforced signatures. Needs `CONFIG_PREEMPT_RCU` (checked at build).
* **Licensing.** `vexemu/gbos_vexemu.c` is GPL-2.0 (it must be: it uses GPL-only kernel symbols). The arithmetic is *not* in this repository: it is cut
  out of the pinned third-party patch (project-churro `vex-emulate.patch`, GPL-2.0) at build time, after the sha256 of both the patch
  and the extracted text are checked.
* **Kill switch.** `echo 0 | sudo tee /sys/module/gbos_vexemu/parameters/enabled` turns emulation off without unloading.


---

# Binder security context on an AppArmor-only kernel: `gbos-secctx.py`

**Needed if boot stops at the boot animation / a black screen and `waydroid logcat` shows `odsign` aborting** with
`BAD: Incorrect usage of binder APIs ... BR_FAILED_REPLY ... error: -22`, and the kernel log has `binder: ... failed to get security context`.

Android 17's `keystore2` asks binder to attach the caller's security context to every call. Binder gets it from the LSM, but no
AppArmor version implements the hook that provides the caller's label, so on a kernel whose only LSM is AppArmor (Ubuntu, Debian,
most custom kernels without SELinux) the lookup always fails with -EINVAL. `odsign` is the first service to hit it and aborts in a loop;
`zygote` never starts. Earlier Android versions in Waydroid did not exercise this.

Two fixes, same effect:

| | Fix | Needs |
|---|---|---|
| a | the 3-hunk AppArmor patch `kernel/patches/apparmor-cred-getsecid.patch` (adds the missing `cred_getsecid` hook) | a kernel rebuild |
| b | **`gbos-secctx.py`**: a module with a kprobe that answers the one failing lookup (secid 0) with the profile name `lxc-waydroid`; every other lookup runs the original code | the tree your kernel was built in; no reboot |

```sh
kernel/gbos-secctx.py build --tree /path/to/built/kernel/tree --out ~/gbos/secctx [--allow-other]
sudo kernel/gbos-secctx.py install --out ~/gbos/secctx       # loads now and at every boot
# then restart the container so the services retry:   waydroid session stop; sudo systemctl restart waydroid-container
kernel/gbos-secctx.py status        # "lookups answered so far" should be above 0
sudo kernel/gbos-secctx.py uninstall
```

It refuses to install on a kernel that already has the patch. Needs `CONFIG_KPROBES`; same unsigned-module limits as above.
Found while running GBOS on a second machine whose kernel pre-dated the patch (FIX-CATALOG 23). The kprobe has an empty
`post_handler` on purpose: kprobes without one get "optimized" into a jump, and an optimized probe ignores a handler's change of the
instruction pointer (the first version of this module silently did nothing for that reason).
