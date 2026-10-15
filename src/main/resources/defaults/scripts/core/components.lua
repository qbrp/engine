local lazy = require("core.util.lazy")

---@alias of<T> ComponentTypeHolder<T>

local type = function (id)
    return lazy.component_holder(id, engine.component.get_type)    
end

local binding = function (id)
    return lazy.component_holder(id, engine.component.get_binding)    
end

return {
    player_inventory = type("core/player/inventory"), ---@type of<PlayerInventoryComponent>
    player_mode = type("core/player/game_mode"), ---@type of<PlayerModeComponent>
    player_physics = type("core/player/physics"), ---@type of<PlayerPhysicsComponent>
    player_attributes = type("core/player/attributes"), ---@type of<PlayerAttributesComponent>
    player_custom_attributes = type("core/player/custom_attributes"), ---@type of<PlayerCustomAttributesComponent>
    player = type("core/player/component"), ---@type of<PlayerComponent>
    player_input = type("core/player/input"), ---@type of<PlayerInputComponent>
    movement_status = type("core/player/movement_status"), ---@type of<MovementStatusComponent>
    player_velocity = type("core/player/velocity"), ---@type of<PlayerVelocityComponent>
    jump = type("core/player/jump"), ---@type of<JumpComponent>
    location = binding("core/location"), ---@type of<LocationComponent>
    dynamic_voxel = type("core/voxel/dynamic_voxel"), ---@type of<DynamicVoxelComponent>
    use_restriction = type("core/voxel/use_restriction"), ---@type of<UseRestrictionComponent>
    light_source = type("core/light/source"), ---@type of<LightSourceComponent>
    luminance = type("core/light/luminance"), ---@type of<LuminanceComponent>
    entity_rpc_receiver = type("core/networking/entity_rpc_receiver"), ---@type of<EntityRpcReceiverComponent>
    entity_rpc_queue = type("core/networking/entity_rpc_queue"), ---@type of<EntityRpcQueueComponent>
    dynamic_voxel_interest = type("core/networking/voxel_interest"), ---@type of<DynamicVoxelInterestComponent>
    door = type("core/voxel/door"), ---@type of<DoorComponent>
    verb_lookup = type("core/interaction/verb_lookup"), ---@type of<VerbLookupComponent>
    hand = type("core/interaction/hand"), ---@type of<HandComponent>
    interactor = type("core/interaction/interactor"), ---@type of<InteractorComponent>
    using_item = type("core/interaction/using_item"), ---@type of<UsingItemComponent>
    give_item = binding("core/player/inventory/give"), ---@type of<GiveItemEvent>
    interaction_interrupt = type("core/interaction/interrupt"), ---@type of<InteractionInterruptComponent>
    item_name = binding("core/item/name"), ---@type of<ItemNameComponent>
    equippable = binding("core/item/equippable"), ---@type of<EquippableComponent>
    decrement = binding("core/item/decrement"), ---@type of<number>
    equipment = binding("core/player/equipment"), ---@type of<EquipmentComponent>
}
