package dev.nooir.bridge

/** Stable human-friendly filename ordering: page 2 comes before page 10. */
object NaturalPageOrder {
    private val pieces = Regex("(\\d+|\\D+)")
    fun compare(first: String, second: String): Int {
        val a = pieces.findAll(first).map { it.value }.toList()
        val b = pieces.findAll(second).map { it.value }.toList()
        for (i in 0 until minOf(a.size, b.size)) {
            val x = a[i]; val y = b[i]
            val diff = if (x.all { it.isDigit() } && y.all { it.isDigit() }) {
                val nx=x.trimStart('0').ifBlank { "0" }
                val ny=y.trimStart('0').ifBlank { "0" }
                nx.length.compareTo(ny.length).takeIf { it != 0 }
                    ?: nx.compareTo(ny)
            } else x.compareTo(y, ignoreCase=true)
            if (diff != 0) return diff
        }
        return a.size.compareTo(b.size)
    }
}