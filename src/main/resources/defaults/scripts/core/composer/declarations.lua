local yaml = require("core.util.yaml")

local declarations = {}
local script_declarations = {}

---@alias ScriptDeclarationKind "single" | "multiple" | "various"

---@class ScriptDeclarations
---@field kind ScriptDeclarationKind
---@field value table

---@param kind ScriptDeclarationKind
---@param value table
---@return ScriptDeclarations
local function explicit(kind, value)
    assert(type(value) == "table", "declarations." .. kind .. " expects table")
    return setmetatable({ kind = kind, value = value }, script_declarations)
end

---@param value table
---@return ScriptDeclarations
function declarations.single(value)
    return explicit("single", value)
end

---@param values table[]
---@return ScriptDeclarations
function declarations.multiple(values)
    return explicit("multiple", values)
end

---@param values table<CategoryId, table[]>
---@return ScriptDeclarations
function declarations.various(values)
    return explicit("various", values)
end

---@alias TemplateIdLiteral string
---@alias DeclarationSourceKind "document" | "script"

---@class DeclarationsDocument
---@field namespace string?
---@field use_templates table<CategoryId, TemplateIdLiteral>?
---@field [string] any

---@class DeclarationsScript
---@field result ScriptDeclarations
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

---@param files ModuleFiles
---@return DeclarationSources
function declarations.parse(files)
    ---@type DeclarationSources
    local sources = { documents = {}, scripts = {} }

    for _, script in ipairs(files.lua) do
        local result = dofile(script.file.path)

        -- Helper scripts may return ordinary values without declaring content.
        if type(result) == "table" and getmetatable(result) == script_declarations then
            sources.scripts[script.file] = {
                result = result,
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

---@param path string
---@param field string
---@return string
local function child_path(path, field)
    return path == "" and field or path .. "." .. field
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

---@param category_id CategoryId
---@return Category
function collector:get_category(category_id)
    return assert(
        self.categories[category_id],
        "unknown declaration category " .. tostring(category_id)
    )
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

---@param category_id CategoryId
---@param value table
---@param path string
function collector:script_value(category_id, value, path)
    local category = self:get_category(category_id)

    assert(type(value) == "table", self.file.path .. ":" .. path .. " must be table")

    if category.keyed then
        self:insert(category, value, nil, path)
        return
    end

    assert(
        type(value.id) == "string",
        self.file.path .. ":" .. child_path(path, "id") .. " must be string"
    )

    self:insert(category, value, value.id, path)
end

---@param category_id CategoryId
---@param value table
---@param path string
function collector:script_multiple(category_id, value, path)
    self:get_category(category_id)

    local location = self.file.path .. ":" .. path
    assert(type(value) == "table", location .. " must be array of declarations")

    local count = 0
    for index in pairs(value) do
        assert(
            type(index) == "number" and index >= 1 and index % 1 == 0,
            location .. " must be array of declarations"
        )
        count = count + 1
    end

    for index = 1, count do
        local declaration_path = path .. "[" .. index .. "]"
        self:script_value(category_id, value[index], declaration_path)
    end
end

---@param script DeclarationsScript
function collector:script(script)
    local result = script.result
    if result.kind == "various" then
        for category_id, values in pairs(result.value) do
            self:script_multiple(category_id, values, tostring(category_id))
        end
        return
    end

    local category_id = assert(
        script.scanned_category_id,
        self.file.path .. ": declarations." .. result.kind
            .. " requires a category file or folder; use declarations.various for explicit categories"
    )
    if result.kind == "single" then
        self:script_value(category_id, result.value, "")
    else
        self:script_multiple(category_id, result.value, "")
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
        collector.new(result, categories, file, "script"):script(script)
    end

    for file, document in pairs(sources.documents) do
        collector.new(result, categories, file, "document"):document(document)
    end

    return result
end

return declarations
