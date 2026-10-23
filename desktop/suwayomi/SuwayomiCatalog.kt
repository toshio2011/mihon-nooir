package dev.nooir.desktop.suwayomi

/** Source and manga discovery for the Suwayomi v2.4.2366 GraphQL contract. */
data class SuwayomiSource(
    val id: Long,
    val name: String,
    val language: String,
    val supportsLatest: Boolean,
)

data class SuwayomiMangaCard(
    val id: Int,
    val title: String,
    val author: String,
    val sourceId: Long,
)

data class SuwayomiPage<T>(
    val items: List<T>,
    val hasNextPage: Boolean,
)

/**
 * Source discovery, per-source popular/latest/search, and local library.
 * Pagination is explicit: the caller decides whether to request another page.
 * No extension installation or remote search happens implicitly.
 */
class SuwayomiCatalog(private val client: SuwayomiClient) {
    fun sources(limit: Int = 100, offset: Int = 0): SuwayomiPage<SuwayomiSource> {
        require(limit in 1..100 && offset >= 0 && offset <= 100_000)
        val data = client.graphql("""
            query NooirSources {
              sources(first: $limit, offset: $offset) {
                nodes { id name lang supportsLatest }
                totalCount
              }
            }
        """.trimIndent())
        val connection = data.obj("sources")
        val items = connection.list("nodes").map { raw ->
            val node = raw.asObject()
            SuwayomiSource(
                id = node.long("id"),
                name = node.str("name"),
                language = node.str("lang"),
                supportsLatest = node["supportsLatest"] as? Boolean ?: error("Missing supportsLatest"),
            )
        }
        val total = connection.int("totalCount")
        return SuwayomiPage(items, offset.toLong() + items.size < total)
    }

    fun library(limit: Int = 100, offset: Int = 0): SuwayomiPage<SuwayomiMangaCard> {
        require(limit in 1..100 && offset >= 0 && offset <= 100_000)
        val data = client.graphql("""
            query NooirLibrary {
              mangas(condition: { inLibrary: true }, first: $limit, offset: $offset) {
                nodes { id title author sourceId }
                totalCount
              }
            }
        """.trimIndent())
        val connection = data.obj("mangas")
        val items = connection.list("nodes").map { it.asObject().toMangaCard() }
        val total = connection.int("totalCount")
        return SuwayomiPage(items, offset.toLong() + items.size < total)
    }

    enum class BrowseMode { POPULAR, LATEST, SEARCH }

    fun browse(sourceId: Long, mode: BrowseMode, page: Int = 1, search: String = ""): SuwayomiPage<SuwayomiMangaCard> {
        require(sourceId > 0) { "Select a valid source" }
        require(page in 1..1000) { "Page must be between 1 and 1000" }
        require(search.length <= 200) { "Search term is too long" }
        if (mode == BrowseMode.SEARCH) require(search.isNotBlank()) { "Enter a manga title to search" }
        val escaped = if (mode == BrowseMode.SEARCH) ", query: ${MiniJson.quote(search.trim())}" else ""
        val data = client.graphql("""
            mutation NooirBrowse {
              fetchSourceManga(input: { source: $sourceId, type: ${mode.name}, page: $page$escaped }) {
                mangas { id title author sourceId }
                hasNextPage
              }
            }
        """.trimIndent())
        val result = data.obj("fetchSourceManga")
        val items = result.list("mangas").map { it.asObject().toMangaCard() }
        require(items.size <= 500) { "Source returned too many manga entries" }
        return SuwayomiPage(items, result["hasNextPage"] as? Boolean ?: error("Missing pagination state"))
    }

    private fun Map<String, Any?>.toMangaCard(): SuwayomiMangaCard =
        SuwayomiMangaCard(int("id"), str("title"), this["author"] as? String ?: "", long("sourceId"))
}
