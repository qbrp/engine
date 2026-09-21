package org.lain.engine.util.ecs

import org.lain.engine.container.AssignedSlot
import org.lain.engine.container.ContainedIn
import org.lain.engine.container.Container
import org.lain.engine.container.Entries
import org.lain.engine.container.HasContainer
import org.lain.engine.container.OccupiedSlots
import org.lain.engine.container.Slots
import org.lain.engine.container.TransferOperation
import org.lain.engine.item.Barrel
import org.lain.engine.item.Count
import org.lain.engine.item.Flashlight
import org.lain.engine.item.Gun
import org.lain.engine.item.GunDisplay
import org.lain.engine.item.GunFireState
import org.lain.engine.item.GunMagazines
import org.lain.engine.item.HeldBy
import org.lain.engine.item.Item
import org.lain.engine.item.ItemAssets
import org.lain.engine.item.ItemName
import org.lain.engine.item.ItemProgressionAnimations
import org.lain.engine.item.ItemSounds
import org.lain.engine.item.ItemTooltip
import org.lain.engine.item.Magazine
import org.lain.engine.item.Mass
import org.lain.engine.item.Writable
import org.lain.engine.player.ArmStatus
import org.lain.engine.player.CustomPlayerAttributes
import org.lain.engine.player.Narration
import org.lain.engine.player.Outfit
import org.lain.engine.player.PlayerContainer
import org.lain.engine.player.PlayerContainerTag
import org.lain.engine.player.PlayerEquipment
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.character.CharacterApplyEvent
import org.lain.engine.player.character.CharacterDisplay
import org.lain.engine.player.character.CharacterPhysical
import org.lain.engine.player.character.SelectedLook
import org.lain.engine.player.interaction.ActionSyncEvent
import org.lain.engine.player.interaction.GiveAction
import org.lain.engine.player.interaction.GunModeToggleAction
import org.lain.engine.player.interaction.HailAction
import org.lain.engine.player.interaction.StartShootAction
import org.lain.engine.player.interaction.StopShootAction
import org.lain.engine.player.interaction.WritableOpenAction
import org.lain.engine.script.EntityRpcReceiver
import org.lain.engine.server.Networked
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.Savable
import org.lain.engine.data.SaveTag
import org.lain.engine.data.UnloadComponent
import org.lain.engine.util.DebugName
import org.lain.engine.world.BulletFireEvent
import org.lain.engine.world.ChunkedPos
import org.lain.engine.world.DynamicVoxel
import org.lain.engine.world.Event
import org.lain.engine.world.LightSource
import org.lain.engine.world.Location
import org.lain.engine.world.Luminance
import org.lain.engine.world.VoxelDoor
import org.lain.engine.world.VoxelEvent
import org.lain.engine.world.WorldSoundPlayRequest

fun ComponentTypeRegistry.registerKotlinComponents() {
    // Events
    registerComponent<VoxelEvent>()
    registerComponent<BulletFireEvent>()
    registerComponent<WorldSoundPlayRequest>()
    registerComponent<WorldSoundPlayRequest.Item>(id = "sound_play_item")
    registerComponent<WorldSoundPlayRequest.Positioned>(id = "sound_play_positioned")

    registerComponent<Event>(isNetworking = true)
    registerComponent<ActionSyncEvent>(isNetworking = true, replicationClass = null)

    // Entity lifecycle
    registerComponent<SaveTag>()
    registerComponent<UnloadComponent>()
    registerComponent<Location>()
    registerComponent<Savable>()

    registerComponent<Networked>()
    registerComponent<PersistentIdComponent>()
    registerComponent<EntityRpcReceiver>(isNetworking = true, isSavable = true, replicationClass = null)
    registerComponent<DebugName>(isNetworking = true, isSavable = true)

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
    registerComponent<ItemName>(isSavable = true, isNetworking = true)
    registerComponent<ItemTooltip>(isSavable = true, isNetworking = true)
    registerComponent<ItemSounds>(isSavable = true, isNetworking = true)
    registerComponent<Count>(isSavable = true, isNetworking = true)
    registerComponent<Mass>(isSavable = true, isNetworking = true)
    registerComponent<Outfit>(isSavable = true, isNetworking = true)
    registerComponent<Flashlight>(isSavable = true, isNetworking = true)
    registerComponent<Writable>(isSavable = true, isNetworking = true)
    registerComponent<ItemAssets>(isSavable = true, isNetworking = true)
    registerComponent<ItemProgressionAnimations>(isSavable = true, isNetworking = true)

    // Weapons
    registerComponent<Gun>(isSavable = true, isNetworking = true)
    registerComponent<GunDisplay>(isSavable = true, isNetworking = true)
    registerComponent<GunFireState>(isSavable = true, isNetworking = true)
    registerComponent<GunMagazines>(isSavable = true, isNetworking = true)
    registerComponent<Barrel>(isSavable = true, isNetworking = true)

    registerComponent<Magazine>(isSavable = true, isNetworking = true)

    // Players
    registerComponent<PlayerEquipment>()
    registerComponent<PlayerContainerTag>()
    registerComponent<PlayerContainer>()

    registerComponent<ArmStatus>(isNetworking = true)
    registerComponent<Narration>(isNetworking = true)

    registerComponent<CustomPlayerAttributes>(isNetworking = true)

    registerComponent<CharacterDisplay>(isNetworking = true)
    registerComponent<CharacterPhysical>(isNetworking = true)
    registerComponent<AppliedCharacter>(isNetworking = true)
    registerComponent<SelectedLook>(isNetworking = true)
    registerComponent<CharacterApplyEvent>(isNetworking = true)

    registerComponent<GiveAction>()
    registerComponent<HailAction>()
    registerComponent<GunModeToggleAction>(replicationClass = GunModeToggleAction::class)
    registerComponent<StartShootAction>(replicationClass = StartShootAction::class)
    registerComponent<StopShootAction>(replicationClass = StopShootAction::class)
    registerComponent<WritableOpenAction>(replicationClass = WritableOpenAction::class)
}
