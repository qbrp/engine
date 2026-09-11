package org.lain.engine.script

data class CallbackType<C : ScriptContext, R : ScriptValue>(val id: String) {
    companion object {
        private val types = mutableListOf<CallbackType<*, *>>()
        val typeList: List<CallbackType<*, *>>
            get() = types

        val PLAYER_INSTANTIATE = voidType<ScriptContext.Player>("player_instantiate")
        val PLAYER_DESTROY = voidType<ScriptContext.Player>("player_destroy")
        val WORLD_TICK_20 = voidType<ScriptContext.World>("world_tick_20")
        val WORLD_TICK = voidType<ScriptContext.World>("world_tick")
        val PLACE_VOXEL = voidType<ScriptContext.VoxelAction>("place_voxel")
        val ITEM_LOAD = voidType<ScriptContext.Item>("item_load")
        val PLAYER_INPUT_TICK = voidType<ScriptContext.PlayerInputTick>("player_input_tick")

        fun <T : ScriptContext, R : ScriptValue> type(id: String) = CallbackType<T, R>(id)
            .also { types += it }

        fun <T : ScriptContext> voidType(id: String) = CallbackType<T, SNil>(id)
            .also { types += it }
    }
}

class Callbacks(
    private val callbacks: Map<CallbackType<*, *>, Script<*, *>> = mapOf()
) {
    fun <C : ScriptContext, R : ScriptValue> of(type: CallbackType<C, R>): Script<C, R>? {
        return callbacks[type] as? Script<C, R>
    }
}