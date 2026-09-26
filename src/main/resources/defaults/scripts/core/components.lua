local lazy = require("core.util.lazy")

---@alias of<T> LazyComponentTypeHolder<T>
---@private

local type_of = lazy.component_holder

return {
    player_inventory = type_of("core/player/inventory"), ---@type of<PlayerInventoryComponent>
    player_mode = type_of("core/player/game_mode"), ---@type of<PlayerModeComponent>
    player_physics = type_of("core/player/physics"), ---@type of<PlayerPhysicsComponent>
    player_attributes = type_of("core/player/attributes"), ---@type of<PlayerAttributesComponent>
    player_custom_attributes = type_of("core/player/custom_attributes"), ---@type of<PlayerCustomAttributesComponent>
    player = type_of("core/player/component"), ---@type of<PlayerComponent>
    player_input = type_of("core/player/input"), ---@type of<PlayerInputComponent>
    movement_status = type_of("core/player/movement_status"), ---@type of<MovementStatusComponent>
    player_velocity = type_of("core/player/velocity"), ---@type of<PlayerVelocityComponent>
    jump = type_of("core/player/jump"), ---@type of<JumpComponent>
    location = type_of("core/location"), ---@type of<LocationComponent>
    dynamic_voxel = type_of("core/voxel/dynamic_voxel"), ---@type of<DynamicVoxelComponent>
    use_restriction = type_of("core/voxel/use_restriction"), ---@type of<UseRestrictionComponent>
    light_source = type_of("core/light/source"), ---@type of<LightSourceComponent>
    luminance = type_of("core/light/luminance"), ---@type of<LuminanceComponent>
    entity_rpc_receiver = type_of("core/networking/entity_rpc_receiver"), ---@type of<EntityRpcReceiverComponent>
    entity_rpc_queue = type_of("core/networking/entity_rpc_queue"), ---@type of<EntityRpcQueueComponent>
    dynamic_voxel_interest = type_of("core/networking/voxel_interest"), ---@type of<DynamicVoxelInterestComponent>
    door = type_of("core/voxel/door"), ---@type of<DoorComponent>
}