-- The configuration resolves AUTO_TRAIL. This script samples only its own mounted root.
local root
local previous, emittedTick = {}, nil
local function resolveParts()
   root = models.model.Cosmetic
   previous, emittedTick = {}, nil
end
resolveParts()
events.RESOURCE_RELOAD:register(resolveParts)
cosmetic_controls:newToggle("enabled"):setTitle("Emit / 发射"):setDefault(true)
cosmetic_controls:newSlider("interval"):setTitle("Interval / 间隔"):setRange(1, 40, 1):setDefault(4)
cosmetic_controls:newSlider("lifetime"):setTitle("Lifetime / 寿命"):setRange(1, 100, 1):setDefault(24)
cosmetic_controls:newSlider("size"):setTitle("Size / 大小"):setRange(.1, 2, .05):setDefault(.35)
cosmetic_controls:newToggle("star"):setTitle("Star sheet / 星光精灵图"):setDefault(false)
   :setOnChange(function(value) cosmetic_controls:set("emissive", value) end)
cosmetic_controls:newToggle("emissive"):setTitle("Full bright / 自发光"):setDefault(false)
cosmetic_controls:newToggle("light"):setTitle("Dynamic light / 动态光源"):setDefault(false)
cosmetic_controls:newSlider("frame_ticks"):setTitle("Frame ticks / 帧间隔"):setRange(1, 10, 1):setDefault(2)
cosmetic_controls:newToggle("random_start"):setTitle("Random start / 随机起帧"):setDefault(true)
cosmetic_controls:newToggle("paused"):setTitle("Pause new stars / 暂停新星光"):setDefault(false)

local ticks = 0
events.TICK:register(function()
   ticks = ticks + 1
end)
-- Emit while the native render/preview scope is active. Carrying a GUI position into a later
-- world tick would lose that scope. Tick counters bound emission independently of frame rate.
events.POST_RENDER:register(function(delta, context)
   local position = root:partToWorldMatrix():apply()
   local last = previous[context]
   previous[context] = position
   if last and (position - last):lengthSquared() > 64 then
      particles:removeParticles()
      emittedTick = ticks
      return
   end
   if not cosmetic_controls:get("enabled") or ticks == 0 or emittedTick == ticks
      or ticks % cosmetic_controls:get("interval") ~= 0 then return end
   emittedTick = ticks
   local star = cosmetic_controls:get("star")
   local texture = star and "model.twinkle_star_sheet" or "model.sakura_petal"
   local velocity = star and vectors.vec3(0, .005, 0) or vectors.vec3(.008, -.012, 0)
   local particle = particles:newCustomParticle(texture, position, velocity)
      :setLifetime(cosmetic_controls:get("lifetime")):setScale(cosmetic_controls:get("size"))
      :setEmissive(cosmetic_controls:get("emissive")):setDynamicLight(cosmetic_controls:get("light"))
   if star then
      particle:setSpriteSheet({columns=4, rows=2, frameCount=8, startFrame=0, endFrame=7,
         frameTimeTicks=cosmetic_controls:get("frame_ticks"), playback="loop",
         randomStart=cosmetic_controls:get("random_start"), paused=cosmetic_controls:get("paused")})
   end
end)
