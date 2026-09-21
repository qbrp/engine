package org.lain.engine.data

import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.lain.engine.item.ItemId
import org.lain.engine.util.file.FileSystem
import java.text.SimpleDateFormat
import java.util.*

fun backupBookContent(writer: String, item: ItemId, pages: List<String>) = StorageCoroutineScope.launch {
    val date = SimpleDateFormat("dd-MM-yyyy-HH-mm").format(Date())
    val name = "$date $item $writer"
        .replace("/", "")
        .replace("\\", "")
        .replace(":", "")
    val pagesJson = Json.encodeToString(pages)
    val file = FileSystem.bookBackups.resolve(name)
    file.createNewFile()
    file.writeText(pagesJson)
}
