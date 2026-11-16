-- A camera-oriented shoulder sigil. No camera control or world lookup is required.
accessory = {options={size=0.35, height=1.4, lean=0.025}, time=0}
local image = assert(textures:getTextures()[1], "The sigil needs its own model texture")
local particle, original
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
    local origin = player:getPos() + vectors.vec3(0.35, accessory.options.height, 0)
    local direction = client:getCameraPos() - origin
    if direction:length() > 0 then
        origin = origin + direction:normalize() * accessory.options.lean
    end
    if not particle or not particle:isAlive() then
        particle = particles:newCustomParticle(image, origin):setLifetime(20):setEmissive(true)
        particle:setQuad(matrices.scale4(accessory.options.size, accessory.options.size, 1))
        original = particle:getQuad()
        particle:setLayers({{blend="additive", cull=false, alpha_cutoff=0.01,
            depth_test=true, order=-1, emissive=true}})
    end
    local rotation = client:getCameraRot()
    local camera = matrices.yRotation4(180 - rotation.y)
        :rightMultiply(matrices.xRotation4(-rotation.x)):rightMultiply(matrices.zRotation4(rotation.z))
    particle:setPos(origin):setQuad(camera:rightMultiply(original:copy())
        :rightMultiply(matrices.zRotation4(time * 35)))
end

events.TICK:register(function()
    accessory.time = accessory.time + 1/20
end)
events.RENDER:register(function(delta)
    accessory.update(accessory.time + delta/20)
end)
