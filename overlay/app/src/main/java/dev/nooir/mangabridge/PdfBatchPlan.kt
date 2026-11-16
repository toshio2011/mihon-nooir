package dev.nooir.mangabridge

/**
 * PDF batch filenames only. No Android APIs or remote file access.
 *
 * User-selected documents are processed sequentially as separate books; duplicate
 * names receive deterministic suffixes rather than overwriting one another.
 * The batch-count bound protects WorkManager's persisted job-chain overhead; it
 * is NOT a PDF page limit. Additional batches can always be queued.
 */
object PdfBatchPlan {
    const val MAX_SELECTION = 50

    fun outputStems(names: List<String>): List<String> {
        require(names.size in 1..MAX_SELECTION) {
            "Choose 1 to $MAX_SELECTION PDF files per batch; queue more batches separately"
        }
        val used = mutableSetOf<String>()
        return names.mapIndexed { index, original ->
            val withoutExt = original.trim().substringBeforeLast('.', original.trim())
            val base = withoutExt.take(55).map { ch ->
                if (ch.code < 128 && (ch.isLetterOrDigit() || ch == '-' || ch == '_')) ch else '_'
            }.joinToString("").trim('_').ifBlank { "PDF_" + (index + 1) }
            var result = base
            var suffix = 2
            while (!used.add(result.lowercase(java.util.Locale.ROOT))) {
                result = base + "_" + suffix++
            }
            result
        }
    }
}
