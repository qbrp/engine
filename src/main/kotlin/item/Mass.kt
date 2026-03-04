package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.transport.packet.ItemComponent

@Serializable
data class Mass(val mass: Float) : ItemComponent