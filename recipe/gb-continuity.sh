#!/system/bin/sh
# gb-continuity.sh — Android 17 Task Continuity ("Continue on") bridge to host gb-link-daemon.
# Attaches a CompanionDeviceManager RawTransport for the active phone association so the host
# can push live foreground app state into GBOS's native taskbar "Continue" pill.
JAR=/system/etc/gb-continuity.jar
[ -f "$JAR" ] || exit 0
HOST="${GB_BRIDGE_IP:-192.168.240.1}"
PORT="${GB_CONTINUITY_PORT:-8095}"
export CLASSPATH="$JAR"
exec /system/bin/app_process /system/bin com.gbos.continuity.Main "$HOST" "$PORT" 10
