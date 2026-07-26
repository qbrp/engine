package org.lain.engine.client.script

import org.lain.engine.client.render.ui.Workspace
import org.lain.engine.script.ScriptContext

object ClientScriptContext {
    data class WorkspaceOpen(val screen: Workspace) : ScriptContext()
}