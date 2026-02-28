package org.lain.engine.client.mc.render

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.model.ModelPart
import net.minecraft.client.render.entity.state.PlayerEntityRenderState
import net.minecraft.client.render.item.ItemRenderState
import org.lain.engine.player.ArmPose

private val ENGINE_MAIN_ARM_POSE_KEY = RenderStateDataKey<ArmPose>.create<ArmPose> { "Engine main arm pose" }

fun PlayerEntityRenderState.setMainArmPose(armPose: ArmPose) {
    setData(ENGINE_MAIN_ARM_POSE_KEY, armPose)
}

fun PlayerEntityRenderState.getMainArmPose() = getData(ENGINE_MAIN_ARM_POSE_KEY)

private val ENGINE_MINOR_ARM_POSE_KEY = RenderStateDataKey<ArmPose>.create<ArmPose> { "Engine minor arm pose" }

fun PlayerEntityRenderState.setMinorArmPose(armPose: ArmPose) {
    setData(ENGINE_MINOR_ARM_POSE_KEY, armPose)
}

fun PlayerEntityRenderState.getMinorArmPose() = getData(ENGINE_MINOR_ARM_POSE_KEY)

private val ENGINE_HEAD_EQUIPMENT_KEY = RenderStateDataKey<List<EquipmentRenderState>>.create<List<EquipmentRenderState>> { "Engine head equipment" }

data class EquipmentRenderState(val itemRenderState: ItemRenderState, val modelPart: ModelPart)

fun PlayerEntityRenderState.setEquipment(eq: List<EquipmentRenderState>) {
    setData(ENGINE_HEAD_EQUIPMENT_KEY, eq)
}

fun PlayerEntityRenderState.getEquipment() = getData(ENGINE_HEAD_EQUIPMENT_KEY)