#!/system/bin/sh
# gb-fridaserver — keep the instrumentation daemon App Cast attaches through alive, from inside the
# container.
#
# WHY THIS IS A BOOT SERVICE
# Every App Cast session attaches frida to com.google.ambient.streaming. Until now that meant a host
# step after every Waydroid (re)start: adb connect, make sure /data/local/tmp/frida-server still
# existed (a /data rebuild deletes it), start it as root, and hope it was up before the session tried
# to attach. Three of those steps had no automation, and a running cast that silently stops updating
# is exactly what a dead frida-server looks like.
#
# WHY THE BINARY IS NOT BAKED
# It is ~171 MB and version-locked to the host's frida client (17.24.3 today). Baking it would turn
# every frida upgrade into a re-bake. This service starts whatever is at $BIN, waits for the host
# supervisor (tools/appcast_up.sh) to push it when a fresh /data wiped it, and restarts it if it dies.
#
# Ordering: started at sys.boot_completed=1, like the other gb-* daemons. Nothing attaches before
# the desktop exists anyway, and zygote/app processes must be forkable before a server is useful.

BIN=/data/local/tmp/frida-server
POLL=10
state=""

log() { echo "gb-fridaserver: $*"; }

# Report only on change: this loop runs forever and an unconditional log line every 10 s would bury
# the rest of logcat.
note() {
    [ "$state" = "$1" ] && return 0
    state="$1"
    log "$*"
}

while :; do
    if [ ! -x "$BIN" ]; then
        # Not an error: a freshly rebuilt /data has no server until the host pushes one. Say so once
        # so a stale-looking "App Cast is broken" report can be traced to this line.
        note "no server at $BIN yet — waiting for the host to push it (tools/appcast_up.sh)"
        sleep "$POLL"
        continue
    fi

    if pidof frida-server >/dev/null 2>&1; then
        note "running (pid $(pidof frida-server))"
        sleep "$POLL"
        continue
    fi

    log "starting $BIN -D"
    # -D daemonizes: the server is re-parented to init, so dropping the adb shell (or the whole adb
    # device) cannot take it down with the command that started it.
    setsid "$BIN" -D >/dev/null 2>&1
    sleep 2
    if pidof frida-server >/dev/null 2>&1; then
        note "running (pid $(pidof frida-server))"
    else
        note "FAILED to start $BIN — is it the right arch/version?"
    fi
    sleep "$POLL"
done
