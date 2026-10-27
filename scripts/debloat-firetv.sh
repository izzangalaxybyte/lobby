#!/bin/bash
# Disables the Amazon apps in debloat-list.txt for user 0. Nothing is uninstalled: run
# restore-firetv.sh to turn them all back on. Amazon blocks a few (metrics, OTA, Appstore); those
# are reported and skipped.
#   scripts/debloat-firetv.sh 192.168.100.14
export ANDROID_SERIAL="$1:5555"
adb connect "$ANDROID_SERIAL" >/dev/null
while read -r p; do
  [ -z "$p" ] && continue
  # </dev/null: adb shell would otherwise eat the rest of the list from stdin.
  if adb shell pm disable-user --user 0 "$p" </dev/null 2>&1 | grep -q disabled-user; then
    echo "disabled  $p"
  else
    echo "skipped   $p (protected or missing)"
  fi
done < "$(dirname "$0")/debloat-list.txt"
