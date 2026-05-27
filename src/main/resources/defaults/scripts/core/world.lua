require("core.bridge")
require("core.util")
require("core.component")

--- debug
---@return string
function listworlds()
    return tablestr(worlds)
end

---@class World
---@field id string
---@field is_client boolean
---@field state Entity
---@field players table<string, Player>
---@field invoke_command fun(self: World, command: string)
---@field iterate fun(self: World, func: fun(...))
---@field add_entity fun(self: World): Entity
---@field set_dynamic_voxel fun(self: World, voxel_pos: number[], networked: boolean): Entity
---@field get_dynamic_voxel fun(self: World, voxel_pos: number[]): Entity?
World = World
worlds = worlds

---@class Entity
---@field
---@field mark_dirty fun(self: Entity, component: Component)
---@field has_component fun(self: Entity, component: Component): boolean
---@field get_component fun(self: Entity, component: Component): table
---@field remove_component fun(self: Entity, component: Component): table?
---@field set_component fun(self: Entity, component: Component): table?
---@field get_all_components fun(self: Entity): Component[]
Entity = Entity

---@class VoxelMeta
---@field id string
---@field has_tag fun(tag: string): boolean

--------------------------------------------

---@class LocationComponent : Component
---@field vector number[]
LocationComponent = Component.of("core/location")

---@field x number
---@field y number
---@field z number
---@return LocationComponent
function LocationComponent.new(x, y, z)
    assert(x ~= nil, "x must be not null")
    assert(y ~= nil, "y must be not null")
    assert(z ~= nil, "z must be not null")
    return LocationComponent:construct({ vector = { x, y, z } })
end

---@field vector number[]
---@return LocationComponent
function LocationComponent.vector(vector)
    assert(vector ~= nil, "vector must be not null")
    return LocationComponent.new(vector[1], vector[2], vector[3])
end

--------------------------------------------

---@class DynamicVoxelComponent : Component
---@field pos number[]
DynamicVoxelComponent = Component.of("core/voxel/dynamic_voxel")

---@class UseRestrictionComponent : Component
UseRestrictionComponent = Component.of("core/voxel/use_restriction")

--------------------------------------------

---@generic T : Component
---@param component T
---@param factory fun(): T
---@return T
function Entity:get_or_create_component(component, factory)
    assert(factory ~= nil, "component factory must be not null")
    assert(component ~= nil, "component type must be not null")
    local component2 = self:get_component(component)
    if (component2 == nil) then
        component2 = factory()
        assert(component ~= nil, "component factory must return not null value")
        self:set_component(component2)
    end
    return component2
end
