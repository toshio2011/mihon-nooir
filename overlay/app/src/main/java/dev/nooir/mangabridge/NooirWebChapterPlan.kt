package dev.nooir.mangabridge

/**
 * Pure selection/ordering state for the public Web → EPUB chapter analyzer.
 * No network or Android dependencies, so selections are testable off-device.
 */
internal object NooirWebChapterPlan {
    data class Entry(
        val url: String,
        val title: String,
        val included: Boolean = true,
    )

    data class Export(val urls: List<String>, val titles: List<String>)

    fun start(chapters: List<Entry>): List<Entry> {
        require(chapters.isNotEmpty()) { "No publicly listed chapters" }
        require(chapters.map { it.url }.distinct().size == chapters.size) {
            "The chapter list contains duplicate links"
        }
        return chapters
    }

    fun chooseAll(entries: List<Entry>, include: Boolean) =
        entries.map { it.copy(included = include) }

    fun toggle(entries: List<Entry>, index: Int): List<Entry> =
        entries.mapIndexed { i, item ->
            if (i == index) item.copy(included = !item.included) else item
        }

    fun reverse(entries: List<Entry>) = entries.reversed()

    fun move(entries: List<Entry>, from: Int, to: Int): List<Entry> {
        if (from !in entries.indices || to !in entries.indices || from == to) return entries
        val copy = entries.toMutableList()
        val item = copy.removeAt(from)
        copy.add(to, item)
        return copy
    }

    fun export(entries: List<Entry>): Export {
        val selected = entries.filter { it.included }
        require(selected.isNotEmpty()) { "Select at least one chapter" }
        val links = selected.map { it.url.trim() }
        require(links.distinct().size == links.size) { "Repeated chapter links" }
        require(links.all { it.length in 1..600 && it.startsWith("https://", true) }) {
            "A selected chapter is not a supported HTTPS link"
        }
        require(links.joinToString("\n").toByteArray(Charsets.UTF_8).size <= 120_000) {
            "The selected chapter list is too large for one export manifest. Select fewer chapters."
        }
        val titles = selected.map { it.title.replace(Regex("\\s+"), " ").trim().take(160) }
        require(titles.joinToString("\n").toByteArray(Charsets.UTF_8).size <= 120_000) {
            "Chapter titles exceed the selection budget"
        }
        return Export(links, titles)
    }
}
