# Android Combined Experimental QA — 24 November 2026

This is an **isolated diagnostic integration branch** combining two already
independent draft feature tracks in one Android build. It is **not** a merge
of their pull requests, a candidate update of the official persistent-signed
app, or a public release.

- **Web → EPUB:** PR #110 at `936ce590c29105397db984eac35b258087261d63`
- **Universal Converter / EPUB → XTC/XTCH:** PR #111 at `9fb498e1a1c26056cfddc75d8b73be14a93e5c4c`
- 43 nonoverlapping Web-to-EPUB paths were copied by exact Git blob SHA.
- Four shared build/test files were combined by preserving both Android
  instrumentation suites, both screenshot scripts, both groups of R8 keep
  rules, and all host regressions. No Windows, firmware or official release
  sources are intentionally modified.
- CI must build one side-by-side diagnostic APK (package
  `dev.nooir.mihontrial.foss`), then run **18 Android EPUB→XTC/XTCH tests,
  six Web TOC tests, two Web EPUB output tests**, and the existing five
  panel/CBZ diagnostic tests. Real XTCH first/next-page and web editor UI
  captures are separate visible acceptance evidence.
- An APK passing these tests is still debug-signed and **must never** replace
  an installed official MihonNooir app. No auto-release or merge is allowed.
- Remaining acceptance: picker → WorkManager → SAF tree-save → finished
  preview shortcut end-to-end, realistic large/illustrated CSS EPUBs, and
  real-world public chapter extraction with covers. Only the owner decides
  whether/when to create a stable-signed release.
