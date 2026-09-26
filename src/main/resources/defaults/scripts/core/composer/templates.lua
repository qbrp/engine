local templates = {}

---@alias Template table

---@param target table
---@param source table
function merge(target, source)
    if table.is_array(source) then
        for _, value in ipairs(source) do
            table.insert(target, type(value) == "table" and table.deep_copy(value) or value)
        end
    else
        for key, value in pairs(source) do
            if type(value) == "table" then
                if target[key] == nil then
                    target[key] = {}
                end

                if type(target[key]) == "table" then
                    merge(target[key], value)
                end
            elseif target[key] == nil then
                target[key] = value
            end
        end
    end
end

---@param template Symbol<Template>
---@param templates table<Id, Symbol<Template>>
---@param processed table<Id, boolean>
---@param processing Id[]
function expand_template(template, templates, processed, processing)
    if processed[template.id] then
        return
    end

    local cycle_start
    for i, id in ipairs(processing) do
        if id == template.id then
            cycle_start = i
            break
        end
    end

    if cycle_start then
        local cycle = {}

        for i = cycle_start, #processing do
            cycle[#cycle + 1] = processing[i].full
        end

        cycle[#cycle + 1] = template.id.full

        error("Обнаружен цикл шаблонов: " .. table.concat(cycle, " -> "))
    end

    processing[#processing + 1] = template.id

    local parent_template = template.template
    if parent_template then
        expand_template(parent_template, templates, processed, processing)
        merge(template.value, parent_template.value)
    end

    processing[#processing] = nil
    processed[template.id] = true
end

---@param categorized_symbols CategorizedSymbols
function templates.extend(categorized_symbols)
    local templates = categorized_symbols.templates
    local processed = {}
    local processing = {}
    for _, template in pairs(templates) do
        expand_template(template, templates, processed, processing)
    end

    for category, symbols in pairs(categorized_symbols) do
        if category == "templates" then goto next_category end
        for id, symbol in pairs(symbols) do
            ---@cast symbol Symbol
            local template = symbol.template
            ---print("extending symbol " .. id.full .. " with template " .. (template and template.id.full or "nil"))
            if template then
                merge(symbol.value, template.value)
            end
        end
        ::next_category::
    end
end

return templates
