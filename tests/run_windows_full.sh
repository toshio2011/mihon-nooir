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
  overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,XtcBookPreview.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,GrayscaleConverter.kt,GrayscalePng.kt,NaturalPageOrder.kt,PageSlicePlanner.kt,PanelContextPeek.kt,PanelDetectionCore.kt,PanelViewportPlanner.kt,PanelAutoFit.kt,PanelWhitespaceCrop.kt,PanelSafeSlicePlanner.kt} \
  overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt \
  desktop/cli/*.kt tests/DesktopVisualRenderingPreviewTest.kt \
  -include-runtime -d "$tmp/windows-rendered-visual-qa.jar"
java -Djava.awt.headless=true -cp "$tmp/windows-rendered-visual-qa.jar" DesktopVisualRenderingPreviewTestKt


# Windows full-app project: headless GUI model tests against exactly the
# current Android v1.0.17 shared renderer and conversion settings.
kotlinc desktop/storage/DesktopDataHome.kt \
  overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,GrayscaleConverter.kt,GrayscalePng.kt,NaturalPageOrder.kt,PageSlicePlanner.kt,PanelContextPeek.kt,PanelDetectionCore.kt,PanelViewportPlanner.kt,PanelAutoFit.kt,PanelWhitespaceCrop.kt,PanelSafeSlicePlanner.kt} \
  overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt \
  desktop/cli/*.kt desktop/suwayomi/*.kt desktop/ui/*.kt \
  tests/DesktopGuiModelTest.kt tests/WindowsOfflineJourneyTest.kt \
  -include-runtime -d "$tmp/windows-full-gui-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/windows-full-gui-tests.jar" DesktopGuiModelTestKt
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
