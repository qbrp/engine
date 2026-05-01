require("core.bridge")
require("core.util")
require("core.component")

---@type World for EmmyLua
local World = World

---@type Entity for EmmyLua
local Entity = Entity

---@param types ComponentType[]|Component[]
---@param fun fun(entity: number, ...)
function World:iterate(types, fun)
    local mapped_types = map(types, function(elem)
        return elem.type or elem
    end)
    self:__iterate(fun, table.unpack(mapped_types))
end

---@param types ComponentType[]|Component[]
---@param fun fun(world: World, entity: number, ...)
function World:iterate_in(types, fun)
    self:iterate(types, function(entity, ...)
        fun(self, entity, ...)
    end)
end

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

---@param voxel_pos number[]
---@param networked boolean default false
---@return Entity
function World:set_dynamic_voxel(voxel_pos, networked)
    assert(voxel_pos, "voxel pos must be not null")
    networked = networked == true
    local entity = self:__set_dynamic_voxel(voxel_pos, networked)
    debug("voxel", "(" .. entity.id .. ") created dynamic voxel, networked = " .. tostring(networked))
    return entity
end

--------------------------------------------

---@param type ComponentType
---@param entity_id number
---@return table
function World:get_component(entity_id, type)
    assert(entity_id, "entity_id must be not null")
    assert(type, "component type must be not null")
    return self:__get_component(entity_id, type.type or type)
end

---@param type ComponentType
---@param entity_id number
---@return Component?
function World:remove_component(entity_id, type)
    assert(entity_id, "entity_id must be not null")
    assert(type, "component type must be not null")
    local component_type = type.type or type
    debug("component", "(" .. entity_id .. ") remove " .. component_type.id)
    return self:__remove_component(entity_id, component_type)
end

---@param entity_id number
---@param component Component?
---@param type ComponentType
function World:set_component(entity_id, component, type)
    assert(entity_id, "entity_id must be not null")
    local component_type = component.type or type
    assert(component_type, "component must be not null")
    debug("component", "(" .. entity_id .. ") set " .. component_type.id)
    return self:__set_component(entity_id, component_type, component)
end

---@param entity_id number
---@param type ComponentType
---@return boolean
function World:has_component(entity_id, type)
    assert(entity_id, "entity_id must be not null")
    local component_type = type.type or type
    return self:__has_component(entity_id, component_type)
end

--------------------------------------------

---@param components Component[]?
---@return Entity
function World:add_entity(components)
    local entity = self:__add_entity()
    if (components ~= nil) then
        for_each(components, function(component) entity:set_component(component) end)
    end
    return entity
end

---@param entity_id number
---@return Component[]
function World:components_of(entity_id)
    assert(entity_id, "entity_id must be not null")
    return self:__get_all_components(entity_id)
end

function World:destroy_entity(entity_id)
    assert(entity_id, "entity_id must be not null")
    debug("entity", entity_id .. " destroyed")
    return self:__destroy_entity(entity_id)
end

--------------------------------------------

---@param world World
---@param id number
---@return Entity
function Entity.of(world, id) return Entity.__of(world, id) end

---@param component Component?
function Entity:set_component(component)
    self.world:set_component(self.id, component)
end

---@param type Component
function Entity:has_component(type)
    self.world:__has_component(self.id, type)
end

---@param type Component
---@return Component?
function Entity:remove_component(type)
    return self.world:__remove_component(self.id, type)
end

---@param type Component
---@return Component?
function Entity:get_component(type)
    return self.world:__get_component(self.id, type)
end

function Entity:destroy()
    self.world:destroy_entity(self.id)
end

---@return Component[]
function Entity:get_all()
    return self.world:components_of(self.id)
end