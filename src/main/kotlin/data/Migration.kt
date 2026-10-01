package org.lain.engine.data

import org.lain.engine.script.ExecutionResult
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.ScriptValue

data class PlannedMigration(val component: ScriptComponent, val baseVersion: Int, val targetVersion: Int)

fun ScriptComponent.planMigration(baseVersion: Int): PlannedMigration? {
    val targetVersion = type.version
    return if (baseVersion != targetVersion) {
        PlannedMigration(this, baseVersion, targetVersion)
    } else {
        null
    }
}

class MigrationException(val version: Int, val targetVersion: Int, cause: Throwable? = null) :
    RuntimeException("Не удалось выполнить миграцию с версии $version до $targetVersion", cause)

fun PlannedMigration.migrate() {
    var value: ScriptValue = component.value
    var version = baseVersion
    while (version < targetVersion) {
        val result = component.type.migrations[version].execute(
            ScriptContext.ComponentMigration(value)
        )
        value = when (result) {
            is ExecutionResult.Failure<ScriptValue> -> throw MigrationException(version, targetVersion, result.error)
            is ExecutionResult.Success<ScriptValue> -> result.value
        }
        version++
    }
}