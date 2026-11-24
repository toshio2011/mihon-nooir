package dev.nooir.mangabridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android/Jsoup DOM regression, no third-party network or copyrighted text. */
@RunWith(AndroidJUnit4::class)
class NooirWebTocHtmlInstrumentedTest {
    @Test fun wattpadPublicStoryDetailsIncludeCoverAndSynopsis() {
        val link = "https://www.wattpad.com/story/413612349?" +
            "utm_source=android&utm_medium=link&wp_uname=Toshio2011"
        assertEquals("413612349", NooirWattpadPublic.storyId(link))
        val details = NooirWattpadPublic.fromPublicStoryDetailsJson(
            "{\"id\":\"413612349\",\"title\":\"The Lantern Observatory\",\"description\":\"A small town kept its lights on.\\\\n\\\\nThe lighthouse keeper waited for dawn.\",\"cover\":\"https://img.wattpad.com/cover/413612349-256-k123.jpg\",\"user\":{\"name\":\"Example Novelist\",\"username\":\"exampleauthor\"},\"storyLanguage\":\"en\",\"parts\":[{\"id\":\"1590012345\",\"title\":\"Part One\",\"url\":\"https://www.wattpad.com/1590012345-part-one\",\"draft\":false},{\"id\":\"1590012346\",\"title\":\"Part Two\",\"url\":\"https://www.wattpad.com/1590012346-part-two\",\"draft\":false}]}"
        )
        assertEquals("The Lantern Observatory", details.title)
        assertEquals("Example Novelist", details.author)
        assertTrue(details.synopsis.contains("lighthouse keeper"))
        assertEquals("https://img.wattpad.com/cover/413612349-256-k123.jpg", details.coverUrl)
        assertEquals("en", details.language)
        assertEquals(2, details.chapters.size)
        val unsafe = NooirWattpadPublic.fromPublicStoryDetailsJson(
            "{\"id\":\"413612349\",\"title\":\"The Lantern Observatory\",\"description\":\"A small town kept its lights on.\\\\n\\\\nThe lighthouse keeper waited for dawn.\",\"cover\":\"https://img.wattpad.com/cover/413612349-256-k123.jpg\",\"user\":{\"name\":\"Example Novelist\",\"username\":\"exampleauthor\"},\"storyLanguage\":\"en\",\"parts\":[{\"id\":\"1590012345\",\"title\":\"Part One\",\"url\":\"https://www.wattpad.com/1590012345-part-one\",\"draft\":false},{\"id\":\"1590012346\",\"title\":\"Part Two\",\"url\":\"https://www.wattpad.com/1590012346-part-two\",\"draft\":false}]}".replace("https://img.wattpad.com/cover/",
                "http://127.0.0.1/cover/")
        )
        assertEquals("", unsafe.coverUrl)
    }

    @Test fun numberedTocControlsAreNotAccidentallyImportedAsChapters() {
        val page = Jsoup.parse(
            """
            <html><body><div class="chapter-list">
              <a href="/chapter/1">Chapter 1</a>
              <a href="/chapter/2">Chapter 2</a>
              <nav class="pagination" aria-label="Pagination">
                <a href="?page=2" rel="next">Next page</a>
                <a href="?page=3">Page 3</a>
                <a href="/chapter/3">Next chapter</a>
              </nav>
            </div></body></html>
            """.trimIndent(), "https://example.org/story/chapters"
        )
        val chapters = NooirWebNovel.discover(page)
        assertEquals(listOf("Chapter 1", "Chapter 2"), chapters.map { it.title })
        val candidates = NooirWebChapterDiscovery.tocLinks(page)
        assertTrue(candidates.any { it.inPager && it.href == "?page=2" })
        assertEquals("https://example.org/story/chapters?page=2",
            NooirWebTocPagination.next(page.location(), candidates))
    }

    @Test fun queryBasedChapterUrlsKeepTheirStoryPath() {
        val page = Jsoup.parse(
            """
            <div class="chapter-list">
              <a href="?chapter=1">Chapter One</a>
              <a href="?chapter=2">Chapter Two</a>
            </div>
            """.trimIndent(), "https://example.org/story/chapters"
        )
        assertEquals(listOf(
            "https://example.org/story/chapters?chapter=1",
            "https://example.org/story/chapters?chapter=2"
        ), NooirWebNovel.discover(page).map { it.url })
    }

    @Test fun encodedChapterPathsRemainExactAndQueryVariantsDeduplicate() {
        val page = Jsoup.parse(
            """
            <div class="chapter-list">
              <a href="/story/chapter/a%2Fb?lang=en&amp;chapter=2">Chapter Encoded</a>
              <a href="/story/chapter/a%2Fb?chapter=2&amp;lang=en&amp;utm_source=feed">
                Chapter Duplicate
              </a>
              <a href="/story/chapter/a/b?lang=en&amp;chapter=2">Chapter Distinct</a>
            </div>
            """.trimIndent(), "https://example.org/story/chapters",
        )
        val chapters = NooirWebNovel.discover(page)
        assertEquals(listOf("Chapter Encoded", "Chapter Distinct"),
            chapters.map { it.title })
        assertEquals("https://example.org/story/chapter/a%2Fb?lang=en&chapter=2",
            chapters[0].url)
        assertEquals("https://example.org/story/chapter/a/b?lang=en&chapter=2",
            chapters[1].url)
    }

    @Test fun plainNextChapterLinkCannotAdvanceTocPage() {
        val page = Jsoup.parse(
            """
            <div class="chapter-list">
              <a href="/chapter/1">Chapter 1</a>
              <a href="/chapter/2">Chapter 2</a>
            </div>
            <a href="/chapter/3" rel="next">Next chapter</a>
            <a href="/chapter/4" class="next-chapter">Next chapter</a>
            <a href="/chapter/0" rel="prev">Previous chapter</a>
            """.trimIndent(), "https://example.org/story/chapters"
        )
        assertEquals(2, NooirWebNovel.discover(page).size)
        assertNull(NooirWebTocPagination.next(
            page.location(), NooirWebChapterDiscovery.tocLinks(page)))
    }
    @Test fun consecutiveTocPagesMergeWithoutDuplicateChapters() {
        val root = "https://example.org/story/chapters"
        val first = Jsoup.parse(
            "<div class='chapter-list'>" +
                "<a href='/chapter/1'>Chapter One</a>" +
                "<a href='/chapter/2'>Chapter Two</a></div>" +
                "<nav class='pagination'><a href='?page=2'>Next page</a></nav>",
            root,
        )
        val second = Jsoup.parse(
            "<div class='chapter-list'>" +
                "<a href='/chapter/2?utm_source=mail'>Chapter Two</a>" +
                "<a href='/chapter/3'>Chapter Three</a></div>",
            root + "?page=2",
        )
        val requests = mutableListOf<String>()
        val chapters = NooirWebChapterDiscovery.chapters(root, first,
            fetchPage = { url ->
                requests += url
                assertEquals(root + "?page=2", url)
                second
            },
            pace = {},
        )
        assertEquals(listOf("Chapter One", "Chapter Two", "Chapter Three"),
            chapters.map { it.title })
        assertEquals(listOf(root + "?page=2"), requests)
    }

    @Test fun unfinishedTocAfterSafetyLimitFailsRatherThanTruncates() {
        val root = "https://example.org/story/chapters"
        fun page(n: Int): org.jsoup.nodes.Document {
            val firstId = n * 2 - 1
            val secondId = n * 2
            val markup = "<div class='chapter-list'>" +
                "<a href='/chapter/" + firstId + "'>Chapter " + firstId + "</a>" +
                "<a href='/chapter/" + secondId + "'>Chapter " + secondId + "</a></div>" +
                "<nav class='pagination'><a href='?page=" + (n + 1) +
                "'>Next page</a></nav>"
            return Jsoup.parse(markup, if (n == 1) root else root + "?page=" + n)
        }
        val failed = runCatching {
            NooirWebChapterDiscovery.chapters(root, page(1),
                fetchPage = { url ->
                    val pageNumber = java.net.URI(url).rawQuery.orEmpty()
                        .substringAfter("page=").toIntOrNull() ?: error("Missing page number")
                    page(pageNumber)
                },
                pace = {},
            )
        }
        assertTrue(failed.isFailure)
        assertTrue(failed.exceptionOrNull()?.message.orEmpty().contains("more than 12 pages"))
    }

}
