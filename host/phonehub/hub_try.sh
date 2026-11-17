#!/bin/bash
# One instrumented Phone Hub connection attempt (GBOS_WAYDROID_FIXES.md 3.19/3.20). Needs adb to the container, jdb, the lid override OPEN.
# Forces the unidentified discovery gate (myr.b -> true) on every re-evaluation for DUR seconds, captures GMS dtdi/Nearby events and prints
# per-hop counters for traffic from the fake Wi-Fi to the phone. Usage: hub_try.sh [PIXEL_IP] [DUR]
. "$(dirname "$(readlink -f "$0")")/lib/gbos-env.sh" || exit 1
A=${ADB:-$(gb_container)}; PX=${1:-}; DUR=${2:-90}
S=${TMPDIR:-/tmp}/hub_try.$$; mkdir -p $S
[ -n "$PX" ] || PX=$(adb -s $A shell 'ip neigh show dev eth1' | awk '/REACHABLE|STALE|DELAY/{print $1}' | grep -v -E '\.(1|255)$' | head -1)
echo "phone ip guess: $PX"
adb -s $A shell "iptables -t raw -N GBC 2>/dev/null; iptables -t raw -F GBC; iptables -t raw -D PREROUTING -j GBC 2>/dev/null; iptables -t raw -I PREROUTING 1 -j GBC
iptables -t raw -A GBC -i eth1 -s $PX -p tcp; iptables -t raw -A GBC -i wlan1 -d $PX -p tcp --syn; iptables -t nat -L GBAP -Z >/dev/null"
(timeout $((DUR+20)) adb -s $A logcat -v threadtime -b main,system,crash 2>&1 | grep --line-buffered -E " Dtdi |NearbyConnections|MEDIUM_ERROR|NearbyMediums: Extra" > $S/lc.txt &)
sleep 1
T0=$(adb -s $A shell date +%T | tr -d '\r')
adb -s $A shell 'am force-stop com.google.ambient.streaming; am set-debug-app -w com.google.ambient.streaming; am start-foreground-service --user 10 -n com.google.ambient.streaming/.multidevicesync.devicelink.dtdi.foreground.ForegroundDiscoveryService >/dev/null'
sleep 5
P=$(adb -s $A shell pidof com.google.ambient.streaming | tr -d '\r'); adb -s $A forward tcp:8700 jdwp:$P >/dev/null
mkfifo $S/jin; (timeout $((DUR+10)) jdb -attach localhost:8700 < $S/jin > $S/jdb.out 2>&1 &); exec 7>$S/jin
echo "stop in myr.b" >&7; sleep 3; echo resume >&7
seen=0; for i in $(seq 1 $DUR); do h=$(grep -c "Breakpoint hit" $S/jdb.out); if [ "$h" -gt "$seen" ]; then echo "set this.b = true" >&7; sleep 0.4; echo cont >&7; seen=$h; fi; sleep 1; done
echo exit >&7; exec 7>&-; adb -s $A shell 'am clear-debug-app'
echo "== events since $T0"
awk -v t="$T0" '$2>=t' $S/lc.txt | grep -E "startOrUpdate|onDeviceDiscovered|Attempting to connect|onConnectionResult|Set upgrading|upgrade|Upgrade|MEDIUM_ERROR|WifiSocket|WakeUp|resolution=|onDisconnected|closed" | grep -v "	at " | cut -c7-200 | head -30
echo "== counters (tcp SYN into AP side / tcp from phone on eth1 / LAN SNAT)"
adb -s $A shell 'iptables -t raw -L GBC -v -n | tail -2; iptables -t nat -L GBAP -v -n | tail -2'
echo "logs in $S"
