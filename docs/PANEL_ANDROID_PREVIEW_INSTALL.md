# Android Panel by Panel — safe test APK installation

This document covers the **experimental PR #32 build**, **not** the stable MangaNooir v1.0.12 release.

## Why a preview APK may fail to install

Ordinary GitHub Actions APKs use a generated Android build signing key. A different CI run can use a different certificate. Android forbids overwriting an installed APK when the package ID is identical but certificates differ, and may show only **App not installed**. Do not uninstall an existing app or clear its data just to try a PR APK.

The stable FOSS package is `dev.nooir.mihon.foss`. To keep user's manga library safe, Android **pull-request** CI builds now use a distinct ID:

- PR #32 panel test: `dev.nooir.mihon.pr32.foss`
- Launcher label: **MihonNooir Panels**
- Other PRs: `dev.nooir.mihon.pr<PR_NUMBER>.foss`, each isolated
- `main` and stable release builds: **unchanged**; still `dev.nooir.mihon.foss` / **MangaNooir**

No manually configured passwords, shared private data, or signed release APK is needed for this CI test.

## Install on Android

1. Open the **latest successful** [PR #32 Android Actions run](https://github.com/toshio2011/mihon-nooir/actions/workflows/build-apk.yml?query=branch%3Afeature%2Fpanel-auto-fit-per-region). **Do not download run #39918193761:** it still uses the old colliding package ID.
2. Download the artifact named `mihon-nooir-foss-apk`, extract the ZIP, and choose the **arm64-v8a FOSS APK** for modern ARM64 phones (or **universal FOSS APK** if unsure).
3. Install the APK from Files. The launcher must read **MihonNooir Panels**. It can appear next to your existing **MangaNooir** without replacing it.
4. It has an independent, initially empty manga library and settings. Configure a trusted manga source or import/restore a Mihon backup deliberately, without deleting your original app.
5. Open a manga → Export → Conversion settings → enable **Panel by Panel (automatic)**. Optionally open image and layout controls → Orientation → **Auto Fit**.
6. Begin with one short chapter; choose the X3/X4 profile and XTCH, then check panel order, reading size, output file size and speech balloons on the physical device.

If installation still fails, capture the exact message and the APK filename, check free storage and ensure the downloaded ZIP was extracted. Verify Android allows installing from the chosen Files/browser app. Do not disable device-wide protections as a shortcut.

## Limitations

This build is experimental and not released. CI validates compilation and synthetic output, not a real-phone UI session, real manga panel coverage, or X3/X4 readability. It is not signed with a persistent stable signing key: subsequent runs of **the same PR** may still be incompatible in-place with one another. Back up test-app data before changing builds. Future stable signing requires a securely maintained release key and separately tested update path.

The preview script runs **only for pull-request builds**. Stable release identity and Folio Nooir firmware remain untouched.
