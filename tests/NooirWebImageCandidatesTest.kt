import dev.nooir.mangabridge.NooirWebImageCandidates as Candidates

fun main() {
    check(Candidates.preferred(
        original = "https://example.org/full.jpg",
        dataSrcset = "thumb.jpg 320w, med.jpg 640w, full.jpg 1200w",
        src = "data:image/gif;base64,aaaa",
    ) == listOf("https://example.org/full.jpg", "full.jpg", "med.jpg", "thumb.jpg"))
    check(Candidates.preferred(srcset = "large.jpg 2x, tiny.jpg 1x").first() == "large.jpg")
    check(Candidates.preferred(srcset = "\n", src = "https://cdn.example.org/ok.png")
        == listOf("https://cdn.example.org/ok.png"))
    check(Candidates.preferred(original = "javascript:alert(1)", lazyOriginal = "blob:abc",
        src = "data:stuff").isEmpty())
    check(Candidates.preferred(original = "photo.jpg", dataSrc = "photo.jpg", src = "photo.jpg")
        == listOf("photo.jpg"))
    println("PASS: lazy/full-size illustration priority, srcset parsing and unsafe scheme exclusion")
}
