package dev.nooir.mangabridge

/** Select full-size and lazy-load image candidates without WebToEpub source code.
 * HTTPS validation, byte budgets and media decoding remain in the caller.
 */
internal object NooirWebImageCandidates {
    private data class Entry(val url: String, val resolution: Double)

    fun preferred(
        original: String = "", lazyOriginal: String = "",
        dataSrcset: String = "", srcset: String = "",
        dataSrc: String = "", src: String = "",
    ): List<String> {
        val result = mutableListOf<String>()
        fun add(raw: String) {
            val value = raw.trim()
            if (value.isNotBlank() && value.length <= 1200 &&
                !value.startsWith("data:", true) &&
                !value.startsWith("javascript:", true) &&
                !value.startsWith("blob:", true) && value !in result) result.add(value)
        }
        add(original)
        add(lazyOriginal)
        for (value in listOf(dataSrcset, srcset)) {
            val candidates = value.take(6000).split(',').take(18).mapNotNull { item ->
                val pieces = item.trim().split(Regex("\\s+"))
                if (pieces.isEmpty() || pieces[0].isBlank()) return@mapNotNull null
                val weight = when {
                    pieces.size == 1 -> 1.0
                    pieces[1].endsWith("w") -> pieces[1].dropLast(1).toDoubleOrNull()
                    pieces[1].endsWith("x") ->
                        pieces[1].dropLast(1).toDoubleOrNull()?.times(1200.0)
                    else -> null
                }
                if (weight == null || !weight.isFinite() || weight <= 0.0) null
                else Entry(pieces[0], weight)
            }
            candidates.sortedByDescending { it.resolution }.forEach { add(it.url) }
        }
        add(dataSrc)
        add(src)
        return result.take(8)
    }
}
