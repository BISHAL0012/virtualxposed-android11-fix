#!/usr/bin/env bash
# Builds the bundled Gallery app (io.va.gallery) into gallery.apk without Gradle.
# Requires: Android SDK build-tools 28.0.3 + platforms/android-28, a JDK (javac/keytool), zip.
set -euo pipefail

GALLERY_DIR="$(cd "$(dirname "$0")" && pwd)"
BT="${ANDROID_HOME}/build-tools/28.0.3"
AJAR="${ANDROID_HOME}/platforms/android-28/android.jar"
OUT="${GALLERY_DIR}/out"

rm -rf "${OUT}"
mkdir -p "${OUT}/classes" "${OUT}/dex"

# 1) Link the manifest (framework-only resources) into a stub APK.
"${BT}/aapt2" link \
  -o "${OUT}/gallery-unsigned.apk" \
  -I "${AJAR}" \
  --manifest "${GALLERY_DIR}/AndroidManifest.xml" \
  --min-sdk-version 21 --target-sdk-version 28 \
  --version-code 1 --version-name "1.0"

# 2) Compile the Java sources against android.jar.
find "${GALLERY_DIR}/src" -name "*.java" > "${OUT}/sources.txt"
javac -source 8 -target 8 -bootclasspath "${AJAR}" -cp "${AJAR}" \
  -d "${OUT}/classes" @"${OUT}/sources.txt"

# 3) Dex the classes.
"${BT}/d8" --output "${OUT}/dex" $(find "${OUT}/classes" -name "*.class")

# 4) Add classes.dex to the APK root.
cd "${OUT}"
zip -q -j gallery-unsigned.apk dex/classes.dex

# 5) Align and sign with a throwaway debug key.
"${BT}/zipalign" -f 4 gallery-unsigned.apk gallery-aligned.apk
if [ ! -f "${OUT}/debug.keystore" ]; then
  keytool -genkeypair -keystore "${OUT}/debug.keystore" -storepass android \
    -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi
"${BT}/apksigner" sign --ks "${OUT}/debug.keystore" --ks-pass pass:android \
  --key-pass pass:android --out "${GALLERY_DIR}/gallery.apk" gallery-aligned.apk

echo "Built ${GALLERY_DIR}/gallery.apk"
