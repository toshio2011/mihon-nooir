package dev.nooir.desktop.compose

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import dev.nooir.desktop.ui.DesktopConversionQueue

fun main()=runBlocking {
    val root=Files.createTempDirectory("nooir-session-jobs-").toFile()
    val gate=CountDownLatch(1)
    try {
        val controller=SessionConversions(this)
        val first=File(root,"first.xtc");val second=File(root,"second.xtc");val canceled=File(root,"canceled.xtc")
        controller.submit(first,"first"){
            check(gate.await(10,TimeUnit.SECONDS))
            first.writeText("first")
        }
        controller.submit(second,"second"){
            check(first.isFile);second.writeText("second")
        }
        val id=controller.submit(canceled,"cancel"){error("Canceled job ran")}
        check(controller.active)
        check(runCatching{controller.submit(first,"duplicate") {}}.isFailure)
        check(runCatching{controller.startBatch(DesktopConversionQueue(root.toPath()))}.isFailure)
        controller.cancel(id)
        gate.countDown()
        withTimeout(15000){while(controller.active)delay(10)}
        check(first.isFile && second.isFile && !canceled.exists())
        check(controller.jobs.map{it.state}==listOf(SessionJobState.COMPLETED,SessionJobState.COMPLETED,SessionJobState.CANCELED))
        controller.startBatch(DesktopConversionQueue(root.toPath()))
        val third=File(root,"third.xtc")
        controller.submit(third,"after batch"){third.writeText("third")}
        withTimeout(15000){while(controller.active)delay(10)}
        check(third.isFile && controller.jobs.last().state==SessionJobState.COMPLETED)
        val sent=File(root,"sent.xtc");val unsent=File(root,"unsent.xtc");val failed=File(root,"failed.xtc")
        var sends=0
        controller.submitWithTransfer(sent,"successful transfer",{book->check(book.readText()=="saved");sends++}){sent.writeText("saved")}
        controller.submitWithTransfer(unsent,"failed transfer",{book->check(book.isFile);sends++;error("Reader offline")}){unsent.writeText("saved")}
        controller.submitWithTransfer(failed,"failed conversion",{sends++}){error("Conversion failed")}
        withTimeout(15000){while(controller.active)delay(10)}
        check(sends==2 && unsent.readText()=="saved" && !failed.exists())
        check(controller.jobs.takeLast(3).map{it.state}==listOf(SessionJobState.COMPLETED,SessionJobState.TRANSFER_UNCONFIRMED,SessionJobState.FAILED))
        check(controller.jobs[controller.jobs.lastIndex-1].detail.contains("Local book saved"))
        controller.clearFinished()
        check(controller.jobs.isEmpty() && first.isFile && second.isFile && third.isFile && unsent.isFile)
        println("PASS: app-owned conversion ordering, duplicate protection, cancellation and batch handoff")
    }finally{gate.countDown();root.deleteRecursively()}
}
