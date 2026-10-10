package org.lain.engine.util.ecs

import org.lain.engine.container.*
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.Savable
import org.lain.engine.data.SaveTag
import org.lain.engine.data.UnloadComponent
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.player.character.*
import org.lain.engine.player.interaction.*
import org.lain.engine.script.EntityRpcReceiver
import org.lain.engine.server.replication.Networked
import org.lain.engine.util.DebugName
import org.lain.engine.world.*

fun ComponentTypeRegistry.registerKotlinComponents() {
    // Events
    registerComponent<VoxelEvent>()
    registerComponent<BulletFireEvent>()
    registerComponent<SoundEmission>(isNetworking = true)

    registerComponent<Event>(isNetworking = true)

    // Entity lifecycle
    registerComponent<SaveTag>()
    registerComponent<UnloadComponent>()
    registerComponent<Location>()
    registerComponent<Savable>()

    registerComponent<Networked>()
    registerComponent<PersistentIdComponent>(id = "persistent_id")
    registerComponent<EntityRpcReceiver>(isNetworking = true, isSavable = true, replicationClass = null)
    registerComponent<DebugName>(id = "debug_name", isNetworking = true, isSavable = true)

    // Containers
    registerComponent<Entries>()
    registerComponent<Container>(isNetworking = true)
    registerComponent<HasContainer>()
    registerComponent<TransferOperation>()
    registerComponent<OccupiedSlots>()
    registerComponent<Slots>()

    // World and voxels
    registerComponent<DynamicVoxel>()
    registerComponent<ChunkedPos>()

    registerComponent<LightSource>(isSavable = true, isNetworking = true)
    registerComponent<Luminance>(isSavable = true, isNetworking = true)
    registerComponent<VoxelDoor>(isSavable = true, isNetworking = true)

    // Items
    registerComponent<HeldBy>()

    registerComponent<Item>(isSavable = true, isNetworking = true)
    registerComponent<ContainedIn>(isSavable = true, isNetworking = true, replicationClass = null)
    registerComponent<AssignedSlot>(isSavable = true, isNetworking = true)
    registerComponent<ItemName>(id = "core/item/name", isSavable = true, isNetworking = true)
    registerComponent<ItemTooltip>(id = "core/item/tooltip", isSavable = true, isNetworking = true)
    registerComponent<ItemSounds>(id = "core/item/sounds", isSavable = true, isNetworking = true)
    registerComponent<Count>(id = "core/item/count", isSavable = true, isNetworking = true)
    registerComponent<Mass>(id = "core/item/mass", isSavable = true, isNetworking = true)
    registerComponent<Writable>(id = "core/item/writable", isSavable = true, isNetworking = true)
    registerComponent<ItemAssets>(id = "core/item/assets", isSavable = true, isNetworking = true)
    registerComponent<ItemProgressionAnimations>(id = "core/item/progression_animations", isSavable = true, isNetworking = true)

    // Weapons
    registerComponent<Gun>(isSavable = true, isNetworking = true)
    registerComponent<GunDisplay>(isSavable = true, isNetworking = true)
    registerComponent<GunFireState>(isSavable = true, isNetworking = true)
    registerComponent<GunMagazines>(isSavable = true, isNetworking = true)
    registerComponent<Barrel>(isSavable = true, isNetworking = true)

    registerComponent<Magazine>(isSavable = true, isNetworking = true)

    // Players
    registerComponent<PlayerContainerTag>()
    registerComponent<PlayerContainer>()

    registerComponent<Equipment>(isNetworking = true, replicationClass = null)

    registerComponent<ArmStatus>(isNetworking = true)
    registerComponent<Narration>(isNetworking = true)

    registerComponent<CustomPlayerAttributes>(isNetworking = true)

    registerComponent<CharacterDisplay>(isNetworking = true)
    registerComponent<CharacterPhysical>(isNetworking = true)
    registerComponent<AppliedCharacter>(isNetworking = true)
    registerComponent<SelectedLook>(isNetworking = true)
    registerComponent<CharacterApplyEvent>(isNetworking = true)

    registerComponent<GiveAction>(isNetworking = true)
    registerComponent<HailAction>(isNetworking = true)
    registerComponent<ToggleGunModeCommand>(isNetworking = true)
    registerComponent<LoadGunFromOffhandCommand>(isNetworking = true)
    registerComponent<HoldGunTriggerCommand>(isNetworking = true)
    registerComponent<OpenWritableCommand>(isNetworking = true)
}
