-- Small world-space history; RGBA and width change along the retained trail.
accessory = {options={samples=12, width=0.08, radius=0.3}, time=0}
local history = {}
local image = assert(textures:getTextures()[1], "The ribbon needs its own texture")
local particle
local root = models.model.model.Cosmetic
models.model.model.Cosmetic.Marker:setVisible(false)
local recordedTime
cosmetic_controls:newSlider("lifetime"):setTitle("Lifetime (ticks) / 寿命（tick）")
    :setRange(1, 300, 1):setDefault(20)

function accessory.clear()
    if particle then
        particle:remove()
    end
    particle, history = nil, {}
end

local function record()
    if not player:isLoaded() then
        accessory.clear()
        return
    end
    local t = accessory.time
    local point = root:partToWorldMatrix():apply(math.cos(t * 3) * accessory.options.radius * 16,
        (1 + math.sin(t * 2) * 0.15) * 16, math.sin(t * 3) * accessory.options.radius * 16)
    history[#history + 1] = point
    if #history > accessory.options.samples then
        table.remove(history, 1)
    end
end

function accessory.update(time)
    if #history < 2 then
        return
    end
    local origin = history[#history]
    if not particle or not particle:isAlive() then
        particle = particles:newCustomParticle(image, origin)
            :setLifetime(cosmetic_controls:get("lifetime")):setEmissive(true)
        particle:setLayers({{blend="alpha", cull=false, alpha_cutoff=0.001,
            depth_test=true, order=1, emissive=true}})
    end
    local vertices = {}
    local function edge(point, fraction, side)
        return {position=point - origin + vectors.vec3(0, side * accessory.options.width * fraction, 0),
            uv=vectors.vec2(fraction, side > 0 and 0 or 1),
            color=vectors.vec4(fraction, 0.25, 1 - fraction, fraction)}
    end
    for index = 1, #history - 1 do
        local a, b = (index - 1) / (#history - 1), index / (#history - 1)
        vertices[#vertices + 1] = edge(history[index], a, 1)
        vertices[#vertices + 1] = edge(history[index], a, -1)
        vertices[#vertices + 1] = edge(history[index + 1], b, -1)
        vertices[#vertices + 1] = edge(history[index + 1], b, 1)
    end
    local uv = matrices.mat3(vectors.vec3(0.8, 0, 0), vectors.vec3(0, 0.8, 0), vectors.vec3(0.1, 0.1, 1))
    particle:setPos(origin):setQuads(vertices)
        :setQuadsTransform(matrices.yRotation4(math.sin(time) * 8), uv)
end

events.TICK:register(function()
    accessory.time = accessory.time + 1/20
end)
events.POST_RENDER:register(function(delta)
    if recordedTime ~= accessory.time then
        recordedTime = accessory.time
        record()
    end
    accessory.update(accessory.time + delta/20)
end)
