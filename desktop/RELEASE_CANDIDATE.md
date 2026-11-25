# Desktop integration candidate — 25 November 2026

This draft consolidates the complete stacked desktop development history through 34e936874f1b7f402b08217b5e9c75feec8aff8c, including the compact high-DPI web editor. The latest Android documentation-only main commit (4b6307961b2712d4b1c02e61190e26511135489f) is retained verbatim. The frozen Android release guard remains in force; no Android app, signing, firmware, release or publication changes.

## Verified evidence before this candidate

- Run 45563098681: all six native targets, portable conversion, real Suwayomi and offline web extraction passed.
- Run 45568094923: native macOS DMG and Linux DEB/RPM package payload checks passed.
- Run 45568367858: Windows x64/ARM64 and Linux x64/ARM64 user-state retention passed; macOS GUI failed on a nullable label in the test.
- Run 45570772806: JVM, Windows ARM64, Linux ARM64 and macOS ARM64 passed after UI/test improvements. Superseded jobs were canceled, not passes.
- Run 45572332745: high-DPI chapter viewport regression is pending at candidate creation.

## Required before publication

1. All eight jobs on this exact integrated commit must pass, including every native architecture, installer checks and real Suwayomi.
2. Inspect fresh screenshots, especially Windows 150% and compact macOS screens.
3. Verify a permitted live public web novel including Wattpad metadata and exported chapter text; offline fixture success does not establish live availability.
4. Decide release package policy for unsigned builds and macOS notarization, test physical machines and actual package-manager upgrades where supported. Windows ARM64 portable is the current intended delivery, not an MSI.
5. Review compatibility and update documentation, release names, checksums, and obtain explicit release approval.

No release artifacts are published by this branch. Existing stable Android release remains unchanged.
