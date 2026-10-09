package org.lain.engine.client.render.ui.character

import com.mojang.authlib.GameProfile
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.PlayerModelPart
import org.lain.engine.Constants.GENDER_MOD_AVAILABLE
import org.lain.engine.mc.compat.updateCharacterEntity
import org.lain.engine.player.character.CharacterProfile
import java.util.*

internal class CharacterPreviewPlayer(
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
    skinGetter: () -> PlayerSkin
): CharacterPreviewPlayer {
    val uuid = UUID.nameUUIDFromBytes(character.id.value.toByteArray(Charsets.UTF_8))
    if (GENDER_MOD_AVAILABLE) {
        updateCharacterEntity(character, uuid)
    }

    return CharacterPreviewPlayer(
        level,
        GameProfile(uuid, "engine-preview-${uuid.toString().take(8)}"),
        skinGetter
    ).apply {
        pose = Pose.STANDING
    }
}
