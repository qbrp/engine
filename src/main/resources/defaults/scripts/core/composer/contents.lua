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
local namespaces_util = require("core.composer.namespaces")
local components = require("core.composer.components")

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
    -- Compilation mutates declaration drafts, so every build gets a fresh require generation.
    declarations.begin_build(module_files)
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
    components.setup_definitions(symbols.components)
    items.lower_item_documents(context, symbols, categories)

    local namespaces = composer.group_namespaces(symbols)
    local inventory_tab_entries = {
        { prefab_id = "core/error/item" }
    }

    local sorted_namespaces = namespaces_util.sort(
        table.shallow_copy(namespaces),
        categories,
        symbols
    )
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
