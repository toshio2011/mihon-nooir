# Windows actual XTC/XTCH binary viewer — experimental, 13 November 2026

This is a **desktop-only** experiment off the existing Windows **draft PR #81**, not public release, updater or Android source.

- **Already existed:** Windows has offline CBZ/folder reader, bookmarks, cover thumbnails and conversion. The new viewer is **not** a second CBZ reader; it reads a finished XTC/XTCH file's **stored 1-bit or 2-bit binary pages**, at original resolution.
- **New UI:** `Inspect XTC / XTCH output` on the desktop toolbar opens a local file selector. The XTC/XTCH viewer parses and validates the book header, page entries, encoded page header, offset and expected image length, and decodes only the requested page using a SwingWorker. Navigation is previous/next; it does not send data to a reader or modify the file. Malformed files result in a visible error, not an export success.
- **New independent host QA:** a real `XtcCodec.write` output is reopened and the exact two pages checked for XTC and XTCH; unreadable and out-of-range inputs are rejected.
- **Code consolidation requirement:** the same newly authored `XtcBookPreview` decoder is staged in Android PR #83's shared `overlay/.../XtcBookPreview.kt`. Windows CI protects that overlay from modification, so this desktop-only experiment stages the matching source under `desktop/ui/XtcBookPreview.kt`. **Never merge both locations as permanent duplicate codecs.** Once the shared preview source is accepted for integration, remove the temporary desktop copy and compile the shared source file in the Windows build instead, then validate binary/pixel parity on the same X3/X4 test books.
- **Not yet complete:** full Windows packaged CI and a real Windows GUI click-through, corrupted-file lifecycle, large-book responsiveness, and Android-to-Windows pixel parity. This is a test candidate, not a stable preview release.
- **Rendering choices:** the four algorithms and opt-in Context Peek are staged in separate Android/shared **draft PR #83**. This Windows experiment does not provide inert placeholder controls: connect its desktop conversion GUI only after those shared options pass CI and are reconciled into the appropriate Windows base. Do not loosen the Windows no-Android-code-edit guard.
- **Firmware:** **Folio Nooir firmware is expressly out of scope**. No firmware memory, BLE, SPI, device profiles, OTA, or flash changes.

No merges/releases; keep public Android v1.0.17, updater draft #80, PDF draft #82, and Windows main draft #81 unchanged.
