-- Same script works for a worn accessory or an ordinary player model.
accessory = {options={size=0.12, height=1.3}, time=0}
local loaded = assert(textures:getTextures()[1], "The source texture remains instance-owned")
local dimensions = loaded:getDimensions()
local aspect = dimensions.x / dimensions.y
local image = textures:newTexture("sprint-glow", 1, 1)
image:fill(0, 0, 1, 1, 1, 1, 1, 1)
local particle
models.model.model.Marker:setVisible(false)

function accessory.clear()
    if particle then
        particle:remove()
    end
    particle = nil
end

function accessory.update(time)
    if not player:isLoaded() then
        accessory.clear()
        return
    end
    local origin = player:getPos() + vectors.vec3(0, accessory.options.height,
        0.25 + 0.05 * math.sin(time * 3))
    if not particle or not particle:isAlive() then
        particle = particles:newCustomParticle(image, origin):setLifetime(20):setEmissive(true)
        particle:setQuad(matrices.scale4(accessory.options.size * aspect, accessory.options.size, 1))
    end
    particle:setPos(origin)
    if player:isSprinting() then
        particle:setColor(1, 0.25, 0.05, 1)
    else
        particle:setColor(0.1, 0.8, 1, 0.8)
    end
end

events.TICK:register(function()
    accessory.time = accessory.time + 1/20
end)
events.RENDER:register(function(delta)
    accessory.update(accessory.time + delta/20)
end)
