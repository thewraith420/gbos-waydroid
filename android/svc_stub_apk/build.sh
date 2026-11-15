#!/bin/bash
# Build + sign the vendor.google.desktop.serviceability stub APK. Output: build/serviceability-stub.apk
set -e
cd "$(dirname "$0")"
# R8/D8 8.5.35 reads class files up to Java 21; a newer JDK (25 on current Ubuntu) would otherwise emit a version it rejects.
REL=$(javac -version 2>&1 | awk '{split($2,v,"."); print (v[1]>21 ? 21 : v[1])}')
T=../svc_analysis/tools
CP=../storprobe/android-all-16-robolectric-13921718.jar
R8=../storprobe/r8-8.5.35.jar
rm -rf build && mkdir -p build/classes build/dex build/res
javac --release $REL -nowarn -cp "$CP" -d build/classes $(find src -name "*.java")
java -cp "$R8" com.android.tools.r8.D8 --min-api 31 --lib "$CP" --output build/dex $(find build/classes -name "*.class")
$T/aapt2 link -o build/unsigned-nodex.apk -I $T/framework-res.apk --manifest AndroidManifest.xml \
    --min-sdk-version 31 --target-sdk-version 35 --version-code 2 --version-name 1.1-waydroid-stub
cp build/unsigned-nodex.apk build/unsigned.apk
python3 - <<PY
import zipfile
z=zipfile.ZipFile("build/unsigned.apk","a",zipfile.ZIP_DEFLATED); z.write("build/dex/classes.dex","classes.dex"); z.close()
PY
[ -f stub.p12 ] || keytool -genkeypair -alias stub -keyalg RSA -keysize 2048 -validity 10000 -storetype PKCS12 \
    -keystore stub.p12 -storepass stubpass -keypass stubpass -dname "CN=Waydroid serviceability stub" >/dev/null 2>&1
mkdir -p build/signer && javac -nowarn -cp $T/apksig-8.13.2.jar -d build/signer signer/Sign.java
java -cp build/signer:$T/apksig-8.13.2.jar Sign stub.p12 build/unsigned.apk build/serviceability-stub.apk
ls -la build/serviceability-stub.apk
