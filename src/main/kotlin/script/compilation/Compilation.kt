package org.lain.engine.script.compilation

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.engine.script.*
import org.lain.engine.server.EngineServer
import org.slf4j.event.Level

interface CompilationContext {
    val exceptions: CompilationReportBuilder
}

class CompilationFailedException(val report: CompilationReport) : Exception(
    "При компиляции ресурсов возникло ${report.errors.size} ошибок"
) {
    fun log() {
        val level = when(report.hasErrors) {
            true -> Level.ERROR
            false -> Level.WARN
        }
        ScriptEngine.LOGGER.atLevel(level).log(
            "Во время компиляции возникло {} ошибок и {} предупреждений",
            report.errors.size,
            report.warnings.size
        )
        report.logTo(ScriptEngine.LOGGER)
    }
}

fun <T : Component> ComponentType(engineId: EngineId): ComponentType<T> =
    ComponentType(engineId.full)

fun NamespacedStorageAccess.loadResult(result: Build) {
    val namespaces = result.namespaces + BuiltinNamespaces.all
    update(
        NamespacedStorage(
            namespaces,
            namespaces.collect { it.sounds },
            namespaces.collect { it.items },
            namespaces.collect { it.progressionAnimations },
            namespaces.collect { it.scripts },
            namespaces.collect { it.components },
            namespaces.collect { it.operations },
            namespaces.collect { it.systems }
        )
    )
}

fun EngineServer.loadBuild(result: Build) {
    simulation.applyCompilationResult(result)
    handler.onScriptsCompiled()
    platform.onCompiled(namespacedStorage.get())
    result.log()
}

//class EntrypointRunException(
//    source: ScriptSource,
//    error: Exception
//) : RuntimeException(
//    "Не удалось загрузить входной скрипт $source: $error",
//    error
//)
