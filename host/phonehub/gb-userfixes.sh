#!/bin/bash
# gb-userfixes: apply the GBOS settings that live in Android's /data (so a data reset loses them) and that Phone Hub
# needs. Idempotent: safe to run again on a working system. Run it AFTER you have signed in to the real user (user 10)
# in GBOS, once Play services has created its databases.
#
#   gb-userfixes.sh                 apply
#   gb-userfixes.sh --dry-run       show the state and the SQL, change nothing on the device
#   gb-userfixes.sh --login-screen  also copy user 10's wallpaper/theme to the login screen (cosmetic)
#   gb-userfixes.sh --skip-tuning   leave the memory-killer settings and the bloat packages alone
#
# What it does, in order:
#   1. user 0 location: remove the no_share_location restriction and turn location on. Without it Android hides the
#      Wi-Fi name from every app, including Nearby, and the cross-device link cannot use the LAN.
#   2. grant ACCESS_LOCAL_NETWORK to the Cross-device app.
#   3. Play services feature flags (rows in phenotype.db, found by NAME so they follow schema ids):
#        com.google.ambient.streaming#com.google.ambient.streaming   45815550 = true   discovery gate
#                                                                    45832059 = false  do not wait for attestation
#        com.google.android.gms.multidevice#com.google.android.gms   45742359 = true   multi-device group available
#      then refreshes the app's flag snapshot and moves any shadowing Play services snapshot aside.
#      THIS EDITS PLAY SERVICES' LOCAL DATABASE to switch on features Google has not enabled for this device. It
#      changes nothing on Google's servers; it is unsupported by Google.
#   4. MultideviceSettings.pb: App Cast and Quick Access preferences -> enabled (Play services is stopped meanwhile).
#   5. (with --login-screen) copy user 10's wallpaper and theme to user 0, which draws the login screen.
#   6. memory-killer tuning, so Android stops killing foreground apps and Play services under host memory pressure.
#   7. disable background packages that only crash-loop or poll in this container.
# Backups of everything it overwrites go to $GB_STATE/userfixes/<time>/.
#
# Also checked, but NOT fixed here because it needs the container stopped: user 10 must be the "main user" of the
# device. If it is not, the script says so; fix it with set-main-user.py (see docs/INSTALL.md).
set -u
HERE="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
. "$HERE/lib/gbos-env.sh" || exit 1

DRY=0; LOGIN=0; TUNE=1
for a in "$@"; do
    case "$a" in
        --dry-run) DRY=1 ;;
        --login-screen) LOGIN=1 ;;
        --skip-tuning) TUNE=0 ;;
        -h|--help) sed -n '2,32p' "$0"; exit 0 ;;
        *) echo "unknown option: $a" >&2; exit 2 ;;
    esac
done

A=${ADB:-$(gb_container)}
HELPERS=${GB_HELPERS:-$HERE/helpers}          # dbexec.apk userrestrict.apk wallset.apk (built by the installer)
B="$GB_STATE/userfixes/$(date +%Y%m%d-%H%M%S)"
GMS=/data/user/10/com.google.android.gms; DB=$GMS/databases/phenotype.db
APP=com.google.ambient.streaming
DBX="CLASSPATH=/data/local/tmp/dbexec.apk app_process /system/bin com.gbos.dbexec.Main"
UR="CLASSPATH=/data/local/tmp/userrestrict.apk app_process /system/bin com.gbos.userrestrict.Main"
S()   { adb -s "$A" shell "$@"; }
say() { echo "== $*"; }

adb connect "$A" >/dev/null 2>&1
[ "$(S getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] || { echo "the container at $A is not reachable or not booted (adb connect, waydroid status)"; exit 1; }
[ "$(S am get-current-user | tr -d '\r')" = 10 ] || echo "note: the current Android user is not 10; run this once user 10 is signed in and in front"
mkdir -p "$B"

if [ $DRY = 0 ]; then
    for f in dbexec userrestrict wallset; do
        [ -f "$HELPERS/$f.apk" ] || { echo "missing $HELPERS/$f.apk: install with  install-host.sh phonehub --work <the folder 'prepare' built>"; exit 1; }
    done
    for f in dbexec userrestrict wallset; do adb -s "$A" push "$HELPERS/$f.apk" /data/local/tmp/ >/dev/null; done
fi

# ---- 0. the main user (read-only check)
say "0. main user"
MAIN=$(S cmd user get-main-user 2>/dev/null | tr -d '\r')
echo "main user: ${MAIN:-unknown}"
case "$MAIN" in
    10) ;;
    *)  echo "WARNING: user 10 is not the main user. Google services ask for it. With Waydroid STOPPED run:"
        echo "    $HERE/set-main-user.py ~/.local/share/waydroid/data/system/users/10.xml --apply" ;;
esac

# ---- 1. user 0 location
say "1. user 0 location"
if [ $DRY = 1 ]; then
    echo "location_mode(user 0) now: $(S settings --user 0 get secure location_mode | tr -d '\r')   (would set 3 and clear no_share_location)"
else
    S "$UR get 0 no_share_location; echo mode0=\$(settings --user 0 get secure location_mode)"
    S "$UR set 0 no_share_location false; settings --user 0 put secure location_mode 3; cmd location set-location-enabled true --user 0; echo mode0=\$(settings --user 0 get secure location_mode)"
fi

# ---- 2. permission
say "2. ACCESS_LOCAL_NETWORK for $APP"
if [ $DRY = 1 ]; then
    echo "granted for $(S "dumpsys package $APP | grep -c 'ACCESS_LOCAL_NETWORK: granted=true'" | tr -d '\r') user(s) (0 = not granted)"
else
    S "pm grant --user 10 $APP android.permission.ACCESS_LOCAL_NETWORK 2>&1 | head -1; dumpsys package $APP | grep -c 'ACCESS_LOCAL_NETWORK: granted=true'"
fi

# ---- 3. Play services flag overrides
say "3. Play services flag overrides"
sql_for() {   # $1 = config package name, $2 = flag, $3 = 1|0
cat <<EOS
DELETE FROM experiment_states_to_overrides WHERE override_id IN (SELECT override_id FROM flag_overrides WHERE name='$2' AND config_package_id=(SELECT config_package_id FROM config_packages WHERE name='$1'));
DELETE FROM flag_overrides_to_commit WHERE override_id IN (SELECT override_id FROM flag_overrides WHERE name='$2' AND config_package_id=(SELECT config_package_id FROM config_packages WHERE name='$1'));
DELETE FROM flag_overrides WHERE name='$2' AND config_package_id=(SELECT config_package_id FROM config_packages WHERE name='$1');
INSERT INTO flag_overrides (config_package_id, account_id, active, name, type, value, source) SELECT config_package_id, account_id, 1, '$2', 2, $3, 0 FROM (SELECT config_package_id, account_id, MAX(experiment_state_id) AS id FROM experiment_states WHERE config_package_id=(SELECT config_package_id FROM config_packages WHERE name='$1') GROUP BY account_id);
INSERT INTO experiment_states_to_overrides (experiment_state_id, override_id) SELECT es.id, o.override_id FROM (SELECT config_package_id, account_id, MAX(experiment_state_id) AS id FROM experiment_states WHERE config_package_id=(SELECT config_package_id FROM config_packages WHERE name='$1') GROUP BY account_id) es JOIN flag_overrides o ON o.config_package_id=es.config_package_id AND o.account_id=es.account_id AND o.name='$2';
INSERT INTO flag_overrides_to_commit (override_id, config_package_id, account_id) SELECT override_id, config_package_id, account_id FROM flag_overrides WHERE name='$2' AND config_package_id=(SELECT config_package_id FROM config_packages WHERE name='$1');
EOS
}
P_APP='com.google.ambient.streaming#com.google.ambient.streaming'
P_MD='com.google.android.gms.multidevice#com.google.android.gms'
{ sql_for "$P_APP" 45815550 1; sql_for "$P_APP" 45832059 0; sql_for "$P_MD" 45742359 1; } > "$B/overrides.sql"
if [ $DRY = 1 ]; then
    cat "$B/overrides.sql"
elif [ "$(S "ls $DB 2>/dev/null | wc -l" | tr -d '\r')" = 0 ]; then
    echo "phenotype.db does not exist yet: sign in to Google in GBOS first, then run this again"
    exit 0
else
    S "$DBX $DB backup /data/local/tmp/ph_userfixes.db" | tail -1
    adb -s "$A" pull /data/local/tmp/ph_userfixes.db "$B/phenotype.db" >/dev/null 2>&1
    adb -s "$A" push "$B/overrides.sql" /data/local/tmp/gb-overrides.sql >/dev/null
    S "$DBX $DB file /data/local/tmp/gb-overrides.sql" | tail -3
    S "$DBX $DB query \"select c.name, o.account_id, o.name, o.value from flag_overrides o join config_packages c using(config_package_id) where o.name in ('45815550','45832059','45742359')\""
    # A snapshot Play services keeps for this app is read BEFORE the app's own one and would hide the override:
    # move it aside (the app falls back to its local snapshot, which now has the override), then refresh that.
    S "for f in \$(ls $GMS/files/phenotype/shared 2>/dev/null | grep -E 'y_xpBOai8TmjEndDHPChTQ|multidevice#com.google.android.gms'); do mv \"$GMS/files/phenotype/shared/\$f\" \"/data/local/tmp/ph_hold_\$f\" && echo moved \$f; done"
    S "am force-stop $APP; am start-foreground-service --user 10 -n $APP/.multidevicesync.devicelink.dtdi.foreground.ForegroundDiscoveryService >/dev/null; sleep 4; am broadcast --user 10 -a com.google.android.gms.phenotype.UPDATE -p $APP --es com.google.android.gms.phenotype.PACKAGE_NAME '$P_APP' | tail -1; sleep 6; am force-stop $APP"
fi

# ---- 4. multidevice settings
say "4. MultideviceSettings.pb (App Cast + Quick Access)"
F=$GMS/files/multidevice/settings/MultideviceSettings.pb
if [ "$(S "ls $F 2>/dev/null | wc -l" | tr -d '\r')" = 0 ]; then
    echo "settings file not created yet (open Settings, Cross-device services once, or connect a phone); skipping"
else
    adb -s "$A" pull "$F" "$B/MultideviceSettings.pb" >/dev/null 2>&1
    python3 - "$B/MultideviceSettings.pb" "$B/MultideviceSettings.patched" <<'EOF'
import sys
d = open(sys.argv[1], 'rb').read(); n = 0
for key in (9, 10):                                   # quick_access_pref, app_cast_pref
    old = bytes([0x1a, 0x04, 0x08, key, 0x10, 0x02]); new = bytes([0x1a, 0x04, 0x08, key, 0x10, 0x01])
    n += d.count(old); d = d.replace(old, new)
open(sys.argv[2], 'wb').write(d); print("entries to change:", n)
EOF
    if [ $DRY = 0 ]; then
        if ! cmp -s "$B/MultideviceSettings.pb" "$B/MultideviceSettings.patched"; then
            adb -s "$A" push "$B/MultideviceSettings.patched" /data/local/tmp/MultideviceSettings.new >/dev/null
            S "cp /data/local/tmp/MultideviceSettings.new $F; chown \$(stat -c %u $GMS):\$(stat -c %g $GMS) $F; chmod 600 $F; am force-stop --user 10 com.google.android.gms; sleep 8; pidof com.google.android.gms"
        else
            echo "already enabled; nothing to change"
        fi
    fi
fi

# ---- 5. login screen (drawn as user 0) looks like user 10
if [ $LOGIN = 1 ]; then
    say "5. user 0 wallpaper/theme to match user 10"
    if [ $DRY = 0 ]; then
        S "CLASSPATH=/data/local/tmp/wallset.apk app_process /system/bin com.gbos.wallset.Main 0 x/y clearlock >/dev/null 2>&1"
        # the DarkLight wallpaper needs its content (the image urls) or it draws black: copy user 10's description to user 0
        S 'X=$(abx2xml /data/system/users/10/wallpaper_info.xml -); L=$(echo "$X" | sed -n "s/.*name=\"light_url\">\([^<]*\)<.*/\1/p"); D=$(echo "$X" | sed -n "s/.*name=\"dark_url\">\([^<]*\)<.*/\1/p"); [ -n "$L" ] && CLASSPATH=/data/local/tmp/wallset.apk app_process /system/bin com.gbos.wallset.Main desc 0 17 "$L" "$D" 3 >/dev/null 2>&1'
        S '[ "$(am get-current-user)" = "10" ] && am force-stop --user 0 com.google.android.desktop.adaptivewallpaper'
        S "settings --user 0 put secure theme_customization_overlay_packages '{\"android.theme.customization.color_both\":\"1\",\"android.theme.customization.color_source\":\"home_wallpaper\"}'; settings --user 0 put secure ui_night_mode 2"
    else
        echo "(dry run) would copy the wallpaper description and theme from user 10 to user 0"
    fi
fi

# ---- 6/7. tuning
if [ $TUNE = 1 ]; then
    say "6. memory-killer tuning"
    if [ $DRY = 0 ]; then
        S "setprop persist.device_config.lmkd_native.pressure_after_kill_min_score 900; setprop persist.device_config.lmkd_native.critical_upgrade false; setprop persist.device_config.lmkd_native.psi_complete_stall_ms 900; setprop persist.device_config.lmkd_native.psi_partial_stall_ms 900; setprop persist.device_config.lmkd_native.kill_timeout_ms 5000; setprop lmkd.reinit 1"
    else
        echo "(dry run) now: min_score=$(S getprop persist.device_config.lmkd_native.pressure_after_kill_min_score | tr -d '\r')   (would set 900)"
    fi
    say "7. disable background packages that only crash-loop or poll here"
    if [ $DRY = 0 ]; then
        S 'for pkg in com.lenovo.vantage com.google.android.aicore com.google.android.rkpdapp com.google.android.desktop.growth com.lenovo.chromebookdiags com.lenovo.lcfcrma com.lenovo.chrome.suw; do
             pm disable-user --user 0 "$pkg" >/dev/null 2>&1 || true
             pm disable-user --user 10 "$pkg" >/dev/null 2>&1 || true
             am force-stop "$pkg" >/dev/null 2>&1 || true
           done'
    else
        echo "(dry run) would disable: com.lenovo.vantage aicore rkpdapp desktop.growth lenovo diagnostics/rma/suw (undo: pm enable <pkg>)"
    fi
fi
echo "done. Backups in $B"
