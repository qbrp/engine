---@class ModuleScript
---@field file ModuleFile
---@field category string?

---@class ModuleFiles
---@field yaml ModuleFile[]
---@field lua ModuleScript[]

local files = {}

---@param categories Categories
---@param name string
---@return string?
local function category_from_filename(categories, name)
    local name = name:match("^(.*)%.lua$")

    if name and categories[name] then
        return name
    end
end

---@param modules Module[]
---@param categories Categories
---@return table<ModuleId, ModuleFiles>
function files.scan(modules, categories)
    ---@type table<ModuleId, ModuleFiles>
    local result = {}

    ---@param entries table<string, ModuleEntry>
    ---@param output ModuleFiles
    ---@param category string?
    local function scan_entries(entries, output, category)
        for _, entry in pairs(entries) do
            if entry.kind == "folder" then
                local child_category = category

                if not child_category and categories[entry.name] then
                    child_category = entry.name
                end

                scan_entries(entry:files(), output, child_category)

            elseif entry.kind == "file" then
                if entry.name == "module.yaml" then
                    goto continue
                end

                if string.ends_with(entry.name, ".lua") then
                    table.insert(output.lua, {
                        file = entry,
                        category = category or category_from_filename(categories, entry.name)
                    })

                elseif string.ends_with(entry.name, ".yaml") then
                    table.insert(output.yaml, entry)
                end
            end

            ::continue::
        end
    end

    for _, module in ipairs(modules) do
        local files = {
            yaml = {},
            lua = {}
        }

        scan_entries(module.dir:files(), files)

        result[module.namespace] = files
    end

    return result
end

return files