local drafts = {}

---@alias CategoryId string
---@alias Categories table<CategoryId, Category>

---@class Category
---@field id CategoryId
---@field type string
---@field kind ContentKind?
---@field keyed boolean?
---@field target? string
---@field script_target string?
---@field document_target string?
---@field draft_id string?
---@field template_name string?

---@type Categories
local categories = {
    items = {
        id = "items",
        type = "ItemPrefabDraft",
        kind = "item",
        draft_id = "item_prefabs",
        script_target = "item_prefabs",
        document_target = "item_documents",
        template_name = "items"
    },

    item_prefabs = {
        id = "item_prefabs",
        type = "ItemPrefabDraft",
        kind = "item",
        target = "item_prefabs",
        template_name = "items"
    },

    item_documents = {
        id = "item_documents",
        type = "ItemDocument",
        kind = "item",
        target = "item_documents",
        template_name = "items"
    },

    item_prefab_addons = {
        id = "item_prefab_addons",
        type = "ItemPrefabAddon",
        kind = "item_prefab_addon",
        target = "item_prefab_addons"
    },
    
    templates = {
        id = "templates",
        type = "Template",
        kind = "other",
        target = "templates",
        template_name = "templates"
    },

    components = {
        id = "components",
        type = "ComponentTypeSettings",
        kind = "component",
        target = "components",
        template_name = "components"
    },

    systems = {
        id = "systems",
        type = "SystemDraft",
        kind = "system",
        target = "systems",
        template_name = "systems"
    },

    scripts = {
        id = "scripts",
        type = "Script",
        kind = "script",
        target = "scripts"
    },

    operations = {
        id = "operations",
        type = "OperationDraft",
        kind = "operation",
        target = "operations",
        template_name = "operations"
    },

    sound_events = {
        id = "sound_events",
        type = "SoundEventDraft",
        kind = "sound_event",
        target = "sound_events",
        template_name = "sound_events"
    },

    progression_animations = {
        id = "progression_animations",
        type = "ProgressionAnimationDraft",
        kind = "other",
        target = "progression_animations",
        template_name = "progression_animations"
    },

    listeners = {
        id = "listeners",
        type = "EventListeners",
        keyed = true
    },
}

drafts.categories = categories

---@alias Identifiable { id: string }

---@generic T : Identifiable
---@class SymbolDraft
---@field value T
---@field id string
---@field file ModuleFile
---@field path string
---@field ordinal integer

---@alias SymbolsDraft table<string, SymbolDraft[]>

---@return ContentsDraft
function drafts.contents()
    return {
        items = {},
        components = {},
        systems = {},
        scripts = {},
        operations = {},
        sound_events = {},
        progression_animations = {}
    }
end

---@return NamespaceDraft
function drafts.namespace(id)
    local draft = drafts.contents()
    ---@cast draft NamespaceDraft
    draft.id = id
    return draft
end

---@param category Category
---@return string[]
local function category_targets(category)
    local targets = {}
    if category.target then table.insert(targets, category.target) end
    if category.document_target then table.insert(targets, category.document_target) end
    if category.script_target then table.insert(targets, category.script_target) end
    return targets
end

---@param categories Categories
---@return CategorizedSymbols
function drafts.categorized_symbols(categories)
    local categorized_symbols = {}
    for _, category in pairs(categories) do
        for _, target in ipairs(category_targets(category)) do
            categorized_symbols[target] = {}
        end
    end
    return categorized_symbols
end

return drafts
