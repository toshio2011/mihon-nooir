package dev.nooir.desktop.ui

import dev.nooir.bridge.ExportFormat
import dev.nooir.bridge.XtcBookPreview
import dev.nooir.mangabridge.MangaConversionOptions
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO

fun main() {
    val root = Files.createTempDirectory("nooir-local-queue-")
    try {
        val storage = root.resolve("state")
        val out = root.resolve("out").toFile().apply { mkdir() }
        val a = root.resolve("alpha").toFile().apply { mkdir() }
        val b = root.resolve("beta").toFile().apply { mkdir() }
        val page = BufferedImage(48, 72, BufferedImage.TYPE_INT_RGB)
        check(ImageIO.write(page, "png", a.resolve("001.png")))
        check(ImageIO.write(page, "png", b.resolve("001.png")))
        page.flush()
        val q = DesktopConversionQueue(storage)
        val opts = MangaConversionOptions.preset("Manga")
        check(q.enqueue(listOf(a,b), out, ExportFormat.XTC, "X4", opts) == 2)
        check(out.list()?.isEmpty() == true)
        check(runCatching { q.enqueue(listOf(a),out,ExportFormat.XTC,"X4",opts) }.isFailure)
        check(q.list().size == 2)
        var started = 0
        DesktopConversionQueue(storage).runPending({ started >= 1 }) { started++ }
        check(q.list().map { it.state } == listOf(ConversionJobState.COMPLETED, ConversionJobState.PENDING))
        check(XtcBookPreview(out.resolve("alpha.xtc")).pageCount == 1)
        val old = out.resolve("alpha.xtc").readBytes()
        // Conflict after enqueue must never overwrite, even on a retry.
        out.resolve("beta.xtc").writeText("existing")
        q.runPending({ false })
        check(q.list()[1].state == ConversionJobState.FAILED)
        check(out.resolve("beta.xtc").readText() == "existing")
        check(runCatching { q.retry(q.list()[1].id) }.isFailure)
        out.resolve("beta.xtc").delete()
        q.retry(q.list()[1].id)
        q.runPending({ false })
        check(q.list().all { it.state == ConversionJobState.COMPLETED })
        check(XtcBookPreview(out.resolve("beta.xtc")).pageCount == 1)
        check(out.resolve("alpha.xtc").readBytes().contentEquals(old))
        check(q.forgetFinished() == 2 && q.list().isEmpty())
        check(out.list()?.size == 2)
        val custom=dev.nooir.bridge.NooirDeviceProfiles.custom(600,1000)
        check(runCatching{q.enqueue(listOf(a,b),out,ExportFormat.XTC,custom,opts,outputNames=listOf("same","same"))}.isFailure)
        check(q.list().isEmpty())
        check(runCatching{q.enqueue(listOf(a),out,ExportFormat.XTC,custom,opts,outputNames=listOf("../escape"))}.isFailure)
        check(q.enqueue(listOf(a,b),out,ExportFormat.XTC,custom,opts,outputNames=listOf("Volume 1","Volume 2"))==2)
        q.runPending({false})
        check(q.list().all{it.state==ConversionJobState.COMPLETED && it.device==custom})
        check(XtcBookPreview(out.resolve("Volume 1.xtc")).page(0).width==600)
        check(XtcBookPreview(out.resolve("Volume 2.xtc")).page(0).height==1000)
        println("PASS: persistent local conversion batches, pause, resume, conflict refusal, retry, actual XTC output and clear metadata only")
    } finally { root.toFile().deleteRecursively() }
}
