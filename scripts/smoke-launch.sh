#!/usr/bin/env bash
set -euo pipefail
package=com.mdiaz.trxlauncher
trap 'adb logcat -d -b crash -s AndroidRuntime:E; echo "Startup smoke test failed at line $LINENO"' ERR
adb install app/build/outputs/apk/release/app-release.apk
# Fresh emulator install: no runtime permissions are pre-granted.
adb shell pm revoke "$package" android.permission.BLUETOOTH_CONNECT
permission_dump=$(adb shell dumpsys package "$package")
if grep -q 'android.permission.BLUETOOTH_CONNECT: granted=true' <<< "$permission_dump"; then
  echo 'FAIL: Bluetooth permission unexpectedly granted.'
  exit 1
fi
adb logcat -c
for attempt in 1 2; do
  adb shell am force-stop "$package"
  adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$package/.MainActivity"
  sleep 5
  initial_pid=$(adb shell pidof "$package" | tr -d '\r')
  test -n "$initial_pid"
  sleep 10
  final_pid=$(adb shell pidof "$package" | tr -d '\r')
  test "$initial_pid" = "$final_pid"
  adb shell dumpsys activity activities | grep -E "mResumedActivity.*$package|topResumedActivity.*$package"
done
if adb logcat -d -b crash | grep -q "Process: $package"; then
  echo 'FAIL: TRX Launcher crashed during startup.'
  exit 1
fi
if adb shell dumpsys activity services "$package" | grep -q 'ServiceRecord.*ObdService'; then
  echo 'FAIL: OBD service started on Home without user permission.'
  exit 1
fi
echo 'PASS: fresh launch and relaunch remained foreground with Bluetooth permission denied.'
