local drafts = require("core.composer.drafts")
local categories = drafts.categories
local listeners = require("core.composer.listeners")
local declarations = require("core.composer.declarations")
local files = require("core.composer.files")
local symbolization = require("core.composer.symbolization")
local resolving = require("core.composer.resolve")
local templates = require("core.composer.templates")
local placeholders = require("core.composer.placeholders")
local items = require("core.composer.items")
local phases = require("core.composer.phases")

---@class ComposerModule
---@field module Module
---@field files ModuleFiles

local composer = {}

---@param symbols CategorizedSymbols
---@return NamespaceDraft[]
function composer.group_namespaces(symbols)
    ---@type NamespaceDraft[]
    local result = {}
    ---@type table<string, NamespaceDraft>
    local namespaces = {}

    local content_fields = {
        item_prefabs = "items",
        scripts = "scripts",
        operations = "operations",
        components = "components",
        systems = "systems",
        sound_events = "sound_events",
        progression_animations = "progression_animations"
    }

    for category, field in pairs(content_fields) do
        for _, symbol in pairs(symbols[category] or {}) do
            local namespace_id = symbol.id.namespace
            local namespace = namespaces[namespace_id]
            if not namespace then
                namespace = drafts.namespace(namespace_id)
                namespaces[namespace_id] = namespace
                table.insert(result, namespace)
            end

            table.insert(namespace[field], symbol.value)
        end
    end

    return result
end

---@param context CompilationContext
---@return Build
function composer.build(context)
    local modules = engine.modules.enabled
    local modules_names = table.map(
        modules,
        function(module)
            return module.namespace
        end
    )

    engine.logger.info(
        "composing {} modules: {}",
        tostring(#modules_names),
        table.concat(modules_names, ", ")
    )

    local module_files = files.scan(modules, categories)
    local symbols = drafts.categorized_symbols(categories)
    local module_drafts = {} ---@type ModuleSymbolsDraft[]
    local listeners_list = {}

    for _, module in ipairs(modules) do
        local files = module_files[module.namespace]
        local categorized_declarations = declarations.normalize(declarations.parse(files), categories)
        local symbols_draft = symbolization.module_draft(module, categories, categorized_declarations)
        table.insert(module_drafts, symbols_draft)
        listeners.collect(listeners_list, categorized_declarations)
    end

    resolving.resolve_symbols(context, module_drafts, symbols)
    templates.extend(symbols)
    placeholders.extend(symbols)
    items.lower_item_documents(symbols, categories)

    local namespaces = composer.group_namespaces(symbols)
    local inventory_tab_entries = {
        { prefab_id = "core/error/item" }
    }

    local sorted_namespaces = table.shallow_copy(namespaces)
    table.sort(sorted_namespaces, function (a, b)
        return a.id < b.id
    end)
    sorted_namespaces = table.map(sorted_namespaces, function (namespace)
        local sorted_categories = {}
        local categorized_contents = table.shallow_copy(namespace)
        categorized_contents.id = nil
        for category_id, drafts in pairs(categorized_contents) do
            local sorted_drafts = table.shallow_copy(drafts)
            local category = categories[category_id]
            local symbol_category_id = category.draft_id or category.id
            local symbol_category = symbols[symbol_category_id]
            table.sort(sorted_drafts, function(a, b)
                local symbol_a = assert(symbol_category[a.id])
                local symbol_b = assert(symbol_category[b.id])

                if symbol_a.ordinal ~= symbol_b.ordinal then
                    return symbol_a.ordinal < symbol_b.ordinal
                end

                return a.id.full < b.id.full
            end)
            sorted_categories[category_id] = sorted_drafts
        end
        return sorted_categories
    end)
    for _, namespace in ipairs(sorted_namespaces) do
        for _, item_prefab in ipairs(namespace.items) do
            table.insert(inventory_tab_entries, {
                prefab_id = item_prefab.id
            })
        end
    end

    return {
        namespaces = namespaces,
        listeners = listeners.merge(listeners_list),
        root_phase = phases.compose_root(context, symbols),
        inventory_tab = {
            entries = inventory_tab_entries
        }
    }
end

return composer
