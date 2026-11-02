# MihonNooir — Complete Panel/Android/Windows Development Handover

**As of:** 2 November 2026 (MYT, UTC+08:00). **This document supersedes the dated status summaries in** `docs/PROJECT_HANDOVER.md`, `docs/PANEL_IMPLEMENTATION_STATUS.md`, and older handover files, while leaving their historical analysis intact.

**Repository:** https://github.com/toshio2011/mihon-nooir
**Companion firmware (DO NOT MODIFY in this task):** https://github.com/toshio2011/folio-nooir
**Current OFFICIAL Android release:** [v1.0.16](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.16), signed and previously installed/tested by user. **No public v1.0.17 release.**
**Core objective:** Convert manga on Android or full Windows app into XTC, XTCH, EPUB2, EPUB3 or CBZ for XTEINK, with big readable panel images, independent Row by Row/Panel by Panel modes, and *repeat artwork and speech through overlap rather than crop it away*.

## 1. GitHub branches, PRs and source-of-truth

| Work | Status / immutable ref | What it means |
| --- | --- | --- |
| Official Android v1.0.16 | Signed release tag commit `bad62d4103bd132aef1807ed2e41edb0258eb624` | Existing production APK; leave unchanged |
| Release-compatible development base | `integration/android-v1.0.17-base` | From the **exact v1.0.16 tag**, not the repository's divergent `main` branch. PR #55 was squashed here. This handover is committed as docs-only follow-up |
| [PR #55](https://github.com/toshio2011/mihon-nooir/pull/55) | **MERGED** squash commit `f8b4eb029c320bf55a839f077a3553e857783ffe` | Safe row detection, slanted *pale* gutter fallback, final-piece Auto Fit and regression tests |
| [PR #56](https://github.com/toshio2011/mihon-nooir/pull/56) | **DRAFT, NOT MERGED**; test branch `test/android-v1.0.17-panel-qa`, commit `3cc742a0c8461fa30ea07623d95c0e5b067d8152` | Changes ONLY `config/version.txt` (1.0.17) and adds `docs/testing/v1.0.17-panel-candidate.md`; generated **CI-signed TEST APK** |
| [PR #57](https://github.com/toshio2011/mihon-nooir/pull/57) | **DRAFT, NOT MERGED**; branch `feature/panel-art-overlap-padding-v1.0.17`; last source/test fix `075a6d9b667781018e87c229b422b8be08f3b488` | New overlap handling at detected boundaries, opt-in minor interrupted-corridor slicing, and experimental straight DARK gutter recovery. NOT in PR #56 APK |
| [PR #53](https://github.com/toshio2011/mihon-nooir/pull/53) | **DRAFT, NOT MERGED**; branch `feature/windows-full-v1.0.16`, head `ee0cbf2f23c120b81c4e31f57fe135277736c062` | Separate **full Windows** desktop effort. Shares converter/settings. Do not rewrite it as a stripped fork and do not alter official Android build while advancing it |
| Historical backup | `backup/panel-auto-fit-pr55-pre-release-base` | Old experimental pre-rebase state, NOT source for release |

**CRITICAL history warning:** The repository's `main` branch diverges from the official signed v1.0.16 tag and lacks some released Conan/panel diagnostics. Never assume `main` is the source for the latest Android release; **compare against v1.0.16 tag / release-compatible development base** before rebasing, merging, or creating a signed release.

## 2. CI and APK facts (verify again before acting)

- **PR #55** merged after a successful full Android/host conversion CI run [#41322954860](https://github.com/toshio2011/mihon-nooir/actions/runs/41322954860), including the emulator panel-diagnostic ZIP smoke test.
- **PR #56**: exact head `3cc742a0` passed full CI [#41373078233](https://github.com/toshio2011/mihon-nooir/actions/runs/41373078233): Kotlin host tests, Python checks, EPUBCheck, Android FOSS assembly, APK identity/signature inspection, and emulator panel-diagnostic smoke. Downloadable GitHub Actions ZIP artifact `mihon-nooir-foss-apk`, artifact ID **13110582047**: https://github.com/toshio2011/mihon-nooir/actions/runs/41373078233/artifacts/13110582047. The user was also given the extracted `MihonNooir_v1.0.17_PANEL_QA_arm64_TEST.apk` in the earlier conversation; verify the actual file is available in the new chat/container before linking it.
- **Signing warning:** The PR #56 artifact is **CI-signed**, NOT the persistently signed official v1.0.16 APK. Android may reject an in-place install due to a different certificate. Back up app data; do not advise uninstalling the official installation as a casual test step. This is a test build, not a public release.
- **PR #57 ongoing:** Previous host conversion CI [#41382542531](https://github.com/toshio2011/mihon-nooir/actions/runs/41382542531) failed on `PanelSafeSlicePlannerTest` ("Locally overlapping art may be safely repeated"). Next CI [#41383551925](https://github.com/toshio2011/mihon-nooir/actions/runs/41383551925) ALSO failed on the single-interruption test. Root cause was a synthetic fixture using `y % 500 in 496..504` where `500..504 % 500 == 0..4`, accidentally making half of the supposed gap solid ink. A **test-fixture correction** (explicit `y in 496..504`) was pushed as `075a6d9b` (no production code changed by that commit). [Latest CI #41384731647](https://github.com/toshio2011/mihon-nooir/actions/runs/41384731647) was **still running during this handover**. Check its outcome! If red, inspect the exact failing test/log before further changes. Never mark PR #57 green based on PR #56 or prior runs.
- Do **not** merge PR #57 or promote it to an APK/public release while tests fail or before real-manga QA. An Android build from PR #56 will NOT include PR #57's overlap/dark-gutter changes.

## 3. User-approved design rules (these are non-negotiable)

1. **Manga should be large/readable on XTEINK X4:** select landscape or portrait for each **FINAL split piece**, whichever fills more of the screen without cropping content. `AUTO_FIT` is not a blanket orientation on the original page. Split first; then orient each finished region.
2. **Overlap is WELCOME.** Prefer repeating a character's limb, drawing or speech bubble in two consecutive frames over cutting/removing it. Small repeated art is okay; lost speech is NOT. Preserve source pixels and reading order.
3. **Panel by Panel:** detect horizontal rows first; then split individual panels inside each row. Try guarded fallbacks for missed boundaries; if insufficient proof, preserve the row/group. Do not create many tiny border-only or title-only panels. Right-to-left Japanese reading order; preserve user mode preference.
4. **Row by Row:** stays ROW-ONLY. Normal horizontal seams first, conservative pale-gray recovery second. Draft PR #57 may additionally try **dark horizontal seams**, never vertical/panel cuts. If none can be trusted, preserve original page rather than enter general spread/webtoon slicer. Keep confirmed rows even when another tier cannot split.
5. **Not all gutters are white:** some are gray or black/dark; some boundaries are diagonal or irregular. Dark background alone, a thin black frame rule, letter strokes or touching artwork alone are NOT proof of a panel. Prefer false negative (group) to destructively unsafe false positive.
6. Keep **Full Page Before Splits**, safe padding, RTL/LTR, mode OFF behavior, chapter order/covers, user-selected overlap, existing export formats, XTEINK reader compatibility, and prior Android v1.0.16 Conan safety behavior.
7. **No OCR, paid AI, network image processing or mandatory page-by-page manual preview**. Conversion runs on phone/desktop. Big output files are acceptable when useful, but do not silently truncate.
8. **Maintain Windows parity** via shared Kotlin core and settings. Do not modify Folio Nooir or the separately developing full Windows PR #53 without an explicit scope decision.

## 4. Merged PR #55 — actual implemented behavior

**Files:** `overlay/app/src/main/java/dev/nooir/bridge/PanelDetectionCore.kt`, `PanelAutoFit.kt`, `PanelSafeSlicePlanner.kt` (existing shared), `AndroidPageSource.kt`, `desktop/cli/NooirDesktopCli.kt`, and related tests. The merged diff against official v1.0.16 changes nine intended files, not app signing/Gradle.

- **Auto Fit after splitting**: existing safe `PanelSafeSlicePlanner.plan` can split long confirmed panels, then `PanelAutoFit.refitFinalSlices` selects portrait/landscape for each final piece, maximizing occupied fraction (portrait tie). Row by Row remains a single view per detected row, not column panels.
- **Row-only pale-gray fallback**: `detectRows` can find strongly validated horizontally continuous pale-gray separations when the regular detectors miss them. Crossing speech balloons and isolated dark horizontal rules are protected. On total failure, Android and desktop keep the whole source page without invoking arbitrary general PageSlicePlanner cuts.
- **Guarded diagonal PALE fallback**: a single otherwise undivided non-sparse illustrated page may be split into two **overlapping rectangular views** when a strong straight-slanted pale corridor is visible through almost the entire page. It does not yet perform general polygon segmentation. At merge time this recovery was page-level only, not yet a general slanted retry inside each already-found row.
- Preserve original signed v1.0.16's diagnostic ZIP, Conan title/footer/framed-border safeguards and sparse-tier handling.
- The user accepted overlap at boundaries; strictly dark gutter detection was NOT in the merged #55.

## 5. Draft PR #57 — unmerged, experimental behavior and limitations

**Branch:** `feature/panel-art-overlap-padding-v1.0.17`. **Current head at handover:** `075a6d9b` (verify live).
**Important code paths:** `PanelDetectionCore.kt`, `PanelSafeSlicePlanner.kt`, `AndroidPageSource.kt`, `desktop/cli/NooirDesktopCli.kt`, and `tests/PanelDetectionCoreTest.kt` / `tests/PanelSafeSlicePlannerTest.kt`.

- **Honor configured overlap on detected rectangular regions:** `PanelDetectionCore.overlapPaddingPercent` maps selected overlap to 1–5% of whole source width/height on **each** side of interior splits (8% selection -> 4% each side; 30% caps at 5%). Android and Windows CLI now use this for `sourceRegions` instead of hardcoded 1%. Detection, RTL order and sparse-tier decisions must not change just because padding increased.
- **Opt-in interrupted split on unusually long CONFIRMED panels:** first attempt fully clean 3px corridor as before. If no clean seam and user selected **at least 8% overlap**, permit an otherwise pale seam with <=20% locally crossing sampled ink; pad BOTH output frames (symmetric protection), reject sparse candidates and full-ink/uncertain spans. This is NOT a general permission to cut anywhere through artwork. The first regression test needed correction; CI pending.
- **Dark separator proposal:** `recoverDarkGutter` attempts thick, continuous black/dark straight boundaries, with pale flanks and **independently substantial artwork beyond both sides**; rejects a thin black rule, solid black splash, or heavily interrupted separator. Independent Row by Row calls only horizontal dark recovery; Panel by Panel can attempt vertical dark recovery on a rejected tier and horizontal dark recovery on a wholly unsplit page. It yields a rectangular axis; crop overlap retains adjacent art.
- **Limitations:** No verified dark diagonal/curved/irregular polygon detection. Mixed or interrupted black gaps can still be grouped. General detector thresholds may be overly strict or insufficient on real manga: physical/regression validation needed. Existing strict row-first splits and story-title guards have higher priority. Potential false positives must be investigated instead of turning dark thresholds loose.
- **Safety:** New tests must pass before even considering merge. PR #57 changes **production code** and is not contained in the PR #56 v1.0.17 QA build. Keep the feature draft pending verification.

## 6. Actual user-export evidence — One Piece EPUB, 2 Nov 2026

A user-uploaded **`One Piece.epub`** is an **already converted** output, not the original unsplit manga panels. The source image geometry therefore cannot be reconstructed reliably from this output alone. The file is private; **never add manga art, diagnostic screenshots or the EPUB to the public GitHub repository.**

Verified in the conversation/runtime: **27,379,286 bytes**, ZIP archive with **735 image entries**, **389 at 480×800 (portrait)** and **346 at 800×480 (landscape)**. Approximately 736 XHTML/HTML items including navigation. The export supports mixed orientation structurally. Visual review flagged some thin titles/isolated story art or huge white margins, notably around output frames **43–47, 240–249, 273–276**. A previous analysis flagged **7 images with under 1% sampled dark pixels**; treat this as a QA lead, NOT proof all seven are wrong (some may be intentionally blank or overview pages). Do not assume an EPUB page index is the same as original manga source page.

**Key open issue:** Too many small/near-empty magnified screens. Larger overlap alone does not solve over-fragmentation and may enlarge whitespace. Need paired real source pages (or private panel-diagnostic ZIP) and matching settings to decide whether faults arise from row/column detection, trim, long-panel safe slicer, overview screens, title guards or post-split Auto Fit. Keep confidence and minimum-content safeguards. Compare exports with `tools/panel_epub_diff.py` described in `docs/PANEL_EXPORT_QA.md` using same chapters/options; it anchors identical PNGs by hashes and spine order without committing private images.

The user previously shared Detective Conan panel/row EPUB and private diagnostic ZIP in other chats, but these were **not available as files in the latest handover session**. No new real Conan source regression was performed. Do not fabricate physical tests.

## 7. How to validate next — exact order

1. **Check PR #57 latest CI** for `075a6d9b` (or newer): https://github.com/toshio2011/mihon-nooir/actions/runs/41384731647. If failed, read the `Validate XTC, XTCH, EPUB 2, EPUB 3 and CBZ export formats` job logs and the first failed Kotlin assertion. Run/repair legitimate tests, never silently remove guards. Old failed runs were primarily a wrong `%500` fixture.
2. If host tests pass, ensure complete **Android FOSS APK**, **EPUBCheck**, actual format checks, version/signing identity and **Android emulator panel diagnostic smoke** pass for the *same SHA*. A green Kotlin job alone is not enough.
3. Crosscheck Row by Row only-horizontal on white/pale-gray/dark gaps, separated mixed-quality tiers, black splash pages, crossing balloons and thin rules. Panel by Panel should split clear rows first and preserve uncertain content; watch RTL.
4. **Compare real permitted original source** and new exports, especially irregular/slanted/dark gutters, speech balloons extending across borders and row/panel layouts. Test 0%, 8%, 12%, 20% overlap; verify both sides retain crossing speech. Focus the previously flagged One Piece sparse frames; avoid treating already-exported images as uncut originals.
5. Compare XTC, XTCH, EPUB 2/3 and CBZ page count/order and output geometry. Test actual X4 firmware loading/page navigation; compare UI readability with existing official v1.0.16. Android pixel/Bitmap vs Windows Java2D matching is unproven even with shared geometry.
6. Decide whether to merge #57 **only after green same-commit CI and practical regression confidence**. The already-built PR #56 APK remains an older baseline; **create a fresh test APK** from the merged change before any release discussion. Do not silently upgrade #56 or publish a v1.0.17 GitHub release.
7. If releasing eventually, use persistent official signing credentials and verify the certificate against v1.0.16. Do not re-sign with debug key or overwrite released tag/assets. Preserve full Windows PR #53 and no Folio Nooir modifications.

## 8. Useful URLs, local files and commands

- **Core merged:** https://github.com/toshio2011/mihon-nooir/pull/55
- **Test candidate:** https://github.com/toshio2011/mihon-nooir/pull/56
- **Unmerged overlap/dark work:** https://github.com/toshio2011/mihon-nooir/pull/57
- **Windows:** https://github.com/toshio2011/mihon-nooir/pull/53
- **Test APK artifact:** https://github.com/toshio2011/mihon-nooir/actions/runs/41373078233/artifacts/13110582047
- **CI latest dark/overlap:** https://github.com/toshio2011/mihon-nooir/actions/runs/41384731647
- **One Piece QA input from conversation:** `One Piece.epub` (private; upload or attach again if not accessible next chat)
- **Earlier test kit artifacts from conversation:** `MihonNooir_v1.0.17_Synthetic_Panel_QA_Kit.zip`, `MihonNooir_v1.0.17_QA_kit_preview.png` (verify paths before sharing in any future turn)
- **Synthetic tests:** `bash tests/run.sh` on matching branch (requires Kotlin compiler and other build prerequisites); Python tests `python3 -m unittest discover -s tests -p "test_*.py" -v`
- **Format comparison:** `python3 tools/panel_epub_diff.py "old.epub" "new.epub" --json -o panel-comparison.json` (same chapters/profile and settings)
- **Build CI:** `.github/workflows/build-apk.yml`; contains Android full build and emulator panel diagnostic smoke.
- **CI artifact** is a ZIP holding APK variants and checksums; for a recent ARM64 phone use `app-arm64-v8a-foss.apk` after verifying identity and signing. Never imply that CI signing permits official v1.0.16 in-place updates.

## 9. Minimal copy/paste to resume in new ChatGPT chat

> Continue MihonNooir manga panel/export development from **docs/HANDOVER_2026-11-02_PANEL_V1.0.17.md** in the repository. Official Android v1.0.16 stays untouched. PR #55 is merged on the release-based `integration/android-v1.0.17-base`; PR #56 is a green CI-built **older** v1.0.17 test APK (not official signed). PR #57 is the current **unmerged** overlap + dark-gutter work. First verify latest PR #57 CI for `075a6d9b`, fix any real tests, then review One Piece's over-fragmented screens using actual unsplit manga/detector diagnostics. Preserve rows-first, Row by Row only-horizontal, speech/art overlap, per-final-piece Auto Fit, all five formats, RTL, and Windows shared-core parity. Don't merge or release without QA. Check GitHub live; historical handovers contain stale versions.

**Guardrail:** This is a dated state snapshot. GitHub may change after it was written; the next chat must re-check live PR/CI SHAs before making new claims.
