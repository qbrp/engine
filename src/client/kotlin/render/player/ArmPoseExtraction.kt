package org.lain.engine.client.render.player

import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.client.render.scene.RenderScene
import org.lain.engine.player.ArmStatus
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.armPoseOf
import org.lain.engine.world.World

fun World.extractPlayerArmPoses(scene: RenderScene) {
    iterate<PlayerExtraction, PlayerInventory, ArmStatus>() { _, (node), inventory, armStatus ->
        val extendsArm = armStatus.extend
        val mainHandItem = inventory.mainHandItem
        val offHandItem = inventory.offHandItem
        val holdsMultipleItems = mainHandItem != null && offHandItem != null

        val mainHandSafety = mainHandItem.isGunWithoutSafety()
        val offHandSafety = offHandItem.isGunWithoutSafety()

        with(scene) {
            node.requireComponent<PlayerRenderNode>().apply {
                mainArmPose = armPoseOf(
                    holdsMultipleItems,
                    mainHandSafety,
                    extendsArm,
                    true,
                    mainHandItem != null,
                    offHandItem != null
                )
                offArmPose = armPoseOf(
                    holdsMultipleItems,
                    offHandSafety,
                    extendsArm,
                    false,
                    offHandItem != null,
                    mainHandItem != null
                )
            }
        }
    }
}
