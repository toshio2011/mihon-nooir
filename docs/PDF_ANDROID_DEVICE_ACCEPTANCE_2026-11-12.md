# PDF large/batch Android acceptance — device QA sheet

**Scope:** MihonNooir draft PDF PR #82 only (`feature/pdf-large-multi-input-v1.0.17`). This is a **manual test plan**, not an assertion of completed phone or e-reader testing.

## Release and installation safety gate

- **Do not uninstall published v1.0.17**, clear its data or replace its signing key. Preserve the existing library, backups and downloaded chapters.
- The FOSS APK produced by the pull-request GitHub Actions workflow is a **debug-signed diagnostic/emulator artifact**, **not** an in-place upgrade for the published app. On 12 November, the successful run 43188159593 explicitly reported `V2 Signer: certificate DN: C=US, O=Android, CN=Android Debug` on all five APK variants, even though they passed APK signature verification. Its package is `dev.nooir.mihontrial.foss`. **Do not install any of those APKs over the currently installed public stable-signed app**. `assembleFossAndroidTest` produces a separate test APK, not a user-facing release.
- A real in-place candidate must use the persistent stable-release certificate and must pass an explicit certificate fingerprint comparison with public v1.0.17 before installation, alongside a version/update and backup check. Do not silently reuse the PR workflow's diagnostic artifacts as an upgrade, and do not uninstall the existing app to work around signature conflicts.
- First require fully green exact-head CI for host, panel/ZIP, and Android PDF instrumentation, followed by an explicitly approved **correctly signed** device candidate or a verified isolated side-by-side test package. Never solve a package-conflict error by uninstalling the current app.
- Do not merge, release or publish an APK until the owner approves; public v1.0.17, Windows draft #81, updater draft #80, `main` and signing material remain unchanged.

## Functional matrix

Record **device/Android version/build SHA/source PDF type/pages/size/free storage/elapsed time/memory peak/output bytes/page count/page order/repro steps** for each run. Use synthetic or legally owned documents; never upload private PDFs into public issues.

| ID | Case | Expected outcome |
|---|---|---|
| S1 | Select one ordinary PDF; EPUB3 MIXED; use default title/author | Extractable text selectable, images preserved separately where possible; embedded PDF title/author used when present; output filename remains chosen name |
| S2 | Same PDF as EPUB2 and EPUB3 ORIGINAL_PAGES | All source pages retained and ordered; EPUB2/3 validate and open; no silent page cutoff |
| S3 | Three PDFs with identical/case-variant names and one Unicode filename; save to chosen folder | Individual collision-safe names; no overwrites, no silent SAF auto-rename; correct per-PDF EPUB metadata |
| S4 | Three PDFs converted in order; force one input to fail/deny permission after queueing | Failed item clearly reported; later queued items behave as intended; no incorrect success notification or published partial book |
| S5 | Small PDF to X3 and X4 XTC/XTCH; inspect on actual supported reader | Correct width/height, number and order of pages; no corrupted 1-bit/2-bit planes; verify Folio Nooir X4 physically and X3 where a device is available |
| S6 | PDF with scanned diagrams/blank pages and mixed illustrations/text | Complex content preserved by page-image fallback without dropping diagrams, while normal text remains reflowable |
| S7 | 2,000+ and 5,000-page **realistic** PDFs: text, scanned, mixed, vectors and varying page sizes | No arbitrary cutoff, orderly pages, bounded memory/scratch; real out-of-storage or genuine format limits reported explicitly |
| S8 | Local **Save to phone** output exceeding 180 MiB when enough storage is available | Phone save not blocked by XTEINK's independent 180 MiB *upload* advisory; no need to run an enormous input simply to validate policy |
| S9 | Send >180 MiB output to XTEINK over Wi-Fi | Clear transfer-only advisory; do not start or report successful transfer |
| S10 | Pause/close UI, rotate, background/reopen during a long batch | WorkManager serial queue remains observable; no duplicate concurrent PDFBox conversion or duplicate output |
| S11 | Restart app/device after jobs are queued, using persisted input and folder URI permissions | Jobs resume or fail explicitly if grants expire; no silent overwrites or misleading completion |
| S12 | Cancel during source copy, page conversion, single CreateDocument save and batch-folder save | Cancellation detected; single user-selected URI **never automatically deleted**; newly created incomplete batch output cleaned up; user informed a partial single file may remain |
| S13 | Force very low storage or storage-provider exception; retry after freeing space | Meaningful error, no unbounded memory growth, no completed-but-corrupt output treated as success; new jobs still work |
| S14 | Source PDF with password/encryption, corrupt PDF, or extremely large image | Explicit safe failure (password/OOM/space reason), no crash loop, source unchanged |
| S15 | Existing target filename in selected phone folder or XTEINK directory | Refuse overwrite even if case differs; never replace an existing book |

## Acceptance gates

1. **Automated:** exact-head full green CI. Synthetic emulator tests are useful but do **not** prove the manual tests above.
2. **Android device:** run S1–S4, S6–S8 and S10–S15 on the intended phone; collect redacted errors only. Give high priority to SAF provider behavior and cancellation/low storage.
3. **Reader:** verify native XTC/XTCH and EPUB output open/read, including first/middle/last page and page ordering on the intended XTEINK firmware; X3 support cannot be asserted from X4-only testing.
4. **Release:** user reviews results, signing/update path is validated without uninstalling the current app, and user explicitly approves PR merge + tagged release.

## Results template

```text
Date / device / Android:
Installed app version, package, signing compatibility:
Draft build commit and APK SHA-256:
Test ID / input PDF type / pages / input size:
Output format / device preset / output pages / bytes:
Free storage before/after; memory peak; elapsed time:
Result PASS / FAIL / BLOCKED:
First, middle and last output pages inspected:
SAF provider name (when relevant):
Cancellation/permission observations:
Redacted logs or reproducible steps:
```

**Not a release announcement.** No actual physical REDMAGIC or XTEINK acceptance results are implied by this document.
