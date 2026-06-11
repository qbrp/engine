Easing = {
    ease_in_sine = function(t)
        return 1 - math.cos((t * math.pi) / 2);
    end
}

---@alias Easing fun(t: number): number