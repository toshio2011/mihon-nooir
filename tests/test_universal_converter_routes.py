"""Structure regressions for the experimental Universal Converter navigation.

These assertions intentionally do not masquerade as full Android UI tests.
They guard preserved original routes and stop a future refactor from hiding
specialized source engines behind a dead or misleading universal entry.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "overlay/app/src/main/java/dev/nooir/mangabridge"


class UniversalConverterPreservationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.hub = (BASE / "NooirToolsHubDialog.kt").read_text(encoding="utf-8")
        cls.dialog = (BASE / "AnyFileTransferDialog.kt").read_text(encoding="utf-8")
        cls.policy = (BASE / "NooirLocalConversionPolicy.kt").read_text(encoding="utf-8")

    def test_all_existing_tools_remain_reachable(self):
        routes = {
            '"queue" -> NooirActivityQueueDialog': "activity queue",
            '"files" -> AnyFileTransferDialog': "local files",
            '"web" -> NooirWebToReaderDialog': "public web converter",
            '"novels" -> NooirNovelBatchDialog': "web-novel batches",
            '"pdf" -> NooirPdfToEpubDialog': "dedicated PDF engine",
            '"device" -> NooirDeviceFilesDialog': "device file management",
            '"converter" -> AlertDialog': "unified source navigation",
            '"preview" -> NooirFinishedBookPreviewDialog': "finished-book output viewer",
        }
        for token, purpose in routes.items():
            with self.subTest(route=purpose):
                self.assertIn(token, self.hub)
        self.assertIn('Text("Universal Book Converter")', self.hub)
        self.assertIn('tool = "converter"', self.hub)
        self.assertIn('Text("Preview finished XTC / XTCH book")', self.hub)
        viewer = (BASE / "NooirFinishedBookPreviewDialog.kt").read_text(encoding="utf-8")
        helper = (BASE / "NooirFinishedBookPreview.kt").read_text(encoding="utf-8")
        self.assertIn('XtcBookPreview(file).page(index)', helper)
        self.assertIn('NooirFinishedBookPreview.decodePage', viewer)
        self.assertIn('ActivityResultContracts.OpenDocument()', viewer)
        self.assertIn('onOpenFinishedPreview', self.dialog)
        screenshots = (ROOT / ".github/scripts/universal-converter-ui-screenshots.sh").read_text(
            encoding="utf-8")
        self.assertIn('lookup "$target" drawer', screenshots)
        self.assertIn('Tapped visible drawer root:', screenshots)
        self.assertIn('Staged real finished XTCH in picker start folder:', screenshots)
        self.assertNotIn('tap_visible "Downloads" 2', screenshots)
        self.assertLess(viewer.index('Text("Page " +'), viewer.index('val pageImage = bitmap'))
        self.assertIn('tap_visible "Next" 3', screenshots)
        self.assertIn('Decoded XTCH page 2', screenshots)
        self.assertIn('01d-actual-decoded-xtch-next-page', screenshots)
        self.assertIn('FAIL: finished XTCH page 2', screenshots)
        self.assertIn('initialUri = previewUri', self.hub)
        self.assertIn('KEY_LAST_SAVED_PREVIEW_URI', self.dialog)
        self.assertIn('Preview last converted book saved to phone', self.dialog)
        worker = (BASE / "NooirTransferWorker.kt").read_text(encoding="utf-8")
        self.assertIn('lastSavedPreviewUri = target.toString()', worker)
        self.assertIn('completedData.putString(KEY_LAST_SAVED_PREVIEW_URI', worker)

    def test_pdf_remains_a_real_specialist_not_a_fake_conversion(self):
        self.assertIn('onOpenPdfConverter = { tool = "pdf" }', self.hub)
        self.assertIn('onOpenPdfConverter: (() -> Unit)? = null', self.dialog)
        self.assertIn('Text("Open PDF converter")', self.dialog)
        self.assertIn('Picker.PDF, Picker.ANY -> false', self.policy)
        self.assertIn('NooirTools', self.hub)

    def test_input_then_output_then_settings_are_accessible(self):
        for text in (
            'Text("1 · Choose input file type")',
            'Text("2 · Select source files")',
            'Text("3 · Reader & output format")',
            'Text("Convert to")',
            '"4 · More conversion settings"',
            'Text("5 · Destination")',
        ):
            with self.subTest(text=text):
                self.assertIn(text, self.dialog)
        self.assertIn('pickerMimeTypes(', self.dialog)
        self.assertIn('matchesPicker(', self.dialog)
        self.assertIn('canPickerConvertTo(', self.dialog)
        self.assertIn('if (hasEpubInput)', self.dialog)
        self.assertIn('if (hasMangaInput)', self.dialog)

    def test_existing_transfer_routes_and_settings_not_removed(self):
        for token in (
            "NooirTransferWorker.KEY_URIS",
            "NooirTransferWorker.KEY_FORMAT",
            "NooirTransferWorker.KEY_OPTIONS",
            "NooirTransferWorker.KEY_EPUB_FONT",
            "NooirTransferWorker.KEY_EPUB_MARGIN",
            "NooirTransferWorker.KEY_EPUB_RTL",
            "NooirTransferWorker.KEY_EPUB_DIRECTION",
            "NooirTransferWorker.KEY_EPUB_LINE_HEIGHT",
            "NooirTransferWorker.KEY_EPUB_FONT_FAMILY",
            "NooirDeviceWorkQueue.NAME",
            "MangaConversionSettings(",
            "chooseFolder.launch(null)",
            "NooirDevicePicker(device)",
            "NooirDeviceWorkQueue.hasFailed",
            'Text("Share selected with another app or device")',
        ):
            with self.subTest(feature=token):
                self.assertIn(token, self.dialog)
        self.assertIn('"Send " + chosen.size + " original file(s)"', self.dialog)
        self.assertIn('"Convert & send · " + chosen.size + " file(s)"', self.dialog)

    def test_native_epub_respects_visual_hidden_html_without_dropping_aria(self):
        renderer = (BASE / "NooirEpubToXtc.kt").read_text(encoding="utf-8")
        self.assertIn('element.hasAttr("hidden")', renderer)
        self.assertIn('hiddenFromRendering(element)', renderer)
        self.assertIn('property == "display"', renderer)
        self.assertIn('property == "visibility"', renderer)
        self.assertIn('"aria-hidden" is intentionally NOT excluded', renderer)
        smoke = (ROOT / ".github/scripts/panel-diagnostic-smoke.sh").read_text(encoding="utf-8")
        self.assertIn("hiddenXhtmlDoesNotPolluteRealEncodedXtch", smoke)
        self.assertIn("'OK (18 tests)'", smoke)
        self.assertIn("androidDocumentsProviderSaveAndPreviewFinishedXtch", smoke)
        self.assertIn("invalidPreferredImageFallsBackToRealEncodedXtch", smoke)

    def test_finished_preview_r8_api_survives_minified_foss_build(self):
        apply = (ROOT / "tools/apply_overlay.py").read_text(encoding="utf-8")
        self.assertIn('-keep class dev.nooir.mangabridge.NooirFinishedBookPreview { *; }', apply)
        self.assertIn('-keep class dev.nooir.mangabridge.NooirFinishedBookPreview$* { *; }', apply)

    def test_epub_typography_settings_pass_through_to_export(self):
        worker = (BASE / "NooirTransferWorker.kt").read_text(encoding="utf-8")
        importer = (BASE / "NooirEpubImportConverter.kt").read_text(encoding="utf-8")
        renderer = (BASE / "NooirEpubToXtc.kt").read_text(encoding="utf-8")
        for token in ("Reading presets", "Font family", "Line height: ",
                      "Book reading direction", "epubDirection",
                      "epubLineHeight", "epubFontFamily"):
            self.assertIn(token, self.dialog)
        for token in ("KEY_EPUB_LINE_HEIGHT", "KEY_EPUB_FONT_FAMILY",
                      "KEY_EPUB_DIRECTION", "directionMode = epubDirection",
                      "lineHeightPercent = epubLineHeight", "fontFamily = epubFontFamily"):
            self.assertIn(token, worker)
        self.assertIn("lineSpacing = lineHeightPercent.coerceIn(100, 200) / 100f", importer)
        self.assertIn("fontFamily = fontFamily", importer)
        self.assertIn("Typeface.create(settings.fontFamily", renderer)


if __name__ == "__main__":
    unittest.main()
