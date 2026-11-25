# Android ↔ Desktop backup migration — development stage 1

Read-only Backup & restore → Inspect backup previews a gzip protobuf backup locally, including library count, chapter read/bookmark/page positions, history, sources and extension repository records. No private backup is checked in. The user may open the local Suwayomi WebUI for its existing import/export tools; native restore/export is not yet implemented.

Next acceptance: pinned Suwayomi restore/export using synthetic source and signed IDs in a disposable server root; Android import of desktop export with category order/ID mapping verified; missing source matching; explicit trusted repository review including signing identity before registration and compatible extension installation. Never fetch a repository solely because a backup names it. No downloaded pages, source credentials or Nooir preferences are assumed to migrate. Android production unchanged.
