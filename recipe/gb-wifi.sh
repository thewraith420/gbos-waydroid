#!/system/bin/sh
# Fake Wi-Fi launcher: registers android.hardware.wifi.IWifi/default (a stub HAL claiming one station interface, wlan0) and the
# "wifinl80211" service (a stub wificond; the real binary is missing from this image). Needs wlan0 to exist for Wi-Fi to really come
# up: the host script wifi-hwsim.sh moves a mac80211_hwsim radio into this container. See GBOS_WAYDROID_FIXES.md, section 3.18.
CLASSPATH=/system/etc/fakewifi.apk exec app_process /system/bin com.gbos.fakewifi.Main
