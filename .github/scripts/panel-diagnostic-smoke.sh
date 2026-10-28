#!/usr/bin/env bash
# Execute from android-emulator-runner as ONE bash process. The action runs
# individual 'script:' lines in separate shells, so shell variables do not
# survive across YAML lines.
set -euo pipefail
echo "Checking exact-commit app and Android instrumentation APKs"
find diagnostic-apks -type f -name '*.apk' -print
app="diagnostic-apks/foss/app-universal-foss.apk"
test_apk="diagnostic-apks/androidTest/foss/app-foss-androidTest.apk"
test -s "$app"
test -s "$test_apk"

adb install -r "$app"
adb install -r "$test_apk"

echo "Running Android CBZ-to-panel diagnostics parity/integrity test"
adb shell am instrument -w -r \
  -e class dev.nooir.mangabridge.PanelDiagnosticArchiveInstrumentedTest \
  dev.nooir.mihontrial.foss.test/androidx.test.runner.AndroidJUnitRunner \
  | tee diagnostic-instrumentation.txt

# Android runner writes one 'OK (1 test)' success line for our single test.
# Fail CI on test assertion failures, process crashes or a missing test.
grep -Eq '^OK \(1 test\)' diagnostic-instrumentation.txt

echo "Reading the actual privately saved diagnostic ZIP from the emulator"
adb root
adb wait-for-device
adb pull \
  /data/user/0/dev.nooir.mihontrial.foss/files/manganooir_panel_debug/latest.zip \
  android-runtime-panel-diagnostic.zip
test -s android-runtime-panel-diagnostic.zip
python3 tools/panel_diagnostic_inspect.py \
  android-runtime-panel-diagnostic.zip --json \
  | tee android-panel-diagnostic-inspection.json
