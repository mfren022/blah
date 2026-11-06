#!/usr/bin/env bash
# Builds android/build/Marginalia.apk: the Marginalia web app, bundled into assets/www,
# inside the small WebView shell in this folder. No Gradle needed.
#
# Needs Node + npm (the web build), a JDK (javac) and the Android build tools: aapt2, dx,
# zipalign, apksigner and a platform android.jar (API 23 or newer).
#   Ubuntu/Debian: sudo apt-get install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23
#   Android SDK:   ANDROID_HOME=~/Android/Sdk bash android/build-apk.sh   (set DX to a d8 wrapper if dx is gone)
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
APP="$(cd "$HERE/.." && pwd)"
OUT="$HERE/build"

SDK="${ANDROID_HOME:-/usr/lib/android-sdk}"
BUILD_TOOLS="${BUILD_TOOLS:-$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)}"
ANDROID_JAR="${ANDROID_JAR:-$(ls "$SDK"/platforms/android-*/android.jar | sort -V | tail -1)}"
AAPT2="${AAPT2:-$BUILD_TOOLS/aapt2}"
DX="${DX:-$BUILD_TOOLS/dx}"
ZIPALIGN="${ZIPALIGN:-$BUILD_TOOLS/zipalign}"
APKSIGNER="${APKSIGNER:-$BUILD_TOOLS/apksigner}"

MIN_SDK=24     # Android 7.0
TARGET_SDK=34  # Android 14
VERSION_NAME="$(node -p "require('$APP/package.json').version")"
VERSION_CODE="${VERSION_CODE:-$(git -C "$APP" rev-list --count HEAD 2>/dev/null || echo 1)}"

# Signing key. The first build creates one (with a random password) in android/signing/,
# which git ignores. Keep that folder: Android only installs an update over an existing copy
# (keeping its library) when both are signed with the same key.
KEYSTORE="${KEYSTORE:-$HERE/signing/sideload.jks}"
KEY_ALIAS="${KEY_ALIAS:-marginalia}"
PASS_FILE="${PASS_FILE:-${KEYSTORE%.*}.pass}"
if [ ! -f "$KEYSTORE" ]; then
  echo "› Creating a signing key in $(dirname "$KEYSTORE") (keep it to update installed copies)"
  mkdir -p "$(dirname "$KEYSTORE")"
  (umask 077 && head -c 24 /dev/urandom | base64 | tr -d '/+=\n' > "$PASS_FILE")
  keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -storepass "$(cat "$PASS_FILE")" \
    -alias "$KEY_ALIAS" -keyalg RSA -keysize 3072 -validity 10950 \
    -dname "CN=Marginalia (sideload build)" >/dev/null 2>&1
fi
KEY_PASS="${KEY_PASS:-$(cat "$PASS_FILE")}"

rm -rf "$OUT"
mkdir -p "$OUT/assets" "$OUT/classes"

echo "› Building the web app"
(cd "$APP" && npx vite build --mode android --outDir "$OUT/assets/www" --emptyOutDir --logLevel warn)
rm -f "$OUT/assets/www/sw.js" # everything is bundled in the APK; no service worker

echo "› Linking resources, manifest and assets"
"$AAPT2" compile --dir "$HERE/res" -o "$OUT/res.zip"
"$AAPT2" link -o "$OUT/unsigned.apk" \
  --manifest "$HERE/AndroidManifest.xml" -I "$ANDROID_JAR" -A "$OUT/assets" \
  --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  "$OUT/res.zip"

echo "› Compiling Java"
javac -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -Xlint:-options -encoding UTF-8 \
  -d "$OUT/classes" $(find "$HERE/src" -name '*.java')
"$DX" --dex --min-sdk-version="$MIN_SDK" --output="$OUT/classes.dex" "$OUT/classes"
(cd "$OUT" && zip -q -j unsigned.apk classes.dex)

echo "› Aligning and signing"
"$ZIPALIGN" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEY_PASS" --key-pass "pass:$KEY_PASS" \
  --min-sdk-version "$MIN_SDK" --out "$OUT/Marginalia.apk" "$OUT/aligned.apk"
"$APKSIGNER" verify "$OUT/Marginalia.apk"
rm -rf "$OUT/aligned.apk" "$OUT/unsigned.apk" "$OUT/res.zip" "$OUT/classes" "$OUT/classes.dex"

echo "✓ $OUT/Marginalia.apk — $(du -h "$OUT/Marginalia.apk" | cut -f1), version $VERSION_NAME ($VERSION_CODE)"
