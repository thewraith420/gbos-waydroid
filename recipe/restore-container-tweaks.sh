#!/bin/bash
# restore-container-tweaks.sh: re-apply the container settings that `waydroid init` / `waydroid upgrade` (run by the Magisk installer) regenerated away.
# These are exactly the config edits try.sh makes AFTER building the image; this script does ONLY them (no image build, no data wipe):
#   1. lxc config: bind a dummy file over /proc/sys/kernel/unprivileged_bpf_disabled (netbpfload would otherwise reboot Android)
#   2. lxc config: cgroup:ro -> cgroup:rw (Android 17 creates its own cgroups)
#   3. lxc config: writable tmpfs over /metadata
#   4. lxc config: keep the bpf capability (netd needs CAP_BPF)
#   5. waydroid_base.prop / waydroid.prop: ro.debuggable=1, ro.build.type=userdebug
# Symptom without them: netd aborts at boot, init keeps killing the zygotes, boot animation then black.
#   sudo bash restore-container-tweaks.sh      then restart Waydroid:  sudo waydroid container stop; waydroid session stop; start it again
# Backups of the files it edits go to /var/lib/waydroid/tweaks-backup-<time>/.
set -eu
[ "$(id -u)" = 0 ] || { echo "run with sudo"; exit 1; }
CFG=/var/lib/waydroid/lxc/waydroid/config; FAKE=/var/lib/waydroid/fake-sysctl
B=/var/lib/waydroid/tweaks-backup-$(date +%Y%m%d-%H%M%S); mkdir -p "$B"
cp "$CFG" "$B/config"; cp /var/lib/waydroid/waydroid_base.prop /var/lib/waydroid/waydroid.prop "$B/"
mkdir -p "$FAKE"; [ -f "$FAKE/unprivileged_bpf_disabled" ] || echo 2 > "$FAKE/unprivileged_bpf_disabled"
grep -q "fake-sysctl/unprivileged_bpf_disabled" "$CFG" || echo "lxc.mount.entry = $FAKE/unprivileged_bpf_disabled proc/sys/kernel/unprivileged_bpf_disabled none bind,create=file 0 0" >> "$CFG"
sed -i 's/^lxc.mount.auto = cgroup:ro /lxc.mount.auto = cgroup:rw /' "$CFG"
grep -q "tmpfs metadata tmpfs" "$CFG" || echo "lxc.mount.entry = tmpfs metadata tmpfs nodev,mode=0771,uid=0,gid=1000,create=dir 0 0" >> "$CFG"
grep -qE '^lxc.cap.keep = .* bpf( |$)' "$CFG" || sed -i 's/^lxc.cap.keep = .*/& bpf/' "$CFG"
for p in /var/lib/waydroid/waydroid_base.prop /var/lib/waydroid/waydroid.prop; do
  if grep -q '^ro.debuggable=' "$p"; then sed -i 's/^ro.debuggable=.*/ro.debuggable=1/' "$p"; else printf 'ro.debuggable=1\n' >> "$p"; fi
  if grep -q '^ro.build.type=' "$p"; then sed -i 's/^ro.build.type=.*/ro.build.type=userdebug/' "$p"; else printf 'ro.build.type=userdebug\n' >> "$p"; fi
done
echo "--- resulting config lines:"; grep -E "cap.keep|cgroup|fake-sysctl|metadata" "$CFG"; grep -H -E "debuggable|build.type" /var/lib/waydroid/waydroid_base.prop /var/lib/waydroid/waydroid.prop
echo "done; backups in $B; now restart Waydroid"
