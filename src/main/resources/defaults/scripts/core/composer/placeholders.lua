local placeholders = {}

---@param document table
---@param id Id
local function substitute_placeholders(document, id)
    for key, value in pairs(document) do
        if type(value) == "string" then
            local new_value = value
            new_value = string.gsub(new_value, "{id}", id.full)
            new_value = string.gsub(new_value, "{local_id}", id.loc)
            new_value = string.gsub(new_value, "{namespace}", id.namespace)
            document[key] = new_value
        elseif type(value) == "table" then
            substitute_placeholders(value, id)
        end
    end
end

---@param categorized_symbols CategorizedSymbols
function placeholders.extend(categorized_symbols)
    for category, symbols in pairs(categorized_symbols) do
        if category == "templates" then goto next_category end
        for id, symbol in pairs(symbols) do
            substitute_placeholders(symbol.value, symbol.id)
        end
        ::next_category::
    end
end

return placeholders
