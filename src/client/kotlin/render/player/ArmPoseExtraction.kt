package org.lain.engine.client.render.player

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.client.render.scene.RenderScene
import org.lain.engine.player.ArmPose
import org.lain.engine.player.ArmStatus
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.armPoseOf
import org.lain.engine.world.World

data class ArmPoses(val main: ArmPose, val off: ArmPose) : Component

fun World.extractPlayerArmPoses(scene: RenderScene) {
    iterate<PlayerExtraction, PlayerInventory, ArmStatus>() { _, (node), inventory, armStatus ->
        val extendsArm = armStatus.extend
        val mainHandItem = inventory.mainHandItem
        val offHandItem = inventory.offHandItem
        val holdsMultipleItems = mainHandItem != null && offHandItem != null

        val mainHandSafety = mainHandItem.isGunWithoutSafety()
        val offHandSafety = offHandItem.isGunWithoutSafety()

        with(scene) {
            node.setComponent(
                ArmPoses(
                    armPoseOf(
                        holdsMultipleItems,
                        mainHandSafety,
                        extendsArm,
                        true,
                        mainHandItem != null,
                        offHandItem != null
                    ),
                    armPoseOf(
                        holdsMultipleItems,
                        offHandSafety,
                        extendsArm,
                        false,
                        offHandItem != null,
                        mainHandItem != null
                    )
                )
            )
        }
    }
}
