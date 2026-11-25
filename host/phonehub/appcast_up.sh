#!/bin/bash
# appcast_up.sh — the host half of App Cast, as one command.
#
# WHY THIS EXISTS
# Starting App Cast was a sequence of steps that all had to be right and in order, and several of them
# failed silently when they were not:
#
#   1. Waydroid has to be up and Android booted as user 10    (otherwise adb talks to nothing)
#   2. the container needs an adb connection, with the right vendor key
#   3. frida-server must be running INSIDE the container       (container-side now: gb-fridaserver)
#   4. Cross Device Services must be Connected first           (the keeper can only fix it while we
#                                                              do not hold its stand-down lock)
#   5. ambient must be running, and must stay the SAME process (the session attaches to a pid)
#   6. exactly one appcast_session.py, with the phone's address (a stale one is a silent dead cast)
#
# Steps 1-5 are now checked and waited for here, in that order, and the session is restarted if it
# exits or if ambient is replaced underneath it.
#
# USAGE
#   appcast_up.sh                 bring everything up and stay resident (this is the service's ExecStart)
#   appcast_up.sh status          one screen: waydroid, adb, frida, CDS, ambient, session, listeners
#   appcast_up.sh stop            clean stop of the supervisor and its session (Ctrl-C equivalent)
#   appcast_up.sh --dry-run       do every check and every prerequisite, but do not start the session
#   appcast_up.sh --takeover      if a session started by hand is running, stop it first
#
# Env knobs: GB_BOOT_TIMEOUT (s, default 600) · GB_CDS_TIMEOUT (s, default 240) · GB_PHONE_IP
# (skip phone discovery) · GB_APPCAST_ONCE=1 (bring up, then exit without supervising).
set -uo pipefail

W="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$W/lib/gbos-env.sh" || exit 1
APPDIR="$W/injector"
PY="$APPDIR/.venv/bin/python"
PKG=com.google.ambient.streaming
FGS="$PKG/.multidevicesync.devicelink.dtdi.foreground.ForegroundDiscoveryService"
LOCK=$GB_RUN/gb-appcast.lock
PIDFILE=$GB_RUN/gb-appcast-supervisor.pid
PHONE_FILE=$GB_RUN/gb-appcast-phone-ip          # written by host_relay when the phone connects
SUPLOG=${GB_APPCAST_SUPERVISOR_LOG:-$GB_RUN/gb-appcast-supervisor.log}
SESSLOG=${GB_APPCAST_SESSION_LOG:-$GB_RUN/gb-appcast-session.log}
STATE=$GB_STATE
BOOT_TIMEOUT=${GB_BOOT_TIMEOUT:-600}
CDS_TIMEOUT=${GB_CDS_TIMEOUT:-240}
# How long a launched window gets to feed its first frame before it counts as the 0-frame failure.
FRAME_DEADLINE=${GB_FRAME_DEADLINE:-120}
# Consecutive empty launches before the supervisor stops trying (each one restarts ambient, so an
# unattended loop here would be a restart storm).
MAX_EMPTY_LAUNCHES=${GB_MAX_EMPTY_LAUNCHES:-5}

# adb refuses the container's key unless the key that authorized it is loaded. By default that is
# ~/.android/adbkey; set GB_ADB_KEY in ~/.config/gbos/gbos.env if you authorized a different one.
[ -n "${GB_ADB_KEY:-}" ] && export ADB_VENDOR_KEYS="$GB_ADB_KEY:$HOME/.android/adbkey"

DRY_RUN=0
TAKEOVER=0
SESS_PID=""
SESS_AMBIENT=""
stopping=0

# ---------------------------------------------------------------- plumbing

# stderr, not stdout: several helpers are called as $(...) and their real output is a pid or an
# address. Progress lines must never end up inside that value.
log() { printf '%s [up] %s\n' "$(date +%T)" "$*" | tee -a "$SUPLOG" >&2; }

A() { adb -s "$CONT" "$@" 2>/dev/null; }

find_container() { gb_container; }     # asked from Waydroid; see lib/gbos-env.sh

ambient_pid() {
    A shell pidof "$PKG" | tr -d '\r' | awk '{print $1}'
}

session_pids() {
    # Sessions started by hand are often `cd appcast_injector && ./.venv/bin/python appcast_session.py`,
    # so match the script name rather than one absolute path — but only on a python command line, so an
    # editor, a tail or a log line mentioning it is never mistaken for a session.
    ps -eo pid=,args= | awk -v me="$$" '$0 ~ /python/ && $0 ~ /appcast_session\.py/ && $1 != me {print $1}'
}

# ---------------------------------------------------------------- prerequisites

wait_boot() {
    local deadline=$(( SECONDS + BOOT_TIMEOUT )) said=""
    while :; do
        CONT=$(gb_container)        # the container's address is DHCP-assigned; follow it across Waydroid restarts
        if ! adb devices | awk -v c="$CONT" '$1==c && $2=="device" {found=1} END {exit !found}'; then
            [ "$said" != "connect" ] && { log "waiting for the container at $CONT (adb connect)"; said="connect"; }
            adb connect "$CONT" >/dev/null 2>&1
        else
            local booted user
            booted=$(A shell getprop sys.boot_completed | tr -d '\r')
            user=$(A shell am get-current-user | tr -d '\r')
            if [ "$booted" = "1" ] && [ "$user" = "10" ]; then
                [ "$said" != "ready" ] && { log "container ready: $CONT, Android booted, current user 10"; said="ready"; }
                return 0
            fi
            [ "$said" != "boot" ] && { log "container reachable; waiting for boot_completed/user 10 (boot=$booted user=$user)"; said="boot"; }
        fi
        if [ "$SECONDS" -ge "$deadline" ]; then
            log "FATAL: container not ready after ${BOOT_TIMEOUT}s — is Waydroid running?"
            return 1
        fi
        sleep 3
    done
}

frida_client_version() { "$PY" -c 'import frida; print(frida.__version__)' 2>/dev/null; }

fetch_frida_server() {
    # A rebuilt /data has no frida-server, and it is ~171 MB of version-locked binary: cache it on the
    # host once and push it back whenever the container has lost it.
    local ver="$1" bin="$STATE/frida-server-$ver-android-x86_64"
    if [ -x "$bin" ]; then echo "$bin"; return 0; fi
    command -v curl >/dev/null || { log "WARN: no curl, cannot fetch the frida-server binary"; return 1; }
    mkdir -p "$STATE"
    local url="https://github.com/frida/frida/releases/download/$ver/frida-server-$ver-android-x86_64.xz"
    log "container has no frida-server; downloading $ver (once, cached in $STATE)"
    if ! curl -fL --retry 3 --connect-timeout 15 -o "$bin.xz" "$url"; then
        log "WARN: download failed ($url)"; return 1
    fi
    # This binary runs as root inside the container: never run one that does not match the pinned checksum.
    local want; want=$(awk -v v="$ver" '$1 == v {print $2; exit}' "$W/frida.sha256" 2>/dev/null)
    if [ -z "$want" ]; then
        log "WARN: no pinned checksum for frida-server $ver in $W/frida.sha256; refusing to use it"
        rm -f "$bin.xz"; return 1
    fi
    if [ "$(sha256sum "$bin.xz" | awk '{print $1}')" != "$want" ]; then
        log "WARN: frida-server $ver does not match its pinned checksum; refusing to use it"
        rm -f "$bin.xz"; return 1
    fi
    if ! ( xz -d -f "$bin.xz" 2>/dev/null || unxz -f "$bin.xz" 2>/dev/null ); then
        log "WARN: could not decompress $bin.xz (install xz-utils)"; return 1
    fi
    chmod 755 "$bin"; echo "$bin"
}

ensure_frida() {
    local ver bin
    ver=$(frida_client_version)
    [ -n "$ver" ] || { log "WARN: the venv python cannot import frida; skipping the frida-server check"; return 0; }

    if [ -z "$(A shell pidof frida-server)" ]; then
        if [ "$(A shell '[ -x /data/local/tmp/frida-server ] && echo yes')" = "yes" ]; then
            log "frida-server is not running but exists in the container; starting it"
        else
            bin=$(fetch_frida_server "$ver") || return 1
            log "pushing $(basename "$bin") to the container (this takes a few seconds)"
            adb -s "$CONT" push "$bin" /data/local/tmp/frida-server >/dev/null 2>&1 || {
                log "WARN: push failed"; return 1; }
        fi
        A shell "chmod 755 /data/local/tmp/frida-server"
        # gb-fridaserver would also start it at boot; doing it here keeps a session that is being
        # restarted mid-login from waiting for a boot event that already happened.
        A shell "setsid /data/local/tmp/frida-server -D >/dev/null 2>&1"
        local i
        for i in $(seq 1 20); do
            [ -n "$(A shell pidof frida-server)" ] && break
            sleep 0.5
        done
    fi

    local spid sver
    spid=$(A shell pidof frida-server | tr -d '\r')
    if [ -z "$spid" ]; then
        log "WARN: frida-server did not come up; an App Cast session cannot attach without it"
        return 1
    fi
    sver=$(A shell /data/local/tmp/frida-server --version | tr -d '\r')
    if [ "$sver" != "$ver" ]; then
        log "WARN: frida version mismatch — client $ver, server $sver; the client will refuse to attach"
    fi
    # The only proof that matters: can the host client actually see the container's processes?
    if ! "$PY" - "$CONT" <<'EOF' >/dev/null 2>&1
import sys, frida
frida.get_device_manager().get_device(sys.argv[1]).enumerate_processes()
EOF
    then
        log "WARN: frida client cannot enumerate processes on $CONT yet (server still starting?)"
        return 1
    fi
    log "frida-server up (pid $spid, $sver), client can enumerate the container"
}

cds_connected() {
    # Same signal gb-hub-link uses: the app's own ongoing notification (id=1). A loose match can read
    # "Connected" from a mirrored phone notification, so keep the package+id scoping.
    A shell "dumpsys notification --noredact" \
        | grep -A 30 "pkg=$PKG .*id=1 tag=null" | grep "android.title=String (Connected)" >/dev/null
}

wait_cds() {
    # Connected has to be STABLE, not a single sample. MEASURED 2026-10-30 23:35: the keeper was
    # mid-repair with jdb attached to ambient, the notification briefly read Connected, the supervisor
    # attached a session to that process, and ambient died 0 frames in. Three samples 6 s apart means
    # the keeper has finished its gate kick (it detaches jdb) and the link stayed up.
    local deadline=$(( SECONDS + CDS_TIMEOUT )) said="" stable=0
    while [ "$SECONDS" -lt "$deadline" ]; do
        if ! pgrep -f "jdb -attach" >/dev/null 2>&1 && cds_connected; then
            stable=$((stable + 1))
            if [ "$stable" -ge 3 ]; then
                log "Cross Device Services: Connected (stable for ~12s, jdb detached)"
                return 0
            fi
        else
            stable=0
        fi
        [ "$said" != "cds" ] && {
            log "waiting up to ${CDS_TIMEOUT}s for Cross Device Services (gb-hub-link owns it until we hold $LOCK)"
            said="cds"; }
        sleep 4
    done
    log "WARN: CDS did not hold Connected for ~18s yet."
    log "      Leaving keeper unlocked; supervisor will retry until CDS connects."
    return 1
}

ensure_ambient() {
    local pid
    pid=$(ambient_pid)
    if [ -z "$pid" ]; then
        log "ambient is not running; starting $FGS"
        A shell "am start-foreground-service --user 10 -n $FGS"
        local i
        for i in $(seq 1 40); do
            sleep 0.5
            pid=$(ambient_pid)
            [ -n "$pid" ] && break
        done
    fi
    [ -n "$pid" ] || { log "FATAL: ambient is not running and would not start"; return 1; }
    echo "$pid"
}

kill_stale_relays() {
    pkill -f 'host_relay\.py' 2>/dev/null || true
    pkill -f 'phone_rearm\.py' 2>/dev/null || true
    pkill -f 'input_relay\.py' 2>/dev/null || true
    rm -f $GB_RUN/gb-input-relay.pid
}

resolve_phone() {
    # --phone-app dials us, so the phone needs no adb for the video path. The gesture relay and the
    # Re-arm server do need an address, and wireless debugging is normally off, so: explicit env,
    # then adb (a paired phone is in `adb devices`), then the last address we saw on the CURRENT subnet.
    local serial ip
    local lan_ip local_sub
    lan_ip=$(gb_lan_ip)
    local_sub=$(echo "${lan_ip:-0.0.0.0}" | cut -d. -f1-3)

    # An explicit address only counts while it is on THIS network. A supervisor started at home with
    # GB_PHONE_IP=192.0.2.99 kept that pin on another network and aimed every phone connection at nothing.
    if [ -n "${GB_PHONE_IP:-}" ]; then
        if [ "$(echo "$GB_PHONE_IP" | cut -d. -f1-3)" = "$local_sub" ]; then echo "$GB_PHONE_IP"; return 0; fi
        log "ignoring GB_PHONE_IP=$GB_PHONE_IP: not on the current subnet ($local_sub.x) — discovering instead"
    fi

    serial=$(adb devices 2>/dev/null | awk -v c="$CONT" '$2=="device" && $1!=c {print $1; exit}')
    if [ -n "$serial" ]; then
        ip=${serial%%:*}
        if [ "$(echo "$ip" | cut -d. -f1-3)" = "$local_sub" ]; then
            mkdir -p "$STATE"; echo "$ip" >"$STATE/phone-ip"
            log "phone $serial found in adb devices (its adb is only needed to start the gesture agent)"
            echo "$ip"; return 0
        fi
    fi

    # The phone pushes video to us, so host_relay learns its address from that connection.
    if [ -s "$PHONE_FILE" ]; then
        ip=$(cat "$PHONE_FILE")
        if [ "$(echo "$ip" | cut -d. -f1-3)" = "$local_sub" ]; then
            mkdir -p "$STATE"; echo "$ip" >"$STATE/phone-ip"
            log "phone address learned from its video connection: $ip"
            echo "$ip"; return 0
        fi
    fi

    # Check ARP cache / ip neigh for candidate on current subnet answering on port 8099
    local arp_ip
    for arp_ip in $(ip -4 neigh show dev "$(gb_lan_iface)" 2>/dev/null | awk -v pfx="$local_sub" '$1 ~ pfx && ($4=="REACHABLE" || $4=="STALE" || $4=="DELAY") && $1 !~ /\.(1|254)$/ {print $1}'); do
        if python3 -c "import socket; s=socket.create_connection(('$arp_ip', 8099), timeout=0.3); s.close()" 2>/dev/null; then
            mkdir -p "$STATE"; echo "$arp_ip" >"$STATE/phone-ip"
            log "phone found via ARP & port 8099 probe: $arp_ip"
            echo "$arp_ip"; return 0
        fi
    done

    # Check cached address ONLY if on the current subnet
    if [ -s "$STATE/phone-ip" ]; then
        ip=$(cat "$STATE/phone-ip")
        if [ "$(echo "$ip" | cut -d. -f1-3)" = "$local_sub" ]; then
            log "using cached address on current subnet: $ip"
            echo "$ip"; return 0
        fi
    fi
    log "WARN: no phone address on current subnet ($local_sub) yet — video still works (the app dials us)"
}

watch_phone() {
    # Started only when the session came up without an address. The video connection always happens,
    # so the address appears within seconds of the phone casting; the Re-arm server then exists, and
    # the app's button can bring gestures back with no IP typed anywhere.
    while :; do
        local ip
        ip=$(cat "$PHONE_FILE" 2>/dev/null)
        if [ -n "$ip" ]; then
            mkdir -p "$STATE"; echo "$ip" >"$STATE/phone-ip"
            if ss -ltn 2>/dev/null | grep ':8091 ' >/dev/null; then
                log "phone $ip connected; a re-arm server is already listening on 8091"
            else
                log "phone $ip connected; starting the re-arm server so the app's button can restore touch"
                "$PY" "$APPDIR/phone_rearm.py" --phone "$ip" \
                    --token "$( "$PY" -c 'import secrets;print(secrets.token_hex(8))' )" \
                    >>$GB_RUN/appcast_rearm.log 2>&1 &
            fi
            return 0
        fi
        sleep 3
    done
}

# ---------------------------------------------------------------- the session

start_session() {
    local phone=$1 fresh=${2:-0}
    local args=(--no-restart --phone-app --input-agent)
    kill_stale_relays
    # ALWAYS pass --no-restart here: stop_session (--safe-stop) already force-stopped and restarted
    # ambient before wait_cds ran, and wait_cds just waited for gb-hub-link to connect CDS on this
    # exact ambient PID. Restarting ambient again here after wait_cds kills the newly connected CDS link!
    local note=""
    [ -n "$phone" ] && args+=(--phone-ip "$phone")
    # GB_OPTS passes a FEED_OPTS JSON through to the session, e.g. GB_OPTS='{"ownDecoder":1}' to run
    # the native NDK AMediaCodec decoder (GB_NDEC) instead of the app's jys path.
    [ -n "${GB_OPTS:-}" ] && { args+=(--opts "$GB_OPTS"); note="$note (opts $GB_OPTS)"; }
    # A cycle caused by a Phone Hub click has to cast the app the user clicked, not the default.
    # appcast_session writes this file only when its in-place agent recycle failed; reading it here
    # is what makes the fallback path correct rather than "opens Settings again".
    if [ -f $GB_RUN/gb-appcast-recast-target ]; then
        local tgt; tgt=$(tr -d '[:space:]' < $GB_RUN/gb-appcast-recast-target 2>/dev/null)
        if [ -n "$tgt" ]; then
            args+=(--target "$tgt")
            note=" (recast target $tgt)"
            rm -f $GB_RUN/gb-appcast-recast-target
        fi
    fi

    # --no-restart: attach to the ambient that CDS is already living in. Force-stopping it here would
    # take the Cross-Device link down with it (see APPCAST_HANDOFF.md §3.1).
    touch "$LOCK"
    { echo; echo "===== $(date '+%F %T') session start: ${args[*]} ====="; } >>"$SESSLOG"
    SESS_OFFSET=$(wc -l <"$SESSLOG" 2>/dev/null || echo 0)
    "$PY" "$APPDIR/appcast_session.py" "${args[@]}" >>"$SESSLOG" 2>&1 &
    SESS_PID=$!
    SESS_TS=$(date +%s)
    FRAMES_OK=0
    log "session started (pid $SESS_PID)${note} — log: $SESSLOG"
    sleep 5
    SESS_AMBIENT=$(ambient_pid)
    log "attached to ambient pid ${SESS_AMBIENT:-?}"
}

frames_since_start() {
    tail -n +"$(( ${SESS_OFFSET:-0} + 1 ))" "$SESSLOG" 2>/dev/null | grep -aq 'frame#'
}

stream_ended_since_start() {
    # ONLY the user closing the window. "stream ended: AMBIENT DIED" also contains "stream ended",
    # and treating that as "user closed it, stand by" is how the 23:35 incident got misread.
    tail -n +"$(( ${SESS_OFFSET:-0} + 1 ))" "$SESSLOG" 2>/dev/null | grep -aq 'stream-end (surface-released)'
}

stop_session() {
    kill_stale_relays
    [ -n "$SESS_PID" ] || return 0
    kill -0 "$SESS_PID" 2>/dev/null || { kill_stale_relays; return 0; }
    # SIGINT is the session's clean stop: it force-stops ambient before detaching, which is the only
    # teardown measured not to fault the unloaded agent (and take CDS down with it).
    log "stopping session (pid $SESS_PID) with SIGINT for a clean teardown"
    kill -INT "$SESS_PID" 2>/dev/null
    local i
    for i in $(seq 1 60); do
        kill -0 "$SESS_PID" 2>/dev/null || { log "session stopped"; kill_stale_relays; return 0; }
        sleep 1
    done
    log "session ignored SIGINT for 60s; sending SIGKILL"
    kill -9 "$SESS_PID" 2>/dev/null
    sleep 1
    kill_stale_relays
}

release_lock() {
    # Never leave the keeper's stand-down lock behind between restarts: while it exists, gb-hub-link
    # will not repair the Cross-Device link.
    rm -f "$LOCK"
}

cleanup() { release_lock; rm -f "$PIDFILE"; kill_stale_relays; }

on_signal() {
    stopping=1
    log "stop requested"
    stop_session
    cleanup
    exit 0
}

supervise() {
    local phone fails=0 delay=5 watch="" fresh=0 outcome nmfails=0
    while :; do
        if ! wait_boot; then sleep 30; continue; fi
        ensure_frida
        # Order matters, and a restart after a container reboot is where it bites: CDS has to be given
        # the chance to come up while the keeper is free to repair it (no lock yet). MEASURED
        # 2026-10-30 23:30: without this wait, the first post-bake session attached to an ambient whose
        # CDS had not connected yet, and the lock then kept the keeper from doing anything about it.
        if ! wait_cds; then
            fails=$((fails + 1))
            delay=15
            log "CDS not connected yet; waiting for keeper before session attach..."
            sleep "$delay"
            continue
        fi
        ensure_ambient >/dev/null

        # Re-resolve on every (re)start: the phone may have shown up on the video relay since the
        # last attempt, which is exactly the address the gesture path needs.
        phone=$(resolve_phone)
        start_session "$phone" "$fresh"
        fails=0; delay=5; outcome=""
        [ -z "$phone" ] && { watch_phone & watch=$!; }

        # Watch the session and the process it is attached to. A session that outlives an ambient
        # restart is attached to nothing, and it will sit there looking healthy. So does a session
        # whose App Cast window armed llz.a and never got an Establish (the documented 0-frame
        # failure) — the process is fine, the picture is simply never fed.
        while :; do
            sleep 5
            if [ "$FRAMES_OK" = 0 ] && frames_since_start; then
                FRAMES_OK=1
                log "stream is live (frames fed)"
            fi
            if stream_ended_since_start; then
                # The window is gone. That is the user closing it (a legitimate stop, even before the
                # first frame — they may just not want the cast), so never relaunch it behind their
                # back: keep the session and stand by. `status` reports the idle state.
                log "App Cast window closed — session kept, window not relaunched; open App Cast in GBOS to resume"
                outcome=idle
                break
            fi
            if [ "$FRAMES_OK" = 0 ] && [ $(( $(date +%s) - SESS_TS )) -ge "$FRAME_DEADLINE" ]; then
                log "no frames ${FRAME_DEADLINE}s after launch — window armed but never established; recovering"
                outcome=noframes
                stop_session
                break
            fi
            if [ -n "$SESS_PID" ] && ! kill -0 "$SESS_PID" 2>/dev/null; then
                wait "$SESS_PID" 2>/dev/null
                log "session exited (see $SESSLOG); restarting in ${delay}s"
                break
            fi
            local cur; cur=$(ambient_pid)
            if [ -n "$SESS_AMBIENT" ] && [ -n "$cur" ] && [ "$cur" != "$SESS_AMBIENT" ]; then
                log "ambient was replaced (pid $SESS_AMBIENT -> $cur); re-attaching"
                stop_session
                break
            fi
            if [ -z "$cur" ]; then
                log "ambient disappeared; stopping session and cleaning up relays before restart"
                stop_session
                kill_stale_relays
                break
            fi
        done

        if [ "$outcome" = idle ]; then
            # Waiting, not restarting: the user can reopen the window, and restarting the session here
            # would force-stop ambient (taking CDS down) for no reason.
            log "standing by while the window is closed (gb-appcast status shows this state)"
            while :; do
                sleep 10
                if [ -n "$SESS_PID" ] && ! kill -0 "$SESS_PID" 2>/dev/null; then
                    log "session exited while idle"
                    break
                fi
                cur=$(ambient_pid)
                if [ -n "$SESS_AMBIENT" ] && [ -n "$cur" ] && [ "$cur" != "$SESS_AMBIENT" ]; then
                    log "ambient was replaced while idle (pid $SESS_AMBIENT -> $cur); re-attaching"
                    break
                fi
            done
            release_lock
            fresh=0
            continue
        fi

        # A 0-frame window is receiver state inside ambient, so the recovery is a fresh ambient; the
        # next attempt goes back to --no-restart.
        if [ "$outcome" = noframes ]; then
            fresh=1; nmfails=$((nmfails + 1))
            if [ "$nmfails" -ge "$MAX_EMPTY_LAUNCHES" ]; then
                # Retrying this fast against a live app is how a bad loop becomes an ambient restart
                # storm (and, on this container, an OOM). Stop and say what to do instead.
                log "FATAL: $nmfails launches in a row fed no frames — stopping instead of looping."
                log "       Likely the phone is not casting: open GBOS Cast and press Start casting (or"
                log "       Re-arm for touch), then: systemctl --user restart gb-appcast"
                stop_session
                cleanup
                exit 0
            fi
        else
            fresh=0
        fi
        if [ "$FRAMES_OK" = 1 ]; then nmfails=0; fi

        [ -n "$watch" ] && { kill "$watch" 2>/dev/null; watch=""; }
        release_lock
        fails=$((fails + 1))
        if [ "$fails" -ge 3 ]; then delay=60; elif [ "$fails" -ge 2 ]; then delay=20; fi
        sleep "$delay"
    done
}

# ---------------------------------------------------------------- status

status() {
    CONT=$(find_container)
    local booted user spid sver cds pid sess listeners
    if adb devices | awk -v c="$CONT" '$1==c && $2=="device" {f=1} END {exit !f}'; then
        booted=$(A shell getprop sys.boot_completed | tr -d '\r')
        user=$(A shell am get-current-user | tr -d '\r')
        spid=$(A shell pidof frida-server | tr -d '\r')
        sver=$(A shell /data/local/tmp/frida-server --version 2>/dev/null | tr -d '\r')
        pid=$(ambient_pid)
        cds=$(cds_connected && echo Connected || echo "not connected")
        printf 'container   %s  adb=connected  boot=%s  user=%s\n' "$CONT" "${booted:-?}" "${user:-?}"
        printf 'frida       pid=%s version=%s (client %s)\n' "${spid:--}" "${sver:--}" "$(frida_client_version)"
        printf 'ambient     pid=%s\n' "${pid:--}"
        printf 'CDS         %s\n' "$cds"
    else
        printf 'container   %s  adb=NOT CONNECTED\n' "$CONT"
    fi
    sess=$(session_pids | tr '\n' ' ')
    if [ -n "$sess" ]; then
        # Idle = the LAST thing in the log is a stream ending, not a session start (the log accumulates
        # across restarts).
        if awk '/session start:/{s=NR} /stream ended/{e=NR} END{exit !(e > s)}' "$SESSLOG" 2>/dev/null; then
            printf 'session     running (pid(s) %s)  IDLE — window closed, not relaunched\n' "$sess"
        else
            printf 'session     running (pid(s) %s)  log %s\n' "$sess" "$SESSLOG"
        fi
        # The injected script logs one line per frame; the supervisor's own t=Ns counter only moves
        # while the session's frame callback is wired, so the frame line is the honest answer to
        # "is the window actually moving".
        grep -a 'frame#' "$SESSLOG" 2>/dev/null | tail -1 | sed 's/^/            last frame: /'
        tail -1 "$SESSLOG" 2>/dev/null | sed 's/^/            /'
    else
        printf 'session     not running\n'
    fi
    if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE" 2>/dev/null)" 2>/dev/null; then
        printf 'supervisor  running (pid %s)\n' "$(cat "$PIDFILE")"
    else
        printf 'supervisor  not running\n'
    fi
    listeners=$(ss -ltn 2>/dev/null | awk '$4 ~ /:(8098|8099|8100)$/ {printf "%s ", $4}')
    listeners=${listeners:-none}
    printf 'listeners   %s(udp 8096 discovery)\n' "$listeners"
    if [ -e "$LOCK" ]; then
        printf 'keeper      standing down (lock %s present -> gb-hub-link is not repairing CDS)\n' "$LOCK"
    fi
}

# ---------------------------------------------------------------- entry

CONT=$(find_container)

CMD=up
for a in "$@"; do
    case "$a" in
        up|status|stop) CMD=$a ;;
    esac
done

case "$CMD" in
    status) status; exit 0 ;;
    stop)
        if [ -f "$PIDFILE" ]; then
            kill -INT "$(cat "$PIDFILE")" 2>/dev/null && log "asked supervisor $(cat "$PIDFILE") to stop"
        else
            log "no supervisor pidfile; stopping any session started by hand"
            for p in $(session_pids); do kill -INT "$p" 2>/dev/null && log "SIGINT to session $p"; done
        fi
        exit 0 ;;
esac

for a in "$@"; do
    case "$a" in
        up|status|stop) : ;;                # already picked up as CMD above
        --dry-run) DRY_RUN=1 ;;
        --takeover) TAKEOVER=1 ;;
        *) echo "unknown option: $a" >&2; exit 2 ;;
    esac
done

if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE" 2>/dev/null)" 2>/dev/null; then
    log "supervisor already running (pid $(cat "$PIDFILE")); nothing to do"
    exit 0
fi

trap on_signal INT TERM
echo $$ >"$PIDFILE"
log "starting: container=$CONT dry_run=$DRY_RUN takeover=$TAKEOVER log=$SUPLOG"

existing=$(session_pids | tr '\n' ' ')
if [ -n "${existing// /}" ]; then
    if [ "$TAKEOVER" = 1 ]; then
        log "stopping the session that is already running ($existing) so this one owns the rig"
        for p in $existing; do kill -INT "$p" 2>/dev/null; done
        for i in $(seq 1 60); do
            [ -z "$(session_pids | tr -d '\n ')" ] && break
            sleep 1
        done
        [ -z "$(session_pids | tr -d '\n ')" ] || { log "FATAL: existing session would not stop"; cleanup; exit 1; }
    else
        # Two sessions inject into the same process and fight over the same ports; the handoff rule is
        # one session, ever. Refuse rather than make that race worse.
        log "a session is already running ($existing) — pass --takeover to replace it, or use 'stop'"
        cleanup
        exit 1
    fi
fi

if [ "$DRY_RUN" = 1 ] || [ "${GB_APPCAST_ONCE:-0}" = 1 ]; then
    # Prerequisites first, in the order the handoff documents: CDS has to come up while the keeper is
    # still free to repair it, and only then do we take the lock and attach.
    wait_boot || { cleanup; exit 1; }
    ensure_frida || log "continuing without a verified frida-server"
    wait_cds || true
    pid=$(ensure_ambient) || { cleanup; exit 1; }
    log "ambient pid=$pid"
fi

if [ "$DRY_RUN" = 1 ]; then
    phone=$(resolve_phone)
    log "phone address: ${phone:-unknown (the app dials us; re-arm starts when it connects)}"
    log "dry run: every prerequisite is up; not starting the session"
    cleanup
    exit 0
fi

if [ "${GB_APPCAST_ONCE:-0}" = 1 ]; then
    phone=$(resolve_phone)
    start_session "$phone"
    log "GB_APPCAST_ONCE=1: session started, not supervising"
    rm -f "$PIDFILE"
    exit 0
fi

# supervise() waits indefinitely for Waydroid + CDS before taking the lock and attaching.
supervise
