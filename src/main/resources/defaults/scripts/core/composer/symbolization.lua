local drafts = require("core.composer.drafts")
local symbolization = {}

---@class ModuleSymbolsDraft
---@field module Module
---@field symbols SymbolsDraft

---@param module Module
---@param categories Categories
---@param categorized_declarations CategorizedDeclarations
---@return ModuleSymbolsDraft 
function symbolization.module_draft(module, categories, categorized_declarations)
    local categorized_symbols = drafts.categorized_symbols(categories) ---@type SymbolsDraft
    
    for category, declarations in pairs(categorized_declarations) do
        if category.keyed then
            goto next_category
        end
        
        for idx, declaration in ipairs(declarations) do
            local target = category.target

            if declaration.source_kind == "document" then
                target = category.document_target or target
            else
                target = category.script_target or target
            end

            assert(target)
            assert(declaration.id)

            table.insert(categorized_symbols[target],  {
                id = declaration.id,
                value = declaration.value,
                file = declaration.file,
                path = declaration.path,
                ordinal = idx
            })
        end

        ::next_category::
    end

    return {
        module = module,
        symbols = categorized_symbols
    }
end

return symbolization
