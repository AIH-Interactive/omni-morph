# Figura Molang 脚本示例

本文面向普通 Figura Avatar，不依赖 YSM 模型。目标是说明 Figura 模型如何在 Lua 中写入 Molang 变量、求值 Molang 表达式，并把结果应用到 `models.*` 或 `animations.*`。

## 引擎结论

- 普通 Figura Avatar 在加载动画数据时创建 `MolangContext`。
- 动画 NBT 中的 `mau`、`mbw`、`msd`、`mld` 会被解析为 Molang 表达式，用于动画 offset、blend weight、start delay、loop delay。
- Lua 侧入口是 `animations`：
  - `animations:evalMolang(expr)`：求值 Molang 表达式。
  - `animations:setMolangVar(name, value)`：写入一个变量。
  - `animations:getMolangVar(name)`：读取一个变量。
  - `animations:setMolangVars(table)`：批量写入变量。
  - `animations:getMolangVars()`：读取所有已写入变量。
- 单个 `Animation` 也有 `setMolangVar/getMolangVar/setMolangVars`，但变量仍写入 Avatar 的共享 Molang 变量空间。

> 注意：如果 Avatar 没有动画数据，普通 Figura 路径不会创建 `MolangContext`，此时 `animations:evalMolang(...)` 返回 `0`，写变量也不会实际生效。建议至少保留一个可加载的空动画或普通动画。

## 最小示例：Lua 写变量，Molang 求值，应用到模型

```lua
animations:setMolangVar("v.console_scale", 1.25)

events.RENDER:register(function(delta)
  local scale = animations:evalMolang(
    "math.clamp(v.console_scale + math.sin(query.anim_time * 6) * 0.1, 0.2, 3.0)"
  )

  local head = models.model.Head
  if head then
    head:setScale(scale)
  end
end)
```

## 使用 avatar_controls 传值给 Molang

控件可以绑定到 `v.*` 变量，也可以在 `onChange` 中手动写入。绑定方式更适合 console 页面和 UI 控件。

```lua
avatar_controls:newPage("molang.console")
  :title("Molang Console")

avatar_controls:newSlider("molang.scale")
  :page("molang.console")
  :title("Scale")
  :range(0.2, 3.0, 0.05)
  :binding("v.console_scale")
  :default(1.0)

avatar_controls:newSlider("molang.sway")
  :page("molang.console")
  :title("Sway")
  :range(0.0, 1.0, 0.05)
  :binding("v.console_sway")
  :default(0.3)

events.RENDER:register(function(delta)
  local scale = animations:evalMolang("math.clamp(v.console_scale, 0.2, 3.0)")
  local yaw = animations:evalMolang("math.sin(query.anim_time * 5) * v.console_sway * 25")

  local head = models.model.Head
  if head then
    head:setScale(scale)
    head:setRot(0, yaw, 0)
  end
end)
```

## 手动批量写入 Molang 变量

`setMolangVars(table)` 的 key 直接作为变量名存储，建议不要在 table key 里写 `v.` 前缀；表达式读取时再用 `v.name`。

```lua
animations:setMolangVars({
  console_scale = 1.2,
  console_sway = 0.4,
  console_phase = 1.57
})

events.RENDER:register(function(delta)
  local yaw = animations:evalMolang(
    "math.sin(query.anim_time * 4 + v.console_phase) * v.console_sway * 30"
  )

  if models.model.Head then
    models.model.Head:setRot(0, yaw, 0)
    models.model.Head:setScale(animations:evalMolang("v.console_scale"))
  end
end)
```

## 读取和调试 Molang 变量

```lua
animations:setMolangVar("v.console_scale", 1.5)

print("console_scale", animations:getMolangVar("v.console_scale"))
print("eval", animations:evalMolang("v.console_scale * 2"))

for name, value in pairs(animations:getMolangVars()) do
  print("molang var", name, value)
end
```

## 用 Molang 查询驱动 Figura 模型

Molang 表达式可读取 `query.*`，例如时间、生命值、位置、相机距离等。下面示例用生命值和相机距离控制模型显示和缩放。

```lua
events.RENDER:register(function(delta)
  local health_ratio = animations:evalMolang("query.health / math.max(query.max_health, 1)")
  local near_camera = animations:evalMolang("query.distance_from_camera < 4")

  local aura = models.model.Aura
  if aura then
    aura:setVisible(near_camera > 0)
    aura:setScale(math.max(0.2, health_ratio))
  end
end)
```

## 写变量后驱动指定 Animation

下面示例通过共享 Molang 变量控制动画速度、权重或脚本逻辑。单个动画对象上的 `setMolangVar` 只是链式写法，变量仍进入 Avatar 的共享变量表。

```lua
local idle = animations.model.idle

if idle then
  idle:setMolangVar("v.idle_weight", 0.8)
  idle:play()
end

events.RENDER:register(function(delta)
  local weight = animations:evalMolang(
    "math.clamp(v.idle_weight + math.sin(query.anim_time * 2) * 0.2, 0, 1)"
  )

  if idle then
    idle:blend(weight)
  end
end)
```

## Molang 和 Lua for 结合：尾巴链条驱动

下面示例假设模型里有一组尾巴骨骼：`Tail1`、`Tail2`、`Tail3`、`Tail4`、`Tail5`。Lua 的 `for` 循环负责遍历骨骼链，Molang 表达式负责计算每一节的摆动值。

```lua
local tail_parts = {
  models.model.Tail1,
  models.model.Tail2,
  models.model.Tail3,
  models.model.Tail4,
  models.model.Tail5
}

animations:setMolangVars({
  tail_speed = 4.0,
  tail_amp = 18.0,
  tail_delay = 0.45,
  tail_lift = 6.0
})

events.RENDER:register(function(delta)
  for index, part in ipairs(tail_parts) do
    if part then
      animations:setMolangVar("v.tail_index", index)

      local yaw = animations:evalMolang(
        "math.sin(query.anim_time * v.tail_speed - v.tail_index * v.tail_delay) * v.tail_amp"
      )

      local pitch = animations:evalMolang(
        "math.cos(query.anim_time * v.tail_speed - v.tail_index * v.tail_delay) * v.tail_lift"
      )

      part:setRot(pitch, yaw, 0)
    end
  end
end)
```

如果需要通过 console 页面调参，可以把速度、幅度、延迟和抬起量绑定到 Molang 变量。

```lua
avatar_controls:newPage("tail.console")
  :title("Tail Console")

avatar_controls:newSlider("tail.speed")
  :page("tail.console")
  :title("Speed")
  :range(0.0, 10.0, 0.1)
  :binding("v.tail_speed")
  :default(4.0)

avatar_controls:newSlider("tail.amp")
  :page("tail.console")
  :title("Amplitude")
  :range(0.0, 45.0, 0.5)
  :binding("v.tail_amp")
  :default(18.0)

avatar_controls:newSlider("tail.delay")
  :page("tail.console")
  :title("Delay")
  :range(0.0, 1.5, 0.05)
  :binding("v.tail_delay")
  :default(0.45)

avatar_controls:newSlider("tail.lift")
  :page("tail.console")
  :title("Lift")
  :range(-20.0, 20.0, 0.5)
  :binding("v.tail_lift")
  :default(6.0)

local tail_parts = {
  models.model.Tail1,
  models.model.Tail2,
  models.model.Tail3,
  models.model.Tail4,
  models.model.Tail5
}

events.RENDER:register(function(delta)
  for index, part in ipairs(tail_parts) do
    if part then
      animations:setMolangVar("v.tail_index", index)

      local phase = "query.anim_time * v.tail_speed - v.tail_index * v.tail_delay"
      local yaw = animations:evalMolang("math.sin(" .. phase .. ") * v.tail_amp")
      local pitch = animations:evalMolang("math.cos(" .. phase .. ") * v.tail_lift")

      part:setRot(pitch, yaw, 0)
    end
  end
end)
```

这个模式的分工是：Lua 管“遍历和应用到模型部件”，Molang 管“时间、变量、数学表达式”。如果尾巴骨骼命名不同，只需要替换 `tail_parts` 表里的模型路径。

## bbmodel 保留表达式，外部只传值

如果动画 keyframe 里写的是 Molang 表达式，可以把具体数值从 bbmodel 中抽离出来，只保留变量引用。运行时再通过 Lua、console 页面或动作轮写入变量值。

这种模式适合把动画公式固化在 bbmodel 中，把调参权交给外部脚本：

- bbmodel：保留每节骨骼的 Molang 表达式。
- Lua：提供默认值、console 控件、动作轮入口和预设切换。
- Molang 变量：作为 bbmodel 和 Lua 之间的参数通道。

例如尾巴每节骨骼的 `rotation_y` 可以写成：

```text
Tail1 rotation_y:
math.sin(query.anim_time * v.tail_speed - 1 * v.tail_delay) * v.tail_amp

Tail2 rotation_y:
math.sin(query.anim_time * v.tail_speed - 2 * v.tail_delay) * v.tail_amp

Tail3 rotation_y:
math.sin(query.anim_time * v.tail_speed - 3 * v.tail_delay) * v.tail_amp

Tail4 rotation_y:
math.sin(query.anim_time * v.tail_speed - 4 * v.tail_delay) * v.tail_amp

Tail5 rotation_y:
math.sin(query.anim_time * v.tail_speed - 5 * v.tail_delay) * v.tail_amp
```

如果还要上下抬尾，可以在每节骨骼的 `rotation_x` 中写：

```text
Tail1 rotation_x:
math.cos(query.anim_time * v.tail_speed - 1 * v.tail_delay) * v.tail_lift

Tail2 rotation_x:
math.cos(query.anim_time * v.tail_speed - 2 * v.tail_delay) * v.tail_lift

Tail3 rotation_x:
math.cos(query.anim_time * v.tail_speed - 3 * v.tail_delay) * v.tail_lift
```

Lua 侧只负责传入默认值和可调参数：

```lua
animations:setMolangVars({
  tail_speed = 4.0,
  tail_amp = 18.0,
  tail_delay = 0.45,
  tail_lift = 6.0
})

avatar_controls:newPage("tail.console")
  :title("Tail Console")

avatar_controls:newSlider("tail.speed")
  :page("tail.console")
  :title("Speed")
  :range(0.0, 10.0, 0.1)
  :binding("v.tail_speed")
  :default(4.0)

avatar_controls:newSlider("tail.amp")
  :page("tail.console")
  :title("Amplitude")
  :range(0.0, 45.0, 0.5)
  :binding("v.tail_amp")
  :default(18.0)

avatar_controls:newSlider("tail.delay")
  :page("tail.console")
  :title("Delay")
  :range(0.0, 1.5, 0.05)
  :binding("v.tail_delay")
  :default(0.45)

avatar_controls:newSlider("tail.lift")
  :page("tail.console")
  :title("Lift")
  :range(-20.0, 20.0, 0.5)
  :binding("v.tail_lift")
  :default(6.0)
```

动作轮可以只负责切换预设值：

```lua
local root = action_wheel:newPage("root")

root:newAction(1)
  :title("Soft Tail")
  :item("minecraft:feather")
  :onLeftClick(function()
    animations:setMolangVars({
      tail_speed = 2.5,
      tail_amp = 10.0,
      tail_delay = 0.55,
      tail_lift = 3.0
    })
  end)

root:newAction(2)
  :title("Fast Tail")
  :item("minecraft:rabbit_foot")
  :onLeftClick(function()
    animations:setMolangVars({
      tail_speed = 6.0,
      tail_amp = 24.0,
      tail_delay = 0.35,
      tail_lift = 8.0
    })
  end)

action_wheel:setPage(root)
```

需要注意：keyframe 表达式在动画加载时会被解析和预编译，所以外部应传入变量值，而不是动态替换表达式文本。普通 keyframe 求值也不会自动知道“当前是第几节尾巴”，因此每节表达式里应固定 `1/2/3/4/5`，或者使用 `v.tail1_amp`、`v.tail2_amp` 这类分节变量。

## Lua for 给尾巴内部表达式指定偏移值

如果希望 bbmodel 内部表达式保持固定格式，但每节尾巴的相位、幅度或延迟由 Lua 批量指定，可以给每节骨骼准备独立变量。Lua 的 `for` 循环只负责写入参数，bbmodel 里的 Molang 表达式负责消费这些参数。

bbmodel 中每节尾巴的 `rotation_y` 可以写成：

```text
Tail1 rotation_y:
math.sin(query.anim_time * v.tail_speed + v.tail_1_offset) * v.tail_1_amp

Tail2 rotation_y:
math.sin(query.anim_time * v.tail_speed + v.tail_2_offset) * v.tail_2_amp

Tail3 rotation_y:
math.sin(query.anim_time * v.tail_speed + v.tail_3_offset) * v.tail_3_amp

Tail4 rotation_y:
math.sin(query.anim_time * v.tail_speed + v.tail_4_offset) * v.tail_4_amp

Tail5 rotation_y:
math.sin(query.anim_time * v.tail_speed + v.tail_5_offset) * v.tail_5_amp
```

如果还需要上下抬尾，每节 `rotation_x` 可以写：

```text
Tail1 rotation_x:
math.cos(query.anim_time * v.tail_speed + v.tail_1_offset) * v.tail_1_lift

Tail2 rotation_x:
math.cos(query.anim_time * v.tail_speed + v.tail_2_offset) * v.tail_2_lift

Tail3 rotation_x:
math.cos(query.anim_time * v.tail_speed + v.tail_3_offset) * v.tail_3_lift
```

Lua 侧用 `for` 批量给每节表达式指定偏移值：

```lua
local tail_count = 5

local tail_config = {
  speed = 4.0,
  amp = 18.0,
  lift = 6.0,
  offset_step = -0.45,
  amp_falloff = 0.9,
  lift_falloff = 0.85
}

animations:setMolangVar("v.tail_speed", tail_config.speed)

for index = 1, tail_count do
  local amp = tail_config.amp * (tail_config.amp_falloff ^ (index - 1))
  local lift = tail_config.lift * (tail_config.lift_falloff ^ (index - 1))
  local offset = tail_config.offset_step * (index - 1)

  animations:setMolangVar("v.tail_" .. index .. "_amp", amp)
  animations:setMolangVar("v.tail_" .. index .. "_lift", lift)
  animations:setMolangVar("v.tail_" .. index .. "_offset", offset)
end
```

也可以把这套写值逻辑封装成函数，供 console 控件或动作轮预设调用：

```lua
local tail_count = 5

local function apply_tail_preset(speed, amp, lift, offset_step)
  animations:setMolangVar("v.tail_speed", speed)

  for index = 1, tail_count do
    local normalized = (index - 1) / math.max(tail_count - 1, 1)
    local falloff = 1.0 - normalized * 0.35

    animations:setMolangVar("v.tail_" .. index .. "_amp", amp * falloff)
    animations:setMolangVar("v.tail_" .. index .. "_lift", lift * falloff)
    animations:setMolangVar("v.tail_" .. index .. "_offset", offset_step * (index - 1))
  end
end

apply_tail_preset(4.0, 18.0, 6.0, -0.45)

local root = action_wheel:newPage("root")

root:newAction(1)
  :title("Soft Tail")
  :item("minecraft:feather")
  :onLeftClick(function()
    apply_tail_preset(2.5, 10.0, 3.0, -0.55)
  end)

root:newAction(2)
  :title("Fast Tail")
  :item("minecraft:rabbit_foot")
  :onLeftClick(function()
    apply_tail_preset(6.0, 24.0, 8.0, -0.35)
  end)

action_wheel:setPage(root)
```

这个写法的优势是 bbmodel 表达式稳定，不需要在 Lua 中逐帧控制骨骼，也不需要动态改 keyframe。Lua 只在初始化、console 改值或动作轮切换预设时写入变量；动画播放时由 Molang keyframe 自己读取每节偏移值。

## 动作轮入口：打开 Molang Console

```lua
avatar_controls:newPage("molang.console")
  :title("Molang Console")

avatar_controls:newSlider("molang.scale")
  :page("molang.console")
  :title("Scale")
  :range(0.2, 3.0, 0.05)
  :binding("v.console_scale")
  :default(1.0)

local root = action_wheel:newPage("root")

root:newAction(1)
  :title("Molang")
  :item("minecraft:amethyst_shard")
  :onLeftClick(function()
    avatar_controls:openPage("molang.console")
  end)

action_wheel:setPage(root)
```

## 使用建议

- 对模型部件做实时变换时，推荐在 `events.RENDER` 中 `evalMolang`，再应用到 `models.*`。
- 对跨脚本或控制台传值，推荐统一使用 `v.*` 命名，例如 `v.console_scale`、`v.weapon_mode`。
- `setMolangVar("v.name", value)` 和 `setMolangVar("variable.name", value)` 最终都会写入 `name`。
- `setMolangVars({ name = value })` 不会剥离 `v.` 前缀，因此批量写入时直接使用 `name`。
- 表达式解析失败或上下文不存在时，`animations:evalMolang(...)` 返回 `0`；调试时先确认 Avatar 有动画数据。
