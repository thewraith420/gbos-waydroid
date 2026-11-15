#!/system/bin/sh
# Keep per-user emulated ("sdcard") storage mounted.
# Optimized: checks /proc/mounts to avoid spawning app_process / Java VM every 5s when storage is already mounted.
while true; do
    if grep -q "/mnt/user/10/emulated" /proc/mounts 2>/dev/null && grep -q "/mnt/user/0/emulated" /proc/mounts 2>/dev/null; then
        sleep 30
        continue
    fi
    for v in $(sm list-volumes 2>/dev/null | awk '/^emulated;/ && /unmounted/{print $1}'); do
        sm mount "$v" 2>/dev/null
    done
    sleep 5
done
