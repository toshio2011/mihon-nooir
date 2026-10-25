# MangaNooir v1.0.13 — Android panel-export test candidate

This is **one existing MihonNooir app**, not a separately installed test edition.

- Application ID: `dev.nooir.mihon.foss` (unchanged from v1.0.12).
- Android launcher label: **MihonNooir** (renamed from MangaNooir; package identity remains unchanged).
- Candidate version: **1.0.13**; not yet tagged or published.
- Source: stacked development PRs #30 → #31 → #32, with Panel by Panel (automatic) and optional Auto Fit.
- Original Mihon library and other app sources are not modified. Folio Nooir is not modified.

## Why the previously downloaded APK may not install

The previous test build used the same application ID as v1.0.12, but may have been signed with a different ephemeral CI certificate. An *update* over an installed APK requires the same signing certificate; however, **a Google Play Protect / Android dangerous-app block is a separate security result** and cannot be diagnosed from CI signatures alone.

Do not disable Play Protect or attempt to bypass an explicit harmful-app block. Capture the exact text/screenshot; determine whether the device is warning that the developer is unknown, requesting a scan, declaring the APK harmful, or rejecting it for other reasons. Google publishes developer-specific guidance at https://developers.google.com/android/play-protect/warning-dev-guidance and supports appeals when an application is incorrectly flagged.

CI now checks each Android APK's archive integrity, signing validity, package ID, launcher label, and version; it publishes SHA-256 checksums with the artifacts. **A technically valid APK may still be blocked by Play Protect**, so no installation or safety outcome is promised until physical testing.

## Single-app test workflow

1. Choose the latest **successful** PR #32 `Build Mihon Nooir debug APK` run. Discard the old run #39918193761, which has version 1.0.12 and lacks the new artifact checks.
2. Download the `mihon-nooir-foss-apk` artifact, extract ZIP, select `arm64-v8a` or `universal` APK. Verify the SHA-256 digest against the included file.
3. **Back up your existing app**, using Mihon's backup feature; backup archives generally don't contain downloaded pages. Do not uninstall solely to dismiss a dangerous-app block.
4. When normal, safe installation is accepted, open **MihonNooir → manga → Export → Conversion settings**, enable **Panel by Panel (automatic)** and optionally **Auto Fit**.
5. Start with one legally accessible downloaded chapter, export to XTC/XTCH/EPUB2/EPUB3/CBZ, compare page order and output counts, then test X3/X4 readability. In difficult pages, conservative whole-page fallback is expected.
6. After a successful Android installation, representative real manga tests, X3/X4 reading, and signing/release verification, evaluate merging PR #30 then #31 then #32 and tag `v1.0.13`. Until then **no stable release is claimed**.

## Remaining development gaps

- Real manga panel coverage, irregular balloons/borderless pages, large-batch page count/size preflight and splitting.
- Android/desktop bitmap parity and physical X3/X4 tests.
- Persistent signing identity for reliable in-place upgrades. Do not publish a stable release with an untracked ephemeral signing key.
- Play Protect / other Android security blocking cause (needs exact warning and device-side observations).

The launcher is renamed to MihonNooir while keeping the original package identity; changing the visible name alone does not create a new Android app.
