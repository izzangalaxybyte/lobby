#!/bin/bash
# Installs homehound on a Fire TV or Android TV over network adb and turns on the Home button
# service, so pressing Home opens homehound instead of the stock launcher.
#   scripts/setup-device.sh 192.168.100.14 [path/to/homehound.apk]
set -euo pipefail
export ANDROID_SERIAL="$1:5555"
cd "$(dirname "$0")/.."
APK="${2:-$(ls -t app/build/outputs/apk/release/homehound-*-release.apk | head -1)}"

adb connect "$ANDROID_SERIAL" >/dev/null
adb install -r "$APK"

SVC=com.thegalaxybyte.homehound/com.thegalaxybyte.homehound.HomeButtonService
cur=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
case "$cur" in
  *"$SVC"*) ;;
  null|"") adb shell settings put secure enabled_accessibility_services "$SVC" ;;
  *) adb shell settings put secure enabled_accessibility_services "$cur:$SVC" ;;
esac
adb shell settings put secure accessibility_enabled 1
adb shell input keyevent KEYCODE_HOME
echo "homehound is set up on $ANDROID_SERIAL"
