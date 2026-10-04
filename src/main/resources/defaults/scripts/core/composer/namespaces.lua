local namespaces = {}

---@param namespaces_list NamespaceDraft[]
---@param categories table<string, Category>
---@param symbols CategorizedSymbols
---@return NamespaceDraft[]
function namespaces.sort(namespaces_list, categories, symbols)
    table.sort(namespaces_list, function (a, b)
        return a.id < b.id
    end)
    namespaces_list = table.map(namespaces_list, function (namespace)
        local sorted_namespace = table.shallow_copy(namespace)
        for category_id, category in pairs(categories) do
            local drafts = namespace[category_id] or {}
            local sorted_drafts = table.shallow_copy(drafts)
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
            sorted_namespace[category_id] = sorted_drafts
        end
        return sorted_namespace
    end)
    return namespaces_list
end

return namespaces
