-- Quaternion bobbing plus an independently composed local Euler adjustment.
accessory = {options={period=2, tilt={12, 20, 0}}, time=0}
local charm = models.model.model.Charm
local rest = {position={0, 10, 3}, quaternion={0, 0, 0, 1}, scale={1, 1, 1}}
local frames = {
    {time=0, position={-3, 10, 3}, quaternion={0, 0, 0, 1}, scale={1, 1, 1}},
    {time=1, position={3, 12, 3}, quaternion={0, 0, 1, 0}, scale={1.25, 0.75, 1}},
    {time=2, position={-3, 10, 3}, quaternion={0, 0, 0, -1}, scale={1, 1, 1}}
}

function accessory.update(time)
    local sample = (time % accessory.options.period) * 2 / accessory.options.period
    local rotation = matrices.composeEulerXYZ(vectors.vec3(table.unpack(accessory.options.tilt)),
        vectors.vec3(0, time * 30, 0))
    local offset = matrices.xRotation4(rotation.x):rightMultiply(matrices.yRotation4(rotation.y))
        :rightMultiply(matrices.zRotation4(rotation.z))
    charm:setMatrix(matrices.sample4(rest, frames, sample, 7):rightMultiply(offset))
end

events.TICK:register(function()
    accessory.time = accessory.time + 1/20
end)
events.RENDER:register(function(delta)
    accessory.update(accessory.time + delta/20)
end)
accessory.update(0)
