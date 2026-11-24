import dev.nooir.mangabridge.NooirWebImageProfile as Profile

fun main() {
    check(Profile.parse("ORIGINAL") == Profile.ORIGINAL)
    check(Profile.parse("BALANCED").maxEdge == 1600)
    check(Profile.parse("SMALL").maxEdge == 1000)
    check(Profile.BALANCED.jpegQuality > Profile.SMALL.jpegQuality)
    check(Profile.ORIGINAL.maxEdge == 0)
    check(runCatching { Profile.parse("UNSAFE") }.isFailure)
    println("PASS: bounded web illustration quality presets, invalid input rejected")
}
