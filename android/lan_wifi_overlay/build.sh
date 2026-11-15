#!/bin/bash
# Static overlay that makes the LAN NIC (eth1) look like a Wi-Fi network to apps (GBOS_WAYDROID_FIXES.md, section 3.17).
set -e
cd "$(dirname "$0")"
T=../svc_analysis/tools
rm -rf build && mkdir -p build/signer
$T/aapt2 compile --dir res -o build/res.zip
$T/aapt2 link -o build/unsigned.apk -I $T/framework-res.apk --manifest AndroidManifest.xml build/res.zip --min-sdk-version 31 --target-sdk-version 37 --version-code 1 --version-name 1.0 --no-resource-removal
S=../svc_stub_apk
javac -nowarn -cp $T/apksig-8.13.2.jar -d build/signer $S/signer/Sign.java
java -cp build/signer:$T/apksig-8.13.2.jar Sign $S/stub.p12 build/unsigned.apk build/lan-wifi-overlay.apk
ls -la build/lan-wifi-overlay.apk
