package dev.nooir.mangabridge

import android.content.Context
import dev.nooir.bridge.ReflowEpubStyle
import dev.nooir.bridge.NooirEpubCssRules
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * User-managed, app-private Web → EPUB projects. No credentials, browser
 * sessions, downloaded chapter content or remote sync are stored here.
 * Saving a project does not modify or merge an existing exported EPUB.
 */
internal class NooirWebProjectStore(context: Context) {
    internal data class Project(
        val id: String,
        val name: String,
        val sourceUrl: String,
        val knownChapterUrls: String,
        val chapterUrls: String,
        val chapterTitles: String,
        val bookTitle: String,
        val author: String,
        val description: String,
        val language: String,
        val coverUrl: String,
        val collageUrls: String,
        val collage: Boolean,
        val filename: String,
        val contentSelector: String,
        val removeSelector: String,
        val epub2: Boolean,
        val includeImages: Boolean,
        val imageProfile: String,
        val readingStyle: String,
        val customCss: String,
    )

    private val file = File(context.filesDir, "nooir-web-novel-projects-v1.json")

    fun list(): List<Project> {
        if (!file.exists()) return emptyList()
        require(file.length() in 2..1_000_000) { "Saved web-novel library is too large or invalid" }
        val array = JSONArray(file.readText(Charsets.UTF_8))
        require(array.length() <= 24) { "Saved web-novel library exceeds 24 projects" }
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Project(
                id = item.getString("id"),
                name = item.getString("name"),
                sourceUrl = item.optString("sourceUrl"),
                knownChapterUrls = item.optString("knownChapterUrls"),
                chapterUrls = item.getString("chapterUrls"),
                chapterTitles = item.optString("chapterTitles"),
                bookTitle = item.optString("bookTitle"),
                author = item.optString("author"),
                description = item.optString("description"),
                language = item.optString("language"),
                coverUrl = item.optString("coverUrl"),
                collageUrls = item.optString("collageUrls"),
                collage = item.optBoolean("collage"),
                filename = item.optString("filename"),
                contentSelector = item.optString("contentSelector"),
                removeSelector = item.optString("removeSelector"),
                epub2 = item.optBoolean("epub2"),
                includeImages = item.optBoolean("includeImages", true),
                imageProfile = item.optString("imageProfile", "ORIGINAL"),
                readingStyle = item.optString("readingStyle", "STANDARD"),
                customCss = item.optString("customCss"),
            ).also(::validate)
        }
    }

    fun save(project: Project): List<Project> {
        validate(project)
        val previous = list()
        val id = project.id.ifBlank { UUID.randomUUID().toString() }
        val saved = project.copy(id = id)
        val updated = listOf(saved) + previous.filterNot { it.id == id }
        require(updated.size <= 24) { "Local library is full (24 projects). Remove one first." }
        write(updated)
        return updated
    }

    fun delete(id: String): List<Project> {
        val previous = list()
        val updated = previous.filterNot { it.id == id }
        if (updated.size != previous.size) write(updated)
        return updated
    }

    private fun validate(p: Project) {
        require(p.id.isBlank() || Regex("^[a-f0-9-]{36}$").matches(p.id)) {
            "Invalid saved-project identity"
        }
        require(p.name.isNotBlank() && p.name.length <= 100) { "Give this project a short name" }
        require(p.chapterUrls.isNotBlank() &&
            p.chapterUrls.toByteArray(Charsets.UTF_8).size <= 120_000) {
            "Saved chapter link list is too long"
        }
        require(p.chapterUrls.lines().map(String::trim).filter(String::isNotBlank)
            .all { it.length <= 600 && it.startsWith("https://", true) }) {
            "Saved project contains a non-HTTPS chapter link"
        }
        require(p.sourceUrl.isBlank() ||
            (p.sourceUrl.length <= 600 && p.sourceUrl.startsWith("https://", true))) {
            "Saved story URL must be public HTTPS"
        }
        require(p.knownChapterUrls.length <= 120_000) {
            "Saved public chapter inventory is too long"
        }
        require(p.knownChapterUrls.lines().filter(String::isNotBlank)
            .all { it.length <= 600 && it.startsWith("https://", true) }) {
            "Saved chapter inventory contains a non-HTTPS URL"
        }
        require(p.chapterTitles.length <= 120_000 && p.bookTitle.length <= 240 &&
            p.author.length <= 120 && p.description.length <= 1200 &&
            p.coverUrl.length <= 600 && p.collageUrls.length <= 2500 &&
            p.filename.length <= 70 && p.language.length <= 32) {
            "Saved project metadata is too large"
        }
        NooirWebExtractRules.validate(p.contentSelector, p.removeSelector)
        NooirWebImageProfile.parse(p.imageProfile)
        ReflowEpubStyle.parse(p.readingStyle)
        NooirEpubCssRules.canonicalize(p.customCss)
    }

    private fun write(items: List<Project>) {
        val array = JSONArray()
        items.forEach { p ->
            array.put(JSONObject().apply {
                put("id", p.id); put("name", p.name); put("sourceUrl", p.sourceUrl)
                put("knownChapterUrls", p.knownChapterUrls)
                put("chapterUrls", p.chapterUrls); put("chapterTitles", p.chapterTitles)
                put("bookTitle", p.bookTitle); put("author", p.author)
                put("description", p.description); put("language", p.language)
                put("coverUrl", p.coverUrl); put("collageUrls", p.collageUrls)
                put("collage", p.collage); put("filename", p.filename)
                put("contentSelector", p.contentSelector); put("removeSelector", p.removeSelector)
                put("epub2", p.epub2); put("includeImages", p.includeImages)
                put("imageProfile", p.imageProfile); put("readingStyle", p.readingStyle)
                put("customCss", p.customCss)
            })
        }
        val bytes = array.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 1_000_000) { "Saved library exceeds 1 MB. Reduce chapter lists." }
        val temporary = File(file.parentFile, file.name + ".tmp")
        try {
            temporary.outputStream().use { stream ->
                stream.write(bytes)
                stream.flush()
                (stream as java.io.FileOutputStream).fd.sync()
            }
            require(temporary.renameTo(file)) { "Could not replace saved project library" }
        } finally { temporary.delete() }
    }
}
