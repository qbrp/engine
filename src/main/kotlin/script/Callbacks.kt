package org.lain.engine.script

data class ScriptCallback<C : ScriptContext>(
    val scripts: List<Script<C, Unit>> = listOf()
) {
    fun execute(ctx: C) {
        scripts.forEach { it.execute(ctx) }
    }
}

data class CallbackType<C : ScriptContext>(val id: String) {
    companion object {
        val PLAYER_INSTANTIATE = CallbackType<ScriptContext.Player>("player_instantiate")
        val PLAYER_DESTROY = CallbackType<ScriptContext.Player>("player_destroy")
        val WORLD_TICK_20 = CallbackType<ScriptContext.World>("world_tick_20")
        val WORLD_TICK = CallbackType<ScriptContext.World>("world_tick")
        val PLACE_VOXEL = CallbackType<ScriptContext.VoxelAction>("place_voxel")
        val ITEM_LOAD = CallbackType<ScriptContext.ItemLoad>("item_load")
        val PLAYER_INPUT_TICK = CallbackType<ScriptContext.PlayerInputTick>("player_input_tick")

        fun list() = listOf(
            PLAYER_INSTANTIATE, PLAYER_DESTROY, WORLD_TICK_20, WORLD_TICK, PLACE_VOXEL, ITEM_LOAD
        )
    }
}

class Callbacks(
    private val callbacks: Map<CallbackType<*>, ScriptCallback<*>> = mapOf()
) {
    fun <C : ScriptContext> of(type: CallbackType<C>): ScriptCallback<C>? {
        return callbacks[type] as? ScriptCallback<C>
    }
}