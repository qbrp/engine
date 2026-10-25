local components = require("core.components")

---@class Migrations
---@field ids IdMigration[]

---@class IdMigration
---@field from Id
---@field to Id

local id_migration = {}

---@param context CompilationContext
---@param migrations IdMigration[]
---@return EventListeners
function id_migration.events(context, migrations)
    local from_to = {} ---@type table<Id, Id>

    for _, migration in ipairs(migrations) do
        if from_to[migration.from] then
            context.reports:report_error {
                phase = "validation",
                message = "Миграция идентификатора " .. migration.from.full .. " ссылается на уже ссылается на цель " .. from_to[migration.from].full
            }
        else
            from_to[migration.from] = migration.to
        end
    end

    ---@type EventListeners
    return {
        entity_materialization = function(context)
            local entity = context.entity
            while true do
                local item = entity:get_component(components.item)
                if not item then return end

                local to_id = from_to[item.prefab_id]
                if not to_id then return end

                engine.logger.info(
                    "item {} migrated: {} -> {}",
                    item.uuid,
                    item.prefab_id.full,
                    to_id.full
                )

                entity:set_component(components.item, { uuid = item.uuid, prefab_id = to_id })

                local asset_variants = entity:get_component(components.asset_variants)
                if asset_variants then
                    local default = asset_variants.default
                    if default == item.prefab_id then
                        asset_variants.assets.default = to_id
                        entity:set_component(components.asset_variants, asset_variants)
                    end
                end
            end
        end
    }
end

return id_migration
