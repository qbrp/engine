package org.lain.engine.util.file

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.lain.engine.player.CustomName
import org.lain.engine.player.InvalidCustomNameException
import org.lain.engine.player.MovementStatus
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerId
import org.lain.engine.player.VoiceApparatus
import org.lain.engine.player.VoiceLoose
import org.lain.engine.player.chatHeadsEnabled
import org.lain.engine.player.customName
import org.lain.engine.util.Color
import org.lain.engine.util.get
import org.lain.engine.util.require
import org.slf4j.LoggerFactory

private val PLAYERS_DATA_DIR = ENGINE_DIR.resolve("players")
private val PLAYERS_JSON = Json {
    prettyPrint = true
}
private val PLAYER_DATA_LOGGER = LoggerFactory.getLogger("Engine Player Data")

@Serializable
data class CustomNamePersistentData(
    val string: String,
    val color1: Color,
    val color2: Color? = null,
) {
    fun toDomain(name: String): CustomName? = try {
        CustomName(string, color1, color2)
    } catch (e: InvalidCustomNameException) {
        PLAYER_DATA_LOGGER.warn("Игрок $name имеет неправильное имя: $string (${e.message})")
        null
    }
}

private fun CustomName.toPersistentData() = CustomNamePersistentData(string, color1, color2)

@Serializable
data class PersistentPlayerData(
    @SerialName("custom_name") val customName: CustomNamePersistentData?,
    @SerialName("speed_intention") val speedIntention: Float,
    val stamina: Float,
    val voiceApparatus: VoiceApparatus,
    val voiceLoose: VoiceLoose?,
    @SerialName("chat_heads") val chatHeads: Boolean = true
)

fun savePersistentPlayerData(player: Player) {
    val id = player.id.value.toString()
    val file = PLAYERS_DATA_DIR.resolve("$id.json")

    val customName = player.customName
    val movementStatus = player.require<MovementStatus>()
    val speedIntention = movementStatus.intention
    val stamina = movementStatus.stamina

    file.ensureExists()
    file.writeText(
        PLAYERS_JSON.encodeToString(
            PersistentPlayerData(
                customName?.toPersistentData(),
                speedIntention,
                stamina,
                player.require(),
                player.get(),
                player.chatHeadsEnabled
            )
        )
    )
}

fun parsePersistentPlayerData(playerId: PlayerId): PersistentPlayerData? {
    val file = PLAYERS_DATA_DIR.resolve(playerId.value.toString() + ".json")
    if (!file.exists()) return null
    return PLAYERS_JSON.decodeFromString<PersistentPlayerData>(file.readText())
}