# MihonNooir v1.0.17 — PR #57 hands-on panel QA

**Status:** unreleased developer test only. **Verified build commit:** `e07945dd9030deec97528f359b6ab12f36480252`; [successful full CI run #41395512040](https://github.com/toshio2011/mihon-nooir/actions/runs/41395512040), covering format tests, FOSS Android APK build, identity/signature checks, and Android emulator opt-in diagnostic-ZIP smoke test.

## Obtain the exact QA build

- [GitHub Actions APK artifact: mihon-nooir-foss-apk](https://github.com/toshio2011/mihon-nooir/actions/runs/41395512040/artifacts/13119926327). This is a ZIP containing ABI-specific APKs, a universal APK, and checksums. Download using the signed-in GitHub Actions artifacts interface; artifact access/expiration follow GitHub policy.
- **Safety / signing:** CI verifies the APK as **Android debug-signed** (certificate SHA-256 `bdf916db22e2c4ee0f3026273209d292db0fbc494739bf34e81a80d81d53e5b5`), **not** the official signed v1.0.16 release key. It uses the existing FOSS application ID `dev.nooir.mihontrial.foss`. It is **not an in-place update** for a release-signed official installation of that ID, and is **not a separately installable QA flavor**. Prefer an **Android emulator or spare phone**. Do not uninstall the official installed app or risk its app data merely to test this artifact.
- CI source head is the exact PR #57 commit above. A later docs-only head must not be confused with the verified QA binary.

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
| One Piece previous exported EPUB pages around 239–248, 273/276, 373 | Re-export **original source pages** and enable panel diagnostics | Determine whether the source had separate TOC/credit pages before calling frames over-split; avoid deleting legitimate sparse publisher material |
| Row by Row and WEBTOON | Try RTL/LTR and long vertical source | Row by Row does not cut vertical columns; webtoon reads top-to-bottom. No unintended panel cuts |
| Color EPUB 2/3, XTC, XTCH and CBZ | Small sample of same chapter | Check orientation, page order, correct format and actual XTEINK X4 readability |

### Suggested test steps

1. On a **separate test environment**, export a small Conan chapter using `More conversion settings`. Choose X4 480×800, `PANEL_BY_PANEL`, `PAGED`, `AUTO_FIT`, RTL, white-margin crop, overlap 8%, and **Full Page Before Splits** if whole-page comparisons help.
2. Turn on **Panel Diagnostics** only for the small sample. Keep the resulting private ZIP for source-index/region matching; this is not part of the public manga export.
3. Export an EPUB copy. Repeat with overlap 0% (to identify true padding changes); optionally repeat Row by Row. Keep all output names distinct.
4. Compare unchanged settings/chapter selections using `python3 tools/panel_epub_diff.py old.epub new.epub -o diff.txt`. This reports changed output frame ranges but **cannot** prove pixel completeness.
5. Inspect the private ZIP's `pages/*.json` for `sourceOrdinal`, `detectorRegionsThumbnail`, `sourceRegionsWithPadding`, `preOverviewSlicesSource`, `outputSlicesSource`, `outputRotated`, and `events`. Frame indices drift when split counts change; compare by original source ordinal, not by new frame number alone.
6. Open the export in Folio Nooir on the X4 and verify legibility, panel order, and visible boundaries. The X4 firmware is not part of this PR.

## Release gate

Do **not** merge PR #57 or publish an official v1.0.17 release on CI-only evidence. Green CI proves the programmed invariants and emulator smoke, **not** real manga crop quality. Physical/source-paired acceptance remains required. Preserve official signed Android v1.0.16, Windows draft PR #53 and Folio Nooir.
