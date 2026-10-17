local components = require("core.components")

---@class Migrations
---@field ids IdMigration[]

---@class IdMigration
---@field from Id
---@field to Id

local id_migration = {}

---@param context CompilationContext
---@param listeners_list EventListeners[]
---@param migrations IdMigration[]
function id_migration:append_events(context, listeners_list, migrations)
    local migration_sources = {} ---@type table<Id, Id>
    
    for _, migration in ipairs(migrations) do
        if migration_sources[migration.from] then
            
        else
            migration_sources[migration.from] = migration.to
        end
    end

    ---@type EventListeners
    local listeners = {
        entity_materialization = function (context)
            local item = context.entity:get_component(components.item)
            if not item then return end

            local migration_source = migration_sources[item.prefab_id]
            if not migration_source then return end
            

        end
    }
    table.insert(listeners_list, listeners)
end

return
