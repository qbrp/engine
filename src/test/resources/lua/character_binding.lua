local access, ready = engine.component.get_binding("core/character/applied")
assert(ready and access ~= nil)
local character_type = { access = access }
local look_access, look_ready = engine.component.get_binding("core/character/applied_look")
assert(look_ready and look_access ~= nil)
local look_type = { access = look_access }

return {
    get = function(entity)
        return entity:require_component(character_type)
    end,
    iterate = function(world)
        local characters = {}
        world:iterate({ character_type }, function(_, _, character)
            characters[#characters + 1] = character
        end)
        return characters
    end,
    get_look = function(entity)
        return entity:require_component(look_type)
    end,
    iterate_looks = function(world)
        local looks = {}
        world:iterate({ look_type }, function(_, _, look)
            looks[#looks + 1] = look
        end)
        return looks
    end,
}
