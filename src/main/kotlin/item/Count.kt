package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.util.Component
import org.lain.engine.util.get

@Serializable
data class Count(var value: Int) : Component

val EngineItem.count
    get() = this.get<Count>()?.value ?: 1