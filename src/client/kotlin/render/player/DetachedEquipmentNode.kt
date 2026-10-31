package org.lain.engine.client.render.player

import kotlinx.serialization.Serializable
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.model.geom.ModelPart
import org.lain.cyberia.ecs.*
import org.lain.engine.client.render.item.EngineItemDisplayContext
import org.lain.engine.client.render.item.TransformationRenderInstance
import org.lain.engine.client.render.item.extractModelReference
import org.lain.engine.client.render.scene.PropModel
import org.lain.engine.client.render.scene.RenderEntityIdSerializer
import org.lain.engine.client.render.scene.RenderEntityReferences
import org.lain.engine.client.render.scene.RenderScene
import org.lain.engine.client.render.scene.TransformationEditorTarget
import org.lain.engine.player.*
import org.lain.engine.world.World

@Serializable
data class DetachedEquipmentNode(
    @Serializable(with = RenderEntityIdSerializer::class)
    val node: EntityId,
    val slot: EquipmentSlot,
    val rotateLeftSide: Boolean,
    val mirrorX: Boolean,
)

@Serializable
data class DetachedEquipment(val nodes: List<DetachedEquipmentNode>) : RenderEntityReferences {
    override fun remapEntityReferences(remap: (EntityId) -> EntityId): DetachedEquipment =
        copy(nodes = nodes.map { it.copy(node = remap(it.node)) })
}

fun World.extractDetachedEquipmentRenderStates(scene: RenderScene) {
    iterate<PlayerExtraction, Equipment, PlayerComponent> { _, (node), (slots), (player) ->
        val nodes = slots
            .filterValues { item -> item.getComponent<Equippable>()?.display == OutfitDisplay.Separated }
            .mapNotNull { (slotId, item) ->
                val slot = scene.namespacedStorage.equipmentSlots[slotId] ?: return@mapNotNull null
                val model = item.extractModelReference()

                DetachedEquipmentNode(
                    node = scene.addEntity {
                        setComponent(
                            PropModel(
                                model,
                                if (slot.part == PlayerPart.HEAD) EngineItemDisplayContext.HEAD else EngineItemDisplayContext.OUTFIT
                            )
                        )
                        setComponent(
                            TransformationEditorTarget(
                                TransformationRenderInstance.Equipment(player.id.value, slotId),
                            )
                        )
                    },
                    slot = slot,
                    model.resolve().properties.rotateLeftSide,
                    model.resolve().properties.mirrorX,
                )
            }

        with(scene) {
            node.setComponent(DetachedEquipment(nodes))
        }
    }
}

fun modelPartOf(part: PlayerPart, model: PlayerModel<*>): ModelPart = when (part) {
    PlayerPart.HEAD -> model.head
    PlayerPart.LEFT_ARM -> model.leftArm
    PlayerPart.RIGHT_ARM -> model.rightArm
    PlayerPart.LEFT_PALM -> model.leftArm
    PlayerPart.RIGHT_PALM -> model.rightArm
    PlayerPart.BODY -> model.body
    PlayerPart.LEFT_LEG -> model.leftLeg
    PlayerPart.RIGHT_LEG -> model.rightLeg
    PlayerPart.LEFT_FEET -> model.leftLeg
    PlayerPart.RIGHT_FEET -> model.rightLeg
}
