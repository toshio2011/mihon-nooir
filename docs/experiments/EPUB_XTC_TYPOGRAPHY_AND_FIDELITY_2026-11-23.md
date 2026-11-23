# EPUB → XTC/XTCH quality & settings — 23 Nov 2026

> Experimental Android-only extension of draft PR #111. The public v1.0.19,
> Windows desktop, Folio Nooir and separate Web→EPUB PR #110 remain unchanged.
> No merge or release without explicit approval.

## Benchmark: x4converter.rho.sh (inspected 23 Nov 2026)

The reference converter exposes device presets, orientation, font family and
custom font, size, weight, text alignment, word spacing, hyphenation/language,
line height, margins, ignore-source-margins, quality mode, dithering strength,
negative pages, optional full-bleed cover, book/chapter progress bar fields,
chapter navigation, page preview and batch export.

This is a **feature benchmark**, not source reuse. The reference uses CREngine
for EPUB pagination, whereas our independent first-pass implementation uses
Jsoup + Android StaticLayout plus the existing Nooir XTC codec. UI parity
alone **will not** give equivalent EPUB CSS/layout fidelity. Do not copy
third-party prebuilt WASM, fonts or GPL-covered rendering code without a
specific provenance, rebuild and licensing review.

## First small implemented increment (still pending exact-head CI)

- EPUB-only quick typography presets: **Compact (20px, 14px margin, 110%)**,
  **Balanced (24px, 26px, 118%)**, and **Large (32px, 20px, 135%)**.
- Native Android font families: sans-serif, serif and monospace. These are
  system families; custom book/TTF/OTF fonts are not yet supported.
- User-adjustable line height **100–200%**, separate from existing font size,
  margin, X3/X4 output selection and RTL direction.
- Settings saved in the same shared preferences, transported through the
  existing durable WorkManager request, bounded/validated in the worker and
  passed to the real renderer used by **Convert & save** and **Convert & send**.
- Existing manga/CBZ and PDF engines, original-send, file filtering, collision
  checks, background queue and Wi-Fi transfer are not replaced.
- The Android instrumented test converts a synthetic book twice and asserts
  the final independently decoded XTCH prose page pixels differ when the
  typography options change. The smoke workflow requires this sixth test
  and publishes the styled decoded screenshot for visual inspection.

**Limits:** preset choice is a convenience UI over numbers, not a new rendering
engine. EPUB CSS, embedded fonts, tables/floats/MathML/SVG, linguistic
hyphenation, custom dither strength and progress bars remain outstanding.
A passing synthetic test is not a real-book/physical-reader acceptance.

## Second increment: semantic inline emphasis (exact-head CI pending)

- Preserve XHTML `<strong>`/`<b>`, `<em>`/`<i>` and `<cite>`
  during real EPUB text pagination, including nested bold+italic content.
- Keep heading structure and illustration order intact; split styled text
  safely around embedded images without dropping its subsequent formatting.
- Normalize source whitespace at text-node append time so Android spans keep
  stable offsets, rather than flattening the formatted string after parsing.
- Add synthetic XHTML style/order instrumentation and a second end-to-end
  comparison against **independently decoded X4 XTCH** bytes.
- Capture one new final decoded typography QA PNG.
- This is semantic inline styling only: arbitrary CSS, document fonts,
  tables, complex writing modes and footnote layout remain unsupported.

## Next quality increments, separately validated

1. **Renderer fidelity before UI bloat**: compare source EPUB page flow,
   source inline styles, headings, illustrations, captions, multi-column/table
   and fixed-layout cases; preserve mixed text/image order, detect unsupported
   CSS rather than silently misrepresenting output. Prototype more capable
   reflow rendering in isolation and review engine licensing before any
   integration. Never downgrade EPUB image handling to manga-only heuristics.
2. **Actual output preview**: use the existing shared XTC/XTCH decoder to show
   post-encoding cover, representative prose and illustration pages, including
   both 1-bit XTC and 2-bit XTCH. On-device full-resolution screenshots and
   explicit before/after settings comparison; no pre-encode-only preview.
3. **Image and quality controls**: meaningful fast/quality descriptions,
   inspect current quantizer/dither behavior before adding controls, optional
   negative pages with cover policy, sensible defaults for text and pictures.
   Verify visual gain, output size, RAM and time on X3 and X4.
4. **Advanced typography**: alignment/justification, heading styles, word
   spacing, language-aware shaping, documented safe hyphenation, validated
   local fonts, fallback glyph checks for Malay, Arabic and CJK; expose only
   controls that the chosen renderer actually honors.
5. **Progress and cover**: book / chapter percentages and page counts based
   on final pagination, chapter mark positions, optional footer/progress
   footprint reflected in reflow, and a full-bleed cover setting.
6. **Batch / saved presets**: reusable EPUB-only setting profiles, per-book
   overrides, sequential conversion with cancellation and disk-space guard.
   Keep source/output filtering and all non-EPUB tools as they are.

## Acceptance gates

- Actual EPUB2 + EPUB3, longer illustrated novels, Arabic RTL and CJK fonts,
  challenging CSS and image-only/fixed-layout EPUBs.
- Preview vs independently decoded final XTC/XTCH pixels match.
- No missing chapter images, blank pages, clipped lines, repeated content or
  incorrect 1-based chapter indices after the cover.
- X3 528×792 and X4 480×800 both validate; physical hardware test is separate
  from emulator-only evidence.
- XTC/XTCH output-size and conversion-time comparison before recommending
  default quality modes; test X3/Lite-related paths without touching firmware.
- Exact-head Android host checks, APK compile and instrumented emulator pass;
  UI screenshot review and physical e-reader acceptance still required.
