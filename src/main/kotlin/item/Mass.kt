package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.util.Component

@Serializable
data class Mass(val mass: Float) : Component