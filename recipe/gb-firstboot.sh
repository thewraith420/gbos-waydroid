#!/system/bin/sh
# OS bringup: mark the device/users provisioned and disable the Googlebook desktop setup wizard so a
# fresh boot drops to the launcher instead of the first-run wizard. We MUST bypass that wizard because it
# blocks forever waiting for the GSC (Google Security Chip) HAL
# android.system.desktop.security.gsc.IGsc/default, which has no hardware in the container -> ANR
# "Googlebook setup isn't responding". (Adding a Google account via Settings is a separate flow that does
# not need the wizard.) These settings live in /data, so they are re-applied each first boot.
# On a fresh /data user 10 (the first user, created by the system at first boot) may not exist yet when boot_completed
# fires, and every per-user command below (settings/pm --user 10) silently fails if it doesn't. Wait for it
# (up to ~2 minutes) so a wiped /data still gets the setup-wizard / repairmode fixes.
for try in $(seq 1 60); do
    pm list users 2>/dev/null | grep -q 'UserInfo{10:' && break
    sleep 2
done
settings put --user 0  secure user_setup_complete 1
settings put --user 10 secure user_setup_complete 1
settings put global setup_wizard_has_run 1
settings put global device_provisioned 1
pm disable --user 0  com.google.android.desktop.setupwizard
pm disable --user 10 com.google.android.desktop.setupwizard

# repairmode's BootCompletedReceiver FATALs on every boot trying to bind the missing vendor service
# vendor.google.desktop.serviceability.VendorServiceabilityService (no such HAL in the container) ->
# "Android System has stopped" popup. It is a diagnostics component; disable it. State lives in /data.
pm disable-user --user 0  com.google.android.desktop.repairmode
pm disable-user --user 10 com.google.android.desktop.repairmode

# desktop-sar-service tries to bind missing Wi-Fi SAR HAL (IDesktopWifiExt) every 5s; disable it.
pm disable com.android.desktop.sar 2>/dev/null || true
pm disable-user --user 0  com.android.desktop.sar 2>/dev/null || true
pm disable-user --user 10 com.android.desktop.sar 2>/dev/null || true

# Google's flag sync (GMS) flipped DeviceConfig profcollect_native_boot/enabled to true. With it on, system_server starts
# ProfcollectForwardingService, whose onBootPhase(500) calls UsbManager.getCurrentFunctions(); UsbService only has a
# device manager if /sys/class/android_usb exists, which it doesn't in the container -> IllegalStateException ->
# system_server crash loop on the NEXT boot (black screen after the boot animation). Freeze server-side flag syncing
# (shell `device_config put` still works) and force the flag off. Undo: device_config set_sync_disabled_for_tests none
device_config set_sync_disabled_for_tests persistent
device_config put profcollect_native_boot enabled false

# Sound: the AudioFlinger master-mute fix is done by the gb-audiofix service (gb-audiofix.sh, started from the rc at
# boot_completed): a single cycle here ran too early and did not stick.

# Keep the display awake. There is NO physical power button wired into the container, so once Android's
# screen-off timer or doze fires the display goes to state OFF and nothing can wake it: SurfaceFlinger
# stops compositing, InputDispatcher loses its focused window (FocusRequest -> NO_WINDOW) and drops every
# tap, and Waydroid keeps painting the last frame on the host window -> looks like a live-but-frozen
# desktop (this was the recurring "frozen login screen / frozen desktop"). Pin it on. screen_off_timeout
# and stay_on_while_plugged_in persist in /data; deviceidle disable does NOT, so re-apply every boot.
settings put system screen_off_timeout 2147483647
settings put secure  sleep_timeout -1
settings put secure  attentive_timeout -1
settings put global  stay_on_while_plugged_in 7
dumpsys deviceidle disable
input keyevent 224   # KEYCODE_WAKEUP in case it is already asleep at boot_completed

# lmkd was panicking under memory pressure: swap_free_low_percentage defaults to 10%, and with a ~47GB
# swap (zram+disk+build) that demanded ~4.7GB FREE swap at all times. The moment the heavy Google sign-in
# WebView (embedded Chrome renderer) loaded, free swap dipped under that and lmkd went into a kill spiral
# (launcher/gms/webview-renderer, dozens per second) -> MinuteMaid "Render process gone" -> add-account
# failed. Lower the swap panic threshold + raise thrashing tolerance so lmkd SWAPS instead of killing.
# device_config values persist in /data but re-apply + lmkd.reinit each boot to be certain they're live.
device_config put lmkd_native swap_free_low_percentage 2
device_config put lmkd_native thrashing_limit 100
device_config put lmkd_native thrashing_limit_critical 100
device_config put lmkd_native swap_util_max 100
setprop lmkd.reinit 1

# WebView flags (read from this file because build.prop sets ro.debuggable=1). Multi-process WebView 158 with
# --no-sandbox + NetworkServiceInProcess: Google sign-in reaches the password page AND the Claude Android app runs.
# DO NOT use --single-process: it aborts the Claude app (render_process_host_impl.cc InSameStoragePartition).
# Without --no-sandbox / NetworkServiceInProcess the sign-in page's JS throws a TypeError in its Next handler.
# Do NOT pass --disable-gpu (WebView CHECKs against it). /system/bin/unlock-signin used to enforce this file every
# second; it now manages a decoy path (…/webview-command-lin0), so this file can be edited freely for experiments.
mkdir -p /data/local/tmp
printf 'webview --no-sandbox --enable-features=NetworkServiceInProcess --disable-features=WebViewSandboxedRenderer,BackgroundResourceFetch,ScriptStreaming,ConsumeCompileHints --blink-settings=scriptStreamingEnabled=false\n' > /data/local/tmp/webview-command-line
chmod 0666 /data/local/tmp/webview-command-line
# refresh the /data copy whenever the image's version differs (an updated image must not keep running a stale script);
# the daemon reads /data/local/tmp/fix-ui.js on every cycle, so it can still be edited there for debugging until the next boot
if [ -f /system/etc/fix-ui.js ] && ! cmp -s /system/etc/fix-ui.js /data/local/tmp/fix-ui.js 2>/dev/null; then
    cp /system/etc/fix-ui.js /data/local/tmp/fix-ui.js
    chmod 0666 /data/local/tmp/fix-ui.js
fi
touch /data/local/tmp/unlock.log /data/local/tmp/fix-ui.out /data/local/tmp/cmd.sh /data/local/tmp/cmd.out
chmod 0666 /data/local/tmp/unlock.log /data/local/tmp/fix-ui.out /data/local/tmp/cmd.sh /data/local/tmp/cmd.out 2>/dev/null || true
settings put global webview_multiprocess 0   # also ask WebView not to use a separate render process

# Select the upstream Chromium WebView (com.android.webview, baked into /system_ext/app by bake-apps.sh).
# Google-signed WebView builds (149/155/156) never deliver fetch()/XHR responses in this container, which
# breaks Google sign-in (the MinuteMaid page's Next button). The framework's provider order lists Google's
# WebView first, so a fresh /data would pick the broken one: select ours explicitly every boot. Retry because
# the WebView update service can lag boot_completed. No-op if the package is not installed.
for try in 1 2 3 4 5 6 7 8; do
    out=$(cmd webviewupdate set-webview-implementation com.android.webview 2>&1)
    case "$out" in *Success*) break;; esac
    sleep 3
done
if [ -x /system/bin/unlock-signin ] && ! pidof unlock-signin >/dev/null 2>&1; then
    /system/bin/unlock-signin
fi

# Per-user emulated ("sdcard") storage: mount any unmounted emulated volume (belt alongside the
# gb-storagemount daemon). Retry since a user's storage may not be ready the instant boot_completed fires.
for try in 1 2 3 4 5 6; do
    pending=$(sm list-volumes 2>/dev/null | awk '/^emulated;/ && /unmounted/{print $1}')
    [ -z "$pending" ] && break
    for v in $pending; do sm mount "$v"; done
    sleep 3
done

# Fake lid sensor state if base state is not already OPEN (2) or TABLET (3) (e.g. without waydroid_lid):
# avoids triggering unnecessary device_state overrides when the virtual SW_LID=0 switch already provides base OPEN.
if ! dumpsys device_state 2>/dev/null | grep -qE 'mBaseState=Optional\[DeviceState\{identifier=(2|3),'; then
    cmd device_state state 2 2>/dev/null || true
fi

# Start wallpaper & theme sync daemon so User 0 (Keyguard / Lockscreen) mirrors User 10's wallpaper & Monet style.
DAEMON=""
[ -f /system/bin/wallpaper-sync-daemon.sh ] && DAEMON="/system/bin/wallpaper-sync-daemon.sh"
[ -f /data/local/tmp/wallpaper-sync-daemon.sh ] && DAEMON="/data/local/tmp/wallpaper-sync-daemon.sh"
if [ -n "$DAEMON" ] && ! pgrep -f wallpaper-sync-daemon.sh >/dev/null 2>&1; then
    nohup /system/bin/sh "$DAEMON" >/dev/null 2>&1 &
fi

