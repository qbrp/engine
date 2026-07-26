package org.lain.engine.client.script

import org.lain.engine.script.CallbackType
import org.lain.engine.script.ScriptContext

object ClientCallbacks {
    val WORKSPACE_OPEN = CallbackType<ScriptContext>("workspace_open")

    fun list() = listOf(
        WORKSPACE_OPEN,
    )
}