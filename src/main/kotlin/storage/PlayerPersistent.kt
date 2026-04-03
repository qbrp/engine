package org.lain.engine.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.lain.engine.container.getContainerSlots
import org.lain.engine.item.ItemUuid
import org.lain.engine.player.*
import org.lain.engine.util.Color
import org.lain.engine.util.component.EntityId
import org.lain.engine.util.component.get
import org.lain.engine.util.component.require
import org.lain.engine.util.file.ENGINE_DIR
import org.lain.engine.util.file.ensureExists
import org.lain.engine.world.World
import org.lain.engine.world.world
import org.slf4j.LoggerFactory
import java.io.File

val STORAGE_DIR = ENGINE_DIR.resolve("storage")
    .also { it.mkdirs() }

val File.playerData
    get() = this.resolve("engine_players")
        .also { ensureExists() }

private val PLAYERS_JSON = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
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
    @SerialName("chat_heads") val chatHeads: Boolean = true,
    val equipment: Map<EquipmentSlot, ItemUuid> = mapOf(),
    val skinEyeY: Float = 2f,
)

fun World.getEquipmentContainerSlots(container: EntityId) = getContainerSlots(container)
    .mapKeys { (slotId, _) -> EquipmentSlot.ofSlot(slotId) }

fun File.savePersistentPlayerData(player: EnginePlayer) {
    val id = player.id.value.toString()
    val file = resolve("$id.json")

    val customName = player.customName
    val movementStatus = player.require<MovementStatus>()
    val speedIntention = movementStatus.intention
    val stamina = movementStatus.stamina
    val world = player.world

    val equipmentSlots = world.getEquipmentContainerSlots(player.equipmentContainer)

    file.ensureExists()
    file.writeText(
        PLAYERS_JSON.encodeToString(
            PersistentPlayerData(
                customName?.toPersistentData(),
                speedIntention,
                stamina,
                player.require<VoiceApparatus>().copy(),
                player.get<VoiceLoose>()?.copy(),
                player.chatHeadsEnabled,
                equipmentSlots.mapValues { (_, item) -> item.uuid },
                player.require<PlayerModel>().skinEyeY,
            )
        )
    )
}

fun File.parsePersistentPlayerData(playerId: PlayerId): PersistentPlayerData? {
    val file = resolve(playerId.value.toString() + ".json")
    if (!file.exists()) return null
    return PLAYERS_JSON.decodeFromString<PersistentPlayerData>(file.readText())
}