package org.lain.engine.util.ecs

import org.lain.engine.container.*
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.Savable
import org.lain.engine.data.SaveTag
import org.lain.engine.data.UnloadComponent
import org.lain.engine.item.*
import org.lain.engine.mc.BlockStateVoxelMeta
import org.lain.engine.mc.ecs.MinecraftEntity
import org.lain.engine.mc.ecs.MinecraftItem
import org.lain.engine.mc.ecs.MinecraftPlayer
import org.lain.engine.player.*
import org.lain.engine.player.character.*
import org.lain.engine.player.interaction.*
import org.lain.engine.script.EntityRpcQueue
import org.lain.engine.script.EntityRpcReceiver
import org.lain.engine.script.dev.EntityInspectionComponent
import org.lain.engine.script.lua.library.LuaEntityComponent
import org.lain.engine.server.ReplayViewer
import org.lain.engine.server.replication.Changes
import org.lain.engine.server.replication.Interests
import org.lain.engine.server.replication.Networked
import org.lain.engine.server.replication.PlayerInstantiationConfirmation
import org.lain.engine.server.replication.PlayerReplicationState
import org.lain.engine.util.DebugName
import org.lain.engine.world.*

fun ComponentTypeRegistry.registerKotlinComponents() {
    // Events
    registerComponent<VoxelEvent>(id = "core/world/event/voxel")
    registerComponent<BulletFireEvent>(id = "core/world/event/bullet_fire")
    registerComponent<SoundEmission>(id = "core/world/event/sound", isNetworking = true)

    registerComponent<Event>(id = "core/event/component", isNetworking = true)

    // Entity lifecycle
    registerComponent<SaveTag>(id = "core/entity/save")
    registerComponent<UnloadComponent>(id = "core/entity/unload")
    registerComponent<Location>(id = "core/entity/location")
    registerComponent<Savable>(id = "core/entity/savable")

    registerComponent<Networked>(id = "core/networking/networked")
    registerComponent<PersistentIdComponent>(id = "core/entity/persistent_id")
    registerComponent<EntityRpcReceiver>(id = "core/networking/rpc_receiver", isNetworking = true, isSavable = true, replicationClass = null)
    registerComponent<DebugName>(id = "core/entity/debug_name", isNetworking = true, isSavable = true)

    // Containers
    registerComponent<Entries>(id = "core/container/entries")
    registerComponent<Container>(id = "core/container/component", isNetworking = true)
    registerComponent<HasContainer>(id = "core/container/reference")
    registerComponent<TransferOperation>(id = "core/container/transfer/operation")
    registerComponent<OccupiedSlots>(id = "core/container/occupied_slots")
    registerComponent<Slots>(id = "core/container/slots")

    // World and voxels
    registerComponent<DynamicVoxel>(id = "core/world/dynamic_voxel")
    registerComponent<ChunkedPos>(id = "core/world/chunked_position")

    registerComponent<LightSource>(id = "core/world/light_source", isSavable = true, isNetworking = true)
    registerComponent<Luminance>(id = "core/world/luminance", isSavable = true, isNetworking = true)
    registerComponent<VoxelDoor>(id = "core/world/voxel_door", isSavable = true, isNetworking = true)

    // Items
    registerComponent<HeldBy>(id = "core/item/held_by")
    registerComponent<Equippable>(id = "core/item/equippable", isSavable = true, isNetworking = true)

    registerComponent<Item>(id = "core/item/component", isSavable = true, isNetworking = true)
    registerComponent<ContainedIn>(id = "core/item/contained_in", isSavable = true, isNetworking = true, replicationClass = null)
    registerComponent<AssignedSlot>(id = "core/item/assigned_slot", isSavable = true, isNetworking = true)
    registerComponent<ItemName>(id = "core/item/name", isSavable = true, isNetworking = true)
    registerComponent<ItemTooltip>(id = "core/item/tooltip", isSavable = true, isNetworking = true)
    registerComponent<ItemSounds>(id = "core/item/sounds", isSavable = true, isNetworking = true)
    registerComponent<Count>(id = "core/item/count", isSavable = true, isNetworking = true)
    registerComponent<Mass>(id = "core/item/mass", isSavable = true, isNetworking = true)
    registerComponent<Writable>(id = "core/item/writable", isSavable = true, isNetworking = true)
    registerComponent<ItemAssets>(id = "core/item/assets", isSavable = true, isNetworking = true)
    registerComponent<ItemProgressionAnimations>(id = "core/item/progression_animations", isSavable = true, isNetworking = true)

    // Weapons
    registerComponent<Gun>(id = "core/item/gun/component", isSavable = true, isNetworking = true)
    registerComponent<GunDisplay>(id = "core/item/gun/display", isSavable = true, isNetworking = true)
    registerComponent<GunFireState>(id = "core/item/gun/fire_state", isSavable = true, isNetworking = true)
    registerComponent<GunMagazines>(id = "core/item/gun/magazines", isSavable = true, isNetworking = true)
    registerComponent<Barrel>(id = "core/item/gun/barrel", isSavable = true, isNetworking = true)

    registerComponent<Magazine>(id = "core/item/magazine/component", isSavable = true, isNetworking = true)

    // Players
    registerComponent<PlayerContainerTag>(id = "core/player/container_tag")
    registerComponent<PlayerContainer>(id = "core/player/container")

    registerComponent<Equipment>(id = "core/player/equipment", isNetworking = true, replicationClass = null)

    registerComponent<MovementStatus>(id = "core/player/state/movement_status", isNetworking = true)
    registerComponent<ArmStatus>(id = "core/player/arm_status", isNetworking = true)
    registerComponent<DisplayName>(id = "core/player/display_name", isNetworking = true)

    registerComponent<NarrationEvent>(id = "core/player/event/narration", isNetworking = true)

    registerComponent<CustomPlayerAttributes>(id = "core/player/state/custom_attributes", isNetworking = true)
    registerComponent<PlayerAttributes>(id = "core/player/state/attributes", isNetworking = true)

    registerComponent<CharacterDisplay>(id = "core/character/display", isNetworking = true)
    registerComponent<CharacterPhysical>(id = "core/character/physical", isNetworking = true)
    registerComponent<AppliedCharacter>(id = "core/character/applied", isNetworking = true)
    registerComponent<SelectedLook>(id = "core/character/selected_look", isNetworking = true)
    registerComponent<CharacterApplyEvent>(id = "core/character/event/apply", isNetworking = true)
    registerComponent<CharacterUnloadEvent>(id = "core/character/event/unload", isNetworking = true)

    registerComponent<GiveAction>(id = "core/interaction/action/give", isNetworking = true)
    registerComponent<HailAction>(id = "core/interaction/action/hail", isNetworking = true)
    registerComponent<ToggleGunModeCommand>(id = "core/interaction/command/toggle_gun_mode", isNetworking = true)
    registerComponent<LoadGunFromOffhandCommand>(id = "core/interaction/command/load_gun_from_offhand", isNetworking = true)
    registerComponent<HoldGunTriggerCommand>(id = "core/interaction/command/hold_gun_trigger", isNetworking = true)
    registerComponent<OpenWritableCommand>(id = "core/interaction/command/open_writable", isNetworking = true)

    // Container operations
    registerComponent<Approved>(id = "core/container/transfer/approved")
    registerComponent<AssignSlot>(id = "core/container/slot/assign")
    registerComponent<DetachedSlot>(id = "core/container/slot/detached")
    registerComponent<EntriesDirty>(id = "core/container/entries_dirty")
    registerComponent<MoveFrom>(id = "core/container/transfer/source")
    registerComponent<Rejected>(id = "core/container/transfer/rejected")
    registerComponent<Unresolved>(id = "core/container/transfer/unresolved")

    // Item runtime state
    registerComponent<GunBarrelLoad>(id = "core/item/gun/load_barrel")
    registerComponent<GunMagazineLoad>(id = "core/item/gun/load_magazine")
    registerComponent<GunMagazineTakeOff>(id = "core/item/gun/take_off_magazine")
    registerComponent<GunModeToggle>(id = "core/item/gun/toggle_mode")
    registerComponent<HoldsGunTrigger>(id = "core/item/gun/holds_trigger")
    registerComponent<MagazineLoadAction>(id = "core/item/magazine/load")
    registerComponent<OpenWritable>(id = "core/item/open_writable")

    // Minecraft adapters
    registerComponent<BlockStateVoxelMeta>(id = "core/minecraft/voxel/block_state")

    // Minecraft entity adapters
    registerComponent<MinecraftEntity>(id = "core/minecraft/entity")
    registerComponent<MinecraftItem>(id = "core/minecraft/item")
    registerComponent<MinecraftPlayer>(id = "core/minecraft/player")

    // Player runtime state
    registerComponent<AcousticMessageQueue>(id = "core/player/acoustic_messages")
    registerComponent<DecrementItem>(id = "core/item/decrement")
    registerComponent<DefaultPlayerAttributes>(id = "core/player/default_attributes")
    registerComponent<DeveloperMode>(id = "core/player/developer_mode")
    registerComponent<EnginePlayerModel>(id = "core/player/model")
    registerComponent<GiveItemEvent>(id = "core/player/event/give_item")
    registerComponent<Hearing>(id = "core/player/hearing")
    registerComponent<MessageQueue>(id = "core/player/messages")
    registerComponent<Orientation>(id = "core/player/orientation")
    registerComponent<PlayerChatHeadsComponent>(id = "core/player/chat_heads")
    registerComponent<PlayerComponent>(id = "core/player/reference")
    registerComponent<PlayerDisconnectEvent>(id = "core/player/event/disconnect")
    registerComponent<PlayerInventory>(id = "core/player/state/inventory")
    registerComponent<PlayerModeComponent>(id = "core/player/state/game_mode")
    registerComponent<PlayerPhysics>(id = "core/player/state/physics")
    registerComponent<SpawnMark>(id = "core/player/spawn")
    registerComponent<StartSpectatingMark>(id = "core/player/start_spectating")
    registerComponent<TransferItemEvent>(id = "core/player/event/transfer_item")
    registerComponent<Velocity>(id = "core/player/state/velocity")
    registerComponent<VoiceApparatus>(id = "core/player/voice_apparatus")
    registerComponent<VoiceLoose>(id = "core/player/voice_loose")

    // Character runtime state
    registerComponent<UsedCharacters>(id = "core/character/used")

    // Interaction runtime state
    registerComponent<Hand>(id = "core/interaction/state/hand")
    registerComponent<InteractionExecution>(id = "core/interaction/execution")
    registerComponent<InteractionInterrupt>(id = "core/interaction/interrupted")
    registerComponent<Interactor>(id = "core/interaction/state/interactor")
    registerComponent<PlayerInput>(id = "core/player/state/input")
    registerComponent<Progression>(id = "core/interaction/progression")
    registerComponent<TakeOffMagazineCommand>(id = "core/interaction/command/take_off_magazine")
    registerComponent<TestCommand>(id = "core/interaction/command/test")
    registerComponent<TestHold>(id = "core/interaction/test_hold")
    registerComponent<UsingInput>(id = "core/interaction/using_input")
    registerComponent<UsingItem>(id = "core/interaction/state/using_item")
    registerComponent<VerbLookup>(id = "core/interaction/state/verb_lookup")

    // Script runtime state
    registerComponent<EntityRpcQueue>(id = "core/networking/rpc_queue")

    // Script inspection
    registerComponent<EntityInspectionComponent>(id = "core/script/entity_inspection")

    // Lua entity handles
    registerComponent<LuaEntityComponent>(id = "core/script/lua_entity")

    // Server runtime state
    registerComponent<ReplayViewer>(id = "core/server/replay_viewer")

    // Replication state
    registerComponent<Changes>(id = "core/networking/changes")
    registerComponent<Interests>(id = "core/networking/interests")
    registerComponent<PlayerInstantiationConfirmation>(id = "core/networking/player_instantiation_confirmation")
    registerComponent<PlayerReplicationState>(id = "core/networking/player_state")

    // World runtime state
    registerComponent<CommandInvoke>(id = "core/world/command")
    registerComponent<DynamicVoxelInterest>(id = "core/world/voxel_interest")
    registerComponent<HintDestroyEvent>(id = "core/world/event/destroy_hint")
    registerComponent<PlayerCommandAccess>(id = "core/world/command_access")
    registerComponent<RecoilEvent>(id = "core/world/event/recoil")
    registerComponent<ShootGeometry>(id = "core/world/shoot_geometry")
    registerComponent<VoxelDestroyEvent>(id = "core/world/event/destroy_voxel")
    registerComponent<WorldEntity>(id = "core/world/entity")
}
