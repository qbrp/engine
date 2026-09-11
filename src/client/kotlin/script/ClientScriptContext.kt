package org.lain.engine.client.script

import org.lain.engine.client.render.ui.Workspace
import org.lain.engine.item.EngineItem
import org.lain.engine.script.ScriptContext
import org.lain.engine.world.World

object ClientScriptContext {
    data class WorkspaceOpen(val screen: Workspace) : ScriptContext
    data class ItemTooltip(val world: World, val item: EngineItem) : ScriptContext
}