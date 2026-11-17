-- Sampling a clip never starts playback or dispatches its timeline events.
accessory = {options={period=2, boundary="POST", echo_delay=0.2}, time=0}
local lantern, echo = models.model.model.Lantern, models.model.model.Echo
local clip = assert(animations:getAnimations()[1], "The lantern model must contain its sway animation")
clip:setKeyframeBoundary(accessory.options.boundary)

function accessory.update(time)
    local sample = time % accessory.options.period
    local pose = assert(clip:sample(lantern, sample), "Animation must bind Lantern")
    lantern:setMatrix(pose)
    -- A smaller trailing charm demonstrates a history query on the same clip.
    local history = assert(clip:sample(lantern, math.max(0, sample - accessory.options.echo_delay)))
    echo:setMatrix(history:translate(-2, 0, 0):scale(0.5, 0.5, 0.5))
end

events.TICK:register(function()
    accessory.time = accessory.time + 1/20
end)
events.RENDER:register(function(delta)
    accessory.update(accessory.time + delta/20)
end)
accessory.update(0)
