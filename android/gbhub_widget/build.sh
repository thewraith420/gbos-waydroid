#!/bin/bash
set -e
cd "$(dirname "$0")"
# R8/D8 8.5.35 reads class files up to Java 21; a newer JDK (25 on current Ubuntu) would otherwise emit a version it rejects.
REL=$(javac -version 2>&1 | awk '{split($2,v,"."); print (v[1]>21 ? 21 : v[1])}')
T=../svc_analysis/tools
CP=../storprobe/android-all-16-robolectric-13921718.jar
R8=../storprobe/r8-8.5.35.jar

rm -rf build && mkdir -p build/classes build/dex build/signer build/gen build/res

# 1. resources -> compiled zip + generated R.java
"$T/aapt2" compile --dir res -o build/res.zip
"$T/aapt2" link -o build/u0.apk -I "$T/framework-res.apk" \
    --manifest AndroidManifest.xml \
    --java build/gen \
    --min-sdk-version 34 --target-sdk-version 35 \
    --version-code 1 --version-name 1.0 \
    --auto-add-overlay \
    -R build/res.zip

# 2. java -> classes -> dex
javac --release $REL -nowarn -cp "$CP" -d build/classes \
    $(find src build/gen -name "*.java")
java -cp "$R8" com.android.tools.r8.D8 --min-api 34 --lib "$CP" \
    --output build/dex $(find build/classes -name "*.class")

# 3. sign
cp build/u0.apk build/unsigned.apk
python3 -c "import zipfile;z=zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED);z.write('build/dex/classes.dex','classes.dex');z.close()"
S=../svc_stub_apk
javac -nowarn -cp "$T/apksig-8.13.2.jar" -d build/signer "$S/signer/Sign.java"
java -cp "build/signer:$T/apksig-8.13.2.jar" Sign "$S/stub.p12" build/unsigned.apk build/gbhub_widget.apk
ls -la build/gbhub_widget.apk

