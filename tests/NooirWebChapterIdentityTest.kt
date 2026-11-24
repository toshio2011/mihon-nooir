import dev.nooir.mangabridge.NooirWebChapterIdentity as Identity

fun main() {
    check(Identity.key("https://EXAMPLE.org/read/12/?utm_source=abc#reader") ==
        "https://example.org/read/12")
    check(Identity.key("https://EXAMPLE.org/read/12?fbclid=z&part=3&utm_medium=social") ==
        "https://example.org/read/12?part=3")
    check(Identity.key("https://example.org/reader?chapter=2") !=
        Identity.key("https://example.org/reader?chapter=3"))
    check(Identity.key("https://example.org/book?lang=en&chapter=2&utm_source=a") ==
        Identity.key("https://EXAMPLE.ORG/book?chapter=2&lang=en#reader"))
    check(Identity.key("https://example.org/book?part=1&part=2") !=
        Identity.key("https://example.org/book?part=2&part=1"))
    check(Identity.key("https://example.org/Chapter/12") !=
        Identity.key("https://example.org/chapter/12"))
    check(Identity.key("https://example.org/read/12?token=public") ==
        "https://example.org/read/12?token=public")
    check(Identity.key("not a valid URL") == "not a valid URL")
    println("PASS: chapter inventory identities strip trackers but preserve real chapter parameters")
}
