#!/bin/bash
# gb-hub-link: keeps GBOS's Cross-Device link alive across restarts (GBOS_WAYDROID_FIXES.md 3.19/3.20).
# Forces the discovery gate lin.b -> true during initial service startup, then IMMEDIATELY
# detaches jdb so no debugger or breakpoint stays attached during normal operation.
# This prevents JDWP deadlocks, socket timeouts, and connection drop/reconnect thrashing.

HUBDIR="$(dirname "$(readlink -f "$0")")"
. "$HUBDIR/lib/gbos-env.sh" || exit 1
ADB_FIXED=${ADB_ADDR:-}      # an explicit ADB_ADDR pins the address; otherwise it follows Waydroid
ADB_ADDR=${ADB_ADDR:-$(gb_container)}
PORT=${JDWP_PORT:-8701}
APP=com.google.ambient.streaming
SVC=$APP/.multidevicesync.devicelink.dtdi.foreground.ForegroundDiscoveryService
D=$GB_RUN/hub-link
mkdir -p "$D"
# AppCast coexistence: while this lock exists an injected AppCast session is live, so the keeper
# must never force-stop ambient or attach jdb (force-stop kills the injected window; jdb's
# suspend-all freezes every thread in the process, i.e. a frozen cast). It does NOT stand down
# entirely: it runs the in-process repair ladder below, which restores the link without touching
# ambient. Fixed path (XDG-independent) so the AppCast runner can reliably create/remove it.
APPCAST_LOCK=${APPCAST_LOCK:-$GB_RUN/gb-appcast.lock}
# Created by gb-reconnect.sh: asks the keeper for a full repair even while a cast is live.
FORCE_KICK_FLAG=${FORCE_KICK_FLAG:-$GB_RUN/gb-hub-force-kick}

log() { echo "$(date +%T) [gb-hub-link] $*"; }
A() { adb -s "$ADB_ADDR" "$@"; }

# Route Play services' LAN traffic out eth1 (2026-11-02). After the Play services auto-update of
# 2026-10-29 the container pins GMS (uids 10205/1010205) to the eth0 network via a per-app preference
# (ip rule 15040 "fwmark 0x10064 ... lookup eth0"), so Nearby's WIFI_LAN upgrade left through the
# host's NAT as the laptop's own address and the phone hung up before the ConnectionRequestFrame: the Cross-Device link
# stayed BLE-only and fragile. Sending GMS -> LAN out eth1 (source = the container's LAN address) restored the upgrade
# (MEASURED: "outgoing WIFI_LAN upgrade latency 37 ms ... ENCRYPTED_WIFI_LAN", bandwidth HIGH).
# The laptop's own address is excluded: the ipvlan eth1 cannot reach its parent host.
# Rules live in the container's kernel and vanish on a container restart, so this is re-checked
# every loop; it only changes (and logs) when the subnet, host IP or GMS uids differ.
GMS_RULE_SIG=""
ensure_gms_lan_route() {
  local subnet hostip uids sig
  subnet=$(A shell "ip -4 route show dev eth1 scope link proto kernel" 2>/dev/null | awk 'NR==1{print $1}' | tr -d '\r')
  [ -n "$subnet" ] || return 0
  uids=$(A shell "pm list packages -U com.google.android.gms" 2>/dev/null | tr -d '\r' \
         | awk '$1=="package:com.google.android.gms"{sub("uid:","",$2); print $2}')
  [ -n "$uids" ] || return 0
  hostip=$(python3 -c "import ipaddress,subprocess,sys
net=ipaddress.ip_network(sys.argv[1],strict=False)
for l in subprocess.run(['ip','-4','-o','addr'],capture_output=True,text=True).stdout.splitlines():
    a=l.split()[3].split('/')[0]
    if ipaddress.ip_address(a) in net: print(a); break" "$subnet" 2>/dev/null)
  sig="$subnet|$hostip|$uids"
  # Fast path: rules already match what we installed last time (and are still present).
  if [ "$sig" = "$GMS_RULE_SIG" ] && A shell "ip rule show pref 15030" 2>/dev/null | grep "$subnet" >/dev/null; then
    return 0
  fi
  local cmd="for p in 15028 15029 15030 15031; do while ip rule del pref \$p 2>/dev/null; do :; done; done"
  local u
  for u in ${uids//,/ }; do
    [ -n "$hostip" ] && cmd="$cmd; ip rule add pref 15028 to $hostip/32 uidrange $u-$u lookup eth0"
    cmd="$cmd; ip rule add pref 15030 to $subnet uidrange $u-$u lookup eth1"
  done
  if A shell "$cmd" >/dev/null 2>&1; then
    GMS_RULE_SIG="$sig"
    log "Play services LAN route: $subnet via eth1 for uids $uids (host ${hostip:-none} excluded) — enables the Wi-Fi LAN upgrade"
  fi
}

wait_container() {
  until { [ -n "$ADB_FIXED" ] || ADB_ADDR=$(gb_container); }; adb connect "$ADB_ADDR" 2>&1 | grep -q -E "connected to|already connected" \
        && [ "$(A shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] \
        && [ "$(A shell am get-current-user 2>/dev/null | tr -d '\r')" = 10 ] \
        && [ -n "$(A shell pidof com.google.android.gms 2>/dev/null | tr -d '\r')" ]; do
    sleep 5
  done

  # Wait for fake Wi-Fi to settle
  local w
  for w in {1..15}; do
    [ "$(A shell 'cmd wifi status 2>/dev/null | grep -c "connected to"' | tr -d '\r')" = 1 ] && break
    sleep 2
  done

  # Ensure DeviceState is OPEN (2)
  A shell "cmd device_state state 2" >/dev/null 2>&1 || true
}

is_connected() {
  # The link status is the app's own ongoing notification (id=1, channel device_link_channel_id).
  # Matching the whole package block also hits the phone notifications this app mirrors, which carry their own
  # titles/texts (e.g. "Connected to: …"), so a loose match can read "Connected" while the link is down — or
  # miss it while the link is up, which makes the keeper kick a healthy session and freeze it under jdb.
  A shell "dumpsys notification --noredact" 2>/dev/null \
    | grep -A 30 "pkg=$APP .*id=1 tag=null" \
    | grep -q "android.title=String (Connected)"
}

is_app_running() {
  [ -n "$(A shell pidof $APP 2>/dev/null | tr -d '\r')" ]
}

last_radio_try=0
ensure_radio() {
  # A crashed Bluetooth stack keeps mEnable=true but parks at State: OFF and stops retrying ("Reached maximum retry to
  # restart Bluetooth"), which leaves discovery with no BLE radio — the 2026-10-29 outage. A user switching Bluetooth off
  # clears mEnable, so only that crash pattern is repaired here; a deliberate off is left alone.
  local d st en now
  now=$(date +%s)
  [ $((now - last_radio_try)) -ge 60 ] || return 0
  d=$(A shell "dumpsys bluetooth_manager" 2>/dev/null) || return 0
  st=$(printf '%s\n' "$d" | sed -n 's/^  State: *//p' | head -1 | tr -d '\r')
  en=$(printf '%s\n' "$d" | sed -n 's/^  mEnable: *//p' | head -1 | tr -d '\r')
  [ "$st" = "OFF" ] && [ "$en" = "true" ] || return 0
  last_radio_try=$now
  log "Bluetooth is OFF with mEnable=true (stack gave up); re-enabling it."
  A shell "cmd bluetooth_manager enable" >/dev/null 2>&1 || true
  sleep 8
}

last_discovery_restart=0
discovery_backoff=120
restart_discovery() {
  # CAST-SAFE repair: stop and restart ForegroundDiscoveryService inside the ALREADY-RUNNING ambient
  # process. Nothing is force-stopped, no debugger attaches, so the injected SurfaceView, the native
  # decoder, our hooks and the App Cast window all survive. The service's dtdi binding is dropped and
  # rebuilt, which is what clears a stale connection state / a failure-burst cooldown on the ambient
  # side without paying for a process restart.
  #
  # Backs off instead of retrying on a fixed 2-min timer. Measured 2026-11-01: with a flat throttle the
  # keeper restarted the service every ~2.5 min during an outage, and each restart tore down the dtdi
  # binding while GMS was still mid-handshake ("Waiting for connections"), so the repair fought the thing
  # it was repairing. The interval doubles per attempt up to 15 min and resets once the link is back.
  local now
  now=$(date +%s)
  [ $((now - last_discovery_restart)) -ge $discovery_backoff ] || return 1
  last_discovery_restart=$now
  discovery_backoff=$(( discovery_backoff * 2 ))
  [ $discovery_backoff -le 900 ] || discovery_backoff=900
  log "Cast live: restarting ForegroundDiscoveryService in place (no force-stop, window untouched; next in-place retry in ${discovery_backoff}s)."
  A shell "cmd device_state state 2" >/dev/null 2>&1 || true
  A shell "am stop-service --user 10 -n $SVC" >/dev/null 2>&1 || true
  sleep 2
  A shell "am start-foreground-service --user 10 -n $SVC" >/dev/null 2>&1 || true
  return 0
}

# On-device flight recorder (ambient/Dtdi/Nearby/activity events, rotating files in /data/local/tmp/gbrec). The
# logcat process dies with every GBOS shutdown, and the only drops worth studying are the ones nobody saw coming,
# so the keeper re-creates it whenever it is missing (checked at most once a minute; the script is idempotent
# only because we test first).
REC_SCRIPT="$(dirname "$(readlink -f "$0")")/gb-rec-start.sh"
last_rec_check=0
ensure_recorder() {
  local now; now=$(date +%s)
  [ $((now - last_rec_check)) -ge 60 ] || return 0
  last_rec_check=$now
  [ -r "$REC_SCRIPT" ] || return 0
  if [ -z "$(A shell 'ps -A -o ARGS' 2>/dev/null | grep '[g]brec/gb-rec.log')" ]; then
    A push "$REC_SCRIPT" /data/local/tmp/gb-rec-start.sh >/dev/null 2>&1 || return 0
    A shell "sh /data/local/tmp/gb-rec-start.sh" >/dev/null 2>&1 || true
    log "On-device recorder was not running; started it (logs: /data/local/tmp/gbrec)."
  fi
}

KICK_SEEN=0
last_gms_refresh=0
refresh_gms() {
  ensure_gms_lan_route   # the Wi-Fi upgrade happens during the connect this triggers; route must already be in place
  # 2026-10-30 outage: the gate kick itself was healthy (3 gate hits every cycle, radio up, the Pixel's BLE
  # advertisements arriving) but the link never formed, because Play services answered CDS's every poll with
  # `DtdiService getApiAvailability: false` — it was holding a stale view of the account's cross-device state.
  # Restarting GMS alone was not enough; restarting it AFTER any account-side change (the user toggling
  # "Cross-device services" on the Pixel) made it re-read the state and the link came up within a minute.
  # The gate kick cannot fix that, so refresh GMS once when a kick fails, at most every 10 minutes.
  # Also the repair for a cast session: this restarts GMS, NOT ambient, so a live cast survives it
  # (ambient's dtdi binding re-establishes on its own). It is the step that clears the stale Nearby
  # endpoint / "Connection cooldown ... after N failures" state measured 2026-11-01, where CDS could
  # only ever dial the Pixel over BLE (advertisement=null, psm=0, deviceToken=null) and timed out.
  local now i
  now=$(date +%s)
  # `refresh_gms force` skips the 10-minute throttle (used when a kick provably did nothing).
  [ "${1:-}" = force ] || [ $((now - last_gms_refresh)) -ge 600 ] || return 0
  last_gms_refresh=$now
  log "Kick produced no link; restarting Play services so it re-reads the account's cross-device state..."
  A shell "am force-stop com.google.android.gms" >/dev/null 2>&1 || true
  for i in {1..20}; do
    [ -n "$(A shell pidof com.google.android.gms 2>/dev/null | tr -d '\r')" ] && break
    sleep 2
  done
  # Dtdi/Nearby take a while after the GMS process is back before they answer availability polls.
  # MEASURED 2026-11-03: a gate kick 15 s after a Play services restart lands in the
  # `getApiAvailability: false` trap and every further kick re-enters it; the same sequence with ~80 s
  # of quiet before the kick connected first time. So settle here, before anything touches ambient.
  sleep "${GMS_SETTLE:-60}"
}

kick_gate() {
  ensure_gms_lan_route   # the Wi-Fi upgrade happens during the connect this triggers; route must already be in place
  log "Cross-Device not connected; initiating discovery gate kick..."
  A shell "cmd device_state state 2" >/dev/null 2>&1 || true

  # Start service with wait-for-debugger
  A shell "am force-stop $APP; am set-debug-app -w $APP; am start-foreground-service --user 10 -n $SVC >/dev/null 2>&1"
  sleep 3

  local P
  P=$(A shell pidof $APP 2>/dev/null | tr -d '\r')
  if [ -z "$P" ]; then
    log "App did not start, retrying in next cycle"
    A shell "am clear-debug-app" >/dev/null 2>&1 || true
    return 1
  fi

  A forward tcp:$PORT jdwp:$P >/dev/null 2>&1 || true
  rm -f "$D/in" "$D/out"
  mkfifo "$D/in"
  : > "$D/out"

  timeout 25 jdb -attach localhost:$PORT < "$D/in" > "$D/out" 2>&1 &
  local J=$!
  exec 7>"$D/in"

  local i
  for i in {1..30}; do
    grep -q "Initializing jdb" "$D/out" 2>/dev/null && break
    sleep 0.1
  done

  echo "stop in lin.b" >&7
  sleep 2
  echo resume >&7
  A shell "am clear-debug-app" >/dev/null 2>&1

  log "jdb attached (pid $P), watching for initial gate flow events..."
  local seen=0 h dur=15
  for i in $(seq 1 $dur); do
    h=$(grep -c "Breakpoint hit.*lin\.b" "$D/out" 2>/dev/null || true)
    if [ "$h" -gt "$seen" ]; then
      echo "set this.b = true" >&7
      sleep 0.4
      echo cont >&7
      seen=$h
    fi
    sleep 1
  done

  # Clean detach: clear breakpoint, resume all threads, and exit jdb
  echo "clear lin.b" >&7 2>/dev/null || true
  sleep 0.2
  echo resume >&7 2>/dev/null || true
  sleep 0.2
  echo exit >&7 2>/dev/null || true
  exec 7>&-

  wait $J 2>/dev/null || true
  kill -9 $J 2>/dev/null || true
  rm -f "$D/in"
  A forward --remove tcp:$PORT >/dev/null 2>&1 || true

  KICK_SEEN=$seen
  log "jdb fully detached. Gate forced $seen time(s)."
  sleep 3

  if is_connected; then
    log "SUCCESS: Cross-Device link is CONNECTED to companion device."
  else
    log "Gate kick complete; waiting for discovery/handshake."
  fi
}

last_cast_notice=0
repair_cast_mode() {
  # The cast-safe ladder. Ordered least- to most-disruptive, and none of its steps touch ambient:
  #   1. ensure_radio      — a Bluetooth stack that gave up parks at State: OFF; restarting it does not
  #                          affect the cast, which rides the LAN, but discovery needs the BLE radio.
  #   2. restart_discovery — in-process stop/start of ForegroundDiscoveryService (above).
  #   3. refresh_gms       — clears a stale cross-device state / cooldown; ambient re-binds afterwards.
  # kick_gate() is deliberately NOT reachable from here: force-stop + jdb suspend-all is exactly what
  # must not happen while a window is on screen. A full kick still runs the moment the cast closes.
  local now m
  now=$(date +%s)

  ensure_radio

  # 2026-11-01: restart_discovery is intentionally NOT used here any more. A restarted
  # ForegroundDiscoveryService is a new instance whose gate (lin.b) is false again, and nothing in this
  # path can force it (no jdb during a cast), so the restart threw away the forced gate and could never
  # link. Play services was the only in-place step that ever helped.
  refresh_gms
  for m in {1..18}; do
    if is_connected; then
      discovery_backoff=120
      log "SUCCESS: Cross-Device link restored during a live cast (Play services refresh, window preserved)."
      return 0
    fi
    sleep 5
  done

  # Still down. Do not escalate to a process restart while the user is looking at a stream; say so
  # once per 15 min instead of every cycle, and let the normal kick path run when the lock clears.
  if [ $((now - last_cast_notice)) -ge 900 ]; then
    last_cast_notice=$now
    log "Cast live and link still down after the in-place repair; leaving ambient alone (full gate kick deferred until the cast closes)."
  fi
  return 1
}

# Failed-kick backoff. 2026-11-03 19:00-19:14: with the link down and no cast live, every loop pass kicked the
# gate again (each kick force-stops ambient) and restarted Play services, ~every 100 s for 14 minutes; each
# fresh ambient re-entered the `getApiAvailability: false` trap, so the churn itself kept the link down. After a
# kick that produced no link, leave everything alone for a growing interval: 4, 8, 16, then 30 min.
# (A widget Reconnect, via FORCE_KICK_FLAG, always goes straight through.)
kick_fail_streak=0
next_kick_after=0
note_kick_result() {
  if is_connected; then
    kick_fail_streak=0; next_kick_after=0
  else
    kick_fail_streak=$((kick_fail_streak + 1))
    local wait=$((240 << (kick_fail_streak - 1)))
    [ "$wait" -gt 1800 ] && wait=1800
    next_kick_after=$(( $(date +%s) + wait ))
    log "Kick #$kick_fail_streak produced no link; leaving ambient and Play services alone for $((wait / 60)) min."
  fi
}

log "Starting GBOS Cross-Device link keeper..."

while true; do
  wait_container
  ensure_gms_lan_route
  ensure_recorder

  # AppCast coexistence. A live cast forbids force-stop and jdb (see APPCAST_LOCK above), but it must
  # not mean "do nothing": on 2026-11-01 the lock was present for the whole session and CDS sat down for
  # 2 h with a perfectly healthy cast, because the keeper had opted out entirely. So: keep watching, and
  # repair the LINK in place when it drops.
  # Explicit request (widget Reconnect / gb-reconnect.sh): do the repair that actually works - refresh
  # Play services, then the full gate kick - even with a cast live. The kick force-stops ambient; the
  # AppCast supervisor sees the new pid and re-attaches by itself (~45 s, one visible flash).
  if [ -e "$FORCE_KICK_FLAG" ]; then
    rm -f "$FORCE_KICK_FLAG"
    if is_connected; then
      log "Reconnect requested but the link is already CONNECTED; nothing to do."
    else
      log "Reconnect requested: refreshing Play services, then a full gate kick (cast will re-attach)."
      kick_fail_streak=0; next_kick_after=0     # an explicit request clears any backoff
      refresh_gms force
      kick_gate || true
      for m in {1..12}; do is_connected && { log "Confirmed: Link established after requested reconnect."; break; }; sleep 5; done
    fi
    continue
  fi

  if [ -e "$APPCAST_LOCK" ]; then
    if is_connected; then
      discovery_backoff=120
      ensure_radio
      sleep 15
      continue
    fi

    # Link is down with a cast live. A single miss can be the notification being re-posted, and CDS
    # suppresses retries for ~1 min after a burst of failures, so observe before repairing anything.
    ensure_radio
    sleep 30
    if is_connected; then
      sleep 5
      continue
    fi

    log "AppCast session active and Cross-Device link is DOWN — repairing in place (cast preserved)."
    repair_cast_mode || true
    sleep 15
    continue
  fi

  if is_connected; then
    # Steady state: connected and healthy, do not interfere! (Still check the radio, cheaply, so a stack that dies
    # later is noticed before the link does.)
    ensure_radio
    sleep 15
    continue
  fi

  # No link: make sure there is a radio before kicking the discovery gate.
  ensure_radio

  # A kick recently failed: give the link a quiet interval (see the backoff note above).
  if [ "$(date +%s)" -lt "$next_kick_after" ]; then
    sleep 15
    continue
  fi

  # Not connected: kick the gate once and let the connection run natively.
  # A single miss can be the app refreshing that notification mid-update, and a kick is expensive (jdb freezes every
  # thread at each gate hit), so look again before taking the link apart.
  sleep 10
  if is_connected; then
    sleep 5
    continue
  fi
  kick_gate || true

  # A kick that forced the gate 0 times produced nothing to settle (measured 17:07 and 19:41 on 2026-11-01:
  # every 0-force kick was followed by a successful 3-force kick right after a Play services restart).
  # Do that restart now instead of waiting out the settle loop below.
  if [ "$KICK_SEEN" = 0 ] && ! is_connected; then
    log "Gate was forced 0 times: refreshing Play services and kicking again straight away."
    refresh_gms force
    kick_gate || true
  fi

  # Monitor connection settling
  for m in {1..12}; do
    if is_connected; then
      log "Confirmed: Link established and active."
      break
    fi
    sleep 5
  done

  # Still down after a full kick: the likely cause is a stale cross-device state inside Play services, which the
  # gate kick cannot repair. Nudge GMS, then back off (note_kick_result) before kicking again.
  if ! is_connected; then
    refresh_gms
    sleep 10
  fi
  note_kick_result
done
