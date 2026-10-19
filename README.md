<div align="center">

# MangaNooir

**Read manga on Android. Prepare it for your e-reader. Send it over Wi-Fi.**

An independent, experimental Android fork of [Mihon](https://github.com/mihonapp/mihon), with an e-reader conversion workflow inspired by [MangaPoint](https://github.com/W1seGit/MangaPoint) and designed alongside [Folio Nooir](https://github.com/toshio2011/folio-nooir).

[All guides](docs/README.md) · [Choose chapters](docs/CHAPTER_SELECTION.md) · [Getting started](docs/GETTING_STARTED.md) · [Wi-Fi transfer](docs/WIFI_TRANSFER.md) · [FAQ](docs/FAQ.md) · [Roadmap](https://github.com/toshio2011/mihon-nooir/issues/8) · [**Project handover (developers)**](docs/PROJECT_HANDOVER.md)

![Android](https://img.shields.io/badge/platform-Android-informational)
![Status](https://img.shields.io/badge/status-experimental-orange)
![Mihon](https://img.shields.io/badge/base-Mihon-blue)

</div>

> [!IMPORTANT]
> **This is an experimental fork, not an official Mihon app or a stable MangaNooir release.** Automated APK builds have passed, but real-world conversion and XTEink transfers still need testing. There are **no published stable GitHub Releases yet**. Back up your library before changing test APKs.

## Why MangaNooir?

Mihon is already a great way to browse, organize and read manga on Android. MangaNooir aims to bridge the gap between reading on your phone and reading on an e-ink device **without giving up Mihon's existing reader, extensions, library, downloads, tracking, or backups**.

- **Stay in Mihon:** use your installed source extensions and existing manga library. No Suwayomi server or separate login is required.
- **Export manga:** choose XTC, XTCH, fixed-layout EPUB or CBZ for your reader.
- **Keep your originals:** converting does not replace or remove Mihon downloads.
- **Save to your phone or send to Folio Nooir:** use Android's Save file picker, or the XTEink's local Wi-Fi File Transfer mode.
- **Bring your own files:** an experimental workflow recognizes local CBZ/image manga, converts supported inputs, and can send other documents unchanged.

No manga or content-source extensions are bundled. You are responsible for what you access, download and transfer.

## What is available?

This repository has a **working main branch** and **newer experimental preview branches**. The code in a green preview build is not necessarily in the main-branch APK.

| Feature | Main branch · 1.0.1 | Experimental 1.0.4–1.0.5 · [PR #10](https://github.com/toshio2011/mihon-nooir/pull/10) / [PR #11](https://github.com/toshio2011/mihon-nooir/pull/11) |
|---|---|---|
| Mihon reader, library, downloads and extensions | Yes | Yes |
| Export existing downloaded chapters | Yes | Yes |
| X4 / X3 presets; XTC, XTCH, EPUB, CBZ | Yes | Yes |
| Save converted file / send to Folio Nooir via Wi-Fi | Yes — hardware testing needed | Yes — hardware testing needed |
| **Quick Export** online chapters without permanent downloads | Not on main | Experimental |
| **Send & Convert** files selected from phone storage | Not on main | Experimental |
| Choose several files for a batch | Not on main | Experimental — up to 25 per batch |
| Background jobs, status, cancel and upload byte progress | Not on main | Experimental |
| Manga, Manhwa, Manhua presets and image adjustments | Not on main | Experimental |

The 1.0.4 and 1.0.5 previews have passed Android CI and format tests. That is **not** a guarantee of success on every manga source, XTEink model, SD card or firmware. **Web-article conversion, broader direct-device protocols, advanced preview/zoom controls and automatic large-file splitting are still planned**, not finished features.

## Download and install

There is **no published stable release** to download yet. Testers can use GitHub Actions build artifacts:

1. Open [GitHub Actions → Build Mihon Nooir debug APK](https://github.com/toshio2011/mihon-nooir/actions/workflows/build-apk.yml), or the [green experimental 1.0.5 build](https://github.com/toshio2011/mihon-nooir/actions/runs/39228413383) ([earlier 1.0.4 build](https://github.com/toshio2011/mihon-nooir/actions/runs/39220489438)).
2. Open a **successful** workflow run, scroll to **Artifacts**, and download `mihon-nooir-foss-apk` (GitHub sign-in may be needed).
3. Unzip the downloaded artifact on your Android phone.
4. On an ARM64 phone, install an APK named with `arm64-v8a` if present, or a suitable universal APK. Avoid `unsigned` APKs.
5. Allow installation from your file manager when Android asks.

**Already have Mihon?** MangaNooir uses a different app identity, so it can coexist with Mihon. **Already have an older MangaNooir test APK?** CI signing keys can differ: Android may reject an in-place update. Back up your MangaNooir data before considering uninstalling the older test build.

[Full first-run walkthrough →](docs/GETTING_STARTED.md) · [Build and update policy →](docs/UPDATES.md)

## Quick start

### Choose chapters and export manga

**Select the chapters *before* opening the Export dialog**—there isn't a second chapter picker inside Export.

1. Open the manga's **details page**, then **long-press** the first chapter you want.
2. **Tap** other chapters to add or remove them. For a consecutive range, long-pressing a later chapter may select the chapters between them. **Select All** and **Invert Selection** are available in the top toolbar; check the highlighted chapters before proceeding.
3. Tap the **Save/Export icon** in the top toolbar (**Export selected chapters** in selection mode).
4. Pick **X4/X3**, the output format (Auto / XTC / XTCH / EPUB / CBZ), then **Save file** or **Send to XTEink**. Your original Mihon downloads stay unchanged.

**No chapters selected?** Normal Export includes **all downloaded chapters** of that manga. With chapters selected, Normal Export includes **only selected chapters already downloaded**; selected online-only chapters are skipped.

**Need chapters you haven't downloaded?** In experimental 1.0.4/1.0.5, select the chapters first, then enable **Quick Export · fetch missing pages** in Export. Quick Export requires an online source and **at least one selected chapter**. Missing pages are fetched temporarily, processed one chapter at a time, and cleaned afterward.

[**Detailed guide: selecting chapters, exporting ranges and Quick Export →**](docs/CHAPTER_SELECTION.md)


### Send an existing file from your phone — 1.0.4 preview

Open **More → Send & Convert files → Choose multiple files**. Select CBZ, images, EPUB, PDF or other documents. Send the originals unchanged, or choose **Convert manga & send** for supported CBZ/image inputs. Other formats are **not automatically made readable by the XTEink** just because they can be transferred.

[More about formats, metadata and conversion options →](docs/CONVERSION.md)

## Send to XTEink over Wi-Fi

Your phone and Folio Nooir reader need to reach each other on the **same local network**, or you can connect the phone directly to the reader's **Create Hotspot** network. You do **not** need Bluetooth or a USB cable.

1. On your XTEink, open **Home → File Transfer → Join Network**. Join a **2.4 GHz** Wi-Fi network and leave File Transfer running.
2. Note the IP address displayed by the reader, for example `192.168.1.102` (this is only an example).
3. From your phone, open `http://<reader-ip>/` in Chrome. Check that the device's file manager loads.
4. In MangaNooir's transfer settings, enter **only** the address or hostname (`192.168.1.102`, **without** `http://`), and choose an existing destination folder such as `/Books` or `/`.
5. Start with a small EPUB or CBZ. Wait for the XTEink's **success confirmation**, then check the file on the reader.

For a reader-created hotspot, choose **File Transfer → Create Hotspot** and connect the phone to `CrossPoint-Reader`. Use the address actually shown on the reader (often `192.168.4.1`).

> [!WARNING]
> Folio Nooir's local web server and hotspot do **not** use authentication. Use a trusted network, close File Transfer when finished, and avoid exposing it to strangers. Interrupted transfers or full SD cards can leave incomplete files. The experimental 1.0.4 sender has a conservative **180 MiB per-file** limit; that is a safety guard, not a measured hardware maximum.

[**Step-by-step Wi-Fi transfer guide and troubleshooting →**](docs/WIFI_TRANSFER.md) · [Folio Nooir web server guide](https://github.com/toshio2011/folio-nooir/blob/main/docs/webserver.md)

## Output formats and metadata

| Format | Designed for | Metadata |
|---|---|---|
| **XTC** | Small monochrome pages | Native title/author and chapter navigation |
| **XTCH** | Four-shade grayscale pages | Native title/author and chapter navigation |
| **EPUB** | Fixed-layout comic pages | OPF metadata, cover, subjects and navigation |
| **CBZ** | Comic archive readers | Ordered images and `ComicInfo.xml` metadata |

Reader support differs by firmware. EPUB/CBZ may contain extra Nooir metadata JSON that other readers ignore; XTC/XTCH do not have standardized native fields for every rich metadata attribute.

[Format details, reading order and conversion controls →](docs/CONVERSION.md)

**Experimental 1.0.5:** XTC/XTCH exports can now include an actual manga cover page and a bounded optional synopsis-metadata trailer, with a post-upload Folio Nooir metadata-sync attempt. Direct synopsis reading from manually copied files requires an experimental [companion firmware update](https://github.com/toshio2011/folio-nooir/pull/1). [How XTC cover and synopsis work →](https://github.com/toshio2011/mihon-nooir/blob/feature/xtc-cover-synopsis/docs/XTC_METADATA.md)

## Build from source

This repository stores an **integration overlay** rather than a complete second copy of Mihon. CI checks out the pinned Mihon revision in `config/mihon-upstream.sha`, applies `tools/apply_overlay.py`, and builds the FOSS APK.

```bash
python3 -m unittest discover -s tests -p 'test_*.py' -v
bash tests/run.sh   # JDK + kotlinc
python3 tools/apply_overlay.py /path/to/pinned-mihon-checkout
# Inside that Mihon checkout:
./gradlew :app:assembleFoss -Pdist=foss -Pinclude-telemetry=false
```

Fork updates are separate from official Mihon updates. Regular releases require a **persistent Android signing key**; do not expect unrelated CI debug APKs to upgrade one another. See [the update policy](docs/UPDATES.md).

## Experimental work and next-session handover (20 October 2026)

**Continue development from [the full cross-chat project handover](docs/PROJECT_HANDOVER.md).** It audits all stacked draft PRs through **experimental 1.0.9**, plus the newer **1.0.10 web-novel and Nooir Tools/Activity Queue branches** that have **not yet passed CI**, their exact source heads, remaining gaps, and X4 hardware test requirements.

The newest [draft PR #15](https://github.com/toshio2011/mihon-nooir/pull/15) includes the earlier experimental previews plus landscape/overlap, Web → reflowable EPUB, PDF → mixed text-and-image EPUB, a lightweight XTEink Device Files browser, and a unified background job queue. Its [1.0.9 CI build](https://github.com/toshio2011/mihon-nooir/actions/runs/39332739610) passed. **None of these stacked experimental PRs is merged into main, and physical X4 validation is still outstanding.** Always check newer commits and runs before using this status.

## Roadmap and feedback

This is a work in progress. The open [master roadmap](https://github.com/toshio2011/mihon-nooir/issues/8) tracks full-volume splitting, reader/device profiles, more MangaPoint-style controls, background transfer hardening, web articles, metadata improvements, and testing with actual XTEink devices.

If a transfer fails, capture the **build version, device/firmware, file type and size, destination folder, and error message**. Do not post copyrighted manga pages or credentials in reports.

## Credits and licenses

MangaNooir stands on the work of several open-source communities:

- **[Mihon](https://github.com/mihonapp/mihon)** — the full Android reading experience, library, extensions and core app. Thanks to the Mihon Open Source Project, [Javier Tomás](https://github.com/inorichi), and the many Mihon/Tachiyomi contributors. **Apache License 2.0** ([upstream license](https://github.com/mihonapp/mihon/blob/main/LICENSE)).
- **[MangaPoint](https://github.com/W1seGit/MangaPoint)** — inspiration and technical reference for e-reader workflows, image processing, XTC/XTCH, EPUB/CBZ and reader presets. Thanks to its authors and contributors. **MIT License** ([license](LICENSES/MANGAPOINT-MIT.txt)).
- **[Folio Nooir](https://github.com/toshio2011/folio-nooir)** — the companion experimental reader firmware and the network-transfer endpoint this project targets. Its firmware builds on **[CrossPoint Reader](https://github.com/crosspoint-reader/crosspoint-reader)**; thanks to the original CrossPoint contributors and ongoing firmware community.
- **Open-source contributors and testers** — bug reports, format investigations and device feedback make interoperability possible.

The **MangaNooir integration code** is distributed under [MIT](LICENSE), while the **Mihon source it patches remains Apache-2.0** and included MangaPoint-derived material retains its MIT notices. Retain applicable upstream notices and license texts when distributing modified builds. This project is **unofficial** and is not affiliated with or endorsed by Mihon, MangaPoint, XTEink or any manga provider.

MangaNooir provides no manga, bundled sources or hosted catalog. Only use content you have the right to access and transfer.
