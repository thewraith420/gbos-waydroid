#!/bin/bash
set -e
cd "$(dirname "$0")"
# R8/D8 8.5.35 reads class files up to Java 21; a newer JDK (25 on current Ubuntu) would otherwise emit a version it rejects.
REL=$(javac -version 2>&1 | awk '{split($2,v,"."); print (v[1]>21 ? 21 : v[1])}')
R8="${R8:-../storprobe/r8-8.5.35.jar}"
rm -rf build && mkdir -p build/classes build/dex
javac --release "$REL" -nowarn -d build/classes $(find src -name "*.java") 2>&1 | grep -v "^Note:" || true
java -cp "$R8" com.android.tools.r8.D8 --min-api 34 --output build/dex $(find build/classes -name "*.class")
(cd build/dex && python3 -c "import zipfile;z=zipfile.ZipFile('../gb-continuity.jar','w',zipfile.ZIP_DEFLATED);z.write('classes.dex');z.close()")
ls -la build/gb-continuity.jar
