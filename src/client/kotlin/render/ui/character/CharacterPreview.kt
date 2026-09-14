package org.lain.engine.client.render.ui.character

import com.mojang.authlib.GameProfile
import com.wildfire.main.WildfireGender
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.world.entity.Pose
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.mc.compat.GENDER_MOD_AVAILABLE
import org.lain.engine.mc.compat.wildfireGender
import org.lain.engine.player.character.CharacterProfile
import java.util.UUID

internal class CharacterPreviewPlayer(
    level: ClientLevel,
    profile: GameProfile,
    private val previewSkin: PlayerSkin
) : RemotePlayer(level, profile) {
    override fun getSkin(): PlayerSkin = previewSkin

    override fun isSpectator(): Boolean = false

    override fun isCreative(): Boolean = false
}

internal fun createCharacterPreviewPlayer(
    character: CharacterProfile,
    skin: PlayerSkin
): CharacterPreviewPlayer? {
    val level = MinecraftClient.level ?: return null
    val uuid = character.id.toStableUuid()
    if (GENDER_MOD_AVAILABLE) {
        WildfireGender.getOrAddPlayerById(uuid).apply {
            updateGender(character.biologicalSex.wildfireGender)
            updateBustSize(character.genderParams.breastSize)
        }
    }

    return CharacterPreviewPlayer(
        level,
        GameProfile(uuid, "engine-preview-${uuid.toString().take(8)}"),
        skin
    ).apply {
        pose = Pose.STANDING
    }
}

private fun String.toStableUuid(): UUID {
    return runCatching { UUID.fromString(this) }
        .getOrElse { UUID.nameUUIDFromBytes(toByteArray(Charsets.UTF_8)) }
}
