#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,GrayscaleConverter.kt} tests/CoreSmokeTest.kt -include-runtime -d "$tmp/smoke.jar"
java -jar "$tmp/smoke.jar"
kotlinc overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,GrayscaleConverter.kt} tests/CoreChapterTest.kt -include-runtime -d "$tmp/chapters.jar"
java -jar "$tmp/chapters.jar"

kotlinc overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,BookArchiveWriter.kt} tests/FormatsTest.kt -include-runtime -d "$tmp/formats.jar"
java -jar "$tmp/formats.jar"
kotlinc overlay/app/src/main/java/dev/nooir/bridge/PageSlicePlanner.kt tests/PageSlicePlannerTest.kt -include-runtime -d "$tmp/slicing.jar"
java -jar "$tmp/slicing.jar"

kotlinc overlay/app/src/main/java/dev/nooir/bridge/ReflowEpubWriter.kt tests/ReflowEpubTest.kt -include-runtime -d "$tmp/reflow.jar"
java -jar "$tmp/reflow.jar"
