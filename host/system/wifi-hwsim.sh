#!/bin/bash
# wifi-hwsim.sh: give the Waydroid container a VIRTUAL Wi-Fi radio (GBOS_WAYDROID_FIXES.md, section 3.18, experiment).
#
#   sudo ./wifi-hwsim.sh up       load mac80211_hwsim (one radio), move that radio into the running container's network
#                                 namespace and name the interface wlan0 there
#   sudo ./wifi-hwsim.sh reload   reload the module with TWO radios (wlan0 station + wlan1 access point) and move both in
#   sudo ./wifi-hwsim.sh down     move/remove them again (the radio returns to the host when the container's namespace goes away)
#   sudo ./wifi-hwsim.sh status   show what exists on the host and in the container (read-only)
#   sudo ./wifi-hwsim.sh watch    loop forever (the systemd unit gb-wifi-host.service): whenever the container is running and has no Wi-Fi radio,
#                                 do what `up` does
#
# It never touches the real Wi-Fi adapter: only the phy that mac80211_hwsim creates is moved. NetworkManager is told to ignore the
# virtual interface while it is still on the host. The radio has to be moved again whenever the container restarts (the namespace is new).
set -u
IW=$(command -v iw || echo /sbin/iw)
LXC="lxc-info -P /var/lib/waydroid/lxc -n waydroid -p -H"

cpid() { $LXC 2>/dev/null | head -1; }
hwsim_phys() { for d in /sys/class/ieee80211/*; do [ -e "$d" ] || continue; readlink -f "$d/device" 2>/dev/null | grep -q mac80211_hwsim && basename "$d"; done; }   # phys created by mac80211_hwsim

status() {
    echo "== host: module"; lsmod | grep -E "^(mac80211_hwsim|mac80211|cfg80211) " || echo "(none loaded)"
    echo "== host: phys"; $IW phy 2>&1 | grep -E "^Wiphy" || echo "(none)"
    echo "== host: virtual phys (no backing device): $(hwsim_phys | tr '\n' ' ')"
    P=$(cpid); echo "== container pid: ${P:-not running}"
    if [ -n "${P:-}" ]; then
        echo "== container: phys"; nsenter -t "$P" -n $IW phy 2>&1 | grep -E "^Wiphy" || echo "(none)"
        echo "== container: wireless devices"; nsenter -t "$P" -n $IW dev 2>&1 | head -12
        echo "== container: links"; nsenter -t "$P" -n ip -o link | cut -c1-90
    fi
}

# Two virtual radios: wlan0 = the station Android uses, wlan1 = the access point "Ghome" that gb-fakeap.sh runs (section 3.20).
RADIOS=2
cphys() { nsenter -t "$1" -n $IW phy 2>/dev/null | awk '/^Wiphy/{print $2}'; }

up() {
    [ "$(id -u)" = 0 ] || { echo "run with sudo"; exit 1; }
    P=$(cpid); [ -n "$P" ] || { echo "container is not running"; exit 1; }
    if ! lsmod | grep -q "^mac80211_hwsim "; then
        modprobe mac80211_hwsim radios=$RADIOS || { echo "modprobe mac80211_hwsim failed"; exit 1; }
        sleep 1
    fi
    inside=$(cphys "$P" | wc -l)
    outside=$(hwsim_phys | wc -l)
    if [ $((inside + outside)) -lt $RADIOS ]; then
        echo "mac80211_hwsim was loaded with fewer than $RADIOS radios; run '$0 reload' (this briefly drops wlan0)"; exit 1
    fi
    for phy in $(hwsim_phys | sort -V); do
        for dev in /sys/class/ieee80211/$phy/device/net/*; do
            [ -e "$dev" ] && command -v nmcli >/dev/null && nmcli device set "$(basename "$dev")" managed no 2>/dev/null
        done
        echo "moving $phy into container netns (pid $P)"
        $IW phy "$phy" set netns "$P" || { echo "iw set netns failed"; exit 1; }
    done
    sleep 1
    # name the interfaces by phy order: first phy -> wlan0 (station), second -> wlan1 (access point)
    n=0
    for phy in $(cphys "$P" | sort -V | head -$RADIOS); do
        dev=$(nsenter -t "$P" -n $IW dev | awk -v ph="$phy" '$1=="phy#"substr(ph,5){f=1;next} /^phy#/{f=0} f&&/Interface/{print $2; exit}')
        [ -n "$dev" ] || continue
        nsenter -t "$P" -n ip link set "$dev" down 2>/dev/null
        nsenter -t "$P" -n ip link set "$dev" name "gbtmp$n"
        n=$((n+1))
    done
    n=0
    for t in gbtmp0 gbtmp1; do
        nsenter -t "$P" -n ip link set "$t" name "wlan$n" 2>/dev/null && nsenter -t "$P" -n ip link set "wlan$n" down 2>/dev/null
        n=$((n+1))
    done
    echo "done: container now has wlan0 (station) and wlan1 (access point)"
    status
}

down() {
    [ "$(id -u)" = 0 ] || { echo "run with sudo"; exit 1; }
    P=$(cpid)
    if [ -n "$P" ]; then
        for phy in $(cphys "$P"); do
            nsenter -t "$P" -n $IW phy "$phy" set netns 1 && echo "moved $phy back to the host"
        done
        sleep 1
    fi
}

# Tell the container which Wi-Fi network the host is on, so the fake access point (gb-fakeap.sh) can carry the same name.
# The container's /data is a bind mount of the session data dir (see config_session), so a file there appears at /data/local/tmp.
publish_ssid() {
    local d ssid
    d=$(sed -n 's#^lxc.mount.entry = \(/[^ ]*\) data .*#\1#p' /var/lib/waydroid/lxc/waydroid/config_session 2>/dev/null | head -1)
    [ -d "$d/local/tmp" ] || return 0
    ssid=$(nmcli -t -f ACTIVE,SSID dev wifi 2>/dev/null | sed -n 's/^yes://p' | head -1)
    [ -n "$ssid" ] || return 0          # not on Wi-Fi right now: keep whatever name the container already has
    [ "$(cat "$d/local/tmp/gb-host-ssid" 2>/dev/null)" = "$ssid" ] || { printf '%s' "$ssid" > "$d/local/tmp/gb-host-ssid.tmp" && mv -f "$d/local/tmp/gb-host-ssid.tmp" "$d/local/tmp/gb-host-ssid"; echo "published host SSID: $ssid"; }
}

watch() {
    [ "$(id -u)" = 0 ] || { echo "run with sudo"; exit 1; }
    while true; do
        publish_ssid
        P=$(cpid)
        if [ -n "$P" ] && [ "$(cphys "$P" | wc -l)" -lt $RADIOS ] && [ -e /proc/$P/ns/net ]; then
            echo "container (pid $P) has fewer than $RADIOS Wi-Fi radios: adding"
            up </dev/null >/dev/null 2>&1 || echo "up failed (will retry)"
        fi
        sleep 5
    done
}

reload() {
    [ "$(id -u)" = 0 ] || { echo "run with sudo"; exit 1; }
    echo "== moving radios back and reloading mac80211_hwsim with $RADIOS radios"
    down
    sleep 1
    rmmod mac80211_hwsim 2>&1 || { echo "could not unload mac80211_hwsim (still in use?)"; exit 1; }
    modprobe mac80211_hwsim radios=$RADIOS || { echo "modprobe failed"; exit 1; }
    sleep 1
    up
}

case "${1:-status}" in watch) watch ;; up) up ;; down) down ;; reload) reload ;; status) status ;; *) echo "usage: $0 up|down|reload|status|watch"; exit 1 ;; esac
