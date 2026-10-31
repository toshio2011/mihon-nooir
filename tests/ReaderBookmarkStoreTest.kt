import dev.nooir.desktop.ui.ReaderBookmarkStore
import java.nio.file.Files

fun main() {
    val root = Files.createTempDirectory("mihonnooir-bookmarks-")
    try {
        val first = root.resolve("Book One.cbz").toFile()
        val second = root.resolve("Book Two.cbz").toFile()
        val directory = root.resolve("bookmarks")
        val store = ReaderBookmarkStore(directory)
        check(store.list(first).isEmpty())
        store.add(first, 2, 1234L)
        store.add(first, 0, 1200L)
        store.add(first, 2, 9999L) // idempotent, retains original timestamp
        store.add(second, 2, 4321L)
        check(store.list(first).map { it.pageIndex } == listOf(0, 2))
        check(store.list(first).last().createdAt == 1234L)
        check(store.list(second).map { it.pageIndex } == listOf(2))
        val persisted = ReaderBookmarkStore(directory)
        check(persisted.list(first).size == 2) { "Bookmarks must survive restart" }
        persisted.remove(first, 2)
        check(store.list(first).map { it.pageIndex } == listOf(0))
        check(store.list(second).size == 1) { "Bookmarks are independent per comic" }
        check(runCatching { store.add(first, 5000) }.isFailure)
        check(runCatching { store.add(first, -1) }.isFailure)
        for (page in 1..99) store.add(first, page)
        check(store.list(first).size == ReaderBookmarkStore.MAX_BOOKMARKS_PER_BOOK)
        check(runCatching { store.add(first, 100) }.isFailure)
        val index = directory.resolve("reader-bookmarks-v1.tsv")
        val before = Files.readString(index)
        check(!before.contains("Book One.cbz") && !before.contains(root.toString())) {
            "Index must not leak local book paths"
        }
        Files.writeString(index, "BROKEN INDEX\n")
        check(runCatching { store.list(first) }.isFailure)
        check(runCatching { store.add(first, 1) }.isFailure)
        check(Files.readString(index) == "BROKEN INDEX\n") {
            "Corrupted bookmark index must not be silently overwritten"
        }
        println("Windows reader bookmarks passed: multi-page, idempotence, restart, isolation, bound, privacy, corruption")
    } finally {
        root.toFile().deleteRecursively()
    }
}
