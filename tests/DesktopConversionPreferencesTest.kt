package dev.nooir.desktop.ui

import dev.nooir.mangabridge.MangaConversionOptions
import java.nio.file.Files

fun main() {
    val root = Files.createTempDirectory("nooir-conversion-defaults-")
    try {
        val data = root.resolve("first")
        val other = root.resolve("second")
        val store = DesktopConversionPreferences { data }
        val defaults=MangaConversionOptions.preset("Manga")
        val path=data.resolve(DesktopConversionPreferences.FILE_NAME)
        check(store.load()==DesktopConversionPreferences.Loaded(defaults))
        check(!Files.exists(data)) // reading defaults must not create anything
        val selected=defaults.copy(preset="Custom",panelMode="ROW_BY_ROW",orientation="AUTO_FIT",
            enlargeOversizedSplits=false,minimumScreenFillPercent=65,renderAlgorithm="ATKINSON",
            overlapPercent=19,keepChapterFirstPageWhole=false)
        store.save(selected)
        val readAgain=DesktopConversionPreferences { data }
        check(readAgain.load()==DesktopConversionPreferences.Loaded(selected))
        check(DesktopConversionPreferences { other }.load().options==defaults && !Files.exists(other))
        store.save(selected.copy(allowMissingCover=true,skipUnavailableChapters=true,panelDiagnostics=true))
        check(readAgain.load().options==selected)
        // Queue defaults never rewrite older job tokens; validate actual durable token migration.
        val oldToken=selected.token().split("|").take(22).joinToString("|")
        Files.writeString(path,oldToken+"\n")
        check(readAgain.load().options==selected.copy(enlargeOversizedSplits=true,minimumScreenFillPercent=50))
        val before=Files.readAllBytes(path)
        check(runCatching { store.save(selected.copy(preset="bad|separator")) }.isFailure)
        check(Files.readAllBytes(path).contentEquals(before))
        for (broken in listOf("invalid",selected.token().replace("|65","|99"),"x".repeat(4097))) {
            Files.writeString(path,broken)
            val loaded=readAgain.load()
            check(loaded.options==defaults && loaded.warning!=null)
            check(Files.readString(path)==broken) // read doesn't delete or repair corrupt input
        }
        store.save(selected)
        check(readAgain.load().options==selected)
        check(Files.list(data).use { it.count() } == 1L)
        Files.delete(path);Files.createDirectory(path)
        check(readAgain.load().warning!=null)
        check(runCatching { store.save(selected) }.isFailure)
        check(Files.isDirectory(path))
        println("PASS: durable conversion defaults, legacy settings, isolated roots, one-job flags excluded, malformed input retained, atomic replace")
    } finally { root.toFile().deleteRecursively() }
}
