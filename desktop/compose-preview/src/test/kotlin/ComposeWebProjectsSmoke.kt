package dev.nooir.desktop.compose

import dev.nooir.desktop.ui.DesktopWebProjects
import dev.nooir.mangabridge.*
import java.nio.file.Files
import org.json.JSONArray

fun main(){
    val root=Files.createTempDirectory("nooir-web-projects-compose")
    try{
        val store=DesktopWebProjects(root)
        val a=NooirWebChapterPlan.Entry("https://example.org/a","A")
        val b=NooirWebChapterPlan.Entry("https://example.org/b","B",false)
        val c=NooirWebChapterPlan.Entry("https://example.org/c","C")
        val draft=DesktopWebProjects.Project(name="QA novel",source="https://example.org/story",known=listOf(a.url,b.url),
            chapters=listOf(b,a),title="Novel",author="Author",description="Synopsis",language="en",
            coverUrl="",localCover="",collage=true,epub2=false,style="STANDARD",imageProfile="ORIGINAL",
            includeImages=true,css="",selector="",remove="",collageUrls=listOf("https://example.org/1.png","https://example.org/2.png"))
        val saved=store.save(draft)
        check(store.list().single()==saved)
        check(NooirWebChapterUpdates.findNew(saved.known,listOf(a,b,c))==listOf(c))
        check(runCatching{store.save(saved.copy(collageUrls=listOf("https://example.org/1.png")))}.isFailure)
        check(store.list().single()==saved)
        val file=root.resolve("web-projects-v1.json")
        val legacy=JSONArray(Files.readString(file))
        legacy.getJSONObject(0).remove("collageUrls")
        Files.writeString(file,legacy.toString())
        check(store.list().single().collageUrls.isEmpty())
        store.delete(saved.id)
        check(store.list().isEmpty())
        println("PASS: web project persistence, legacy compatibility, manual cover list validation and chapter inventory")
    }finally{root.toFile().deleteRecursively()}
}
