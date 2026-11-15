package org.lain.engine.client.render.ui

interface Command {
    fun invoke()
    fun undo()
}

class CommandHistory(val stack: MutableList<Command>) {
    fun push(command: Command) {
        try {
            command.invoke()
            stack += command
        } catch (e: Exception) {
            command.undo()
            throw e
        }
    }

    fun undo() {
        stack.removeLastOrNull()?.undo()
    }
}