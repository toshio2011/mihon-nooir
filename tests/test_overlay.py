import tempfile, shutil, subprocess, unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

class TestOverlay(unittest.TestCase):
    def test_integration_anchors(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); app=root/'app'; app.mkdir()
            (app/'build.gradle.kts').write_text('applicationId = "app.mihon"\nversionCode = 38\nversionName = "0.21.2"')
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
            subprocess.run(['python3',str(ROOT/'tools/apply_overlay.py'),str(root)],check=True,capture_output=True,text=True)
            self.assertIn('NooirExportDialog',(manga/'MangaScreen.kt').read_text())
            self.assertIn('nooirSaveQuick', (manga/'MangaScreen.kt').read_text())
            self.assertIn('quickExport', (manga/'MangaViewModel.kt').read_text())
            self.assertIn('nooirDownloadProvider',(manga/'MangaViewModel.kt').read_text())
            self.assertIn('dev.nooir.mihon',(app/'build.gradle.kts').read_text())
            self.assertIn('versionName = "1.0.4"',(app/'build.gradle.kts').read_text())
            self.assertIn('versionCode = 1000004',(app/'build.gradle.kts').read_text())
            self.assertIn('toshio2011/mihon-nooir',(updater/'AppUpdateChecker.kt').read_text())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/MihonNooirExporter.kt').exists())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/NooirExportDialog.kt').exists())
            self.assertIn('Quick Export', (root/'app/src/main/java/dev/nooir/mangabridge/NooirExportDialog.kt').read_text())
            self.assertIn('online.getPageList', (root/'app/src/main/java/dev/nooir/mangabridge/MihonNooirExporter.kt').read_text())
            self.assertIn('onClickExport = onExportClicked', (presentation/'MangaScreen.kt').read_text())
            self.assertIn('Export to XTEink', (components/'MangaToolbar.kt').read_text())
            self.assertIn('MangaNooir', (strings/'strings.xml').read_text())
            self.assertIn('AnyFileTransferDialog', (moretab/'MoreTab.kt').read_text())
            self.assertIn('Send & Convert files', (morescreen/'MoreScreen.kt').read_text())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/ImportedMangaConverter.kt').exists())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/AnyFileTransferDialog.kt').exists())
            self.assertNotIn('chapters.size <= 5', (root/'app/src/main/java/dev/nooir/mangabridge/MihonNooirExporter.kt').read_text())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/NooirTransferWorker.kt').exists())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/MangaConversionOptions.kt').exists())
            self.assertIn('OpenMultipleDocuments', (root/'app/src/main/java/dev/nooir/mangabridge/AnyFileTransferDialog.kt').read_text())
            self.assertIn('getWorkInfoByIdFlow', (root/'app/src/main/java/dev/nooir/mangabridge/AnyFileTransferDialog.kt').read_text())
            self.assertIn('MangaConversionSettings', (root/'app/src/main/java/dev/nooir/mangabridge/NooirExportDialog.kt').read_text())
            self.assertIn('onProgress', (root/'app/src/main/java/dev/nooir/bridge/NooirUpload.kt').read_text())

if __name__=='__main__': unittest.main()