#!/usr/bin/env bash
# Verify the public Android app and QA app coexist on a disposable emulator.
# This script NEVER runs on the user's phone or uninstalls either application.
set -euo pipefail
official="dev.nooir.mihontrial.foss"
qa="dev.nooir.mihontrial.foss.qa"
official_apk="official/app-universal-foss.apk"
qa_apk="qa-artifact/MihonNooir-QA-universal.apk"
test -s "$official_apk"
test -s "$qa_apk"

capture_failure() {
  local rc="$1"
  trap - ERR
  adb logcat -d -v threadtime -t 4000 > qa-emulator-logcat.txt || true
  exit "$rc"
}
trap 'capture_failure $?' ERR

package_uid() {
  adb shell pm list packages -U | tr -d '\r' |
    awk -v name="package:$1" '$1 == name { print $2 }'
}

echo "Install official stable-signed app first; verify QA does not replace it"
adb install -r "$official_apk"
before_uid=$(package_uid "$official")
test -n "$before_uid"
if [[ -n "$(package_uid "$qa")" ]]; then
  echo "Unexpected pre-installed QA tester" >&2
  exit 1
fi
adb install -r "$qa_apk"
after_uid=$(package_uid "$official")
qa_uid=$(package_uid "$qa")
test -n "$qa_uid"
test "$before_uid" = "$after_uid"
test "$qa_uid" != "$after_uid"
{
  printf 'Official package: %s | uid: %s (unchanged)\n' "$official" "$after_uid"
  printf 'QA package: %s | uid: %s (separate)\n' "$qa" "$qa_uid"
  adb shell pm path "$official"
  adb shell pm path "$qa"
} | tee qa-emulator-install.txt

echo "Launch QA by its own manifest activity, never launch a package installer"
adb shell am start -W -n \
  "$qa/eu.kanade.tachiyomi.ui.main.MainActivity" |
  tee qa-emulator-launch.txt
grep -Fq 'Status: ok' qa-emulator-launch.txt
sleep 4
test -n "$(package_uid "$official")"
test "$(package_uid "$official")" = "$before_uid"
test -n "$(package_uid "$qa")"
if adb logcat -d -v brief -t 500 | grep -F "FATAL EXCEPTION" | grep -F "$qa"; then
  echo "QA app crashed after launch" >&2
  exit 1
fi
echo "PASS: published v1.0.17 and isolated QA coexist with different UIDs; official app remains installed"
