#!/bin/bash
# Build donor-a16/: Waydroid-patched Android 16 binaries from the WayDroid-ATV system image,
# plus the transitive closure of their libraries (minus bionic, which stays Googlebook's).
# Runs as the normal user; try.sh then patches RUNPATH and installs them.
#   ./prep-donor.sh
set -euo pipefail
cd "$(dirname "$0")"
A=atv16/root/system
[ -d "$A" ] || fsck.erofs --extract=atv16/root --no-preserve atv16/system.img >/dev/null

# Binaries taken from the donor (path under /system/bin)
BINS="init waydroid-preinit servicemanager"

D=donor-a16
rm -rf "$D"; mkdir -p "$D/lib" "$D/bin"
for b in $BINS; do cp "$A/bin/$b" "$D/bin/"; done
cp "$A/etc/init/init.waydroid.rc" "$D/"

# HIDL: Googlebook's /system/bin/hwservicemanager is a dangling link to system_ext (which doesn't
# ship it), so libhidl reports HIDL unsupported and every HIDL vendor HAL fails. Take ATV's.
mkdir -p "$D/sysext-bin"
cp "$A/system_ext/bin/hwservicemanager" "$D/sysext-bin/"
cp "$A/system_ext/etc/init/hwservicemanager.rc" "$D/"

# netbpfload lives in the tethering APEX: ATV's Android 16 one tolerates the container's read-only
# /proc/sys and honours ro.bpf.kver_override (Googlebook's Android 17 one rejects non-LTS 7.x kernels).
T=$(mktemp -d)
unzip -q -o "$A/apex/com.android.tethering.apex" apex_payload.img -d "$T"
/sbin/debugfs -R "dump /bin/netbpfload $D/bin/netbpfload-wd" "$T/apex_payload.img" >/dev/null 2>&1
rm -rf "$T"
[ -s "$D/bin/netbpfload-wd" ] || { echo "failed to extract netbpfload"; exit 1; }

SKIP=" libc.so libm.so libdl.so libdl_android.so "
seen=" "
queue=""
for b in $BINS netbpfload-wd; do queue="$queue bin/$b"; done
queue="$queue sysext-bin/hwservicemanager"
while [ -n "${queue// /}" ]; do
  set -- $queue; f=$1; shift; queue="$*"
  for l in $(readelf -d "$D/$f" 2>/dev/null | awk '/NEEDED/{gsub(/[\[\]]/,"",$5); print $5}'); do
    case "$SKIP$seen" in *" $l "*) continue ;; esac
    seen="$seen$l "
    if [ -f "$A/lib64/$l" ]; then cp "$A/lib64/$l" "$D/lib/"; queue="$queue lib/$l"
    else echo "warning: $l not in donor /system/lib64"; fi
  done
done
echo "donor: $(ls $D/bin | tr '\n' ' ')| $(ls $D/lib | wc -l) libs"
