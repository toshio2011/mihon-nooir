import dev.nooir.mangabridge.NooirWebExtractRules as Rules

fun main() {
    check(Rules.valid("", ""))
    check(Rules.valid("article", ".advertisement, .social-share"))
    check(Rules.valid("#chapter-content", "div.ad"))
    check(Rules.valid("main .chapter-body, article.post-content", "aside, nav"))
    check(Rules.valid(".reading-content p", ".popup"))
    check(!Rules.valid("article:has(script)", ""))
    check(!Rules.valid("article [data-private]", ""))
    check(!Rules.valid("article > p", ""))
    check(!Rules.valid("article, , main", ""))
    check(!Rules.valid("article", ".bad:contains(secret)"))
    check(!Rules.valid("a".repeat(161), ""))
    check(!Rules.valid("main", "#x, #y, #z, #a, #b"))
    check(!Rules.valid("article div p span strong em", ""))
    println("PASS: bounded public chapter extraction selectors and unsafe CSS rejection")
}
