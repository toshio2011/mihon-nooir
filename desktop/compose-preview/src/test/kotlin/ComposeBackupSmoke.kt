package dev.nooir.desktop.compose

import com.sun.net.httpserver.HttpServer
import dev.nooir.desktop.suwayomi.DesktopBackupExtensions
import dev.nooir.desktop.suwayomi.DesktopBackupInspector
import dev.nooir.desktop.suwayomi.DesktopBackupTransport
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

/** Local synthetic fixture; never downloads or executes an extension APK. */
fun main() {
    val dir = Files.createTempDirectory("compose-backup-qa").toFile()
    val repo = "https://example.org/repo.json"
    val store = DesktopBackupExtensions.Store(repo, "Synthetic", "QA", "synthetic-key", "https://example.org", null, true, null)
    val raw = ByteArrayOutputStream().also { bytes ->
        GZIPOutputStream(bytes).use { gz ->
            for (id in 42..44) gz.write(byteArrayOf(10, 5, 8, id.toByte(), 18, 1, 120))
        }
    }.toByteArray()
    val input = dir.resolve("fixture.tachibk").apply {
        writeBytes(DesktopBackupExtensions.withStores(raw, listOf(store)))
    }
    val original = input.readBytes()
    val recovery = dir.resolve("before-restore.tachibk")
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val operations = java.util.Collections.synchronizedList(mutableListOf<String>())
    val installed = java.util.concurrent.atomic.AtomicBoolean(false)
    fun node(id: Int, pkg: String, active: Boolean) =
        """{"pkgName":"$pkg","name":"Synthetic $id","storeIndexUrl":"$repo","isInstalled":$active,"isObsolete":false,"source":{"nodes":[{"id":"$id"}]}}"""
    server.createContext("/api/graphql") { exchange ->
        try {
            val q = exchange.requestBody.readNBytes(200_001).toString(Charsets.ISO_8859_1)
            val data = when {
                "NooirValidate" in q -> {
                    val missing = (42..44).filter { it != 43 || !installed.get() }.joinToString(",") {
                        """{"id":"$it","name":"Synthetic $it"}"""
                    }
                    """{"data":{"validateBackup":{"missingSources":[$missing],"missingTrackers":[]}}}"""
                }
                "NooirExport" in q -> {
                    check("includeServerSettings: false" in q)
                    operations.add("snapshot")
                    """{"data":{"createBackup":{"url":"/api/graphql/files/backup/synthetic.tachibk"}}}"""
                }
                "NooirRestore(" in q -> {
                    check(recovery.isFile && recovery.readBytes().contentEquals(raw))
                    check("includeServerSettings: false" in q)
                    operations.add("restore")
                    """{"data":{"restoreBackup":{"id":"123","status":{"state":"SUCCESS"}}}}"""
                }
                "NooirBackupStores" in q ->
                    """{"data":{"extensionStores":{"totalCount":1,"nodes":[{"name":"Synthetic","badgeLabel":"QA","signingKey":"synthetic-key","contactWebsite":"https://example.org","contactDiscord":null,"indexUrl":"$repo","isLegacy":true,"extensionListUrl":null}]}}}"""
                "NooirBackupCatalog" in q -> {
                    val nodes = listOf(node(42,"synthetic.bad",false), node(43,"synthetic.good",installed.get())).joinToString(",")
                    """{"data":{"extensions":{"totalCount":2,"nodes":[$nodes]}}}"""
                }
                "NooirRecoverExtension" in q && "synthetic.bad" in q -> {
                    operations.add("bad")
                    """{"errors":[{"message":"Unavailable synthetic package"}]}"""
                }
                "NooirRecoverExtension" in q && "synthetic.good" in q -> {
                    operations.add("good")
                    check(installed.compareAndSet(false, true))
                    val extension = node(43,"synthetic.good",true)
                    """{"data":{"updateExtension":{"extension":$extension}}}"""
                }
                else -> error("Unexpected fixture operation")
            }.toByteArray()
            exchange.sendResponseHeaders(200, data.size.toLong())
            exchange.responseBody.use { it.write(data) }
        } finally { exchange.close() }
    }
    server.createContext("/api/graphql/files/backup/synthetic.tachibk") { exchange ->
        exchange.sendResponseHeaders(200, raw.size.toLong())
        exchange.responseBody.use { it.write(raw) }
    }
    server.start()
    try {
        val api = DesktopBackupTransport("http://127.0.0.1:" + server.address.port)
        val summary = DesktopBackupInspector.inspect(input)
        check(summary.favorites == 3 && summary.referencedSourceIds == setOf(42L,43L,44L))
        check(api.validate(input).missingSources.size == 3)
        val plan = api.extensionPlan(input)
        val approved = DesktopBackupExtensions.recoverable(plan)
        check(approved.map { it.pkgName } == listOf("synthetic.bad", "synthetic.good"))
        check(plan.sources.single { it.id == 44L }.state == DesktopBackupExtensions.SourceState.UNRESOLVED)
        check(operations.isEmpty())
        check(api.await(api.beginRestore(input,recovery)) == "SUCCESS")
        check(operations.toList() == listOf("snapshot","restore"))
        val results = DesktopBackupExtensions.recoverBatch(approved) {
            api.installReviewedExtension(input,it)
        }
        check(results.map { it.needsReview } == listOf(true,false))
        check(results.last().confirmed == setOf(43L))
        check(api.validate(input).missingSources.map { it.id }.toSet() == setOf(42L,44L))
        check(operations.toList() == listOf("snapshot","restore","bad","good"))
        check(input.readBytes().contentEquals(original))
        check(DesktopBackupInspector.inspect(input) == summary)
        check(recovery.readBytes().contentEquals(raw))
        check(runCatching { api.export(recovery) }.isFailure)
        println("PASS: partial extension recovery preserves backup and continues after failure")
    } finally {
        server.stop(0)
        dir.deleteRecursively()
    }
}
