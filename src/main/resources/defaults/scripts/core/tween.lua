---@type World
local World = World

--- https://easings.net/
Easing = {
    ease_in_sine = function(t)
        return 1 - math.cos((t * math.pi) / 2)
    end,

    ease_out_sine = function(t)
        return math.sin((t * math.pi) / 2)
    end
}

---@alias Easing fun(t: number): number

--------------------------------------------------

---@class Tween
---@field start number
---@field final number
---@field easing Easing
---@field duration number
---@field progress number
---@field apply fun(entity: Entity, value: number)
Tween = {}

---@param start number
---@param final number
---@param duration number
---@param apply fun(entity: Entity, value: number)
---@param easing Easing?
function Tween.create(start, final, duration, apply, easing)
    return {
        start = start,
        final = final,
        duration = duration,
        easing = easing or Easing.ease_in_sine,
        progress = 0,
        apply = apply
    }
end

--------------------------------------------------

---@class TweenEntity : Entity
---@field entity Entity
---@field with fun(self: TweenEntity, tween: Tween): TweenEntity

---@param entity Entity
---@return TweenEntity
function World:tween(entity)
    local tween_entity = self:add_entity()

    tween_entity:set_component(
        TweenTargetComponent:construct({
            entity = entity
        })
    )

    return setmetatable({
        entity = tween_entity,

        with = function(self, tween)
            local container = self.entity:get_or_create_component(
                TweenContainerComponent,
                function()
                    return TweenContainerComponent:construct({
                        tweens = {}
                    })
                end
            )

            table.insert(container.tweens, tween)

            return self
        end
    }, {
        __index = tween_entity
    })
end

--------------------------------------------------

---@class TweenTargetComponent : Component
---@field entity Entity
TweenTargetComponent = Component.of("core/tween/target")

---@class TweenContainerComponent : Component
---@field tweens Tween[]
TweenContainerComponent = Component.of("core/tween/container")

---@param world World
---@param entity Entity
---@param container TweenContainerComponent
---@param target TweenTargetComponent
local function TweenSystem(world, entity, container, target)
    if not target.entity:exists() then
        entity:destroy()
        return
    end

    for i = #container.tweens, 1, -1 do
        local tween = container.tweens[i]
        if tween.duration <= 0 then
            tween.apply(target.entity, tween.final)
            table.remove(container.tweens, i)
            goto continue
        end

        local t = tween.progress / tween.duration

        if t >= 1 then
            tween.apply(target.entity, tween.final)
            table.remove(container.tweens, i)
            goto continue
        end

        local eased = tween.easing(t)
        local value = tween.start + (tween.final - tween.start) * eased

        tween.apply(target.entity, value)

        tween.progress = tween.progress + 1

        ::continue::
    end

    if #container.tweens == 0 then
        entity:destroy()
    end
end

Callbacks.build()
     :system({ TweenContainerComponent, TweenTargetComponent }, TweenSystem)
     :submit()