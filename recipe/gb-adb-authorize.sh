#!/bin/bash
# Let this PC's adb into the GBOS container on a FRESH Android /data.
#
#   sudo ./gb-adb-authorize.sh            (Waydroid running and booted; run from your normal user via sudo)
#
# Why: a fresh /data has no trusted adb keys AND has USB debugging switched off (settings global adb_enabled=0).
# Android only shows the "Allow USB debugging?" prompt while debugging is on, so on a fresh install the prompt never
# appears and `adb connect` stays "unauthorized" forever; every Phone Hub script then fails at its first step.
# This writes your user's adb public key into the container's /data/misc/adb/adb_keys and turns debugging on.
# try.sh runs it after the first boot; run it yourself after anything else that resets Android's data.
#
# Security: this trusts exactly one key, the one in ~/.android/adbkey.pub of the user who ran sudo. adb into the
# container is root (userdebug build); gb-lan.sh keeps port 5555 closed on the LAN-facing interface.
set -u
[ "$(id -u)" = 0 ] || { echo "run with sudo"; exit 1; }
U="${SUDO_USER:?run with sudo from your normal user}"
HOME_U="$(getent passwd "$U" | cut -d: -f6)"
PUB="$HOME_U/.android/adbkey.pub"

if [ ! -s "$PUB" ]; then
    # adb creates the user's key pair the first time its server starts
    command -v adb >/dev/null || { echo "adb is not installed (see docs/TUTORIAL-adb.md); skipping adb authorization"; exit 1; }
    sudo -u "$U" HOME="$HOME_U" adb start-server >/dev/null 2>&1
    [ -s "$PUB" ] || { echo "no adb key at $PUB and adb did not create one; skipping"; exit 1; }
fi

booted=""
for _ in $(seq 1 60); do
    booted="$(waydroid shell -- getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    [ "$booted" = 1 ] && break
    sleep 5
done
[ "$booted" = 1 ] || { echo "Android has not finished booting; start Waydroid and run this again"; exit 1; }

key="$(cat "$PUB")"
# append (keeping keys already trusted), never duplicate
printf '%s\n' "$key" | waydroid shell -- sh -c '
    f=/data/misc/adb/adb_keys
    mkdir -p /data/misc/adb
    read -r k
    touch "$f"
    grep -qxF "$k" "$f" || printf "%s\n" "$k" >> "$f"
    chown system:shell "$f"; chmod 640 "$f"
    settings put global adb_enabled 1
' || { echo "could not write the key into the container"; exit 1; }
echo "adb: this PC's key ($PUB) is trusted by the container and USB debugging is on"
echo "check:  adb connect $(waydroid status 2>/dev/null | awk '/IP address/{print $3}'):5555 && adb devices"
