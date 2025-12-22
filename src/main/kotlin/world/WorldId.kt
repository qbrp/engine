package org.lain.engine.world

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class WorldId(val value: String) {
    override fun toString(): String {
        return value
    }
}