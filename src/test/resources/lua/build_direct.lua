local namespace = "lua_test"
local item = engine.id.new(namespace, "item")
local component = engine.id.new(namespace, "state")
local system = engine.id.new(namespace, "tick")

return {
    namespaces = {{
        id = namespace,
        item_prefabs = {{
            id = item,
            display_name = "Lua item",
            max_count = 7,
            assets = { default = "lua_test/model" },
            on_load = function() end,
        }},
        scripts = {{
            id = engine.id.new(namespace, "answer"),
            execute = function(value) return value + 2 end,
        }},
        components = {{
            id = component,
            persistent = true,
            replicating = true,
        }},
        systems = {{
            id = system,
            side = "both",
            query = { component },
            tick = function() end,
        }},
    }},
    listeners = {
        world_tick = function() end,
    },
    phases = {
        base = {
            name = "Lua phase",
            steps = {{ type = "system", system = system }},
        },
    },
    inventory_tab = {
        entries = {{ prefab_id = item, tooltip = { "First", "Second" } }},
    },
}
