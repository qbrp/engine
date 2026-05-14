package org.lain.engine.util

import org.lain.engine.util.file.ENGINE_PREFERENCES

private val ID_PREFERENCES = ENGINE_PREFERENCES.node("id")

fun nextId(): Long {
    val id = ID_PREFERENCES.getLong("lastId", 0L) + 1
    ID_PREFERENCES.putLong("lastId", id)
    return id
}

fun nextIdStr(): String {
    return nextId().toString()
}

private var lastId = 0L

fun nextIdFast(): Long {
    return ++lastId
}