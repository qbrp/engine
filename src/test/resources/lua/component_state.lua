local function component_holder(id)
    local access, ready = engine.component.get_type(id)
    assert(ready and access ~= nil)
    return { access = access }
end

local health = component_holder("lua_test/health")
local label = component_holder("lua_test/label")

return {
    independent_states = function()
        local first = engine.component.state()
        local second = engine.component.state()
        assert(first ~= second)
        assert(first:get_component(health) == nil)
        assert(not first:has_component(health))
        assert(first:remove_component(health) == nil)

        local component = { value = 10 }
        assert(first:set_component(health, component) == component)
        assert(first:has_component(health))
        assert(not second:has_component(health))
        assert(second:get_component(health) == nil)
        return first, second, component
    end,

    set_and_read = function()
        local state = engine.component.state()
        local health_value = { value = 10 }
        local label_value = { text = "initial" }

        assert(state:set_component(health, health_value) == health_value)
        assert(state:set_component(label, label_value) == label_value)
        assert(state:get_component(health) == health_value)
        assert(state:require_component(label) == label_value)
        assert(state:has_component(health))
        assert(state:has_component(label))
        state:get_component(health).value = 25
        state:require_component(label).text = "updated"
        return state, health_value, label_value
    end,

    remove_and_reinsert = function()
        local state = engine.component.state()
        local original = { value = 10 }
        local other = { text = "other" }
        local replacement = { value = 20 }
        state:set_component(health, original)
        state:set_component(label, other)

        assert(state:remove_component(health) == original)
        assert(not state:has_component(health))
        assert(state:get_component(health) == nil)
        assert(state:remove_component(health) == nil)
        assert(state:require_component(label) == other)
        assert(state:set_component(health, replacement) == replacement)
        assert(state:require_component(health) == replacement)
        return state, replacement, other
    end,

    missing_required = function()
        local state = engine.component.state()
        local ok, failure = pcall(state.require_component, state, health)
        assert(not ok)
        assert(tostring(failure):find("lua_test/health", 1, true))
        assert(not state:has_component(health))
        return state
    end,

    duplicate_type = function()
        local state = engine.component.state()
        local original = { value = 10 }
        state:set_component(health, original)

        local ok, failure = pcall(state.set_component, state, health, { value = 20 })
        assert(not ok)
        assert(tostring(failure):find("already added", 1, true))
        assert(state:require_component(health) == original)
        return state, original
    end,

    native_binding = function()
        local state = engine.component.state()
        local access, ready = engine.component.get_binding("core/mass")
        assert(ready and access ~= nil)
        local holder = { access = access }

        for _, method in ipairs({
            "get_component", "require_component", "has_component", "set_component", "remove_component"
        }) do
            local ok, failure = pcall(state[method], state, holder, { kg = 1 })
            assert(not ok, method .. " должен отклонять обычный компонент")
            assert(tostring(failure):find("not a script component type access", 1, true))
        end
        return state
    end,

    read_from_kotlin = function(state)
        local value = state:require_component(health)
        assert(value.value == 42)
        value.value = 43
        assert(state:get_component(health) == value)
        assert(state:remove_component(health) == value)
        assert(not state:has_component(health))
        return value
    end,
}
