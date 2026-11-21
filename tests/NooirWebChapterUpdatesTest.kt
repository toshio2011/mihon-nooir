import dev.nooir.mangabridge.NooirWebChapterPlan as Plan
import dev.nooir.mangabridge.NooirWebChapterUpdates as Update

fun main() {
    val latest = listOf(
        Plan.Entry("https://example.org/ch1", "One"),
        Plan.Entry("https://example.org/ch2", "Two"),
        Plan.Entry("https://example.org/ch3", "Three"),
        Plan.Entry("https://example.org/ch4", "Four"),
    )
    // Chapter 2 was knowingly excluded: it must not reappear as a new update.
    val found = Update.findNew(listOf(latest[0].url, latest[1].url), latest)
    check(found.map { it.title } == listOf("Three", "Four"))
    val updated = Update.append(listOf(latest[0].url), listOf("Custom One"), found)
    check(updated.titles == listOf("Custom One", "Three", "Four"))
    check(updated.urls == listOf(latest[0].url, latest[2].url, latest[3].url))
    val deduplicated = Update.append(listOf(latest[0].url, latest[2].url),
        listOf("One", "Selected Three"), found)
    check(deduplicated.titles == listOf("One", "Selected Three", "Four"))
    check(Update.findNew(latest.map { it.url }, latest).isEmpty())
    check(runCatching { Update.findNew(emptyList(), latest) }.isFailure)
    println("PASS: known-excluded chapters, updates, deduplication, preserved custom titles")
}
