# Universal Book Converter — preservation and quality checklist

> 23 November 2026. Draft engineering QA only; **not public v1.0.19**.
> PR #111 is independent of the advanced WebToEpub PR #110 and the
> Windows, updater, and Folio Nooir tracks. No merge, release or flash is authorized.

## Confirmed baseline — previous tested head

- Android Actions run [45091994589](https://github.com/toshio2011/mihon-nooir/actions/runs/45091994589) **passed** host tests, FOSS APK build and emulator smoke on integration head `a4a97e61`.
- The five original panel/CBZ diagnostic tests and five EPUB→XTC/XTCH Android tests all passed. Tests decode **real final book bytes**, not a mock extension change.
- The emulator pulled **9 decoded PNGs** from the completed book files. The original Actions artifact is `mihon-nooir-panel-zip-device-smoke` (artifact ID `14782326990`).
- Reviewed screenshot dimensions: X4 **480×800** (XTC/XTCH), X3 **528×792** (XTCH). The synthetic prose pages display legible headings and text, RTL Arabic is positioned on the leading/right side, and the simple embedded square illustration is present. The output is **not** a CSS fidelity, typography beauty, or real illustrated-book acceptance test.
- WebToEpub-specific improvements stay in [PR #110](https://github.com/toshio2011/mihon-nooir/pull/110). Its exact-commit Android Actions run [45047980145](https://github.com/toshio2011/mihon-nooir/actions/runs/45047980145) passed synthetic web article → EPUB2 and EPUB3 file export checks and existing panel regression tests.

## User flow (new draft, pending exact-commit UI acceptance)

`More → Nooir Tools → Universal Book Converter` offers the existing
source-specific engines rather than replacing them:

| User source | Route/engine preserved | Currently offered output |
| --- | --- | --- |
| Local EPUB | AnyFileTransferDialog → NooirEpubToXtc | Experimental XTC/XTCH |
| Image / CBZ / ZIP | Existing manga/image export | XTC/XTCH, EPUB2/3, CBZ as reader supports |
| PDF | Original dedicated NooirPdfToEpubDialog | Its existing PDF converter choices |
| Public web page | Original NooirWebToReaderDialog | Its currently implemented web options |
| Web novel chapters | Original NooirNovelBatchDialog | Its currently implemented batch options |
| Any local file | Original-file send/share | No conversion until selected source is supported |

The local-file picker selects an **input type first**, asks Android to filter by
MIME (with a Browse All fallback), checks the actual filename/MIME combination,
then shows **compatible outputs and relevant settings**. Archive/image bytes
receive worker-side inspection. The old background queue, progress/cancellation,
destination safeguards, original-file send and save-to-phone remain in place.

On small screens the source chips use two rows. Unknown/misreported vendor EPUB
and CBZ MIME types are explicitly handled; generic ZIP alone is not assumed to
be a valid comic if the document lacks an archive extension.

Structural preservation tests are in `tests/test_universal_converter_routes.py`.
These **do not** replace a real interactive UI walkthrough or a fresh APK build.

## Finished output preview UI checks — current draft

- A real read-only `XtcBookPreview` viewer is reachable from the Universal
  Converter and from the local-file conversion dialog. It supports choosing
  finished XTC/XTCH output or opening the last successfully saved XTC/XTCH
  via a one-tap WorkManager completion link.
- The UI screenshot script now visits the new finished-file viewer, verifies
  its document-picker button is accessible, captures an actual emulator screen,
  returns to the converter, then checks the old local/PDF routes.
- The screenshot script is a **best-effort diagnostic** in CI: any navigation
  failure is captured in notes and must be reviewed, rather than equated
  with the successful codec tests. The Android pixel-parity instrumentation
  is a separate automated check. Opening a real user file and navigating
  its decoded page images still needs interactive acceptance.

## Expanded real-file preview acceptance (Android emulator)

- Add Android instrumentation that **opens** a finished XTCH via a file URI,
  copies it to isolated owned cache via `NooirFinishedBookPreview.open`,
  verifies format/page count, decoded page size, byte-for-byte immutability
  of the source, and rejection of a corrupt file before display.
- Save a synthetic **real encoded** `NooirQA.xtch` from the existing
  independent-decoder test, push that exact output to the emulator's
  Downloads directory, and let the ordinary Android DocumentsUI picker
  choose it. UI script records actual first/next-page screencaps when
  accessible, or an explicit failure note. No owner book is used.
- This extends the strict EPUB instrumentation gate to **14 tests**;
  the DocumentsUI navigation remains a best-effort screenshot diagnostic
  until proven reliable across emulator images.

## Remaining acceptance gates before owner approval

1. **Exact-commit Android CI** after the latest input-picker refinements and
   preservation test; ensure the route checks and all existing exports pass.
2. **UI walkthrough and screenshots:** first-launch entry, source switch reset,
   Android SAF picker filters and Browse All fallback, multi-file rename,
   compatible output chips, source-specific settings, back navigation and
   original-file send. Include narrow phone layouts and permission denials.
3. **Real EPUB fidelity set:** text-heavy multilingual EPUB2/3; cover + inline
   picture order; CSS-heavy and SVG/fixed layout rejection or faithful rendering;
   corrupt/oversized/DRM EPUB error handling; long-book memory and cancel.
4. **Actual Web → EPUB sites:** legal public HTML article and paginated
   chapters, readability versus original, TOC completeness, covers, preview,
   combined-book order; retain independently tested WebToEpub PR #110.
5. **PDF and manga regression set:** existing PDF output selections, CBZ
   panel/row/Auto Fit, oversized panels, RTL, renderer controls, large batch,
   device filename conflict and interrupted transfer recovery.
6. **Physical X3/X4 checks** only with an approved build and deliberate
   owner-led transfer; current emulator success is not evidence of hardware
   reader performance or firmware changes.

**Policy:** preserve working features, integrate access, enhance quality.
Never claim unimplemented input/output pairs, silently copy/rename during
conversion, fold the WebToEpub draft into this branch without explicit review,
or publish a new release without approval.


## CI failure and targeted repair — 23 November 2026

- Android FOSS release-equivalent APK successfully compiled on head
  `a502ea3`, but the emulator instrumentation failed **2 of 14** cases.
  Both new preview tests raised `NoClassDefFoundError` for
  `NooirFinishedBookPreview`. The remaining 12 EPUB tests succeeded.
- Root cause: R8 removes the preview helper class from the minified app
  despite an independently compiled test APK needing its un-obfuscated
  Java/Kotlin class. Existing XTC decoder/EPUB keep rules worked.
- The integration overlay now adds narrow R8 rules for the preview singleton
  and its nested `Loaded` data class. A host-level structural test ensures
  these keep rules cannot accidentally be removed during refactors.
- This does **not** turn off minification, change release signing,
  change Android app identity or alter Folio Nooir firmware.
- Exact-head smoke is required before acceptance; treat this as an
  implementation fix rather than a completed validation.


## 23 Nov 2026: confirmed 14-test pass; DocumentsUI navigation follow-up

- Exact Android CI head `3b0da77` **passed** app compile and emulator
  instrumentation ([Actions 45172197071](https://github.com/toshio2011/mihon-nooir/actions/runs/45172197071)):
  **14/14** EPUB/XTC/XTCH and post-export preview tests.
- Its independent best-effort UI capture **did not complete**. Captured
  real DocumentsUI screen showed the picker had reopened the onboarding folder
  `Download/Nooir_QA`, not the parent `Download` holding `NooirQA.xtch`.
  The previous script accidentally clicked an unrelated nested `downloads`
  folder and never returned to the app.
- The screenshot script now explicitly opens **Show roots → Downloads** in
  DocumentsUI. It records a drawer screenshot and demands that the actual
  exported XTCH be selected and decoded, with page-number semantics, before
  treating the optional UI walkthrough as complete. Missing files/screens
  produce truthful failure notes, not success claims.
- CI still uploads UI diagnostics and does not let a flaky onboarding picker
  mark the **codec regression** failed. Review the capture exit code and
  first/next-page screencaps separately for UI acceptance.


## 23 November: real DocumentsUI drawer mis-tap correction

- Exact-head Android CI on `b54f2d3` [passed](https://github.com/toshio2011/mihon-nooir/actions/runs/45178888169):
  APK, 15 EPUB XTC/XTCH tests and existing diagnostics. Separate real UI
  capture remains incomplete.
- The actual screenshot `01b-picker-roots.png` shows the **Downloads** drawer
  entry inside x=0–631, but recorded navigation tapped `Downloads` at x=765,
  a matching node behind the drawer. The next screenshot remained in
  `Nooir_QA` with only `autobackup`, `downloads`, `local`.
- Fix the harness: drawer-root lookup now refuses nodes outside the visible
  left drawer; staged synthetic **real encoded** `NooirQA.xtch` is copied into
  the onboarding destination *after folder creation* so the ordinary picker
  should find it without navigating. The system Downloads drawer is a
  constrained fallback. No mock image, app permissions or conversion engines
  changed.
- New CI must demonstrate selected finished-file first/next page screenshots;
  the script's optional screenshot capture remains a separate UI acceptance
  check, not proof by merely passing binary unit tests.


## 23–24 November: real XTCH first-page screenshot verified; page-two gate

- [Android exact-head 45205233377](https://github.com/toshio2011/mihon-nooir/actions/runs/45205233377) passed Android APK, the 15 EPUB XTC/XTCH tests and panel smoke. The screenshot artifact proves the app selected the *actual* `NooirQA.xtch` through DocumentsUI, decoded its page, and displayed `XTCH · 6 pages` / `Page 1 of 6` with an accessible `Decoded XTCH page 1` image. The emulator also reached local EPUB, PDF and dedicated PDF converter routes.
- The **Next** screenshot was absent even though the script exit was zero: the page-navigation row sat below the 305dp preview image, outside the initially visible 550dp scrollable dialog. `tap_visible "Next" 1` could not find it and quietly continued.
- Move First/Prev/Next/Last and the page counter *above* the preview image, so they are visible on the emulator's smaller screen and on handheld phones without extra scrolling. Keep the real decoded bitmap, existing page controls, back button, and temporary-file safety unchanged.
- Strengthen the optional real UI walkthrough: verify `Page 1 of 6` and decoded-page semantics, require a successful **Next** tap, wait for asynchronous decoded `Page 2 of 6`, capture an actual second-page screenshot, and report a failure within the UI walk if any condition is missing. A green codec suite is not a substitute for the second screenshot.
- This change is in the experimental Android preview and test harness only; no Windows, Folio firmware, release version or public Android APK is changed.


## 24 November: explicit hidden XHTML fidelity regression

- Preview UI acceptance now **passed at exact head `6fad0d8`**:
  [Actions 45215512098](https://github.com/toshio2011/mihon-nooir/actions/runs/45215512098)
  produced actual first- and next-page XTCH screenshots; the event log records
  opening `NooirQA.xtch`, seeing page 1, tapping Next, decoding page 2 of 6,
  returning to the converter, and visiting the preserved EPUB/PDF routes.
  The full Android build and 15 EPUB tests were green.
- EPUB XHTML extraction must not render `hidden`, inline
  `style="display:none"` or `visibility:hidden/collapse` fallback elements:
  they may contain duplicate prose or non-existent fallback images that would
  otherwise break an entire conversion. Conversely, `aria-hidden="true"`
  does NOT mean invisible and should preserve visually displayed content.
- Added a 16th Android instrumentation test: real XTCH page count and decoded
  pixels must be **identical** between the normal EPUB fixture and a fixture
  with hidden duplicate text and an absent hidden illustration. Also checks
  hidden element skipping and visible aria-hidden text in reading order.
  The smoke gate now requires exactly 16 tests and this named regression.
- This covers explicit XHTML visibility only: external stylesheet cascades,
  complex CSS and JavaScript remain out of scope. No public Android release,
  Windows source, or Folio firmware changes.
