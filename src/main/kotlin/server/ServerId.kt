package org.lain.engine.server

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ServerId(val value: String) {
    init {
        check(value.isNotEmpty() && value.all { it in 'a'..'z' || it in '1'..'9' || it in "-_" }) {
            "Server id ($value) contains characters outside [a-z0-9_-]"
        }
    }

    override fun toString(): String = value
}