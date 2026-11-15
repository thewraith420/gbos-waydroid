#!/system/bin/sh
# OS bringup: keep the UI-critical processes OOM-immune. The Waydroid container shares the host's RAM,
# and under memory pressure the host/cgroup OOM killer reaps system_server (and surfaceflinger) even at
# their normal protective oom_score_adj, restarting the whole desktop. Pin them to -1000
# (OOM_SCORE_ADJ_MIN = never kill) and re-apply, since their pids change when they restart.

KNOWN_PIDS=""

check_alive() {
    [ -n "$KNOWN_PIDS" ] || return 1
    for p in $KNOWN_PIDS; do
        [ -d "/proc/$p" ] || return 1
    done
    return 0
}

while true; do
    if ! check_alive; then
        NEW_PIDS=""
        for name in system_server surfaceflinger; do
            for pid in $(pidof "$name" 2>/dev/null); do
                echo -1000 > "/proc/$pid/oom_score_adj" 2>/dev/null
                NEW_PIDS="$NEW_PIDS $pid"
            done
        done
        for pid in $(pgrep -f "com.google.android.gms" 2>/dev/null); do
            echo -900 > "/proc/$pid/oom_score_adj" 2>/dev/null
            NEW_PIDS="$NEW_PIDS $pid"
        done
        for pid in $(pgrep -f "nexuslauncher" 2>/dev/null); do
            echo -900 > "/proc/$pid/oom_score_adj" 2>/dev/null
            NEW_PIDS="$NEW_PIDS $pid"
        done
        KNOWN_PIDS="$NEW_PIDS"
    fi
    sleep 8
done
