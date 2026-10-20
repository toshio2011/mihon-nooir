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
replace_once(vm, "import android.content.Context\n", "import android.content.Context\nimport android.net.Uri\nimport dev.nooir.bridge.ExportFormat\nimport dev.nooir.mangabridge.MihonNooirExporter\nimport dev.nooir.mangabridge.MangaConversionOptions\nimport dev.nooir.mangabridge.NooirMangaExportWorker\n")
replace_once(vm, "import eu.kanade.tachiyomi.data.download.DownloadManager\n", "import eu.kanade.tachiyomi.data.download.DownloadManager\nimport eu.kanade.tachiyomi.data.download.DownloadProvider\n")
replace_once(vm, "    private val downloadManager: DownloadManager,\n", "    private val downloadManager: DownloadManager,\n    private val nooirDownloadProvider: DownloadProvider,\n")
replace_once(vm, "    val snackbarHostState: SnackbarHostState = SnackbarHostState()\n", '''    val snackbarHostState: SnackbarHostState = SnackbarHostState()

    /** The worker owns all conversion and upload state; navigating away is safe. */
    fun exportDownloadedToNooir(
        context: Context, destination: Uri, format: ExportFormat, device: String,
        quickExport: Boolean, options: MangaConversionOptions,
    ) {
        val s = successState ?: return
        val candidates = if (s.isAnySelected) s.selectedChapters else s.chapters
        val selected = if (quickExport) s.selectedChapters.map { it.chapter }
            else candidates.filter { it.isDownloaded }.map { it.chapter }
        val result = runCatching {
            require(selected.isNotEmpty()) { "Select chapters to export first" }
            NooirMangaExportWorker.enqueue(context, s.manga.id, s.manga.title, selected.map { it.id },
                destination, null, "", format, device, quickExport, options)
            "Manga export queued in background. Check More → Send & Convert for progress."
        }.getOrElse { "Could not queue export: " + it.message }
        viewModelScope.launch { snackbarHostState.showSnackbar(result) }
    }

    fun sendDownloadedToNooir(
        context: Context, format: ExportFormat, device: String, host: String, folder: String,
        quickExport: Boolean, options: MangaConversionOptions, outputName: String,
    ) {
        val s = successState ?: return
        val candidates = if (s.isAnySelected) s.selectedChapters else s.chapters
        val selected = if (quickExport) s.selectedChapters.map { it.chapter }
            else candidates.filter { it.isDownloaded }.map { it.chapter }
        val result = runCatching {
            require(selected.isNotEmpty()) { "Select chapters to export first" }
            NooirMangaExportWorker.enqueue(context, s.manga.id, s.manga.title, selected.map { it.id },
                null, host, folder, format, device, quickExport, options, outputName)
            "Manga transfer queued. It continues after Back; check More → Send & Convert."
        }.getOrElse { "Could not queue manga transfer: " + it.message }
        viewModelScope.launch { snackbarHostState.showSnackbar(result) }
    }
''')

screen = upstream / "app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt"
replace_once(screen, "import androidx.compose.foundation.layout.systemBarsPadding\n",
    "import androidx.compose.foundation.layout.systemBarsPadding\nimport dev.nooir.bridge.ExportFormat\nimport dev.nooir.mangabridge.NooirExportDialog\nimport dev.nooir.mangabridge.MangaConversionOptions\nimport dev.nooir.mangabridge.NooirMangaExportWorker\n")
replace_once(screen, "        MangaScreen(\n            state = successState,", '''        var showNooirExport by remember { mutableStateOf(false) }
        var nooirSaveFormat by remember { mutableStateOf(ExportFormat.AUTO) }
        var nooirSaveDevice by remember { mutableStateOf("X4") }
        var nooirSaveQuick by remember { mutableStateOf(false) }
        var nooirSaveOptions by remember { mutableStateOf(MangaConversionOptions()) }
        val nooirSaveLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null) {
                // The export runs in WorkManager and must retain the SAF write grant
                // even if Android recreates the app process.
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Some document providers grant a non-persistable URI. The
                    // worker may then fail if the app process is killed.
                }
                viewModel.exportDownloadedToNooir(context, uri, nooirSaveFormat, nooirSaveDevice, nooirSaveQuick, nooirSaveOptions)
            }
        }
        if (showNooirExport) {
            NooirExportDialog(
                mangaTitle = successState.manga.title,
                selectedChapterCount = successState.selectedChapters.size,
                onlineSource = successState.source is HttpSource,
                onDismiss = { showNooirExport = false },
                onSave = { format, device, quickExport, options ->
                    nooirSaveFormat = format
                    nooirSaveDevice = device
                    nooirSaveQuick = quickExport
                    nooirSaveOptions = options
                    val filename = successState.manga.title.take(70)
                        .map { c -> if (c.isLetterOrDigit() || c == ' ' || c == '-' || c == '_') c else '_' }.joinToString("")
                    nooirSaveLauncher.launch(filename + "." + format.resolved(device).extension)
                },
                onSend = { format, device, host, folder, quickExport, options, outputName ->
                    viewModel.sendDownloadedToNooir(context, format, device, host, folder, quickExport, options, outputName)
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
# New WorkManager worker needs Metro graph injection just as Mihon's own workers do.
app_graph = upstream / "app/src/main/java/mihon/app/di/AppGraph.kt"
replace_once(app_graph, "import eu.kanade.tachiyomi.data.download.DownloadWorker\n",
    "import eu.kanade.tachiyomi.data.download.DownloadWorker\nimport dev.nooir.mangabridge.NooirMangaExportWorker\n")
replace_once(app_graph, "    fun inject(downloadWorker: DownloadWorker)\n",
    "    fun inject(downloadWorker: DownloadWorker)\n    fun inject(nooirMangaExportWorker: NooirMangaExportWorker)\n")

# Keep original Mihon's More layout clean: expose ONE Nooir Tools entry.
# The tool hub itself routes to the existing converters, file manager and queue.
more_tab = upstream / "app/src/main/java/eu/kanade/tachiyomi/ui/more/MoreTab.kt"
replace_once(more_tab, "import eu.kanade.presentation.more.MoreScreen\n",
    "import eu.kanade.presentation.more.MoreScreen\nimport dev.nooir.mangabridge.NooirToolsHubDialog\nimport androidx.compose.runtime.remember\nimport androidx.compose.runtime.mutableStateOf\n")
replace_once(more_tab, "        MoreScreen(\n", '''        var showNooirTools by remember { mutableStateOf(false) }
        if (showNooirTools) {
            NooirToolsHubDialog(onDismiss = { showNooirTools = false })
        }
        MoreScreen(
''')
replace_once(more_tab, "            onClickDownloadQueue = { navigator.push(DownloadQueueScreen) },",
    "            onClickNooirTools = { showNooirTools = true },\n            onClickDownloadQueue = { navigator.push(DownloadQueueScreen) },")
more_screen = upstream / "app/src/main/java/eu/kanade/presentation/more/MoreScreen.kt"
replace_once(more_screen, "    onClickDownloadQueue: () -> Unit,",
    "    onClickNooirTools: () -> Unit,\n    onClickDownloadQueue: () -> Unit,")
replace_once(more_screen, '''            item {
                val downloadQueueState = downloadQueueStateProvider()''',
    '''            item {
                TextPreferenceWidget(
                    title = "Nooir Tools",
                    subtitle = "Send, convert, manage e-reader files and see activity",
                    icon = MaterialSymbols.Rounded.Storage,
                    onPreferenceClick = onClickNooirTools,
                )
            }
            item {
                val downloadQueueState = downloadQueueStateProvider()''')

# Use a separate app identity and independent release versions.
# PDFBox-Android is used only for local PDF text/image extraction.
# The separate Android platform PdfRenderer supplies bitmap-page fallback.
replace_once(upstream / "app/build.gradle.kts", "dependencies {\n",
    "dependencies {\n    implementation(\"com.tom-roush:pdfbox-android:2.0.27.0\")\n")
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