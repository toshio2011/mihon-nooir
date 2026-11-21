package dev.nooir.mangabridge

/**
 * Detect public chapters added since the last saved story inventory.
 * Comparing all discovered chapters (including excluded ones) prevents
 * the app from mistaking intentionally skipped chapters for new updates.
 */
internal object NooirWebChapterUpdates {
    fun findNew(
        knownUrls: List<String>,
        discovered: List<NooirWebChapterPlan.Entry>,
    ): List<NooirWebChapterPlan.Entry> {
        require(knownUrls.isNotEmpty()) { "No saved chapter inventory to compare" }
        val known = knownUrls.map(String::trim).toSet()
        return discovered.filterNot { it.url.trim() in known }
            .distinctBy { it.url.trim() }
    }

    fun append(
        selectedUrls: List<String>,
        selectedTitles: List<String>,
        newChapters: List<NooirWebChapterPlan.Entry>,
    ): NooirWebChapterPlan.Export {
        val old = selectedUrls.mapIndexed { index, url ->
            NooirWebChapterPlan.Entry(url, selectedTitles.getOrNull(index).orEmpty())
        }
        // When a newly discovered URL is already in the selected chapters
        // (e.g. an imported manual URL), never duplicate it in the EPUB.
        val selected = selectedUrls.map(String::trim).toSet()
        val added = newChapters.filterNot { it.url.trim() in selected }
            .distinctBy { it.url.trim() }
        return NooirWebChapterPlan.export(old + added)
    }
}
