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
