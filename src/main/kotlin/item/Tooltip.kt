package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.CallbackType
import org.lain.engine.script.ScriptContext
import org.lain.engine.world.World
import kotlin.collections.mutableListOf

@Serializable
data class ItemTooltip(val text: String) : Component