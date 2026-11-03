# MihonNooir v1.0.17 — parallel-installable Android QA APK

This is a **test-only delivery path**, separate from draft panel PR #57. It builds the *same* Android overlay/panel converter as PR #57 from the pinned Mihon upstream, but changes temporary compiled-app identity **after** the normal overlay step.

- Official release (untouched): `MihonNooir`, package `dev.nooir.mihontrial.foss`, signed v1.0.16.
- Experimental QA (side-by-side): `MihonNooir QA`, package `dev.nooir.mihonqa.foss`, versionName `1.0.17-panel-qa`, debug-signed.
- The QA app has **independent Android app data**. It does not import/copy official app's database, downloads, login sessions or preferences, nor does it replace/uninstall the official application.
- Auto-updater is disabled **only in the temporary QA checkout**, so testing won't offer replacement by an official artifact. No release/signing files, default Android identity, panel algorithms, Windows code or Folio firmware are changed.

## Implementation

`tools/prepare_parallel_qa.py upstream` operates only on a disposable **already integrated** upstream checkout. It checks every expected Gradle/i18n anchor before writing, keeps the existing FOSS suffix, and fails closed if upstream changes. Unit tests in `tests/test_parallel_qa.py` exercise package separation, QA labeling, updater deactivation, duplicate application and rejection without partial writes.

The isolated `build-panel-qa.yml` workflow builds this QA APK on the separate QA branch PR. It validates package ID, app label, version marker, APK signatures and existing forbidden-installer checks. A second emulator job downloads the unchanged **public signed official v1.0.16 x86_64 APK** and confirms official and QA install side-by-side while QA launches. This is intentionally **not** a publish workflow.

Use the **same fully green workflow run**'s `mihonnooir-v1.0.17-panel-qa-parallel` artifact, then extract `app-arm64-v8a-foss.apk` for an arm64 Android test phone. The zip may require GitHub login. Do not use an old artifact after branch updates. Do not install unless both the artifact verification and co-install emulator job succeed. It is not officially signed.

## Private real-book QA (unresolved)

On QA, export the same original One Piece source pages with identical options: X4 480×800, `PANEL_BY_PANEL`, `PAGED`, `AUTO_FIT`, RTL, crop on, overlap 8%, and optionally Full Page Before Splits. Enable *Panel Diagnostics* on the limited chapter and save the ZIP **privately**. Compare by sourceOrdinal and chapterPageOrdinal, not frame position or total count. Revisit #114 (still whole), the slanted sequence formerly #884/#885–887 (oversized overlap), and near-empty #278/#281/#287/#344/#379/#477/#507 (frame numbers may drift). Preserve credits, bubbles and all original source pixels. No manga assets or private diagnostics are uploaded publicly. Also check Row by Row and physically view the result on X4.

**Release gate:** This QA APK proves installation isolation only after green CI. It does not establish detector improvements, real One Piece acceptance, final signing continuity, or release readiness. Leave official v1.0.16 and Windows PR #53 untouched; do not merge or release v1.0.17 without acceptance.
