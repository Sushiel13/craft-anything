#!/usr/bin/env bash
# Installs SceneCam on the emulator, walks through it like a person, saves screenshots + crash logs to test-out/.
set -u
OUT=test-out
mkdir -p "$OUT"
PKG=com.sushil.scenecam
APK=app/build/outputs/apk/debug/app-debug.apk

adb install -r "$APK"
adb logcat -c

shot() { adb exec-out screencap -p > "$OUT/$1.png" || true; }
tap_text() {   # tap the first on-screen element whose text contains $1
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 1
  local b
  b=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -m1 "text=\"[^\"]*$1" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+')
  [ -z "$b" ] && { echo "not found: $1"; return 1; }
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}
scroll_down() { adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/4)) 400; sleep 1; }

adb shell am start -W -n $PKG/.MainActivity
sleep 4; shot 1-phone-picker
tap_text "Galaxy S Ultra"; sleep 3; shot 2-home-photo
scroll_down; shot 3-home-photo-scrolled
adb shell input swipe $((W/2)) $((H/4)) $((W/2)) $((H*3/4)) 300; adb shell input swipe $((W/2)) $((H/4)) $((W/2)) $((H*3/4)) 300; sleep 1
tap_text "Beach"; sleep 3; shot 4-beach-photo
scroll_down; shot 5-beach-photo-steps
scroll_down; shot 6-beach-photo-tips
tap_text "VIDEO" || true; sleep 2; adb shell input swipe $((W/2)) $((H/4)) $((W/2)) $((H*3/4)) 300; sleep 1; shot 7-beach-video
adb shell input keyevent KEYCODE_BACK; sleep 2
tap_text "VIDEO"; sleep 2
scroll_down
tap_text "Cinematic"; sleep 3; shot 8-cinematic
scroll_down; shot 9-cinematic-steps
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input swipe $((W/2)) $((H/4)) $((W/2)) $((H*3/4)) 300; sleep 1
tap_text "Change"; sleep 2; scroll_down; tap_text "Pro / Pro Max"; sleep 2
tap_text "PHOTO"; sleep 2; tap_text "Night City"; sleep 3; shot 10-iphone-night
scroll_down; shot 11-iphone-night-steps

adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
echo "================ CRASH BUFFER ================"
adb logcat -d -b crash > "$OUT/crash.txt" 2>/dev/null || true
if [ -s "$OUT/crash.txt" ]; then cat "$OUT/crash.txt"; else echo "(no crash)"; fi
exit 0
