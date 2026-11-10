#!/usr/bin/env bash
# Android package-manager regression: official v1.0.17 -> signed v1.0.18.
# Never uninstall, clear data, or flash. Exact package+version+UID must persist.
set -euo pipefail
PKG=dev.nooir.mihontrial.foss
OLD=official-v1.0.17/app-x86_64-foss.apk
NEW=candidate-v1.0.18/app-x86_64-foss.apk
test -s "$OLD"
test -s "$NEW"

temp=$(mktemp -d)
trap 'rm -rf "$temp"' EXIT
package_uid() {
  awk -F= '/^[[:space:]]*userId=[0-9]+[[:space:]]*$/ {gsub(/[[:space:]]/, "", $2); print $2; exit}' "$1"
}
check_package() {
  local ver="$1" name="$2" dest="$3"
  # Capture output to a file first. With pipefail, grep -q on the live
  # dumpsys stream can terminate early and turn success into SIGPIPE (141).
  adb shell pm path "$PKG" > "$temp/$dest.paths"
  grep -Fq 'package:' "$temp/$dest.paths"
  adb shell dumpsys package "$PKG" > "$temp/$dest.dump"
  tr -d '\r' < "$temp/$dest.dump" > "$temp/$dest.clean"
  grep -Fq "versionCode=$ver" "$temp/$dest.clean"
  grep -Fq "versionName=$name" "$temp/$dest.clean"
}
adb wait-for-device
adb install -r "$OLD"
check_package 1000017 1.0.17 before
before_uid=$(package_uid "$temp/before.clean")
test -n "$before_uid"

# Android PackageManager validates signing, manifest and version while -r
# retains the existing package installation and its app data/UID.
adb install -r "$NEW"
check_package 1000018 1.0.18 after
after_uid=$(package_uid "$temp/after.clean")
test -n "$after_uid"
test "$before_uid" = "$after_uid"
echo "PASS: Android upgraded signed official v1.0.17 to v1.0.18 in-place and retained UID $after_uid"
echo 'This cannot prove actual user-library migration or Android browser download UX.'
