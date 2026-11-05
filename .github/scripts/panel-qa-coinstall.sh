#!/usr/bin/env bash
# Android emulator only: prove the publicly signed official APK and our
# differently named QA APK install together without replacing either.
set -euo pipefail

official=official-apk/app-x86_64-foss.apk
test -s "$official"
mapfile -t qa_apks < <(find qa-artifact -type f -name 'app-x86_64-foss.apk')
(( ${#qa_apks[@]} == 1 )) || { echo "Expected exactly one x86_64 QA APK"; exit 1; }
qa="${qa_apks[0]}"

echo "Installing unmodified signed release v1.0.16 into fresh emulator"
adb install "$official"
adb shell pm path dev.nooir.mihontrial.foss | tee qa-official-package.txt
grep -Fq 'package:' qa-official-package.txt

echo "Installing experimental v1.0.17 QA alongside the official app"
adb install "$qa"
adb shell pm path dev.nooir.mihonqa10.foss | tee qa-parallel-package.txt
grep -Fq 'package:' qa-parallel-package.txt

# Neither installation may disappear or alias the other package.
adb shell pm list packages | tee qa-package-list.txt
grep -Fxq 'package:dev.nooir.mihontrial.foss' qa-package-list.txt
grep -Fxq 'package:dev.nooir.mihonqa10.foss' qa-package-list.txt
echo "PASS: both independently named packages coexist on Android"

echo "Launching QA only"
adb shell monkey -p dev.nooir.mihonqa10.foss -c android.intent.category.LAUNCHER 1 | tee qa-launch.txt
grep -Fq 'Events injected: 1' qa-launch.txt
sleep 4
adb shell pidof dev.nooir.mihonqa10.foss | tee qa-running-process.txt
test -s qa-running-process.txt
echo "PASS: QA app starts while official package remains installed"
