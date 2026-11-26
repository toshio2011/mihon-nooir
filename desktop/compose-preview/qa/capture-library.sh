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
