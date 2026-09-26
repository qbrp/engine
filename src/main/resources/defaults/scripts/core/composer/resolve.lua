local drafts = require("core.composer.drafts")
local categories = drafts.categories

local resolver = {}

---@generic T
---@class Symbol
---@field id Id
---@field value T
---@field module Module
---@field file ModuleFile
---@field ordinal integer
---@field template Symbol<Template>?

---@class CategorizedSymbols
---@field item_documents table<Id, Symbol<ItemDocument>>
---@field item_prefabs table<Id, Symbol<ItemPrefabDraft>>
---@field templates table<Id, Symbol<Template>>
---@field components table<Id, Symbol<ComponentTypeSettings>>
---@field systems table<Id, Symbol<SystemDraft>>
---@field scripts table<Id, Symbol<Script>>
---@field operations table<Id, Symbol<OperationDraft>>

---@param module Module
---@param context CompilationContext
---@param category Category
---@param draft SymbolDraft
---@param id_str IdLiteral
---@return Id?
---@return string? error
function resolver.resolve_id_catching(context, module, category, draft, id_str)
    local id, err = module:resolve_id(id_str)
    if err then
        context.reports:report_error({
            message = err,
            phase = "validation",
            location = { source = draft.file.path, path = draft.path },
            target = {
                kind = category.kind,
                local_id = engine.id.fetch_local(draft.id)
            }
        })
    end
    return id, err
end

---@param module Module
---@param context CompilationContext
---@param allowed table<string, boolean>
---@param resolved_id Id
---@return boolean success
function resolver.validate_namespace(context, module, allowed, resolved_id)
    if not allowed[resolved_id.namespace] then
        context.reports:report_error({
            namespace = resolved_id.namespace,
            message = "Идентификатор " ..
            resolved_id.full ..
            " принадлежит неправильному пространству имён (для модуля доступны: " ..
            table.concat(module.namespaces, ", ") .. ")",
            phase = "validation"
        })
        return false
    end
    return true
end

---@param module Module
---@param context CompilationContext
---@param category Category
---@param draft SymbolDraft
---@param id_str IdLiteral
---@return Id?
---@return boolean success
function resolver.resolve_id_validation(context, module, category, draft, id_str)
    local resolved_id, err = resolver.resolve_id_catching(context, module, category, draft, id_str)
    if err then return nil, false end

    if not resolver.validate_namespace(context, module, module.allowed_namespaces, resolved_id) then return nil, false end

    return resolved_id, true
end

---@param id Id
---@param draft SymbolDraft
---@param module Module
---@param template Symbol<Template>?
---@param ordinal integer
---@return Symbol
local function symbol_kind(id, draft, module, template, ordinal)
    return {
        id = id,
        value = draft.value,
        module = module,
        file = draft.file,
        template = template,
        ordinal = ordinal
    }
end

---@param context CompilationContext
---@param drafts ModuleSymbolsDraft[]
---@param symbols CategorizedSymbols
---@return CategorizedSymbols
function resolver.resolve_symbols(context, drafts, symbols)
    local template_symbols_by_draft = {}

    for _, module_draft in ipairs(drafts) do
        local module = module_draft.module

        for _, draft in ipairs(module_draft.symbols.templates or {}) do
            local resolved_id, success =
                resolver.resolve_id_validation(context, module, categories.templates, draft, draft.id)

            if not success then goto next_template_draft end

            assert(resolved_id)

            local symbol = symbol_kind(resolved_id, draft, module, nil, assert(draft.ordinal))
            symbols.templates[resolved_id] = symbol
            template_symbols_by_draft[draft] = symbol

            ::next_template_draft::
        end
    end

    -- All templates must be registered before resolving their parents so
    -- inheritance can refer to templates declared later or in another module.
    for _, module_draft in ipairs(drafts) do
        local module = module_draft.module

        for _, draft in ipairs(module_draft.symbols.templates or {}) do
            local symbol = template_symbols_by_draft[draft]
            if not symbol then goto next_template_parent end

            local template_id_str = draft.value.template
            if not template_id_str then goto next_template_parent end

            local template_id, err = resolver.resolve_id_catching(
                context,
                module,
                categories.templates,
                draft,
                template_id_str
            )
            local template = not err and symbols.templates[template_id]

            if not err and not template then
                context.reports:report_error({
                    message = "Шаблон " .. template_id_str .. " не найден",
                    phase = "validation",
                    location = {
                        source = draft.file.path,
                        path = draft.path
                    },
                    target = {
                        kind = categories.templates.kind,
                        local_id = engine.id.fetch_local(draft.id)
                    }
                })
            end

            symbol.template = template

            ::next_template_parent::
        end
    end

    for _, module_draft in ipairs(drafts) do
        local module = module_draft.module

        for category_id, symbol_drafts in pairs(module_draft.symbols) do
            if category_id == "templates" then goto next_category end

            local category =
                assert(categories[category_id], "category with id " .. category_id .. " not found")

            local target = symbols[category_id]
            if not target then
                target = {}
                symbols[category_id] = target
            end

            for _, draft in ipairs(symbol_drafts) do
                local resolved_id, success =
                    resolver.resolve_id_validation(context, module, category, draft, draft.id)

                if not success then goto next_draft end

                assert(resolved_id)

                draft.value.id = resolved_id

                local ordinal = assert(draft.ordinal)
                local template_id_entry = draft.value.template
                local template_id_str =
                    template_id_entry or ("/default/" .. category.template_name)

                local is_default = not template_id_entry

                local template_id, err = resolver.resolve_id_catching(
                    context,
                    module,
                    categories.templates,
                    draft,
                    template_id_str
                )

                local template = not err and symbols.templates[template_id]

                if not err and not template and not is_default then
                    context.reports:report_error({
                        message = "Шаблон " .. template_id_str .. " не найден",
                        phase = "validation",
                        location = {
                            source = draft.file.path,
                            path = draft.path
                        },
                        target = {
                            kind = category.kind,
                            local_id = engine.id.fetch_local(draft.id)
                        }
                    })
                end

                target[resolved_id] =
                    symbol_kind(resolved_id, draft, module, template, ordinal)

                ::next_draft::
            end

            ::next_category::
        end
    end

    ---@cast symbols CategorizedSymbols
    return symbols
end

return resolver
