#!/bin/bash
# fix-vintf-dialog.sh: stop the boot-time "There's an internal problem with your device. Contact your manufacturer" dialog.
#
# Why it appears: Android checks the running kernel against the framework compatibility matrix. Every VINTF check passes except one:
#   "Runtime info <==> Framework Compatibility Matrix? INCOMPATIBLE, kernelSepolicyVersion = 0 but required >= 30"
# SELinux is disabled in this container (it shares the host kernel), so the kernel reports policy version 0.
#
# What this does (ONE change, nothing else): installs a copy of /system/etc/vintf/compatibility_matrix.device.xml in the baked lower overlay with
#   <kernel-sepolicy-version>30</kernel-sepolicy-version>  ->  <kernel-sepolicy-version>0</kernel-sepolicy-version>
# i.e. it stops requiring a kernel SELinux policy version that this container cannot have. It does not enable or disable SELinux.
#
#   sudo bash fix-vintf-dialog.sh            apply
#   sudo bash fix-vintf-dialog.sh --remove   undo (the original file comes back)
# Then restart Waydroid. Verify with: adb shell vintf | tail -8   (the "Runtime info" line should say GOOD)
set -eu
OV=/var/lib/waydroid/overlay
DST="$OV/system/etc/vintf/compatibility_matrix.device.xml"
STALE=/var/lib/waydroid/overlay_rw/system/system/etc/vintf/compatibility_matrix.device.xml
W="$(cd "$(dirname "$0")" && pwd)"
[ "$(id -u)" = 0 ] || { echo "run with sudo"; exit 1; }

if [ "${1:-}" = "--remove" ]; then
    rm -f "$DST" "$STALE"
    echo "removed; restart Waydroid"; exit 0
fi

# source: the pristine file from the system image (the container must have been started once), else a copy pulled earlier
SRC=/var/lib/waydroid/rootfs/system/etc/vintf/compatibility_matrix.device.xml
[ -f "$SRC" ] || SRC="$W/compatibility_matrix.device.orig.xml"
[ -f "$SRC" ] || { echo "cannot find the original matrix (looked in rootfs and $W)"; exit 1; }
grep -q '<kernel-sepolicy-version>30</kernel-sepolicy-version>' "$SRC" || { echo "expected line not found in $SRC; refusing"; exit 1; }

TMP=$(mktemp)
sed 's#<kernel-sepolicy-version>30</kernel-sepolicy-version>#<kernel-sepolicy-version>0</kernel-sepolicy-version>#' "$SRC" > "$TMP"
python3 -c "import xml.dom.minidom as m,sys; m.parse(sys.argv[1])" "$TMP" || { echo "result is not well-formed XML; refusing"; exit 1; }
echo "change:"; diff "$SRC" "$TMP" || true

install -D -m 644 "$TMP" "$DST"
rm -f "$STALE" "$TMP"          # a stale copy in the upper layer would shadow the baked one
echo "installed $DST; restart Waydroid"
