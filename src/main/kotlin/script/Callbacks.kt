package org.lain.engine.script

class ScriptCallback<C : ScriptContext, R : Any>(val scripts: List<Script<C, R>> = listOf()) {
    fun execute(ctx: C) {
        scripts.forEach { it.execute(ctx) }
    }
}

typealias VoidScriptCallback<C> = ScriptCallback<C, Unit>

typealias PlayerInstantiateCallback = VoidScriptCallback<ScriptContext.Player>
typealias PlayerDestroyCallback = VoidScriptCallback<ScriptContext.Player>
typealias WorldTickSecondCallback = VoidScriptCallback<ScriptContext.World>
typealias WorldTickCallback = VoidScriptCallback<ScriptContext.World>

data class Callbacks(
    val playerInstantiate: PlayerInstantiateCallback = ScriptCallback(),
    val playerDestroy: PlayerDestroyCallback = ScriptCallback(),
    val worldTickSecond: WorldTickSecondCallback = ScriptCallback(),
    val worldTick: WorldTickCallback = ScriptCallback(),
)