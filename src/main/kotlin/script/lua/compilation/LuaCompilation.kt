package org.lain.engine.script.lua.compilation

import org.lain.cyberia.ecs.ComponentType
import org.lain.engine.script.CallbackType
import org.lain.engine.script.Callbacks
import org.lain.engine.script.CompilationException
import org.lain.engine.script.CompilationResult
import org.lain.engine.script.CompiledNamespace
import org.lain.engine.script.ScriptCallback
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.ScriptId
import org.lain.engine.script.ScriptSystem
import org.lain.engine.script.ScriptSystemDefinition
import org.lain.engine.script.ScriptSystemId
import org.lain.engine.script.SystemPhase
import org.lain.engine.script.SystemSide
import org.lain.engine.script.lua.LuaScript
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.LuaScriptEngine.CompilationContext
import org.lain.engine.script.lua.library.asEngineScriptComponentType
import org.lain.engine.script.lua.library.componentType
import org.lain.engine.script.lua.library.lazyComponentType
import org.lain.engine.script.lua.nullable
import org.lain.engine.script.lua.toIntentInput
import org.lain.engine.script.lua.toList
import org.lain.engine.script.toScriptComponentId
import org.lain.engine.script.toScriptId
import org.lain.engine.script.yaml.namespacedId
import org.lain.engine.util.Intent
import org.lain.engine.util.IntentId
import org.lain.engine.util.NamespaceId
import org.lain.engine.util.Timestamp
import org.lain.engine.util.component.ComponentMeta
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import kotlin.collections.plus
import kotlin.collections.set

typealias CallbackRetrieveMap = MutableMap<CallbackType<*>, ScriptCallback<ScriptContext>>

context(ctx: LuaScriptEngine)
fun runCompilationFunctionsLua(
    functions: List<LuaFunction>,
    callbackTypes: List<CallbackType<*>>
): CompilationResult {
    val compilation = CompilationContext()
    val start = Timestamp()
    val callbacks: CallbackRetrieveMap = mutableMapOf()
    val phases = mutableListOf<SystemPhase>()
    functions.forEach { function ->
        val table = function.call().checktable()
        table.get("namespaces").nullable()?.checktable()
            ?.toList { it.checktable() }
            ?.forEach { namespace ->
                val namespaceId =
                    runCatching { NamespaceId(namespace.get("id").tojstring()) }.getOrNull() ?: return@forEach

                try {
                    val itemsArray = namespace.get("items").nullable()?.checktable()
                    val scriptsArray = namespace.get("scripts").nullable()?.checktable()
                    val componentsArray = namespace.get("components").nullable()?.checktable()
                    val intentsArray = namespace.get("intents").nullable()?.checktable()
                    val systemsArray = namespace.get("systems").nullable()?.checktable()

                    val items = compileItemsLua(namespaceId, itemsArray?.toList { it.checktable() } ?: emptyList())
                    val scripts = scriptsArray?.toList { it.checktable() }
                        ?.associate { script ->
                            val id = script.get("id").tojstring()
                            val function = script.get("fun").checkfunction()
                            namespacedId(namespaceId, id).toScriptId() to LuaScript<ScriptContext, Unit>(ctx, function)
                        } ?: emptyMap()

                    val components = componentsArray?.toList { it.checktable() }
                        ?.associate { componentType ->
                            val componentId = namespacedId(namespaceId, componentType.get("id").tojstring()).toScriptComponentId()
                            val isSavable = componentType.get("savable").nullable()?.toboolean() ?: false
                            val isNetworking = componentType.get("networking").nullable()?.toboolean() ?: false
                            componentId to ScriptComponentType(
                                ComponentType(componentId.id),
                                ComponentMeta(isSavable, null, isNetworking)
                            )
                        } ?: emptyMap()

                    val intents = intentsArray?.toList { it.checktable() }
                        ?.associate { intent ->
                            val id = IntentId(namespacedId(namespaceId, intent.get("id").tojstring()))
                            val script = ScriptId(intent.get("script").tojstring())
                            val name = intent.get("name")?.nullable()?.tojstring() ?: id.value
                            val inputs = intent.get("inputs")?.nullable()?.checktable()
                                ?.toList { it.checktable() }
                                ?.map { it.toIntentInput() } ?: emptyList()
                            val permission = if(intent.get("permission")?.nullable()?.toboolean() == true) {
                                "intent.${id.value.replace("/", ".")}"
                            } else {
                                null
                            }
                            id to Intent(id, name, script, inputs, permission = permission)
                        } ?: emptyMap()

                    val systems = systemsArray?.toList { it.checktable() }
                        ?.associate { systemL ->
                            val id = ScriptSystemId(systemL["id"].tojstring())
                            val script = LuaScript<ScriptContext.SystemEntityHandle, Unit>(
                                ctx,
                                systemL["update"].checkfunction()
                            )
                            val side = SystemSide.valueOf(systemL["side"].checkjstring().uppercase())
                            val query = systemL["query"].checktable().toList {
                                it.lazyComponentType.id
                            }
                             id to CompiledNamespace.ScriptSystem(query, side, script)
                        } ?: emptyMap()

                    compilation.namespaces[namespaceId] = CompiledNamespace(
                        items.associateBy { it.id },
                        mapOf(),
                        mapOf(),
                        scripts,
                        components,
                        intents,
                        systems
                    )
                } catch (e: Exception) {
                    compilation.errors += CompilationException(namespaceId, e)
                }
            }
        table.get("callbacks").nullable()?.checktable()?.retrieveCallbacks(callbackTypes, callbacks)
        table.get("phases").nullable()?.checktable()
            ?.toList { phaseL ->
                    SystemPhase(
                    phaseL.get("id").tojstring(),
                    phaseL.get("systems").checktable().toList { systemIdL ->
                        ScriptSystemId(systemIdL.tojstring())
                    }
                )
            }
            ?.let { phases.addAll(it) }
    }
    return CompilationResult(
        compilation.namespaces,
        compilation.errors,
        Callbacks(callbacks.toMap()),
        phases,
        start.timeElapsed()
    )
}

context(ctx: LuaScriptEngine)
fun LuaTable.retrieveCallbacks(
    callbackTypes: List<CallbackType<*>>,
    callbacksOutput: CallbackRetrieveMap,
) {
    callbackTypes.forEach { type ->
        val script = get(type.id).nullable()?.checkfunction()
            ?.let { function -> LuaScript<ScriptContext, Unit>(ctx, function) }
            ?: return@forEach
        val existing = callbacksOutput[type]
        callbacksOutput[type] = existing?.let { it.copy(scripts = it.scripts + script) }
            ?: ScriptCallback(listOf(script))
    }
}