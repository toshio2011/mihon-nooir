import dev.nooir.mangabridge.PdfBatchPlan

fun main() {
    check(PdfBatchPlan.outputStems(listOf("One.pdf")) == listOf("One"))
    check(PdfBatchPlan.outputStems(listOf("One.pdf", "one.PDF", "One.pdf")) ==
        listOf("One", "one_2", "One_3"))
    check(PdfBatchPlan.outputStems(listOf("a/b.pdf", "a\\b.PDF", ".pdf")).size == 3)
    check(PdfBatchPlan.outputStems(listOf("東京.pdf")) == listOf("PDF_1"))
    check(PdfBatchPlan.outputStems(List(50) { "book.pdf" }).distinct().size == 50)
    check(runCatching { PdfBatchPlan.outputStems(emptyList()) }.isFailure)
    check(runCatching { PdfBatchPlan.outputStems(List(51) { "book.pdf" }) }.isFailure)
    println("PASS: PDF batch names are safe, distinct and preserve order; no limit on page count")
}
