#!/bin/bash
# Build + sign the Wi-Fi resource overlay. Output: build/gbos-wifi-overlay.apk. Needs aapt2 and the framework-res.apk of the
# image (or any recent one), and the signer of android/svc_stub_apk; override the three paths with AAPT2 / FRAMEWORK_RES / APKSIG.
set -e
cd "$(dirname "$0")"
T=../svc_analysis/tools
AAPT2=${AAPT2:-$T/aapt2}; FRAMEWORK_RES=${FRAMEWORK_RES:-$T/framework-res.apk}; APKSIG=${APKSIG:-$T/apksig-8.13.2.jar}
rm -rf build && mkdir -p build/signer
"$AAPT2" compile --dir res -o build/res.zip
"$AAPT2" link -o build/unsigned.apk -I "$FRAMEWORK_RES" --manifest AndroidManifest.xml --min-sdk-version 31 --target-sdk-version 35 build/res.zip
S=../svc_stub_apk
[ -f "$S/stub.p12" ] || (cd "$S" && keytool -genkeypair -alias stub -keyalg RSA -keysize 2048 -validity 10000 -storetype PKCS12 -keystore stub.p12 -storepass stubpass -keypass stubpass -dname "CN=GBOS stub" >/dev/null 2>&1)
javac -nowarn -cp "$APKSIG" -d build/signer $S/signer/Sign.java
java -cp build/signer:"$APKSIG" Sign $S/stub.p12 build/unsigned.apk build/gbos-wifi-overlay.apk
ls -la build/gbos-wifi-overlay.apk
