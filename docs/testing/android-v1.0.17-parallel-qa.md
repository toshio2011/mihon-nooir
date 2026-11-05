# MihonNooir v1.0.17 — parallel-installable QA5 (experimental overlapping reading rows)

This is a **test-only delivery path**, separate from draft panel PR #57. It builds the *same* Android overlay/panel converter as PR #57 from the pinned Mihon upstream, but changes temporary compiled-app identity **after** the normal overlay step.

- Official release (untouched): `MihonNooir`, package `dev.nooir.mihontrial.foss`, signed v1.0.16.
- Experimental QA (side-by-side): `MihonNooir QA5`, package `dev.nooir.mihonqa5.foss`, versionName `1.0.17-overlap-qa`, debug-signed.
- The QA app has **independent Android app data**. It does not import/copy official app's database, downloads, login sessions or preferences, nor does it replace/uninstall the official application.
- Auto-updater is disabled **only in the temporary QA checkout**, so testing won't offer replacement by an official artifact. No release/signing files, default Android identity, panel algorithms, Windows code or Folio firmware are changed.

## Implementation

`tools/prepare_parallel_qa.py upstream` operates only on a disposable **already integrated** upstream checkout. It checks every expected Gradle/i18n anchor before writing, keeps the existing FOSS suffix, and fails closed if upstream changes. Unit tests in `tests/test_parallel_qa.py` exercise package separation, QA labeling, updater deactivation, duplicate application and rejection without partial writes.

The isolated `build-panel-qa.yml` workflow builds this QA APK on the separate QA branch PR. It validates package ID, app label, version marker, APK signatures and existing forbidden-installer checks. A second emulator job downloads the unchanged **public signed official v1.0.16 x86_64 APK** and confirms official and QA install side-by-side while QA launches. This is intentionally **not** a publish workflow.

Use the **same fully green workflow run**'s `mihonnooir-v1.0.17-overlap-qa5-parallel` artifact, then extract `app-arm64-v8a-foss.apk` for an arm64 Android test phone. The zip may require GitHub login. Do not use an old artifact after branch updates. Do not install unless both the artifact verification and co-install emulator job succeed. It is not officially signed.

## Private real-book QA (unresolved)

On QA, export the same original One Piece source pages with identical options: X4 480×800, `PANEL_BY_PANEL`, `PAGED`, `AUTO_FIT`, RTL, crop on, overlap 8%, and optionally Full Page Before Splits. Enable *Panel Diagnostics* on the limited chapter and save the ZIP **privately**. Compare by sourceOrdinal and chapterPageOrdinal, not frame position or total count. Revisit #114 (still whole), the slanted sequence formerly #884/#885–887 (oversized overlap), and near-empty #278/#281/#287/#344/#379/#477/#507 (frame numbers may drift). Preserve credits, bubbles and all original source pixels. No manga assets or private diagnostics are uploaded publicly. Also check Row by Row and physically view the result on X4.

**Release gate:** This QA APK proves installation isolation only after green CI. It does not establish detector improvements, real One Piece acceptance, final signing continuity, or release readiness. Leave official v1.0.16 and Windows PR #53 untouched; do not merge or release v1.0.17 without acceptance.

## QA2 content checkpoint

This QA2 variant uses the exact stacked **draft PR #60** detector state, including draft PR #59's dense-text safeguard and PR #60's strong, thick black horizontal gutter recovery. Its **new** package ID allows it to coexist with official v1.0.16 *and* the earlier QA1 APK, which may have a different signing key. Neither existing app is uninstalled or overwritten. The label explicitly says QA2. This is a new experimental build only; source 75's speech-strip over-split and angled dark gutters remain unresolved until a private same-source re-export is inspected. Original copyrighted EPUB/diagnostics never go to GitHub.

## QA3 checkpoint (4 Nov 2026)

QA3 is a separate experimental build from stacked **draft PR #62**, which adds a protected shallow dialogue-strip coalescer to the PR #59/#60 text and thick-dark-gap fixes. Package `dev.nooir.mihonqa5.foss` is new and distinct from official v1.0.16, QA1, and QA2; none should be uninstalled or overwritten. All published artifacts are debug-signed, not official. The source-75-shaped private diagnostic geometry predicts 12 → 6 output frames with `Full Page Before Splits` still enabled; that prediction requires an **actual new private EPUB** plus diagnostics to verify. Source 31's angled rails remain unresolved, and any source-content false positives are release blockers. Do not merge/publish on CI alone.

## QA4 — paired diagonal rails (4 Nov 2026)

QA4 contains draft PRs #59/#60/#62 plus draft #64's conservative asymmetric **paired slanted horizontal frame rail** fallback. Its package ID `dev.nooir.mihonqa5.foss`, label `MihonNooir QA5`, versionName `1.0.17-overlap-qa`, and disabled updater are applied exclusively in the throwaway pinned upstream build checkout. It is isolated from official signed v1.0.16, earlier QA1/QA2/QA3 and Android production source. Expected private improvement: whole source page 31 and its duplicate 136 become three protected, overlapping row views plus full-page overview. This is a projection based on thumbnails, not a verified EPUB export. Same-source 201-page output and real X4 reading are release gates. Do not ship on CI alone.

## QA5 — user-suggested last-resort overlapping rows (5 Nov 2026)

QA5 is a **new**, separately installable experimental APK built from the head of draft PR #66, stacked on #64. Unlike QA4, after *all* existing detector passes fail on an otherwise illustrated portrait page, it can return three intentionally overlapping reading windows: top **0–40%**, middle **30–70%**, bottom **60–100%**, in that order. On each window only a strict clean full-height column gap may produce two left/right reading views. No confident inner panel boundary means keep the entire overlapping row; do not subdivide dialogue/art. All source coordinates are covered by the windows and overlaps are not controlled by the user's normal 0% export overlap setting. The source art is never polygon-clipped.

This is a **readability fallback, NOT verified panel recognition**. The eligibility screen checks rich balanced thumbnail content in every third (illustration, pale paper, and edge variation), and keeps cover/title/sparse/text/monochrome and long-strip content in existing modes. Private preflight from 201 user-supplied One Piece thumbnails predicts 37 candidates among the 45 formerly fully unsplit pages, but neither a Kotlin rerun nor real EPUB export has yet confirmed this number. Any false cut on a splash or speech bubble is a blocking regression. Keep this PR and its base unmerged; obtain same-source private EPUB + diagnostic ZIP and X4 read-through before considering release.

The QA5 package `dev.nooir.mihonqa5.foss` and label `MihonNooir QA5` coexist with signed official v1.0.16 and prior QA1–QA4; updater is off in QA5's disposable build. Do not confuse QA5 with the signed production APK.
