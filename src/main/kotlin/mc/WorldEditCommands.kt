package org.lain.engine.mc

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.fabric.FabricAdapter
import org.lain.engine.util.injectMinecraftEngineServer
import org.lain.engine.util.isClassAvailable
import org.lain.engine.world.BULLET_DAMAGE_DECALS_LAYER
import org.lain.engine.world.VoxelPos
import org.lain.engine.world.removeDecals

fun isWorldEditAvailable() = isClassAvailable("com.sk89q.worldedit.WorldEdit")

fun ServerCommandDispatcher.registerWorldEditCommands() {
    val server by injectMinecraftEngineServer()
    val sessionManager = WorldEdit.getInstance().sessionManager

    val decalLayers = listOf(
        BULLET_DAMAGE_DECALS_LAYER,
    ).associateBy { it.name }

    register(
        literal("/removedecals")
            .then(
                selection("layer", decalLayers.keys.toList() + "all")
                    .executeCatching { ctx ->
                        val actor = FabricAdapter.adaptCommandSource(ctx.source)
                        val session = sessionManager.get(actor)
                        val blockPoses = session.selection
                        val world = blockPoses.world
                        val engineWorld = FabricAdapter.adapt(world).let { server.engine.getWorld(it) }
                        val layers = ctx.command.getString("layer").let {
                            if (it == "all") {
                                decalLayers.values.toList()
                            } else {
                                listOf(decalLayers[it] ?: friendlyError("Слой $decalLayers не существует"))
                            }
                        }
                        engineWorld.removeDecals(layers, blockPoses.map { VoxelPos(it.x(), it.y(), it.z()) })
                        ctx.sendFeedback(
                            "Удалены декали ${layers.joinToString { it.name }} на ${blockPoses.volume} блоках",
                            true
                        )
                    }
            )
    )
}