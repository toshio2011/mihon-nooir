#!/usr/bin/env bash
# Experimental REAL emulator screenshots for the Android Web -> EPUB editor.
# This script does not generate mockups or touch production data; CI uses a
# throwaway emulator with the exact draft APK and its existing debug signer.
set -euo pipefail
mkdir -p android-web-epub-screenshots
pkg=dev.nooir.mihontrial.foss
out=android-web-epub-screenshots
echo "Package: $pkg" > "$out/capture-notes.txt"
echo "Emulator model: $(adb shell getprop ro.product.model | tr -d '\r')" >> "$out/capture-notes.txt"

capture() {
  local name="$1"
  adb exec-out screencap -p > "$out/$name.png"
  test -s "$out/$name.png"
  echo "Captured $name.png" | tee -a "$out/capture-notes.txt"
}

dump_ui() {
  # UIAutomator exports actual displayed Compose semantics / Android widgets.
  adb shell uiautomator dump /sdcard/nooir-window.xml >/dev/null 2>&1 || return 1
  adb exec-out cat /sdcard/nooir-window.xml > "$out/window.xml" || return 1
  test -s "$out/window.xml"
}

lookup() {
  local needle="$1"
  python3 - "$out/window.xml" "$needle" <<'PY'
import sys, re
import xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
    needle = sys.argv[2].lower()
    nodes = []
    for node in root.iter("node"):
        caption = ((node.attrib.get("text") or "") + " " +
                   (node.attrib.get("content-desc") or "")).lower()
        if needle not in caption:
            continue
        m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",
                         node.attrib.get("bounds", ""))
        if m is None:
            continue
        a,b,c,d = map(int,m.groups())
        # Prefer an exact button label over a longer paragraph containing it.
        priority = 0 if caption.strip() == needle else 1
        nodes.append((priority, (a+c)//2, (b+d)//2, a, b))
    if nodes:
        nodes.sort(key=lambda item: item[0])
        print(*nodes[0][1:])
except Exception:
    pass
PY
}

tap_visible() {
  local target="$1" retries="${2:-1}" coord
  local attempt=0
  while [ "$attempt" -lt "$retries" ]; do
    dump_ui || return 1
    coord="$(lookup "$target")"
    if [ -n "$coord" ]; then
      read -r x y left top <<< "$coord"
      adb shell input tap "$x" "$y"
      sleep 2
      echo "Tapped visible text: $target ($x,$y)" >> "$out/capture-notes.txt"
      return 0
    fi
    attempt=$((attempt+1))
    adb shell input swipe 550 1370 550 520 400
    sleep 1
  done
  echo "Text not found: $target" | tee -a "$out/capture-notes.txt"
  return 1
}

# Use the APK already installed by the preceding successful Android smoke job.
adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 9
capture "00-launch-state"
dump_ui || true

# Mihon's first launch has multiple onboarding pages and requires a
# Storage Access Framework folder selection before its main tabs unlock.
# Complete this only in the throwaway emulator, never on a user's device.
for onboarding in {1..8}; do
  dump_ui || true
  if [ -n "$(lookup "Select a folder")" ]; then
    tap_visible "Select a folder" 1 || true
    sleep 2
    tap_visible "Download" 3 || tap_visible "Documents" 2 || true
    tap_visible "Use this folder" 2 || tap_visible "Select" 1 || true
    tap_visible "Allow" 2 || true
    sleep 3
  fi
  if tap_visible "Finish" 1; then break; fi
  if ! tap_visible "Next" 1; then
    tap_visible "Continue" 1 || tap_visible "Skip" 1 || true
  fi
done

if ! tap_visible "More" 2; then
  # Bottom-right tab on Pixel 2 emulators; only a fallback if accessibility
  # text is not exposed (the diagnostic dump documents the actual screen).
  adb shell input tap 950 1770
  sleep 3
fi
if ! tap_visible "Nooir Tools" 5; then
  echo "Could not reach Nooir Tools. See launch-state and window.xml." >> "$out/capture-notes.txt"
  capture "01-navigation-blocked"
  exit 0
fi
if ! tap_visible "Web novels" 4; then
  echo "Could not open Web novels panel. See window.xml." >> "$out/capture-notes.txt"
  capture "02-tools-menu"
  exit 0
fi

capture "01-web-novel-editor"
dump_ui || true
cp "$out/window.xml" "$out/01-editor-accessibility.xml" || true

# The combined-book controls are lower in this same real Compose dialog.
# Toggle the separate Checkbox next to the visible option, not the text node.
coord="$(lookup "Combine pasted webpages into ONE EPUB")"
if [ -n "$coord" ]; then
  read -r x y left top <<< "$coord"
  adb shell input tap "$(( left > 55 ? left - 40 : 45 ))" "$y"
  sleep 2
  capture "02-combined-book"
fi

for i in 1 2 3 4 5; do
  adb shell input swipe 540 1320 540 520 380
  sleep 2
  capture "03-combined-settings-$i"
done
dump_ui || true
cp "$out/window.xml" "$out/03-final-accessibility.xml" || true
echo "All screenshots are raw adb exec-out screencap PNGs." >> "$out/capture-notes.txt"
