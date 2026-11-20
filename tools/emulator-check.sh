#!/usr/bin/env bash
# Installs SceneCam on the emulator, opens it, and saves screenshots + crash logs to test-out/.
set -u
OUT=test-out
mkdir -p "$OUT"
PKG=com.sushil.scenecam
APK=app/build/outputs/apk/debug/app-debug.apk

adb install -r "$APK"
adb logcat -c

shot() { adb exec-out screencap -p > "$OUT/$1.png" || true; }
crashed() {
  adb logcat -d -b crash > "$OUT/crash.txt" 2>/dev/null || true
  [ -s "$OUT/crash.txt" ]
}

# 1) First launch: permission dialog
adb shell am start -W -n $PKG/.CameraActivity
sleep 6
shot 1-first-launch

# 2) Answer the permission dialogs like a person would ("While using the app"), then check the camera is up
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 1
  local b
  b=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -m1 "text=\"$1\"" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+')
  [ -z "$b" ] && return 1
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
for n in 1 2; do
  tap_text "While using the app" || tap_text "Allow" || true
  sleep 3
done
shot 2a-after-allow
# Fallback so the rest of the test can run even if the dialog wasn't found
adb shell pm grant $PKG android.permission.CAMERA || true
adb shell pm grant $PKG android.permission.RECORD_AUDIO || true
sleep 5
shot 2-camera-photo

# 3) Take a photo (tap the shutter: centre-bottom of screen)
SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}
adb shell input tap $((W / 2)) $((H - H * 9 / 100))
sleep 4
shot 3-after-shutter

# 4) Swipe the scene bar / tap a scene to the right of centre
adb shell input tap $((W * 3 / 4)) $((H - H * 27 / 100))
sleep 5
shot 4-next-scene

adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
grep -E "AndroidRuntime|FATAL|SceneCam|scenecam|CameraDevice|Camera2" "$OUT/logcat.txt" | tail -200 > "$OUT/relevant.txt" || true

echo "================ CRASH BUFFER ================"
if crashed; then cat "$OUT/crash.txt"; else echo "(no crash)"; fi
echo "================ RELEVANT LOG ================"
cat "$OUT/relevant.txt" | tail -80
exit 0
