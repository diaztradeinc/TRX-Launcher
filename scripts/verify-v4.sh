#!/usr/bin/env bash
set -euo pipefail
mkdir -p verification
capture_diagnostics() {
  adb logcat -d | python3 -c 'import sys,re; lines=[re.sub(r"AIza[0-9A-Za-z_-]+", "[REDACTED]",s) for s in sys.stdin if re.search(r"TRXNavigation|Authorization failure|not authorized|API project|API key|Google Maps Android API",s,re.I)];print("".join(lines))' > verification/navigation-diagnostics.txt
  cat verification/navigation-diagnostics.txt
}
trap capture_diagnostics EXIT
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
  adb shell am start -W -n com.mdiaz.trxlauncher/.MainActivity
  sleep 1
  adb shell input tap "$page" 1360
  sleep 2
  for tab in 140 357 574 791; do
    adb shell input tap "$tab" 355
    sleep 1
    test -n "$(adb shell pidof com.mdiaz.trxlauncher)"
    adb shell dumpsys activity activities | grep 'mResumedActivity' | grep -q 'com.mdiaz.trxlauncher'
    adb exec-out screencap -p > "verification/page-${page}-tab-${tab}.png"
    if [ "$page" = 324 ] && { [ "$tab" = 574 ] || [ "$tab" = 791 ]; }; then adb shell input keyevent BACK; sleep 2; fi
  done
done
adb logcat -d -b crash > verification/crash-log.txt
if grep -q 'Process: com.mdiaz.trxlauncher' verification/crash-log.txt; then
  cat verification/crash-log.txt
  exit 1
fi
