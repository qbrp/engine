package org.lain.engine.script.lua.compilation

import org.lain.engine.script.*
import org.lain.engine.script.compilation.*
import org.lain.engine.script.lua.*
import org.lain.engine.script.lua.library.resolveIdReference
import org.lain.engine.util.component.ComponentMeta
import org.lain.engine.util.toOperationId
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable

typealias CallbackRetrieveMap = Map<CallbackType<*, *>, Script<*, *>>

data class LuaCompilationContext(
    val callbackTypes: List<CallbackType<*, *>>,
    override val exceptions: CompilationReportBuilder = CompilationReportBuilder(),
) : CompilationContext

context(context: LuaCompilationContext)
private inline fun <T> compileEntry(
    namespaceId: NamespaceId,
    kind: SymbolKind,
    table: LuaTable,
    compile: () -> T
): T? {
    val idValue = table["id"]
    val localId = idValue.tojstring()

    return try {
        compile()
    } catch (e: LuaError) {
        context.exceptions.report(
            CompilationDiagnostic(
                severity = CompilationDiagnosticSeverity.ERROR,
                message = e.message ?: "Ошибка компиляции $kind",
                phase = CompilationPhase.COMPILATION,
                namespace = namespaceId,
                target = CompilationDiagnosticTarget(kind, localId),
                cause = e.cause
            )
        )
        null
    }
}

context(
    context: LuaCompilationContext,
    luaScriptEngine: LuaScriptEngine
)
private fun compiledNamespacesList(
    namespace: LuaTable
): Pair<NamespaceId, NamespaceDraft>? {
    val namespaceId = try {
        NamespaceId(
            namespace.get("id").nullable()?.tojstring()
                ?: error("Значение id не указано или равно nil")
        )
    } catch (e: RuntimeException) {
        context.exceptions.report(
            CompilationDiagnostic(
                CompilationDiagnosticSeverity.ERROR,
                "Не удалось получить идентификатор таблицы пространства имён: ${e.message}",
                CompilationPhase.COMPILATION
            )
        )
        return null
    }

    return try {
        val itemsArray = namespace.get("items").nullable()?.checktable()
        val scriptsArray = namespace.get("scripts").nullable()?.checktable()
        val componentsArray = namespace.get("components").nullable()?.checktable()
        val operationsArray = namespace.get("operations").nullable()?.checktable()
        val systemsArray = namespace.get("systems").nullable()?.checktable()

        val items =
            compileItemPrefabsLua(
                namespaceId,
                itemsArray?.toList { it.checktable() } ?: emptyList())
        val scripts = scriptsArray?.toList { it.checktable() }
            ?.mapNotNull { script ->
                compileEntry(
                    namespaceId,
                    SymbolKind.SCRIPT,
                    script
                ) {
                    val id = script.get("id")
                    val function = script.get("execute").checkfunction()
                    id.resolveIdReference().toScriptId() to LuaScript<ScriptContext, ScriptValue>(
                        luaScriptEngine,
                        function
                    )
                }
            }
            ?.toMap()
            ?: emptyMap()

        val components = componentsArray?.toList { it.checktable() }
            ?.mapNotNull { componentType ->
                compileEntry(
                    namespaceId,
                    SymbolKind.COMPONENT,
                    componentType
                ) {
                    val componentId =
                        componentType.get("id").resolveIdReference().toScriptComponentId()
                    val isSavable = componentType.get("savable").nullable()?.toboolean() ?: false
                    val isNetworking =
                        componentType.get("networking").nullable()?.toboolean() ?: false
                    componentId to ScriptComponentType(
                        ComponentType(componentId.id),
                        ComponentMeta(isSavable, null, isNetworking)
                    )
                }
            }
            ?.toMap()
            ?: emptyMap()

        val operations = operationsArray
            ?.toList { it.checktable() }
            ?.mapNotNull { operation ->
                compileEntry(
                    namespaceId,
                    SymbolKind.OPERATION,
                    operation
                ) {
                    val id = operation.get("id").resolveIdReference().toOperationId()
                    val script = operation.get("script").resolveIdReference().toScriptId()
                    val name = operation.get("name")?.nullable()?.tojstring() ?: id.toString()
                    val inputs = operation.get("inputs")?.nullable()?.checktable()
                        ?.toList { it.checktable() }
                        ?.map { it.toOperationInput() } ?: emptyList()
                    val permission =
                        if (operation.get("permission")?.nullable()?.toboolean() == true) {
                            "operation.${id.toString().replace("/", ".")}"
                        } else {
                            null
                        }
                    id to NamespaceDraft.Operation(name, script, inputs, permission = permission)
                }
            }
            ?.toMap()
            ?: emptyMap()

        val systems = systemsArray?.toList { it.checktable() }
            ?.mapNotNull { systemL ->
                compileEntry(
                    namespaceId,
                    SymbolKind.SYSTEM,
                    systemL
                ) {
                    val id = systemL["id"].resolveIdReference().toScriptSystemId()
                    val script = LuaScript<ScriptContext.SystemEntityHandle, ScriptValue>(
                        luaScriptEngine,
                        systemL["update"].checkfunction()
                    )
                    val side = SystemSide.valueOf(systemL["side"].checkjstring().uppercase())
                    val query = systemL["query"].checktable()
                        .toList { it.resolveIdReference().toScriptComponentId() }
                    id to NamespaceDraft.ScriptSystem(query, side, script)
                }
            }
            ?.toMap()
            ?: emptyMap()

        namespaceId to NamespaceDraft(
            items = items.associateBy { it.id },
            sounds = emptyMap(),
            progressionAnimations = emptyMap(),
            scripts = scripts,
            components = components,
            operations = operations,
            systems = systems
        )
    } catch (e: DiagnosticException) {
        context.exceptions.report(
            e.toDiagnostic(
                DiagnosticContext(CompilationPhase.COMPILATION, namespaceId)
            )
        )
        null
    } catch (e: Exception) {
        context.exceptions.report(
            CompilationDiagnostic(
                severity = CompilationDiagnosticSeverity.ERROR,
                message = e.message
                    ?: "Не удалось скомпилировать пространство имён $namespaceId",
                phase = CompilationPhase.COMPILATION,
                namespace = namespaceId,
                cause = e
            )
        )
        null
    }
}

context(lua: LuaScriptEngine)
fun LuaCompilationContext.compiledBuildDraft(table: LuaTable): BuildDraft {
    val namespaces = table.get("namespaces").nullable()?.checktable()
        ?.toList { it.checktable() }
        ?.mapNotNull { namespace ->
            compiledNamespacesList(namespace)
        }
        ?.toMap()
        ?: emptyMap()
    val callbacks = table.get("listeners").nullable()?.checktable()
        ?.retrieveCallbacks(callbackTypes)
        ?: emptyMap()

    fun LuaTable.toPhaseDraft(): SystemPhaseDraft {
        return SystemPhaseDraft(
            get("name").tojstring(),
            get("systems").checktable().toList { systemIdL ->
                systemIdL.resolveIdReference().toScriptSystemId()
            },
            get("phases").checktable().toList { innerPhaseL ->
                innerPhaseL.checktable().toPhaseDraft()
            }
        )
    }

    val phases = table.get("phases").nullable()?.checktable()
        ?.toList { phaseL -> phaseL.checktable().toPhaseDraft() }
        ?: emptyList()

    return BuildDraft(
        phases = phases,
        callbacks = callbacks,
        namespaces = namespaces,
    )
}

context(ctx: LuaScriptEngine)
fun LuaTable.retrieveCallbacks(
    callbackTypes: List<CallbackType<*, *>>,
): CallbackRetrieveMap {
    val table = this
    return callbackTypes.mapNotNull { type ->
        val script = table.get(type.id).nullable()?.checkfunction()
            ?.let { function -> LuaScript<ScriptContext, ScriptValue>(ctx, function) }
            ?: return@mapNotNull null
        type to script
    }
        .toMap()
}
