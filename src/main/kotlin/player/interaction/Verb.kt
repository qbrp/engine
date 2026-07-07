package org.lain.engine.player.interaction

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class VerbId(val value: String)

@Serializable
data class VerbType(
    val id: VerbId,
    val name: String,
    val priority: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        return other is VerbType && other.id == id
    }

    override fun hashCode(): Int {
        var result = priority
        result = 31 * result + id.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}

fun VerbType(id: String, name: String, priority: Int = 0) = VerbType(VerbId(id), name, priority)