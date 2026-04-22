package org.lain.engine.transport.packet

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.protobuf.ProtoBuf
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.container.AssignedSlot
import org.lain.engine.container.ContainedIn
import org.lain.engine.item.*
import org.lain.engine.player.Outfit
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.componentSubclassSerializers
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.PacketCodec
import org.lain.engine.world.World

interface ItemComponent : Component

@OptIn(ExperimentalSerializationApi::class)
internal val ItemProtobuf = ProtoBuf {
    serializersModule = SerializersModule {
        polymorphic(ItemComponent::class) {
            subclass(ItemAssets::class)
            subclass(ItemName::class)
            subclass(Gun::class)
            subclass(GunDisplay::class)
            subclass(ItemTooltip::class)
            subclass(Mass::class)
            subclass(Writable::class)
            subclass(ItemSounds::class)
            subclass(Outfit::class)
            subclass(ItemProgressionAnimations::class)
            subclass(Flashlight::class)
            subclass(Count::class)
        }
        polymorphic(Component::class) { componentSubclassSerializers() }
    }
}

@Serializable
data class ItemPacket(val item: ClientboundItemData) : Packet

@Serializable
data class ClientboundItemData(
    val id: ItemId,
    val uuid: PersistentId,
    val components: List<ItemComponent>,
) {
    companion object {
        fun from(world: World, item: EngineItem): ClientboundItemData = with(world) {
            val meta = item.requireComponent<ItemMeta>()
            ClientboundItemData(
                meta.id,
                item.requireComponent(),
                listOfNotNull(
                    item.getComponent<ItemAssets>()?.copy(),
                    item.getComponent<ItemName>()?.copy(),
                    item.getComponent<Gun>()?.copy(),
                    item.getComponent<GunDisplay>()?.copy(),
                    item.getComponent<ItemTooltip>()?.copy(),
                    item.getComponent<Mass>()?.copy(),
                    item.getComponent<Writable>()?.copy(),
                    item.getComponent<ItemSounds>()?.copy(),
                    item.getComponent<Flashlight>()?.copy(),
                    item.getComponent<Outfit>()?.copy(),
                    item.getComponent<ItemProgressionAnimations>()?.copy(),
                    item.requireComponent<Count>().copy(),
                    item.getComponent<ContainedIn>()?.copy(),
                    item.getComponent<AssignedSlot>()?.copy(),
                )
            )
        }
    }
}

val CLIENTBOUND_ITEM_ENDPOINT = Endpoint<ItemPacket>(
    PacketCodec.Kotlinx(
        ItemPacket.serializer(),
        ItemProtobuf
    ),
)

@Serializable
data class WriteableUpdatePacket(val item: PersistentId, val contents: List<String>) : Packet

val SERVERBOUND_WRITEABLE_UPDATE_ENDPOINT = Endpoint<WriteableUpdatePacket>()