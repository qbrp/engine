package org.lain.engine.item

import org.lain.engine.util.Component
import org.lain.engine.util.get

data class Count(var value: Int) : Component

val EngineItem.count
    get() = this.get<Count>()?.value ?: 1