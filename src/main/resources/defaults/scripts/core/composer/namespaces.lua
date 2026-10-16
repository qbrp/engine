local namespaces = {}

---@param namespaces_list NamespaceDraft[]
---@param symbols CategorizedSymbols
---@return NamespaceDraft[]
function namespaces.sort(namespaces_list, symbols)
    table.sort(namespaces_list, function (a, b)
        return a.id < b.id
    end)
    namespaces_list = table.map(namespaces_list, function (namespace)
        local sorted_namespace = table.shallow_copy(namespace)
        for category_id, drafts in pairs(namespace) do
            if category_id == "id" then -- пропускаем поле id из NamespaceDraft
                goto continue    
            end
            local category_symbols = assert(symbols[category_id], "cannot find category " .. category_id .. " in symbols from NamespaceDraft")

            local sorted_drafts = table.shallow_copy(drafts)
            table.sort(sorted_drafts, function(a, b)
                local symbol_a = assert(category_symbols[a.id], "cannot find symbol " .. a.id.full .. " in category " .. category_id .. " to sort")
                local symbol_b = assert(category_symbols[b.id], "cannot find symbol " .. b.id.full .. " in category " .. category_id .. " to sort")

                if symbol_a.ordinal ~= symbol_b.ordinal then
                    return symbol_a.ordinal < symbol_b.ordinal
                end

                return a.id.full < b.id.full
            end)
            sorted_namespace[category_id] = sorted_drafts

            ::continue::
        end
        return sorted_namespace
    end)
    return namespaces_list
end

return namespaces
