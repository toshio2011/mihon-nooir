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

## Known Conan cases to investigate

| Previous output | Question to answer from debug ZIP |
|---|---|
| P113–P114, Row R78 | Was a sparse bottom tier classified as one panel, and was it later split after applying guard padding? |
| P143 / Row R96 | Which original divider proposal failed: seam pixels, insufficient distributed ink, border rule or margin? |
| P311–P315 / Row R227 | Did the panel tree mistake short title/typographic strokes for actual independent illustrated panels? |

Fix the **cause** after inspecting the original detector input and real output geometry; never globally delete low-ink frames because a manga may intentionally contain sparse lettering, end matter or blank artwork.
