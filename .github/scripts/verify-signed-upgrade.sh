#!/usr/bin/env bash
# Real Android package-manager regression: official v1.0.17 -> signed v1.0.18.
# This is an in-place upgrade; do NOT uninstall, clear package data, or flash.
set -euo pipefail
PKG=dev.nooir.mihontrial.foss
OLD=official-v1.0.17/app-x86_64-foss.apk
NEW=candidate-v1.0.18/app-x86_64-foss.apk

test -s "$OLD"
test -s "$NEW"
adb wait-for-device
adb install -r "$OLD"
adb shell pm path "$PKG" | grep -q 'package:'
adb shell dumpsys package "$PKG" | tr -d '\r' | grep -q 'versionCode=1000017'

# PackageManager's -r preserves the app's existing UID/data; package validity
# and upgrade signatures are verified by Android itself before replacement.
adb install -r "$NEW"
adb shell pm path "$PKG" | grep -q 'package:'
adb shell dumpsys package "$PKG" | tr -d '\r' | grep -q 'versionCode=1000018'
adb shell dumpsys package "$PKG" | tr -d '\r' | grep -q 'versionName=1.0.18'

echo 'PASS: official v1.0.17 upgraded in-place to persistently signed v1.0.18 without uninstall'
echo 'This does not substitute for real-device library, data-migration or UI testing.'
