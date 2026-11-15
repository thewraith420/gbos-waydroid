#!/bin/bash
# Merge the Googlebook system_a + system_ext_a + product_a erofs images into one
# system-as-root image (system.img) in the layout Waydroid expects.
#
# Googlebook's system_a already has /product and /system_ext as empty mount points with
# /system/product and /system/system_ext symlinked to them, so we just fill those in.
#
# Must run as root so file ownership and SELinux xattrs survive extraction:
#   sudo ./build-system-img.sh
set -euo pipefail
cd "$(dirname "$0")"
[ "$(id -u)" = 0 ] || { echo "run with sudo"; exit 1; }

EXTRACT="fsck.erofs --preserve --xattrs"
rm -rf root tmp_product tmp_system_ext system.img

echo "== extracting system_a"
$EXTRACT --extract=root system_a.img
for d in product system_ext; do
  [ -d "root/$d" ] && [ -z "$(ls -A "root/$d")" ] || { echo "root/$d is not an empty mount point"; exit 1; }
done

echo "== extracting product_a and system_ext_a into their mount points"
$EXTRACT --extract=tmp_product product_a.img
$EXTRACT --extract=tmp_system_ext system_ext_a.img
rmdir root/product root/system_ext
mv tmp_product root/product
mv tmp_system_ext root/system_ext

# Optional: INIT_FROM=/path/to/init swaps in a Waydroid-patched init (stock Android init
# aborts in first stage inside an LXC container). Overwrite contents in place so the
# original owner, mode and SELinux label stay on the file.
if [ -n "${INIT_FROM:-}" ]; then
  echo "== replacing /system/bin/init with $INIT_FROM"
  cat "$INIT_FROM" > root/system/bin/init
fi
# Optional: INIT_LIBS="a.so b.so" adds extra libraries that init needs to /system/lib64.
for lib in ${INIT_LIBS:-}; do
  echo "== adding $(basename "$lib") to /system/lib64"
  install -o 0 -g 0 -m 644 "$lib" root/system/lib64/
done
# Optional: INIT_LIBDIR=dir installs a private library set as /system/lib64/wdinit, for an
# init whose RUNPATH points there (keeps its older libs away from every other process).
if [ -n "${INIT_LIBDIR:-}" ]; then
  echo "== installing private init libraries as /system/lib64/wdinit"
  install -d -o 0 -g 0 -m 755 root/system/lib64/wdinit
  install -o 0 -g 0 -m 644 "$INIT_LIBDIR"/*.so root/system/lib64/wdinit/
fi

# Optional: EXTRA_FILES="src:dest:mode ..." installs extra files (dest relative to the image root).
for spec in ${EXTRA_FILES:-}; do
  IFS=: read -r src dest mode <<< "$spec"
  echo "== installing $dest"
  install -D -o 0 -g 0 -m "${mode:-644}" "$src" "root/$dest"
done

# Remove init .rc files for ChromeOS/host daemons that have no hardware to talk to in the container and
# otherwise crash-loop (timberslide = ChromeOS embedded-controller log collector; spams a FATAL every
# ~5s). Deleting the rc stops init from ever starting them.
for rc in timberslide; do
  rm -f "root/system/etc/init/$rc.rc"
done

# Disable the setup wizard at the property level so a fresh boot heads for the launcher, not first-run.
# WHY we can't just run setup: the Googlebook setup wizard BLOCKS waiting for the GSC (Google Security
# Chip) HAL android.system.desktop.security.gsc.IGsc/default, which has no hardware in the container (we
# stub gscd to /system/bin/true). Setup waits forever -> ANR "Googlebook setup isn't responding". (Also
# needs pinweaver/trusty security services we don't have.) So we bypass setup and provision manually
# (gb-firstboot flags + gb-storagemount daemon). Belt to gb-firstboot.sh.
if ! grep -q '^ro.setupwizard.mode=' root/system/build.prop; then
  printf 'ro.setupwizard.mode=DISABLED\n' >> root/system/build.prop
fi

# ro.debuggable=1 so the standalone WebView reads /data/local/tmp/webview-command-line (gb-firstboot
# writes --single-process there). WHY: the Google add-account flow (and Chrome sign-in) renders
# accounts.google.com in com.google.android.webview, but its ISOLATED sandboxed render process cannot
# establish its renderer<->browser IPC in this LXC container -> the sign-in page NEVER loads (zero network,
# then MinuteMaid's 90s "Loading took too long" timeout -> "Something went wrong"). Proven isolated to the
# WebView: full Chrome (its own renderer) loads pages fine. --single-process runs the renderer INSIDE the
# host GMS process (which has working networking - checkin/attestation already succeed), bypassing the
# broken isolated process. WebView only reads that flags file when debuggable (user build won't, and live
# WebView developer-mode would not stick). Trade-off: makes all apps debuggable (slightly more memory on
# the tight 7.4GB). Revert by removing this + the firstboot file if it regresses stability.
# NOTE: ro.* props are write-once (FIRST definition wins), and build.prop already ships ro.debuggable=0,
# so we must REPLACE that line in place, not append (an appended second line is ignored).
if grep -q '^ro.debuggable=' root/system/build.prop; then
  sed -i 's/^ro.debuggable=.*/ro.debuggable=1/' root/system/build.prop
else
  printf 'ro.debuggable=1\n' >> root/system/build.prop
fi

# Set ro.build.type=userdebug across all partitions so WebView's AndroidInfo.b() returns true.
# In WebViewGoogle.apk (classes.dex at 0xd9140), reading /data/local/tmp/webview-command-line
# is gated on AndroidInfo.b(), which checks "eng".equals(Build.TYPE) || "userdebug".equals(Build.TYPE).
# Without ro.build.type=userdebug, WebView skips reading the command-line flags file entirely.
for prop in root/system/build.prop root/product/etc/build.prop root/system_ext/etc/build.prop; do
  [ -f "$prop" ] || continue
  if grep -q '^ro.build.type=' "$prop"; then
    sed -i 's/^ro.build.type=.*/ro.build.type=userdebug/' "$prop"
  else
    printf 'ro.build.type=userdebug\n' >> "$prop"
  fi
done

# Waydroid's LXC config mounts tmpfs on these; its LineageOS images ship them, stock Android doesn't.
mkdir -p root/mnt_extra root/var root/run
chmod 755 root/mnt_extra root/var root/run

echo "== building system.img (erofs, lz4hc)"
mkfs.erofs -zlz4hc -T 1230768000 system.img root

echo "== cleaning up extracted tree"
rm -rf root
chown "${SUDO_UID:-0}:${SUDO_GID:-0}" system.img
ls -lh system.img
