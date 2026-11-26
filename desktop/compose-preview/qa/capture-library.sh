#!/usr/bin/env bash
# Capture the actual Compose window, using only an isolated loopback fixture.
set -euo pipefail
out="$PWD/compose-visual-evidence"
mkdir -p "$out"
fixture_pid=""
app_pid=""
cleanup() {
  if [[ -n "$app_pid" ]]; then kill -- "-$app_pid" 2>/dev/null || true; fi
  if [[ -n "$fixture_pid" ]]; then kill "$fixture_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT
# Retain the visible UI and last stage when a GUI assertion fails.
trap 'rc=$?; echo "GUI capture failed at line $LINENO (exit $rc)" >&2; import -window root "$out/compose-failure.png" 2>/dev/null || true; tail -n 35 "$out/application.log" >&2 || true; tail -n 25 "$out/fixture.log" >&2 || true; exit "$rc"' ERR
/usr/bin/python3 desktop/compose-preview/qa/preview_fixture.py > "$out/fixture.log" 2>&1 &
fixture_pid=$!
for i in {1..20}; do
  if curl -fsS http://127.0.0.1:4567/health >/dev/null 2>&1; then break; fi
  sleep 1
done
curl -fsS http://127.0.0.1:4567/health >/dev/null
setsid env NOOIR_COMPOSE_QA_CAPTURE=1 gradle -p desktop/compose-preview run --no-daemon > "$out/application.log" 2>&1 &
app_pid=$!
window=""
for i in {1..300}; do
  window="$(xdotool search --onlyvisible --name 'MihonNooir.*Compose experiment' 2>/dev/null | head -n 1 || true)"
  if [[ -n "$window" ]] && grep -q LIBRARY_OK "$out/fixture.log" &&
     [[ "$(grep -c '^COVER_OK ' "$out/fixture.log" || true)" -ge 4 ]]; then
    break
  fi
  kill -0 "$app_pid"
  sleep 1
done
test -n "$window"
grep -q LIBRARY_OK "$out/fixture.log"
test "$(grep -c '^COVER_OK ' "$out/fixture.log" || true)" -ge 4
sleep 3
xdotool getwindowname "$window" > "$out/window.txt"
xdotool getwindowgeometry "$window" >> "$out/window.txt"
import -window "$window" "$out/compose-library.png"
test -s "$out/compose-library.png"
identify "$out/compose-library.png" >> "$out/window.txt"
printf 'Actual Compose application window; synthetic original cover art and library data; commit %s\n' "${GITHUB_SHA:-local}" > "$out/provenance.txt"

# The reviewed 1040x720 library screenshot has its first cover at x=105..277,
# y=166..412. Refuse other sizes rather than silently clicking the wrong UI.
geometry="$(xdotool getwindowgeometry --shell "$window")"
grep -qx 'WIDTH=1040' <<< "$geometry"
grep -qx 'HEIGHT=720' <<< "$geometry"
xdotool mousemove --window "$window" 190 280 click 1
for i in {1..30}; do
  if grep -q '^MANGA_OK 1$' "$out/fixture.log"; then break; fi
  sleep 1
done
grep -q '^MANGA_OK 1$' "$out/fixture.log"
sleep 3
import -window "$window" "$out/compose-manga-details.png"
test -s "$out/compose-manga-details.png"
identify "$out/compose-manga-details.png" >> "$out/window.txt"
# This confirms a real library-to-details action and chapter metadata request.
# Verified details capture has first chapter Read action at (970,384) after the selection toolbar.
xdotool mousemove --window "$window" 970 384 click 1
for i in {1..30}; do
  if grep -Fxq 'READER_OK 101' "$out/fixture.log" && grep -Fxq 'PAGE_OK 0' "$out/fixture.log"; then break; fi
  sleep 1
done
grep -Fxq 'READER_OK 101' "$out/fixture.log"
grep -Fxq 'PAGE_OK 0' "$out/fixture.log"
sleep 3
import -window "$window" "$out/compose-reader-page1.png"
test -s "$out/compose-reader-page1.png"
identify "$out/compose-reader-page1.png" >> "$out/window.txt"
# The actual 1040x720 reader capture places Next at (621,665)
# and Previous at (510,665). Verify real page requests, not just clicks.
xdotool mousemove --window "$window" 621 665 click 1
for i in {1..30}; do
  if grep -Fxq 'PAGE_OK 1' "$out/fixture.log"; then break; fi
  sleep 1
done
grep -Fxq 'PAGE_OK 1' "$out/fixture.log"
sleep 3
import -window "$window" "$out/compose-reader-page2.png"
test -s "$out/compose-reader-page2.png"
identify "$out/compose-reader-page2.png" >> "$out/window.txt"
# Exact pixel comparison rejects identical screenshots after the next-page request.
if compare -metric AE "$out/compose-reader-page1.png" "$out/compose-reader-page2.png" null: 2>"$out/page-difference.txt"; then
  echo "Reader page did not visibly change" >&2
  exit 1
fi
xdotool mousemove --window "$window" 510 665 click 1
for i in {1..30}; do
  if [[ "$(grep -Fxc 'PAGE_OK 0' "$out/fixture.log" || true)" -ge 2 ]]; then break; fi
  sleep 1
done
test "$(grep -Fxc 'PAGE_OK 0' "$out/fixture.log" || true)" -ge 2
sleep 3
import -window "$window" "$out/compose-reader-return-page1.png"
test -s "$out/compose-reader-return-page1.png"
# Leave the reader by its actual back control, preserving the chapter list.
xdotool mousemove --window "$window" 145 40 click 1
sleep 3
import -window "$window" "$out/compose-return-details.png"
test -s "$out/compose-return-details.png"

# Capture the chapter selection state before running the actual export engine test.
xdotool mousemove --window "$window" 948 324 click 1
sleep 1
xdotool mousemove --window "$window" 145 384 click 1
sleep 2
import -window "$window" "$out/compose-chapter-selection.png"
test -s "$out/compose-chapter-selection.png"
# The Export action is at the right edge of the selection toolbar.
xdotool mousemove --window "$window" 980 324 click 1
for i in {1..20}; do
  if grep -Fxq 'QA_EXPORT_DIALOG chapters=1' "$out/application.log"; then break; fi
  sleep 1
done
# A PNG alone does not prove that a modal opened.
grep -Fxq 'QA_EXPORT_DIALOG chapters=1' "$out/application.log"
sleep 2
# Include the modal dialog, which is a separate native window.
import -window root "$out/compose-export-dialog.png"
test -s "$out/compose-export-dialog.png"
# Enter a real output path through the visible field and trigger the actual Export button.
# Fixed footer geometry is checked by the readiness/complete diagnostics, not filenames alone.
xdotool mousemove 480 528 click 1
xdotool type --clearmodifiers --delay 1 "$out/compose-gui-export.xtc"
for i in {1..15}; do
  if grep -Fxq 'QA_OUTPUT_READY' "$out/application.log"; then break; fi
  sleep 1
done
grep -Fxq 'QA_OUTPUT_READY' "$out/application.log"
sleep 1
import -window root "$out/compose-export-ready.png"
xdotool mousemove 785 658 click 1
for i in {1..90}; do
  if grep -Fxq 'QA_EXPORT_COMPLETE' "$out/application.log"; then break; fi
  sleep 1
done
grep -Fxq 'QA_EXPORT_COMPLETE' "$out/application.log"
test -s "$out/compose-gui-export.xtc"
import -window root "$out/compose-export-complete.png"
xdotool mousemove 402 658 click 1
for i in {1..20}; do
  if grep -Fq 'QA_CONVERTED_FRAME page=1 count=' "$out/application.log"; then break; fi
  sleep 1
done
grep -Fq 'QA_CONVERTED_FRAME page=1 count=' "$out/application.log"
sleep 2
import -window root "$out/compose-converted-book-preview.png"
test -s "$out/compose-converted-book-preview.png"
# Close the experimental app before the independent headless engine verification.
kill -- "-$app_pid" 2>/dev/null || true
wait "$app_pid" || true
app_pid=""
gradle -p desktop/compose-preview qaExport --no-daemon --stacktrace -PqaOutput="$out" > "$out/export-test.log" 2>&1
grep -Fxq 'PASS: Compose export XTC and XTCH decoded successfully' "$out/export-test.log"
test "$(grep -Fxc 'CBZ_OK 101' "$out/fixture.log" || true)" -eq 3
if grep -Fxq 'REJECTED_OPERATION' "$out/fixture.log"; then
  echo "Unexpected fixture operation" >&2
  exit 1
fi

gradle -p desktop/compose-preview qaBackup --no-daemon --stacktrace > "$out/backup-test.log" 2>&1
grep -Fxq 'PASS: partial extension recovery preserves backup and continues after failure' "$out/backup-test.log"
