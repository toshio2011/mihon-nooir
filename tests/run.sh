#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/NooirExportPreflight.kt tests/PreflightTest.kt -include-runtime -d "$tmp/preflight.jar"
java -jar "$tmp/preflight.jar"

# Send & Convert must never claim to convert EPUB, PDF or other documents
# through the manga-image path (and must not silently copy them as XTC).
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/NooirLocalConversionPolicy.kt \
  tests/NooirLocalConversionPolicyTest.kt -include-runtime -d "$tmp/local-file-support.jar"
java -jar "$tmp/local-file-support.jar"

# Offline EPUB package / OPF spine parser before Android bitmap rendering.
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/NooirEpubBook.kt \
  tests/NooirEpubBookTest.kt -include-runtime -d "$tmp/nooir-epub-import.jar"
java -jar "$tmp/nooir-epub-import.jar"


kotlinc overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,GrayscaleConverter.kt} tests/CoreSmokeTest.kt -include-runtime -d "$tmp/smoke.jar"
java -jar "$tmp/smoke.jar"

# Decode the finished XTC/XTCH bytes rather than trusting source-page
# quantization previews. Test packed 1-bit and 2-bit exact round trips.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,XtcBookPreview.kt} \
  tests/XtcBookPreviewTest.kt -include-runtime -d "$tmp/xtc-preview.jar"
java -jar "$tmp/xtc-preview.jar"

# Rendering choices share a bounded-memory core and must keep legacy output.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,GrayscaleConverter.kt} \
  tests/GrayscaleModesTest.kt -include-runtime -d "$tmp/render-modes.jar"
java -jar "$tmp/render-modes.jar"

# Opt-in context peek duplicates neighboring art only around known panel borders.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelContextPeek.kt} \
  tests/PanelContextPeekTest.kt -include-runtime -d "$tmp/panel-peek.jar"
java -jar "$tmp/panel-peek.jar"

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

kotlinc overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,ReflowEpubWriter.kt,NooirEpubCssRules.kt} tests/ReadableFixturesTest.kt -include-runtime -d "$tmp/readable-fixtures.jar"
java -Djava.awt.headless=true -jar "$tmp/readable-fixtures.jar"

kotlinc overlay/app/src/main/java/dev/nooir/bridge/PageSlicePlanner.kt tests/PageSlicePlannerTest.kt -include-runtime -d "$tmp/slicing.jar"
java -jar "$tmp/slicing.jar"

kotlinc overlay/app/src/main/java/dev/nooir/bridge/{ReflowEpubWriter.kt,NooirEpubCssRules.kt} tests/ReflowEpubTest.kt -include-runtime -d "$tmp/reflow.jar"
java -jar "$tmp/reflow.jar"

# Single-article CreateDocument failures/cancellations must clean up only
# newly created phone files; existing document URIs must remain untouched.
python3 tests/test_web_article_save_cleanup.py

# Combined web-book cover geometry: pure Kotlin, no Android graphics dependencies.
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/NooirCoverCollageLayout.kt \
  tests/NooirCoverCollageLayoutTest.kt -include-runtime -d "$tmp/collage-layout.jar"
java -jar "$tmp/collage-layout.jar"

# Public novel chapter checklist: selected order must match the combined EPUB manifest.
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/NooirWebChapterPlan.kt \
  tests/NooirWebChapterPlanTest.kt -include-runtime -d "$tmp/web-chapter-plan.jar"
java -jar "$tmp/web-chapter-plan.jar"

# Saved-story update detection preserves excluded chapters and user-defined order.
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/{NooirWebChapterIdentity.kt,NooirWebChapterPlan.kt,NooirWebChapterUpdates.kt} \
  tests/NooirWebChapterUpdatesTest.kt -include-runtime -d "$tmp/web-chapter-updates.jar"
java -jar "$tmp/web-chapter-updates.jar"

# Chapter URL identity must preserve meaningful chapter queries while
# ignoring nonessential marketing links in the saved-project inventory.
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/NooirWebChapterIdentity.kt \
  tests/NooirWebChapterIdentityTest.kt -include-runtime -d "$tmp/web-chapter-identity.jar"
java -jar "$tmp/web-chapter-identity.jar"

# Bounded public table-of-contents pagination. Never follow next-chapter
# links as TOC pages, accept a different host, or silently exceed the limit.
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/NooirWebTocPagination.kt \
  tests/NooirWebTocPaginationTest.kt -include-runtime -d "$tmp/web-toc-pagination.jar"
java -jar "$tmp/web-toc-pagination.jar"

# Public chapter selector policy: pure Kotlin, independent of Android/Jsoup.
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/NooirWebExtractRules.kt \
  tests/NooirWebExtractRulesTest.kt -include-runtime -d "$tmp/web-extract-rules.jar"
java -jar "$tmp/web-extract-rules.jar"

# Image optimization presets are independent of Android bitmap implementations.
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/NooirWebImageProfile.kt \
  tests/NooirWebImageProfileTest.kt -include-runtime -d "$tmp/web-image-profile.jar"
java -jar "$tmp/web-image-profile.jar"

# Public HTML image selection: lazy-load/full-resolution ordering and fallbacks.
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/NooirWebImageCandidates.kt \
  tests/NooirWebImageCandidatesTest.kt -include-runtime -d "$tmp/web-image-candidates.jar"
java -jar "$tmp/web-image-candidates.jar"

kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/NooirWebImageSignature.kt \
  tests/NooirWebImageSignatureTest.kt -include-runtime -d "$tmp/web-image-signature.jar"
java -jar "$tmp/web-image-signature.jar"

# Public HTML reader candidate scoring: reject link-heavy navigation blocks.
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/NooirWebReadability.kt \
  tests/NooirWebReadabilityTest.kt -include-runtime -d "$tmp/web-readability.jar"
java -jar "$tmp/web-readability.jar"

# Retry only transient public HTTP failures, never access-denied pages.
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/NooirWebFetchPolicy.kt \
  tests/NooirWebFetchPolicyTest.kt -include-runtime -d "$tmp/web-fetch-policy.jar"
java -jar "$tmp/web-fetch-policy.jar"

# Desktop uses the same source files as Android; never maintain duplicate codecs.
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt tests/ConversionOptionsParityTest.kt -include-runtime -d "$tmp/options-parity.jar"
java -jar "$tmp/options-parity.jar"

kotlinc \
  overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,GrayscaleConverter.kt,GrayscalePng.kt,NaturalPageOrder.kt,PageSlicePlanner.kt,PanelContextPeek.kt,PanelDetectionCore.kt,PanelViewportPlanner.kt,PanelAutoFit.kt,PanelWhitespaceCrop.kt,PanelSafeSlicePlanner.kt,PanelReadableSliceRefiner.kt} \
  overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt \
  desktop/cli/NooirDesktopCli.kt tests/DesktopCliTest.kt \
  -include-runtime -d "$tmp/desktop-cli-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/desktop-cli-tests.jar" DesktopCliTestKt

# D0: exercise a mock Suwayomi v2.4.2366 GraphQL/CBZ server and export its
# downloaded chapter images with the exact Android-compatible writers.
kotlinc \
  overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,GrayscaleConverter.kt,GrayscalePng.kt,NaturalPageOrder.kt,PageSlicePlanner.kt,PanelContextPeek.kt,PanelDetectionCore.kt,PanelViewportPlanner.kt,PanelAutoFit.kt,PanelWhitespaceCrop.kt,PanelSafeSlicePlanner.kt,PanelReadableSliceRefiner.kt} \
  overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt \
  desktop/cli/NooirDesktopCli.kt \
  desktop/suwayomi/{MiniJson.kt,SuwayomiClient.kt,SuwayomiToNooir.kt} \
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

# Page-level safeguard: printed introduction/contents pages must not be
# fragmented into tiny comic panels; ordinary illustrated rows still split.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelDetectionCore.kt} \
  tests/TextDominantPageRegressionTest.kt -include-runtime -d "$tmp/panel-text-pages.jar"
java -jar "$tmp/panel-text-pages.jar"

# Experimental black-background gutter recovery: only meaningful thick
# frames between independently illustrated regions; Row Only unchanged.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelDetectionCore.kt} \
  tests/BlackFramedGutterRegressionTest.kt -include-runtime -d "$tmp/panel-black-framed-gutter.jar"
java -jar "$tmp/panel-black-framed-gutter.jar"

# Source-75-shaped shallow speech strip regression (synthetic pixels only):
# merge aligned columns, reject a crossing balloon, preserve all coordinates.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelDetectionCore.kt} \
  tests/SpeechTierCoalescingRegressionTest.kt -include-runtime -d "$tmp/panel-speech-tier.jar"
java -jar "$tmp/panel-speech-tier.jar"

# Conservative paired slanted rail fallback: middle story row can have
# asymmetric paper shoulders; no unsafe crossing/slash or lost pixels.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelDetectionCore.kt} \
  tests/PairedSlantedFrameRegressionTest.kt -include-runtime -d "$tmp/panel-paired-slants.jar"
java -jar "$tmp/panel-paired-slants.jar"

# Experimental last-resort overlapping three-row reading views. This
# explicitly tests guaranteed source coverage, conservative inner columns,
# order and non-fragmentation of prose/splash/already split pages.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelDetectionCore.kt} \
  tests/OverlappingRowRescueRegressionTest.kt -include-runtime -d "$tmp/panel-overlapping-rows.jar"
java -jar "$tmp/panel-overlapping-rows.jar"

# Panels+-inspired Kotlin frame-confidence recovery is still an
# EXPERIMENT, validated independently on synthetic framed/negative pages.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelDetectionCore.kt} \
  tests/FrameEvidenceRegressionTest.kt -include-runtime -d "$tmp/panel-frame-evidence.jar"
java -jar "$tmp/panel-frame-evidence.jar"

# Experimental fitted-size post-processor: 5mm hard merge / 10mm soft
# sparse-only merge, and safe 12%-overlap halves for source-wide >2/3 rows.
# Never drop an image pixel or change normal row-only/whole-page cases.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelViewportPlanner.kt,PanelSafeSlicePlanner.kt,PanelAutoFit.kt,PanelReadableSliceRefiner.kt} \
  tests/PanelReadableSliceRefinerTest.kt -include-runtime -d "$tmp/panel-readable-slices.jar"
java -jar "$tmp/panel-readable-slices.jar"

# Detect a falsely isolated speech strip from an otherwise single
# narrow manga panel (private source72 geometry, independently authored).
# Its 35px source fragment can appear large after AUTO_FIT: fitted-size
# checks alone cannot solve this case. Preserve every source pixel.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelDetectionCore.kt} \
  tests/BubbleFragmentSameColumnRegressionTest.kt -include-runtime -d "$tmp/panel-bubble-fragment.jar"
java -jar "$tmp/panel-bubble-fragment.jar"

# QA9: full-width near-empty scan header/footer must not become its
# own zoom viewport. Preserve pixels by joining only physically touching,
# full-coverage illustrated neighbors; leave real mini panels intact.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelDetectionCore.kt} \
  tests/SparsePageEdgeRegressionTest.kt -include-runtime -d "$tmp/panel-sparse-page-edge.jar"
java -jar "$tmp/panel-sparse-page-edge.jar"

# Panel Phase B: one panel per viewport, RTL wide-panel cuts, overlap and bounds.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelViewportPlanner.kt} \
  tests/PanelViewportPlannerTest.kt -include-runtime -d "$tmp/panel-phase-b.jar"
java -jar "$tmp/panel-phase-b.jar"

# Panel export integration: actual generated files for XTC, XTCH, EPUB2/3 and CBZ.
kotlinc \
  overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,GrayscaleConverter.kt,GrayscalePng.kt,NaturalPageOrder.kt,PageSlicePlanner.kt,PanelContextPeek.kt,PanelDetectionCore.kt,PanelViewportPlanner.kt,PanelAutoFit.kt,PanelWhitespaceCrop.kt,PanelSafeSlicePlanner.kt,PanelReadableSliceRefiner.kt} \
  overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt \
  desktop/cli/NooirDesktopCli.kt tests/DesktopPanelExportTest.kt \
  -include-runtime -d "$tmp/desktop-panel-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/desktop-panel-tests.jar" DesktopPanelExportTestKt

# Panel Phase C: preserve safe splits, then independently refit every piece.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelViewportPlanner.kt,PanelSafeSlicePlanner.kt,PanelAutoFit.kt} \
  tests/PanelAutoFitTest.kt -include-runtime -d "$tmp/panel-auto-fit.jar"
java -jar "$tmp/panel-auto-fit.jar"

# Opt-in panel crop: shrink paper borders without losing artwork or balloons.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelDetectionCore.kt,PanelWhitespaceCrop.kt} \
  tests/PanelWhitespaceCropTest.kt -include-runtime -d "$tmp/panel-crop.jar"
java -jar "$tmp/panel-crop.jar"

# Shared OCR-free safe-corridor plan: identical Android/Windows boundaries.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{PageSlicePlanner.kt,PanelSafeSlicePlanner.kt} \
  tests/PanelSafeSlicePlannerTest.kt -include-runtime -d "$tmp/panel-safe-corridors.jar"
java -jar "$tmp/panel-safe-corridors.jar"

# Long PDF-to-EPUB container regression. Synthetic original sections only:
# proves that both EPUB versions can store well over 600 source pages.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{ReflowEpubWriter.kt,NooirEpubCssRules.kt} \
  tests/PdfLargeReflowTest.kt -include-runtime -d "$tmp/pdf-long-epub.jar"
java -Djava.awt.headless=true -jar "$tmp/pdf-long-epub.jar"

# Many PDFs are separate, serial jobs with collision-safe names; this bound
# is per picker selection and never a page-count limit.
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/PdfBatchPlan.kt \
  tests/PdfBatchPlanTest.kt -include-runtime -d "$tmp/pdf-batch-plan.jar"
java -jar "$tmp/pdf-batch-plan.jar"

# Verify bounded, cancellable and length-checked local SAF copy behavior.
# No e-reader transfer-size limit or Android dependency in this path.
kotlinc overlay/app/src/main/java/dev/nooir/mangabridge/PdfSaveCopy.kt \
  tests/PdfSaveCopyTest.kt -include-runtime -d "$tmp/pdf-save-copy.jar"
java -jar "$tmp/pdf-save-copy.jar"

# Shared phone-vs-XTEINK size policy: only the upload is bounded.
kotlinc overlay/app/src/main/java/dev/nooir/bridge/XteinkTransferPolicy.kt \
  tests/XteinkTransferPolicyTest.kt -include-runtime -d "$tmp/xteink-transfer-advisory.jar"
java -jar "$tmp/xteink-transfer-advisory.jar"

# Regression: the in-app updater must select a compatible Android APK
# regardless of GitHub asset ordering; do not accidentally choose x86_64 on ARM.
kotlinc \
  overlay/data/src/main/java/tachiyomi/data/release/NooirReleaseApkSelector.kt \
  tests/UpdaterAssetSelectionTest.kt \
  -include-runtime -d "$tmp/updater-assets.jar"
java -jar "$tmp/updater-assets.jar"
