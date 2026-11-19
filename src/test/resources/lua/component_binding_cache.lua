return function(world, ...)
    local rows = {}
    world:iterate({ ... }, function(callback_world, entity, ...)
        assert(callback_world == world)
        rows[#rows + 1] = { entity = entity, components = { ... } }
    end)
    return rows
end
