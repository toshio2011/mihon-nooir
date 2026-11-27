package dev.nooir.desktop.ui

import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.NooirDeviceProfiles
import dev.nooir.desktop.NooirDesktopCli
import dev.nooir.desktop.storage.DesktopDataHome
import dev.nooir.mangabridge.MangaConversionOptions
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Base64
import java.util.UUID

internal enum class ConversionJobState { PENDING, RUNNING, COMPLETED, FAILED, INTERRUPTED, CANCELED }

internal data class DesktopConversionJob(
    val id: String, val input: String, val output: String,
    val format: ExportFormat, val device: String, val token: String,
    val state: ConversionJobState, val error: String = "",
    val textEpub: DesktopTextEpubImport.Settings? = null,
    val pdfReflow: Boolean = false,
) {
    fun args(): Array<String> = arrayOf(
        "--input", input, "--output", output, "--format", format.name,
        "--device", device, "--title", File(input).nameWithoutExtension.take(120).ifBlank { "Manga" },
        "--options-token", token,
    )
}

/**
 * Explicit local conversion queue. The file lock serializes index updates
 * across process instances; a separate runner lease guards active conversion.
 * Never auto-retry an interrupted export or overwrite an existing destination.
 */
internal class DesktopConversionQueue(private val folder: Path = DesktopDataHome.root()) {
    companion object {
        private const val HEADER = "MIHONNOOIR_CONVERSION_QUEUE_V1"
        private const val MAX_JOBS = 500
        private const val MAX_BYTES = 8_000_000L
        private val gate = Any()
        private val encoder = Base64.getUrlEncoder().withoutPadding()
        private val decoder = Base64.getUrlDecoder()
        private fun enc(s: String) = encoder.encodeToString(s.toByteArray(StandardCharsets.UTF_8))
        private fun dec(s: String) = String(decoder.decode(s), StandardCharsets.UTF_8)
        // Optional ninth column. Legacy eight-column jobs keep their exact
        // image-comic behavior. Older previews reject, rather than misrun,
        // rows containing new typography settings.
        private fun typographyToken(settings: DesktopTextEpubImport.Settings): String =
            listOf(settings.fontPx, settings.marginPx, settings.lineHeight,
                settings.family, settings.direction).joinToString("|")

        private fun typographyFromToken(token: String): DesktopTextEpubImport.Settings {
            val parts = token.split('|')
            require(parts.size == 5) { "Damaged typography settings; original preserved" }
            return DesktopTextEpubImport.Settings(parts[0].toInt(), parts[1].toInt(),
                parts[2].toInt(), parts[3], parts[4])
        }
    }

    // Same filename policy as DesktopUiModel.suggestedFile; keep the queue
    // independent of the Swing JFrame and its unrelated screen dependencies.
    private fun queueOutputName(title: String, format: ExportFormat): String {
        val normalized = title.replace(Regex("[^\\p{L}\\p{N} ._()-]"), "_")
            .trim().take(80).trimEnd(' ', '.').ifBlank { "MihonNooir" }
        val reserved = Regex("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$")
        val safe = if (reserved.matches(normalized)) "_$normalized" else normalized
        return safe + "." + format.extension
    }

    private val index = folder.resolve("conversion-queue-v1.tsv")
    private fun <T> transaction(block: () -> T): T = synchronized(gate) {
        Files.createDirectories(folder)
        require(!Files.isSymbolicLink(folder) && Files.isDirectory(folder)) { "Unsafe queue folder" }
        val lockPath = folder.resolve("conversion-queue.lock")
        require(!Files.isSymbolicLink(lockPath)) { "Unsafe queue lock" }
        FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use { block() }
        }
    }
    private fun read(): MutableList<DesktopConversionJob> {
        if (!Files.exists(index)) return mutableListOf()
        require(Files.isRegularFile(index) && !Files.isSymbolicLink(index) &&
            Files.size(index) <= MAX_BYTES) { "Invalid conversion index; original preserved" }
        val lines = Files.readAllLines(index, StandardCharsets.UTF_8)
        require(lines.firstOrNull() == HEADER && lines.size <= MAX_JOBS + 1) {
            "Invalid conversion queue header; original preserved"
        }
        return lines.drop(1).map { line ->
            val p = line.split('\t')
            require(p.size in 8..9) { "Damaged conversion job; original preserved" }
            DesktopConversionJob(p[0], dec(p[1]), dec(p[2]), ExportFormat.valueOf(p[3]),
                p[4], dec(p[5]), ConversionJobState.valueOf(p[6]), dec(p[7]),
                p.getOrNull(8)?.let { dec(it) }?.takeUnless { it == "pdf:reflow" }?.let { typographyFromToken(it) },
                p.getOrNull(8)?.let { dec(it) == "pdf:reflow" } ?: false).also { job ->
                require(runCatching { UUID.fromString(job.id) }.isSuccess &&
                    File(job.input).isAbsolute && File(job.output).isAbsolute &&
                    job.input.length <= 4096 && job.output.length <= 4096 &&
                    job.token.length in 1..4096 && job.error.length <= 400 &&
                    job.device.length in 1..80 &&
                    job.format != ExportFormat.AUTO &&
                    File(job.output).extension.equals(job.format.extension, true) &&
                    (job.textEpub == null || File(job.input).extension.equals("epub", true) &&
                        job.format in setOf(ExportFormat.XTC, ExportFormat.XTCH)) &&
                    (!job.pdfReflow || File(job.input).extension.equals("pdf", true) &&
                        job.format in setOf(ExportFormat.EPUB, ExportFormat.EPUB2))) {
                    "Invalid conversion job; original preserved"
                }
            }
        }.also { jobs ->
            require(jobs.map { it.id }.distinct().size == jobs.size)
        }.toMutableList()
    }
    private fun write(jobs: List<DesktopConversionJob>) {
        require(jobs.size <= MAX_JOBS)
        val data = buildString {
            append(HEADER).append('\n')
            jobs.forEach { j ->
                val columns = listOf(j.id, enc(j.input), enc(j.output), j.format.name, j.device,
                    enc(j.token), j.state.name, enc(j.error.take(400)))
                val mode = if (j.pdfReflow) "pdf:reflow" else j.textEpub?.let { typographyToken(it) }
                append((columns + listOfNotNull(mode?.let { enc(it) })).joinToString("\t")).append('\n')
            }
        }
        require(data.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES)
        val tmp = Files.createTempFile(folder, ".conversion-queue-", ".tmp")
        try {
            Files.writeString(tmp, data, StandardCharsets.UTF_8)
            // A provider without atomic replace fails closed; do not destroy
            // the previously durable index on an unsupported filesystem.
            Files.move(tmp, index, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(tmp) }
    }

    fun list(): List<DesktopConversionJob> = transaction { read().toList() }

    fun enqueue(inputs: List<File>, target: File, format: ExportFormat, device: String,
        options: MangaConversionOptions, textEpub: DesktopTextEpubImport.Settings? = null,
        pdfReflow: Boolean = false): Int = transaction {
        require(inputs.isNotEmpty() && inputs.size <= MAX_JOBS)
        require(format != ExportFormat.AUTO && NooirDeviceProfiles.forId(device).accepts(format))
        require(!pdfReflow || textEpub == null && format in setOf(ExportFormat.EPUB, ExportFormat.EPUB2)) {
            "PDF reflow batches require EPUB output"
        }
        require(textEpub == null || format in setOf(ExportFormat.XTC, ExportFormat.XTCH)) {
            "Text EPUB batches require XTC or XTCH output"
        }
        require(device.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "Invalid device profile ID" }
        val directory = target.canonicalFile
        require(directory.isDirectory) { "Choose an existing output folder" }
        val jobs = read()
        require(jobs.size + inputs.size <= MAX_JOBS) { "Conversion queue full; clear finished records first" }
        val reserved = jobs.filter { it.state != ConversionJobState.CANCELED }
            .map { it.output.lowercase(java.util.Locale.ROOT) }.toMutableSet()
        val added = inputs.map { selected ->
            val source = selected.canonicalFile
            require(source.isDirectory || source.isFile && source.extension.lowercase() in setOf("zip", "cbz", "pdf", "epub", "png", "jpg", "jpeg", "webp", "bmp", "gif")) {
                "Choose an image folder, CBZ/ZIP, PDF or image-based EPUB"
            }
            require(!pdfReflow || source.isFile && source.extension.equals("pdf", true)) {
                "Choose only PDFs for a PDF reflow batch"
            }
            require(textEpub == null || source.isFile && source.extension.equals("epub", true)) {
                "Choose only EPUB files for a text EPUB batch"
            }
            val output = File(directory, queueOutputName(source.nameWithoutExtension, format)).canonicalFile
            require(output.parentFile == directory && source != output && !output.exists()) {
                "Output already exists or is not a new local destination: " + output.name
            }
            require(!source.isDirectory || !output.toPath().startsWith(source.toPath())) {
                "Use an output folder outside the source image folder"
            }
            require(reserved.add(output.path.lowercase(java.util.Locale.ROOT))) {
                "Duplicate destination: " + output.name
            }
            require(source.path.length <= 4096 && output.path.length <= 4096)
            DesktopConversionJob(UUID.randomUUID().toString(), source.path, output.path, format,
                device, options.copy(panelDiagnostics = false).token(), ConversionJobState.PENDING,
                textEpub = textEpub, pdfReflow = pdfReflow)
        }
        write(jobs + added)
        added.size
    }

    fun retry(id: String) = change(id, setOf(ConversionJobState.FAILED, ConversionJobState.INTERRUPTED),
        ConversionJobState.PENDING, requireNewOutput = true)
    fun cancel(id: String) = change(id, setOf(ConversionJobState.PENDING, ConversionJobState.FAILED,
        ConversionJobState.INTERRUPTED), ConversionJobState.CANCELED)
    fun forgetFinished(): Int = transaction {
        val jobs = read()
        val keep = jobs.filterNot { it.state in setOf(ConversionJobState.COMPLETED, ConversionJobState.CANCELED) }
        if (keep.size != jobs.size) write(keep)
        jobs.size - keep.size
    }

    private fun change(id: String, from: Set<ConversionJobState>, to: ConversionJobState,
        error: String = "", requireNewOutput: Boolean = false) = transaction {
        val jobs = read()
        val i = jobs.indexOfFirst { it.id == id }
        require(i >= 0 && jobs[i].state in from) { "Job cannot transition to $to" }
        if (requireNewOutput) require(!File(jobs[i].output).exists()) {
            "Output exists. Inspect it first; this queue never overwrites files."
        }
        jobs[i] = jobs[i].copy(state = to, error = error.take(400))
        write(jobs)
    }

    /** The lease is held for the whole batch, including the actual codec call. */
    fun runPending(stop: () -> Boolean, progress: (DesktopConversionJob) -> Unit = {}) {
        Files.createDirectories(folder)
        val lease = folder.resolve("conversion-runner.lock")
        require(!Files.isSymbolicLink(lease)) { "Unsafe runner lock" }
        FileChannel.open(lease, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            val held = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
            requireNotNull(held) { "Conversion jobs are already running in another window/process" }
            held.use {
                transaction {
                    val jobs = read()
                    if (jobs.any { it.state == ConversionJobState.RUNNING }) {
                        write(jobs.map { job ->
                            if (job.state == ConversionJobState.RUNNING) job.copy(
                                state = ConversionJobState.INTERRUPTED,
                                error = "Prior run interrupted. Inspect destination before retrying.") else job
                        })
                    }
                }
                while (!stop()) {
                    val job = transaction {
                        val jobs = read()
                        val i = jobs.indexOfFirst { it.state == ConversionJobState.PENDING }
                        if (i < 0) null else {
                            val next = jobs[i].copy(state = ConversionJobState.RUNNING, error = "")
                            jobs[i] = next
                            write(jobs)
                            next
                        }
                    } ?: break
                    progress(job)
                    try {
                        require(!File(job.output).exists()) { "Destination already exists; no overwrite" }
                        val input = File(job.input)
                        val output = File(job.output)
                        val options = MangaConversionOptions.fromToken(job.token)
                        // PDFs intentionally preserve original pages in a batch;
                        // the optional text companion remains an explicit
                        // single-document workflow. No manga slicing on PDFs.
                        when {
                            job.pdfReflow ->
                                DesktopPdfImport.exportReflow(input, output, job.format, job.device, options)
                            input.isFile && input.extension.equals("pdf", true) ->
                                DesktopPdfImport.export(input, output, job.format, job.device, options)
                            job.textEpub != null ->
                                DesktopTextEpubImport.export(input, output, job.format, job.device, job.textEpub, options)
                            input.isFile && input.extension.equals("epub", true) ->
                                DesktopImageEpubImport.export(input, output, job.format, job.device, options)
                            else -> NooirDesktopCli.main(job.args())
                        }
                        check(File(job.output).isFile && File(job.output).length() > 0L)
                    } catch (e: Exception) {
                        change(job.id, setOf(ConversionJobState.RUNNING), ConversionJobState.FAILED,
                            e.message ?: e.javaClass.simpleName)
                        continue
                    }
                    change(job.id, setOf(ConversionJobState.RUNNING), ConversionJobState.COMPLETED)
                }
            }
        }
    }
}
