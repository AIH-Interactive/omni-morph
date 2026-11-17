-- A small two-bone back pennant. Edit dimensions in model/model.bbmodel.
accessory = {options={period=2, bend=24}, time=0}
local rig = models.model.model.Pennant
local mast, tip, cloth = rig.Mast, rig.Mast.Tip, rig.Cloth
cloth:setInverseBindMatrix(mast:getName(), matrices.mat4())
cloth:setInverseBindMatrix(tip:getName(), matrices.translate4(0, -4, 0))

function accessory.update(time)
    local degrees = math.sin(time * math.pi * 2 / accessory.options.period) * accessory.options.bend
    local half = math.rad(degrees) / 2
    mast:setMatrix(matrices.compose4(vectors.vec3(0, 0, 0), vectors.vec4(0, 0, 0, 1), vectors.vec3(1, 1, 1)))
    tip:setMatrix(matrices.compose4(vectors.vec3(0, 4, 0),
        vectors.vec4(0, 0, math.sin(half), math.cos(half)), vectors.vec3(1, 1, 1)))
end

events.TICK:register(function()
    accessory.time = accessory.time + 1/20
end)
events.RENDER:register(function(delta)
    accessory.update(accessory.time + delta/20)
end)
accessory.update(0)
