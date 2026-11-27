package dev.nooir.desktop.compose

import com.sun.net.httpserver.HttpServer
import dev.nooir.desktop.suwayomi.DesktopBackupTransport
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Test the real transport without downloading, executing or contacting any extension. */
fun main() {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val url = "https://example.org/synthetic/repo.json"
    val installed = AtomicBoolean(false)
    val calls = AtomicInteger(0)
    val key = java.util.concurrent.atomic.AtomicReference("synthetic-key")
    val version = java.util.concurrent.atomic.AtomicReference("7")
    fun node(): String =
        """{"pkgName":"synthetic.catalog","name":"Synthetic catalog extension","storeIndexUrl":"$url","isInstalled":${installed.get()},"isObsolete":false,"versionCodeLong":"${version.get()}","versionName":"1.${version.get()}","source":{"nodes":[{"id":"42"}]}}"""
    server.createContext("/api/graphql") { exchange ->
        try {
            val q = exchange.requestBody.readNBytes(200001).toString(Charsets.UTF_8)
            val body = when {
                "NooirBackupStores" in q ->
                    """{"data":{"extensionStores":{"totalCount":1,"nodes":[{"name":"Synthetic","badgeLabel":"QA","signingKey":"${key.get()}","contactWebsite":"https://example.org","contactDiscord":null,"indexUrl":"$url","isLegacy":true,"extensionListUrl":null}]}}}"""
                "NooirBackupCatalog" in q ->
                    """{"data":{"extensions":{"totalCount":1,"nodes":[${node()}]}}}"""
                "NooirRecoverExtension" in q -> {
                    check("synthetic.catalog" in q && "install: true" in q)
                    calls.incrementAndGet()
                    check(installed.compareAndSet(false,true))
                    """{"data":{"updateExtension":{"extension":${node()}}}}"""
                }
                else -> error("Unexpected operation")
            }.toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        } finally { exchange.close() }
    }
    server.start()
    try {
        val api = DesktopBackupTransport("http://127.0.0.1:" + server.address.port)
        val store = api.extensionStores().single()
        val item = api.extensionCatalog().single()
        check(!item.installed && calls.get() == 0)
        check(runCatching { api.installCatalogExtension(item.copy(versionCode = null), store) }.isFailure)
        key.set("changed-key")
        check(runCatching { api.installCatalogExtension(item, store) }.isFailure)
        key.set("synthetic-key")
        version.set("8")
        check(runCatching { api.installCatalogExtension(item, store) }.isFailure)
        check(calls.get() == 0)
        version.set("7")
        check(api.installCatalogExtension(item, store) == setOf(42L))
        check(calls.get() == 1 && api.extensionCatalog().single().installed)
        check(runCatching { api.installCatalogExtension(item, store) }.isFailure)
        check(calls.get() == 1)
        println("PASS: reviewed catalogue install and stale identity refusal")
    } finally { server.stop(0) }
}
