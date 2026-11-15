#!/bin/bash
# Make Settings > Storage AND Play installs work on this Googlebook/Waydroid image.
#
# Root causes found (clean-boot measurements):
#  1) vold has no fstab (ro.hardware=unknown, no suffix) -> getTotalBytes() throws
#     -> Settings>Storage crashes. Fix: set ro.boot.fstab_suffix (an init setprop;
#     prop files can't set ro.boot.*) + a /vendor/etc/fstab.unknown entry for /data.
#  2) vold reports the SIZE OF THE FSTAB DEVICE as the internal storage total. If it
#     points at loop0 (vendor.img, 754 MB) the total shows "1 GB", free is 228 GB, so
#     Play thinks the disk is full and fails every install with 1047 ("not enough
#     space"). Fix: point /data at a loop device backed by a 997 GB sparse file, so
#     vold reports a real ~1 TB total. The device must be attached BEFORE vold starts
#     (early-fs), so it's attached from an `on init` action.
#  3) MediaProvider sometimes doesn't register the external_primary volume at first
#     boot -> Settings>Storage can throw "Volume external_primary not found".
#     NOTE: the earlier gb-mediafix (force-stop MediaProvider after boot) is REMOVED
#     in this version to test Gemini's theory that it orphans the /storage/emulated
#     FUSE mount. If Settings>Storage crashes again after reboot, re-add it.
#
# Everything is written into the Waydroid overlay (overlay_rw), NOT /data, so the
# signed-in Google account and the sideloaded WebView are untouched, and it persists
# across a normal reboot. Do NOT run try.sh (that rebuilds /data and wipes sign-in).
#
# Usage:  sudo bash fix-storage-persist.sh      then clean shutdown + start Waydroid.
set -e
# Write into BOTH overlay layers: "overlay" (persistent; survives `waydroid init -f` / try.sh, which deletes
# overlay_rw) and "overlay_rw" (the writable upper layer, if it exists right now).
TARGETS="/var/lib/waydroid/overlay /var/lib/waydroid/overlay_rw"
LOOP=/dev/block/loop63            # high, won't collide with Waydroid's apex loops
IMG=/data/gb-datadisk.img
SIZE=997801869312                 # matches the real /data partition size

for RW in $TARGETS; do
[ -d "$RW" ] || continue
install -d -m 755 "$RW/vendor/etc" "$RW/vendor/etc/init"

# fstab: map /data to the big loop device so vold reports the real total size.
printf '%s /data ext4 noatime,nosuid,nodev,errors=remount-ro wait\n' "$LOOP" \
    > "$RW/vendor/etc/fstab.unknown"
chmod 644 "$RW/vendor/etc/fstab.unknown"

# init rc: suffix + attach the loop (before vold at early-fs). No MediaProvider restart.
cat > "$RW/vendor/etc/init/gb-storagefix.rc" <<RC
on init
    setprop ro.boot.fstab_suffix unknown
    exec_start gb-datadisk

service gb-datadisk /system/bin/sh -c "[ -f $IMG ] || /system/bin/toybox truncate -s $SIZE $IMG; [ -b $LOOP ] || /system/bin/toybox mknod $LOOP b 7 63; /system/bin/toybox losetup $LOOP $IMG"
    user root
    group root
    oneshot
    disabled
RC
chmod 644 "$RW/vendor/etc/init/gb-storagefix.rc"

done

echo "Installed into Waydroid overlay:"
for RW in $TARGETS; do [ -d "$RW" ] && echo "  $RW/vendor/etc/fstab.unknown -> $(cat "$RW/vendor/etc/fstab.unknown")"; done
echo "  gb-storagefix.rc written alongside it (suffix + loop63 attach)"
echo
echo "Now do a clean Waydroid shutdown + start to apply."
