import dev.nooir.bridge.ReflowEpubWriter
import dev.nooir.bridge.ReflowEpubFormat
import dev.nooir.bridge.ReflowMetadata
import java.io.File
import java.util.zip.ZipFile

fun main() {
    val temp = File.createTempFile("nooir-reflow-", ".epub")
    try {
        ReflowEpubWriter.write(temp, ReflowMetadata(
            title = "Test & Diagrams",
            author = "Someone", description = "Mixed book <PDF>",
        )) { epub ->
            val asset = ReflowEpubWriter.ImageAsset(byteArrayOf(1,2,3,4), "png", "image/png")
            val name = epub.addImage(asset)
            check(name == "../images/img00001.png")
            epub.addSection("Chapter 1", "<h1>Chapter 1</h1><p>Select this text</p>" +
                "<figure><img src=\"$name\" alt=\"floor plan\"/></figure><p>More text after illustration</p>")
            epub.addSection("Scanned appendix", "<p>A later chapter</p>")
            epub.addSection("Third public webpage", "<p>Chapter three in reading order</p>")
        }
        ZipFile(temp).use { zip ->
            val first = zip.entries().nextElement()
            check(first.name == "mimetype" && first.method == java.util.zip.ZipEntry.STORED)
            check(String(zip.getInputStream(first).readBytes()) == "application/epub+zip")
            val xml = String(zip.getInputStream(zip.getEntry("OEBPS/text/s00001.xhtml")).readBytes())
            check(xml.contains("Select this text") && xml.contains("floor plan"))
            check(xml.contains("../images/img00001.png"))
            val opf = String(zip.getInputStream(zip.getEntry("OEBPS/package.opf")).readBytes())
            check(opf.contains("text/s00001.xhtml") && opf.contains("text/s00002.xhtml"))
            check(opf.contains("images/img00001.png"))
            check(opf.contains("Test &amp; Diagrams"))
            val nav = String(zip.getInputStream(zip.getEntry("OEBPS/nav.xhtml")).readBytes())
            val firstTitle = nav.indexOf("Chapter 1")
            val secondTitle = nav.indexOf("Scanned appendix")
            val thirdTitle = nav.indexOf("Third public webpage")
            check(firstTitle >= 0 && secondTitle > firstTitle && thirdTitle > secondTitle)
            check(opf.contains("text/s00003.xhtml"))
            check(zip.getEntry("OEBPS/nav.xhtml") != null)
            check(zip.getEntry("META-INF/container.xml") != null)
        }
        // Regression: visible cover + embedded metadata + proper manifest
        // for both EPUB versions supported by the Windows/Folio readers.
        for (format in ReflowEpubFormat.entries) {
            val covered = File.createTempFile("nooir-covered-", ".epub")
            try {
                ReflowEpubWriter.write(covered, ReflowMetadata(
                    "The Example Novel", "Example Author", "A short description", "ms",
                    "https://example.org/story",
                ), format) { epub ->
                    val src = epub.addImage(ReflowEpubWriter.ImageAsset(
                        byteArrayOf(137.toByte(),80,78,71,13,10,26,10), "png", "image/png"
                    ), isCover = true)
                    epub.addSection("Cover",
                        "<figure><img src=\"$src\" alt=\"Book cover\"/></figure>")
                    epub.addSection("Chapter 1", "<h1>Chapter 1</h1><p>Actual reflowable text</p>")
                }
                ZipFile(covered).use { zip ->
                    val opf = zip.getInputStream(zip.getEntry("OEBPS/package.opf"))
                        .bufferedReader().readText()
                    check(opf.contains("<dc:language>ms</dc:language>"))
                    check(opf.contains("<dc:creator>Example Author</dc:creator>"))
                    check(opf.contains("A short description"))
                    check(opf.contains("images/img00001.png"))
                    check(zip.getEntry("OEBPS/images/img00001.png") != null)
                    val cover = zip.getInputStream(zip.getEntry("OEBPS/text/s00001.xhtml"))
                        .bufferedReader().readText()
                    check(cover.contains("Book cover"))
                    val chapter = zip.getInputStream(zip.getEntry("OEBPS/text/s00002.xhtml"))
                        .bufferedReader().readText()
                    check(chapter.contains("Actual reflowable text"))
                    if (format == ReflowEpubFormat.EPUB3) {
                        check(opf.contains("properties=\"cover-image\""))
                        val nav = zip.getInputStream(zip.getEntry("OEBPS/nav.xhtml"))
                            .bufferedReader().readText()
                        check(nav.indexOf(">Cover<") in 0 until nav.indexOf(">Chapter 1<"))
                    } else {
                        check(opf.contains("<meta name=\"cover\" content=\"img1\"/>"))
                        check(opf.contains("<reference type=\"cover\""))
                        check(zip.getEntry("OEBPS/toc.ncx") != null)
                    }
                }
            } finally { covered.delete() }
        }
        // An inaccessible chapter must never expose a truncated combined ebook.
        val aborted = File.createTempFile("nooir-failed-merge-", ".epub")
        try {
            val failure = runCatching {
                ReflowEpubWriter.write(aborted, ReflowMetadata("Aborted book")) { epub ->
                    epub.addSection("Public chapter 1", "<p>This chapter is readable</p>")
                    error("Chapter 2 is not publicly accessible")
                }
            }
            check(failure.isFailure)
            check(!aborted.exists()) { "Incomplete EPUB leaked after chapter failure" }
        } finally { aborted.delete() }
        println("Reflow EPUB: ordered chapters, abort cleanup, metadata, text and images passed")
    } finally { temp.delete() }
}
