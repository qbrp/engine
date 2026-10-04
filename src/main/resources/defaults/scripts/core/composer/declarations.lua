local yaml = require("core.util.yaml")

local declarations = {}
local script_declarations = {}
local DECLARATION_KEY = "__declaration"

------
--- Scripts declaration drafts
------

---@alias ScriptDeclarationKind "single" | "multiple" | "various"

---@class ScriptDeclarationsDraftInput
---@field __declaration ScriptDeclarationMarker

---@class ScriptDeclarationMarker
---@field kind ScriptDeclarationKind

---@class ScriptDeclarationsDraft
---@field kind ScriptDeclarationKind
---@field value table

---@generic T : table
---@param kind ScriptDeclarationKind
---@param value T
---@return T
local function explicit(kind, value)
    assert(type(value) == "table", "declarations." .. kind .. " expects table")
    local declaration = setmetatable({ kind = kind }, script_declarations)
    value[DECLARATION_KEY] = declaration
    return value
end

---@generic T : table
---@param value T
---@return T
function declarations.single(value)
    return explicit("single", value)
end

---@generic T : table
---@param values T
---@return T
function declarations.multiple(values)
    return explicit("multiple", values)
end

---@generic T : table
---@param values T
---@return T
function declarations.various(values)
    return explicit("various", values)
end

------
--- Scripts declarations
------

---@alias RawScriptDeclaration table
---@alias OrderedDeclarations RawScriptDeclaration[]

---@class ScriptsDeclarations
---@field categories table<CategoryId, OrderedDeclarations>

local draft_mapping = {}

---@param values table<any, RawScriptDeclaration>
---@param category Category
---@param path string
---@return OrderedDeclarations
function draft_mapping.multiple(values, category, path)
    assert(type(values) == "table", path .. " must be table")

    local declaration_values = {}
    for key, value in pairs(values) do
        if key ~= DECLARATION_KEY then
            declaration_values[key] = value
        end
    end

    local ordered = table.is_array(declaration_values)
        and declaration_values
        or table.values(declaration_values)
    for index, value in ipairs(ordered) do
        local declaration_path = path .. "[" .. index .. "]"
        assert(type(value) == "table", declaration_path .. " must be table")

        if not category.keyed then
            assert(type(value.id) == "string", declaration_path .. ".id must be string")
        end
    end

    return ordered
end

---@param category_id CategoryId?
---@param path string
---@param kind ScriptDeclarationKind
local function assert_category_id(category_id, path, kind)
    assert(
        category_id,
        path .. ": declarations." .. kind
            .. " requires a category file or folder; use declarations.various for explicit categories"
    )
end

---@param draft ScriptDeclarationsDraft
---@param category CategoryId?
---@param path string
---@param categories table<CategoryId, Category>
---@return ScriptsDeclarations
function draft_mapping.convert(draft, category, path, categories)
    assert(type(draft.value) == "table", path .. ": declarations." .. tostring(draft.kind) .. " expects table")

    local values_by_category
    if draft.kind == "single" or draft.kind == "multiple" then
        assert_category_id(category, path, draft.kind)
        values_by_category = {
            [category] = draft.kind == "single" and { draft.value } or draft.value
        }
    elseif draft.kind == "various" then
        values_by_category = draft.value
    else
        error(path .. ": unknown declarations kind: " .. tostring(draft.kind))
    end

    ---@type ScriptsDeclarations
    local result = { categories = {} }
    for category_id, values in pairs(values_by_category) do
        if category_id == DECLARATION_KEY then goto continue end

        local category_path = path .. ":" .. tostring(category_id)
        assert(type(category_id) == "string", category_path .. " category id must be string")
        local declaration_category = assert(
            categories[category_id],
            category_path .. ": unknown declaration category " .. category_id
        )
        result.categories[category_id] = draft_mapping.multiple(values, declaration_category, category_path)

        ::continue::
    end

    return result
end

------

---@alias TemplateIdLiteral string
---@alias DeclarationSourceKind "document" | "script"

---@class DeclarationsDocument
---@field namespace string?
---@field use_templates table<CategoryId, TemplateIdLiteral>?
---@field [string] any

---@class DeclarationsScript
---@field result ScriptDeclarationsDraft
---@field scanned_category_id CategoryId?

---@class DeclarationSources
---@field documents table<ModuleFile, DeclarationsDocument>
---@field scripts table<ModuleFile, DeclarationsScript>

---@alias CategorizedDeclarations table<Category, Declaration[]>

---@generic T
---@class Declaration<T>
---@field value T
---@field id string?
---@field file ModuleFile
---@field path string
---@field source_kind DeclarationSourceKind

local value_cache = {}

---@param files_by_module table<ModuleId, ModuleFiles>
function declarations.begin_build(files_by_module)
    value_cache = {}

    -- Declaration scripts may require each other. Clear every entry before loading any of
    -- them so the scanner and regular require calls share one fresh object graph.
    for _, files in pairs(files_by_module) do
        for _, script in ipairs(files.lua) do
            if script.module_name then
                package.loaded[script.module_name] = nil
            end
        end
    end
end

---@param script ModuleScript
---@return any
local function require_caching(script)
    local cached = value_cache[script.file.path]
    if cached then
        return cached.value
    end

    local value = script.module_name and require(script.module_name) or dofile(script.file.path)
    value_cache[script.file.path] = { value = value }
    return value
end

---@param files ModuleFiles
---@return DeclarationSources
function declarations.parse(files)
    ---@type DeclarationSources
    local sources = { documents = {}, scripts = {} }

    for _, script in ipairs(files.lua) do
        local result = require_caching(script)
        local declaration = type(result) == "table" and result[DECLARATION_KEY] or nil

        -- Helper scripts may return ordinary values without declaring content.
        if declaration and type(declaration) == "table" and getmetatable(declaration) == script_declarations then
            sources.scripts[script.file] = {
                result = {
                    kind = declaration.kind,
                    value = result
                },
                scanned_category_id = script.category
            }
        end
    end

    for _, file in ipairs(files.yaml) do
        local document = yaml.eval(file:read())
        assert(type(document) == "table", file.path .. " must contain declarations document")
        sources.documents[file] = document
    end

    return sources
end


---@param namespace string?
---@param id string
---@return string
local function document_id(namespace, id)
    return namespace and namespace .. "/" .. id or id
end

---@class DeclarationCollector
---@field result CategorizedDeclarations
---@field categories table<CategoryId, Category>
---@field file ModuleFile
---@field source_kind DeclarationSourceKind
local collector = {}
collector.__index = collector

---@param result CategorizedDeclarations
---@param categories table<CategoryId, Category>
---@param file ModuleFile
---@param source_kind DeclarationSourceKind
---@return DeclarationCollector
function collector.new(result, categories, file, source_kind)
    return setmetatable({
        result = result,
        categories = categories,
        file = file,
        source_kind = source_kind
    }, collector)
end

---@param category Category
---@param value any
---@param id string?
---@param path string
function collector:insert(category, value, id, path)
    local category_declarations = self.result[category]
    if not category_declarations then
        category_declarations = {}
        self.result[category] = category_declarations
    end

    category_declarations[#category_declarations + 1] = {
        value = value,
        id = id,
        file = self.file,
        path = path,
        source_kind = self.source_kind
    }
end

---@param script ScriptsDeclarations
function collector:scripts(script)
    for category_id, values in pairs(script.categories) do
        local category = self.categories[category_id]
        for index, value in ipairs(values) do
            local id
            if not category.keyed then
                id = value.id
            end
            self:insert(category, value, id, category_id .. "[" .. index .. "]")
        end
    end
end

---@param document DeclarationsDocument
function collector:document(document)
    for category_id, category in pairs(self.categories) do
        local values = document[category_id]
        if values == nil then goto continue end

        assert(
            type(values) == "table",
            self.file.path .. ":" .. category_id .. " must be table"
        )

        if next(values) == nil then goto continue end

        if category.keyed then
            self:insert(category, values, nil, category_id)
            goto continue
        end

        local use_template = document.use_templates and document.use_templates[category_id]

        for id, value in yaml.mapping_pairs(values) do
            local path = category_id .. "." .. tostring(id)

            assert(
                type(id) == "string",
                self.file.path .. ":" .. category_id .. " declaration id must be string"
            )
            assert(
                type(value) == "table",
                self.file.path .. ":" .. path .. " must be table"
            )

            if use_template and value.template == nil then
                value.template = use_template
            end

            self:insert(
                category,
                value,
                document_id(document.namespace, id),
                path
            )
        end

        ::continue::
    end
end


---@param sources DeclarationSources
---@param categories table<CategoryId, Category>
---@return CategorizedDeclarations
function declarations.normalize(sources, categories)
    ---@type CategorizedDeclarations
    local result = {}

    for file, script in pairs(sources.scripts) do
        local mapped = draft_mapping.convert(script.result, script.scanned_category_id, file.path, categories)
        collector.new(result, categories, file, "script"):scripts(mapped)
    end

    for file, document in pairs(sources.documents) do
        collector.new(result, categories, file, "document"):document(document)
    end

    return result
end

return declarations
