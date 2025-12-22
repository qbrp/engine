package org.lain.engine.util

import net.minecraft.util.Identifier
import org.lain.engine.CommonEngineServerMod
import java.util.prefs.Preferences

object IdPreferences {
    val prefs = Preferences.userNodeForPackage(IdPreferences::class.java)
}

private val prefs
    get() = IdPreferences.prefs

fun nextId(): Long {
    val id = prefs.getLong("lastId", 0L) + 1
    prefs.putLong("lastId", id)
    return id
}

fun nextIdStr(): String {
    return nextId().toString()
}

fun EngineId(path: String) = Identifier.of(CommonEngineServerMod.MOD_ID, path)