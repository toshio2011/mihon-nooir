# Android Panel by Panel diagnostics (opt-in)

**Purpose:** Capture *why* the Android panel renderer makes each cut. Detective Conan test EPUBs have shown repeated blank frame lines and chopped title lettering even after panel regression fixes. The EPUB itself is a resampled output and does not contain the grayscale thumbnail the detector actually evaluated.

This feature does **not** change panel detection, rendering or e-reader formats. It is a local debug aid for validating future changes.

## On a phone

1. Install a compatible **experimental test APK** and back up existing app data first. A test-signed APK may not be installable over a build signed with a different certificate.
2. Open the normal **Export to e-reader** dialog for the manga. Select the same downloaded chapters and settings as your previous test.
3. Open **More conversion settings**, set **Panel by Panel**, then turn on **Save panel diagnostics after this export**. This switch is OFF by default.
4. Tap **Save file** (EPUB/CBZ/etc.) or **Send to XTEink** as normal. Export runs in the existing foreground queue. It also captures diagnostics from the original pages without altering the exported book.
5. After the job **succeeds**, reopen the Export dialog and tap **Save latest panel diagnostics ZIP**. Choose a folder with Android's file picker. The diagnostic ZIP is otherwise private and is **not automatically uploaded**.
6. Only share the ZIP if you have permission to share the manga imagery inside. Send the ZIP **along with the EPUB** when investigating issues, preferably with the same chapter selection and conversion settings. The diagnostic toggle resets to OFF for the next export.

Only the **last successfully captured diagnostic ZIP** is kept in app-private storage; another diagnostic export replaces it. Save your ZIP through the file picker before starting another debug run. If a capture exceeds its limits or encounters an error, the book should still export; the job result includes a diagnostic status.

## Contents of the ZIP

- `README.txt`: brief format, privacy and cap summary.
- `summary.json`: completed archive marker, total source page count, one-based output-frame count including any separate cover, and number of original source samples. Incomplete, cancelled, or oversized captures must never replace a previous valid ZIP.
- `thumbnails/00001.png`: **lossless, exact grayscale image** that the panel detector received at up to 600×900, with no post-export resampling or e-reader quantization. These are essential for reproducing seam decisions.
- `pages/00001.json`: which chapter/page it came from (1-based ordinals; no manga title or source URL), original decoded dimensions, detector image size, original regions in **thumbnail coordinates**, mapped regions in **source coordinates** with protective padding, accepted/rejected trace events, and actual final screen rectangles.
- `originals/00001.source` (only when present): unmodified original compressed source bytes for a limited selection of suspicious pages. The generic extension is deliberate; inspect their file signatures before opening.

Rectangle arrays are `[left, top, right, bottom]`, with zero-based top/left and **exclusive right/bottom**. The `outputFrameStartOneBased` value counts from 1 and includes a separately exported cover when present. `outputFrameCount` includes optional full-page overview frames. Output frame numbers can differ from the previous EPUB; align by chapter and source ordinal rather than assume the old P113 number.

The trace is **observational**. It records accepted horizontal seams, sparse-tier decisions, rejected recursive partitions, column retry counts (unsafe seam / inadequate ink on either side), fallbacks and final output geometry. Some low-level short-circuit rejection conditions may be summarized, not logged at every pixel.

## Bounded capture and privacy

- No network activity, analytics, OCR, AI or extra external storage permission is needed.
- Up to 3,000 source pages and approximately 120 MiB of ZIP data. Large/very long runs may produce an unavailable or incomplete capture status rather than silently substitute different book pages.
- Up to 24 suspicious pages may include *original source bytes* (max 8 MiB each, 80 MiB combined). Every processed page still has an exact detector thumbnail even when its original is omitted.
- No title, manga filename, chapter name, source ID, URL, account identifier or device IP is serialized. The images themselves can still be copyrighted or visually identifying, so **review before sharing**.
- Files stay within app-private storage until you select **Save latest panel diagnostics ZIP**. Turning the toggle off affects future captures; it does not automatically delete an already saved ZIP.
- The diagnostic switch is one-job-only: starting an export resets its saved preference to OFF, while preserving the flag for the job already queued. Enabling debug adds extra work and disk I/O.

## Confirmed Conan examples (28 October 2026)

A private user-provided same-export Panel EPUB, Row EPUB and diagnostic ZIP were inspected. The **diagnostic capture is valid** (67 source pages, 330 output frames including cover). Read the [exact source/trace mappings and targeted regression brief](PANEL_CONAN_DIAGNOSTIC_FINDINGS_2026-10-28.md) before changing the detector.

| Frame numbers in **that** Panel export | Confirmed cause from trace |
|---|---|
| P113–P114 (source 21) | A 42-pixel-high **terminal border/footer tier** was rejected recursively but wrongly accepted by column retry as two independent panels |
| P143 (source 26) | Two genuine upper illustrated panels remained **grouped** after a recursive minimum-area rejection and 45 unsafe-seam retry rejections |
| P311–P315 (source 66) | A 58-pixel-high **typographic title band** was accepted as five panel candidates; exact detector thumbnail is available, original source sample was omitted by the ZIP's 24-image cap |

These output numbers are **not stable identifiers** after re-export. Match by source ordinal and overview. The corrections are still **proposals, not implemented**. Fix the cause, preserve all content, and never globally delete low-ink frames: sparse lettering, end matter and intentionally blank artwork may be legitimate.


## Validate a saved ZIP offline

Do **not** upload or commit the ZIP to a public GitHub issue. It may contain original copyrighted pages. On a trusted computer with Python 3, run:

```bash
python3 tools/panel_diagnostic_inspect.py "MihonNooir-panel-diagnostics.zip" --json
python3 tools/panel_diagnostic_inspect.py "MihonNooir-panel-diagnostics.zip" --frame 143
```

The inspector checks archive integrity and entry CRC, fully decodes the PNG grayscale scanlines, verifies frame-number continuity (including the extra cover/overview frames), confirms each output rectangle is within its source coordinates, and checks original-image caps and sensitive JSON field names. It reports **source ordinals** for frames whose detector rejected cuts or whose export produced four or more views; this is evidence to investigate, **not** a claim that those frames are wrong.

The `--frame` argument is the **one-based output frame from that same export**. Do not apply old Detective Conan frame numbers to a new export without first checking alignment. Match the exported EPUB's spine to `outputFrameStartOneBased` and `outputFrameCount` in the ZIP. Exported cover images and Full Page Before Splits previews count as frames. If cover handling or EPUB reader numbering differs, verify the actual EPUB spine before attributing a source page.

The inspector cannot reconstruct the unavailable full-resolution source image when `originalImage` is absent, and cannot infer whether a split is narratively good from frame counts. For Conan P113–P114, P143, and P311–P315, inspect the original detector grayscale input, rejected seam events, sampled source bytes (if present), and same-run EPUB together before altering the detector.

**Archive safety:** Capture is limited to 3,000 pages, 120 MiB compressed, 24 optional originals, 8 MiB per original and 80 MiB originals total. A close, validation, size or atomic-replacement failure must keep the prior successful private ZIP intact. The ZIP is available only after the book output succeeds; the choice to save/share remains with the user.
