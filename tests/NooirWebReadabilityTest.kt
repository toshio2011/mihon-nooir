import dev.nooir.mangabridge.NooirWebReadability as Readability

fun main() {
    check(Readability.score(380, 5, 20) > Readability.score(1400, 0, 1350))
    check(Readability.score(700, 10, 0) > Readability.score(700, 2, 100))
    check(Readability.score(900, 0, 900) < 0)
    check(Readability.score(300, 3, 0) == 480)
    check(Readability.score(Int.MAX_VALUE, Int.MAX_VALUE, 0) == Int.MAX_VALUE)
    check(runCatching { Readability.score(-1, 2, 0) }.isFailure)
    println("PASS: prose scoring outranks menus and rejects invalid lengths")
}
