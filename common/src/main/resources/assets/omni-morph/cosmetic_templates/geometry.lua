-- Handles belong only to this cosmetic instance. Resolve once and after a resource reload.
local root, primary, secondary
local category = "@CATEGORY@"
local function resolveParts()
   root = models.model.Cosmetic
   primary, secondary = nil, nil
   if category == "Head" then primary = root.Headwear
   elseif category == "Back" then primary = root.Backpack.Flap
   elseif category == "Feet" then primary = root.Footwear
   elseif category == "Legs" then primary = root.Legwear
   elseif category == "Face" then primary, secondary = root.Glasses.LeftLens, root.Glasses.RightLens
   elseif category == "Hair" then primary = root.HairClip.Ribbon
   elseif category == "Tail" then primary = root.TailBase
   elseif category == "Wings" then primary, secondary = root.WingRoot.LeftWing, root.WingRoot.RightWing end
end
resolveParts()
events.RESOURCE_RELOAD:register(resolveParts)

cosmetic_controls:newToggle("visible"):setTitle("Visible / 显示"):setDefault(true)
cosmetic_controls:newToggle("animate"):setTitle("Animate / 动画"):setDefault(true)
cosmetic_controls:newSlider("amplitude"):setTitle("Amplitude / 幅度"):setRange(0, 45, 1):setDefault(12)
cosmetic_controls:newSlider("speed"):setTitle("Speed / 速度"):setRange(0, 2, .05):setDefault(.2)
cosmetic_controls:newSlider("offset"):setTitle("Local offset / 局部偏移"):setRange(-8, 8, .25):setDefault(0)
cosmetic_controls:newSlider("opacity"):setTitle("Lens opacity / 镜片透明度"):setRange(0, 1, .05):setDefault(.7)
local time = 0
events.TICK:register(function()
   time = time + 1
   local visible = cosmetic_controls:get("visible")
   root:setVisible(visible)
   if not visible or not primary then return end
   local wave = cosmetic_controls:get("animate") and math.sin(time * cosmetic_controls:get("speed")) or 0
   local angle = wave * cosmetic_controls:get("amplitude")
   primary:setPos(0, cosmetic_controls:get("offset"), 0)
   if category == "Face" then
      primary:setOpacity(cosmetic_controls:get("opacity"))
      secondary:setOpacity(cosmetic_controls:get("opacity"))
   elseif category == "Wings" then
      primary:setRot(0, angle, 0)
      secondary:setRot(0, -angle, 0)
   elseif category == "Tail" then primary:setRot(0, angle, 0)
   else primary:setRot(angle, 0, 0) end
end)
