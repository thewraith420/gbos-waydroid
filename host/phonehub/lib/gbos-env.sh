# Shared runtime configuration for the GBOS Phone Hub host scripts. Source it:  . "$W/lib/gbos-env.sh"
# Everything here can be overridden from the environment or ~/.config/gbos/gbos.env (KEY=VALUE lines).
#
#   GB_RUN        private runtime dir for locks, pid files, logs, the phone-agent token (default $XDG_RUNTIME_DIR/gbos)
#   GB_STATE      persistent cache (default ~/.cache/gbos): last phone address, downloaded frida-server
#   GB_CONTAINER  adb address of the Waydroid container (default: asked from `waydroid status`)
#   GB_LAN_IFACE  host network interface the phone is reached through (default: the default-route interface)
#   GB_BRIDGE_IP  Waydroid's host-side bridge address (default 192.168.240.1)
#
# Nothing in /tmp is used for state: it is world-writable, and one of the files held the phone-agent token.

[ -f "${GB_CONFIG:-$HOME/.config/gbos/gbos.env}" ] && {
    set -a; . "${GB_CONFIG:-$HOME/.config/gbos/gbos.env}"; set +a; }

if [ -z "${GB_RUN:-}" ]; then
    if [ -n "${XDG_RUNTIME_DIR:-}" ]; then GB_RUN="$XDG_RUNTIME_DIR/gbos"; else GB_RUN="/tmp/gbos-$(id -u)"; fi
fi
mkdir -p -m 700 "$GB_RUN" 2>/dev/null
# A directory that someone else created (possible when we fall back to /tmp) is not ours to trust.
if [ "$(stat -c %u "$GB_RUN" 2>/dev/null)" != "$(id -u)" ]; then
    echo "gbos: $GB_RUN is not owned by you; refusing to use it" >&2
    return 1 2>/dev/null || exit 1
fi
GB_STATE="${GB_STATE:-${XDG_CACHE_HOME:-$HOME/.cache}/gbos}"
GB_BRIDGE_IP="${GB_BRIDGE_IP:-192.168.240.1}"
export GB_RUN GB_STATE GB_BRIDGE_IP

# adb address of the container. Asked from Waydroid when it is running; 192.168.240.112 (the address Waydroid's
# documentation uses) is only the last resort, for when the container is stopped and we are just waiting for it.
gb_container() {
    local ip
    if [ -n "${GB_CONTAINER:-}" ]; then echo "$GB_CONTAINER"; return; fi
    ip=$(waydroid status 2>/dev/null | awk '/^IP address/ {print $NF; exit}')
    case "$ip" in
        [0-9]*.[0-9]*.[0-9]*.[0-9]*) echo "$ip:5555" ;;
        *) echo "192.168.240.112:5555" ;;
    esac
}

# Interface the default route leaves through, e.g. wlp3s0 / enp5s0.
gb_lan_iface() {
    if [ -n "${GB_LAN_IFACE:-}" ]; then echo "$GB_LAN_IFACE"; return; fi
    ip -4 route get 8.8.8.8 2>/dev/null | awk '{for (i = 1; i < NF; i++) if ($i == "dev") {print $(i + 1); exit}}'
}

# This host's LAN address (the one the phone can reach).
gb_lan_ip() {
    ip -4 route get 8.8.8.8 2>/dev/null | awk '{for (i = 1; i < NF; i++) if ($i == "src") {print $(i + 1); exit}}'
}
