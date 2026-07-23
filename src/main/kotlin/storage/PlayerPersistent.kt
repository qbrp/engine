package org.lain.engine.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.container.getContainerSlots
import org.lain.engine.player.*
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.character.AppliedCharacters
import org.lain.engine.server.ServerPlatform
import org.lain.engine.util.Color
import org.lain.engine.util.file.ENGINE_DIR
import org.lain.engine.util.file.ensureExists
import org.lain.engine.world.World
import org.slf4j.LoggerFactory
import java.io.File

val STORAGE_DIR = ENGINE_DIR.resolve("storage")
    .also { it.mkdirs() }

val File.playerData
    get() = this.resolve("engine-players")
        .also { ensureExists() }

private val PLAYERS_JSON = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    serializersModule = COMPONENT_SERIALIZERS_MODULE
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
data class PersistentCharacterData(
    val components: List<ComponentDto>,
    val look: String,
    val items: SerializedInventory
)

typealias SerializedInventory = String

@Serializable
data class PersistentPlayerData(
    @SerialName("custom_name") val customName: CustomNamePersistentData?,
    @SerialName("speed_intention") val speedIntention: Float,
    val stamina: Float,
    val voiceApparatus: VoiceApparatus,
    val voiceLoose: VoiceLoose?,
    @SerialName("chat_heads") val chatHeads: Boolean = true,
    val equipment: Map<EquipmentSlot, PersistentId> = mapOf(),
    val skinEyeY: Float = 2f,
    val components: List<ComponentDto> = listOf(),
    val characters: Map<String, PersistentCharacterData> = mapOf(),
    val appliedCharacter: String? = null,
)

fun World.getEquipmentContainerSlots(container: EntityId) = getContainerSlots(container)
    .mapKeys { (slotId, _) -> EquipmentSlot.ofSlot(slotId) }

fun File.savePersistentPlayerData(
    player: EnginePlayer
) = with(player.world) {
    val id = player.id.value.toString()
    val file = resolve("$id.json")

    val customName = player.customName
    val movementStatus = player.require<MovementStatus>()
    val speedIntention = movementStatus.intention
    val stamina = movementStatus.stamina

    val equipmentSlots = getEquipmentContainerSlots(player.equipmentContainer)
    val voiceApparatus = player.require<VoiceApparatus>().copy()
    val voiceLoose = player.get<VoiceLoose>()?.copy()
    val chatHeadsEnabled = player.chatHeadsEnabled
    val equipment = equipmentSlots.mapValues { (_, item) -> item.requireComponent<PersistentIdComponent>().id }
    val skinEyeY = player.require<EnginePlayerModel>().skinEyeY
    val savableComponents = componentManager.getSavableComponents(player.entity).map { it.toSnapshotDto() }
    val appliedCharacter = player.get<AppliedCharacter>()?.character?.profile?.id
    val characters = player.get<AppliedCharacters>()?.characters ?: emptyMap()

    //TODO: логировать ошибки
    CoroutineScope(Dispatchers.IO).launch {
        file.ensureExists()
        file.writeText(
            PLAYERS_JSON.encodeToString(
                PersistentPlayerData(
                    customName = customName?.toPersistentData(),
                    speedIntention = speedIntention,
                    stamina = stamina,
                    voiceApparatus = voiceApparatus,
                    voiceLoose = voiceLoose,
                    chatHeads = chatHeadsEnabled,
                    equipment = equipment,
                    skinEyeY = skinEyeY,
                    components = savableComponents,
                    appliedCharacter = appliedCharacter,
                    characters = characters,
                )
            )
        )
    }
}

fun File.parsePersistentPlayerData(playerId: PlayerId): PersistentPlayerData? {
    val file = resolve(playerId.value.toString() + ".json")
    if (!file.exists()) return null
    return PLAYERS_JSON.decodeFromString<PersistentPlayerData>(file.readText())
}
