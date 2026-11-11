# MihonNooir PDF track — continuation, 11 November 2026

This is a follow-up to [10 November all-tracks handover](HANDOVER_2026-11-10_ALL_TRACKS.md). The PDF-only work belongs in **draft PR #82**, not production Android `main`, the Windows PR #81 branch, or the updater PR #80 branch. **Never merge or release without owner approval.**

## Verified baseline

- At commit `dc4dd01ca510f53678197983778ba265aa15b4ce`, [CI 42947942973](https://github.com/toshio2011/mihon-nooir/actions/runs/42947942973) passed host format/EPUBCheck, minified Android FOSS build, panel/ZIP instrumentation, a 1,001-page original PDF conversion and five blank/vector MIXED pages.
- The first test for 257 selectable-text PDF pages at `c2baaba2a24107d95c47ee0fd3c04786f6b6f215` failed **before conversion** with `NoClassDefFoundError: PDFBoxResourceLoader`. The independently compiled instrumentation referred to PDFBox authoring APIs removed from the release-minified app APK; this was not a real 257-page conversion failure.
- At commit `cd64b6c31eef884eefbba5635ffbb36533a5b95b`, [CI 42966132662](https://github.com/toshio2011/mihon-nooir/actions/runs/42966132662) **passed fully**: FOSS app and test APK compilation, Android panel regression, and all **three** synthetic Android PDF instrumentation tests. The final log reports `OK (3 tests)` and preservation of **1,001 original pages**, **five mixed blank/vector pages**, and **257 ordered selectable-text pages**.

## Performance fixes already verified on Android

- Original-page mode no longer accesses indexed `PDDocument.getPage(index)` unnecessarily; MIXED mode iterates PDFBox's page tree in order.
- `PageCollector.processPages` now extracts only the already-selected page rather than traversing the entire PDF tree on every `getText(book)` call. The 257-page text-layer Android test verifies source markers remain selectable, ordered, and do not unexpectedly degrade into image fallback.
- This addresses a page-tree traversal scaling risk; it **does not establish measured 2,000/5,000-page speed or low-memory safety**, nor guarantee arbitrary PDF layouts map perfectly into reflowable XHTML.

## Follow-up local phone-save safety changes

Source prepared on staging `wip/pdf-save-copy-2026-11-11` (commits `51ac0daa0660c85c881e343f3a6c1ef440f7f220` and `bbd8b54220a3bc53845176096e4313a05e905d83`) for bringing onto PR #82 after the above baseline passes:

- `PdfSaveCopy.copy`: 32 KiB bounded copy, cancellation checks, expected byte-length validation, propagate provider errors and flush.
- Both **single CreateDocument Save to phone** and **multi-file SAF folder save** use this same helper. The single user-selected document URI is never automatically deleted; a newly created incomplete batch-folder output is still deleted on failure.
- Host tests cover exact copied bytes, mid-copy cancellation, short source, provider failure, and **streaming a synthetic 181 MiB output without large allocations**. A local isolated Kotlin/JVM smoke run passed these cases. The 180 MiB bound stays exclusively in XTEINK Wi-Fi transfer policy, not local Save to phone.
- This save change **has not yet passed full GitHub Android CI or physical device SAF-provider tests** when this note was written. Do not claim otherwise.

## Mandatory remaining acceptance

1. Run the complete exact-head CI after the phone-save changes land on draft PR #82: host, EPUBCheck, Android FOSS compile, panel regression, and all three Android PDF tests. Repair only evidence-backed failures.
2. Test real Android SAF providers: single file vs folder batch, duplicate/case/unicode names, low storage, output stream errors, cancel/restart, permission expiry, failure followed by a successful queued PDF. Never delete a user-selected existing URI or silently overwrite a file.
3. Measure **2,000 and 5,000 page** text, vector, scanned and mixed PDFs on an actual handset; validate memory/disk usage, output page/art order, EPUB2/3 and XTC/XTCH format bounds and genuine X4 open/read where available.
4. Keep local book conversion/save independent of XTEINK's per-upload 180 MiB safety advisory, and keep the real XTC/XTCH 65,535-page header bound and other codec/reader limits.
5. Keep the public Android v1.0.17 release assets, release certificate, `main`, Windows draft #81, and updater draft #80 untouched. **No merge, version bump, release or signing changes** without explicit user approval.

## Status links

- [PDF draft PR #82](https://github.com/toshio2011/mihon-nooir/pull/82)
- [Windows draft PR #81](https://github.com/toshio2011/mihon-nooir/pull/81)
- [Android updater draft PR #80](https://github.com/toshio2011/mihon-nooir/pull/80)
- [Stable Android v1.0.17](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.17)

## Afternoon follow-up — provider-backed Android and long mixed-page QA

- [CI 42968545819](https://github.com/toshio2011/mihon-nooir/actions/runs/42968545819) on `0712ca396a93489d5480acefe92ecfcbbe6a810e` **passed** complete host/FOSS build, panel ZIP smoke, and three PDF Android tests. Host log explicitly reported `PASS: full phone copy, cancellation, truncation, provider failure, and streaming >180 MiB`. This confirms the synthetic 181 MiB local phone copy lacks the separate XTEINK 180 MiB Wi-Fi advisory.
- [CI 42970980205](https://github.com/toshio2011/mihon-nooir/actions/runs/42970980205) on `a19e81a8a1a3c1731a7fff038c1737835f2f9fcc`: host/FOSS + panel passed; Android PDF instrumentation had **3 passes, 1 failure**. The sole failure was `NoSuchFieldError: PdfSaveCopy.INSTANCE` from R8 minification and independently compiled test APK before Android MediaStore provider assertions executed. Therefore do **not** classify this as provider I/O or cancellation failure.
- [b29fb44](https://github.com/toshio2011/mihon-nooir/commit/b29fb44c40fdac00b1a7559c29d42079a5fb3111) retains only the `PdfSaveCopy` singleton and methods in the minified FOSS test build; [CI 42973834553](https://github.com/toshio2011/mihon-nooir/actions/runs/42973834553) was **still in progress** as this note was updated. Only after `OK (4 tests)` and all jobs pass is the actual Android MediaStore provider copy/cancellation path validated.
- **Staged, not yet added to draft PR #82**: `wip/pdf-2001-text-qa-2026-11-11` contains a 2,001-page Android-generated PDF with selectable text and every source-page marker checked in EPUB3 order, plus a synthetic Android PDF combining a selectable paragraph and a separate embedded bitmap. The second checks real text stays reflowable and its illustration is separately embedded. The smoke script requires **six** Android PDF instrumentation cases. Neither new staged test has run or passed CI yet. The 2,001-page test logs elapsed conversion milliseconds but must **not** be represented as real REDMAGIC/5,000-page heavy-scanned-PDF performance.
- **Promotion gate**: wait until provider-test [CI 42973834553](https://github.com/toshio2011/mihon-nooir/actions/runs/42973834553) completes. If fully green, check PR #82's current head and ancestry, fast-forward this staging branch into PR #82, then inspect exact-head full CI. If not green, diagnose the actual provider failure first, without relaxing tests or touching the production v1.0.17 release.

## 11 November afternoon — six-test Android gate and metadata audit

- [CI 42973834553](https://github.com/toshio2011/mihon-nooir/actions/runs/42973834553) on `b29fb44c` **passed all jobs**, including `OK (4 tests)` from the real Android **MediaStore Downloads** provider-backend smoke. The original minification error is closed. This verifies synthetic files using the Android emulator's MediaStore, **not** SAF CreateDocument/OpenDocumentTree on a real phone.
- [CI 42977017460](https://github.com/toshio2011/mihon-nooir/actions/runs/42977017460) at `1fed26d` **passed all jobs**: FOSS build/host tests, panel ZIP smoke and `OK (6 tests)` Android PDF instrumentation. New cases preserve all **2,001 selectable-text pages in order**, and preserve mixed-page **selectable text + a separate embedded illustration**. These are synthetic emulator fixtures, not physical phone benchmarks. The instrumentation log does not expose an independently calibrated runtime/memory measurement, so do not claim a meaningful 2,001-page performance number.
- **UI metadata bug found**: the single-PDF picker filled optional `bookTitle` with the filename, overriding PDF's embedded `Info.Title` despite UI text saying "leave blank to use PDF metadata". Set default title override to blank; keep suggested **output filename** from the selected file. EPUB uses PDF metadata when available and falls back to the filename. Clarify that XTC/XTCH's current conversion uses filename-based titles and does not promise preservation of embedded PDF title/author. This change is limited to the draft Android PDF dialog; verify in the next exact-head CI.
- Pending **real device acceptance**: run a recent **working test APK installed by the owner without replacing public v1.0.17** only after confirming signature/update compatibility and the user explicitly agrees; convert small text/illustration, 2,000-page and 5,000-page varied PDFs in EPUB2/3 and XTC/XTCH. Measure actual free space, process memory, conversion duration, verified page/order/TOC; then exercise SAF single and batch saves, case/unicode duplicate names, low storage, cancellation, restart, permission denial, and non-overwrite. No automatic upload to XTEINK or real device flashing.
