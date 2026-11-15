#!/bin/bash
set -e
cd "$(dirname "$0")"
# R8/D8 8.5.35 reads class files up to Java 21; a newer JDK (25 on current Ubuntu) would otherwise emit a version it rejects.
REL=$(javac -version 2>&1 | awk '{split($2,v,"."); print (v[1]>21 ? 21 : v[1])}')
# override with TOOLS_DIR / ANDROID_ALL_JAR / R8_JAR when the sibling directories are not there
T=${TOOLS_DIR:-../svc_analysis/tools}; CP=${ANDROID_ALL_JAR:-../storprobe/android-all-16-robolectric-13921718.jar}; R8=${R8_JAR:-../storprobe/r8-8.5.35.jar}
rm -rf build && mkdir -p build/classes build/dex build/signer
javac --release $REL -nowarn -cp "$CP" -d build/classes $(find src -name "*.java")
java -cp "$R8" com.android.tools.r8.D8 --min-api 31 --lib "$CP" --output build/dex $(find build/classes -name "*.class")
$T/aapt2 link -o build/u0.apk -I $T/framework-res.apk --manifest AndroidManifest.xml --min-sdk-version 31 --target-sdk-version 35 --version-code 1 --version-name 1.0
cp build/u0.apk build/unsigned.apk
python3 -c "import zipfile;z=zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED);z.write('build/dex/classes.dex','classes.dex');z.close()"
S=../svc_stub_apk
javac -nowarn -cp $T/apksig-8.13.2.jar -d build/signer $S/signer/Sign.java
java -cp build/signer:$T/apksig-8.13.2.jar Sign $S/stub.p12 build/unsigned.apk build/fakewifi.apk
ls -la build/fakewifi.apk
