import dev.nooir.bridge.PdfReflowLines

private fun expect(value: Boolean, message: String) {
    if (!value) error(message)
}

fun main() {
    val l = PdfReflowLines::Line
    // Synthetic novel page: large initial S is emitted after the body; centered
    // page number is at the footer, and Malay repeated words must keep hyphens.
    val lines = listOf(
        l("1", 207f, 541f, 11f),
        l("uatu masa dulu,", 93f, 179f, 16f),
        l("bahawa hati yang setia", 93f, 207f, 16f),
        l("seolah-", 93f, 235f, 16f),
        l("olah ada.", 93f, 263f, 16f),
        l("Aku cuba untuk kejar kembali.", 88f, 299f, 16f),
        l("S", 28f, 248f, 96f),
    )
    val result = PdfReflowLines.paragraphs(lines, 420f, 595f)
    expect(result.size == 2, "Expected two paragraphs, got $result")
    expect(result[0].text.startsWith("Suatu masa dulu,"), "Opening drop cap was lost: $result")
    expect("seolah-olah ada." in result[0].text, "Hyphenated Malay word was damaged: $result")
    expect(result.none { it.text.trim() == "1" || " 1 " in it.text }, "Printed page number leaked into prose")
    expect(result[1].text == "Aku cuba untuk kejar kembali.", "Paragraph separation failed")

    val quoted = PdfReflowLines.paragraphs(listOf(
        l("“K", 52f, 275f, 125f),
        l("au ni tak habis lagi ke", 181f, 184f, 16f),
        l("ketawa tu?”", 181f, 212f, 16f),
        l("288", 201f, 541f, 11f),
    ), 420f, 595f)
    expect(quoted.first().text.startsWith("“Kau ni"), "Quoted opening drop cap detached: $quoted")
    expect(quoted.none { "288" in it.text }, "Numeric footer not removed")

    val noText = PdfReflowLines.paragraphs(listOf(l("307", 201f, 541f, 11f)), 420f, 595f)
    expect(noText.isEmpty(), "Image-only final page needs image fallback")

    val normalNumbers = PdfReflowLines.paragraphs(listOf(
        l("2026 ialah tahun yang indah.", 65f, 250f, 16f),
    ), 420f, 595f)
    expect(normalNumbers.single().text.startsWith("2026"), "Removed legitimate paragraph number")

    println("PDF geometry regression tests passed")
}
