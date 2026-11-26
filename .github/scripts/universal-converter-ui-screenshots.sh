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
  local needle="$1" region="${2:-all}"
  python3 - "$out/window.xml" "$needle" "$region" <<'PY'
import sys, re
import xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
    needle = sys.argv[2].lower()
    region = sys.argv[3]
    frame = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",
                         root.find(".//node").attrib.get("bounds", ""))
    width = int(frame.group(3)) if frame else 1080
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
        # DocumentsUI exposes the page UNDER its open drawer as accessible
        # nodes. The old generic lookup selected that obscured page's
        # "Downloads" (x=765), *outside* the actual drawer (x<631).
        # Drawer-scoped taps must originate within the visible drawer.
        if region == "drawer" and (a+c)//2 >= width * 0.6:
            continue
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

# Only select entries physically inside the open DocumentsUI drawer.
# A regular tap_visible() may choose a matching label behind the overlay.
tap_drawer_root() {
  local target="$1" coord x y left top
  dump_ui || return 1
  coord="$(lookup "$target" drawer)"
  if [ -z "$coord" ]; then
    echo "Drawer root not found inside visible drawer: $target" >> "$out/capture-notes.txt"
    return 1
  fi
  read -r x y left top <<< "$coord"
  adb shell input tap "$x" "$y"
  sleep 2
  echo "Tapped visible drawer root: $target ($x,$y)" >> "$out/capture-notes.txt"
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

# The fresh emulator created Nooir_QA under its selected Android SAF folder.
# Mirror the exact REAL XTCH codec output into that folder only after
# onboarding (before this, pre-creating it would break the folder dialog).
# DocumentsUI remembers this destination when opening a new document.
if adb shell test -s /sdcard/Download/NooirQA.xtch; then
  for candidate in /sdcard/Download/Nooir_QA /sdcard/Documents/Nooir_QA; do
    if adb shell test -d "$candidate"; then
      adb shell cp /sdcard/Download/NooirQA.xtch "$candidate/NooirQA.xtch"
      adb shell test -s "$candidate/NooirQA.xtch"
      echo "Staged real finished XTCH in picker start folder: $candidate" >> "$out/capture-notes.txt"
      adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
        -d "file://$candidate/NooirQA.xtch" >/dev/null || true
    fi
  done
fi

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
# Capture the actual hub, queue filters and reader browser before returning to
# the existing Universal Converter journey. Never start a network request,
# cancel a job, change a backup, or contact a third-party source.
# Each PNG must have a matching visible UI title in its accessibility dump.
capture_verified() {
  local name="$1" label="$2"
  if ! dump_ui || [ -z "$(lookup "$label")" ]; then
    echo "FAIL: $name missing expected visible label: $label" >> "$out/capture-notes.txt"
    capture "${name}-blocked"
    return 1
  fi
  capture "$name"
  cp "$out/window.xml" "$out/$name.xml"
}
capture_verified "00a-nooir-tools-hub" "Activity Queue" || exit 1
tap_visible "Activity Queue · All current jobs" 3 || exit 1
capture_verified "00b-activity-queue-current" "Nooir Activity Queue" || exit 1
tap_visible "History" 2 || exit 1
capture_verified "00c-activity-queue-history" "Nooir Activity Queue" || exit 1
tap_visible "Close" 2 || exit 1
tap_visible "Browse XTEink files" 3 || exit 1
capture_verified "00d-reader-file-browser" "XTEink Device Files" || exit 1
# No hostname entry, Connect & browse, or browser launch.
tap_visible "Close" 2 || exit 1
capture_verified "00e-return-to-nooir-tools" "Activity Queue" || exit 1
echo "PASS: queue Current/History and reader browser opened; no reader contacted" >> "$out/capture-notes.txt"

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
    # The file should already appear inside the onboarding folder, after
    # copying actual encoded bytes into that folder above. If this provider
    # does not refresh its listing, explicitly choose the *visible drawer*
    # Downloads entry; never click a "Downloads" label behind the overlay.
    if [ -z "$(lookup "NooirQA.xtch")" ]; then
      if ! tap_visible "Show roots" 2; then
        echo "DocumentsUI navigation drawer unavailable" >> "$out/capture-notes.txt"
        capture "01b-picker-navigation-blocked"
        exit 1
      fi
      capture "01b-picker-roots"
      if ! tap_drawer_root "Downloads"; then
        echo "System Downloads root unavailable in visible DocumentsUI drawer" >> "$out/capture-notes.txt"
        capture "01b-downloads-root-missing"
        exit 1
      fi
      capture "01b-system-downloads-root"
      dump_ui || true
      cp "$out/window.xml" "$out/01b-downloads-root.xml" || true
    fi
    if tap_visible "NooirQA.xtch" 2; then
      capture "01c-actual-decoded-xtch-page"
      dump_ui || true
      cp "$out/window.xml" "$out/01c-actual-decoded-page.xml" || true
      if [ -n "$(lookup "Page 1 of 6")" ] &&
         [ -n "$(lookup "Decoded XTCH page 1")" ]; then
        echo "PASS: real finished XTCH page 1 opened from Android document picker" >> "$out/capture-notes.txt"
        if ! tap_visible "Next" 3; then
          echo "FAIL: finished XTCH Next control was not visible or clickable" >> "$out/capture-notes.txt"
          capture "01d-preview-next-blocked"
          exit 1
        fi
        # Decode runs on Dispatchers.IO. The page counter may update before
        # Image(bitmap) has published its accessible decoded-page semantics.
        page_two_ready=false
        for attempt in {1..6}; do
          sleep 1
          dump_ui || true
          if [ -n "$(lookup "Page 2 of 6")" ] &&
             [ -n "$(lookup "Decoded XTCH page 2")" ]; then
            page_two_ready=true
            break
          fi
        done
        if [ "$page_two_ready" != true ]; then
          echo "FAIL: finished XTCH page 2 did not become visible and decoded" >> "$out/capture-notes.txt"
          capture "01d-preview-page-two-missing"
          exit 1
        fi
        capture "01d-actual-decoded-xtch-next-page"
        cp "$out/window.xml" "$out/01d-actual-decoded-next-page.xml" || true
        echo "PASS: real finished XTCH page 2 decoded after Next tap" >> "$out/capture-notes.txt"
      else
        echo "FAIL: picker returned but real XTCH page 1 or its decoded image was unavailable" >> "$out/capture-notes.txt"
        capture "01c-preview-missing-page-label"
        exit 1
      fi
    else
      echo "NooirQA.xtch missing from the actual system Downloads root" >> "$out/capture-notes.txt"
      capture "01c-downloads-book-missing"
      exit 1
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
