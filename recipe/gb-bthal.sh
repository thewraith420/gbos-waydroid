#!/system/bin/sh
# Bluetooth HCI HAL launcher (replaces the stock service definition of vendor.bluetooth-1-1 from zzz-googlebook-waydroid.rc).
# If the host helper (gb-bt-bridge, systemd unit on the host) is reachable on the Waydroid bridge address, run the Java HAL that
# forwards HCI to the host's real adapter; otherwise fall back to the stock simulated controller (RootCanal) so Bluetooth keeps
# working as before. The decision is made each time the service (re)starts, i.e. at boot; restart Waydroid after installing the host
# helper. A bare port probe never touches the host adapter (the helper only takes it after a hello frame).
HOST=192.168.240.1
PORT=6611
for i in $(seq 1 100); do                      # up to ~20 s (0.2s poll): start bthal as soon as eth0 is up so Bluetooth doesn't abort
    if toybox nc -w 1 $HOST $PORT </dev/null >/dev/null 2>&1; then
        CLASSPATH=/system/etc/bthal.apk:/system/framework/android.hidl.base-V1.0-java.jar:/system/framework/android.hidl.manager-V1.0-java.jar \
            exec app_process /system/bin com.gbos.bthal.Main $HOST $PORT
    fi
    sleep 0.2
done
exec /vendor/bin/hw/android.hardware.bluetooth@1.1-service.sim
