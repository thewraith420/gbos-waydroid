#!/system/bin/sh
# gb-fakeap: a simulated Wi-Fi access point so GBOS sees a connected Wi-Fi network that carries the SAME NAME as the network the host is on.
# GBOS_WAYDROID_FIXES.md, section 3.20. Needs two mac80211_hwsim radios inside the container: wlan0 (station, Android's) and wlan1
# (this AP). The host service (wifi-hwsim.sh watch) publishes the host's current Wi-Fi name into /data/local/tmp/gb-host-ssid; this script
# follows it: when the name changes it restarts hostapd with the new name and re-joins Android to it. Traffic from the AP side is NATed
# out through eth0 (the container's internet); eth1 stays the real-LAN presence.
FALLBACK_SSID=Ghome
SSID_FILE=/data/local/tmp/gb-host-ssid
AP_IF=wlan1
AP_IP=10.77.0.1
STA_IP=10.77.0.2
DNS=192.168.240.1
UPLINK=eth0
CONF=/data/misc/wifi/gb-ap.conf
DHCPD=${DHCPD:-/system/bin/gb-dhcpd}
LOG=/data/local/tmp/gb-fakeap.log
exec >>$LOG 2>&1
echo "== $(date) gb-fakeap start"

# wait for the AP radio
i=0; while [ ! -d /sys/class/net/$AP_IF ]; do i=$((i+1)); [ $i -gt 600 ] && { echo "no $AP_IF after 20 min, giving up"; exit 0; }; sleep 2; done
echo "$AP_IF present"

ip addr flush dev $AP_IF 2>/dev/null
ip addr add $AP_IP/24 dev $AP_IF
ip link set $AP_IF up
mkdir -p /data/misc/wifi

# Kernel settings for same-netns hairpin: wlan0 station must be able to ARP and reach wlan1 AP gateway 10.77.0.1
echo 1 > /proc/sys/net/ipv4/ip_forward
for i in all wlan0 $AP_IF $UPLINK; do echo 0 > /proc/sys/net/ipv4/conf/$i/rp_filter 2>/dev/null; done
for i in all wlan0 $AP_IF; do echo 1 > /proc/sys/net/ipv4/conf/$i/accept_local 2>/dev/null; done

# Data path. Both ends of the radio link live in this one network namespace, and mac80211_hwsim copies packets together with their conntrack
# state, so pushing traffic through the radio and NATing it (wlan0 -> wlan1 -> uplink) cannot work for TCP. The radio only carries what Android
# needs to believe it is on Wi-Fi (scan, auth, DHCP). Real traffic of the Wi-Fi *network* (sockets carrying its fwmark) is policy-routed straight
# out the real interfaces: LAN destinations and multicast via eth1 (so the phone sees GBOS at its real LAN address), everything else via the uplink.
# The kernel then picks the egress interface's own address as the source: no NAT, no hairpin.
netrules() {
  E1=$(ip -4 -o addr show eth1 2>/dev/null | sed 's/.*inet \([0-9.]*\)\/.*/\1/')
  LANCIDR=$(ip -4 route show dev eth1 scope link 2>/dev/null | awk '{print $1; exit}')
  RAW_MARK=$(ip rule | awk '/lookup wlan0/{for(i=1;i<=NF;i++) if($i ~ /fwmark/) {split($(i+1),a,"/"); print a[1]}}' | head -1)
  [ -n "$RAW_MARK" ] && MARK=$(printf '0x%x' $((RAW_MARK & 0xffff))) || MARK=""
  [ -n "$MARK" ] || { echo "netrules: no wlan0 fwmark rule yet"; return; }
  key="$E1|$LANCIDR|$MARK"
  [ "$key" = "$LASTKEY" ] && return
  LASTKEY="$key"
  for p in 15000 15001 15002 15003; do while ip rule del pref $p 2>/dev/null; do :; done; done
  [ -n "$LANCIDR" ] && ip rule add pref 15000 fwmark $MARK/0xffff to $LANCIDR lookup eth1
  ip rule add pref 15001 fwmark $MARK/0xffff to 224.0.0.0/4 lookup eth1
  ip rule add pref 15002 fwmark $MARK/0xffff to 255.255.255.255 lookup eth1
  ip rule add pref 15003 fwmark $MARK/0xffff lookup $UPLINK
  echo "netrules: mark=$MARK lan=$LANCIDR eth1=$E1"
}
netrules

pkill gb-dhcpd 2>/dev/null
$DHCPD $AP_IF $AP_IP $STA_IP 255.255.255.0 $DNS &
echo "dhcpd started"

# make Android join SSID $1 (forgetting the open network we added for the previous name, $2)
join() {
    new="$1"; old="$2"
    if [ -n "$old" ] && [ "$old" != "$new" ]; then
        cmd wifi list-networks 2>/dev/null | while read id ssid sec; do
            [ "$ssid" = "$old" ] && [ "$sec" = "open" ] && cmd wifi forget-network "$id" >/dev/null 2>&1
        done
    fi
    cmd wifi list-networks 2>/dev/null | grep -F -q "$new" || cmd wifi add-network "$new" open -r auto >/dev/null 2>&1
    cmd wifi connect-network "$new" open >/dev/null 2>&1
    echo "join requested: $new"
}

cur=""; HPID=""; tries=0
while true; do
  netrules
  want=$(cat $SSID_FILE 2>/dev/null)
  [ -n "$want" ] || want=$FALLBACK_SSID
  alive=0; [ -n "$HPID" ] && kill -0 $HPID 2>/dev/null && alive=1
  if [ "$want" != "$cur" ] || [ $alive = 0 ]; then
    [ $alive = 1 ] && { kill $HPID; sleep 1; }
    hex=$(printf '%s' "$want" | od -An -tx1 | tr -d ' \n')
    cat > $CONF <<EOC
interface=$AP_IF
driver=nl80211
ssid2=$hex
hw_mode=g
channel=6
ieee80211n=1
wmm_enabled=1
auth_algs=1
beacon_int=100
EOC
    # hostapd drops to the wifi user before it reads its config. This script runs as root, so on a fresh /data the file is
    # created root 0600 and hostapd exits at once ("Could not open configuration file", logcat only) in an endless restart
    # loop: no access point, Android never joins. (An old /data hid this: its copy had been made world-readable by hand.)
    chown root:wifi $CONF; chmod 640 $CONF
    /vendor/bin/hw/hostapd $CONF >/data/local/tmp/hostapd.out 2>&1 &
    HPID=$!
    echo "hostapd started (pid $HPID) as '$want'"
    old="$cur"; cur="$want"
    ( sleep 8; join "$want" "$old" ) &
    tries=0
  else
    # keep trying to be connected: Android can drop the saved network or never have connected yet
    tries=$((tries+1))
    if [ $((tries % 6)) = 0 ]; then
      # a boot (or a system_server restart) can leave Wi-Fi switched off although the setting says on: switch it back on
      if cmd wifi status 2>/dev/null | grep -q 'Wifi is disabled' && [ "$(settings get global wifi_on 2>/dev/null)" = 1 ]; then
        cmd wifi set-wifi-enabled enabled >/dev/null 2>&1; echo "wifi was disabled: re-enabled"
      fi
      if ! cmd wifi status 2>/dev/null | grep -q 'Wifi is connected to'; then
        # eth1 (the LAN presence) is labelled Wi-Fi by the lan-wifi overlay. If it comes up BEFORE wlan0 has joined, it already satisfies Android's
        # Wi-Fi network request and the Wi-Fi stack never joins ("CMD_START_CONNECT but no requests and not connected, bailing"; found 2026-10-25).
        # So: drop eth1 for the join (at most once per 5 minutes), then bring it back (gb-lan re-applies its address).
        now=$(date +%s)
        if [ $((now - ${LASTDANCE:-0})) -ge 300 ] && [ -d /sys/class/net/eth1 ]; then
          LASTDANCE=$now
          echo "wifi not connected: dropping eth1 for the join"
          ip link set eth1 down; sleep 3; join "$want" ""
          w=0; while [ $w -lt 8 ]; do sleep 4; cmd wifi status 2>/dev/null | grep -q 'Wifi is connected to' && break; w=$((w+1)); done
          ip link set eth1 up; echo "eth1 restored (wifi: $(cmd wifi status 2>/dev/null | grep -c 'Wifi is connected to'))"
        else
          join "$want" ""
        fi
      fi
    fi
  fi
  sleep 5
done
