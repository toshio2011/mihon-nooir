# MihonNooir cross-platform visual integration — 13 November 2026

**Experimental, unmerged and not released.** This branch is intentionally based on [draft Android/shared PR #83](https://github.com/toshio2011/mihon-nooir/pull/83), with Windows code staged from [draft desktop PR #84](https://github.com/toshio2011/mihon-nooir/pull/84). **None of their existing branch heads were altered**. This branch is a review/test bridge, not an instruction to merge the tracks or release an APK.

## User-requested scope

1. **Actual output inspection**: Android already had a **live conversion first-page image/CBZ preview**; that remains intact. A separate Android SAF dialog opens an already-saved XTC/XTCH binary. Windows now provides `Inspect XTC / XTCH output` in its toolbar, showing true saved pages and navigation. Both use **one canonical, bounds-checked, pure-JVM parser**: `overlay/app/src/main/java/dev/nooir/bridge/XtcBookPreview.kt`. The experimental temporary Windows duplicate `desktop/ui/XtcBookPreview.kt` is **NOT PRESENT in this integration branch**. The existing XTC writer is unchanged; the reader decodes one page at a time.
2. **Opt-in smarter panel framing**: the original panel detector's uncertain-page/bubble/sparse protection remains. `PanelContextPeek` only extends a confirmed region by a bounded small adjacent edge; default `0`, up to `8%`. Android and Windows desktop CLI both call the same helper; *no additional split*, no automatic data loss or overwrite, no intentional changes to row-only.
3. **More e-ink rendering choices with explanations**: Floyd–Steinberg (original), Atkinson (clean white / may lose fine gray), Bayer 8×8 (ordered dot texture / possible grid), Threshold (crisp lines / posterized gradients). Android `MangaConversionOptions` Compose UI includes descriptions and Windows `DesktopConversionDialog` includes a genuine selector with a description for each. Both Android `AndroidPageSource` and Windows `NooirDesktopCli` use the exact same `GrayscaleConverter.quantize`. Existing 7–20-field token settings remain backward compatible, with `LEGACY` mode leaving prior Floyd/threshold choice intact. Colored EPUB/CBZ exports are unchanged when original color is enabled; XTC=1-bit, XTCH=2-bit.
4. **No Folio Nooir firmware changes**: this project is MihonNooir only. No XTEINK device flashing, ESP-IDF, BLE, SPI, OTA, reader cache, or firmware branch changes.

## Branch and CI protection

- Android experiment PR #83 and Windows experiment PR #84 stay draft and independently tested. This cross-platform branch combines only the necessary Windows paths and the existing Android/shared experimental core; it **does not** modify Android source relative to PR #83.
- Its Windows workflow updates the protection baseline specifically for this draft integration PR to **the actual pull request base**. The check still refuses Android overlay, signing, `config/`, Android build workflows or `tests/run.sh` edits relative to that base. **Standalone Windows PR #81/#84 keep their original stronger main-based guard unchanged.**
- The integrated Windows builds compile the **canonical** `XtcBookPreview.kt` and `PanelContextPeek.kt` from the shared overlay. Before packaging, Windows host and UI-model tests run.
- `DesktopVisualRenderingPreviewTest` creates only original synthetic artwork with gradients, e-ink screentones, simulated speech bubble borders and faint text; it uses the real desktop conversion CLI to output **8 complete books** (XTC and XTCH × four rendering modes), decodes them again from disk, asserts native X4 dimensions and mode-dependent page pixels, and writes a labeled PNG comparison sheet and its source to the GitHub Actions artifact `mihonnooir-visual-four-modes-xtc-xtch` (if tests pass).
- Existing generated host-verified EPUB/XTC interoperability fixtures, Android panel ZIP emulator and signed Android APK safety checks are independent gates. An APK from ordinary PR CI remains **Android Debug signed** and **not safe to install over public v1.0.17**.
- Exact-head automated and physical results have **not been claimed yet** here. Do not merge/release on the strength of a synthetic sheet alone.

## Outstanding acceptance

- **Integration CI:** full Android FOSS host/diagnostic APK and panel tests, Windows build/JVM tests, portable app packaging and Suwayomi fixture. Observe the exact run for this integration branch, investigate any failure.
- **Human visual check:** compare source, two binary formats and four rendering modes on realistic gray gutters, diagonal borders, nonrectangular and overlapping panels, manga dialogue, dark backgrounds and RTL order. Generated synthetic comparison is a guide, not a pass/fail oracle for actual artwork. Reuse private legally held Conan/One Piece problem cases only after access is intentionally provided, without publishing copyrighted page art.
- **Android physical phone:** real SAF file selection, permission/cancellation, temporary-file cleanup, first/middle/last page, app lifecycle and bounded heap/free-space behavior. Do not uninstall public v1.0.17.
- **Windows physical machine:** actual GUI click-through, saved XTC/XTCH page navigation, rendering selectors and visual image scaling on a real monitor, no unnecessary client/server requests.
- **XTEINK:** compare output files on X3/X4 hardware when safely available, without any firmware modification.
- **Release order:** finish stable updater and PDF acceptances first; only then consider these visual features for a later Android/Windows milestone with explicit owner approval.

## Rollout constraints

**No merge to main, no public release, no installed app replacement, no signing changes.** Keep Android updater #80, Windows base #81, PDF #82, Android visual #83, Windows visual #84 unchanged; this integration branch is the only development output.
