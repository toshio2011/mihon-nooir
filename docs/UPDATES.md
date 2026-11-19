# Update policy

## Keeping the app up to date

[Official Android v1.0.19](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.19) was published 19 November 2026. It shares the persistent signing certificate and application ID `dev.nooir.mihontrial.foss` with official v1.0.18. All five RC2 APKs were verified, an Android 16 emulator upgraded in place from official v1.0.18 with the same package UID, and the owner approved RC2 visual output on the phone. Publication promoted those exact verified APKs, not a rebuild. Not every device or library migration has been tested.

**Upgrading from official v1.0.18:** back up first; use the architecture-aware in-app updater or manually install the matching APK over the existing app.

**Upgrading from official v1.0.17:** back up library and important downloads, then download the correct APK manually. The previous updater could choose the wrong CPU architecture. Most modern phones use [ARM64](https://github.com/toshio2011/mihon-nooir/releases/download/v1.0.19/app-arm64-v8a-foss.apk); use the [universal APK](https://github.com/toshio2011/mihon-nooir/releases/download/v1.0.19/app-universal-foss.apk) if uncertain. Verify against the release checksums. Install over the existing app; never uninstall/clear data to resolve an unexpected signature conflict. Older test-signed APKs (including v1.0.12) may use a different key.

Since v1.0.18, the app directs the in-app update checker to this repository, selects an architecture-compatible official FOSS APK, and opens the official download in a browser with Android's normal installation approval. Do not install CI/debug or separate QA artifacts over the official app.

See [installation](GETTING_STARTED.md) and [v1.0.19 release verification](releases/v1.0.19.md).

### Contributor policy

Keep the independent numeric version in `config/version.txt`. Publish official Android `vX.Y.Z` release tags with the five FOSS APKs and SHA-256 checksums, using the established persistent key. Repository secrets `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` must never be committed. Verify signing and in-place upgrade before owner-approved publication. For v1.0.19, [signed verification](https://github.com/toshio2011/mihon-nooir/actions/runs/44450814868) and [artifact promotion](https://github.com/toshio2011/mihon-nooir/actions/runs/44456570394) are separate; publish only the verified artifact and check all uploaded assets before making the release public. Never automatically publish just because upstream Mihon changes. Windows uses a separate release track and remains unreleased.

## Keeping up with upstream Mihon

`.github/workflows/watch-mihon.yml` runs daily (and manually) to check Mihon's default branch against the pinned commit in `config/mihon-upstream.sha`. It opens or updates one issue when commits differ.

Integration upgrades: compare commits → patch code on a branch → run tests/build → verify conversion and transfer → update SHA in reviewed PR → release stable-signed APK under new fork version. This prevents fast Mihon upstream changes from breaking Nooir-specific code.

## Formats and metadata

- XTC/XTCH: portable native title/author, chapters and image pages. Normal exports omit the private JSON trailer by default; the optional extended-metadata setting can add it for compatible experimental workflows. Folio Nooir does not consume a standard in-file XTC/XTCH synopsis; a future optional sidecar would need reader support.
- EPUB 2 Classic: OPF 2.0, NCX chapters and traditional `meta name=cover` pointing to a real image.
- EPUB 3 Modern: preserved OPF 3.0 / nav XHTML exporter with book metadata.
- CBZ: ComicInfo.xml fields and chapter bookmarks.
- Download originals intact so the user can later change export format without downloading again.
