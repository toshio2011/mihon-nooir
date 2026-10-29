#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/NooirExportPreflight.kt tests/PreflightTest.kt -include-runtime -d "$tmp/preflight.jar"
java -jar "$tmp/preflight.jar"

kotlinc overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,GrayscaleConverter.kt} tests/CoreSmokeTest.kt -include-runtime -d "$tmp/smoke.jar"
java -jar "$tmp/smoke.jar"

kotlinc overlay/app/src/main/java/dev/nooir/bridge/XtcCodec.kt tests/XtcExtendedMetadataTest.kt -include-runtime -d "$tmp/xtc-metadata.jar"
java -jar "$tmp/xtc-metadata.jar"

kotlinc overlay/app/src/main/java/dev/nooir/bridge/XtcCodec.kt tests/XtcFolioTest.kt -include-runtime -d "$tmp/folio-xtc.jar"
java -jar "$tmp/folio-xtc.jar"
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,GrayscaleConverter.kt} tests/CoreChapterTest.kt -include-runtime -d "$tmp/chapters.jar"
java -jar "$tmp/chapters.jar"

kotlinc overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt} tests/FormatsTest.kt -include-runtime -d "$tmp/formats.jar"
java -jar "$tmp/formats.jar"

kotlinc overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt} tests/Epub2Test.kt -include-runtime -d "$tmp/epub2.jar"
java -jar "$tmp/epub2.jar"

kotlinc overlay/app/src/main/java/dev/nooir/bridge/PdfReflowLines.kt tests/PdfReflowLinesTest.kt -include-runtime -d "$tmp/pdf-reflow.jar"
java -jar "$tmp/pdf-reflow.jar"

kotlinc overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,ReflowEpubWriter.kt} tests/ReadableFixturesTest.kt -include-runtime -d "$tmp/readable-fixtures.jar"
java -Djava.awt.headless=true -jar "$tmp/readable-fixtures.jar"

kotlinc overlay/app/src/main/java/dev/nooir/bridge/PageSlicePlanner.kt tests/PageSlicePlannerTest.kt -include-runtime -d "$tmp/slicing.jar"
java -jar "$tmp/slicing.jar"

kotlinc overlay/app/src/main/java/dev/nooir/bridge/ReflowEpubWriter.kt tests/ReflowEpubTest.kt -include-runtime -d "$tmp/reflow.jar"
java -jar "$tmp/reflow.jar"

# Desktop uses the same source files as Android; never maintain duplicate codecs.
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt tests/ConversionOptionsParityTest.kt -include-runtime -d "$tmp/options-parity.jar"
java -jar "$tmp/options-parity.jar"

kotlinc \
  overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,GrayscaleConverter.kt,GrayscalePng.kt,NaturalPageOrder.kt,PageSlicePlanner.kt,PanelDetectionCore.kt,PanelViewportPlanner.kt,PanelAutoFit.kt,PanelWhitespaceCrop.kt,PanelSafeSlicePlanner.kt} \
  overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt \
  desktop/cli/NooirDesktopCli.kt tests/DesktopCliTest.kt \
  -include-runtime -d "$tmp/desktop-cli-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/desktop-cli-tests.jar" DesktopCliTestKt

# D0: exercise a mock Suwayomi v2.4.2366 GraphQL/CBZ server and export its
# downloaded chapter images with the exact Android-compatible writers.
kotlinc \
  overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,GrayscaleConverter.kt,GrayscalePng.kt,NaturalPageOrder.kt,PageSlicePlanner.kt,PanelDetectionCore.kt,PanelViewportPlanner.kt,PanelAutoFit.kt,PanelWhitespaceCrop.kt,PanelSafeSlicePlanner.kt} \
  overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt \
  desktop/cli/NooirDesktopCli.kt \
  desktop/suwayomi/{MiniJson.kt,SuwayomiClient.kt,SuwayomiToNooir.kt} \
  desktop/ui/{DesktopReadingLayout.kt,ReaderChapterSequence.kt,WebtoonWindow.kt,WebtoonStripView.kt,LocalComicReader.kt,ReaderBookmarkStore.kt,LocalLibraryStore.kt,OfflineSeriesCatalog.kt,OfflineChapterSaver.kt,OfflineSaveQueue.kt} \
  tests/SuwayomiBridgeTest.kt \
  -include-runtime -d "$tmp/suwayomi-bridge-tests.jar"
java --add-modules jdk.httpserver -Djava.awt.headless=true -cp "$tmp/suwayomi-bridge-tests.jar" SuwayomiBridgeTestKt

# D0.2: mock v2.4.2366 installed-source discovery, paginated browse and search.
kotlinc \
  desktop/suwayomi/{MiniJson.kt,SuwayomiClient.kt,SuwayomiBrowseCli.kt} \
  tests/SuwayomiBrowseTest.kt \
  -include-runtime -d "$tmp/suwayomi-browse-tests.jar"
java --add-modules jdk.httpserver -cp "$tmp/suwayomi-browse-tests.jar" SuwayomiBrowseTestKt

# Panel export Phase A: pure Kotlin image geometry and safe reading-order fallback.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelDetectionCore.kt} \
  tests/PanelDetectionCoreTest.kt -include-runtime -d "$tmp/panel-phase-a.jar"
java -jar "$tmp/panel-phase-a.jar"

# Private Conan diagnostic cases reproduced as independently authored geometry:
# terminal footer merging, dark framed corridor, shallow header protection.
# All cover the complete source page and preserve independent Row by Row.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelDetectionCore.kt} \
  tests/ConanPanelRegressionTest.kt -include-runtime -d "$tmp/panel-conan-regressions.jar"
java -jar "$tmp/panel-conan-regressions.jar"

# Panel Phase B: one panel per viewport, RTL wide-panel cuts, overlap and bounds.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelViewportPlanner.kt} \
  tests/PanelViewportPlannerTest.kt -include-runtime -d "$tmp/panel-phase-b.jar"
java -jar "$tmp/panel-phase-b.jar"

# Panel export integration: actual generated files for XTC, XTCH, EPUB2/3 and CBZ.
kotlinc \
  overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,GrayscaleConverter.kt,GrayscalePng.kt,NaturalPageOrder.kt,PageSlicePlanner.kt,PanelDetectionCore.kt,PanelViewportPlanner.kt,PanelAutoFit.kt,PanelWhitespaceCrop.kt,PanelSafeSlicePlanner.kt} \
  overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt \
  desktop/cli/NooirDesktopCli.kt tests/DesktopPanelExportTest.kt \
  -include-runtime -d "$tmp/desktop-panel-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/desktop-panel-tests.jar" DesktopPanelExportTestKt

# Panel Phase C: deterministic per-panel Auto Fit without OCR or cropping.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelViewportPlanner.kt,PanelAutoFit.kt} \
  tests/PanelAutoFitTest.kt -include-runtime -d "$tmp/panel-auto-fit.jar"
java -jar "$tmp/panel-auto-fit.jar"

# Opt-in panel crop: shrink paper borders without losing artwork or balloons.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelWhitespaceCrop.kt} \
  tests/PanelWhitespaceCropTest.kt -include-runtime -d "$tmp/panel-crop.jar"
java -jar "$tmp/panel-crop.jar"

# Shared OCR-free safe-corridor plan: identical Android/Windows boundaries.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelSafeSlicePlanner.kt} \
  tests/PanelSafeSlicePlannerTest.kt -include-runtime -d "$tmp/panel-safe-corridors.jar"
java -jar "$tmp/panel-safe-corridors.jar"

# Windows full-app project: headless GUI model tests against exactly the
# Android 1.0.16 shared panel renderer and conversion settings.
kotlinc \
  overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,GrayscaleConverter.kt,GrayscalePng.kt,NaturalPageOrder.kt,PageSlicePlanner.kt,PanelDetectionCore.kt,PanelViewportPlanner.kt,PanelAutoFit.kt,PanelWhitespaceCrop.kt,PanelSafeSlicePlanner.kt} \
  overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt \
  desktop/cli/NooirDesktopCli.kt desktop/suwayomi/*.kt desktop/ui/*.kt \
  tests/DesktopGuiModelTest.kt \
  -include-runtime -d "$tmp/windows-full-gui-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/windows-full-gui-tests.jar" DesktopGuiModelTestKt

# Windows-only lifecycle: launch arguments, pinned JAR, loopback binding and
# refusal to claim an external server. No Suwayomi jar is executed in CI.
kotlinc desktop/suwayomi/{MiniJson.kt,SuwayomiClient.kt,SuwayomiProcessManager.kt} \
  tests/WindowsSuwayomiProcessManagerTest.kt \
  -include-runtime -d "$tmp/windows-suwayomi-manager-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/windows-suwayomi-manager-tests.jar" WindowsSuwayomiProcessManagerTestKt

# Windows-only optional first-run server installer: verifies the official
# download path via localhost fixtures and never executes a downloaded JAR.
kotlinc desktop/suwayomi/{MiniJson.kt,SuwayomiClient.kt,SuwayomiProcessManager.kt,OfficialSuwayomiCache.kt} \
  tests/VerifiedSuwayomiCacheTest.kt \
  -include-runtime -d "$tmp/windows-verified-server-cache-tests.jar"
java --add-modules jdk.httpserver -Djava.awt.headless=true \
  -cp "$tmp/windows-verified-server-cache-tests.jar" VerifiedSuwayomiCacheTestKt

# Persistent Windows-only queue for explicit offline CBZ copies. No server,
# Android app source or network connection is needed for these tests.
kotlinc desktop/ui/OfflineSaveQueue.kt tests/OfflineSaveQueueTest.kt \
  -include-runtime -d "$tmp/windows-offline-save-queue-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/windows-offline-save-queue-tests.jar" OfflineSaveQueueTestKt

# True Suwayomi remote download requests: synthetic localhost GraphQL only.
# No third-party manga, extension installation or Android build edits.
kotlinc desktop/suwayomi/{MiniJson.kt,SuwayomiClient.kt} \
  desktop/ui/{RemoteChapterQueue.kt,RemoteChapterRunner.kt} \
  tests/RemoteChapterQueueTest.kt \
  -include-runtime -d "$tmp/windows-remote-chapter-queue-tests.jar"
java --add-modules jdk.httpserver -Djava.awt.headless=true \
  -cp "$tmp/windows-remote-chapter-queue-tests.jar" RemoteChapterQueueTestKt

# User-selected completed remote downloads become explicit offline CBZ jobs.
# Neither a source request nor a CBZ copy runs during this handoff.
kotlinc desktop/ui/{RemoteChapterQueue.kt,OfflineSaveQueue.kt,RemoteChapterOfflineHandoff.kt} \
  tests/RemoteChapterOfflineHandoffTest.kt \
  -include-runtime -d "$tmp/windows-remote-offline-handoff-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/windows-remote-offline-handoff-tests.jar" RemoteChapterOfflineHandoffTestKt

# Windows-only bounded Download All queue: atomic capacity checks, dedupe and
# no source requests until a separate UI confirmation.
kotlinc desktop/ui/RemoteChapterQueue.kt tests/RemoteChapterBatchTest.kt \
  -include-runtime -d "$tmp/windows-remote-batch-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/windows-remote-batch-tests.jar" RemoteChapterBatchTestKt

# Windows-only multiple explicit page bookmarks, independently persisted
# from resume position; no Android app or server-side changes.
kotlinc desktop/ui/ReaderBookmarkStore.kt tests/ReaderBookmarkStoreTest.kt \
  -include-runtime -d "$tmp/windows-reader-bookmarks-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/windows-reader-bookmarks-tests.jar" ReaderBookmarkStoreTestKt
