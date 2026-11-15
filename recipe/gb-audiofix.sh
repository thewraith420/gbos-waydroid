#!/system/bin/sh
# Boot-time fix for "no sound": AudioFlinger's output threads come up with master mute ON while AudioService reports it OFF,
# so AudioService's own setMasterMute(false) is a no-op; cycling true -> false (via audioctl.apk) rewrites the threads.
# A single cycle at boot_completed is too early / gets overridden, so watch for a few minutes and re-cycle whenever the
# output thread still reports "Master mute: on"; stop after it has been off for ~1 minute straight.
CP=/system/etc/audioctl.apk
[ -f "$CP" ] || exit 0
off=0
for i in $(seq 1 90); do                      # up to ~7.5 min
    line=$(dumpsys media.audio_flinger 2>/dev/null | grep -m1 "Master mute")
    case "$line" in
    *": on"*)
        CLASSPATH=$CP app_process /system/bin com.gbos.audioctl.Main setmastermute true  >/dev/null 2>&1
        CLASSPATH=$CP app_process /system/bin com.gbos.audioctl.Main setmastermute false >/dev/null 2>&1
        off=0 ;;
    *": off"*)
        off=$((off + 1)); [ "$off" -ge 12 ] && exit 0 ;;
    esac
    sleep 5
done
exit 0
