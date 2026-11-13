#!/usr/bin/env bash
# Real Android package-manager regression: official v1.0.17 -> signed v1.0.18.
# Never uninstall or clear data. Verify exact package, version and retained UID.
set -Eeuo pipefail

PKG=dev.nooir.mihontrial.foss
OLD=official-v1.0.17/app-x86_64-foss.apk
NEW=candidate-v1.0.18/app-x86_64-foss.apk
test -s "$OLD"
test -s "$NEW"

temp=$(mktemp -d)
trap 'rm -rf "$temp"' EXIT

package_uid() {
  # Modern Android 'dumpsys package' may not include userId. Ask PackageManager
  # for the installed application's UID explicitly, and match the EXACT package.
  # Read a captured file to avoid pipefail/SIGPIPE and reject other packages.
  awk -v pkg="package:$PKG" '$1 == pkg {
    for (i=2; i<=NF; i++) {
      if ($i ~ /^uid:[0-9]+$/) {
        sub(/^uid:/, "", $i)
        print $i
        exit
      }
    }
  }' "$1"
}
read_package_uid() {
  local dest="$1"
  adb shell pm list packages -U "$PKG" > "$temp/$dest.uid"
  package_uid "$temp/$dest.uid"
}

check_package() {
  local ver="$1" name="$2" dest="$3" attempt
  echo "Checking installed $PKG version $name (code $ver)"
  # adb install -r returning Success may precede package state visibility.
  # Retry only bounded package-state reads, not the actual APK installation.
  for attempt in 1 2 3 4 5 6 7 8; do
    adb shell pm path "$PKG" > "$temp/$dest.paths" || true
    adb shell dumpsys package "$PKG" > "$temp/$dest.dump" || true
    tr -d '\r' < "$temp/$dest.dump" > "$temp/$dest.clean"
    if grep -Fq 'package:' "$temp/$dest.paths" &&
       grep -Fq "versionCode=$ver" "$temp/$dest.clean" &&
       grep -Fq "versionName=$name" "$temp/$dest.clean"; then
      echo "Package state confirmed: $name on attempt $attempt"
      return 0
    fi
    if [[ "$attempt" -lt 8 ]]; then sleep 1; fi
  done
  echo "FAIL: $PKG did not report expected version $name after $attempt checks" >&2
  echo "Package paths:" >&2
  cat "$temp/$dest.paths" >&2
  echo "Captured package version and UID lines (max 12):" >&2
  awk '/versionCode=|versionName=|userId=/ { print; if (++n >= 12) exit }' "$temp/$dest.clean" >&2
  return 1
}

adb wait-for-device
echo "Installing official signed v1.0.17 baseline without uninstall"
adb install -r "$OLD"
check_package 1000017 1.0.17 before
before_uid=$(read_package_uid before)
if [[ -z "$before_uid" ]]; then
  echo "FAIL: PackageManager did not report a UID for installed v1.0.17" >&2
  cat "$temp/before.uid" >&2
  exit 1
fi

# Android PackageManager enforces signing, manifest and version during -r.
# The existing installation/UID and app files must not be cleared.
echo "Installing persistent-key-signed v1.0.18 over v1.0.17"
adb install -r "$NEW"
check_package 1000018 1.0.18 after
after_uid=$(read_package_uid after)
if [[ -z "$after_uid" || "$after_uid" != "$before_uid" ]]; then
  echo "FAIL: Android package UID not preserved (before=$before_uid after=${after_uid:-missing})" >&2
  exit 1
fi

echo "PASS: Android upgraded signed official v1.0.17 to v1.0.18 in-place and retained UID $after_uid"
echo 'Real-library migration and browser download UX still require physical device QA.'
