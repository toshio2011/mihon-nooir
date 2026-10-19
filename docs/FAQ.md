# Frequently asked questions

[← Documentation](README.md) · [Main README](../README.md)

### How do I choose which chapters to export?

On the manga details page, **long-press** one chapter, then tap the others. Press **Export** in the top toolbar. Selection happens **before** the Export dialog. [See the illustrated-by-steps chapter selection walkthrough.](CHAPTER_SELECTION.md)

### Can I export chapters I haven't downloaded?

Yes, with **Quick Export** in the experimental preview. Select the chapters first and enable **Quick Export · fetch missing pages**. It uses the installed online source extension and temporary cache. The stable main integration does **not** have Quick Export. [Full chapter guide →](CHAPTER_SELECTION.md)

### What happens if I don't select any chapters?

Normal Export uses **all downloaded chapters** of that manga. Quick Export requires at least one selected chapter. Be careful when exporting a large series.

### How do I import a local CBZ or image from my phone?

That's a separate action: **More → Send & Convert files** in experimental 1.0.4/1.0.5. The Android file picker selects existing documents, and supported manga inputs can be converted. It is **not** the chapter picker for a Mihon manga. [Getting started →](GETTING_STARTED.md)

### Can I send several files at once?

The experimental preview supports **up to 25 local files per batch** with sequential processing. Chapter selection can include many chapters, but the manga-details exporter combines them into one book; it doesn't yet output one separate file per chapter. [Read the chapter guide →](CHAPTER_SELECTION.md)

### Do I need the same Wi-Fi on my phone and XTEink?

Both devices must reach each other over the same local network, or the phone can join the XTEink's **Create Hotspot** network. The XTEink must stay in **File Transfer** mode. [Complete Wi-Fi transfer guide →](WIFI_TRANSFER.md)

### Can I leave the app during a transfer?

In the experimental 1.0.4/1.0.5 preview, background jobs are intended to keep working after you navigate Back or switch apps. Force-stopping the app, losing Wi-Fi, revoking access or Android battery restrictions can still interrupt a job. Check **More → Send & Convert** and the Android notification. [Transfer progress troubleshooting →](WIFI_TRANSFER.md)

### Why does XTC show the wrong cover or no synopsis?

The main 1.0.1 exporter doesn't add a distinct manga cover page and XTC/XTCH don't have a standard synopsis field. **Experimental 1.0.5** adds a proper manga-cover page when available, an optional rich-metadata trailer, and a Wi-Fi metadata sync to Folio Nooir. Reading a synopsis from an XTC file copied manually needs the [companion firmware change](https://github.com/toshio2011/folio-nooir/pull/1), which remains experimental. [Technical XTC metadata guide →](https://github.com/toshio2011/mihon-nooir/blob/feature/xtc-cover-synopsis/docs/XTC_METADATA.md)

### Which file format should I choose?

For supported XTEink firmware, try **XTCH** for four-level grayscale or **XTC** for black-and-white. **EPUB** and **CBZ** are more portable across compatible comic readers. Format recognition and cover/synopsis behavior differ by device and firmware. [Formats and conversion guide →](CONVERSION.md)

### Where do I download the APK?

There are no published stable GitHub Releases yet. Use a successful [GitHub Actions build](https://github.com/toshio2011/mihon-nooir/actions/workflows/build-apk.yml); see [Getting Started](GETTING_STARTED.md) for how to download and install the artifact. Back up MangaNooir before replacing an APK signed with a different key.

### Does MangaNooir replace my original Mihon?

No. MangaNooir has a different Android app identity and can coexist with Mihon. Exports preserve the original downloaded manga files.

### What if my issue isn't listed?

Check the [roadmap](https://github.com/toshio2011/mihon-nooir/issues/8) or [GitHub Issues](https://github.com/toshio2011/mihon-nooir/issues), and include your app version, device model, Folio Nooir version, file format, selected chapter count, and the exact message shown. Avoid sharing copyrighted book files or passwords publicly.
