#!/bin/bash
# One-shot Googlebook-on-Waydroid attempt: rebuild system.img, (re)start Waydroid,
# wait, then collect logs into this folder for Claude to read.
#   sudo ./try.sh            rebuild + boot + capture
#   sudo NOBUILD=1 ./try.sh  skip the rebuild
#   sudo REINIT=1 ./try.sh   system.img is already built: install it and run `waydroid init` (no rebuild)
set -uo pipefail
cd "$(dirname "$0")"
[ "$(id -u)" = 0 ] || { echo "run with sudo"; exit 1; }
U="${SUDO_USER:?run with sudo from your normal user}"
W="$(pwd)"

# Preflight: two machine-specific conditions that otherwise show up only as a silent failure after a long build.
# (Both have a loadable-module fix that needs no kernel rebuild: `installer/gbos-install.py addons`, docs/UNSUPPORTED-HARDWARE.md.)
cpu_missing=""
for f in gfni vaes vpclmulqdq avx_vnni sha_ni; do grep -m1 '^flags' /proc/cpuinfo | grep -qw "$f" || cpu_missing="$cpu_missing $f"; done
if [ -n "$cpu_missing" ] && [ ! -d /sys/module/gbos_vexemu ] && [ "${GBOS_IGNORE_CPU:-}" != 1 ]; then
  echo "This CPU lacks:$cpu_missing"
  echo "The Googlebook image uses them in its first processes; Android would die with SIGILL within a second and show nothing."
  echo "Load the instruction-emulation module first (builds without a kernel rebuild):"
  echo "    installer/gbos-install.py addons --tree <your kernel's build tree> --out ~/gbos/addons --build"
  echo "    sudo kernel/gbos-vexemu.py install --out ~/gbos/addons/vexemu"
  echo "(set GBOS_IGNORE_CPU=1 to go on anyway)"
  exit 1
fi
if grep -qw apparmor /sys/kernel/security/lsm 2>/dev/null && ! grep -qw selinux /sys/kernel/security/lsm 2>/dev/null \
   && [ ! -d /sys/module/gbos_secctx ] && ! grep -q ' apparmor_cred_getsecid$' /proc/kallsyms 2>/dev/null; then
  echo "WARNING: AppArmor is the only security module and this kernel lacks the cred_getsecid fix, so Android's odsign"
  echo "will abort in a loop and boot will stop at the boot animation. Fix without a kernel rebuild:"
  echo "    sudo kernel/gbos-secctx.py install --out <dir>     (build it with: installer/gbos-install.py addons ... --build)"
  echo "Continuing; see docs/FIX-CATALOG.md entry 23."
fi

# A rebuild runs `waydroid init -f` (which deletes the writable overlay) and moves your old Android data aside (kept, renamed).
# Make that a decision, not an accident. Set GBOS_YES=1 for unattended use.
if [ -z "${NOBUILD:-}" ]; then
  echo "This rebuilds Waydroid from scratch: 'waydroid init -f' deletes its writable overlay, and the old"
  echo "Android's data (Google accounts, installed apps and their data) is moved aside to data.before-gbos-<time>."
  echo "Nothing is deleted, but the new Android starts clean. For a proper backup first, stop Waydroid and run:"
  echo "    sudo ./gbos-backup.py backup --out /some/disk/folder --with overlay,data --user $U"
  if [ "${GBOS_YES:-}" != 1 ]; then
    printf "Type 'reset' to go on, anything else to stop: "
    read -r ans
    [ "$ans" = reset ] || { echo "stopped; nothing was changed."; exit 1; }
  fi
  # the small part (container config, properties, host rules and units) is always saved, so a failed run can be undone
  if [ -x ./gbos-backup.py ]; then
    ./gbos-backup.py backup --out "$W/backups" --user "$U" || echo "WARNING: the configuration backup failed; continuing"
  fi
fi

# Current experiment: WayDroid-ATV Android 16 init + waydroid-preinit (creates the container's
# /dev nodes incl. loop-control for apexd) + init.waydroid.rc, with a private Android 16 library
# set at /system/lib64/wdinit via RUNPATH; bionic libc/libm/libdl/libdl_android stay Googlebook's.
command -v patchelf >/dev/null || { echo "error: patchelf is missing. Run installer/gbos-install.py check: it prints the command that installs every missing package." >&2; exit 1; }
DONOR="$W/donor-a16"            # built by ./prep-donor.sh (BINS list lives there)
STAGE="$W/stage-wdinit"
rm -rf "$STAGE"; mkdir -p "$STAGE/lib" "$STAGE/bin" "$STAGE/sysext-bin"
cp "$DONOR"/bin/* "$STAGE/bin/"
cp "$DONOR"/sysext-bin/* "$STAGE/sysext-bin/"
cp "$DONOR"/lib/*.so "$STAGE/lib/"
for f in "$STAGE"/bin/* "$STAGE"/sysext-bin/* "$STAGE"/lib/*.so; do
  # static binaries have no dynamic section to patch
  readelf -d "$f" 2>/dev/null | grep -q NEEDED || continue
  patchelf --set-rpath /system/lib64/wdinit "$f" || exit 1
done
export INIT_FROM="$STAGE/bin/init"
export INIT_LIBDIR="$STAGE/lib"
# Waydroid's init.waydroid.rc overrides vendor.audio-hal with the 32-bit android.hardware.audio.service
# plus "onrestart restart audioserver"; being in /system it wins over the vendor rc, so its exec failure
# SIGKILLs audioserver every cycle. Audio runs in-process (passthrough manifest), so swap that block for
# a disabled no-op.
awk '
  /^service vendor\.audio-hal /{print "service vendor.audio-hal /system/bin/true\n    override\n    class hal\n    user root\n    oneshot\n    disabled"; skip=1; next}
  skip && /^$/{skip=0}
  !skip{print}
' "$DONOR/init.waydroid.rc" > "$STAGE/init.waydroid.rc"
grep -q "^service vendor.audio-hal /system/bin/true" "$STAGE/init.waydroid.rc" || { echo "init.waydroid.rc audio patch failed"; exit 1; }
EXTRA_FILES="$STAGE/init.waydroid.rc:system/etc/init/init.waydroid.rc:644 $W/zzz-googlebook-waydroid.rc:system_ext/etc/init/zzz-googlebook-waydroid.rc:644 $W/netbpfload.rc:system/etc/init/netbpfload.rc:644"
for b in "$STAGE"/bin/*; do
  [ "$(basename "$b")" = init ] && continue
  EXTRA_FILES="$EXTRA_FILES $b:system/bin/$(basename "$b"):755"
done
# system_ext pieces (installed at the real path, never through the absolute /system/... symlink)
EXTRA_FILES="$EXTRA_FILES $STAGE/sysext-bin/hwservicemanager:system_ext/bin/hwservicemanager:755"
EXTRA_FILES="$EXTRA_FILES $DONOR/hwservicemanager.rc:system_ext/etc/init/hwservicemanager.rc:644"
EXTRA_FILES="$EXTRA_FILES $W/netd-gb.o:system/etc/bpf-gb/netd.o:644"
# libservice-connectivity.so patched so the clat BPF SELinux-context check is non-fatal on this
# no-SELinux (AppArmor) host; bind-mounted over the APEX copy in netbpfload.rc. See patch-clat-selinux.py.
EXTRA_FILES="$EXTRA_FILES $W/libservice-connectivity-gb.so:system/etc/bpf-gb/libservice-connectivity.so:644"
# libbluetooth_jni.so with its wake-alarm timerfds (CLOCK_BOOTTIME_ALARM) changed to plain CLOCK_BOOTTIME
# so the BT stack doesn't abort on timerfd_create EPERM (no CAP_WAKE_ALARM in a container) and crash-loop
# the persistent BT app into a RescueParty reboot. Bind-mounted over the APEX copy in netbpfload.rc.
EXTRA_FILES="$EXTRA_FILES $W/libbluetooth_jni-gb.so:system/etc/bpf-gb/libbluetooth_jni.so:644"
# Drop the android.hardware.bluetooth system feature so BluetoothManagerService never enables the
# adapter. BT can't work in the container (no BT HAL / wake-alarm timers), and its stack init aborts
# (LE audio etc.); as a persistent app that crash-loops into a RescueParty reboot. No feature -> no
# enable -> no stack init -> no crash. (The timerfd patch above also helps but the stack hits further
# aborts; removing the feature stops it at the source.)
EXTRA_FILES="$EXTRA_FILES $W/features-gb.xml:system/etc/permissions/features.xml:644"
# Keeps system_server/surfaceflinger OOM-immune (-1000) so the host OOM killer can't reap the UI under
# memory pressure. Run by the gb-oomguard service in zzz rc. See gb-oomguard.sh.
EXTRA_FILES="$EXTRA_FILES $W/gb-oomguard.sh:system/bin/gb-oomguard.sh:755"
# First-boot provisioning (mark set-up + disable desktop setup wizard -> boot to launcher). See zzz rc.
EXTRA_FILES="$EXTRA_FILES $W/gb-firstboot.sh:system/bin/gb-firstboot.sh:755"
# gb-storagemount: daemon that keeps per-user emulated (sdcard) storage mounted (vold never auto-mounts
# it in the container, and the active user's volume appears only after login). See gb-storagemount.sh.
EXTRA_FILES="$EXTRA_FILES $W/gb-storagemount.sh:system/bin/gb-storagemount.sh:755"
# input-bridge: Waydroid feeds host input via /dev/input/wl_*_events, which stock Googlebook EventHub
# can't read (not evdev). This native daemon copies those streams into real uinput devices EventHub
# does read -> keyboard/mouse/touch. See input-bridge.c + the gb-inputbridge service in zzz rc.
EXTRA_FILES="$EXTRA_FILES $W/input-bridge:system/bin/input-bridge:755"
# IDC for the bridge's virtual keyboard: declare it EXTERNAL so Android doesn't treat it as an internal
# keyboard and DISABLE it on the (host) lid-switch state. Without this the keyboard flaps enabled/disabled
# and keystrokes never dispatch. Named for its uinput identity (vendor 0x1209, product 0xab00).
EXTRA_FILES="$EXTRA_FILES $W/Vendor_1209_Product_ab00.idc:system/usr/idc/Vendor_1209_Product_ab00.idc:644"
# cgroup v2 only (Googlebook's also asks for v1 blkio/cpu/cpuset mounts, impossible in the container)
EXTRA_FILES="$EXTRA_FILES $W/cgroups-v2.json:system/etc/cgroups.json:644"
EXTRA_FILES="$EXTRA_FILES $W/libselinux-gb.so:system/lib64/libselinux.so:644"
# libandroid_runtime.so with android.os.Debug.isVmapStack() patched to return true instead of
# LOG(FATAL) "Kernel configs could not be fetched" when /proc/config.gz is absent (a kernel built without
# CONFIG_IKCONFIG_PROC). Otherwise system_server aborts ~14s after the first UI draws. x86_64 does use
# vmap stacks, so returning true is accurate. See the isVmapStack patch (offset 0x136230).
EXTRA_FILES="$EXTRA_FILES $W/libandroid_runtime-gb.so:system/lib64/libandroid_runtime.so:644"
# libmeminfo.so with ReadPerProcessGpuMem() patched to return an empty result. StatsPullAtomService
# periodically pulls the GPU_MEM atom -> KernelAllocationStats_getGpuAllocations (libandroid_runtime) ->
# ReadPerProcessGpuMem (here) -> BpfMapRO::abortOnMismatch -> abort(), because the gpu_mem BPF map isn't
# loaded in the container. That SIGABRTs system_server minutes after the desktop appears. Same gpu_mem
# family as the isVmapStack/getGpuTotalUsageKb/getGpuPrivateMemoryKb stubs in libandroid_runtime-gb.so,
# but reached through a third path inside libmeminfo. See patch-libmeminfo-gpumem.py (offset 0x276d0).
EXTRA_FILES="$EXTRA_FILES $W/libmeminfo-gb.so:system/lib64/libmeminfo.so:644"
# libalarm_jni.so with CLOCK_REALTIME_ALARM(8)/CLOCK_BOOTTIME_ALARM(9) changed to CLOCK_REALTIME(0)/
# CLOCK_BOOTTIME(7) so AlarmManagerService's native timerfd driver opens in the container instead of
# failing with EPERM and falling back to the buggy Java AlarmHandler (which NPEs in
# getAlarmOperationBundle when an OnAlarmListener alarm fires). See patch-alarm-jni.py.
EXTRA_FILES="$EXTRA_FILES $W/libalarm_jni-gb.so:system/lib64/libalarm_jni.so:644"
# Native stub (libbinder_ndk) that registers vendor.google.desktop.vpd_executor.IVpdExecutor/default so
# DesktopRepairModeService.onBootPhase() stops blocking system_server's main thread on a VPD HAL that
# can't exist in the container. Built from vpd-stub.c with the Android NDK. See zzz rc for the service.
EXTRA_FILES="$EXTRA_FILES $W/vpd-stub:system/bin/vpd-stub:755"
# vold_prepare_subdirs patched so a missing SELinux file context (lgetfilecon ENODATA on this no-SELinux
# host) is non-fatal; otherwise HSUM user-data prep fails and system_server tries to reboot-to-recovery
# and dies. See patch-vold-subdirs.py.
EXTRA_FILES="$EXTRA_FILES $W/vold_prepare_subdirs-gb:system/bin/vold_prepare_subdirs:755"
# Waydroid task HAL (IWaydroidTask) — a Waydroid SYSTEM service missing from Googlebook; the
# Waydroid vendor hwcomposer blocks waiting for it. Donor from ATV's Android 16 system.
EXTRA_FILES="$EXTRA_FILES $W/donor-task/vendor.waydroid.task@1.0-service:system/bin/hw/vendor.waydroid.task@1.0-service:755"
EXTRA_FILES="$EXTRA_FILES $W/donor-task/vendor.waydroid.task@1.0.so:system/lib64/vendor.waydroid.task@1.0.so:644"
EXTRA_FILES="$EXTRA_FILES $W/donor-task/vendor.waydroid.task@1.0-service.rc:system/etc/init/vendor.waydroid.task@1.0-service.rc:644"
EXTRA_FILES="$EXTRA_FILES $W/donor-task/vendor.waydroid.display@1.0.so:system/lib64/vendor.waydroid.display@1.0.so:644"
# bpfloader patched so userdebug ringbuf test failure does not panic BpfLoader-rs. See patch-bpfloader.py.
EXTRA_FILES="$EXTRA_FILES $W/bpfloader-gb:system/bin/bpfloader:755"
# Test BPF programs required on userdebug builds by BpfLoader-rs (missing from Googlebook user image; from ATV16 donor)
EXTRA_FILES="$EXTRA_FILES $W/atv16/root/system/etc/bpf/bpfRingbufProg.bpf:system/etc/bpf/bpfRingbufProg.bpf:644"
EXTRA_FILES="$EXTRA_FILES $W/atv16/root/system/etc/bpf/memevents/bpfMemEventsTest.bpf:system/etc/bpf/memevents/bpfMemEventsTest.bpf:644"
export EXTRA_FILES

sudo -u "$U" waydroid session stop >/dev/null 2>&1
systemctl stop waydroid-container

# Host udev rule: the in-container input-bridge creates uinput devices named waydroid_* (keyboard/mouse/
# touch). uinput isn't namespaced, so those event nodes also surface on the host and the host's libinput/
# GNOME would process them (double input, overview-on-motion), and systemd-logind would watch waydroid_lid
# as a lid switch. Make host libinput ignore them and keep logind off them (see the rule file for why it is
# 65-, not 99-); the container's EventHub reads them directly and is unaffected.
rm -f /etc/udev/rules.d/99-waydroid-input-ignore.rules   # pre-0.1.4 name; its TAG-= could not untag
install -D -m 644 "$W/65-waydroid-input-ignore.rules" /etc/udev/rules.d/65-waydroid-input-ignore.rules
udevadm control --reload-rules 2>/dev/null

# netd (Android 17) uses legacy iptables; the container shares the host kernel and can't load
# modules itself, so load the legacy ip_tables modules on the host first and persist them in
# /etc/modules-load.d so Waydroid still boots after a host reboot without running try.sh.
cat > /etc/modules-load.d/waydroid-iptables.conf <<'MODS'
ip_tables
iptable_filter
iptable_nat
iptable_mangle
iptable_raw
ip6_tables
ip6table_filter
ip6table_nat
ip6table_mangle
ip6table_raw
MODS
for m in ip_tables iptable_filter iptable_nat iptable_mangle iptable_raw \
         ip6_tables ip6table_filter ip6table_nat ip6table_mangle ip6table_raw; do
  modprobe $m 2>/dev/null
done
rm -rf /var/lib/waydroid/overlay/system/usr/idc /var/lib/waydroid/overlay/system/usr/idc_backup 2>/dev/null || true
install -D -m 644 "$W/libalarm_jni-gb.so" /var/lib/waydroid/overlay/system/lib64/libalarm_jni.so
install -D -m 755 "$W/input-bridge" /var/lib/waydroid/overlay/system/bin/input-bridge
install -D -m 755 "$W/gb-firstboot.sh" /var/lib/waydroid/overlay/system/bin/gb-firstboot.sh
install -D -m 755 "$W/unlock-signin" /var/lib/waydroid/overlay/system/bin/unlock-signin
install -D -m 755 "$W/eval-js" /var/lib/waydroid/overlay/system/bin/eval-js
install -D -m 644 "$W/zzz-googlebook-waydroid.rc" /var/lib/waydroid/overlay/system_ext/etc/init/zzz-googlebook-waydroid.rc

# Vendor: Waydroid's image advertises 32-bit x86 (abilist32=x86, ro.zygote=zygote64_32), but the
# Googlebook system is 64-bit only. Patch a copy (vendor-gb.img) to 64-bit only; original untouched.
patch_vendor() {
  local img="$W/vendor-gb.img" tmp="$W/stage-vendor"
  rm -rf "$tmp"; mkdir -p "$tmp"
  cp "$W/vendor.img" "$img"
  # The stock vendor image is packed with zero free blocks, so any edit fails with ENOSPC. Grow the
  # copy by 32 MiB (file first, then the ext4 fs to fill it) before editing. Waydroid mounts vendor
  # read-only, so the extra slack is harmless.
  truncate -s +32M "$img"
  e2fsck -fy "$img" >/dev/null 2>&1
  resize2fs "$img" >/dev/null 2>&1 || { echo "vendor image resize failed"; return 1; }
  # Edit three files in the vendor image. debugfs proved unreliable for multiple file rewrites in one
  # ext4 image (the 3rd write silently kept the old content, whether in one session or three). try.sh
  # runs as root, so loop-mount the image and edit with normal tools instead. Each file is rewritten
  # in place via `cat tmp > file` / here-doc / sed-to-temp-then-cat-back, which keeps the original
  # inode, mode and security.selinux xattr (the host kernel has no SELinux to set labels with anyway,
  # and the container runs with SELinux absent, so the labels are moot).
  local mnt; mnt="$(mktemp -d)"
  mount -o loop "$img" "$mnt" || { echo "vendor loop mount failed"; rmdir "$mnt"; return 1; }

  # 1) build.prop: force 64-bit-only.
  sed -e 's/^ro.vendor.product.cpu.abilist=.*/ro.vendor.product.cpu.abilist=x86_64,arm64-v8a/' \
      -e 's/^ro.vendor.product.cpu.abilist32=.*/ro.vendor.product.cpu.abilist32=/' \
      -e 's/^ro.zygote=.*/ro.zygote=zygote64/' "$mnt/build.prop" > "$tmp/build.prop"
  cat "$tmp/build.prop" > "$mnt/build.prop"

  # 1b) ODM build.prop: this is the real source of the lingering 32-bit x86 ABI. ODM has the highest
  # property precedence (odm > vendor > system), so its ro.odm.product.cpu.abilist32=x86 wins and makes
  # ro.product.cpu.abilist32=x86. Then ZygoteProcess.bootCompleted() tries to notify an x86 (32-bit)
  # zygote that doesn't exist on this 64-bit-only system -> "Unsupported zygote ABI: x86" FATAL right at
  # "Boot is finished". Strip x86 here too.
  if [ -f "$mnt/odm/etc/build.prop" ]; then
    sed -e 's/^ro.odm.product.cpu.abilist=.*/ro.odm.product.cpu.abilist=x86_64,arm64-v8a/' \
        -e 's/^ro.odm.product.cpu.abilist32=.*/ro.odm.product.cpu.abilist32=/' \
        "$mnt/odm/etc/build.prop" > "$tmp/odm.prop"
    cat "$tmp/odm.prop" > "$mnt/odm/etc/build.prop"
  fi

  # 2) VINTF manifest: the vendor's HIDL audio@7.1 service (/vendor/bin/hw/android.hardware.audio.service)
  # is a 32-bit i386 binary with no 64-bit build, so it can't exec on this 64-bit-only system. With the
  # manifest declaring it HWBINDER, audioserver blocks in waitForService() for that dead service and
  # never registers AudioFlinger -> AudioService.<init> hangs 65s in muteMicrophone() -> Watchdog kills
  # system_server. (Deleting the entry instead makes AudioFlinger::onFirstRef() null-deref: audioserver
  # needs *some* HAL factory.) Rewrite audio + audio.effect to PASSTHROUGH so audioserver dlopens the
  # 64-bit impls the vendor DOES ship (/vendor/lib64/hw/android.hardware.audio@7.1-impl.so ->
  # audio.primary.waydroid.so) in its own process - no 32-bit service binary needed.
  awk '
    /<hal /{inhal=1; blk=$0 ORS; isaudio=0; next}
    inhal{
      blk=blk $0 ORS
      if($0 ~ /android\.hardware\.audio/) isaudio=1
      if($0 ~ /<\/hal>/){inhal=0;
        if(isaudio) gsub(/<transport>hwbinder<\/transport>/, "<transport arch=\"32+64\">passthrough</transport>", blk)
        printf "%s", blk}
      next
    }
    {print}
  ' "$mnt/etc/vintf/manifest.xml" > "$tmp/manifest-audiopt.xml"
  cat "$tmp/manifest-audiopt.xml" > "$mnt/etc/vintf/manifest.xml"

  # 3) android.hardware.audio.service.rc: the stock vendor.audio-hal has "onrestart restart audioserver",
  # so every time it fails exec (status 127) init SIGKILLs audioserver before it finishes init -> the
  # same muteMicrophone watchdog kill. Audio now runs in-process (passthrough), so make it a disabled
  # no-op with no onrestart line.
  cat > "$mnt/etc/init/android.hardware.audio.service.rc" <<'RC'
# Rewritten by try.sh: 32-bit android.hardware.audio.service can't exec on this 64-bit-only system.
# Audio runs in-process via the passthrough HIDL HAL (manifest rewritten to passthrough). Keep this
# service as a disabled no-op WITHOUT "onrestart restart audioserver" (that line SIGKILLed audioserver
# on every exec failure and caused the crash loop).
service vendor.audio-hal /system/bin/true
    class hal
    user root
    oneshot
    disabled
RC

  # 4) hwcomposer null-buffer crash: Waydroid's composer@2.1 SIGSEGVs in get_buffer_metadata_cros when a
  # layer has no backing buffer (hit during scroll/cursor compositing) -> the whole container reboots.
  # Patch in a null check so it returns empty metadata instead of dereferencing null. The python script
  # verifies the exact bytes before patching, so it is a no-op if the binary ever differs. See
  # patch-hwcomposer-nullbuf.py (offset 0x38820).
  if [ -f "$mnt/lib64/hw/hwcomposer.waydroid.so" ]; then
    python3 "$W/patch-hwcomposer-nullbuf.py" "$mnt/lib64/hw/hwcomposer.waydroid.so" || { echo "hwcomposer patch failed"; }
    # Second null-deref in the same buffer-less-layer path: create_buffer_cros+12 reads [null+0x5c].
    # disable_hwc_overlays=1 does NOT prevent it (confirmed: still crashes intermittently during webview
    # compositing -> composer SIGSEGV -> SurfaceFlinger restart = "crash to boot logo"). See
    # patch-hwcomposer-createbuf.py (offset 0x38c20).
    python3 "$W/patch-hwcomposer-createbuf.py" "$mnt/lib64/hw/hwcomposer.waydroid.so" || { echo "hwcomposer createbuf patch failed"; }
  else
    echo "WARN: hwcomposer.waydroid.so not found in vendor image; skipping crash patch"
  fi

  sync
  umount "$mnt" 2>/dev/null || umount -l "$mnt"
  rmdir "$mnt" 2>/dev/null

  # Verify (read-only).
  /sbin/debugfs -R "cat /build.prop" "$img" 2>/dev/null | grep -qE "^ro.zygote=zygote64$" || { echo "vendor build.prop patch failed"; return 1; }
  /sbin/debugfs -R "cat /etc/vintf/manifest.xml" "$img" 2>/dev/null | grep -A2 "android.hardware.audio<" | grep -q "passthrough" || { echo "manifest audio passthrough rewrite failed"; return 1; }
  # Check for the neutralized service line (not a bare "onrestart" — the explanatory comment mentions it).
  /sbin/debugfs -R "cat /etc/init/android.hardware.audio.service.rc" "$img" 2>/dev/null | grep -q "vendor.audio-hal /system/bin/true" || { echo "audio hal rc neutralize failed"; return 1; }

  mkdir -p /etc/waydroid-extra/images            # does not exist on a machine that never used extra images
  cp "$img" /etc/waydroid-extra/images/vendor.img
}

# Container tweak: Android 17's netbpfload treats the failed write to the (read-only, by
# design) /proc/sys/kernel/unprivileged_bpf_disabled as fatal and reboots. Bind a dummy file
# over that one path so the write lands in a file, never in the host's real sysctl.
lxc_tweaks() {
  local cfg=/var/lib/waydroid/lxc/waydroid/config fake=/var/lib/waydroid/fake-sysctl
  mkdir -p "$fake"
  echo 2 > "$fake/unprivileged_bpf_disabled"
  grep -q "fake-sysctl/unprivileged_bpf_disabled" "$cfg" || \
    echo "lxc.mount.entry = $fake/unprivileged_bpf_disabled proc/sys/kernel/unprivileged_bpf_disabled none bind,create=file 0 0" >> "$cfg"
}

# `waydroid init -f` does NOT touch the user's Android data (~/.local/share/waydroid/data): that directory belongs to the
# user, not to /var/lib/waydroid. Booting the new Android on top of an older Android's data keeps that data's user layout
# (a full "Owner" user 0 from stock Waydroid), and Googlebook needs the headless system user 0 + real user 10 that Android
# only creates on a fresh /data. So move the old data aside (renamed, never deleted) and let the first boot start clean.
set_aside_data() {
  local d; d="$(getent passwd "$U" | cut -d: -f6)/.local/share/waydroid/data"
  [ -d "$d" ] || return 0
  sudo -u "$U" waydroid session stop >/dev/null 2>&1
  waydroid container stop >/dev/null 2>&1
  local keep="$d.before-gbos-$(date +%Y%m%d-%H%M%S)"
  mv "$d" "$keep" && echo "old Android data kept at: $keep (delete it when you are sure you do not need it)"
}

if [ -z "${NOBUILD:-}" ]; then
  ./build-system-img.sh || exit 1
  mkdir -p /etc/waydroid-extra/images
  cp system.img /etc/waydroid-extra/images/
  patch_vendor || { echo "vendor patch failed"; exit 1; }
  # -i is REQUIRED: without it `waydroid init` takes the images from /var/lib/waydroid/images, i.e. the stock images
  # of a machine that already ran Waydroid, and silently ignores the ones just copied to waydroid-extra. (A machine
  # whose waydroid.cfg already pointed at waydroid-extra, like the one this was developed on, hides the bug.)
  set_aside_data
  waydroid init -f -i /etc/waydroid-extra/images
  # Waydroid injects waydroid_base.prop/waydroid.prop as ro.* props at container init, BEFORE
  # /system/build.prop is read, so ro.debuggable there wins (ro.* is write-once). `waydroid init -f`
  # regenerates these with ro.debuggable=0, so force it to 1 here (needed so WebView reads the
  # --single-process flags file gb-firstboot writes; see build-system-img.sh).
  for p in /var/lib/waydroid/waydroid_base.prop /var/lib/waydroid/waydroid.prop; do
    [ -f "$p" ] || continue
    if grep -q '^ro.debuggable=' "$p"; then sed -i 's/^ro.debuggable=.*/ro.debuggable=1/' "$p"
    else printf 'ro.debuggable=1\n' >> "$p"; fi
    if grep -q '^ro.build.type=' "$p"; then sed -i 's/^ro.build.type=.*/ro.build.type=userdebug/' "$p"
    else printf 'ro.build.type=userdebug\n' >> "$p"; fi
  done
elif [ -n "${REINIT:-}" ]; then
  # the image is already built (an earlier run got as far as build-system-img.sh): install it and initialise Waydroid
  mkdir -p /etc/waydroid-extra/images
  cp system.img /etc/waydroid-extra/images/
  patch_vendor || { echo "vendor patch failed"; exit 1; }
  set_aside_data
  waydroid init -f -i /etc/waydroid-extra/images
elif [ -n "${PATCH_VENDOR:-}" ]; then
  patch_vendor || { echo "vendor patch failed"; exit 1; }
fi
for p in /var/lib/waydroid/waydroid_base.prop /var/lib/waydroid/waydroid.prop; do
  [ -f "$p" ] || continue
  if grep -q '^ro.debuggable=' "$p"; then sed -i 's/^ro.debuggable=.*/ro.debuggable=1/' "$p"
  else printf 'ro.debuggable=1\n' >> "$p"; fi
  if grep -q '^ro.build.type=' "$p"; then sed -i 's/^ro.build.type=.*/ro.build.type=userdebug/' "$p"
  else printf 'ro.build.type=userdebug\n' >> "$p"; fi
  # Codec2 on. On a desktop GPU (gralloc=gbm) Waydroid writes debug.stagefright.ccodec=0, which switches Codec2 off and leaves
  # only the OMX codecs its usual LineageOS images bring. This image has no usable OMX (its OMX HAL is 32-bit only and is
  # removed from the VINTF manifest by bake-apps.sh), so the codec list apps see would be EMPTY: no video or audio
  # playback/recording through MediaCodec. 4 is Android's own default (Codec2 for everything). Verified: 96 codecs listed,
  # YouTube plays through c2.ffmpeg.av1.decoder (hardware path) with sound.
  sed -i 's/^debug.stagefright.ccodec=.*/debug.stagefright.ccodec=4/' "$p"
done
# `waydroid init` regenerated the container config above; put the settings Android 17 needs back (this function was
# defined but never called, which a machine that already had these lines in its config hid).
lxc_tweaks
# Android 17 creates /sys/fs/cgroup/{system,apps} at boot and puts every process in its own
# cgroup; Waydroid mounts cgroup read-only. Let the container write its own cgroup subtree
# (LXC cgroup namespace: it only sees /lxc.payload.waydroid, never the host hierarchy).
sed -i 's/^lxc.mount.auto = cgroup:ro /lxc.mount.auto = cgroup:rw /' /var/lib/waydroid/lxc/waydroid/config
# /metadata is a dir inside the read-only system image, but Android writes to it (aconfigd
# feature-flag storage, vold). Mount a writable tmpfs over it so those mkdirs succeed.
CFG=/var/lib/waydroid/lxc/waydroid/config
grep -q "tmpfs metadata tmpfs" "$CFG" ||   echo "lxc.mount.entry = tmpfs metadata tmpfs nodev,mode=0771,uid=0,gid=1000,create=dir 0 0" >> "$CFG"
# netd needs CAP_BPF (host has unprivileged_bpf_disabled=2). The container already keeps
# sys_admin, which covers bpf(); keeping bpf too lets init hand netd just that capability.
grep -qE '^lxc.cap.keep = .* bpf( |$)' /var/lib/waydroid/lxc/waydroid/config || \
  sed -i 's/^lxc.cap.keep = .*/& bpf/' /var/lib/waydroid/lxc/waydroid/config

dmesg -C
# Record the kernel log continuously: Android's init logging overflows the ring buffer,
# so a snapshot at the end loses the early boot (BPF loading etc.).
dmesg -w > dmesg.txt 2>&1 &
DMESG_PID=$!
systemctl start waydroid-container
sudo -u "$U" XDG_RUNTIME_DIR="/run/user/$(id -u "$U")" WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-wayland-0}" \
  waydroid session start > session.txt 2>&1 &
# Capture logcat continuously through the whole boot (reconnecting across container reboots), so crashes
# aren't lost to buffer rotation. A short snapshot at the end only catches the post-RescueParty state.
( for i in $(seq 1 60); do waydroid logcat 2>/dev/null; sleep 1; done ) > logcat.txt 2>&1 &
LOGCAT_PID=$!
# First boot is slow: zygote class-preload alone eats ~60s before system_server even starts,
# then full service init + dexopt before boot_completed. Give it room to reach the desktop.
echo "== waiting ${BOOT_WAIT:-180}s for Android to boot (or fail)"
sleep "${BOOT_WAIT:-180}"

kill "$DMESG_PID" "$LOGCAT_PID" 2>/dev/null
pkill -f "waydroid logcat" 2>/dev/null
# A fresh /data trusts no adb key and has debugging off, so the "Allow USB debugging?" prompt never appears and every
# Phone Hub script would fail with "unauthorized". Trust this user's key now (it waits for boot_completed itself).
if [ -x "$W/gb-adb-authorize.sh" ]; then
  "$W/gb-adb-authorize.sh" || echo "WARNING: adb authorization failed; run  sudo ./gb-adb-authorize.sh  once Android is up"
fi
cp /var/lib/waydroid/waydroid.log waydroid.log
waydroid status > status.txt 2>&1
chown "$U" dmesg.txt waydroid.log status.txt logcat.txt session.txt
echo "== done: logs captured in $W"
