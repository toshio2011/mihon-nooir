import tempfile, shutil, subprocess, unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

class TestOverlay(unittest.TestCase):
    def test_integration_anchors(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); app=root/'app'; app.mkdir()
            (app/'build.gradle.kts').write_text('applicationId = "app.mihon"\nversionCode = 38\nversionName = "0.21.2"\ndependencies {\n}\n')
            manga=root/'app/src/main/java/eu/kanade/tachiyomi/ui/manga'
            manga.mkdir(parents=True)
            (manga/'MangaViewModel.kt').write_text('import android.content.Context\nimport eu.kanade.tachiyomi.data.download.DownloadManager\n    private val downloadManager: DownloadManager,\n    val snackbarHostState: SnackbarHostState = SnackbarHostState()\n')
            (manga/'MangaScreen.kt').write_text('import androidx.compose.foundation.layout.systemBarsPadding\n        MangaScreen(\n            state = successState,\n            onDownloadActionClicked = viewModel::runDownloadAction.takeIf { !successState.source.isLocalOrStub() },\n            onInvertSelection = viewModel::invertSelection,\n        )')
            presentation = root/'app/src/main/java/eu/kanade/presentation/manga'
            presentation.mkdir(parents=True)
            (presentation/'MangaScreen.kt').write_text(
                "    onDownloadActionClicked: ((DownloadAction) -> Unit)?,\n" * 3 +
                "            onDownloadActionClicked = onDownloadActionClicked,\n" * 2 +
                "                onClickDownload = onDownloadActionClicked,\n" * 2
            )
            components = presentation/'components'
            components.mkdir(parents=True)
            (components/'MangaToolbar.kt').write_text(
                "import mihon.icons.materialsymbols.rounded.SelectAll\n"
                "    onClickDownload: ((DownloadAction) -> Unit)?,\n"
                "                    add(\n"
                "                        AppBar.Action(\n"
                "                            title = stringResource(MR.strings.action_filter),\n"
                "                        return@buildList\n"
            )
            strings = root/'i18n/src/commonMain/moko-resources/base'
            strings.mkdir(parents=True)
            (strings/'strings.xml').write_text('<string name="app_name" translatable="false">Mihon</string>')
            moretab = root/'app/src/main/java/eu/kanade/tachiyomi/ui/more'
            moretab.mkdir(parents=True)
            (moretab/'MoreTab.kt').write_text(
                "import eu.kanade.presentation.more.MoreScreen\n"
                "        MoreScreen(\n"
                "            onClickDownloadQueue = { navigator.push(DownloadQueueScreen) },\n"
            )
            morescreen = root/'app/src/main/java/eu/kanade/presentation/more'
            morescreen.mkdir(parents=True)
            (morescreen/'MoreScreen.kt').write_text(
                "    onClickDownloadQueue: () -> Unit,\n"
                "            item {\n"
                "                val downloadQueueState = downloadQueueStateProvider()\n"
            )
            graph = root/'app/src/main/java/mihon/app/di'
            graph.mkdir(parents=True)
            (graph/'AppGraph.kt').write_text(
                "import eu.kanade.tachiyomi.data.download.DownloadWorker\n"
                "    fun inject(downloadWorker: DownloadWorker)\n"
            )
            updater = root/'app/src/main/java/eu/kanade/tachiyomi/data/updater'
            updater.mkdir(parents=True)
            (updater/'AppUpdateChecker.kt').write_text('"mihonapp/mihon-preview"\n"mihonapp/mihon"\n')
            subprocess.run(['python3',str(ROOT/'tools/apply_overlay.py'),str(root)],check=True,text=True)
            self.assertIn('NooirExportDialog',(manga/'MangaScreen.kt').read_text())
            self.assertIn('nooirSaveQuick', (manga/'MangaScreen.kt').read_text())
            self.assertIn('quickExport', (manga/'MangaViewModel.kt').read_text())
            self.assertIn('NooirMangaExportWorker.enqueue', (manga/'MangaViewModel.kt').read_text())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/NooirMangaExportWorker.kt').exists())
            self.assertIn('fun inject(nooirMangaExportWorker', (graph/'AppGraph.kt').read_text())
            self.assertIn('dev.nooir.mihon',(app/'build.gradle.kts').read_text())
            self.assertIn('versionName = "1.0.11"',(app/'build.gradle.kts').read_text())
            self.assertIn('versionCode = 1000011',(app/'build.gradle.kts').read_text())
            self.assertIn('toshio2011/mihon-nooir',(updater/'AppUpdateChecker.kt').read_text())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/MihonNooirExporter.kt').exists())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/NooirExportDialog.kt').exists())
            self.assertIn('Quick Export', (root/'app/src/main/java/dev/nooir/mangabridge/NooirExportDialog.kt').read_text())
            self.assertIn('online.getPageList', (root/'app/src/main/java/dev/nooir/mangabridge/MihonNooirExporter.kt').read_text())
            self.assertIn('onClickExport = onExportClicked', (presentation/'MangaScreen.kt').read_text())
            self.assertIn('Export to XTEink', (components/'MangaToolbar.kt').read_text())
            self.assertIn('MangaNooir', (strings/'strings.xml').read_text())
            # The hub replaces scattered More entries without hiding any tools.
            more_tab_text = (moretab/'MoreTab.kt').read_text()
            more_screen_text = (morescreen/'MoreScreen.kt').read_text()
            hub = (root/'app/src/main/java/dev/nooir/mangabridge/NooirToolsHubDialog.kt')
            queue = (root/'app/src/main/java/dev/nooir/mangabridge/NooirActivityQueueDialog.kt')
            self.assertTrue(hub.exists())
            self.assertTrue(queue.exists())
            self.assertIn('NooirToolsHubDialog', more_tab_text)
            self.assertIn('onClickNooirTools', more_tab_text)
            self.assertEqual(more_screen_text.count('title = "Nooir Tools"'), 1)
            self.assertIn('onClickNooirTools', more_screen_text)
            for dialog in (
                'NooirActivityQueueDialog',
                'AnyFileTransferDialog',
                'NooirWebToReaderDialog',
                'NooirNovelBatchDialog',
                'NooirPdfToEpubDialog',
                'NooirDeviceFilesDialog',
            ):
                self.assertIn(dialog, hub.read_text(), dialog)
            self.assertIn('getWorkInfosForUniqueWorkFlow', queue.read_text())
            self.assertIn('cancelUniqueWork(NooirDeviceWorkQueue.NAME)', queue.read_text())
            self.assertIn('Cancel entire Nooir queue', queue.read_text())
            # Mihon's native download queue stays present.
            self.assertIn('onClickDownloadQueue', more_screen_text)
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/NooirWebNovel.kt').exists())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/NooirNovelBatchWorker.kt').exists())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/NooirDeviceFileClient.kt').exists())
            self.assertIn('getWorkInfosForUniqueWorkFlow', (root/'app/src/main/java/dev/nooir/mangabridge/AnyFileTransferDialog.kt').read_text())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/NooirPdfToEpub.kt').exists())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/NooirPdfExportWorker.kt').exists())
            self.assertIn('pdfbox-android', (app/'build.gradle.kts').read_text())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/NooirWebExportWorker.kt').exists())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/ImportedMangaConverter.kt').exists())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/AnyFileTransferDialog.kt').exists())
            self.assertNotIn('chapters.size <= 5', (root/'app/src/main/java/dev/nooir/mangabridge/MihonNooirExporter.kt').read_text())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/NooirTransferWorker.kt').exists())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/MangaConversionOptions.kt').exists())
            opts=(root/'app/src/main/java/dev/nooir/mangabridge/MangaConversionOptions.kt').read_text()
            self.assertIn('overlapPercent', opts)
            self.assertIn('orientation', opts)
            self.assertIn('landscapeClockwise', opts)
            self.assertTrue((root/'app/src/main/java/dev/nooir/bridge/PageSlicePlanner.kt').exists())
            self.assertIn('OpenMultipleDocuments', (root/'app/src/main/java/dev/nooir/mangabridge/AnyFileTransferDialog.kt').read_text())
            self.assertIn('getWorkInfoByIdFlow', (root/'app/src/main/java/dev/nooir/mangabridge/AnyFileTransferDialog.kt').read_text())
            self.assertIn('MangaConversionSettings', (root/'app/src/main/java/dev/nooir/mangabridge/NooirExportDialog.kt').read_text())
            self.assertIn('onProgress', (root/'app/src/main/java/dev/nooir/bridge/NooirUpload.kt').read_text())
            # Regression: a failed book should not poison the WorkManager FIFO chain.
            work_dir = root/'app/src/main/java/dev/nooir/mangabridge'
            queue_source = (work_dir/'NooirDeviceWorkQueue.kt').read_text()
            self.assertIn('nooir_job_failed', queue_source)
            self.assertIn('hasFailed', queue_source)
            worker_names = (
                'NooirMangaExportWorker.kt', 'NooirTransferWorker.kt',
                'NooirWebExportWorker.kt', 'NooirPdfExportWorker.kt',
                'NooirNovelBatchWorker.kt',
            )
            for filename in worker_names:
                content = (work_dir/filename).read_text()
                self.assertIn('NooirDeviceWorkQueue.failed', content, filename)
                self.assertNotIn('Result.failure(', content, filename)
            activity = (work_dir/'NooirActivityQueueDialog.kt').read_text()
            self.assertIn('NooirDeviceWorkQueue.hasFailed(job)', activity)
            self.assertIn('cancelUniqueWork', activity)
            self.assertIn('confirmCancelAll', activity)
            for filename in (
                'AnyFileTransferDialog.kt', 'NooirNovelBatchDialog.kt',
                'NooirWebToReaderDialog.kt', 'NooirPdfToEpubDialog.kt',
            ):
                self.assertNotIn('cancelWorkById', (work_dir/filename).read_text(), filename)

            # Destination names must be checked before conversion, not only at upload.
            guard = (work_dir/'NooirDestinationGuard.kt').read_text()
            self.assertIn('requireOnReader', guard)
            self.assertIn('requireInPhoneFolder', guard)
            upload = (root/'app/src/main/java/dev/nooir/bridge/NooirUpload.kt').read_text()
            self.assertIn('NooirDestinationGuard.requireOnReader', upload)
            self.assertIn('filenameOverride', upload)
            for filename in ('NooirMangaExportWorker.kt', 'NooirTransferWorker.kt',
                             'NooirWebExportWorker.kt', 'NooirPdfExportWorker.kt',
                             'NooirNovelBatchWorker.kt'):
                self.assertIn('NooirDestinationGuard.requireOnReader',
                              (work_dir/filename).read_text(), filename)
            self.assertIn('KEY_OUTPUT_NAMES', (work_dir/'AnyFileTransferDialog.kt').read_text())
            self.assertIn('outputBaseOverride', (work_dir/'ImportedMangaConverter.kt').read_text())

            # Users can choose every discoverable public novel chapter without 80-chapter truncation.
            novel = (work_dir/'NooirWebNovel.kt').read_text()
            self.assertIn('endAt: Int = Int.MAX_VALUE', novel)
            self.assertNotIn('MAX_CHAPTERS', novel)
            novel_dialog = (work_dir/'NooirNovelBatchDialog.kt').read_text()
            self.assertIn('allChapters', novel_dialog)
            self.assertIn('Int.MAX_VALUE', novel_dialog)
            self.assertIn('NooirNovelBatchWorker.NAMES', novel_dialog)
            # Regression from user feedback: missing XTC/XTCH book covers
            # must not be silently omitted while the manga job reports success.
            manga_export = (work_dir/'MihonNooirExporter.kt').read_text()
            self.assertIn('requireNotNull(coverPage(xtcFormat.depth))', manga_export)
            self.assertIn('yield(cover)', manga_export)
            self.assertIn('Preparing actual book cover', manga_export)
            cover_source = (work_dir/'NooirMangaCover.kt').read_text()
            self.assertIn('imageLooksValid', cover_source)
            self.assertIn('inJustDecodeBounds = true', cover_source)
            # Public HTML pages may be WordPress / Elementor or a story's
            # landing page, which must be distinguished from chapter content.
            web = (work_dir/'NooirWebArticle.kt').read_text()
            self.assertIn('elementor-widget-text-editor', web)
            self.assertIn('story landing/catalog page', web)
            novel = (work_dir/'NooirWebNovel.kt').read_text()
            self.assertIn('visibleWattpadPart', novel)
            # The representative 313-page illustrated text PDF has ~28pt line
            # spacing and ~36pt paragraph gaps. A hardcoded 22pt break splits
            # almost every real book line into its own EPUB paragraph.
            pdf_converter = (work_dir/'NooirPdfToEpub.kt').read_text()
            self.assertIn('fontSizeInPt', pdf_converter)
            self.assertIn('paragraphGap = linePitch * 1.17f', pdf_converter)
            self.assertIn('val normalized = rows.filter', pdf_converter)
            self.assertIn('first?.xDirAdj', pdf_converter)
            self.assertIn('part.text.matches(Regex("[0-9]{1,5}"))', pdf_converter)
            self.assertNotIn('line.y - lastY > 22f', pdf_converter)
            self.assertIn('finishSection()', pdf_converter)
            self.assertIn('isCover = index == 0', pdf_converter)
            self.assertIn('titleOverride: String?', pdf_converter)
            self.assertIn('authorOverride: String?', pdf_converter)
            pdf_dialog = (work_dir/'NooirPdfToEpubDialog.kt').read_text()
            self.assertIn('NooirPdfExportWorker.BOOK_TITLE', pdf_dialog)
            self.assertIn('NooirPdfExportWorker.BOOK_AUTHOR', pdf_dialog)


if __name__=='__main__': unittest.main()