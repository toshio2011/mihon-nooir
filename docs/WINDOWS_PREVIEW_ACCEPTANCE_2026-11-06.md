# MihonNooir Windows — physical preview acceptance checklist

**Status:** draft full-product Windows preview. This file is a QA plan, not a claim of completed tests.

The current feature branch is `feature/windows-full-v1.0.16` ([draft PR #53](https://github.com/toshio2011/mihon-nooir/pull/53)). The Android source in the desktop branch is older than official Android v1.0.17; do not merge or overwrite `main`'s panel converter/Android source until reconciled against the current green Android build.

## Getting the preview (developer/QA only)

Use a **successful** run of [Build MihonNooir Windows desktop preview](https://github.com/toshio2011/mihon-nooir/actions/workflows/desktop-preview.yml), download `mihonnooir-windows-preview-unsigned` in its artifacts, and compare its `.zip.sha256` with the ZIP before extracting. Extract to a new folder and launch `MihonNooir-Preview.exe`. It includes a Java runtime; a separate Java installation should not be required.

This is **unsigned development software**, not an endorsed Windows installer or finished production release. Never bypass Windows security warnings, weaken antivirus or disable SmartScreen to run it. If Windows blocks an untrusted preview, stop the manual test and report it. Use legally owned or original content. No signed Windows installer is being released by this workflow.

## User-visible acceptance paths

- [ ] **Cold launch without internet/server:** Windows opens; the app displays offline-ready status and does not start Suwayomi, install extensions or send requests without interaction.
- [ ] **Convert local folder / CBZ:** select an original test image folder or CBZ, choose the target X4 profile and EPUB 2, and save to a new destination. Verify the resulting EPUB opens and preserves reading order. Repeat with EPUB 3, XTC, XTCH and CBZ. Existing output filenames must not be silently overwritten.
- [ ] **Conversion settings:** compare Panel by Panel, Row by Row (horizontal rows only), Auto Fit, RTL/LTR, full-page overview and first-page preservation on the same original fixture as Android. Verify no lost speech bubbles or artwork and no tiny empty viewports. Current Android v1.0.17 pixel parity is **not yet proven**.
- [ ] **Read local comic:** use `Read local comic` and `Local library`. Test page turns, a two-page spread, webtoon/continuous mode, zoom, page jump, a bookmark (Ctrl+B), fullscreen (F11/Esc) and saved position after app restart. Try a CBZ with many pages, large pages and a corrupt image. No freeze or infinite loading.
- [ ] **Offline library:** add one CBZ/image folder, close/reopen, search by title, view cover, Continue Reading and remove an entry. Removing a library entry should not delete the source files.
- [ ] **Optional localhost backend:** only after explicit approval, use `Download & start Suwayomi` for the verified pinned `v2.4.2366` server. Verify the loopback URL; no automatic extension/source installation. `Stop managed server` must stop only a server started by the app. After stopping, local reading must still work.
- [ ] **Source browsing:** use `Connect / reload sources`, browse/search a trusted user-selected source and inspect manga/chapter metadata. A blank installation with no trusted sources should explain the empty state rather than silently acquire extensions.
- [ ] **Download approval and recovery:** `Queue missing chapter downloads`, `Download requests` and `Offline jobs` must distinguish recorded jobs, submissions to Suwayomi and actual local CBZ copies. Confirm 20-request approval limits, retry, restart recovery and accurate progress; stopping monitoring must not claim to cancel remote downloads.
- [ ] **Reader-to-export journey:** download an authorized chapter, save it offline as CBZ, restart offline, Continue Reading, then convert the saved CBZ to EPUB 2/XTC without initiating new network requests.
- [ ] **Windows ergonomics:** verify 100%, 125% and 150% scaling, keyboard access, dialogs, small screens, cancel buttons, text clipping, and independent preview/reader sizing.

## Automated evidence available

- [x] Kotlin/JVM converter and mock Suwayomi CI completed green, plus the Windows portable ZIP creation and bundled-Java EPUB 2 smoke in [run 42143443109](https://github.com/toshio2011/mihon-nooir/actions/runs/42143443109).
- [ ] Actual pinned Suwayomi server startup, real GraphQL request and owned shutdown on Windows (dedicated real-server CI job under active development).
- [ ] Actual on-screen GUI clicks on a physical Windows desktop.
- [ ] Updated Android v1.0.17 panel behavior reconciled without replacing newer Android code.
- [ ] Official installer/portable distribution, signing approach and production hardening.

Record the Windows version, display scaling, exact test input type/size (not copyrighted pages), expected versus actual result, errors and any reproducible clicks. Keep personal manga files, diagnostic ZIPs and credentials out of public issues/artifacts.
