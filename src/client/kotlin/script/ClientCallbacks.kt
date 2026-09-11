package org.lain.engine.client.script

import org.lain.engine.script.CallbackType
import org.lain.engine.script.CallbackType.Companion.type
import org.lain.engine.script.SList
import org.lain.engine.script.SNil
import org.lain.engine.script.ScriptContext

object ClientCallbacks {
    val WORKSPACE_OPEN = CallbackType<ClientScriptContext.WorkspaceOpen, SNil>("workspace_open")
    val SHOWED_ITEM_TOOLTIP = CallbackType<ClientScriptContext.ItemTooltip, SList>("show_item_tooltip")

    fun list() = listOf(WORKSPACE_OPEN, SHOWED_ITEM_TOOLTIP)
}