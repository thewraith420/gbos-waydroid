#!/system/bin/sh
# Standalone Waydroid IPlatform (`waydroidplatform`) + IUserMonitor notifier for GBOS user 10.
JAR=/system/etc/waydroid-platform.jar
[ -f "$JAR" ] || exit 0
exec env CLASSPATH="$JAR" app_process /system/bin com.gbos.waydroidplatform.Main
