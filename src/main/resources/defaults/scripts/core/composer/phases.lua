local resolver = require("core.composer.resolve")
local phases = {}

---@param context CompilationContext
---@param categorized_symbols CategorizedSymbols
---@return SystemPhase
function phases.compose_root(context, categorized_symbols)
    local systems = categorized_symbols.systems
    local adjacency = {} ---@type table<Id, Id[]>
    local indegree = {} ---@type table<Id, integer>\
    local not_stated = {} ---@type table<Id, boolean>
    local total = 0

    for id in pairs(systems) do
        adjacency[id] = {}
        indegree[id] = 0
        total = total + 1
    end

    -- строим граф
    for id, system_symbol in pairs(systems) do
        local system = system_symbol.value ---@type SystemDraft

        if system.after then
            local base_system, err = resolver.resolve_id_catching(context,
                system_symbol.module,
                systems,
                system_symbol,
                system.after
            )
            if err then goto next_system end
            assert(base_system)
            if not systems[base_system] then
                context.reports:report_error(
                    {
                        phase = "linking",
                        message = "Ссылаемая системой " .. system.id .. " система " .. base_system.full .. " не найдена"
                    }
                )
                goto next_system
            end

            table.insert(adjacency[base_system], id)
            not_stated[id] = nil
            not_stated[base_system] = nil
            indegree[id] = indegree[id] + 1
        elseif not indegree[id] and not adjacency[id] then
            table.insert(not_stated, id)
        end
        ::next_system::
    end

    ---@param name string
    ---@param systems Id[]
    ---@return PhaseStep.Phase
    local function phase_step(name, systems)
        return {
            type = "phase",
            phase = {
                name = name,
                steps = table.map(systems, function (id)
                    return { type = "system", system = id }
                end)
            }
        }
    end

    local phases = { phase_step("Not stated", not_stated) } ---@type PhaseStep.Phase[]
    local ready = {} ---@type Id[]
    for system_id, links in pairs(indegree) do
        if links == 0 then
            table.insert(ready, system_id)
        end
    end
    local idx = 0
    local processed = 0
    while #ready ~= 0 do
        processed = processed + #ready
        table.insert(phases, phase_step("Node " .. idx, ready))
        local next = {}
        for _, system_id in ipairs(ready) do
            local links = adjacency[system_id]
            for _, dependent_system_id in ipairs(links) do
                local degree = indegree[dependent_system_id] - 1
                indegree[dependent_system_id] = degree

                if degree == 0 then
                    table.insert(next, dependent_system_id)
                end
            end
        end
        ready = next
        idx = idx + 1
    end

    if processed ~= total then
        context.reports:report_error(
            { phase = "linking", message = "Обнаружен цикл зависимостей систем" }
        )
        phases = {}
    end

    return {
        name = "Root",
        steps = phases
    }
end

return phases
