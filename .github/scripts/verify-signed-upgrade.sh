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
  # Android dumpsys formatting can include extra fields after userId. Parse
  # package-owned userId from a captured file, not a live pipe (SIGPIPE risk).
  awk -F= '/^[[:space:]]*userId=[0-9]+/ {
    if (match($2, /^[0-9]+/)) {
      print substr($2, RSTART, RLENGTH)
      exit
    }
  }' "$1"
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
before_uid=$(package_uid "$temp/before.clean")
if [[ -z "$before_uid" ]]; then
  echo "FAIL: Installed v1.0.17 package dump did not expose a numeric userId" >&2
  awk '/userId=|versionCode=|versionName=/ {print; if (++n >= 12) exit}' "$temp/before.clean" >&2
  exit 1
fi

# Android PackageManager enforces signing, manifest and version during -r.
# The existing installation/UID and app files must not be cleared.
echo "Installing persistent-key-signed v1.0.18 over v1.0.17"
adb install -r "$NEW"
check_package 1000018 1.0.18 after
after_uid=$(package_uid "$temp/after.clean")
if [[ -z "$after_uid" || "$after_uid" != "$before_uid" ]]; then
  echo "FAIL: Android package UID not preserved (before=$before_uid after=${after_uid:-missing})" >&2
  exit 1
fi

echo "PASS: official v1.0.17 upgraded in place to v1.0.18; package UID preserved ($after_uid)"
echo 'Real-library migration and browser download UX still require physical device QA.'
