import dev.nooir.mangabridge.NooirWebTocPagination as Pager

fun main() {
    fun link(url: String, pager: Boolean = true, next: Boolean = false) =
        Pager.Candidate(url, pager, next)
    check(Pager.next("https://example.org/story/chapters", listOf(
        link("/story/chapters?chapter=2", next = true),
        link("?page=2", next = true))) == "https://example.org/story/chapters?page=2")
    check(Pager.next("https://example.org/story/chapters?page=2", listOf(
        link("?page=3"))) == "https://example.org/story/chapters?page=3")
    check(Pager.next("https://example.org/story/chapters/", listOf(
        link("/story/chapters/page/2/"))) == "https://example.org/story/chapters/page/2/")
    check(Pager.next("https://example.org/story/chapters/page/2/", listOf(
        link("/story/chapters/page/3/"))) == "https://example.org/story/chapters/page/3/")
    check(Pager.next("https://example.org/story/chapters?sort=old", listOf(
        link("?page=2&sort=old"))) == "https://example.org/story/chapters?page=2&sort=old")
    check(Pager.next("https://example.org/story/chapters", listOf(
        link("https://evil.example/story/chapters?page=2", next = true))) == null)
    check(Pager.next("https://example.org/story/chapters", listOf(
        link("/story/chapter/2", next = true))) == null)
    check(Pager.next("https://example.org/story/chapters", listOf(
        link("?page=2", pager = false, next = false))) == null)
    check(Pager.next("https://example.org/story/chapters", listOf(
        link("?page=2&chapter=45"))) == null)
    check(runCatching { Pager.next("https://example.org/story/chapters", listOf(
        link("?page=2")), setOf("https://example.org/story/chapters?page=2")) }.isFailure)
    check(Pager.next("https://example.org/story/chapters?utm_source=x", listOf(
        link("?page=2&fbclid=click"))) == "https://example.org/story/chapters?page=2&fbclid=click")
    check(Pager.sameCollection(
        "https://example.org/story/chapters?page=2",
        "https://example.org/story/chapters/?page=2"))
    check(Pager.sameCollection(
        "https://example.org/story/chapters/page/2/",
        "https://example.org/story/chapters/page/2"))
    check(!Pager.sameCollection(
        "https://example.org/story/chapters?page=2",
        "https://example.org/story/chapter/2"))
    check(!Pager.sameCollection(
        "https://example.org/story/chapters?page=2",
        "https://example.org/story/chapters?page=1"))
    check(!Pager.sameCollection(
        "https://example.org/story/chapters?page=2",
        "https://another.example.org/story/chapters?page=2"))
    check(Pager.MAX_PAGES == 12)
    println("PASS: numbered same-site TOC pagination only; no next-chapter or cross-site traversal")
}
