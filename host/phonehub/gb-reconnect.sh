#!/bin/bash
# gb-reconnect.sh — 1-click / CLI command to recover Cross-Device Services (CDS) & AppCast.
# Resolves the phone on the current network, flushes stale relays, restarts user services,
# and brings up AppCast.
set -euo pipefail

W="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$W/lib/gbos-env.sh" || exit 1
APPDIR="$W/injector"
PKG="com.google.ambient.streaming"
CONT=$(gb_container)
STATE="$GB_STATE"
LOCK="$GB_RUN/gb-appcast.lock"

log() { printf '\e[1;34m[gb-reconnect]\e[0m %s\n' "$*"; }
ok()  { printf '\e[1;32m[gb-reconnect] ✔\e[0m %s\n' "$*"; }
warn(){ printf '\e[1;33m[gb-reconnect] ⚠\e[0m %s\n' "$*"; }
err() { printf '\e[1;31m[gb-reconnect] ✘\e[0m %s\n' "$*"; }

log "Starting GBOS Cross-Device & AppCast Reconnect..."

# 1. Check Waydroid container
if ! adb connect "$CONT" 2>&1 | grep -E "connected to|already connected" >/dev/null; then
    err "Waydroid container is not responding at $CONT. Is Waydroid running?"
    exit 1
fi
booted=$(adb -s "$CONT" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
user=$(adb -s "$CONT" shell am get-current-user 2>/dev/null | tr -d '\r')
if [ "$booted" != "1" ] || [ "$user" != "10" ]; then
    warn "Waydroid is starting up (boot=$booted user=$user). Waiting for Android..."
    sleep 5
fi
ok "Waydroid container is alive (user 10)"

# 2. Flush stale relays and locks
log "Cleaning up stale relays and locks..."
pkill -f 'host_relay\.py' 2>/dev/null || true
pkill -f 'phone_rearm\.py' 2>/dev/null || true
pkill -f 'input_relay\.py' 2>/dev/null || true
rm -f "$LOCK" $GB_RUN/gb-input-relay.pid
ok "Relays cleaned and lock released"

# 3. Detect current network subnet and Pixel IP
lan_ip=$(gb_lan_ip)
local_sub=$(echo "${lan_ip:-0.0.0.0}" | cut -d. -f1-3)
log "Current host network: $lan_ip (subnet $local_sub.*)"

# Find phone IP on current subnet
phone_ip=""
# Check adb devices
for serial in $(adb devices 2>/dev/null | awk -v c="$CONT" '$2=="device" && $1!=c {print $1}'); do
    cand=${serial%%:*}
    if [ "$(echo "$cand" | cut -d. -f1-3)" = "$local_sub" ]; then
        phone_ip="$cand"
        break
    fi
done

# Check ARP table probe on port 8099
if [ -z "$phone_ip" ]; then
    for arp_ip in $(ip -4 neigh show dev "$(gb_lan_iface)" 2>/dev/null | awk -v pfx="$local_sub" '$1 ~ pfx && ($4=="REACHABLE" || $4=="STALE" || $4=="DELAY") && $1 !~ /\.(1|254)$/ {print $1}'); do
        if python3 -c "import socket; s=socket.create_connection(('$arp_ip', 8099), timeout=0.3); s.close()" 2>/dev/null; then
            phone_ip="$arp_ip"
            break
        fi
    done
fi

# Check $GB_RUN/gb-appcast-phone-ip
if [ -z "$phone_ip" ] && [ -s $GB_RUN/gb-appcast-phone-ip ]; then
    cand=$(cat $GB_RUN/gb-appcast-phone-ip 2>/dev/null || true)
    [ "$(echo "$cand" | cut -d. -f1-3)" = "$local_sub" ] && phone_ip="$cand"
fi

# Fallback to cached
if [ -z "$phone_ip" ] && [ -s "$STATE/phone-ip" ]; then
    cand=$(cat "$STATE/phone-ip" 2>/dev/null || true)
    [ "$(echo "$cand" | cut -d. -f1-3)" = "$local_sub" ] && phone_ip="$cand"
fi

if [ -n "$phone_ip" ]; then
    mkdir -p "$STATE"
    echo "$phone_ip" > "$STATE/phone-ip"
    echo "$phone_ip" > $GB_RUN/gb-appcast-phone-ip
    ok "Pixel detected at $phone_ip"
else
    warn "Pixel not yet detected on $local_sub.* (it will auto-bind when casting starts)"
fi

# 4. Restart user services
log "Restarting gb-hub-link and gb-appcast services..."
# Ask the keeper for a full repair (Play services refresh + gate kick) even if a cast is live.
touch $GB_RUN/gb-hub-force-kick
systemctl --user restart gb-hub-link.service gb-appcast.service

# 5. Wait up to 30s for CDS Connected
log "Waiting for Cross-Device Services link..."
cds_ok=0
for i in $(seq 1 15); do
    if adb -s "$CONT" shell "dumpsys notification --noredact" 2>/dev/null \
       | grep -A 30 "pkg=$PKG .*id=1 tag=null" | grep "android.title=String (Connected)" >/dev/null; then
        cds_ok=1
        break
    fi
    sleep 2
done

if [ "$cds_ok" = 1 ]; then
    ok "Cross-Device Services: CONNECTED"
else
    warn "Cross-Device Services is still connecting in background..."
fi

# 6. Launch AppCast launcher in GBOS
log "Opening AppCast remote launcher in GBOS..."
adb -s "$CONT" shell "am start --user 10 -n $PKG/.multidevicesync.appcast.receiver.launcher.RemoteAppsLauncherActivity" >/dev/null 2>&1 || true
ok "AppCast window requested"

ok "All done! AppCast and Cross-Device Services are running."
