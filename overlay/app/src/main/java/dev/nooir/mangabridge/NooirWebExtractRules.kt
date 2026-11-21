package dev.nooir.mangabridge

/**
 * Bounded, deliberately small CSS selector vocabulary for public HTML imports.
 * This allows ordinary tags, ids, classes, descendant selectors and comma
 * alternatives, but rejects costly pseudo-selectors, regexes and attributes.
 * The same validation is used by preview and the background EPUB exporter.
 */
internal object NooirWebExtractRules {
    private val token = Regex("(?:[A-Za-z][A-Za-z0-9_-]*)?(?:[.#][A-Za-z_][A-Za-z0-9_-]*)*")
    private val whitespace = Regex("\\s+")

    fun validate(contentSelector: String, removeSelector: String) {
        validateOne(contentSelector, "Content selector")
        validateOne(removeSelector, "Removal selector")
    }

    fun valid(contentSelector: String, removeSelector: String): Boolean =
        runCatching { validate(contentSelector, removeSelector) }.isSuccess

    private fun validateOne(value: String, label: String) {
        require(value.length <= 160) { label + " must be 160 characters or less" }
        if (value.isBlank()) return
        val groups = value.trim().split(',')
        require(groups.size in 1..4) { label + " supports up to four comma-separated selectors" }
        for (group in groups) {
            val parts = group.trim().split(whitespace)
            require(parts.size in 1..5 &&
                parts.all { it.isNotBlank() && token.matches(it) }) {
                label + " only supports tags, #ids, .classes and descendant spaces; " +
                    "complex CSS, pseudo-selectors and attributes are not supported"
            }
        }
    }
}
