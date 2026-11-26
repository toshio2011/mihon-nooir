# Android ↔ Desktop backup migration — development stage 1

Read-only Backup & restore → Inspect backup previews a gzip protobuf backup locally, including library count, chapter read/bookmark/page positions, history, sources and extension repository records. No private backup is checked in. The user may open the local Suwayomi WebUI for its existing import/export tools; native restore/export is not yet implemented.

Next acceptance: pinned Suwayomi restore/export using synthetic source and signed IDs in a disposable server root; Android import of desktop export with category order/ID mapping verified; missing source matching; explicit trusted repository review including signing identity before registration and compatible extension installation. Never fetch a repository solely because a backup names it. No downloaded pages, source credentials or Nooir preferences are assumed to migrate. Android production unchanged.

## Stage 2 — native transport (pending CI)

Create library backup and Restore library now use the pinned localhost GraphQL API. Import validates before mutation and requires a separate recovery backup. Explicit flags exclude server settings and client metadata. Restore waits for explicit server status; unknown/failure can mean partial changes. No automatic retries, repository installs, or claims of complete Android compatibility. Existing WebUI remains available. Real-server round-trip, category mapping, empty-library export and extension store migration still need acceptance.

## Stage 3 — portable extension metadata and source review (pending CI)

Explicit portable backup includes configured extension stores using Mihon's field 106 with index URL, name, badge, signing identity, contacts, legacy flag and extension list URL. This is not an APK backup. Native restore still uses library-only flags; review missing extensions compares exact signed source IDs with cached catalog and configured store signing identity. No automatic refresh, repository registration or extension installation yet. Missing catalog source IDs are unresolved, not inferred from names. A matched stored signing key is not independent proof of trust. Actual Android import and real backend catalog acceptance remain release gates.

## Stage 4 — real pinned-server acceptance (pending CI)

The existing packaged real-server smoke now also validates a wholly synthetic Android-format backup, saves a recovery copy, restores it, checks the actual library, exports and checks chapter/read/bookmark/page/history counts, then repeats the import to detect duplicates. It uses a deliberately missing signed source ID and a distinct category ID/order. Server SUCCESS alone is insufficient: Suwayomi can log per-manga errors while completing the overall operation. Android device restore, exact category membership/value round-trip, extension installs and local-reader progress write-back remain open.

## Stage 5 — explicit reader progress write-back (pending CI)

A successfully displayed online page can be saved explicitly to the local server before export. Local resume remains automatic. Fresh chapter identity/page-count check refuses known rewind or existing server conflict; simultaneous external writers are not atomic. No automatic chapter-read flag or bookmark mutation. Suwayomi may notify configured sync services. Real-server acceptance now checks a saved page and subsequent backup. Android restore and extension installation remain open.

## Stage 6 — reviewed extension recovery (pending CI)

Review missing extensions now offers one-at-a-time installation for unambiguous exact source-ID matches from an already configured repository with matching stored signing identity. Before mutation, the complete approved package/store/key/source mapping is rechecked. After installation, the backend catalog and validation must confirm the requested source IDs. No automatic retry, repository registration, refresh, fuzzy matching, or external extension execution in CI. Matching signing identity is not independent trust; the user must approve the package. Real third-party compatibility and actual Android restore remain open. Missing repositories are handled manually in the local WebUI.

## Stage 7 — local legacy repository index fallback (pending CI)

Pinned Suwayomi's ExtensionsList records available packages without necessarily populating source IDs for never-installed packages. Native Review downloaded repo index accepts an explicitly user-selected legacy index.min.json, never fetches its URL, and enriches missing catalog mappings only for a configured matching legacy store and exact package/version. The approved version is rechecked before/after installation; source IDs remain exact signed 64-bit values. Local JSON is not cryptographically verified; user must obtain it from a trusted repository. Modern v2 repository indexes, automatic repository registration, actual third-party install compatibility and Android device round-trip remain open. No source-name guessing or Android production changes.
