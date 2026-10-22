# MangaNooir Windows — D1 shared conversion spike (developer-only)

This is the **first implementation increment**, not the Windows desktop app or a Windows installer. It proves that the same Kotlin/JVM conversion codecs and MangaNooir settings can run on desktop without Android APIs. It deliberately does **not** include Suwayomi, manga browsing, chapter downloads, Mihon backup restore, a GUI, PDF or web conversion.

## Shared source — no duplicate codecs

The Windows CLI compiles these **unchanged Android overlay files directly**:

- `overlay/app/src/main/java/dev/nooir/bridge/`: XtcCodec, GrayscaleConverter, GrayscalePng, BookArchiveWriter, Epub2Writer, ExportFormats, NooirDeviceProfiles, PageSlicePlanner and NaturalPageOrder.
- `overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt`: pure JVM MangaConversionOptions model, presets and legacy job-token migration.

Android's `MangaConversionOptions.kt` now only contains the Compose controls and SharedPreferences adapter; the conversion model is shared. Android's manga exporter and image source use the shared PNG writer and natural page ordering. The Android overlay build still copies the same files through `tools/apply_overlay.py`.

The CLI is a **JVM adapter** in `desktop/cli/NooirDesktopCli.kt`, using Java2D/ImageIO for image decoding and rendering. **It has not yet passed pixel-by-pixel parity validation against Android Bitmap**, so do not advertise bit-identical pages.

## Build and run (JDK 21+, kotlinc on PATH)

From the repository root:

```bash
mkdir -p build/desktop
kotlinc \
  overlay/app/src/main/java/dev/nooir/bridge/{XtcCodec.kt,ExportFormats.kt,NooirDeviceProfiles.kt,BookArchiveWriter.kt,Epub2Writer.kt,GrayscaleConverter.kt,GrayscalePng.kt,NaturalPageOrder.kt,PageSlicePlanner.kt} \
  overlay/app/src/main/java/dev/nooir/mangabridge/MangaConversionSettingsCore.kt \
  desktop/cli/NooirDesktopCli.kt \
  -include-runtime -d build/desktop/manganooir-cli.jar

java -Djava.awt.headless=true -cp build/desktop/manganooir-cli.jar dev.nooir.desktop.NooirDesktopCli \
  --input "/path/to/manga-image-folder" \
  --output "/path/to/output/book.xtch" \
  --format XTCH --device X4 --title "My Manga"
```

On Windows PowerShell, compile the same source files by listing paths explicitly (PowerShell does not expand Bash brace patterns), then:

```powershell
java -Djava.awt.headless=true -cp build/desktop/manganooir-cli.jar dev.nooir.desktop.NooirDesktopCli --input "C:\\Manga\\Chapter" --output "C:\\Exports\\Chapter.epub" --format EPUB2 --device X4
```

Formats: `AUTO`, `XTC`, `XTCH`, `EPUB` (EPUB 3), `EPUB2` and `CBZ`. Input: a folder of image files (chapter subfolders are grouped as chapters) or a CBZ/ZIP of image files. Optional `--options-token` accepts the **same token** emitted by MangaNooir Android's `MangaConversionOptions.token()` (including older job-token versions). A root-level `cover.png` is treated as a separate cover. Existing output files are rejected; files are written to a temporary sibling and moved only after a successful conversion.

Run automated checks:

```bash
python3 -m unittest discover -s tests -p "test_*.py" -v
bash tests/run.sh
```

## Proven by CI tests (once green)

- Same Kotlin export codec source is compiled for desktop and Android.
- EPUB 2, EPUB 3, XTC, XTCH, CBZ from synthetic manga images; page/chapter ordering and cover position.
- CBZ/ZIP input, no silent output overwrite, readable grayscale PNG, format metadata and shared legacy settings migration.
- Android overlay structure and full Android FOSS APK build remain regression gates.

## Limitations and next steps

- No manga source browser, chapter downloader, Mihon backup restore, Windows GUI or packaged EXE yet.
- ZIP and image files are bounded; desktop ImageIO codec support differs from Android, especially WebP. Password-protected archives, RAR, PDF and text EPUB are not inputs here.
- Image rendering is currently a Java2D approximation of Android's Bitmap canvas, not certified pixel parity. Cover resolution from online sources and Android preflight confirmations are not yet ported.
- Source files with huge dimensions may require more memory than intended; real-world batch, malformed image and crash-recovery testing remains.
- Next: compare generated pages against Android fixtures, stabilize image adapter, and complete the D0 Suwayomi backend download-to-export proof before GUI development.

Full roadmap: [Windows desktop feasibility and feature plan](../docs/WINDOWS_DESKTOP_FEASIBILITY_ROADMAP.md).
