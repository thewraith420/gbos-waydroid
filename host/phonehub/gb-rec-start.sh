mkdir -p /data/local/tmp/gbrec
SPECS="*:W MultideviceSync:V Dtdi:I NearbyConnections:I NearbyMediums:I NearbyPresence:I ActivityManager:I ActivityTaskManager:I killinfo:I am_proc_died:I am_crash:I am_kill:I am_proc_start:I wm_new_intent:I wm_create_activity:I wm_finish_activity:I wm_destroy_activity:I"
setsid nohup logcat -b all -v threadtime -f /data/local/tmp/gbrec/gb-rec.log -r 6144 -n 8 $SPECS >/data/local/tmp/gbrec/stderr.txt 2>&1 </dev/null &
