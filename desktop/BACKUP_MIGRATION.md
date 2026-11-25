# Android ↔ Desktop backup migration — development stage 1

Read-only Backup & restore → Inspect backup previews a gzip protobuf backup locally, including library count, chapter read/bookmark/page positions, history, sources and extension repository records. No private backup is checked in. The user may open the local Suwayomi WebUI for its existing import/export tools; native restore/export is not yet implemented.

Next acceptance: pinned Suwayomi restore/export using synthetic source and signed IDs in a disposable server root; Android import of desktop export with category order/ID mapping verified; missing source matching; explicit trusted repository review including signing identity before registration and compatible extension installation. Never fetch a repository solely because a backup names it. No downloaded pages, source credentials or Nooir preferences are assumed to migrate. Android production unchanged.

## Stage 2 — native transport (pending CI)

Create library backup and Restore library now use the pinned localhost GraphQL API. Import validates before mutation and requires a separate recovery backup. Explicit flags exclude server settings and client metadata. Restore waits for explicit server status; unknown/failure can mean partial changes. No automatic retries, repository installs, or claims of complete Android compatibility. Existing WebUI remains available. Real-server round-trip, category mapping, empty-library export and extension store migration still need acceptance.

## Stage 3 — portable extension metadata and source review (pending CI)

Explicit portable backup includes configured extension stores using Mihon's field 106 with index URL, name, badge, signing identity, contacts, legacy flag and extension list URL. This is not an APK backup. Native restore still uses library-only flags; review missing extensions compares exact signed source IDs with cached catalog and configured store signing identity. No automatic refresh, repository registration or extension installation yet. Missing catalog source IDs are unresolved, not inferred from names. A matched stored signing key is not independent proof of trust. Actual Android import and real backend catalog acceptance remain release gates.

## Stage 4 — reader progress and real-server acceptance (pending CI)

Explicit Save position to library writes the displayed online page to Suwayomi only after confirmation, refuses detected sync conflicts, retains local progress if the write cannot be confirmed, and checks current server position to avoid detected rewind. This is not automatic synchronization; the upstream mutation does not support compare-and-set. Read completion and local/offline bookmarks are separate. The isolated pinned-server smoke test now checks actual progress write, export with configured extension metadata, validation, recovery snapshot and library restoration. The actual Android restore, extension registration/install and fresh backup UI screenshots remain separate gates.
