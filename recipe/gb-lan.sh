#!/system/bin/sh
# Container side of gb-lan (see gb-lan.py on the host, GBOS_WAYDROID_FIXES.md section 3.15).
# The host daemon writes /data/local/tmp/gb-lan.conf (IFACE ADDR GW DNS) and waits for /data/local/tmp/gb-lan.applied before it creates
# the ipvlan NIC (eth1) in this container. We apply the static configuration to Android's Ethernet manager (which stores it per
# interface name, so it is in place BEFORE eth1 appears and Android never runs DHCP on the shared-MAC link), then confirm.
# The NIC is directly on the home LAN, so block the adb port on it.
CONF=/data/local/tmp/gb-lan.conf
APPLIED=/data/local/tmp/gb-lan.applied
CP=/system/etc/ethcfg.apk
last=""
while true; do
    if [ -f "$CONF" ]; then
        sum=$(md5sum < "$CONF" | cut -d' ' -f1)
        . "$CONF"
        if [ -d "/sys/class/net/$IFACE" ] && ! ip -4 addr show dev "$IFACE" 2>/dev/null | grep -q "inet "; then
            for p in $(ps -A -o PID,ARGS 2>/dev/null | grep "[e]thcfg.Main hold" | while read a b; do echo $a; done); do kill $p 2>/dev/null; done
            last=""
        fi
        if [ "$sum" != "$last" ] || [ ! -f "$APPLIED" ]; then
            out=$(CLASSPATH=$CP app_process /system/bin com.gbos.ethcfg.Main set "$IFACE" "$ADDR" "${GW:--}" "$DNS" 2>&1)
            case "$out" in
            *"set to"*) echo "$sum" > "$APPLIED"; chmod 644 "$APPLIED"; last="$sum" ;;
            esac
        fi
        # Android tears down a network nothing asks for (eth1 is LAN-only/unvalidated and eth0 wins every request): the address would be
        # cleared 5 s after it is set. Keep one explicit request for eth1 alive (ethcfg hold) so the network and its address stay.
        if ! ps -A -o ARGS 2>/dev/null | grep -q "[e]thcfg.Main hold"; then
            CLASSPATH=$CP setsid app_process /system/bin com.gbos.ethcfg.Main hold "$IFACE" >/dev/null 2>&1 &
        fi
        # never expose adb (root, tcp 5555) on the LAN-facing NIC
        iptables -C INPUT -i "$IFACE" -p tcp --dport 5555 -j DROP 2>/dev/null || iptables -I INPUT -i "$IFACE" -p tcp --dport 5555 -j DROP 2>/dev/null
    else
        last=""
        rm -f "$APPLIED"
        for p in $(ps -A -o PID,ARGS 2>/dev/null | grep "[e]thcfg.Main hold" | while read a b; do echo $a; done); do kill $p 2>/dev/null; done
    fi
    sleep 5
done
