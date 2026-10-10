local resolver = require("core.composer.resolve")
local phases = {}

---@class SystemRelation
---@field system Symbol<SystemDraft>
---@field preceding Symbol<SystemDraft>

---@param context CompilationContext
---@param systems table<Id, Symbol<SystemDraft>>
---@return SystemRelation[]
local function system_relations(context, systems)
    local all_relations = {}

    for system_id, system_symbol in pairs(systems) do
        local relation = { system = system_symbol }
        local system = system_symbol.value ---@type SystemDraft
        local after = system["after"]

        if after then
            local after_id, err = resolver.resolve_id_catching(
                context,
                system_symbol.module,
                systems,
                system_symbol,
                after
            )

            if err then goto next_system end
            assert(after_id)

            local after_system = systems[after_id]

            if not after_system then
                context.reports:report_error(
                    {
                        phase = "linking",
                        message = "Ссылаемая системой " .. after_id.full ..
                            " система " .. system_id.full .. " не найдена"
                    }
                )
                goto next_system
            end

            relation.preceding = after_system
        end

        table.insert(all_relations, relation)

        ::next_system::
    end

    return all_relations
end

---@param name string
---@param systems Id[]
---@return SystemPhase
local function systems_phase(name, systems)
    return {
        name = name,
        steps = table.map(systems, function(id)
            return { type = "system", system = id }
        end)
    }
end

---@param name string
---@param subphases SystemPhase[]
---@return SystemPhase
local function parent_phase(name, subphases)
    return {
        name = name,
        steps = table.map(subphases, function(phase)
            return { type = "phase", phase = phase }
        end)
    }
end

---@class Step
---@field systems Symbol<SystemDraft>[]

---@param context CompilationContext
---@param relations SystemRelation[]
---@return Step[]
local function sort_dag(context, relations)
    local adjacency = {} ---@type table<Id, Id[]> -- исходящие вершины
    local indegree = {} ---@type table<Id, integer> -- входящие вершины

    local total = 0

    for _, relation in ipairs(relations) do
        local id = relation.system.id
        adjacency[id] = {}
        indegree[id] = 0
        total = total + 1
    end

    for _, relation in ipairs(relations) do
        local system = relation.system.id
        local preceding_system = relation.preceding

        if preceding_system then
            table.insert(adjacency[preceding_system.id], system)
            indegree[system] = indegree[system] + 1
        end
    end

    local steps = {} ---@type Step[]
    local ready = {} ---@type Id[]

    for system_id, in_relations in pairs(indegree) do
        if in_relations == 0 then
            table.insert(ready, system_id)
        end
    end

    local processed = 0
    while #ready ~= 0 do
        processed = processed + #ready

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

        table.insert(steps, { systems = ready })

        ready = next
    end

    if processed ~= total then
        context.reports:report_error(
            { phase = "linking", message = "Обнаружен цикл зависимостей систем" }
        )
        steps = {}
    end

    return steps
end

---@param name string
---@param context CompilationContext
---@param systems table<Id, Symbol<SystemDraft>>
---@return SystemPhase
local function compose_phase(name, context, systems)
    local relations = system_relations(context, systems)
    local steps = sort_dag(context, relations)
    local subphases = {}

    for index, step in ipairs(steps) do
        table.insert(subphases, systems_phase("Node " .. index, step.systems))
    end

    return parent_phase(name, subphases)
end

---@param context CompilationContext
---@param categorized_symbols CategorizedSymbols
---@return TickPhasesDraft
function phases.compose(context, categorized_symbols)
    local systems = categorized_symbols.systems
    local not_stated = {}
    local verb_lookup = {}

    for system_id, system_symbol in pairs(systems) do
        local phase = system_symbol.value.phase
        if not phase then
            not_stated[system_id] = system_symbol
        elseif phase == "verb_lookup" then
            verb_lookup[system_id] = system_symbol
        end
    end

    local base_phase = compose_phase("base", context, not_stated)
    local verb_lookup_phase = compose_phase("verb_lookup", context, verb_lookup)

    return {
        base = base_phase,
        verb_lookup = verb_lookup_phase
    }
end

return phases
