# MihonNooir — branding and legacy identifier guide

**Current product name: MihonNooir** (one word, both N letters capitalized). The GitHub repository remains [`toshio2011/mihon-nooir`](https://github.com/toshio2011/mihon-nooir), and the Android package ID remains `dev.nooir.mihon.foss`.

## Names to use in new user-facing writing

- **MihonNooir:** the unofficial Android manga reader and e-reader companion; also the name used for the in-development Windows sibling.
- **Folio Nooir:** the separate XTEINK firmware that reads prepared books. Keep these project names distinct.
- **Mihon:** the original upstream Android manga reader on which MihonNooir is based. MihonNooir is independent and is **not** an official Mihon release.
- **Nooir Tools:** the actual Android menu heading; keep the on-screen UI wording in walkthrough steps.
- **MangaNooir:** **former** project name, appropriate only when documenting historical releases or legacy technical artifacts. Do not use it as the current application label.

## Preserve existing technical identifiers until reviewed

Some older names are intentionally *not* mass-renamed because they may be referenced by scripts, compiled entry points, tests, stored preferences, or fixtures. Examples:

- `dev.nooir.desktop.ui.MangaNooirDesktop` is still the Windows JVM launch class in the preview workflow.
- `MangaNooir_*.epub`, `MangaNooir_*.xtc`, `MangaNooir_*.xtch` and `manganooir-readable-reader-interop-fixtures` are historical test file/artifact identifiers.
- `manganooir-cli.jar` and `manganooir-suwayomi.jar` are filenames in developer CLI examples; change each build command and consumer together if they are ever migrated.
- Internal Android preference paths, WorkManager unique-work names, storage directories and historical release tags must **not** change merely for branding.

Do not rewrite dated handovers, historical v1.0.11/v1.0.12 releases, repository links, or actual protocol/file-format bytes just to remove the old name. Put a current-brand notice above historical material when needed.

## Version and safety checklist

Before describing the **latest release**, verify the published [GitHub releases](https://github.com/toshio2011/mihon-nooir/releases); don't infer it from `config/version.txt` or an unmerged PR. As of **30 October 2026**, the official Android release is [**v1.0.16**](https://github.com/toshio2011/mihon-nooir/releases/tag/v1.0.16), signed with a persistent key. The earlier v1.0.12 APKs used different signing, so backups are important before changing installations.

**Windows remains unreleased**, with isolated development on [draft PR #53](https://github.com/toshio2011/mihon-nooir/pull/53). Avoid promising an installer, live Suwayomi source support, or hardware compatibility without end-to-end verification. New documentation should distinguish implemented code, passing synthetic tests, and physically tested behavior.

This guide is a documentation policy only; it authorizes **no** Android source, package, version, keystore, workflow, firmware, or release changes.
