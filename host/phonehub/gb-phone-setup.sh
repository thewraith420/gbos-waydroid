#!/bin/bash
# gb-phone-setup: set up the phone side of Phone Hub / App Cast over wireless debugging (or USB), in one go.
#
#   gb-phone-setup.sh --connect IP:PORT --work W     install W/gbcast_app/build/gbcast.apk and set everything up
#   gb-phone-setup.sh --connect IP:PORT --check      only report what is set up, change nothing
#   first time on this PC: add  --pair IP:PORT --code 123456  (see below)
#   other options: --apk FILE (instead of --work)   --serial S (an adb serial)   --no-accessibility (see 5. below)
#
# Before you run it, on the phone: Settings > About phone > tap Build number 7 times (Developer options), then
# Developer options > Wireless debugging ON (phone and PC on the same Wi-Fi). The Wireless debugging screen shows the
# phone's "IP address & Port": that is --connect. The first time this PC talks to the phone, tap "Pair device with
# pairing code" there: it shows a 6-digit code and a SECOND IP:port, which is --pair (--code is the 6 digits).
# A USB cable with "USB debugging" works too: plug in, accept "Allow USB debugging?", and leave out --connect.
# The pairing is also what lets the PC reach the phone later for the touch agent (step 7).
#
# What it does on the phone, in order (each step is read back afterwards; nothing is claimed that did not take):
#   1. installs GBOS Cast (com.gbos.cast). An existing copy signed with a different key is KEPT, not removed: Android
#      refuses the update, and the remaining steps then apply to the copy you already have.
#   2. grants WRITE_SECURE_SETTINGS (the app's Re-arm button switches wireless debugging on for a few seconds after a
#      phone reboot, so the PC can restart the touch agent, then switches it off again).
#   3. grants RECORD_AUDIO (the phone's sound is sent along with the picture) and POST_NOTIFICATIONS.
#   4. allows its notification listener (a notification tapped on GBOS opens on the phone).
#   5. enables its accessibility service "GBOS Cast input" (touch and typing from GBOS). Optional: the touch agent of
#      step 7 does the same job, and Android's Advanced Protection blocks accessibility services from outside the Play
#      Store anyway. --no-accessibility skips it.
#   6. opens the app.
# Wireless debugging can be switched off again afterwards; Re-arm turns it on by itself for a few seconds when needed.
# It does not touch anything else on the phone. Undo: uninstall GBOS Cast (that drops every grant with it).
#
# Still done by hand afterwards (they need you at the phone): 7. in GBOS Cast press "Re-arm touch forwarding" once
# while App Cast runs on the PC; 8. turn on Cross-device services and scan the QR code GBOS shows (docs/INSTALL.md).
set -u
PKG=com.gbos.cast
LISTENER=$PKG/$PKG.NotifListener
ACC=$PKG/$PKG.CastAccessibilityService
MIN_SDK=34

WORK=""; APK=""; SERIAL=""; CHECK=0; ACCESSIBILITY=1; CONNECT=""; PAIR=""; CODE=""
while [ $# -gt 0 ]; do
    case "$1" in
        --work) WORK="$2"; shift ;;
        --apk) APK="$2"; shift ;;
        --serial) SERIAL="$2"; shift ;;
        --connect) CONNECT="$2"; shift ;;
        --pair) PAIR="$2"; shift ;;
        --code) CODE="$2"; shift ;;
        --check) CHECK=1 ;;
        --no-accessibility) ACCESSIBILITY=0 ;;
        -h|--help) sed -n '2,33p' "$0"; exit 0 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done

say()  { printf '%s\n' "$*"; }
ok()   { printf '  ok    %s\n' "$*"; }
warn() { printf '  WARN  %s\n' "$*"; WARNED=1; }
bad()  { printf '  FAIL  %s\n' "$*"; FAILED=1; }
WARNED=0; FAILED=0

command -v adb >/dev/null || { say "adb is not installed (Debian/Ubuntu: sudo apt install adb)"; exit 1; }

# ---- the phone -------------------------------------------------------------------------------------------------
# The GBOS container is an adb device too (a network address on Waydroid's bridge, model "Googlebook"): it must never
# be mistaken for the phone, so it is excluded by address and by model.
CONTAINER_NET=192.168.240.
is_container() {   # $1 = serial
    case "$1" in "$CONTAINER_NET"*) return 0 ;; esac
    adb -s "$1" shell getprop ro.product.model 2>/dev/null | grep -qi googlebook
}
if [ -n "$PAIR" ]; then
    [ -n "$CODE" ] || { say "--pair needs --code (the 6 digits shown under \"Pair device with pairing code\")"; exit 2; }
    say "== pairing with $PAIR"
    out=$(adb pair "$PAIR" "$CODE" 2>&1 | tr -d '\r')
    case "$out" in
        *uccessfully*) ok "paired" ;;
        *) say "pairing failed: $out"
           say "The pairing code and its port change every time the pairing dialog opens; use the ones shown now."; exit 1 ;;
    esac
fi
if [ -n "$CONNECT" ]; then
    say "== connecting to $CONNECT"
    out=$(adb connect "$CONNECT" 2>&1 | tr -d '\r')
    case "$out" in
        *"connected to"*) ok "connected" ;;
        *) say "could not connect: $out"
           say "Check the IP address & Port on the phone's Wireless debugging screen (the port changes each time it is"
           say "switched on). If this PC never paired with the phone, add --pair IP:PORT --code CODE (see --help)."
           exit 1 ;;
    esac
    [ -n "$SERIAL" ] || SERIAL="$CONNECT"
fi
say "== looking for the phone"
lines=$(adb devices -l 2>/dev/null | sed 1d | sed '/^\s*$/d')
cand=""
while read -r ser rest; do
    [ -n "$ser" ] || continue
    if [ -n "$SERIAL" ]; then [ "$ser" = "$SERIAL" ] || continue
    else is_container "$ser" && continue; fi
    cand="$cand$ser $rest"$'\n'
done <<< "$lines"
cand=$(printf '%s' "$cand" | sed '/^$/d')
n=$(printf '%s' "$cand" | grep -c . || true)
if [ "$n" = 0 ]; then
    say "No phone found. On the phone turn on Developer options > Wireless debugging, then run this again with"
    say "--connect IP:PORT from that screen (first time on this PC also --pair IP:PORT --code CODE; see --help)."
    [ -z "$SERIAL" ] || say "(nothing matches $SERIAL; 'adb devices -l' lists what is connected)"
    exit 1
fi
if [ "$n" -gt 1 ]; then
    say "More than one phone is connected; pick one with --serial:"
    printf '%s\n' "$cand" | cut -c1-120 | sed 's/^/  /'
    exit 1
fi
SERIAL=$(printf '%s' "$cand" | awk '{print $1}')
STATE=$(printf '%s' "$cand" | awk '{print $2}')
if is_container "$SERIAL"; then say "$SERIAL is the GBOS container, not a phone"; exit 1; fi
case "$STATE" in
    device) ;;
    unauthorized)
        say "The phone has not trusted this computer yet. Over USB: accept \"Allow USB debugging?\" with \"Always allow\""
        say "ticked. Over Wi-Fi: pair first (--pair IP:PORT --code CODE). Then run this again."
        exit 1 ;;
    *) say "The phone is '$STATE'. Reconnect it, then run this again."; exit 1 ;;
esac
A() { adb -s "$SERIAL" "$@"; }
SH() { adb -s "$SERIAL" shell "$@" 2>/dev/null | tr -d '\r'; }
model=$(SH getprop ro.product.model); sdk=$(SH getprop ro.build.version.sdk); rel=$(SH getprop ro.build.version.release)
ok "phone: $model, Android $rel (SDK $sdk), serial $SERIAL"
if [ -n "$sdk" ] && [ "$sdk" -lt $MIN_SDK ]; then
    bad "GBOS Cast needs Android 14 (SDK $MIN_SDK) or newer"; exit 1
fi

installed() { SH pm path $PKG | grep -q '^package:'; }
granted()   { SH dumpsys package $PKG | grep -E "^\s+$1: granted=true" -q; }

# ---- 1. install ------------------------------------------------------------------------------------------------
if [ "$CHECK" = 0 ]; then
    say "== 1. install GBOS Cast"
    if [ -z "$APK" ] && [ -n "$WORK" ]; then APK="$WORK/gbcast_app/build/gbcast.apk"; fi
    if [ -z "$APK" ]; then
        installed && ok "already installed (no --work/--apk given, so not reinstalled)" \
                  || { bad "not installed, and no APK given: pass --work W (the prepared work folder) or --apk FILE"; exit 1; }
    elif [ ! -f "$APK" ]; then
        bad "no APK at $APK: run the installer's Prepare step with Phone Hub on (it builds gbcast_app)"; exit 1
    else
        out=$(A install -r "$APK" 2>&1 | tr -d '\r')
        case "$out" in
            *Success*) ok "installed $(basename "$APK")" ;;
            *INSTALL_FAILED_UPDATE_INCOMPATIBLE*|*"signatures do not match"*)
                warn "a GBOS Cast signed with a different key is already installed; it was KEPT (this build was not"
                say  "        installed). The steps below apply to the installed copy. To use this build instead, uninstall"
                say  "        GBOS Cast on the phone first and run this again." ;;
            *) bad "install failed: $(printf '%s' "$out" | tail -1)"; exit 1 ;;
        esac
    fi
fi
installed || { bad "GBOS Cast is not installed on the phone"; exit 1; }
ver=$(SH dumpsys package $PKG | sed -n 's/^\s*versionName=//p' | head -1)
ok "GBOS Cast ${ver:+$ver }is installed"

# ---- 2-3. permissions ------------------------------------------------------------------------------------------
say "== 2-3. permissions"
for perm in WRITE_SECURE_SETTINGS RECORD_AUDIO POST_NOTIFICATIONS; do
    full=android.permission.$perm
    if ! granted $full && [ "$CHECK" = 0 ]; then
        msg=$(SH pm grant $PKG $full 2>&1)
        [ -n "$msg" ] && say "        pm grant: $msg"
    fi
    if granted $full; then ok "$perm granted"
    elif [ "$CHECK" = 1 ]; then warn "$perm not granted"
    else bad "$perm could not be granted"; fi
done

# ---- 4. notification listener ----------------------------------------------------------------------------------
say "== 4. notification access"
listener_on() { SH settings get secure enabled_notification_listeners | tr ':' '\n' | grep -qx "$LISTENER"; }
if ! listener_on && [ "$CHECK" = 0 ]; then
    SH cmd notification allow_listener "$LISTENER" >/dev/null
fi
if listener_on; then ok "notification listener allowed"
elif [ "$CHECK" = 1 ]; then warn "notification listener not allowed (tapping a phone notification on GBOS will not open it)"
else bad "notification listener could not be allowed: Settings > Notifications > Device & app notifications > GBOS Cast"; fi

# ---- 5. accessibility ------------------------------------------------------------------------------------------
say "== 5. touch control through accessibility (optional)"
acc_on() { SH settings get secure enabled_accessibility_services | tr ':' '\n' | grep -qix "$ACC"; }
if acc_on; then ok "accessibility service \"GBOS Cast input\" is on"
elif [ "$CHECK" = 1 ] || [ "$ACCESSIBILITY" = 0 ]; then
    say "  info  accessibility service off: touch control comes from the touch agent instead (step 7)"
else
    cur=$(SH settings get secure enabled_accessibility_services)
    case "$cur" in null|"") new="$ACC" ;; *) new="$cur:$ACC" ;; esac   # append; never drop the user's own services
    SH settings put secure enabled_accessibility_services "$new" >/dev/null
    SH settings put secure accessibility_enabled 1 >/dev/null
    sleep 1
    if acc_on; then ok "accessibility service \"GBOS Cast input\" turned on"
    else warn "Android did not keep the accessibility service on (Advanced Protection or restricted settings block it)."
         say  "        That is fine: the touch agent (step 7) does the same job."
    fi
fi

# ---- 6. open the app --------------------------------------------------------------------------------------------
if [ "$CHECK" = 0 ]; then
    say "== 6. opening GBOS Cast on the phone"
    SH am start -n $PKG/.MainActivity >/dev/null && ok "opened"
fi

say ""
if [ "$FAILED" = 1 ]; then say "RESULT: something did not take; see FAIL above."; exit 2; fi
if [ "$WARNED" = 1 ]; then say "RESULT: phone ready, with warnings above."; else say "RESULT: phone ready."; fi
say "Left to do at the phone:"
say "  7. in GBOS Cast press \"Re-arm touch forwarding\" once, while App Cast runs on the PC (needed again only after"
say "     the phone reboots)."
say "  8. turn on Cross-device services for the account GBOS is signed in with, and scan the QR code GBOS shows."
exit 0
