package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.item.*
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.util.Component
import org.lain.engine.util.get
import org.lain.engine.util.math.ImmutableVec3
import org.lain.engine.world.pos

interface ItemComponent : Component

@Serializable
data class ItemPacket(val item: ClientboundItemData) : Packet

@Serializable
data class ClientboundItemData(
    val id: ItemId,
    val uuid: ItemUuid,
    val pos: ImmutableVec3,
    val maxCount: Int,
    val count: Int,
    val components: List<ItemComponent>,
) {
    companion object {
        fun from(item: EngineItem) = ClientboundItemData(
            item.id,
            item.uuid,
            ImmutableVec3(item.pos),
            item.maxCount,
            item.count,
            listOfNotNull(
                item.get<ItemName>()?.copy(),
                item.get<Gun>()?.copy(),
                item.get<GunDisplay>()?.copy(),
                item.get<ItemTooltip>()?.copy(),
                item.get<Mass>()?.copy(),
                item.get<Writable>()?.copy()
            )
        )
    }
}

val CLIENTBOUND_ITEM_ENDPOINT = Endpoint<ItemPacket>()

@Serializable
data class WriteableUpdatePacket(val item: ItemUuid, val contents: List<String>) : Packet

val SERVERBOUND_WRITEABLE_UPDATE_ENDPOINT = Endpoint<WriteableUpdatePacket>()