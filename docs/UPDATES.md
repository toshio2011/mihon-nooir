# Update policy

## Keeping the app up to date

The [v1.0.11 prerelease](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.11) is publicly available. The [v1.0.12 format/device changes](https://github.com/toshio2011/mihon-nooir/pull/21) are still an **unreleased draft** and have not been merged or tagged. Android experimental signing keys may differ, so back up MangaNooir before replacing older test builds.


The fork will reuse Mihon's existing in-app update checker and installer, but its APK releases must come from **toshio2011/mihon-nooir**, not upstream Mihon. The fork's package ID is `dev.nooir.mihon.foss`. Android always requires matching signing certificates to install an update over an earlier APK.

The integrated build must redirect `AppUpdateChecker.GITHUB_REPO` to this fork, enable updater through `-Pdist=foss`, and use numeric three-part independent versions from `config/version.txt`. Its GitHub releases must publish `vX.Y.Z` with a `-foss.apk` asset so the existing Mihon release parser can discover the download.

A *stable* release will need these repository secrets configured in **Settings → Secrets and variables → Actions**: `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Do not commit them. Debug CI APKs signed with different ephemeral keys cannot replace earlier installs.

No stable release workflow should silently auto-publish when upstream Mihon changes, and app updates should require user approval on Android.

## Keeping up with upstream Mihon

`.github/workflows/watch-mihon.yml` runs daily (and manually) to check Mihon's default branch against the pinned commit in `config/mihon-upstream.sha`. It opens or updates one issue when commits differ.

Integration upgrades: compare commits → patch code on a branch → run tests/build → verify conversion and transfer → update SHA in reviewed PR → release stable-signed APK under new fork version. This prevents fast Mihon upstream changes from breaking Nooir-specific code.

## Formats and metadata

- XTC/XTCH: portable native title/author, chapters and image pages. The v1.0.12 writer no longer adds the private JSON trailer. Folio Nooir does not consume a standard in-file XTC/XTCH synopsis; a future optional sidecar would need reader support.
- EPUB 2 Classic: OPF 2.0, NCX chapters and traditional `meta name=cover` pointing to a real image.
- EPUB 3 Modern: preserved OPF 3.0 / nav XHTML exporter with book metadata.
- CBZ: ComicInfo.xml fields and chapter bookmarks.
- Download originals intact so the user can later change export format without downloading again.
