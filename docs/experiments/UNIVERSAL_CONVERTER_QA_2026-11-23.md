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
