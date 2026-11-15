#!/bin/bash
# gb-autostop: finish the shutdown when GBOS powers itself off. When Android shuts down from its own power menu the container (lxc-start) exits by itself,
# but the Waydroid *session* and the host-side cleanup stay up until someone runs "Stop Waydroid" (= `waydroid session stop`). This watches for exactly that
# situation and runs the same command. It only acts after it has SEEN the container running, and only if the container stays gone for ~3 s
# (so a normal start, or a manual stop, never triggers it). Runs as the logged-in user; no sudo needed.
seen=0; gone=0
log() { echo "$(date +%T) $*"; }
while true; do
  if pgrep -f "[l]xc-start -P /var/lib/waydroid/lxc" >/dev/null; then
    seen=1; gone=0
  elif [ "$seen" = 1 ]; then
    gone=$((gone + 1))
    if [ "$gone" -ge 3 ]; then                       # 3 polls x 1 s = the container has been gone for ~3 s
      if waydroid status 2>/dev/null | grep -q "^Session:.*RUNNING"; then
        log "container exited but the session is still up: running 'waydroid session stop'"
        waydroid session stop
        sleep 5
      fi
      seen=0; gone=0
    fi
  fi
  sleep 1
done
