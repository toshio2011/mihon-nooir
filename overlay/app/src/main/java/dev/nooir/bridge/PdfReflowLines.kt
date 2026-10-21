package dev.nooir.bridge

import kotlin.math.abs
import kotlin.math.max

/**
 * Small, offline PDF text-layout helper. Works on positioned text fragments and
 * never attempts OCR or guesses missing text. Kept independent of Android/PDFBox
 * so paragraph and decorative drop-cap behavior can be unit-tested in JVM CI.
 */
object PdfReflowLines {
    data class Line(val text: String, val x: Float, val y: Float, val fontSize: Float)
    data class Paragraph(val y: Float, val text: String)

    fun paragraphs(source: List<Line>, pageWidth: Float, pageHeight: Float): List<Paragraph> {
        if (source.isEmpty()) return emptyList()

        // A single centered digit at the bottom of a novel page is the printed
        // page number, not part of the paragraph. Do not strip other numbers.
        val filtered = source.filter { line ->
            val t = line.text.trim()
            t.isNotEmpty() && !(t.matches(Regex("[0-9]{1,5}")) &&
                line.y >= pageHeight * 0.84f &&
                line.x in (pageWidth * 0.33f)..(pageWidth * 0.68f))
        }.toMutableList()
        if (filtered.isEmpty()) return emptyList()

        val fontSizes = filtered.map { it.fontSize }.filter { it > 0f }.sorted()
        val font = if (fontSizes.isNotEmpty()) fontSizes[fontSizes.size / 2] else 12f

        // Large opening letters are often emitted *after* several smaller lines
        // in the PDF text stream. Attach one to its overlapping opening line
        // using geometry instead of its original text extraction order.
        val dropCaps = filtered.filter { item ->
            item.fontSize > font * 1.8f && item.text.trim().length in 1..3 &&
                item.text.any { it.isLetter() } &&
                item.x <= pageWidth * 0.46f &&
                item.y <= pageHeight * 0.7f
        }.sortedBy { it.y }
        for (cap in dropCaps) {
            if (!filtered.contains(cap)) continue
            val top = cap.y - cap.fontSize
            val target = filtered.asSequence().filter { it !== cap &&
                it.fontSize < cap.fontSize * 0.65f &&
                it.y >= top - font * 0.65f &&
                it.y <= cap.y - cap.fontSize * 0.3f &&
                it.x >= cap.x
            }.minByOrNull { abs(it.y - (top + font)) +
                abs(it.x - (cap.x + cap.fontSize * 0.6f)) * 0.15f }
            if (target != null) {
                val i = filtered.indexOf(target)
                // When a drop cap carries punctuation such as “K, keep it.
                filtered[i] = target.copy(text = cap.text.trim() + target.text.trimStart())
                filtered.remove(cap)
            }
        }

        // PDFBox may send several positioned fragments at the same baseline.
        // Recombine them in horizontal order before estimating paragraphs.
        val byPosition = filtered.sortedWith(compareBy<Line> { it.y }.thenBy { it.x })
        val visualLines = mutableListOf<Line>()
        for (item in byPosition) {
            val previous = visualLines.lastOrNull()
            if (previous != null && abs(previous.y - item.y) <= max(2.5f, font * 0.17f)) {
                val separator = if (previous.text.endsWith('-') || item.text.startsWith(',') ||
                    item.text.startsWith('.') || item.text.startsWith('!') ||
                    item.text.startsWith('?')) "" else " "
                visualLines[visualLines.lastIndex] = previous.copy(
                    text = previous.text + separator + item.text.trim(),
                )
            } else {
                visualLines += item.copy(text = item.text.trim())
            }
        }
        if (visualLines.size == 1) return listOf(
            Paragraph(visualLines.first().y, visualLines.first().text),
        )

        val gaps = visualLines.zipWithNext { a, b -> b.y - a.y }
            .filter { it > max(3f, font * 0.4f) }.sorted()
        val typicalGap = if (gaps.isNotEmpty()) gaps[gaps.size / 2] else font * 1.5f
        val paragraphGap = max(typicalGap * 1.22f, font * 1.25f)

        val paragraphs = mutableListOf<Paragraph>()
        var firstY = visualLines.first().y
        var previousY = firstY
        val buffer = StringBuilder()
        fun finish() {
            if (buffer.isNotEmpty()) {
                paragraphs += Paragraph(firstY, buffer.toString().trim())
                buffer.clear()
            }
        }
        for (line in visualLines) {
            if (buffer.isNotEmpty() && line.y - previousY > paragraphGap) finish()
            if (buffer.isEmpty()) firstY = line.y
            else if (buffer.endsWith("\u00ad")) buffer.deleteCharAt(buffer.lastIndex)
            else if (!buffer.endsWith("-")) buffer.append(' ')
            // Keep genuine hyphens on Malay reduplications (e.g. seolah-olah).
            buffer.append(line.text)
            previousY = line.y
        }
        finish()
        return paragraphs
    }
}
