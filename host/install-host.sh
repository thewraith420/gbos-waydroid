#!/bin/bash
# Install (or remove) the host-side GBOS services.
#
#   host/install-host.sh phonehub [--no-start] [--work DIR] [--touch-jar FILE] [--prefix DIR]   as your normal user
#   sudo host/install-host.sh system [lan] [wifi] [bt]                              as root
#   host/install-host.sh <phonehub|system ...> --remove
#
# Add --stage DIR to only render the files into DIR (no systemctl, no pip, no downloads); it is how the
# installer is tested.
#
# phonehub  The Phone Hub / App Cast services: Cross-Device link keeper, App Cast supervisor, audio, discovery,
#           widget link daemon, auto-stop. Needs adb on this host (see docs/TUTORIAL-adb.md) and a JDK (jdb).
#           Copies the scripts to ~/.local/share/gbos/phonehub, builds a Python venv with the pinned frida,
#           fetches the pinned frida-server (sha256-verified) and enables the user services.
# system    Root services, each opt-in:
#             lan   ipvlan NIC so phones on your LAN can see the container       (Phone Hub needs this)
#             wifi  virtual Wi-Fi radio (mac80211_hwsim) for the container        (Phone Hub needs this)
#             bt    share the host's Bluetooth adapter with the container         (optional)
#           With no component named, lan and wifi are installed.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
LOCK="$HERE/../sources.lock"
MODE="${1:-}"; [ $# -gt 0 ] && shift
REMOVE=0; START=1; STAGE=""; PREFIX=""; TOUCH_JAR=""; WORK=""; COMPONENTS=()
while [ $# -gt 0 ]; do
    case "$1" in
        --remove) REMOVE=1 ;;
        --no-start) START=0 ;;
        --stage) STAGE="$2"; shift ;;
        --prefix) PREFIX="$2"; shift ;;
        --touch-jar) TOUCH_JAR="$2"; shift ;;
        --work) WORK="$2"; shift ;;
        lan|wifi|bt) COMPONENTS+=("$1") ;;
        -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done

say()  { printf '%s\n' "$*"; }
die()  { printf 'error: %s\n' "$*" >&2; exit 1; }
ctl()  { [ -n "$STAGE" ] && { say "  (stage) systemctl $*"; return 0; }; systemctl "$@"; }

# ---------------------------------------------------------------- system (root)
declare -A SYS_FILES=( [lan]="gb-lan.py" [wifi]="wifi-hwsim.sh" [bt]="gb-bt-bridge.py" )
declare -A SYS_UNIT=(  [lan]="gb-lan.service" [wifi]="gb-wifi-host.service" [bt]="gb-bt-bridge.service" )

do_system() {
    [ "${#COMPONENTS[@]}" -gt 0 ] || COMPONENTS=(lan wifi)
    local sbin=/usr/local/sbin unitdir=/etc/systemd/system c
    if [ -n "$STAGE" ]; then sbin="$STAGE/sbin"; unitdir="$STAGE/units-system"; fi
    if [ -z "$STAGE" ] && [ "$(id -u)" != 0 ]; then die "the system part needs root: sudo $0 system ${COMPONENTS[*]}"; fi
    mkdir -p "$sbin" "$unitdir"
    for c in "${COMPONENTS[@]}"; do
        local f="${SYS_FILES[$c]}" u="${SYS_UNIT[$c]}"
        if [ "$REMOVE" = 1 ]; then
            ctl disable --now "$u" 2>/dev/null || true
            [ "$c" = lan ] && [ -z "$STAGE" ] && "$sbin/gb-lan.py" --down 2>/dev/null || true
            rm -f "$unitdir/$u" "$sbin/$f"
            say "removed $c"
            continue
        fi
        case "$f" in *.py) python3 -c "import ast,sys; ast.parse(open(sys.argv[1]).read())" "$HERE/system/$f" ;; *) bash -n "$HERE/system/$f" ;; esac
        install -D -m 755 "$HERE/system/$f" "$sbin/$f"
        install -D -m 644 "$HERE/system/units/$u" "$unitdir/$u"
        say "installed $c: $sbin/$f, $unitdir/$u"
    done
    ctl daemon-reload
    if [ "$REMOVE" = 0 ]; then
        for c in "${COMPONENTS[@]}"; do ctl enable --now "${SYS_UNIT[$c]}"; done
        say ""
        say "The container halves (gb-lan.sh, gb-wifi.sh, the apps) are installed by recipe/bake-apps.sh."
        say "Restart Waydroid afterwards so the container picks everything up."
    fi
}

# ---------------------------------------------------------------- phonehub (user)
USER_UNITS=(gb-hub-link gb-appcast gb-audio gb-discover gb-link-daemon gb-autostop)

lock_field() {   # lock_field NAME COLUMN  (1-based, '|' separated)
    awk -F'|' -v n="$1" -v c="$2" '{gsub(/^ +| +$/, "", $1)} $1 == n {gsub(/^ +| +$/, "", $c); print $c; exit}' "$LOCK"
}

fetch_frida_server() {
    local url ver want size bin state
    url=$(lock_field frida_server 2); want=$(lock_field frida_server 4); size=$(lock_field frida_server 3)
    [ -n "$url" ] && [ -n "$want" ] || die "sources.lock has no frida_server entry"
    ver=$(basename "$url" | sed -E 's/frida-server-(.*)-android-x86_64\.xz/\1/')
    state="${GB_STATE:-${XDG_CACHE_HOME:-$HOME/.cache}/gbos}"
    bin="$state/frida-server-$ver-android-x86_64"
    printf '%s %s\n' "$ver" "$want" > "$PH/frida.sha256"      # appcast_up.sh checks a late download against this
    if [ -x "$bin" ]; then say "frida-server $ver already cached"; return 0; fi
    mkdir -p "$state"
    say "downloading frida-server $ver ($((size / 1048576)) MiB)"
    curl -fL --retry 3 --connect-timeout 15 -o "$bin.xz" "$url" || die "frida-server download failed"
    if [ "$(sha256sum "$bin.xz" | awk '{print $1}')" != "$want" ]; then
        rm -f "$bin.xz"; die "frida-server does not match the checksum pinned in sources.lock; refusing to use it"
    fi
    xz -d -f "$bin.xz" && chmod 755 "$bin"
    say "frida-server verified and cached: $bin"
}

do_phonehub() {
    PREFIX="${PREFIX:-${XDG_DATA_HOME:-$HOME/.local/share}/gbos}"
    local PH="$PREFIX/phonehub" udir="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user" u
    if [ -n "$STAGE" ]; then PH="$STAGE/phonehub"; udir="$STAGE/units-user"; fi
    [ "$(id -u)" != 0 ] || [ -n "$STAGE" ] || die "run the phonehub part as your normal user, not root"

    if [ "$REMOVE" = 1 ]; then
        for u in "${USER_UNITS[@]}"; do
            ctl --user disable --now "$u.service" 2>/dev/null || true
            rm -f "$udir/$u.service"
        done
        ctl --user daemon-reload
        say "services removed. Left in place (delete by hand if you want them gone): $PH, ~/.config/gbos, ~/.cache/gbos"
        return 0
    fi

    command -v adb >/dev/null || say "warning: adb not found. Install the adb package (docs/TUTORIAL-adb.md); the services need it."
    command -v jdb >/dev/null || say "note: jdb not found (optional fallback for older images without native waydroid_lid; install default-jdk-headless if needed)."
    if [ -z "$STAGE" ]; then
        python3 -c "import venv, ensurepip" 2>/dev/null || die "python3-venv is missing (Debian/Ubuntu: sudo apt install python3-venv)"
        command -v curl >/dev/null && command -v xz >/dev/null || die "curl and xz-utils are needed"
    fi

    mkdir -p "$PH"
    cp -a "$HERE/phonehub/." "$PH/"
    rm -rf "$PH/units"
    find "$PH" -name __pycache__ -prune -exec rm -rf {} + 2>/dev/null || true
    chmod 755 "$PH"/*.sh "$PH"/*.py "$PH"/injector/phone_agent.sh "$PH"/injector/resolve_cds_names.py
    if [ -n "$WORK" ]; then                       # the folder `gbos-install.py prepare` built
        [ -d "$WORK" ] || die "no such folder: $WORK"
        [ -z "$TOUCH_JAR" ] && [ -f "$WORK/touch_injector/build/gb-touch.jar" ] && TOUCH_JAR="$WORK/touch_injector/build/gb-touch.jar"
        for h in dbexec userrestrict wallset; do
            [ -f "$WORK/${h}_app/build/$h.apk" ] || die "$WORK/${h}_app/build/$h.apk is missing: run gbos-install.py prepare first"
            install -D -m 644 "$WORK/${h}_app/build/$h.apk" "$PH/helpers/$h.apk"
        done
    fi
    if [ -n "$TOUCH_JAR" ]; then
        [ -f "$TOUCH_JAR" ] || die "no such file: $TOUCH_JAR"
        install -D -m 644 "$TOUCH_JAR" "$PH/touch/gb-touch.jar"
    fi
    say "installed scripts to $PH"

    if [ -z "$STAGE" ]; then
        if [ ! -x "$PH/injector/.venv/bin/python" ]; then
            say "creating the Python environment"
            python3 -m venv "$PH/injector/.venv"
        fi
        "$PH/injector/.venv/bin/python" -m pip install -q --disable-pip-version-check -r "$PH/requirements.txt"
        fetch_frida_server
    else
        say "  (stage) skipping venv, pip and the frida-server download"
        printf '%s %s\n' "$(lock_field frida_server 2 | sed -E 's/.*frida-server-(.*)-android-x86_64\.xz/\1/')" "$(lock_field frida_server 4)" > "$PH/frida.sha256"
    fi

    mkdir -p "$udir"
    for u in "${USER_UNITS[@]}"; do
        sed "s#@PHONEHUB@#$PH#g" "$HERE/phonehub/units/$u.service" > "$udir/$u.service"
    done
    say "installed ${#USER_UNITS[@]} user units to $udir"

    local cfg="${XDG_CONFIG_HOME:-$HOME/.config}/gbos/gbos.env"
    [ -n "$STAGE" ] && cfg="$STAGE/gbos.env"
    if [ ! -e "$cfg" ]; then install -D -m 600 "$HERE/config/gbos.env.example" "$cfg"; say "wrote $cfg (all defaults commented out)"; fi

    ctl --user daemon-reload
    if [ "$START" = 1 ]; then
        for u in "${USER_UNITS[@]}"; do
            ctl --user enable "$u.service"
            ctl --user restart "$u.service"
        done
        say "started. Status:  $PH/appcast_up.sh status"
    else
        for u in "${USER_UNITS[@]}"; do ctl --user enable "$u.service"; done
        say "enabled but not started (--no-start). Start with:  systemctl --user start ${USER_UNITS[*]/%/.service}"
    fi
}

case "$MODE" in
    system)   do_system ;;
    phonehub) do_phonehub ;;
    *) sed -n '2,22p' "$0"; exit 2 ;;
esac
