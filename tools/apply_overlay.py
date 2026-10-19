#!/usr/bin/env python3
"""Safely apply extension overlay to the pinned upstream Mihon revision."""
from pathlib import Path
import shutil, sys, re

root = Path(__file__).resolve().parents[1]
upstream = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else root / "upstream"
assert (upstream / "app/build.gradle.kts").exists(), "Mihon checkout not found"

def replace_once(path: Path, old: str, new: str):
    source = path.read_text()
    if source.count(old) != 1:
        raise RuntimeError(f"Anchor changed in {path}: expected once, got {source.count(old)} for {old[:70]!r}")
    path.write_text(source.replace(old, new, 1))

for source in (root / "overlay").rglob("*.kt"):
    destination = upstream / source.relative_to(root / "overlay")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)

vm = upstream / "app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaViewModel.kt"
replace_once(vm, "import android.content.Context\n", "import android.content.Context\nimport android.net.Uri\nimport dev.nooir.bridge.ExportFormat\nimport dev.nooir.mangabridge.MihonNooirExporter\n")
replace_once(vm, "import eu.kanade.tachiyomi.data.download.DownloadManager\n", "import eu.kanade.tachiyomi.data.download.DownloadManager\nimport eu.kanade.tachiyomi.data.download.DownloadProvider\n")
replace_once(vm, "    private val downloadManager: DownloadManager,\n", "    private val downloadManager: DownloadManager,\n    private val nooirDownloadProvider: DownloadProvider,\n")
replace_once(vm, "    val snackbarHostState: SnackbarHostState = SnackbarHostState()\n", '''    val snackbarHostState: SnackbarHostState = SnackbarHostState()

    /** Never mutates or deletes Mihon's original downloaded chapters. */
    fun exportDownloadedToNooir(context: Context, destination: Uri, format: ExportFormat, device: String, quickExport: Boolean) {
        val s = successState ?: return
        val candidates = if (s.isAnySelected) s.selectedChapters else s.chapters
        val downloaded = candidates.filter { it.isDownloaded }.map { it.chapter }
        val selected = if (quickExport) s.selectedChapters.map { it.chapter } else downloaded
        viewModelScope.launch {
            val feedback = launch {
                snackbarHostState.showSnackbar("Converting manga…", duration = SnackbarDuration.Indefinite)
            }
            val message = runCatching {
                require(selected.isNotEmpty()) {
                    if (quickExport) "Select 1 to 5 chapters first" else "Download at least one chapter first"
                }
                val result = MihonNooirExporter.export(context, nooirDownloadProvider,
                    s.manga, s.source, selected, destination, format, device, quickExport)
                "Exported " + result.pageCount + " pages as " + result.format.extension.uppercase()
            }.getOrElse { "Export failed: " + it.message }
            feedback.cancel()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun sendDownloadedToNooir(
        context: Context, format: ExportFormat, device: String, host: String, folder: String, quickExport: Boolean,
    ) {
        val s = successState ?: return
        val candidates = if (s.isAnySelected) s.selectedChapters else s.chapters
        val downloaded = candidates.filter { it.isDownloaded }.map { it.chapter }
        val selected = if (quickExport) s.selectedChapters.map { it.chapter } else downloaded
        viewModelScope.launch {
            val feedback = launch {
                snackbarHostState.showSnackbar("Transferring file…", duration = SnackbarDuration.Indefinite)
            }
            val message = runCatching {
                require(selected.isNotEmpty()) {
                    if (quickExport) "Select 1 to 5 chapters first" else "Download at least one chapter first"
                }
                val result = MihonNooirExporter.sendToDevice(context, nooirDownloadProvider,
                    s.manga, s.source, selected, host, folder, format, device, quickExport)
                "Sent " + result.pageCount + " pages to XTEink (" + result.format.extension.uppercase() + ")"
            }.getOrElse { "XTEink transfer failed: " + it.message }
            feedback.cancel()
            snackbarHostState.showSnackbar(message)
        }
    }
''')

screen = upstream / "app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt"
replace_once(screen, "import androidx.compose.foundation.layout.systemBarsPadding\n",
    "import androidx.compose.foundation.layout.systemBarsPadding\nimport dev.nooir.bridge.ExportFormat\nimport dev.nooir.mangabridge.NooirExportDialog\n")
replace_once(screen, "        MangaScreen(\n            state = successState,", '''        var showNooirExport by remember { mutableStateOf(false) }
        var nooirSaveFormat by remember { mutableStateOf(ExportFormat.AUTO) }
        var nooirSaveDevice by remember { mutableStateOf("X4") }
        var nooirSaveQuick by remember { mutableStateOf(false) }
        val nooirSaveLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null) viewModel.exportDownloadedToNooir(context, uri, nooirSaveFormat, nooirSaveDevice, nooirSaveQuick)
        }
        if (showNooirExport) {
            NooirExportDialog(
                mangaTitle = successState.manga.title,
                onDismiss = { showNooirExport = false },
                onSave = { format, device, quickExport ->
                    nooirSaveFormat = format
                    nooirSaveDevice = device
                    nooirSaveQuick = quickExport
                    val filename = successState.manga.title.take(70)
                        .map { c -> if (c.isLetterOrDigit() || c == ' ' || c == '-' || c == '_') c else '_' }.joinToString("")
                    nooirSaveLauncher.launch(filename + "." + format.resolved(device).extension)
                },
                onSend = { format, device, host, folder, quickExport ->
                    viewModel.sendDownloadedToNooir(context, format, device, host, folder, quickExport)
                },
            )
        }
        MangaScreen(
            state = successState,''')
replace_once(screen, "            onDownloadActionClicked = viewModel::runDownloadAction.takeIf { !successState.source.isLocalOrStub() },",
    "            onDownloadActionClicked = viewModel::runDownloadAction.takeIf { !successState.source.isLocalOrStub() },\n            onExportClicked = { showNooirExport = true },")
# Inject export into both phone and tablet toolbars, never above the main screen.
presentation = upstream / "app/src/main/java/eu/kanade/presentation/manga/MangaScreen.kt"
presentation_text = presentation.read_text()
for old, new, count in [
    ("    onDownloadActionClicked: ((DownloadAction) -> Unit)?,",
     "    onDownloadActionClicked: ((DownloadAction) -> Unit)?,\n    onExportClicked: (() -> Unit)?,", 3),
    ("            onDownloadActionClicked = onDownloadActionClicked,",
     "            onDownloadActionClicked = onDownloadActionClicked,\n            onExportClicked = onExportClicked,", 2),
    ("                onClickDownload = onDownloadActionClicked,",
     "                onClickDownload = onDownloadActionClicked,\n                onClickExport = onExportClicked,", 2),
]:
    actual = presentation_text.count(old)
    if actual != count:
        raise RuntimeError(f"Presentation anchor count changed: expected {count}, got {actual}: {old}")
    presentation_text = presentation_text.replace(old, new)
presentation.write_text(presentation_text)

toolbar = upstream / "app/src/main/java/eu/kanade/presentation/manga/components/MangaToolbar.kt"
replace_once(toolbar, "import mihon.icons.materialsymbols.rounded.SelectAll\n",
    "import mihon.icons.materialsymbols.rounded.SelectAll\nimport mihon.icons.materialsymbols.rounded.Save\n")
replace_once(toolbar, "    onClickDownload: ((DownloadAction) -> Unit)?,",
    "    onClickDownload: ((DownloadAction) -> Unit)?,\n    onClickExport: (() -> Unit)?,")
toolbar_text = toolbar.read_text()
marker = "                    add(\n                        AppBar.Action(\n                            title = stringResource(MR.strings.action_filter),"
if toolbar_text.count(marker) != 1:
    raise RuntimeError("Toolbar filter action moved")
toolbar_text = toolbar_text.replace(marker, '''                    if (onClickExport != null) {
                        add(AppBar.Action(title = "Export to XTEink",
                            icon = MaterialSymbols.Rounded.Save, onClick = onClickExport))
                    }
''' + marker)
marker = "                        return@buildList"
if toolbar_text.count(marker) != 1:
    raise RuntimeError("Toolbar selection mode moved")
toolbar_text = toolbar_text.replace(marker, '''                        if (onClickExport != null) {
                            add(AppBar.Action(title = "Export selected chapters",
                                icon = MaterialSymbols.Rounded.Save, onClick = onClickExport))
                        }
''' + marker)
toolbar.write_text(toolbar_text)

# Both the launcher label and the app's UI translation use the same Moko resource.
i18n = upstream / "i18n/src/commonMain/moko-resources/base/strings.xml"
replace_once(i18n, '<string name="app_name" translatable="false">Mihon</string>',
    '<string name="app_name" translatable="false">MangaNooir</string>')
# Use a separate app identity and independent release versions.
replace_once(upstream / "app/build.gradle.kts", 'applicationId = "app.mihon"', 'applicationId = "dev.nooir.mihon"')
version = (root / "config/version.txt").read_text().strip()
assert re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version), "Version must be X.Y.Z"
major, minor, patch = map(int, version.split("."))
assert major <= 20 and minor <= 99 and patch <= 9999, "Version part too large"
version_code = major * 1_000_000 + minor * 10_000 + patch
app_build = upstream / "app/build.gradle.kts"
replace_once(app_build, 'versionCode = 38', f'versionCode = {version_code}')
replace_once(app_build, 'versionName = "0.21.2"', f'versionName = "{version}"')
# Never offer the original Mihon APK as a fork update.
updater = upstream / "app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt"
replace_once(updater, '"mihonapp/mihon-preview"', '"toshio2011/mihon-nooir"')
replace_once(updater, '"mihonapp/mihon"', '"toshio2011/mihon-nooir"')
print("Mihon Nooir integration overlay applied to", upstream)