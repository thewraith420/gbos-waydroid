#!/system/bin/sh
# gb-phone-agent.sh — phone-side GESTURE agent. Runs as uid `shell`, needs no adb afterwards.
#
# Why this exists (and why not the other two paths):
#   * `adb -s <pixel> shell input …` keeps wireless debugging on forever. No.
#   * An in-app AccessibilityService would need no adb at all, but this Pixel has Advanced
#     Protection on and it refuses to grant the accessibility role to a sideloaded app, so that
#     route cannot be enabled at all. (The app ships with it; it is permanently inert here.)
#   * This: uid `shell` is the one Android lets inject input. Start it ONCE over adb (~2 s after a
#     phone reboot), it detaches with setsid, survives adbd going away, then speaks plain LAN TCP.
#     After bootstrap, Wireless debugging can be switched off and gestures keep working.
#
# The video half that used to live here (`screenrecord` piped to nc) is GONE on purpose: video now
# comes from the GBOS Cast app via MediaProjection, which has no 180 s cap. Leaving the old loop in
# place let a `start` bind the video port the app needs and fight it for 8098.
#
#   sh /data/local/tmp/gb-phone-agent.sh start <token> [preferred_port]
#   sh /data/local/tmp/gb-phone-agent.sh status
#   sh /data/local/tmp/gb-phone-agent.sh stop
#
# `start` prints the port that actually WORKS on "AGENT_PORT=<n> AGENT_READY" — see PORT PROBE.
#
# Wire protocol (one gesture per line, token first, everything else ignored):
#   <token> tap <x> <y>
#   <token> swipe <x1> <y1> <x2> <y2> [ms]
#   <token> key <keyevent>
#   <token> launch <package>
#   <token> opennotif <hex>
#   <token> ping          (no-op, used only by the startup probe)
# Live touch (D/M/U/C lines) goes to the separate injector on TOUCH_PORT, not through this loop.
#
# PORT PROBE (why this is not just "bind and hope"):
#   MEASURED the hard way. Something on this phone already held 8097 and never accepted on it
#   (`ss` showed LISTEN with Recv-Q 5 and no `nc` process anywhere). Our listener therefore failed
#   to bind, its error went to /dev/null, and every gesture was accepted at TCP level by the alien
#   listener and silently discarded — the host saw "connected", the phone did nothing, and there
#   was no error anywhere to explain it. So `start` now proves the channel end-to-end: it starts a
#   listener, connects to ITSELF, sends "<token> ping", and only reports a port whose ping shows up
#   in the log. If the port is taken it walks up the range and reports the one that works.
#
# Security: the token is the only thing between the LAN and `input`, and the verbs are an
# ALLOWLIST. Never replace the case statement with `| sh`, which would turn this port into an
# unauthenticated remote shell — strictly worse than leaving wireless debugging on.

TMP=/data/local/tmp
PIDFILE=$TMP/gb-agent-input.pid
TOKFILE=$TMP/gb-agent-input.token
STOP=$TMP/gb-agent.stop
LOG=$TMP/gb-agent-input.log
PORTFILE=$TMP/gb-agent-input.port
# Live touch injector (touch_injector/, a java program run through app_process as this same shell user).
# The verbs above replay a gesture AFTER the finger lifts; this streams the finger live. Optional: if
# the jar was not pushed, `start` simply does not report TOUCH_PORT and the host keeps using tap/swipe.
TOUCHJAR=$TMP/gb-touch.jar
TLOG=$TMP/gb-touch.log
TOUCHPORTFILE=$TMP/gb-touch.port

# One line per applied gesture, trimmed as it grows. Without this, a dropped gesture and a dead
# phone look identical; the only way to tell was reading `ss` backlog counters over adb, which is
# exactly the dependency this agent exists to remove.
note() {
  echo "$(date +%H:%M:%S 2>/dev/null) $*" >> "$LOG" 2>/dev/null
  [ "$(wc -l < "$LOG" 2>/dev/null || echo 0)" -gt 400 ] && tail -200 "$LOG" > "$LOG.tmp" 2>/dev/null && mv "$LOG.tmp" "$LOG" 2>/dev/null
  true
}

input_loop() {
  port=$1; token=$2
  echo $$ > "$PIDFILE"
  attempts=0
  while [ ! -f "$STOP" ]; do
    start=$(date +%s 2>/dev/null || echo 0)
    # -L, NOT -l: `nc -l` serves one connection then sits on a full backlog, so later clients
    # connect successfully and their bytes are dropped. -L backgrounds each connection.
    nc -L -p "$port" 2>>"$LOG" | while read -r tok verb a b c d e; do
      [ "$tok" = "$token" ] || continue
      case "$verb" in
        ping) note "ping" ;;
        tap)
          # Digit-check everything before it reaches `input`: a malformed line must never become
          # an argument to another command.
          case "$a$b" in ''|*[!0-9]*) continue ;; esac
          note "tap $a $b"
          input tap "$a" "$b" ;;
        swipe)
          case "$a$b$c$d${e:-0}" in ''|*[!0-9]*) continue ;; esac
          note "swipe $a $b $c $d"
          input swipe "$a" "$b" "$c" "$d" "${e:-150}" ;;
        key)
          case "$a" in ''|*[!A-Z0-9_]*) continue ;; esac
          note "key $a"
          input keyevent "$a" ;;
        launch)
          case "$a" in ''|*[!a-zA-Z0-9._]*) continue ;; esac
          note "launch $a"
          # A shell `am start` is silently refused while the keyguard is up: the activity never
          # appears and the phone looks untouched (MEASURED 2026-11-01 — an idle Pixel had locked,
          # and launching Calculator from the Phone Hub grid did nothing at all). Wake, dismiss the
          # keyguard, give WindowManager a moment, and only then start.
          input keyevent KEYCODE_WAKEUP
          wm dismiss-keyguard >/dev/null 2>&1 || input keyevent 82 >/dev/null 2>&1
          sleep 0.3
          comp=$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$a" 2>/dev/null | tail -n 1)
          case "$comp" in
            */*) am start -n "$comp" >/dev/null 2>&1 || monkey -p "$a" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 ;;
            *)   monkey -p "$a" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 ;;
          esac ;;
        opennotif)
          # A notification tapped in GBOS: <a> = hex of the phone's StatusBarNotification key. The GBOS
          # Cast app's OpenNotifActivity (a notification listener) fires that notification's own tap
          # action — the deep link — and falls back to launching the app if it is gone. It is started
          # from here because shell may start activities; a background app may not (Android BAL
          # rules), while the now-foreground activity may send the notification's PendingIntent.
          case "$a" in ''|*[!0-9a-f]*) continue ;; esac
          note "opennotif ${#a} hex chars"
          input keyevent KEYCODE_WAKEUP
          wm dismiss-keyguard >/dev/null 2>&1 || input keyevent 82 >/dev/null 2>&1
          sleep 0.3
          am start -n com.gbos.cast/.OpenNotifActivity --es keyhex "$a" >/dev/null 2>&1 ;;
        stop) : > "$STOP"; break ;;
      esac
    done
    [ -f "$STOP" ] && break
    now=$(date +%s 2>/dev/null || echo 0)
    if [ "$now" = "$start" ]; then
      attempts=$((attempts + 1))
      # Died instantly, every time: the port is taken by something that ignores us.
      [ "$attempts" -ge 5 ] && note "listener on $port keeps dying (port held?) - giving up" && break
      sleep 1
    else
      attempts=0
      sleep 0.3
    fi
  done
}

# Start a listener on $1 and prove a gesture round-trips through it.
try_port() {
  port=$1; token=$2
  rm -f "$LOG" "$PIDFILE"
  setsid sh "$0" _input "$port" "$token" </dev/null >/dev/null 2>&1 &
  sleep 0.7
  echo "$token ping" | nc -w 2 127.0.0.1 "$port" >/dev/null 2>&1
  sleep 0.4
  if grep -q "ping" "$LOG" 2>/dev/null; then
    echo "$port" > "$PORTFILE"
    return 0
  fi
  # Failed: kill whatever we just started and move on.
  if [ -f "$PIDFILE" ]; then
    p=$(cat "$PIDFILE" 2>/dev/null)
    [ -n "$p" ] && kill -9 "$p" 2>/dev/null
    rm -f "$PIDFILE"
  fi
  pkill -9 -f "nc -L -p $port" 2>/dev/null
  return 1
}

# Start the live touch injector on the first free port from $1 and wait until it says it is ready.
# $2 = token, $3 = the laptop's address (optional; the injector refuses every other peer).
start_touch() {
  [ -f "$TOUCHJAR" ] || return 1
  tport=$1; ttok=$2; tallow=$3; n=0
  while [ "$n" -lt 20 ]; do
    : > "$TLOG"
    CLASSPATH="$TOUCHJAR" setsid nohup app_process /system/bin com.gbos.touch.Main "$tport" "$ttok" $tallow \
      </dev/null >"$TLOG" 2>&1 &
    w=0
    while [ "$w" -lt 12 ]; do                      # JVM start-up is ~1 s; allow ~4 s
      sleep 0.35; w=$((w + 1))
      grep -q "TOUCH_READY" "$TLOG" 2>/dev/null && { echo "$tport" > "$TOUCHPORTFILE"; return 0; }
      grep -q "TOUCH_FAIL" "$TLOG" 2>/dev/null && break
    done
    grep -q "TOUCH_FAIL no injection" "$TLOG" 2>/dev/null && return 1   # nothing a different port can fix
    pkill -9 -f "com.gbos.touch.Main $tport " 2>/dev/null
    tport=$((tport + 1)); n=$((n + 1))
  done
  return 1
}

stop_all() {
  : > "$STOP"
  pkill -9 -f 'com.gbos.touch.Main' 2>/dev/null
  rm -f "$TOUCHPORTFILE"
  if [ -f "$PIDFILE" ]; then
    p=$(cat "$PIDFILE" 2>/dev/null)
    [ -n "$p" ] && kill -9 -- "-$p" 2>/dev/null
    [ -n "$p" ] && kill -9 "$p" 2>/dev/null
    rm -f "$PIDFILE"
  fi
  p=$(cat "$PORTFILE" 2>/dev/null)
  [ -n "$p" ] && pkill -9 -f "nc -L -p $p" 2>/dev/null
  pkill -9 -f 'gb-phone-agent.sh _input' 2>/dev/null
  rm -f "$TOKFILE" "$PORTFILE"
  true
}

case "${1:-}" in
  start)
    token=$2; want=${3:-8099}; allow_ip=${4:-}
    if [ -z "$token" ]; then
      echo "usage: $0 start <token> [preferred_port] [allowed_ip]" >&2; exit 2
    fi
    stop_all
    rm -f "$STOP" "$LOG"
    echo "$token" > "$TOKFILE"
    chmod 600 "$TOKFILE"
    base=$want
    i=0
    started=""
    while [ "$i" -lt 20 ]; do
      if try_port "$base" "$token"; then started=$base; break; fi
      base=$((base + 1))
      i=$((i + 1))
    done
    if [ -z "$started" ]; then
      echo "FAILED: no free port in $want..$((want + 19))" >&2
      exit 1
    fi
    if [ "$started" != "$want" ]; then
      echo "note: $want was unusable (held by something else), using $started"
    fi
    echo "input agent listening on :$started (pid $(cat "$PIDFILE" 2>/dev/null), uid $(id -u))"
    echo "AGENT_PORT=$started AGENT_READY"
    if start_touch 8110 "$token" "$allow_ip"; then
      echo "TOUCH_PORT=$(cat "$TOUCHPORTFILE") TOUCH_READY"
    else
      echo "touch injector not started (gestures fall back to tap/swipe)"
    fi
    echo "log: $LOG"
    ;;
  _input)
    input_loop "$2" "$3"
    ;;
  touch)
    # Start (or restart) ONLY the live touch injector, leaving the running gesture agent alone.
    token=$2; allow_ip=${3:-}
    [ -z "$token" ] && { echo "usage: $0 touch <token> [allowed_ip]" >&2; exit 2; }
    pkill -9 -f 'com.gbos.touch.Main' 2>/dev/null
    if start_touch 8110 "$token" "$allow_ip"; then
      echo "TOUCH_PORT=$(cat "$TOUCHPORTFILE") TOUCH_READY"
    else
      echo "touch injector not started"
    fi
    ;;
  stop)
    stop_all
    echo "stopped"
    ;;
  status)
    echo "input pid: $(cat "$PIDFILE" 2>/dev/null || echo none)"
    echo "port: $(cat "$PORTFILE" 2>/dev/null || echo unknown)"
    ps -A -o PID,ELAPSED,ARGS 2>/dev/null | grep -E 'nc -L -p' | grep -v grep
    echo "-- last gestures --"; tail -5 "$LOG" 2>/dev/null
    ;;
  *)
    echo "usage: $0 {start <token> [port] [allowed_ip]|touch <token> [allowed_ip]|status|stop}" >&2
    exit 2
    ;;
esac
