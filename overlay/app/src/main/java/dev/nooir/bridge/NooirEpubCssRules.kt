package dev.nooir.bridge

/**
 * User-owned EPUB style overrides, constrained to text-friendly offline CSS.
 * The canonical output is safe to embed in XHTML <style>: no arbitrary HTML,
 * external URLs, scripts, at-rules, pseudo-selectors or browser extensions.
 */
object NooirEpubCssRules {
    private val selectors = setOf(
        "body", "p", "h1", "h2", "h3", "blockquote", "pre", "img",
        "figure", "figcaption", "ul", "ol", "li", "table", "th", "td",
    )
    private val length = Regex("(?i)(?:0|[0-9]{1,3}(?:\\.[0-9]{1,2})?(?:em|rem|pt|%))")
    private val lineHeight = Regex("(?i)(?:normal|[0-9](?:\\.[0-9]{1,2})?(?:em|rem|%)?)")
    private val hexColor = Regex("#[0-9A-Fa-f]{6}")

    fun valid(input: String): Boolean = runCatching { canonicalize(input) }.isSuccess

    fun canonicalize(input: String): String {
        val source = input.trim()
        if (source.isBlank()) return ""
        require(source.length <= 2000) { "EPUB CSS overrides must be 2000 characters or fewer" }
        require(source.endsWith("}")) { "Each EPUB CSS rule must end with }" }
        val parts = source.dropLast(1).split('}')
        require(parts.size in 1..12) { "Use no more than 12 simple EPUB CSS rules" }
        return parts.map { raw ->
            val pair = raw.split('{')
            require(pair.size == 2) { "Use CSS in selector { property: value; } form" }
            val keys = pair[0].split(',').map { it.trim().lowercase() }
            require(keys.size in 1..4 && keys.all { it in selectors }) {
                "CSS selectors must be plain EPUB text tags (body, p, h1, img, etc.)"
            }
            val fields = pair[1].split(';').map(String::trim).filter(String::isNotBlank)
            require(fields.size in 1..12) { "Use up to 12 declarations per CSS rule" }
            val declarations = fields.map { field ->
                val colon = field.indexOf(':')
                require(colon > 0) { "Each CSS declaration needs a property and value" }
                val key = field.substring(0, colon).trim().lowercase()
                val value = field.substring(colon + 1).trim().lowercase()
                val accepted = when (key) {
                    "font-size", "text-indent", "letter-spacing" ->
                        length.matches(value)
                    "line-height" -> lineHeight.matches(value)
                    "margin", "margin-top", "margin-bottom", "margin-left", "margin-right",
                    "padding", "padding-top", "padding-bottom" ->
                        value.split(Regex("\\s+")).size in 1..4 &&
                            value.split(Regex("\\s+")).all { length.matches(it) }
                    "text-align" -> value in setOf("left", "right", "center", "justify", "start", "end")
                    "font-family" -> value in setOf("serif", "sans-serif", "monospace")
                    "font-weight" -> value in setOf("normal", "bold")
                    "hyphens" -> value in setOf("auto", "none", "manual")
                    "color", "background-color" -> hexColor.matches(value)
                    else -> false
                }
                require(accepted) { "Unsupported EPUB CSS property or value: $key" }
                "$key:$value"
            }
            keys.joinToString(",") + "{" + declarations.joinToString(";") + "}"
        }.joinToString("")
    }
}
