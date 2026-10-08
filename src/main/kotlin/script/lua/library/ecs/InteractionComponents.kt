package org.lain.engine.script.lua.library.ecs

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.engine.player.interaction.Hand
import org.lain.engine.player.interaction.HandType
import org.lain.engine.player.interaction.InputAction
import org.lain.engine.player.interaction.InteractionInterrupt
import org.lain.engine.player.interaction.Interactor
import org.lain.engine.player.interaction.UsingItem
import org.lain.engine.player.interaction.Verb
import org.lain.engine.player.interaction.VerbId
import org.lain.engine.player.interaction.VerbLookup
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.lua.LuaScriptComponent
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.LuaUserdataType
import org.lain.engine.script.lua.NIL
import org.lain.engine.script.lua.asUserdataOrThrow
import org.lain.engine.script.lua.castedLuaValue
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.nullable
import org.lain.engine.script.lua.setLuaScriptComponent
import org.lain.engine.script.lua.toList
import org.lain.engine.script.lua.toLuaList
import org.lain.engine.script.lua.toLuaTable
import org.lain.engine.script.lua.library.checkLuaEntity
import org.lain.engine.script.lua.library.fetchComponentTypeFromHolder
import org.lain.engine.script.lua.library.luaEntity
import org.lain.engine.world.World
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.ZeroArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua

private fun LuaValue.toInputAction(): InputAction = when (checkjstring()) {
    "attack" -> InputAction.Attack
    "base" -> InputAction.Base
    "take_off" -> InputAction.TakeOff
    else -> error("Unknown input action: ${tojstring()}")
}

private fun InputAction.id(): String = when (this) {
    InputAction.Attack -> "attack"
    InputAction.Base -> "base"
    InputAction.TakeOff -> "take_off"
}

private fun Verb.toLuaVerb(): LuaTable = luaTable {
    "id"(id.value)
    "name"(name)
    "priority"(priority)
    "input_action"(holdsInput?.id())
    "create_command"(object : ZeroArgFunction() {
        override fun call(): LuaValue = CoerceJavaToLua.coerce(createCommand())
    })
}

context(lua: LuaScriptEngine)
private fun LuaTable.toVerb(): Verb {
    val commandType = get("command_type").nullable()?.fetchComponentTypeFromHolder()
    val createCommand = get("create_command").checkfunction()
    return Verb(
        id = VerbId(get("id").checkjstring()),
        name = get("name").checkjstring(),
        priority = get("priority").nullable()?.checkint() ?: 10,
        holdsInput = get("input_action").nullable()?.toInputAction(),
    ) {
        val command = createCommand.call()
        if (commandType != null) {
            LuaScriptComponent(command, commandType, lua)
        } else {
            command.checkuserdata(Component::class.java) as Component
        }
    }
}

private fun VerbLookup.toLuaComponent(): LuaTable = luaTable {
    "input"(input.toLuaList(InputAction::toLuaTable))
    "verbs"(verbs.toLuaList(Verb::toLuaVerb))
}

context(lua: LuaScriptEngine, world: World)
private fun Hand.toLuaComponent(): LuaTable = luaTable {
    "owner"(owner.luaEntity())
    "side"(
        when (side) {
            HandType.MAIN -> "main"
            HandType.OFFHAND -> "offhand"
        }
    )
    "opposite"(opposite.luaEntity())
    "item"(item?.luaEntity())
}

context(lua: LuaScriptEngine, world: World)
private fun Interactor.toLuaComponent(): LuaTable = luaTable {
    "owner"(owner.luaEntity())
}

data class LuaUsingItem(val world: World, val component: UsingItem)

context(lua: LuaScriptEngine)
fun UsingItemUserdataType() = LuaUserdataType<LuaUsingItem> {
    indexSelf { self, key ->
        when(key.tojstring()) {
            "item" -> with(self.world) { self.component.item.luaEntity() }
            else -> NIL
        }
    }
    newIndexSelf { self, key, value ->
        if (key.tojstring() == "item") {
            self.component.item = value.checkLuaEntity()
        }
    }
}

private inline fun <reified T : Component> World.removeOrphanedLuaComponents(type: ScriptComponentType) {
    val source = componentManager.getComponentArray<T>()
    iterate(type) { entity, _ ->
        if (source.componentOf(entity) == null) {
            entity.removeComponent(type)
        }
    }
}

context(lua: LuaScriptEngine)
fun World.pushInteractionComponents() {
    val world = this
    removeOrphanedLuaComponents<VerbLookup>(CoreScriptComponents.VERB_LOOKUP)
    removeOrphanedLuaComponents<Hand>(CoreScriptComponents.HAND)
    removeOrphanedLuaComponents<Interactor>(CoreScriptComponents.INTERACTOR)
    removeOrphanedLuaComponents<InteractionInterrupt>(CoreScriptComponents.INTERACTION_INTERRUPT)

    iterate<VerbLookup> { entity, lookup ->
        entity.setLuaScriptComponent(lookup.toLuaComponent(), CoreScriptComponents.VERB_LOOKUP)
    }
    iterate<Hand> { entity, hand ->
        entity.setLuaScriptComponent(hand.toLuaComponent(), CoreScriptComponents.HAND)
    }
    iterate<Interactor> { entity, interactor ->
        entity.setLuaScriptComponent(interactor.toLuaComponent(), CoreScriptComponents.INTERACTOR)
    }
    iterate<InteractionInterrupt> { entity, _ ->
        entity.setLuaScriptComponent(luaTable {}, CoreScriptComponents.INTERACTION_INTERRUPT)
    }
    iterate<UsingItem> { entity, usingItem ->
        entity.setLuaScriptComponent(
            lua.usingItemUserdataType.newInstance(LuaUsingItem(world, usingItem)),
            CoreScriptComponents.USING_ITEM
        )
    }
}

context(lua: LuaScriptEngine)
fun World.collectPulledInteractionVerbs() {
    iterate<VerbLookup> { entity, lookup ->
        val scriptedLookup = entity.getComponent(CoreScriptComponents.VERB_LOOKUP)
        val verbs = scriptedLookup?.castedLuaValue
            ?.checktable()
            ?.get("verbs")
            ?.checktable()
            ?.toList { it.checktable().toVerb() }
            .orEmpty()
        lookup.verbs.clear()
        lookup.verbs.addAll(verbs)
    }
}

fun World.pullUsingItem() {
    removeOrphanedLuaComponents<UsingItem>(CoreScriptComponents.USING_ITEM)
    projectLuaComponent<UsingItem>(CoreScriptComponents.USING_ITEM) { value, _ ->
        val item = if (value.isuserdata()) {
            value.asUserdataOrThrow<LuaUsingItem>().component.item
        } else {
            value.checktable()["item"].checkLuaEntity()
        }
        UsingItem(item)
    }
}
