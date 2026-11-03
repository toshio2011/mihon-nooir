# MihonNooir v1.0.17 — PR #57 hands-on panel QA

> **CURRENT (3 November 2026):** Read [November 3 Android panel handover](../HANDOVER_2026-11-03_ANDROID_PANEL_V1.0.17.md) first. PR #57 is experimental, **draft / unmerged**, not v1.0.17 released. The official signed release is [v1.0.16](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.16) and stays untouched. Every new note/commit changes HEAD: verify PR head and BOTH jobs of the exact latest Actions run.

## Verified 3 November CI closure (12:07 MYT)

**Run [#41574727251](https://github.com/toshio2011/mihon-nooir/actions/runs/41574727251) is fully SUCCESS for code commit `33e6e65192dcb49312498558a16e73b7554344cc`.** Both jobs completed successfully: `build` and `android-panel-zip-smoke`. The build passed the early Bash syntax guard, overlay/format checks, APK assembly and instrumentation APK compilation. Raw emulator instrumentation reported `INSTRUMENTATION_STATUS_CODE: 0` for both `shortRuledFooterStaysOneFrameThroughAndroidRender` and `realAndroidCbzZipAndRenderParity`, ending `OK (2 tests)`. A 4,578-byte diagnostic ZIP was actually pulled from the Android emulator and passed the offline inspector (`valid: true`, two synthetic source pages, nine output frames and one separate cover). The inspector flagged source ordinals 1 and 2 for further inspection; these are synthetic fixture heuristics, **not evidence of successful One Piece QA**.

**Exact artifacts from that run (expiry 1 February 2027):** [FOSS QA APK #13203796723](https://github.com/toshio2011/mihon-nooir/actions/runs/41574727251/artifacts/13203796723), [instrumentation APK #13203776875](https://github.com/toshio2011/mihon-nooir/actions/runs/41574727251/artifacts/13203776875), [emulator diagnostic smoke #13202769432](https://github.com/toshio2011/mihon-nooir/actions/runs/41574727251/artifacts/13202769432), and [reader interoperability fixtures #13203451347](https://github.com/toshio2011/mihon-nooir/actions/runs/41574727251/artifacts/13203451347). The QA artifact is debug-signed and **cannot update the release-signed v1.0.16 on the primary phone**. Use an emulator or spare device. Following that green code commit, the PR advanced to `c0847e02` through **five documentation-only commits** (`docs/PANEL_IMPLEMENTATION_STATUS.md`, `docs/PROJECT_HANDOVER.md`, `docs/README.md`, this checklist and One Piece audit). These did not modify converter or CI code, but do not call an untested later SHA the exact tested SHA. Recheck newer runs after every documentation update.

**Outstanding release gate:** Fresh same-source real manga export with private diagnostics, source-ordinal comparisons for nearly-empty frames, #114 and the #884 slanted sequence, speech/art preservation, and physical X4 reading checks. Keep PR draft; do not publish v1.0.17 or modify official v1.0.16, Windows PR #53, or Folio Nooir.

## Exact CI and APK safety

- **Last fully green code checkpoint BEFORE the newer diagnostics and two-test shell-script work:** `ce9487ea4bf747f07e60d2672a883a6bd3d834cc`, [run #41470994731](https://github.com/toshio2011/mihon-nooir/actions/runs/41470994731). Both the converter build and the Android emulator diagnostics passed.
- **Prior proven-debug QA artifact (older code only):** [FOSS APK artifact #13155484472](https://github.com/toshio2011/mihon-nooir/actions/runs/41470994731/artifacts/13155484472). This does **NOT** include subsequent `detectorPreserveSingleFrame` diagnostics or the new Android end-to-end footer instrumentation test. Do not claim it is the newest corrected build.
- **Nov 3 test-run fact:** `aae2fa0d` [run #41558658243](https://github.com/toshio2011/mihon-nooir/actions/runs/41558658243) built successfully; **both new and old Android tests passed**, but the job failed at a stale `OK (1 test)` shell check. `c6d22480` [run #41567687018](https://github.com/toshio2011/mihon-nooir/actions/runs/41567687018) again passed both tests but then hit a **malformed Bash script** before downloading the ZIP. Neither failure was a manga segmentation assertion. **The corresponding CI runs are NOT full green.**
- **Corrective commits:** `01420c9a` rewrote the two-test script safely; `4527b719` added an early `bash -n` syntax check. Any later docs commits also affect the PR SHA. See [Actions list](https://github.com/toshio2011/mihon-nooir/actions) for final result; **never call a queued or failed run green**.
- Only after the exact final commit's **build + emulator smoke** are green, use that same run's `mihon-nooir-foss-apk` ZIP to test its actual code. For the REDMAGIC arm64 phone, the relevant APK inside is `app-arm64-v8a-foss.apk`.
- **Signing boundary:** CI FOSS APKs are **debug-signed** (previously verified certificate SHA-256 `bdf916db22e2c4ee0f3026273209d292db0fbc494739bf34e81a80d81d53e5b5`), use the **same package ID** `dev.nooir.mihontrial.foss`, and cannot be installed over the user's **official release-signed** v1.0.16. Not a distinct QA application. Prefer a test emulator/spare device. **Do not uninstall the official phone app or erase its data for QA.**

## Minimum private real-source tests

Use only artwork you own or may privately test. No diagnostic ZIP or copyrighted manga pages should be posted to public issues/PRs. Keep chapter selection and source images identical between baseline and candidate.

| Case | Recommended settings | Acceptance |
| --- | --- | --- |
| Conan ordinary 2×3 manga page | `PANEL_BY_PANEL`, `PAGED`, `AUTO_FIT`, RTL, crop on, overlap 8% | Rows top-to-bottom, panels right-to-left; selected wide panel rotates only if it fills more space; all balloons intact |
| Conan original source ordinal 21 | Same as above; also repeat with overlap 0% | The large opening can stay landscape; right and left lower panels preserve content and order; 8% repeats boundary art |
| Conan source ordinals 37 and 66 | Same as above, chapter title pages | **Do not demand additional cuts of the short title/headline bands**. These three old under-30%-fill outputs are real text strips, not badly segmented illustrated panoramas. Preserve wording and source order |
| Illustrated panel crossing light/gray gutter | overlap 8%, repeat with 12%; `PANEL_BY_PANEL` | No missing speech bubble border, character, or caption; uncertain gaps stay grouped |
| Thick straight dark gutter | panel then row modes, overlap 8% | Only confidently corroborated seams split; one-pixel black rules and heavily interrupted black bands stay grouped |
| Mixed-spread or staggered panels | RTL and LTR as appropriate | Correct reading order, complete overlap/speech; avoid duplicated *standalone* almost-blank zoom pages |
| Very tall illustration with one clean seam then a solid tail | panel + AUTO_FIT, 0/8/12% | Keep the verified first cut and the remaining intact view; do not roll back all cuts or invent another seam |
| One Piece newest 979-frame EPUB: #114 unsplit, #884 overview + #885–887 slanted views, and mostly blank #278/#281/#287/#344/#379/#477/#507 | Re-export the **same original source pages/settings** with the latest green APK and opt-in diagnostics | Compare by source ordinal, reduce pointless screens while keeping credits and speech; review #884 overlap, recover only proven safe angled cuts |
| Row by Row and WEBTOON | Try RTL/LTR and long vertical source | Row by Row does not cut vertical columns; webtoon reads top-to-bottom. No unintended panel cuts |
| Color EPUB 2/3, XTC, XTCH and CBZ | Small sample of same chapter | Check orientation, page order, correct format and actual XTEINK X4 readability |

### Suggested test steps

1. On a **separate test environment**, export a small Conan chapter using `More conversion settings`. Choose X4 480×800, `PANEL_BY_PANEL`, `PAGED`, `AUTO_FIT`, RTL, white-margin crop, overlap 8%, and **Full Page Before Splits** if whole-page comparisons help.
2. Turn on **Panel Diagnostics** only for the small sample. Keep the resulting private ZIP for source-index/region matching; this is not part of the public manga export.
3. Export an EPUB copy. Repeat with overlap 0% (to identify true padding changes); optionally repeat Row by Row. Keep all output names distinct.
4. Compare unchanged settings/chapter selections using `python3 tools/panel_epub_diff.py old.epub new.epub -o diff.txt`. This reports changed output frame ranges but **cannot** prove pixel completeness.
5. Inspect the private ZIP's `pages/*.json` for `sourceOrdinal`, `detectorRegionsThumbnail`, `sourceRegionsWithPadding`, `preOverviewSlicesSource`, `outputSlicesSource`, `outputRotated`, `events`, and **`detectorPreserveSingleFrame`** (optional; absent in older diagnostic archives). Frame indices drift when split counts change; compare by original source ordinal, not by new frame number alone. Check that any guarded footer merge maps to **one full-source output**, not a clipped/duplicated screen.
6. Open the export in Folio Nooir on the X4 and verify legibility, panel order, and visible boundaries. The X4 firmware is not part of this PR.

## Release gate

Do **not** merge PR #57 or publish an official v1.0.17 release on CI-only evidence. Green CI proves the programmed invariants and emulator smoke, **not** real manga crop quality. Physical/source-paired acceptance remains required. Preserve official signed Android v1.0.16, Windows draft PR #53 and Folio Nooir.
