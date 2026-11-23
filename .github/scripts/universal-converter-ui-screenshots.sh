#!/usr/bin/env bash
# Experimental real emulator screenshots for Universal Book Converter navigation.
# This script does not generate mockups or touch production data; CI uses a
# throwaway emulator with the exact draft APK and its existing debug signer.
set -euo pipefail
mkdir -p android-universal-converter-screenshots
pkg=dev.nooir.mihontrial.foss
out=android-universal-converter-screenshots
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

# On cold Android emulators, the unrelated Pixel Launcher can raise an
# ANR dialog over our app. Dismiss *Wait*, never force-close MihonNooir.
recover_system_dialog() {
  dump_ui || true
  if [ -n "$(lookup "isn't responding")" ]; then
    echo "Dismissing emulator Pixel Launcher ANR with Wait" >> "$out/capture-notes.txt"
    tap_visible "Wait" 1 || true
    sleep 5
  fi
}

# Query the launcher intent via monkey; bare ACTION_MAIN -p cannot resolve
# a component for Mihon and fails before any real screenshot can be taken.
adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 12
for retry in 1 2 3; do recover_system_dialog; done
capture "00-launch-state"
dump_ui || true

# Mihon's first launch has multiple onboarding pages and requires a
# Storage Access Framework folder selection before its main tabs unlock.
# Complete this only in the throwaway emulator, never on a user's device.
for onboarding in {1..8}; do
  recover_system_dialog
  dump_ui || true
  if [ -n "$(lookup "Select a folder")" ]; then
    tap_visible "Select a folder" 1 || true
    sleep 2
    tap_visible "Download" 3 || tap_visible "Documents" 2 || true
    # Android 11+ disallows granting the Downloads/Documents root.
    # Create and select an app-specific child folder in this throwaway AVD.
    if dump_ui && { [ -n "$(lookup "Can't use this folder")" ] || \
       [ -n "$(lookup "CREATE NEW FOLDER")" ]; }; then
      tap_visible "CREATE NEW FOLDER" 2 || tap_visible "New folder" 1 || true
      adb shell input text Nooir_QA
      tap_visible "OK" 2 || tap_visible "CREATE" 2 || true
      # Some document picker versions remain at the parent after creation.
      if dump_ui && [ -n "$(lookup "Nooir_QA")" ]; then
        tap_visible "Nooir_QA" 1 || true
      fi
    fi
    tap_visible "USE THIS FOLDER" 2 || tap_visible "Select" 1 || true
    tap_visible "ALLOW" 2 || true
    sleep 3
  fi
  if tap_visible "Get started" 1; then break; fi
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
  echo "Could not reach Nooir Tools. See first-launch and accessibility dump." >> "$out/capture-notes.txt"
  capture "01-navigation-blocked"
  exit 1
fi
if ! tap_visible "Universal Book Converter" 5; then
  echo "Universal Converter entry not visible" | tee -a "$out/capture-notes.txt"
  capture "01-universal-route-blocked"
  exit 1
fi
capture "01-universal-source-menu"
dump_ui || true
cp "$out/window.xml" "$out/01-source-menu.xml" || true

# Verify the new decoded-output viewer is genuinely reachable in the app
# before inspecting the original local/PDF routes. It must not start a
# conversion or fake a sample image in the UI capture.
if tap_visible "Preview finished XTC / XTCH book" 3; then
  capture "01a-finished-book-preview-entry"
  dump_ui || true
  cp "$out/window.xml" "$out/01a-finished-book-preview.xml" || true
  if [ -z "$(lookup "Choose finished XTC / XTCH book")" ]; then
    echo "Preview screen opened without its real file picker" >> "$out/capture-notes.txt"
    exit 1
  fi
  # The earlier instrumentation exports a real encoded XTCH test book
  # to Downloads on this throwaway emulator. We try to open it via the
  # ordinary Android picker and screenshot the decoded output, not just the
  # empty viewer. Keep a diagnostic note if the picker is inaccessible.
  if tap_visible "Choose finished XTC / XTCH book" 2; then
    capture "01b-android-file-picker"
    dump_ui || true
    if [ -z "$(lookup "NooirQA.xtch")" ]; then
      tap_visible "Downloads" 2 || tap_visible "Download" 2 || true
    fi
    if tap_visible "NooirQA.xtch" 2; then
      capture "01c-actual-decoded-xtch-page"
      dump_ui || true
      cp "$out/window.xml" "$out/01c-actual-decoded-page.xml" || true
      if [ -n "$(lookup "Page 1 of")" ]; then
        echo "PASS: real finished XTCH opened from Android document picker" >> "$out/capture-notes.txt"
        if tap_visible "Next" 1; then
          capture "01d-actual-decoded-xtch-next-page"
        fi
      else
        echo "Picker accepted XTCH but preview page label was not accessible" >> "$out/capture-notes.txt"
      fi
    else
      echo "Could not pick NooirQA.xtch from Android DocumentsUI" >> "$out/capture-notes.txt"
      adb shell input keyevent KEYCODE_BACK
      sleep 2
    fi
  fi
  tap_visible "Back to converter" 3 || {
    echo "Preview back navigation failed" >> "$out/capture-notes.txt"
    exit 1
  }
else
  echo "Finished-book viewer route unavailable" >> "$out/capture-notes.txt"
  exit 1
fi

if ! tap_visible "Local files" 3; then
  echo "Local-file converter route missing" | tee -a "$out/capture-notes.txt"
  capture "02-local-route-blocked"
  exit 1
fi
capture "02-local-input-types"
dump_ui || true
cp "$out/window.xml" "$out/02-local-input-types.xml" || true

if tap_visible "EPUB" 2; then
  capture "03-epub-source-and-outputs"
  dump_ui || true
  cp "$out/window.xml" "$out/03-epub-source.xml" || true
fi
if tap_visible "PDF" 3; then
  capture "04-pdf-source"
  dump_ui || true
  cp "$out/window.xml" "$out/04-pdf-source.xml" || true
  if tap_visible "Open PDF converter" 3; then
    capture "05-dedicated-pdf-converter"
    dump_ui || true
    cp "$out/window.xml" "$out/05-pdf-converter.xml" || true
  fi
fi
echo "All captured PNGs are real emulator screencaps (not mockups)." >> "$out/capture-notes.txt"
