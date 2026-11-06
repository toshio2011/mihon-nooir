package dev.nooir.desktop.suwayomi

import java.io.File

/**
 * Explicit CI-only live integration test against the pinned official Suwayomi JAR.
 * No manga sources, extensions, user credentials, or private content are used.
 * No server is started automatically by the Windows app.
 */
object RealSuwayomiServerSmoke {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "Pass the verified official Suwayomi server JAR path" }
        val jar = File(args.single()).canonicalFile
        val manager = SuwayomiProcessManager()
        try {
            val endpoint = manager.start(jar, startupTimeoutSeconds = 150)
            check(endpoint == "http://127.0.0.1:4567") {
                "Server unexpectedly bound to a different endpoint: $endpoint"
            }
            check(manager.running) { "Managed server stopped before API test" }

            val api = SuwayomiClient(endpoint)
            val sources = api.sources(offset = 0, limit = 1)
            check(sources.totalCount >= 0) { "Invalid source count" }
            val library = api.library(offset = 0, limit = 1)
            check(library.totalCount >= 0) { "Invalid library count" }

            println("PASS: official Suwayomi API responded to real empty-library/source queries on loopback")
            println("No extensions, manga or user data were downloaded.")
        } finally {
            manager.close()
        }
        check(!manager.running) { "Managed Suwayomi process survived close()" }
        println("PASS: MihonNooir shut down only its own managed Suwayomi server")
    }
}
