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
/usr/bin/python3 desktop/compose-preview/qa/preview_fixture.py > "$out/fixture.log" 2>&1 &
fixture_pid=$!
for i in {1..20}; do
  if curl -fsS http://127.0.0.1:4567/health >/dev/null 2>&1; then break; fi
  sleep 1
done
curl -fsS http://127.0.0.1:4567/health >/dev/null
setsid gradle -p desktop/compose-preview run --no-daemon > "$out/application.log" 2>&1 &
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
# Verified details capture has first chapter Read action at (970,360).
xdotool mousemove --window "$window" 970 360 click 1
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
# The next page control will be exercised once the real reader capture is reviewed.
if grep -Fxq 'REJECTED_OPERATION' "$out/fixture.log"; then
  echo "Unexpected fixture operation" >&2
  exit 1
fi
