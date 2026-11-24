#!/usr/bin/env bash
# Execute from android-emulator-runner as ONE bash process. The action runs
# individual 'script:' lines in separate shells, so shell variables do not
# survive across YAML lines.
set -euo pipefail

capture_android_failure() {
  local status="$1"
  trap - ERR
  echo "Android diagnostic test failed (exit $status). Capturing crash evidence."
  adb logcat -d -v threadtime -t 5000 > android-panel-diagnostic-logcat.txt || true
  echo "AndroidRuntime/test runner crash lines:"
  grep -n -E -A 16 -B 4 'FATAL EXCEPTION|AndroidRuntime|INSTRUMENTATION_RESULT|Process crashed|AssertionFailedError|ClassNotFoundException|NoClassDefFoundError|Unable to instantiate' android-panel-diagnostic-logcat.txt | tail -n 180 || true
  adb shell pm list instrumentation > android-panel-instrumentation-packages.txt || true
  exit "$status"
}
trap 'capture_android_failure $?' ERR

echo "Checking exact-commit app and Android instrumentation APKs"
find diagnostic-apks -type f -name '*.apk' -print
app="diagnostic-apks/foss/app-universal-foss.apk"
test_apk="diagnostic-apks/androidTest/foss/app-foss-androidTest.apk"
test -s "$app"
test -s "$test_apk"

adb install -r "$app"
adb install -r "$test_apk"
adb shell pm list instrumentation | tee android-panel-instrumentation-packages.txt
adb logcat -c

echo "Running Android panel diagnostics, footer-render and saved XTC/XTCH SAF tests"
adb shell am instrument -w -r \
  -e class dev.nooir.mangabridge.PanelDiagnosticArchiveInstrumentedTest \
  dev.nooir.mihontrial.foss.test/androidx.test.runner.AndroidJUnitRunner \
  | tee diagnostic-instrumentation.txt

# All five tests MUST have run and passed, and the runner must have finished
# successfully. The old hardcoded "OK (1 test)" check was incorrect after
# introducing the second instrumentation test.
for required in \
  realAndroidCbzZipAndRenderParity \
  shortRuledFooterStaysOneFrameThroughAndroidRender \
  savedBinaryPreviewReadsActualSafBytes \
  oversizedRowZoomRespectsToggleAndAutoFit \
  mixedSlantedRowsAndPanelsThroughAndroidExport
do
  grep -Fq "INSTRUMENTATION_STATUS: test=${required}" diagnostic-instrumentation.txt
done
test "$(grep -c '^INSTRUMENTATION_STATUS_CODE: 0$' diagnostic-instrumentation.txt)" -eq 5
grep -Fxq 'OK (5 tests)' diagnostic-instrumentation.txt
grep -Fxq 'INSTRUMENTATION_CODE: -1' diagnostic-instrumentation.txt

echo "Running synthetic public TOC HTML and next-chapter exclusion checks on Android"
adb shell am instrument -w -r \
  -e class dev.nooir.mangabridge.NooirWebTocHtmlInstrumentedTest \
  dev.nooir.mihontrial.foss.test/androidx.test.runner.AndroidJUnitRunner \
  | tee android-web-toc-instrumentation.txt
grep -Fq 'INSTRUMENTATION_STATUS: test=numberedTocControlsAreNotAccidentallyImportedAsChapters' android-web-toc-instrumentation.txt
grep -Fq 'INSTRUMENTATION_STATUS: test=plainNextChapterLinkCannotAdvanceTocPage' android-web-toc-instrumentation.txt
grep -Fq 'INSTRUMENTATION_STATUS: test=queryBasedChapterUrlsKeepTheirStoryPath' android-web-toc-instrumentation.txt
grep -Fq 'INSTRUMENTATION_STATUS: test=encodedChapterPathsRemainExactAndQueryVariantsDeduplicate' android-web-toc-instrumentation.txt
grep -Fq 'INSTRUMENTATION_STATUS: test=consecutiveTocPagesMergeWithoutDuplicateChapters' android-web-toc-instrumentation.txt
grep -Fq 'INSTRUMENTATION_STATUS: test=unfinishedTocAfterSafetyLimitFailsRatherThanTruncates' android-web-toc-instrumentation.txt
grep -Fq 'INSTRUMENTATION_STATUS: test=wattpadPublicStoryDetailsIncludeCoverAndSynopsis' android-web-toc-instrumentation.txt
grep -Fxq 'OK (7 tests)' android-web-toc-instrumentation.txt
grep -Fxq 'INSTRUMENTATION_CODE: -1' android-web-toc-instrumentation.txt

echo "Exporting synthetic public HTML into real EPUB2/EPUB3 ZIP files on Android"
adb shell am instrument -w -r \
  -e class dev.nooir.mangabridge.NooirWebEpubEndToEndInstrumentedTest \
  dev.nooir.mihontrial.foss.test/androidx.test.runner.AndroidJUnitRunner \
  | tee android-web-epub-export-instrumentation.txt
for required in \
  actualHtmlExportsValidEpub2SectionsAndNcx \
  actualHtmlExportsValidEpub3SectionsAndNavigation \
  wattpadBookCoverAndSynopsisArePackagedInEpub2And3
do
  grep -Fq "INSTRUMENTATION_STATUS: test=${required}" android-web-epub-export-instrumentation.txt
done
test "$(grep -c '^INSTRUMENTATION_STATUS_CODE: 0$' android-web-epub-export-instrumentation.txt)" -eq 3
grep -Fxq 'OK (3 tests)' android-web-epub-export-instrumentation.txt
grep -Fxq 'INSTRUMENTATION_CODE: -1' android-web-epub-export-instrumentation.txt

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
