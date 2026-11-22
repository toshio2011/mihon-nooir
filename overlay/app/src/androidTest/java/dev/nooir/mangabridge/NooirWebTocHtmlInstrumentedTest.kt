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
