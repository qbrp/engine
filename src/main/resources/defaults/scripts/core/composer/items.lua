local items = {}

---@alias ItemDocument table

---@class ItemPrefabAddon
---@field handle fun(module: Module, document: ItemDocument, components: table)

---@param module Module
---@param reference IdReference
---@return Id
local function resolve_id(module, reference)
    local id, err = module:resolve_id(reference)
    assert(id, err)
    return id
end

---@param mode string
---@return GunFireMode
local function lower_fire_mode(mode)
    if mode == "SELECTOR" then
        return "SAFETY"
    end
    return mode
end

---@param modes string[]?
---@return GunFireMode[]
local function lower_fire_modes(modes)
    if not modes or #modes == 0 then
        return { "SAFETY", "SINGLE", "AUTO" }
    end

    local result = {}
    for _, mode in ipairs(modes) do
        table.insert(result, lower_fire_mode(mode))
    end
    return result
end

---@param config table?
---@param component GunComponentDraft?
---@return GunComponentDraft?
local function lower_gun_component(config, component)
    if not component and config then
        component = {
            smoke = table.deep_copy(config.smoke),
            rate = config.rate or 15,
            modes = table.deep_copy(config.modes)
        }
    end

    if component then
        component.modes = lower_fire_modes(component.modes)
    end
    return component
end

---@param config table?
---@param component GunFireStateComponentDraft?
---@return GunFireStateComponentDraft?
local function lower_gun_fire_state_component(config, component)
    if not component and config then
        component = {
            cooldown = 0,
            mode = "SAFETY",
            clicked = false,
            trigger_pressed = false,
            trigger_sound_played = false,
            fired = false
        }
    end

    if component then
        component.mode = lower_fire_mode(component.mode)
    end
    return component
end

---@param module Module
---@param config table?
---@param component GunMagazinesComponentDraft?
---@return GunMagazinesComponentDraft?
local function lower_gun_magazines_component(module, config, component)
    if not component and config and config.magazines then
        component = { supports = config.magazines }
    end

    if component then
        component.supports = resolve_id(module, component.supports)
    end
    return component
end

---@param config table?
---@param component GunDisplayComponentDraft?
---@return GunDisplayComponentDraft?
local function lower_gun_display_component(config, component)
    if not component and config and config.display then
        local display = config.display
        component = {
            ammunition = display.ammunition,
            magazine = display.magazine,
            selector_status = display.selector_status == nil or display.selector_status
        }
    end
    return component
end

---@param module Module
---@param config table?
---@param component BarrelComponentDraft?
---@return BarrelComponentDraft?
local function lower_barrel_component(module, config, component)
    if not component and config then
        component = {
            bullets = config.initial or 0,
            max_bullets = config.bullets,
            ammunition = config.ammunition
        }
    end

    if component and component.ammunition then
        component.ammunition = resolve_id(module, component.ammunition)
    end
    return component
end

---@param module Module
---@param config table?
---@param component MagazineComponentDraft?
---@return MagazineComponentDraft?
local function lower_magazine_component(module, config, component)
    if not component and config then
        component = {
            capacity = config.capacity,
            bullets = config.initial or 0,
            ammunition = config.ammunition
        }
    end

    if component and component.ammunition then
        component.ammunition = resolve_id(module, component.ammunition)
    end
    return component
end

---@param module Module
---@param item_document ItemDocument
---@return ItemBuiltInComponents
local function lower_built_in_components(module, item_document)
    local built_in_components = table.deep_copy(item_document.built_in_components or {})
    local gun_config = item_document.gun

    built_in_components.gun = lower_gun_component(gun_config, built_in_components.gun)
    built_in_components.gun_fire_state =
        lower_gun_fire_state_component(gun_config, built_in_components.gun_fire_state)
    built_in_components.gun_magazines =
        lower_gun_magazines_component(module, gun_config, built_in_components.gun_magazines)
    built_in_components.gun_display =
        lower_gun_display_component(gun_config, built_in_components.gun_display)
    built_in_components.barrel =
        lower_barrel_component(module, item_document.barrel, built_in_components.barrel)
    built_in_components.magazine =
        lower_magazine_component(module, item_document.magazine, built_in_components.magazine)

    return built_in_components
end

---@param context CompilationContext
---@param symbols CategorizedSymbols
---@param categories table<CategoryId, Category>
function items.lower_item_documents(context, symbols, categories)
    ---@type table<Id, Symbol<ItemDocument>>
    local item_documents = assert(symbols[categories.item_documents.id], "item_documents category not found")
    for _, item_document_symbol in pairs(item_documents) do
        local module = item_document_symbol.module
        local item_document = item_document_symbol.value
        local id = item_document_symbol.id
        local ordinal = item_document_symbol.ordinal

        local assets = item_document.assets
        if not assets then
            assets = {}
            item_document.assets = assets
        end

        if not assets.default then
            assets.default = item_document.model or item_document.asset or item_document.default_asset
        end

        for key, value in pairs(assets) do
            assets[key] = module:resolve_id(value)
        end

        local sound_events = item_document.sounds or {}
        local sound_events_resolved = {}
        for key, sound_event_id in pairs(sound_events) do
            local resolved_id, error = module:resolve_id(sound_event_id)
            if not error then
               sound_events_resolved[key] = resolved_id 
            else
                context.reports:report_error {
                    message = "Invalid sound event id in `sounds." .. tostring(key) .. "`: " .. error,
                    phase = "validation",
                    namespace = id.namespace,
                    location = {
                        source = item_document_symbol.file.path,
                        path = item_document_symbol.path .. ".sounds." .. tostring(key)
                    },
                    target = {
                        kind = categories.items.kind,
                        local_id = engine.id.fetch_local(id)
                    }
                }
            end
        end
        
        local components = {}

        ---@type table<Id, Symbol<ItemPrefabAddon>>
        local addons = assert(symbols[categories.item_prefab_addons.id], "item_prefab_addons category not found")
        for _, addon_symbol in pairs(addons) do
            local addon = addon_symbol.value ---@type ItemPrefabAddon
            addon.handle(module, item_document, components)
        end

        local item_prefab = {
            id = id,
            display_name = item_document.display_name or id,
            assets = item_document.assets,
            sound_events = sound_events_resolved,
            max_count = item_document.max_count or 1,
            built_in_components = lower_built_in_components(module, item_document),
            on_load = function(world, entity)
                for _, component in ipairs(components) do
                    entity:set_component(
                        component.type,
                        component.value
                    )
                end
            end
        }

        symbols[categories.item_prefabs.id][id] = {
            id = id,
            value = item_prefab,
            module = module,
            file = item_document_symbol.file,
            ordinal = ordinal
        }
    end
end

return items
