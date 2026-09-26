local items = {}

---@alias ItemDocument table

---@class ItemPrefabAddon
---@field handle fun(module: Module, document: ItemDocument, components: table)

---@param symbols CategorizedSymbols
---@param categories table<CategoryId, Category>
function items.lower_item_documents(symbols, categories)
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
            max_count = item_document.max_count or 1,
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
