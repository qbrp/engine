package org.lain.engine.transport.packet

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.protobuf.ProtoBuf
import org.lain.engine.container.AssignedSlot
import org.lain.engine.container.ContainedIn
import org.lain.engine.item.*
import org.lain.engine.player.Outfit
import org.lain.engine.storage.polymorphicComponent
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.PacketCodec
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.get
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.require
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
        polymorphic(Component::class) { polymorphicComponent() }
    }
}

@Serializable
data class ItemPacket(val item: ClientboundItemData) : Packet

@Serializable
data class ClientboundItemData(
    val id: ItemId,
    val uuid: ItemUuid,
    val components: List<ItemComponent>,
    val entityComponents: List<Component>
) {
    companion object {
        fun from(world: World, item: EngineItem) = ClientboundItemData(
            item.id,
            item.uuid,
            listOfNotNull(
                item.get<ItemAssets>()?.copy(),
                item.get<ItemName>()?.copy(),
                item.get<Gun>()?.copy(),
                item.get<GunDisplay>()?.copy(),
                item.get<ItemTooltip>()?.copy(),
                item.get<Mass>()?.copy(),
                item.get<Writable>()?.copy(),
                item.get<ItemSounds>()?.copy(),
                item.get<Flashlight>()?.copy(),
                item.get<Outfit>()?.copy(),
                item.get<ItemProgressionAnimations>()?.copy(),
                item.require<Count>().copy()
            ),
            with(world) {
                listOfNotNull<Component>(
                    item.entity.getComponent<ContainedIn>()?.copy(),
                    item.entity.getComponent<AssignedSlot>()?.copy(),
                )
            }
        )
    }
}

val CLIENTBOUND_ITEM_ENDPOINT = Endpoint<ItemPacket>(
    PacketCodec.Kotlinx(
        ItemPacket.serializer(),
        ItemProtobuf
    ),
)

@Serializable
data class WriteableUpdatePacket(val item: ItemUuid, val contents: List<String>) : Packet

val SERVERBOUND_WRITEABLE_UPDATE_ENDPOINT = Endpoint<WriteableUpdatePacket>()