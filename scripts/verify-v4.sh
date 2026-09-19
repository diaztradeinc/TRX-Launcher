#!/usr/bin/env bash
set -euo pipefail
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell wm size 1080x1440
adb shell wm density 160
adb shell am start -W -n com.mdiaz.trxlauncher/.MainActivity
sleep 4
adb shell am force-stop com.mdiaz.trxlauncher
adb shell pm grant com.mdiaz.trxlauncher android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant com.mdiaz.trxlauncher android.permission.ACCESS_FINE_LOCATION
adb shell run-as com.mdiaz.trxlauncher mkdir -p shared_prefs
printf '%s\n' '<?xml version="1.0" encoding="utf-8"?><map><boolean name="first_run_complete" value="true"/><int name="theme_choice" value="0"/></map>' | adb shell run-as com.mdiaz.trxlauncher sh -c '"cat > shared_prefs/launcher.xml"'
adb logcat -c
adb shell am start -W -n com.mdiaz.trxlauncher/.MainActivity
sleep 8
mkdir -p verification
for page in 108 324 540 756 972; do
  adb shell input tap "$page" 1360
  sleep 2
  for tab in 140 357 574 791; do
    adb shell input tap "$tab" 355
    sleep 1
    test -n "$(adb shell pidof com.mdiaz.trxlauncher)"
    adb exec-out screencap -p > "verification/page-${page}-tab-${tab}.png"
    if [ "$page" = 324 ] && { [ "$tab" = 574 ] || [ "$tab" = 791 ]; }; then adb shell input keyevent BACK; fi
  done
done
adb logcat -d -b crash > verification/crash-log.txt
if grep -q 'Process: com.mdiaz.trxlauncher' verification/crash-log.txt; then
  cat verification/crash-log.txt
  exit 1
fi
