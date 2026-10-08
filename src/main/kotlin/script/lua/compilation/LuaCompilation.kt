package org.lain.engine.script.lua.compilation

import org.lain.engine.item.toItemPrefabId
import org.lain.engine.player.EquipmentSlot
import org.lain.engine.player.EquipmentSlotId
import org.lain.engine.player.PlayerPart
import org.lain.engine.player.interaction.ProgressionAnimation
import org.lain.engine.player.interaction.ProgressionAnimationId
import org.lain.engine.script.*
import org.lain.engine.script.compilation.*
import org.lain.engine.script.lua.*
import org.lain.engine.script.lua.library.resolveIdReference
import org.lain.engine.script.Operation
import org.lain.engine.util.ecs.ComponentMeta
import org.lain.engine.script.toOperationId
import org.lain.engine.world.ESoundSource
import org.lain.engine.world.SoundEvent
import org.lain.engine.world.toSoundEventId
import org.lain.engine.world.toSoundId
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
        val soundEventsArray = namespace.get("sound_events").nullable()?.checktable()
        val progressionAnimationsArray = namespace.get("progression_animations").nullable()?.checktable()
        val equipmentSlotsArray = namespace.get("equipment_slots").nullable()?.checktable()

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
                    val isSavable = componentType.get("persistent").nullable()?.toboolean() ?: false
                    val isNetworking =
                        componentType.get("replicating").nullable()?.toboolean() ?: false
                    val version = componentType["version"].nullable()?.toint() ?: 0
                    val migrations = componentType["migrations"].nullable()?.checktable()
                        ?.toList { it.checkfunction() }
                        ?.map { LuaScript<ScriptContext.ComponentMigration, ScriptValue>(luaScriptEngine, it) }
                        ?: emptyList()
                    componentId to ScriptComponentType(
                        componentId,
                        ComponentType(componentId.id),
                        ComponentMeta(isSavable, isNetworking),
                        version,
                        migrations
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
                    val script = LuaScript<ScriptContext.OperationExecution, SNil>(luaScriptEngine, operation.get("execute").checkfunction())
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
                    id to Operation(id, name, script, inputs, permission = permission)
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
                        systemL["tick"].checkfunction()
                    )
                    val side = SystemSide.valueOf(systemL["side"].checkjstring().uppercase())
                    val query = systemL["query"].checktable()
                        .toList { it.resolveIdReference().toScriptComponentId() }
                    id to NamespaceDraft.ScriptSystem(query, side, script)
                }
            }
            ?.toMap()
            ?: emptyMap()

        val soundEvents = soundEventsArray?.toList { it.checktable() }
            ?.mapNotNull { soundEventL ->
                compileEntry(
                    namespaceId,
                    SymbolKind.SOUND,
                    soundEventL
                ) {
                    val id = soundEventL.get("id").resolveIdReference().toSoundEventId()
                    val sources = soundEventL.get("sources").checktable().toList {
                        val table = it.checktable()
                        ESoundSource(
                            table["id"].tojstring().toSoundId(),
                            table["volume"].nullable()?.tofloat() ?: 1f,
                            table["pitch"].nullable()?.tofloat() ?: 1f,
                            table["weight"].nullable()?.toint() ?: 1,
                            table["distance"].nullable()?.toint() ?: 16,
                            table["pitch_random"].nullable()?.tofloat() ?: 0f
                        )
                    }
                    id to SoundEvent(id, sources)
                }
            }
            ?.toMap()
            ?: emptyMap()

        val progressionAnimations = progressionAnimationsArray?.toList { it.checktable() }
            ?.mapNotNull { animation ->
                compileEntry(
                    namespaceId,
                    SymbolKind.OTHER,
                    animation
                ) {
                    val id = ProgressionAnimationId(animation["id"].resolveIdReference())
                    val framesTable = animation["frames"].checktable()
                    val frameName = framesTable["name"].nullable()?.tojstring()
                    val frames = if (frameName == null) {
                        framesTable.toList { it.checkjstring() }
                    } else {
                        val count = framesTable["count"].checkint()
                        List(count) { index -> "$frameName${index + 1}" }
                    }
                    val text = animation["text"].checkjstring()
                    val success = animation["success"].nullable()?.tojstring() ?: text
                    id to ProgressionAnimation(frames, text, success)
                }
            }
            ?.toMap()
            ?: emptyMap()

        val equipmentSlots = equipmentSlotsArray?.toList { it.checktable() }
            ?.mapNotNull { equipmentSlot ->
                compileEntry(
                    namespaceId,
                    SymbolKind.EQUIPMENT_SLOT,
                    equipmentSlot
                ) {
                    val id = EquipmentSlotId(equipmentSlot["id"].resolveIdReference())
                    val name = equipmentSlot["name"].tojstring()
                    val part = PlayerPart.valueOf(equipmentSlot["part"].tojstring().lowercase())
                    id to EquipmentSlot(name, false, part)
                }
            }
            ?.toMap()
            ?: emptyMap()

        namespaceId to NamespaceDraft(
            items = items.associateBy { it.id },
            sounds = soundEvents,
            progressionAnimations = progressionAnimations,
            scripts = scripts,
            components = components,
            operations = operations,
            systems = systems,
            equipmentSlots = equipmentSlots
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
fun LuaCompilationContext.compileBuildDraft(buildL: LuaTable): BuildDraft {
    val namespaces = buildL.get("namespaces").nullable()?.checktable()
        ?.toList { it.checktable() }
        ?.mapNotNull { namespace ->
            compiledNamespacesList(namespace)
        }
        ?.toMap()
        ?: emptyMap()
    val callbacks = buildL.get("listeners").nullable()?.checktable()
        ?.retrieveCallbacks(callbackTypes)
        ?: emptyMap()

    val phases = compileTickPhasesDraft(buildL)

    val inventoryTabEntries = buildL.get("inventory_tab").nullable()?.checktable()
        ?.let {
            it["entries"].checktable().toList {
                InventoryTab.Entry(
                    it["prefab_id"].resolveIdReference().toItemPrefabId(),
                    it["tooltip"]?.nullable()?.checktable()?.toList { it.tojstring() } ?: emptyList()
                )
            }
        }
        ?: emptyList()

    return BuildDraft(
        callbacks = callbacks,
        namespaces = namespaces,
        phases = phases,
        inventoryTab = InventoryTab(inventoryTabEntries)
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
