#!/bin/bash
# Re-enables everything debloat-firetv.sh disabled.
#   scripts/restore-firetv.sh 192.168.100.14
export ANDROID_SERIAL="$1:5555"
adb connect "$ANDROID_SERIAL" >/dev/null
while read -r p; do
  [ -n "$p" ] && adb shell pm enable "$p" </dev/null >/dev/null 2>&1 && echo "enabled  $p"
done < "$(dirname "$0")/debloat-list.txt"
