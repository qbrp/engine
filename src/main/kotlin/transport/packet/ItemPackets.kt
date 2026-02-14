package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.item.*
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.util.get
import org.lain.engine.util.math.ImmutableVec3
import org.lain.engine.world.pos

@Serializable
data class ItemPacket(val item: ClientboundItemData) : Packet

@Serializable
data class ClientboundItemData(
    val id: ItemId,
    val uuid: ItemUuid,
    val position: ImmutableVec3,
    val name: ItemName? = null,
    val gun: Gun?,
    val gunDisplay: GunDisplay?,
    val tooltip: ItemTooltip?,
    val count: Int?
) {
    companion object {
        fun from(item: EngineItem) = ClientboundItemData(
            item.id,
            item.uuid,
            ImmutableVec3(item.pos),
            item.get<ItemName>()?.copy(),
            item.get<Gun>()?.copy(),
            item.get<GunDisplay>()?.copy(),
            item.get<ItemTooltip>()?.copy(),
            item.get<Count>()?.value
        )
    }
}

val CLIENTBOUND_ITEM_ENDPOINT = Endpoint<ItemPacket>()