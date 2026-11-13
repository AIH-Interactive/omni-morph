-- The runtime test adds geometry to the Item group before exercising submission.
function events.item_render(item, mode)
    if item.id == "minecraft:stick" then
        return models.model.Item
    end
end

-- Independent camera sample: nil restores the current vanilla calculation.
function pings.feature_fov(value)
    renderer:setFOV(value)
end
