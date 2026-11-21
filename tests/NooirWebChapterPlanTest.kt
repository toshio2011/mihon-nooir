import dev.nooir.mangabridge.NooirWebChapterPlan as Plan

fun main() {
    val initial = Plan.start(listOf(
        Plan.Entry("https://example.org/chap-1", "Prologue"),
        Plan.Entry("https://example.org/chap-2", "Chapter Two"),
        Plan.Entry("https://example.org/chap-3", "Chapter Three"),
        Plan.Entry("https://example.org/chap-4", "Epilogue"),
    ))
    check(initial.size == 4)
    check(Plan.chooseAll(initial, false).none { it.included })
    check(Plan.chooseAll(Plan.chooseAll(initial, false), true).all { it.included })
    val selected = Plan.toggle(Plan.toggle(initial, 1), 3)
    check(Plan.export(selected).titles == listOf("Prologue", "Chapter Three"))
    check(Plan.export(selected).urls == listOf("https://example.org/chap-1", "https://example.org/chap-3"))
    val moved = Plan.move(initial, 3, 0)
    check(moved.map { it.title } ==
        listOf("Epilogue", "Prologue", "Chapter Two", "Chapter Three"))
    check(Plan.export(Plan.reverse(selected)).titles == listOf("Chapter Three", "Prologue"))
    check(Plan.move(initial, -1, 2) == initial)
    check(Plan.move(initial, 1, 999) == initial)
    check(runCatching { Plan.export(Plan.chooseAll(initial, false)) }.isFailure)
    check(runCatching { Plan.start(emptyList()) }.isFailure)
    check(runCatching { Plan.start(listOf(initial[0], initial[0])) }.isFailure)
    check(runCatching { Plan.export(listOf(Plan.Entry("http://localhost/private", "private"))) }.isFailure)
    check(runCatching { Plan.export(listOf(Plan.Entry("https://example.org/chapter", "title"))
        + Plan.Entry("https://example.org/chapter", "repeated")) }.isFailure)
    val overlyLarge = (0..250).map { i ->
        Plan.Entry("https://example.org/chapter/$i/" + "x".repeat(510), "Chapter $i")
    }
    check(runCatching { Plan.export(overlyLarge) }.isFailure) { "Oversized manifest was accepted" }
    check(Plan.export(listOf(Plan.Entry("https://example.org/chapter", " Chapter\nOne ")))
        .titles.single() == "Chapter One")
    println("PASS: Web → EPUB chapter checklist, reordering, single-book manifest validation")
}
