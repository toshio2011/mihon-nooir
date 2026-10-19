import tempfile, shutil, subprocess, unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

class TestOverlay(unittest.TestCase):
    def test_integration_anchors(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); app=root/'app'; app.mkdir()
            (app/'build.gradle.kts').write_text('applicationId = "app.mihon"')
            manga=root/'app/src/main/java/eu/kanade/tachiyomi/ui/manga'
            manga.mkdir(parents=True)
            (manga/'MangaViewModel.kt').write_text('import android.content.Context\nimport eu.kanade.tachiyomi.data.download.DownloadManager\n    private val downloadManager: DownloadManager,\n    val snackbarHostState: SnackbarHostState = SnackbarHostState()\n')
            (manga/'MangaScreen.kt').write_text('import androidx.compose.foundation.layout.systemBarsPadding\n        MangaScreen(\n            state = successState,\n            onInvertSelection = viewModel::invertSelection,\n        )')
            subprocess.run(['python3',str(ROOT/'tools/apply_overlay.py'),str(root)],check=True,capture_output=True)
            self.assertIn('Export to e-reader',(manga/'MangaScreen.kt').read_text())
            self.assertIn('nooirDownloadProvider',(manga/'MangaViewModel.kt').read_text())
            self.assertIn('dev.nooir.mihon',(app/'build.gradle.kts').read_text())
            self.assertTrue((root/'app/src/main/java/dev/nooir/mangabridge/MihonNooirExporter.kt').exists())

if __name__=='__main__': unittest.main()