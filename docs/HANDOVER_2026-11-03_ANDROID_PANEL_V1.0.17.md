# MihonNooir — Android panel-quality handover (3 November 2026)

> **Current working handover for Android v1.0.17 panel quality.** This supersedes the dated 21 October project handover for **this workstream only**. GitHub commits and Actions are the source of truth: always recheck HEAD/CI before treating any line as live. No private manga images or diagnostic ZIPs belong in this repository.
>
> **START HERE:** [Draft PR #57](https://github.com/toshio2011/mihon-nooir/pull/57), branch `feature/panel-art-overlap-padding-v1.0.17`, base `integration/android-v1.0.17-base`. The branch was at `4527b719aa22b07a9ac4dc2f6b55c760ae7a56e6` before these handover-document edits; recheck the PR head.
>
> **Release boundary:** Signed, publicly released Android **v1.0.16** ([release](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.16), 29 October 2026) is unchanged. **v1.0.17 is an experimental QA-only candidate**, PR #57 remains **draft / unmerged**. Do not tag or publish. [Windows draft PR #53](https://github.com/toshio2011/mihon-nooir/pull/53) remains independent and must not be edited here. Do not modify [Folio Nooir](https://github.com/toshio2011/folio-nooir).

## 1. User intent and design rules

The phone does all image analysis/conversion; the XTEINK X4 (480×800 e-ink) should only display already-prepared reading pages. A good conversion means **one comfortably large readable panel per screen when genuinely separable**, reading order preserved (RTL manga, LTR option), and no need for per-page human approval. Accept protected crop overlap when characters, speech balloons or panels interlock; do not accidentally crop any pixels. For ambiguous panels, prefer the intact original or a safely grouped view rather than an invented cut.

Critical user feedback: **some output pages are cut unnecessarily and contain almost nothing**; other angled-panel pages do not split, while newer slanted cuts may repeat too much artwork. The measure of success is *less pointless page turning and larger meaningful art*, NOT the highest possible frame count.

Keep the distinct modes: **Panel by Panel**, **Row by Row** (horizontal tiers only, never implicitly vertical panel splitting), **OFF**, and **WEBTOON**. Preserve EPUB 2/3, XTC, XTCH, CBZ, cover/metadata/chapter selection, contrast/dither, manual and Auto Fit orientation, RTL/LTR, optional Full Page Before Splits overview, and backward-compatible conversion settings. The detector is OCR-free, local, bounded-thumbnails-only, and opt-in; no AI subscription/service.

## 2. Actual private real-book evidence (not release validation)

The user supplied successive **One Piece EPUB exports**. We inspected the *output EPUBs*, not the original-resolution source manga or matched new detector traces. No manga image files or contact sheets were ever committed to GitHub.

- Earlier export **735 frames**; later export **979 frames**, and one intermediate **976 frames**. The additional chapter accounts for most of the total difference; **696 PNG payloads match byte-for-byte** between the 735 and 979 baselines. Higher frame counts do NOT establish better splits.
- In the 979 export, **frame #884** remains the same full-page overview as before, but **#885–887** are now three actual slanted-row reading views. This is a genuine improvement over the previous unsplit #884. Its crop overlap is visually bulky and should be reviewed.
- **Frame #114** still appears as one full small illustrated page, byte-identical to its counterpart in the earlier export; a plausible slanted border is visible, but the current source-level reason for rejection is unknown. This is not caused by the chapter-first-page exemption.
- Frames **#87, #391, #603** are full-page overview screens followed by actual crops. Do not mistake them for detector misses when `showFullPageBeforeSplits=true`.
- Across the 979 frames, **10 contain <1% dark pixels** and **33 <3%** (dark defined as grayscale <220 at exported dimensions). **Eight** of the 10 under-1% image payloads match old-export bytes exactly. This means most of the especially empty frames predate the new slanted-row fix, but it does **not** prove any frame can be safely deleted.
- Representative almost-white or pointless-looking output frames: **#278, #281, #287, #344, #379** (small marks/credits/borders), **#477 and #507** (isolated portions of large curved bubble outlines), and **#941/#949** in the added chapter. Some are actual text/credits/TOC; do not remove them on an ink-percent threshold.
- Prior Conan private diagnostic ZIP (67 original pages, 324 output frames excluding separate cover) confirmed source geometry and no sampled ink-coordinate gaps in that trace, but some title/headline bands were split into tiny fragments. Old 0%-overlap diagnostics are not proof for the newer 8%-overlap behavior. See [Conan diagnostics audit](testing/conan-2026-11-02-private-diagnostics-qa.md).
- Older, detailed 735-frame One Piece audit is [here](testing/one-piece-2026-11-02-output-audit.md). Its source limitation still applies.

**Important:** Export **frame** numbers shift whenever panels split or chapters are added. The diagnostic **sourceOrdinal + chapterPageOrdinal + exact input options** are the stable way to compare before/after. An output-only EPUB cannot prove which stage emitted a suspect image.

## 3. Actual code changes to date — PR #57

Review these files before proposing any new detector rules:

| File | Role |
| --- | --- |
| `overlay/app/src/main/java/dev/nooir/bridge/PanelDetectionCore.kt` | Row-first/RTL panel detector, slanted rails, safe short/ruled terminal footer absorption and `PanelPlan.preserveSingleFrame` |
| `overlay/app/src/main/java/dev/nooir/bridge/PanelSafeSlicePlanner.kt` | Clean/guarded corridor splitting, sparse prevention, no dropped tails |
| `overlay/app/src/main/java/dev/nooir/bridge/PanelAutoFit.kt` | Final source rectangle orientation; maximize usable fill after safe slicing |
| `overlay/app/src/main/java/dev/nooir/mangabridge/AndroidPageSource.kt` | Android bitmap/thumbnail, detector→source padding→trim→safe-slice→render mapping; keeps merged footer `preserveWhole` |
| `overlay/app/src/main/java/dev/nooir/mangabridge/PanelDiagnosticArchive.kt` | Opt-in, private, per-source exact grayscale thumbnail, source/output geometry, trace, optional original compressed source; optional `detectorPreserveSingleFrame` |
| `overlay/app/src/androidTest/java/dev/nooir/mangabridge/PanelDiagnosticArchiveInstrumentedTest.kt` | Emulator end-to-end CBZ/diagnostic parity and synthetic landscape merged-footer actual render test |
| `tests/ConanPanelRegressionTest.kt`, `tests/PanelSafeSlicePlannerTest.kt` | Synthetic source-coverage, sparse/border, RTL/LTR and title/credit protection checks |
| `tools/panel_diagnostic_inspect.py`, `tests/test_panel_diagnostic_inspect.py` | Offline validation of private ZIP and single-preserved-frame source/output mapping |
| `.github/scripts/panel-diagnostic-smoke.sh`, `.github/workflows/build-apk.yml` | CI emulator runner, exact two-test success validation and early shell syntax guard |

**Detector guard detail:** A special *Panel-by-Panel-only* terminal-strip merger runs after validated horizontal tiers. It can combine a near-empty ruled tail with the immediately preceding **single full-width illustrated tier** if geometry and content are unambiguous: tail <=18% page height, <=32% page width in height, <3% ink, and one long horizontal/vertical straight rule or entirely blank. No merge across source pages, across side-by-side differently ordered panels, or for meaningful short text/credits. The prior stricter short-footer absorption is also supported. This uses a rectangle UNION to preserve all source pixels.

If either footer absorption yields **one complete source rectangle**, the plan has `preserveSingleFrame=true`. Android carries it to `DetectedPanel.preserveWhole`; downstream margin-trim and corridor-slicing MUST NOT undo the merge. Normal panel pages and title bands do not inherit this flag. This is **not** a global blank-page removal algorithm.

**Key commits / completed phases:**
1. `4f3bd911`, `39c06f4d`, `cfb5a19a` — strict slanted horizontal dark-frame-rail recovery and synthetic tests. [Fully green CI #41403825755](https://github.com/toshio2011/mihon-nooir/actions/runs/41403825755).
2. `070d63b1`, `a9e878a9`, `c832f686` — guarded near-empty terminal-ruled-strip absorption, reconciled with >=2% row admission and synthetic credit/RTL/Row by Row assertions. [Fully green CI #41436673644](https://github.com/toshio2011/mihon-nooir/actions/runs/41436673644).
3. `667377e3`, `af4b9377`, `87d0d22c`, `bfa1216a`, `ce9487ea` — preserve merged single-frame decision through Android render, fix the older short-footer fallback loophole, regression checks. [Fully green CI #41470994731](https://github.com/toshio2011/mihon-nooir/actions/runs/41470994731) is the **last exact full-green checkpoint before the new instrumentation/diagnostic improvements**.
4. `328d1657`, `2f8b0e0e`, `7344ea1f`, `aae2fa0d` — optional `detectorPreserveSingleFrame` in ZIP; actual Android landscape merged-footer render regression; private ZIP inspector and Python tests. [CI #41558658243](https://github.com/toshio2011/mihon-nooir/actions/runs/41558658243): **build PASS; emulator job flagged FAIL only because smoke script still expected `OK (1 test)`; raw instrumentation reported both named tests PASS and `OK (2 tests)`.** Script did not reach ZIP pull.
5. `c6d22480` — attempted two-test CI check. [CI #41567687018](https://github.com/toshio2011/mihon-nooir/actions/runs/41567687018): **build PASS; emulator tests both PASS; script stopped with Bash syntax error**. The script was inadvertently corrupted during a string replacement: JavaScript `$'` in a replacement string expands to the original string's suffix, duplicating the ZIP-pull block and truncating a grep regex. **Never again use JavaScript string replacement with `$'` in the replacement body.** This was CI script corruption, NOT manga render failure.
6. `01420c9a` — **completely rewrote** the 60-line smoke script to restore valid Bash, require both named passing tests and their summary, then pull and validate ZIP. `4527b719` — added `bash -n .github/scripts/panel-diagnostic-smoke.sh` to the **start** of the build job so shell syntax errors fail fast. [Latest CI from that patch series](https://github.com/toshio2011/mihon-nooir/actions) must be checked live; do not claim it green without inspecting both jobs.

## 4. Current state / exact build verification

At the **3 Nov 2026, MYT handover-writing checkpoint**, PR #57 is open, draft and unmerged. The signed official Android app is [v1.0.16](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.16). The next running CI on the corrected script/syntax guard is not yet confirmed fully green. Before doing anything else:

1. Read [PR #57](https://github.com/toshio2011/mihon-nooir/pull/57) head SHA and [GitHub Actions](https://github.com/toshio2011/mihon-nooir/actions). Confirm **both** `build` and `android-panel-zip-smoke` succeeded for the **same exact code SHA**.
2. If emulator fails, inspect raw job logs, separately identify `INSTRUMENTATION_STATUS_CODE: 0` successes, `OK (2 tests)`, Bash/ADB failures and the final ZIP inspection. Do not assume a panel-regression failure merely from the job label.
3. When completely green, use the exact run's `mihon-nooir-foss-apk` CI artifact for **QA only**. It contains `app-arm64-v8a-foss.apk` for arm64 Android. The artifact is **debug-signed** under `dev.nooir.mihontrial.foss`, cannot safely update the **release-signed** v1.0.16 app installed under the same package ID. Use an emulator/spare device, not a destructive uninstall on the user's primary phone.
4. A successful build only establishes tests, format structure, assembly and synthetic Android output. It does **not** prove real One Piece panel quality or X4 reading experience.

## 5. Minimum release-blocking private source-matched QA

Use the **same original source chapters/settings** as baseline and produce a **fresh** Panel by Panel export with current head. Suggested: X4 **480×800**, `PAGED`, `AUTO_FIT`, **RTL**, crop on, **8% overlap**, `showFullPageBeforeSplits=true`, with an intentionally documented choice of `keepChapterFirstPageWhole`. Also trial 0% and 12% overlap and `ROW_BY_ROW` / `OFF` regressions on a small chapter. The full-page overview is intentional when a single source yields multiple reading views.

Turn on **Panel Diagnostics** (explicit opt-in) for a limited test run and keep the ZIP **private**. It contains copyrighted images, grayscale thumbnails, per-page trace, source rectangles, output rectangles and optional source bytes; it must NOT go into public issues/commits. The ZIP's `pages/*.json` includes source ordinal, output frame start/count, `detectorRegionsThumbnail`, `sourceRegionsWithPadding`, `preOverviewSlicesSource`, `outputSlicesSource`, `outputRotated`, `events`, and, in latest builds, `detectorPreserveSingleFrame`. The original samples are capped and may be absent.

Run:
```bash
python3 tools/panel_diagnostic_inspect.py PRIVATE_DIAGNOSTICS.zip --json
python3 tools/panel_diagnostic_inspect.py PRIVATE_DIAGNOSTICS.zip --frame 114
python3 tools/panel_epub_diff.py OLD_SAME_SOURCE.epub NEW_SAME_SOURCE.epub --json -o PRIVATE_DIFF.json
```
The `--frame` argument is an output ordinal **of the new export**, not source ordinal #114; use ZIP mapping to find the current new frame first. Do not infer perfect pixel integrity from identical frame hashes or ZIP geometry alone.

Inspect: (a) formerly whole **#114** angled panels; (b) old **#884** and following three slanted views and their excess overlap; (c) nearly blank **#278/#281/#287/#344/#379/#477/#507** and the new chapter's **#941/#949**; (d) TOC/credits vs truly redundant borders; (e) multi-column & speech bubbles; (f) no regression in official-signed v1.0.16 (reference only, never overwrite primary app), ROW_BY_ROW, WEBTOON, all formats, X4 on-device legibility. Frame numbers will drift.

**Do not** universally drop <3% ink frames, merge uncertain regions across source images, discard credits, or alter the Windows or Folio projects. If remaining problem is on a separate original source page, a detector-level coalescing rule cannot solve it safely; explain the limitation instead of masking it.

## 6. Next tasks and stop conditions

1. **CI closure:** fix any remaining CI shell/ADB/ZIP issue at exact head. Add no further pixel heuristic changes until verified. The corrected smoke should verify both named tests plus `OK (2 tests)`, then pull the ZIP and run the offline inspector.
2. **QA artifact:** once green, record the exact run ID, artifact ID/expiry and debug signing warning in [QA checklist](testing/android-v1.0.17-pr57-qa-checklist.md). Never link an older build as though it contains newer code.
3. **Private real-source re-export:** compare by *original source ordinal*, not frame number or total count; verify actual eliminated empty screens, retained text and dark/angled artwork.
4. **If regression confirmed:** investigate specifically one converter stage using detector events and source rectangles. Add synthetic tests reproducing it; keep all source pixels. Avoid threshold changes unless evidence justifies them.
5. **Release decision:** remain draft/unmerged and v1.0.16 official until real manga and X4 QA are accepted. Publish/merge only after user approves.

## 7. Copy-paste prompt for a new ChatGPT/Codex chat

> Continue **MihonNooir Android v1.0.17 panel-quality** work at `toshio2011/mihon-nooir`, **draft PR #57**, branch `feature/panel-art-overlap-padding-v1.0.17`, base `integration/android-v1.0.17-base`. First read `docs/HANDOVER_2026-11-03_ANDROID_PANEL_V1.0.17.md`, `docs/testing/one-piece-2026-11-02-output-audit.md`, `docs/testing/android-v1.0.17-pr57-qa-checklist.md`, then recheck PR HEAD and the latest GitHub Actions. Official release **v1.0.16 stays untouched**. Windows PR #53 and Folio Nooir stay untouched. The user cares most about **large readable panels, minimal empty/border-only frames, safe overlap, and complete speech/art**. Important real output facts: One Piece #884 now yields three slanted views but overlaps too much; #114 remains whole; 979-frame export has 10 frames <1% ink, of which 8 were identical in the old export. There are targeted footer coalescers and `PanelPlan.preserveSingleFrame`, plus an Android emulator regression and opt-in diagnostic trace. CI failures on Nov 3 involved outdated 1-test expectations and then malformed Bash, **not failed Android assertions**. Verify the repaired script and both CI jobs. Do not merge/release on CI alone. Next real QA requires **same-source original export + private diagnostic ZIP**, source-ordinal comparison, and X4 visual reading check. Never put private manga images/EPUB/diagnostic ZIP into the public repository. Continue actual implementation only for a confirmed reproducible issue; keep PR draft and report exact new SHA / CI evidence.

**Handover maintenance rule:** When the current head passes/fails CI, update this document's status with the exact SHA/run/artifact. Keep old evidence explicitly historical, and do not confuse completed synthetic checks with real manga acceptance.
