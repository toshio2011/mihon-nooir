# Windows native viewer acceptance — 17 November 2026

Baseline: public Android v1.0.18, commit `d6d12701427d5339859c2cd0201e12db5c6810e4`. Windows source already integrated through #85/#86, so continue from main, not draft #81/#84.

## Current verified baseline

[Windows workflow #43885194685](https://github.com/toshio2011/mihon-nooir/actions/runs/43885194685) passed JVM/live Linux Swing viewer, native Windows app-image all-five-format conversion, and managed Suwayomi live handshake. Its [unsigned portable preview ZIP](https://github.com/toshio2011/mihon-nooir/actions/runs/43885194685/artifacts/14235037143) is an expiring developer test artifact, not a signed installer.

## This draft

- Strict Windows-only changed-path gate for `windows/*` pull requests: Android/shared overlay, tools, version config, and Android build/signing/publication paths cannot change.
- Compile a standalone acceptance test against the **production desktop JAR** with Kotlin friend paths; the test JAR stays separate from the distributable portable app.
- Run the real X3/X4 XTC/XTCH viewer acceptance against the **bundled Windows JRE and production app JAR** at requested Java2D UI scales 1.0, 1.25 and 1.5. Verify the actual reported screen transform and refuse to silently claim a scale that wasn't active.
- Verify fit/native dimensions, resize, rapid Previous/Next, page bounds, corrupt-file dialog, close and unchanged input bytes. Keep existing Xvfb and host coverage.

This strengthens automated desktop QA, not physical Windows acceptance. Hosted Windows runners may lack a display or effective scale overrides; the job deliberately fails if prerequisites are not met. No fake passes, production installer, signing, or release. Remaining: real-machine library/offline/download journey and manual DPI/keyboard/file picker checks; Windows PDF/EPUB import; direct device transfer; installer/update review.

Do not merge this draft or publish Windows until reviewed. Public Android v1.0.18 release and Folio Nooir firmware stay unchanged.

## Follow-up: small-screen library and reader

The preceding exact-head Windows run [#44049702682](https://github.com/toshio2011/mihon-nooir/actions/runs/44049702682) passed all jobs. Logs recorded actual Java2D transforms 1.0, 1.25, 1.5 on Windows Server 2025, each with full X3/X4 saved-book viewer acceptance. [Unsigned verified preview artifact](https://github.com/toshio2011/mihon-nooir/actions/runs/44049702682/artifacts/14314390044), expiry December 1, 2026.

Next change caps initial library/reader frames to the available logical work area and keeps complete toolbar rows horizontally scrollable rather than clipping actions. New actual Swing offline-library acceptance seeds a four-page synthetic CBZ in an empty opt-in isolated CI root, checks search/cover, reads and bookmarks page two, closes/reopens library and reader, verifies persisted page/entry/bookmark and unchanged source bytes, checks window bounds and scrollable controls. Native file chooser, cross-process re-launch, physical DPI and other screens are still manual/open. New exact-head tests are pending; prior green results do not prove this follow-up.

## Follow-up: portable-preview entry point (18 November 2026)

The library/reader change at `e45e5177d75aeef92a487e643b17022aca3ab424` was verified on [all-green Windows CI #44052377690](https://github.com/toshio2011/mihon-nooir/actions/runs/44052377690) at actual 1.0, 1.25 and 1.5 Java2D transforms. Each run logged the offline library filter, cover, page turn, bookmark, close/reopen and source immutability pass. [Verified unsigned portable archive](https://github.com/toshio2011/mihon-nooir/actions/runs/44052377690/artifacts/14314174502), expires Dec 1. Android regression [#44052377701](https://github.com/toshio2011/mihon-nooir/actions/runs/44052377701) passed the build and panel instrumentation; long-PDF job skipped by existing branch policy.

Next: main startup fits the available screen, offline tools remain at top, and compact initial widths use Sources/Browse/Chapters tabs; wide initial widths retain split panes. The compact choice is at startup, not dynamic at resize. Scrollable action rows stay available at reduced widths. New GUI acceptance launches the production entry point/look-and-feel, checks main navigation, opens the actual library button, then runs the existing offline journey. Also exercises the real packaged EXE launch/normal close on Windows. New CI not yet verified. No release, installer, automatic updates or real-source acceptance yet.

## 18 Nov follow-up: local conversion queue and launch gate

Previous head `bb6ce95ff320a36df3ac3c5a26bbea59b51e3bfd` passed JVM and Android build/panel checks, but the real Windows EXE window-detection step timed out; no release or new portable artifact. Replace the .NET MainWindowHandle heuristic with OS enumeration of visible exact-title windows owned by the launched process or direct child, and normal WM_CLOSE; log diagnostics and fail closed if no actual window. This is an attempted fix, not yet proof that launcher is working.

Add persistent **Conversion jobs** for local folders/CBZ/ZIP with multi-selection, shared conversion settings token, output-directory naming, explicit run/recovery, runner file lease, atomic index, pause after current job, retry only if output absent, cancel records and clear finished metadata without deleting files. No background auto-start, no source downloads and no PDF/EPUB input yet. Headless actual conversion test exercises restart/index reload, pause, existing output conflict, retry, binary output and original-output immutability. All new CI pending. Windows only; no Android release changes.
