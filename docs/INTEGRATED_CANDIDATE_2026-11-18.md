# Integrated development candidate — 18 November 2026

No public Android or Windows release; main, current version and Mihon stable upstream pin remain unchanged. This integration is a draft based on Windows PR #96, not a merge to main.

## Verified inputs
- #94 `2c9a65523ed758428f3702bdaee9d38e29bbbb12`: [Windows](https://github.com/toshio2011/mihon-nooir/actions/runs/44226777891) all three jobs and [Android](https://github.com/toshio2011/mihon-nooir/actions/runs/44226777852) build plus all four emulator tests passed, including oversized-row toggle and Auto Fit on X3/X4 saved XTC/XTCH.
- #95 `a9f13050def14d0d9d3f4eab60898ffbdeac1869`: [Android retry](https://github.com/toshio2011/mihon-nooir/actions/runs/44225829801) succeeded with all three emulator tests after initial Maven Central rate limit. Copy its exact overlay backport and merge its exact fixture additions without losing #94's updated source assertions. Real-network and installed-extension checks remain manual.
- #96 `f50ab58c53b5ce7ac2b80b2f6010918548911d65`: [Windows](https://github.com/toshio2011/mihon-nooir/actions/runs/44228410387) all three jobs; native settings modal passed at 100/125/150%, actual library/bookmark/queue retained through synthetic MSI upgrade. [Android regression](https://github.com/toshio2011/mihon-nooir/actions/runs/44228410539) passed. [Prior verified unsigned Windows package](https://github.com/toshio2011/mihon-nooir/actions/runs/44228410387/artifacts/14393914135) expires Dec 2.
- General text EPUB import, exact PDF reflow parity, physical PC/reader Wi-Fi verification and code signing are not complete.

## Integrated acceptance
Trigger the existing long-PDF Android instrumentation for this exact branch in addition to the four panel tests, shared host/export checks and Windows jobs. Test the combined candidate rather than infer it passes from individual branches. Current settings UI stores accepted values in the app session; per-job queue tokens persist; global restart persistence still to be implemented/tested. No new version number or signing changes. No Folio Nooir changes. Real copyrighted manga not bundled; owner-side visual acceptance still pending. No publish/merge without owner approval.
