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
  overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,GrayscaleConverter.kt,GrayscalePng.kt,NaturalPageOrder.kt,PageSlicePlanner.kt} \
  overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt \
  desktop/cli/NooirDesktopCli.kt tests/DesktopCliTest.kt \
  -include-runtime -d "$tmp/desktop-cli-tests.jar"
java -Djava.awt.headless=true -cp "$tmp/desktop-cli-tests.jar" DesktopCliTestKt
