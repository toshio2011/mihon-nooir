#!/usr/bin/env bash
# Windows-only full-app QA. The current Android main tests/run.sh remains intact.
set -euo pipefail
cd "$(dirname "$0")/.."
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# Inspect native binary page tables and pixels from finished XTC/XTCH outputs,
# with the same experimental decoder used by Android. No display required.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,XtcBookPreview.kt} \
  tests/WindowsXtcBinaryPreviewTest.kt \
  -include-runtime -d "$tmp/windows-binary-preview.jar"
java -Djava.awt.headless=true -jar "$tmp/windows-binary-preview.jar"

# Truly exported XTC + XTCH pages are reopened from disk for all four modes.
# Synthetic contact sheets are retained in CI as visual-review artifacts.
kotlinc \
  overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,XtcBookPreview.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,GrayscaleConverter.kt,GrayscalePng.kt,NaturalPageOrder.kt,PageSlicePlanner.kt,PanelContextPeek.kt,PanelDetectionCore.kt,PanelViewportPlanner.kt,PanelAutoFit.kt,PanelWhitespaceCrop.kt,PanelSafeSlicePlanner.kt,PanelReadableSliceRefiner.kt} \
  overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt \
  desktop/cli/*.kt tests/DesktopVisualRenderingPreviewTest.kt \
  -include-runtime -d "$tmp/windows-rendered-visual-qa.jar"
java -Djava.awt.headless=true -cp "$tmp/windows-rendered-visual-qa.jar" DesktopVisualRenderingPreviewTestKt


# Windows full-app project: headless GUI model tests against exactly the
# current Android v1.0.17 shared renderer and conversion settings.
test -s build/desktop/lib/pdfbox-app-3.0.8.jar
sha512sum -c desktop/pdfbox-runtime.sha512
kotlinc -classpath "$PWD/build/desktop/lib/pdfbox-app-3.0.8.jar:$PWD/build/desktop/lib/jsoup-1.21.2.jar:$PWD/build/desktop/lib/json-20250517.jar" desktop/storage/DesktopDataHome.kt \
  overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,XtcBookPreview.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,GrayscaleConverter.kt,GrayscalePng.kt,NaturalPageOrder.kt,PageSlicePlanner.kt,PanelContextPeek.kt,PanelDetectionCore.kt,PanelViewportPlanner.kt,PanelAutoFit.kt,PanelWhitespaceCrop.kt,PanelSafeSlicePlanner.kt,PanelReadableSliceRefiner.kt,XteinkTransferPolicy.kt,ReflowEpubWriter.kt,NooirEpubCssRules.kt} \
  overlay/app/src/main/java/dev/nooir/mangabridge/{MangaConversionSettingsCore.kt,NooirEpubBook.kt,NooirCoverCollageLayout.kt,NooirWebImageProfile.kt,NooirWebChapterPlan.kt,NooirWebChapterIdentity.kt,NooirWebChapterUpdates.kt,NooirWebArticle.kt,NooirWebNovel.kt,NooirWebPageBook.kt,NooirWebChapterDiscovery.kt,NooirWebTocPagination.kt,NooirWebExtractRules.kt,NooirWebFetchPolicy.kt,NooirWebImageCandidates.kt,NooirWebImageSignature.kt,NooirWebReadability.kt,NooirWattpadPublic.kt} \
  desktop/cli/*.kt desktop/suwayomi/*.kt desktop/ui/*.kt \
  tests/DesktopGuiModelTest.kt tests/DesktopXtcViewerSwingSmokeTest.kt \
  tests/WindowsOfflineJourneyTest.kt tests/DesktopWebpSmokeTest.kt tests/DesktopConversionQueueTest.kt tests/DesktopReaderTransferTest.kt tests/DesktopDocumentImportTest.kt tests/DesktopWebImageAdaptersTest.kt tests/DesktopWebEpubTest.kt tests/DesktopWindowsUpdatesTest.kt \
  -include-runtime -d "$tmp/windows-full-gui-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/windows-full-gui-tests.jar" DesktopGuiModelTestKt
java -Djava.awt.headless=true -cp "$tmp/windows-full-gui-tests.jar:$PWD/build/desktop/lib/*" dev.nooir.desktop.ui.DesktopConversionQueueTestKt
java --add-modules jdk.httpserver -Djava.awt.headless=true -cp "$tmp/windows-full-gui-tests.jar" dev.nooir.desktop.ui.DesktopReaderTransferTestKt
java -Djava.awt.headless=true -cp "$tmp/windows-full-gui-tests.jar:$PWD/build/desktop/lib/*" dev.nooir.desktop.ui.DesktopDocumentImportTestKt
java -Djava.awt.headless=true -cp "$tmp/windows-full-gui-tests.jar" dev.nooir.desktop.ui.DesktopWebImageAdaptersTestKt
java -Djava.awt.headless=true -cp "$tmp/windows-full-gui-tests.jar:$PWD/build/desktop/lib/*" dev.nooir.desktop.ui.DesktopWebEpubTestKt
java -Djava.awt.headless=true -cp "$tmp/windows-full-gui-tests.jar" dev.nooir.desktop.ui.DesktopWindowsUpdatesTestKt
# Real Swing dialog acceptance using a virtual display. This exercises actual
# rendered saved-book pages, navigation, resize, fit and 1:1, without touching
# an installed Windows app. Off by default for developer headless terminals.
if [ "${NOOIR_DESKTOP_GUI_SMOKE:-0}" = "1" ]; then
  command -v xvfb-run >/dev/null ||
    { echo "Xvfb unavailable for real desktop UI smoke" >&2; exit 1; }
  MIHONNOOIR_DATA_DIR="$tmp/gui-library-isolated" xvfb-run -a -s "-screen 0 1366x768x24" \
    java -Djava.awt.headless=false -Dnooir.test.offlineLibrary=true -cp "$tmp/windows-full-gui-tests.jar:$PWD/build/desktop/lib/*" \
    dev.nooir.desktop.ui.DesktopXtcViewerSwingSmokeTestKt
fi
# Test the actual bundled pure-Java reader and the existing bounded source
# paths, using original synthetic VP8 + VP8L in a CBZ. Missing dependencies,
# corrupt images and partial-output promotion must fail CI.
test -s build/desktop/lib/imageio-webp-3.15.3.jar
java -Djava.awt.headless=true \
  -cp "$tmp/windows-full-gui-tests.jar:$PWD/build/desktop/lib/*" DesktopWebpSmokeTestKt
# Full Windows user journey: opt-in source request → separate offline CBZ copy →
# persisted bookshelf/reader → EPUB2 and XTC without a running server.
java --add-modules jdk.httpserver -Djava.awt.headless=true \
  -cp "$tmp/windows-full-gui-tests.jar" WindowsOfflineJourneyTestKt

# Windows-only lifecycle: launch arguments, pinned JAR, loopback binding and
# refusal to claim an external server. No Suwayomi jar is executed in CI.
kotlinc desktop/storage/DesktopDataHome.kt desktop/suwayomi/{MiniJson.kt,SuwayomiClient.kt,SuwayomiProcessManager.kt} \
  tests/WindowsSuwayomiProcessManagerTest.kt \
  -include-runtime -d "$tmp/windows-suwayomi-manager-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/windows-suwayomi-manager-tests.jar" WindowsSuwayomiProcessManagerTestKt

# Windows-only optional first-run server installer: verifies the official
# download path via localhost fixtures and never executes a downloaded JAR.
kotlinc desktop/storage/DesktopDataHome.kt desktop/suwayomi/{MiniJson.kt,SuwayomiClient.kt,SuwayomiProcessManager.kt,OfficialSuwayomiCache.kt} \
  tests/VerifiedSuwayomiCacheTest.kt \
  -include-runtime -d "$tmp/windows-verified-server-cache-tests.jar"
java --add-modules jdk.httpserver -Djava.awt.headless=true \
  -cp "$tmp/windows-verified-server-cache-tests.jar" VerifiedSuwayomiCacheTestKt

# Persistent Windows-only queue for explicit offline CBZ copies. No server,
# Android app source or network connection is needed for these tests.
kotlinc desktop/storage/DesktopDataHome.kt desktop/ui/OfflineSaveQueue.kt tests/OfflineSaveQueueTest.kt \
  -include-runtime -d "$tmp/windows-offline-save-queue-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/windows-offline-save-queue-tests.jar" OfflineSaveQueueTestKt

# Online reader transport contract: single-page requests, no source downloads in CI.
kotlinc desktop/suwayomi/{MiniJson.kt,SuwayomiClient.kt} \
  tests/WindowsOnlineChapterApiTest.kt -include-runtime -d "$tmp/windows-online-api.jar"
java --add-modules jdk.httpserver -Djava.awt.headless=true -cp "$tmp/windows-online-api.jar" WindowsOnlineChapterApiTestKt

# True Suwayomi remote download requests: synthetic localhost GraphQL only.
# No third-party manga, extension installation or Android build edits.
kotlinc desktop/storage/DesktopDataHome.kt desktop/suwayomi/{MiniJson.kt,SuwayomiClient.kt} \
  desktop/ui/{RemoteChapterQueue.kt,RemoteChapterRunner.kt} \
  tests/RemoteChapterQueueTest.kt \
  -include-runtime -d "$tmp/windows-remote-chapter-queue-tests.jar"
java --add-modules jdk.httpserver -Djava.awt.headless=true \
  -cp "$tmp/windows-remote-chapter-queue-tests.jar" RemoteChapterQueueTestKt

# User-selected completed remote downloads become explicit offline CBZ jobs.
# Neither a source request nor a CBZ copy runs during this handoff.
kotlinc desktop/storage/DesktopDataHome.kt desktop/ui/{RemoteChapterQueue.kt,OfflineSaveQueue.kt,RemoteChapterOfflineHandoff.kt} \
  tests/RemoteChapterOfflineHandoffTest.kt \
  -include-runtime -d "$tmp/windows-remote-offline-handoff-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/windows-remote-offline-handoff-tests.jar" RemoteChapterOfflineHandoffTestKt

# Windows-only bounded Download All queue: atomic capacity checks, dedupe and
# no source requests until a separate UI confirmation.
kotlinc desktop/storage/DesktopDataHome.kt desktop/ui/RemoteChapterQueue.kt tests/RemoteChapterBatchTest.kt \
  -include-runtime -d "$tmp/windows-remote-batch-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/windows-remote-batch-tests.jar" RemoteChapterBatchTestKt

# Windows-only multiple explicit page bookmarks, independently persisted
# from resume position; no Android app or server-side changes.
kotlinc desktop/storage/DesktopDataHome.kt desktop/ui/ReaderBookmarkStore.kt tests/ReaderBookmarkStoreTest.kt \
  -include-runtime -d "$tmp/windows-reader-bookmarks-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/windows-reader-bookmarks-tests.jar" ReaderBookmarkStoreTestKt

# The large desktop data root may be placed explicitly on another drive.
# Preserve legacy ~/.mihonnooir by default and do not migrate or delete data.
kotlinc desktop/storage/DesktopDataHome.kt tests/DesktopDataHomeTest.kt \
  -include-runtime -d "$tmp/windows-storage-root-tests.jar"
java -jar "$tmp/windows-storage-root-tests.jar"

# Read-only Android/Suwayomi backup preview; synthetic fixtures only.
kotlinc desktop/suwayomi/DesktopBackupInspector.kt tests/DesktopBackupInspectorTest.kt -include-runtime -d "$tmp/desktop-backup-inspector.jar"
java -jar "$tmp/desktop-backup-inspector.jar"

# Localhost-only backup transport contract; synthetic protocol fixture.
kotlinc desktop/suwayomi/{MiniJson.kt,DesktopBackupInspector.kt,DesktopBackupTransport.kt} tests/DesktopBackupTransportTest.kt -include-runtime -d "$tmp/desktop-backup-transport.jar"
java --add-modules jdk.httpserver -jar "$tmp/desktop-backup-transport.jar"
