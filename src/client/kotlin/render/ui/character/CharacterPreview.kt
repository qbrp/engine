package org.lain.engine.client.render.ui.character

import com.mojang.authlib.GameProfile
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.PlayerModelPart
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lain.engine.Constants.GENDER_MOD_AVAILABLE
import org.lain.engine.client.render.CharacterSkin
import org.lain.engine.client.render.player.EnginePlayerRenderState
import org.lain.engine.client.render.player.setEngineRenderState
import org.lain.engine.mc.compat.updateCharacterEntity
import org.lain.engine.player.character.CharacterProfile
import org.lain.engine.player.character.computeCharacterModel
import org.lain.engine.player.scale
import java.util.*
import kotlin.math.roundToInt

class CharacterPreviewPlayer(
    level: ClientLevel,
    profile: GameProfile,
    private val skinGetter: () -> PlayerSkin
) : RemotePlayer(level, profile) {
    override fun getSkin(): PlayerSkin = skinGetter()

    override fun shouldShowName(): Boolean = false

    override fun isSpectator(): Boolean = false

    override fun isCreative(): Boolean = false

    override fun isModelPartShown(modelPart: PlayerModelPart): Boolean = true
}

internal fun createCharacterPreviewPlayer(
    level: ClientLevel,
    character: CharacterProfile,
    uuid: UUID,
    renderState: EnginePlayerRenderState? = null,
    skinGetter: () -> ResourceLocation
): CharacterPreviewPlayer {
    if (GENDER_MOD_AVAILABLE) {
        updateCharacterEntity(character, uuid)
    }

    return CharacterPreviewPlayer(
        level,
        GameProfile(uuid, "engine-preview-${uuid.toString().take(8)}"),
        {
            CharacterSkin(
                skinGetter(),
                computeCharacterModel(
                    character.bodyType,
                    character.biologicalCategory,
                    character.biologicalSex
                )
            )
        }
    ).apply {
        pose = Pose.STANDING
        renderState?.let { setEngineRenderState(it) }
    }
}

class CharacterPreviewWidget(
    val entity: CharacterPreviewPlayer,
    val profile: CharacterProfile
) {
    var x: Float = 0f
    var y: Float = 0f
    var scale: Float = 1f
    val height: Float
        get() = entity.bbHeight * scale

    fun computeScale(visualScale: Float) {
        val heightScale = profile.height.scale
        scale = BASE_ENTITY_SCALE * heightScale * visualScale
    }

    fun computePosition(x: Float, legY: Float) {
        this.x = x
        this.y = legY - (height / 2f).roundToInt()
    }

    fun render(guiGraphics: GuiGraphics) {
        entity.yBodyRotO = entity.yBodyRot
        entity.yRotO = entity.yRot
        entity.yHeadRotO = entity.yHeadRot

        val headRotation = Quaternionf().rotateX(entity.xRot * DEGREES_TO_RADIANS)
        val entityRotation = Quaternionf()
            .rotateZ(Math.PI.toFloat())
            .mul(headRotation)

        InventoryScreen.renderEntityInInventory(
            guiGraphics,
            x,
            y,
            scale,
            Vector3f(0f, entity.bbHeight / 2f + ENTITY_Y_OFFSET, 0f),
            entityRotation,
            headRotation,
            entity
        )
    }

    fun applyCursorLook(
        entityX: Int,
        mouseX: Int,
    ) {
        val yaw = ((mouseX - entityX) / 8f).coerceIn(-35f, 35f)
        applyRotation(180f - yaw * 0.35f, -yaw)
    }

    fun applyRotation(
        bodyYaw: Float,
        relativeHeadYaw: Float,
    ) {
        val headYaw = bodyYaw + relativeHeadYaw
        entity.yBodyRot = bodyYaw
        entity.yRot = headYaw
        entity.yHeadRot = headYaw
    }

    companion object {
        private const val BASE_ENTITY_SCALE = 60f
        private const val DEGREES_TO_RADIANS = 0.017453292f
        private const val ENTITY_Y_OFFSET = 0.0625f
    }
}