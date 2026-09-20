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
python3 scripts/tap-text.py 'GOT IT' || true
adb emu geo fix -122.084 37.422
sleep 3
mkdir -p verification
for page in 108 324 540 756 972; do
  adb shell am start -W -n com.mdiaz.trxlauncher/.MainActivity
  sleep 1
  adb shell input tap "$page" 1360
  sleep 2
  python3 scripts/tap-text.py 'GOT IT' || true
  for tab in 140 357 574 791; do
    adb shell input tap "$tab" 115
    sleep 1
    test -n "$(adb shell pidof com.mdiaz.trxlauncher)"
    adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | grep -q 'com.mdiaz.trxlauncher'
    adb exec-out screencap -p > "verification/page-${page}-tab-${tab}.png"
    if [ "$page" = 324 ] && { [ "$tab" = 574 ] || [ "$tab" = 791 ]; }; then adb shell input keyevent BACK; sleep 2; fi
  done
done
adb shell input tap 324 1360
sleep 2
python3 scripts/tap-text.py 'GOT IT' || true
adb shell input tap 140 115
sleep 2
python3 scripts/tap-text.py 'Where to?'
adb shell input text 'Stanford%sUniversity'
for attempt in 1 2 3 4 5 6; do
  sleep 3
  if python3 scripts/tap-text.py 'STANFORD UNIVERSITY' --case-sensitive; then break; fi
done
adb emu geo fix -122.084 37.422
sleep 15
adb exec-out screencap -p > verification/turn-by-turn.png
capture_diagnostics
grep -q 'PLACES_READY' verification/navigation-diagnostics.txt
grep -q 'ROUTE_READY' verification/navigation-diagnostics.txt
grep -q 'TRUCK_READY' verification/navigation-diagnostics.txt
python3 scripts/tap-text.py '■  END'
adb shell am start -W -n com.mdiaz.trxlauncher/.MainActivity
sleep 2
adb shell input tap 108 1360
sleep 2
adb shell input tap 974 115
sleep 2
adb exec-out screencap -p > verification/settings.png
python3 scripts/tap-text.py 'OEM BLUE'
for attempt in 1 2 3 4 5 6 7 8; do
  if python3 scripts/tap-text.py 'SAVE & RETURN TO TRX'; then break; fi
  adb shell input swipe 540 1180 540 350 350
done
sleep 2
adb shell run-as com.mdiaz.trxlauncher cat shared_prefs/launcher.xml | grep -q 'name="theme_choice" value="3"'
adb exec-out screencap -p > verification/theme-blue.png
adb logcat -d -b crash > verification/crash-log.txt
if grep -q 'Process: com.mdiaz.trxlauncher' verification/crash-log.txt; then
  cat verification/crash-log.txt
  exit 1
fi
capture_diagnostics
grep -q 'BASEMAP_READY' verification/navigation-diagnostics.txt
if grep -qi 'Authorization failure' verification/navigation-diagnostics.txt; then
  echo 'Google authorization failed; withholding APK.'
  exit 1
fi
