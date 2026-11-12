#!/usr/bin/env bash
# Real Android integration of the 1,001-page PDF path. Generated pages only.
set -euo pipefail

on_error() {
  local status="$1"
  trap - ERR
  adb logcat -d -v threadtime -t 3500 > android-pdf-long-logcat.txt || true
  grep -n -E -A 15 -B 3 'FATAL EXCEPTION|AndroidRuntime|AssertionFailedError|OutOfMemoryError|INSTRUMENTATION_RESULT|Process crashed' \
    android-pdf-long-logcat.txt | tail -n 140 || true
  exit "$status"
}
trap 'on_error $?' ERR

app=diagnostic-apks/foss/app-universal-foss.apk
test_apk=diagnostic-apks/androidTest/foss/app-foss-androidTest.apk
test -s "$app"
test -s "$test_apk"
adb install -r "$app"
adb install -r "$test_apk"
adb logcat -c
adb shell am instrument -w -r \
  -e class dev.nooir.mangabridge.PdfLongExportInstrumentedTest \
  dev.nooir.mihontrial.foss.test/androidx.test.runner.AndroidJUnitRunner \
  | tee android-pdf-long-instrumentation.txt
grep -Fq 'INSTRUMENTATION_STATUS: test=converts1001PageOriginalPdfWithoutTruncation' android-pdf-long-instrumentation.txt
grep -Fq 'INSTRUMENTATION_STATUS: test=mixedModeKeepsBlankAndVectorPages' android-pdf-long-instrumentation.txt
grep -Fq 'INSTRUMENTATION_STATUS: test=mixedTextLayerKeeps257SelectablePagesInOrder' android-pdf-long-instrumentation.txt
grep -Fq 'INSTRUMENTATION_STATUS: test=phoneSaveToAndroidDownloadsAndCancelWithoutDeleting' android-pdf-long-instrumentation.txt
grep -Fq 'INSTRUMENTATION_STATUS: test=mixedTextLayerPreserves2001PagesWithoutTruncation' android-pdf-long-instrumentation.txt
grep -Fq 'INSTRUMENTATION_STATUS: test=mixedTextWithEmbeddedIllustrationRemainsReflowable' android-pdf-long-instrumentation.txt
grep -Fq 'INSTRUMENTATION_STATUS: test=pdfXtcAndXtchHaveValid17PageTablesOnX3AndX4' android-pdf-long-instrumentation.txt
test "$(grep -c '^INSTRUMENTATION_STATUS_CODE: 0$' android-pdf-long-instrumentation.txt)" -eq 7
grep -Fxq 'OK (7 tests)' android-pdf-long-instrumentation.txt
grep -Fxq 'INSTRUMENTATION_CODE: -1' android-pdf-long-instrumentation.txt
echo 'PASS: Android 1,001 original, 257/2,001 text pages, mixed figure, MediaStore cancellation, X3/X4 XTC/XTCH page tables'
