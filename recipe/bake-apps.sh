#!/bin/bash
# Make the working upstream WebView part of the system, REPLACING the broken Google WebView 149, and bake in the
# serviceability stub. Everything goes into the Waydroid overlay (the same place try.sh writes), so it survives a
# /data wipe/reset and is applied on every boot. It does NOT touch /data (account and apps stay).
#
#   1) WebView: the factory APK /product/app/WebViewGoogle/WebViewGoogle.apk (Google WebView 149, whose fetch()/XHR
#      never complete in this container) is SHADOWED by the upstream Chromium 158 build (package
#      com.android.webview). After this the factory 149 no longer exists on the system. The folder keeps its old
#      name only because overlayfs replaces the file at the same path; Android uses the package name, not the path.
#   2) Stub: vendor.google.desktop.serviceability (lets Task Manager / Diagnostics open) -> /system_ext/app.
#   3) Vendor VINTF manifest WITHOUT the OMX codec-store HAL declaration (vintf-manifest.no-omx.xml). That HAL's binary is
#      32-bit-only and cannot run on this 64-bit image, but the manifest still declares it, so system_server's
#      StorageManagerService thread waits forever for it inside MediaCodecList, never finishes unlocking users, and
#      Android then reports ALL storage as "unmounted" to apps -> Play Store error 1047. Without the declaration the
#      lookup fails instantly and boot continues.
#   4) gb-firstboot.sh and unlock-signin (WebView flags without --single-process) are re-installed;
#      gb-firstboot.sh also selects com.android.webview as the provider every boot, as a safety net.
#
# Usage:  1) shut Waydroid down cleanly    2) sudo bash bake-apps.sh    3) start Waydroid
#         sudo bash bake-apps.sh --remove   undoes 1) and 2) (the factory 149 comes back)
set -e
W="$(cd "$(dirname "$0")" && pwd)"
OV=/var/lib/waydroid/overlay
WV_SRC="$W/wv_test/chrome-android-desktop/apks/SystemWebView.apk"
STUB_SRC="$W/svc_stub_apk/build/serviceability-stub.apk"
WV_DIR="$OV/product/app/WebViewGoogle"
STUB_DST="$OV/system_ext/app/ServiceabilityStub"

if [ "$(id -u)" != 0 ]; then echo "run with sudo"; exit 1; fi

if [ "${1:-}" = "--remove" ]; then
    rm -rf "$WV_DIR" "$STUB_DST" "$OV/system_ext/app/GbHubWidget"
    rm -f "$OV/vendor/etc/vintf/manifest.xml" "$OV/vendor/etc/media_codecs_c2.xml"; rmdir "$OV/vendor/etc/vintf" 2>/dev/null || true
    rm -f "$OV/system/lib64/libselinux.so" /var/lib/waydroid/overlay_rw/system/system/lib64/libselinux.so   # host-side rm: no whiteout
    rm -f "$OV/product/overlay/GbLanWifiOverlay.apk"; rmdir "$OV/product/overlay" 2>/dev/null || true
    rm -f "$OV/system/bin/gb-fakeap.sh" "$OV/system/bin/gb-fridaserver.sh" "$OV/system/bin/gb-dhcpd" "$OV/system/bin/gb-portrelay" "$OV/system/bin/gb-portrelay.sh" "$OV/system/bin/gb-wifi.sh" "$OV/system/bin/gb-storagemount.sh" "$OV/system/etc/fakewifi.apk" "$OV/vendor/etc/vintf/manifest/android.hardware.wifi.xml" "$OV/vendor/etc/init/android.hardware.cas@1.2-service.rc" "$OV/vendor/etc/init/android.hardware.media.omx@1.0-service.rc" "$OV/vendor/etc/init/android.hardware.camera.provider@2.7-external-service.rc" "$OV/vendor/etc/init/android.hardware.wifi.supplicant-service.rc" "$OV/system/etc/init/udsattestation.rc"; rmdir "$OV/vendor/etc/vintf/manifest" 2>/dev/null || true
    rm -f "$OV/system/bin/gb-lan.sh" "$OV/system/etc/ethcfg.apk" "$OV/system/bin/gb-bthal.sh" "$OV/system/etc/bthal.apk" "$OV/system/bin/gb-audiofix.sh" "$OV/system/bin/gb-waydroidplatform.sh" "$OV/system/etc/waydroid-platform.jar" "$OV/system/bin/gb-surfaceflinger.sh" "$OV/system/lib64/libgb-sf-waydroid.so" "/var/lib/waydroid/overlay_rw/system/system/lib64/libgb-sf-waydroid.so" "$OV/system/lib64/vendor.waydroid.display@1.0.so" "/var/lib/waydroid/overlay_rw/system/system/lib64/vendor.waydroid.display@1.0.so" "$OV/system/bin/hw/vendor.waydroid.task@1.0-service" "/var/lib/waydroid/overlay_rw/system/system/bin/hw/vendor.waydroid.task@1.0-service" "$OV/system/bin/gb-hwcomposer.sh" "$OV/vendor/lib64/libgb-hwc-waydroid.so" "/var/lib/waydroid/overlay_rw/vendor/vendor/lib64/libgb-hwc-waydroid.so" "$OV/vendor/etc/permissions/android.hardware.bluetooth_le.xml" "$OV/vendor/etc/permissions/android.hardware.usb.host.xml" "$OV/system/etc/audioctl.apk" "$OV/system/lib64/libaudiohal@7.1.so" "/var/lib/waydroid/overlay_rw/system/system/lib64/libaudiohal@7.1.so" "$OV/system/lib64/libinputflinger.so" "/var/lib/waydroid/overlay_rw/system/system/lib64/libinputflinger.so" "$OV/system/bin/input-bridge" "/var/lib/waydroid/overlay_rw/system/system/bin/input-bridge"
    rmdir "$OV/product/app" "$OV/product" 2>/dev/null || true
    echo "removed the baked WebView and stub (factory Google WebView 149 is back after the next start)"
    exit 0
fi

for f in "$WV_SRC" "$STUB_SRC" "$W/gb-firstboot.sh" "$W/unlock-signin" "$W/fix-ui.js" "$W/android.software.virtualization_framework.xml" "$W/android.hardware.usb.host.xml" "$W/android.hardware.bluetooth_le.xml" "$W/vintf-manifest.no-omx.xml"; do
    [ -f "$f" ] || { echo "missing: $f"; exit 1; }
done
# The rootfs is only populated while the container runs, so the factory path can't be checked here; it was verified on
# the device (/product/app/WebViewGoogle/WebViewGoogle.apk). Only complain if the rootfs IS present but lacks it.
if [ -d /var/lib/waydroid/rootfs/product/app ] && [ ! -f /var/lib/waydroid/rootfs/product/app/WebViewGoogle/WebViewGoogle.apk ]; then
    echo "factory WebView path not found in the mounted rootfs; aborting"; exit 1
fi
if pgrep -f "lxc-start.*waydroid" >/dev/null 2>&1; then
    echo "WARNING: Waydroid appears to be running. Changing the overlay under a running container is unreliable;"
    echo "         shut it down first (waydroid session stop; sudo waydroid container stop), then re-run."
    exit 1
fi

install -d -m 755 "$OV/product" "$OV/product/app" "$WV_DIR" "$STUB_DST"
install -m 644 "$WV_SRC"   "$WV_DIR/WebViewGoogle.apk"      # shadows the factory 149 file at the same path
install -m 644 "$STUB_SRC" "$STUB_DST/ServiceabilityStub.apk"
# Phone Hub desktop widget (com.gbos.hub): shows the Cross-Device link state and has a Reconnect button. It talks to the host's
# gb-link-daemon (host/install-host.sh phonehub) on the Waydroid bridge, port 8092. Baked as a system app so every user has it
# and a reset of Android's data keeps it. Built only by `gbos-install.py prepare --phone-hub`.
if [ -f "$W/gbhub_widget/build/gbhub_widget.apk" ]; then
    install -D -m 644 "$W/gbhub_widget/build/gbhub_widget.apk" "$OV/system_ext/app/GbHubWidget/GbHubWidget.apk"
    echo "baked the Phone Hub widget (add it from the desktop's widget picker: 'Phone Hub')"
else
    echo "Phone Hub widget not built (prepare without --phone-hub): skipped"
fi
install -D -m 755 "$W/gb-firstboot.sh" "$OV/system/bin/gb-firstboot.sh"
install -D -m 755 "$W/gb-audiofix.sh" "$OV/system/bin/gb-audiofix.sh"   # master-mute watcher (sound)
install -D -m 755 "$W/gb-bthal.sh" "$OV/system/bin/gb-bthal.sh"   # Bluetooth HAL launcher (real adapter via host helper, else simulated)
install -D -m 644 "$W/bthal_app/build/bthal.apk" "$OV/system/etc/bthal.apk"   # Java Bluetooth HCI HAL
install -D -m 755 "$W/gb-lan.sh" "$OV/system/bin/gb-lan.sh"   # LAN presence, container half (applies the host-chosen static IP)
install -D -m 644 "$W/ethcfg_app/build/ethcfg.apk" "$OV/system/etc/ethcfg.apk"   # Android Ethernet-manager CLI
# Fake Wi-Fi: stub Wi-Fi HAL + stub wificond (one app_process service) and the VINTF declaration of the HAL (section 3.18).
install -D -m 755 "$W/gb-wifi.sh" "$OV/system/bin/gb-wifi.sh"
install -D -m 755 "$W/fakeap/gb-fakeap.sh" "$OV/system/bin/gb-fakeap.sh"   # fake Wi-Fi access point named like the host network (section 3.20)
install -D -m 755 "$W/portrelay/gb-portrelay.sh" "$OV/system/bin/gb-portrelay.sh"   # 127.0.0.1:2222 -> Terminal VM sshd (Antigravity)
install -D -m 755 "$W/portrelay/gb-portrelay" "$OV/system/bin/gb-portrelay"
install -D -m 755 "$W/fakeap/gb-dhcpd" "$OV/system/bin/gb-dhcpd"        # its tiny DHCP server (static binary)
install -D -m 755 "$W/wallpaper-sync-daemon.sh" "$OV/system/bin/wallpaper-sync-daemon.sh"   # user 10 -> user 0 wallpaper/Monet sync
install -D -m 644 "$W/fakewifi_app/build/fakewifi.apk" "$OV/system/etc/fakewifi.apk"
install -D -m 644 "$W/fakewifi_app/vintf/android.hardware.wifi.xml" "$OV/vendor/etc/vintf/manifest/android.hardware.wifi.xml"
# Neutralize crashing 32-bit vendor HAL services and missing hardware daemons
install -D -m 644 "$W/hal-stubs/android.hardware.cas@1.2-service.rc" "$OV/vendor/etc/init/android.hardware.cas@1.2-service.rc"
install -D -m 644 "$W/hal-stubs/android.hardware.media.omx@1.0-service.rc" "$OV/vendor/etc/init/android.hardware.media.omx@1.0-service.rc"
install -D -m 644 "$W/hal-stubs/android.hardware.camera.provider@2.7-external-service.rc" "$OV/vendor/etc/init/android.hardware.camera.provider@2.7-external-service.rc"
install -D -m 644 "$W/hal-stubs/android.hardware.wifi.supplicant-service.rc" "$OV/vendor/etc/init/android.hardware.wifi.supplicant-service.rc"
install -D -m 644 "$W/hal-stubs/udsattestation.rc" "$OV/system/etc/init/udsattestation.rc"
install -D -m 755 "$W/gb-storagemount.sh" "$OV/system/bin/gb-storagemount.sh"
# frida-server supervisor for App Cast: starts /data/local/tmp/frida-server at boot and keeps it alive, so the
# host no longer has to start the instrumentation daemon after every Waydroid restart (see gb-fridaserver.sh).
install -D -m 755 "$W/gb-fridaserver.sh" "$OV/system/bin/gb-fridaserver.sh"
# LAN NIC as Wi-Fi (retry 2026-10-20, now that the fake Wi-Fi in section 3.18 makes Wi-Fi "enabled"): static overlay on the connectivity resources that sets
# config_ethernet_interfaces to "eth1;...;;1" (transport 1 = WIFI), so apps see eth1 (the host-LAN ipvlan link) as a connected Wi-Fi network.
# Phone Hub's setup asks for "both devices on the same Wi-Fi network". Read once at boot; to drop it, delete the file below and bake again.
install -D -m 644 "$W/lan_wifi_overlay/build/lan-wifi-overlay.apk" "$OV/product/overlay/GbLanWifiOverlay.apk"
# unlock-signin rewrites the WebView flags file every second; this build no longer forces --single-process/--no-sandbox
# (with WebView 158 single-process made com.anthropic.claude abort: render_process_host_impl.cc InSameStoragePartition).
install -D -m 755 "$W/unlock-signin" "$OV/system/bin/unlock-signin"
install -D -m 644 "$W/audioctl_app/build/audioctl.apk" "$OV/system/etc/audioctl.apk"   # tiny audio-service CLI used by gb-firstboot.sh (master-mute fix)
if [ -f "$W/gb-waydroidplatform.sh" ] && [ -f "$W/waydroid_platform/build/waydroid-platform.jar" ]; then
    install -D -m 755 "$W/gb-waydroidplatform.sh" "$OV/system/bin/gb-waydroidplatform.sh"
    install -D -m 644 "$W/waydroid_platform/build/waydroid-platform.jar" "$OV/system/etc/waydroid-platform.jar"
fi
if [ -f "$W/gb-surfaceflinger.sh" ]; then
    install -D -m 755 "$W/gb-surfaceflinger.sh" "$OV/system/bin/gb-surfaceflinger.sh"
fi
if [ -f "$W/libgb-sf-waydroid.so" ]; then
    install -D -m 644 "$W/libgb-sf-waydroid.so" "$OV/system/lib64/libgb-sf-waydroid.so"
fi
if [ -f "$W/donor-task/vendor.waydroid.display@1.0.so" ]; then
    install -D -m 644 "$W/donor-task/vendor.waydroid.display@1.0.so" "$OV/system/lib64/vendor.waydroid.display@1.0.so"
fi
if [ -f "$W/donor-task/vendor.waydroid.task@1.0-service" ] && [ -f "$W/patch-waydroid-task.py" ]; then
    python3 "$W/patch-waydroid-task.py" "$W/donor-task/vendor.waydroid.task@1.0-service" "$OV/system/bin/hw/vendor.waydroid.task@1.0-service"
    chmod 755 "$OV/system/bin/hw/vendor.waydroid.task@1.0-service"
fi
if [ -f "$W/gb-hwcomposer.sh" ]; then
    install -D -m 755 "$W/gb-hwcomposer.sh" "$OV/system/bin/gb-hwcomposer.sh"
fi
if [ -f "$W/libgb-hwc-waydroid.so" ]; then
    install -D -m 644 "$W/libgb-hwc-waydroid.so" "$OV/vendor/lib64/libgb-hwc-waydroid.so"
fi
install -D -m 644 "$W/fix-ui.js" "$OV/system/etc/fix-ui.js"   # sign-in page injection (incl. focused-input paint fix); gb-firstboot copies it to /data if absent
# Linux Terminal (AVF): the framework only registers VirtualMachineManager when this feature is declared; the stripped vendor image omits it.
# USB host: without this feature UsbService never creates its UsbHostManager, so Android cannot enumerate or open any USB device
# (no UsbManager device list, no USB passthrough to apps or to the Linux Terminal VM). The container already has /dev/bus/usb
# nodes (660 root:usb, created by ueventd, hotplug included since the container is privileged).
# Bluetooth LE: the stack here brings up LE (BLE_ON) but the system only declares android.hardware.bluetooth, not bluetooth_le.
# Google Nearby / Quick Share, Fast Pair, Companion Device and phone-integration components gate on the LE feature.
install -D -m 644 "$W/android.hardware.bluetooth_le.xml" "$OV/vendor/etc/permissions/android.hardware.bluetooth_le.xml"
install -D -m 644 "$W/android.hardware.usb.host.xml" "$OV/vendor/etc/permissions/android.hardware.usb.host.xml"
install -D -m 644 "$W/android.software.virtualization_framework.xml" "$OV/vendor/etc/permissions/android.software.virtualization_framework.xml"
install -D -m 644 "$W/zzz-googlebook-waydroid.rc" "$OV/system_ext/etc/init/zzz-googlebook-waydroid.rc"
# Linux Terminal (AVF) needs libselinux getprevcon() to work: virtmgr calls it during createVm, and it reads
# /proc/thread-self/attr/prev, which this container's kernel (AppArmor, no SELinux) always answers with EINVAL, so
# every VM creation failed with "getprevcon failed: Invalid argument". Patch ONE instruction in a copy of the stock
# libselinux.so: getprevcon's reference to the string "prev" is redirected to the existing string "current", so it
# returns the process's AppArmor label (non-NULL) instead of failing. SELinux is disabled in this container, so no
# SELinux enforcement is affected. The original is kept as libselinux.orig.so; undo with --remove.
python3 - "$W/libselinux.orig.so" "$OV/system/lib64/libselinux.so" <<'PYEOF'
import hashlib, struct, sys
src, dst = sys.argv[1], sys.argv[2]
b = bytearray(open(src, "rb").read())
assert len(b) == 151920 and hashlib.md5(b).hexdigest() == "2be75f3ef761ab1da05a35f5a00730cb", "unexpected libselinux.orig.so"
INS = 0x17483                                   # lea rdx,[rip+disp32] inside getprevcon (file offset == vaddr here)
assert b[INS:INS+3] == bytes([0x48, 0x8d, 0x15]), "unexpected opcode"
cur = struct.unpack("<i", b[INS+3:INS+7])[0]
assert INS + 7 + cur == 0x3fd4 and b[0x3fd4:0x3fd9] == b"prev\0", "getprevcon does not reference 'prev'"
assert b[0x4968:0x4970] == b"current\0", "'current' string not at expected address"
b[INS+3:INS+7] = struct.pack("<i", 0x4968 - (INS + 7))
orig = open(src, "rb").read()
diff = [i for i, (x, y) in enumerate(zip(orig, b)) if x != y]
assert len(orig) == len(b) and diff and all(INS + 3 <= i < INS + 7 for i in diff), "unexpected bytes changed"
import os
os.makedirs(os.path.dirname(dst), exist_ok=True)
open(dst, "wb").write(b); os.chmod(dst, 0o644)
print("patched libselinux.so written (getprevcon -> attr/current); changed file offsets:", [hex(i) for i in diff])
PYEOF
# Audio (sound): Android 17's libaudiohal@7.1 asks a HAL-7.x stream for its process ID via IBase::getDebugInfo() after
# prepareForWriting() and fails with NAME_NOT_FOUND (-2) when the answer is NO_PID (-1). This image runs the audio HAL
# IN-PROCESS (passthrough; the standalone 32-bit audio service cannot run on this 64-bit-only system), and an in-process
# HIDL object always reports pid -1. The failure is silent, the HAL keeps its half of the stream queue, every later write
# gets "prepareForWriting twice" (-61), and no sound ever leaves audioserver. Patch ONE compare-and-jump in a copy of
# libaudiohal@7.1.so (83 f8 ff 74 07 -> five NOPs) so the -1 is accepted; the PID is only used for an optional
# HAL-thread priority request that is off by default. Original kept as libaudiohal-7.1.orig.so; undo with --remove.
python3 - "$W/libaudiohal-7.1.orig.so" "$OV/system/lib64/libaudiohal@7.1.so" <<'PYEOF'
import hashlib, os, sys
src, dst = sys.argv[1], sys.argv[2]
b = bytearray(open(src, "rb").read())
assert len(b) == 2337472 and hashlib.md5(b).hexdigest() == "6bce5ac1da7483299bcb9b50cbfd32c3", "unexpected libaudiohal-7.1.orig.so"
OFF = 0xb6b8d                                   # file offset == vaddr in this R+E segment
assert bytes(b[OFF-6:OFF]) == bytes.fromhex("8b8530ffffff"), "unexpected code before patch site"
assert bytes(b[OFF:OFF+5]) == bytes.fromhex("83f8ff7407"), "unexpected patch-site bytes"
assert bytes(b[OFF+5:OFF+7]) == bytes.fromhex("8903"), "unexpected code after patch site"
orig = bytes(b)
b[OFF:OFF+5] = b"\x90" * 5
diff = [i for i, (x, y) in enumerate(zip(orig, b)) if x != y]
assert len(orig) == len(b) and diff and all(OFF <= i < OFF + 5 for i in diff), "unexpected bytes changed"
os.makedirs(os.path.dirname(dst), exist_ok=True)
open(dst, "wb").write(b); os.chmod(dst, 0o644)
print("patched libaudiohal@7.1.so written; changed file offsets:", [hex(i) for i in diff])
PYEOF

# Fullscreen / display-resize crash: Waydroid's input-bridge presents the host pointer as an absolute PEN that is held "in
# range" forever (BTN_TOOL_PEN is set once and never released), so Android keeps a permanent stylus hover. When the display
# size changes (e.g. hiding the host's top panel so the window goes fullscreen) the touch mapper is reconfigured and
# forgets that hover WITHOUT sending HOVER_EXIT; the next event is HOVER_ENTER while InputDispatcher still holds the old
# hover, which trips LOG(FATAL) "Expected ACTION_HOVER_MOVE instead of ..." (InputDispatcher.cpp:741) and aborts
# system_server (= whole Android session dies). The dispatcher already copes with stale hovering windows right after that
# check (it synthesizes the HOVER_EXITs), so only the abort is wrong here. Patch the severity argument of that one
# LogMessage constructor in a copy of libinputflinger.so: `mov ecx,r12d` (44 89 e1) -> `push 4; pop rcx` (6a 04 59), i.e.
# the message is logged as ERROR instead of FATAL. Original kept as libinputflinger.orig.so; undo with --remove.
python3 - "$W/libinputflinger.orig.so" "$OV/system/lib64/libinputflinger.so" <<'PYEOF'
import hashlib, os, sys
src, dst = sys.argv[1], sys.argv[2]
b = bytearray(open(src, "rb").read())
assert len(b) == 4494656 and hashlib.md5(b).hexdigest() == "b38ab38637db63d22853f952b55645a0", "unexpected libinputflinger.orig.so"
OFF = 0x3235d0                                  # file offset == vaddr in this R+E segment
assert bytes(b[OFF-5:OFF]) == bytes.fromhex("bae5020000"), "line number 741 (0x2e5) not loaded just before the patch site"
assert bytes(b[OFF:OFF+3]) == bytes.fromhex("4489e1"), "unexpected patch-site bytes"
assert bytes(b[OFF+3:OFF+9]) == bytes.fromhex("41b9ffffffff"), "unexpected code after patch site"
assert bytes(b[OFF+9:OFF+10]) == b"\xe8", "LogMessage constructor call not where expected"
orig = bytes(b)
b[OFF:OFF+3] = bytes.fromhex("6a0459")
diff = [i for i, (x, y) in enumerate(zip(orig, b)) if x != y]
assert len(orig) == len(b) and diff and all(OFF <= i < OFF + 3 for i in diff), "unexpected bytes changed"
os.makedirs(os.path.dirname(dst), exist_ok=True)
open(dst, "wb").write(b); os.chmod(dst, 0o644)
print("patched libinputflinger.so written; changed file offsets:", [hex(i) for i in diff])
PYEOF
# Pointer range vs fullscreen: the stock-layout input-bridge declared a 1920x1045 window (Y range 0..1044), which Android scales
# onto the display, so with the host panel hidden (display 1920x1080) the pointer vanished in the bottom strip. native/input-bridge.c
# is built with PTR_H=1080 (Y range 0..1079) instead, so no byte-patch is needed here: install the rebuilt binary.
# Trade-off: with the panel visible (1045-high window) the mapping is ~3% stretched the other way.
install -D -m 755 "$W/input-bridge" "$OV/system/bin/input-bridge"
# A copy of these in the writable upper layer (overlay_rw) SHADOWS the lower-layer copy installed above, so the container
# would keep running the old file. Drop stale upper copies so the lower (baked) ones take effect.
for f in system/system/bin/unlock-signin system/system/bin/gb-firstboot.sh system/system/bin/gb-audiofix.sh system/system/bin/gb-waydroidplatform.sh system/system/etc/waydroid-platform.jar system/system/bin/gb-surfaceflinger.sh system/system/lib64/libgb-sf-waydroid.so system/system/lib64/vendor.waydroid.display@1.0.so system/system/bin/hw/vendor.waydroid.task@1.0-service system/system/bin/gb-hwcomposer.sh vendor/vendor/lib64/libgb-hwc-waydroid.so system/system/bin/gb-bthal.sh system/system/bin/gb-lan.sh system/system/bin/gb-wifi.sh system/system/bin/gb-fakeap.sh system/system/bin/gb-dhcpd system/system/bin/gb-storagemount.sh system/system/bin/gb-portrelay system/system/bin/gb-portrelay.sh system/system/bin/gb-fridaserver.sh system/system/etc/fakewifi.apk system/system/etc/ethcfg.apk system/system/etc/bthal.apk system/system/etc/fix-ui.js system/system/etc/audioctl.apk system/system/lib64/libselinux.so system/system/lib64/libaudiohal@7.1.so system/system/lib64/libinputflinger.so system/system/bin/input-bridge system/system_ext/etc/init/zzz-googlebook-waydroid.rc system/system/etc/init/udsattestation.rc system/system/etc/init/magisk.rc vendor/vendor/etc/init/android.hardware.cas@1.2-service.rc vendor/vendor/etc/init/android.hardware.media.omx@1.0-service.rc vendor/vendor/etc/init/android.hardware.camera.provider@2.7-external-service.rc vendor/vendor/etc/init/android.hardware.wifi.supplicant-service.rc product/product/etc/permissions/android.hardware.biometrics.face.xml; do
    rm -f "/var/lib/waydroid/overlay_rw/$f"
done
rm -rf "$OV/system/etc/init/magisk" "/var/lib/waydroid/overlay_rw/system/system/etc/init/magisk" "$OV/product/etc/permissions/android.hardware.biometrics.face.xml"
# Shadow the vendor VINTF manifest (overlay lower layer survives try.sh/reset). Must stay well-formed XML.
python3 -c "import xml.dom.minidom,sys; xml.dom.minidom.parse(sys.argv[1])" "$W/vintf-manifest.no-omx.xml"
install -D -m 644 "$W/vintf-manifest.no-omx.xml" "$OV/vendor/etc/vintf/manifest.xml"
# Codec list. Android builds the list of codecs apps can use (MediaCodecList) from /vendor/etc/media_codecs_c2.xml. This vendor
# image only ships the software Codec2 list under another name (media_codecs_google_c2.xml, which includes the audio and video
# lists), and its media_codecs.xml is an empty template; the OMX codecs are gone (above). Without this link the list is EMPTY
# ("MediaCodecList generated and serialized (56 bytes)"): no app can find a decoder or encoder, so video and audio playback,
# recording and calls fail inside apps.
ln -sfn media_codecs_google_c2.xml "$OV/vendor/etc/media_codecs_c2.xml"

# Storage fix (fstab + loop device + fstab-suffix rc): also written into the persistent overlay layer, because
# `waydroid init -f` (run by try.sh) deletes overlay_rw, where it used to live.
bash "$W/fix-storage-persist.sh" >/dev/null && echo "storage fix written to the persistent overlay layer"

echo "Done. In the overlay:"
ls -la "$WV_DIR" "$STUB_DST" | sed 's/^/  /'
cat <<'EOT'

Start Waydroid now. The first boot scans a 417 MB WebView and may take a minute or two longer than usual.
Then check (and finish the cleanup if needed):
  adb shell dumpsys webviewupdate | head -12        # provider should be com.android.webview 158
  adb shell pm path com.google.android.webview     # should print nothing (149 is gone)
If a leftover data copy of Google 149 is still listed (an old "update" from /data), remove it:
  adb shell pm uninstall com.google.android.webview
EOT
