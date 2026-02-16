package org.lain.engine.storage

import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.lain.engine.item.ItemId
import java.text.SimpleDateFormat
import java.util.*

private val BOOK_BACKUPS_DIR = STORAGE_DIR.resolve("books")
    .also { it.mkdirs() }

fun backupBookContent(writer: String, item: ItemId, pages: List<String>) = ItemIoCoroutineScope.launch {
    val date = SimpleDateFormat("dd-MM-yyyy-HH-mm").format(Date())
    val name = "$date $item $writer"
        .replace("/", "")
        .replace("\\", "")
        .replace(":", "")
    val pagesJson = Json.encodeToString(pages)
    val file = BOOK_BACKUPS_DIR.resolve(name)
    file.createNewFile()
    file.writeText(pagesJson)
}