# avatar_console 脚本示例

本文示例用于在 `avatar_console` 中直接执行，重点覆盖“传值”场景：

- 普通 Figura Avatar：通过 `avatar_controls:set(id, value)` 写值，再由 Lua 把值应用到 `models.*`。
- YSM Avatar：通过 `avatar_controls` 的 `binding("v.*")` 写入 Molang 变量，或者用 `animations:setMolangVar(...)` 临时直写变量。

## 添加 console 控件页面

`avatar_controls:newPage("console")` 会在根页面创建一个跳转入口；后续控件使用 `:page("console")` 归入这个页面。

```lua
avatar_controls:newPage("console")
  :title("Console")

avatar_controls:newLabel("console.title")
  :page("console")
  :title("Avatar Console")

avatar_controls:newSlider("console.head_scale")
  :page("console")
  :title("Head Scale")
  :range(0.2, 3.0, 0.05)
  :default(1.0)

avatar_controls:newToggle("console.visible")
  :page("console")
  :title("Visible")
  :default(true)

avatar_controls:newButton("console.apply")
  :page("console")
  :title("Apply")
  :onPress(function()
    local head = models.model.Head
    if head then
      head:setVisible(avatar_controls:get("console.visible"))
      head:setScale(avatar_controls:get("console.head_scale") or 1)
    end
  end)

avatar_controls:openPage("console")
```

如果只需要定义页面，不想自动打开，删除最后一行 `avatar_controls:openPage("console")` 即可。

## 页面入口放在动作轮盘

如果希望入口不出现在控件根页面，而是放到 Figura 动作轮盘，可以在动作轮根页创建 `Console` 按钮，点击后打开 `avatar_controls` 的 console 页面。

```lua
avatar_controls:newPage("console")
  :title("Console")

avatar_controls:newSlider("console.head_scale")
  :page("console")
  :title("Head Scale")
  :range(0.2, 3.0, 0.05)
  :default(1.0)

avatar_controls:newToggle("console.visible")
  :page("console")
  :title("Visible")
  :default(true)

local root = action_wheel:newPage("root")

root:newAction(1)
  :title("Console")
  :item("minecraft:command_block")
  :onLeftClick(function()
    avatar_controls:openPage("console")
  end)

action_wheel:setPage(root)
```

也可以让轮盘入口先进入一个轮盘内的 console 子页面，再由子页面打开控件页或触发调试动作。

```lua
local root = action_wheel:newPage("root")
local consoleWheel = action_wheel:newPage("console")

root:newAction(1)
  :title("Console")
  :item("minecraft:command_block")
  :onLeftClick(function()
    action_wheel:setPage(consoleWheel)
  end)

consoleWheel:newAction(1)
  :title("Open Controls")
  :item("minecraft:book")
  :onLeftClick(function()
    avatar_controls:openPage("console")
  end)

consoleWheel:newAction(2)
  :title("Emotion 2")
  :item("minecraft:amethyst_shard")
  :onLeftClick(function()
    animations:setMolangVar("v.roaming.emotion", 2)
  end)

consoleWheel:newAction(8)
  :title("Back")
  :item("minecraft:arrow")
  :onLeftClick(function()
    action_wheel:setPage(root)
  end)

action_wheel:setPage(root)
```

## 普通 Figura 模型：传值到模型部件

```lua
-- avatar_console 执行
local visible = true
local scale = 1.25

avatar_controls:newToggle("console.visible")
  :page("console")
  :title("Console Visible")
  :default(true)

avatar_controls:newSlider("console.head_scale")
  :page("console")
  :title("Console Head Scale")
  :range(0.2, 3.0, 0.05)
  :default(1.0)

avatar_controls:set("console.visible", visible)
avatar_controls:set("console.head_scale", scale)

local head = models.model.Head
if head then
  head:setVisible(avatar_controls:get("console.visible"))
  head:setScale(avatar_controls:get("console.head_scale"))
end
```

## 普通 Figura 模型：持续应用传入值

适合需要在控制台改值后持续影响模型状态的场景。

```lua
avatar_controls:newSlider("console.head_scale")
  :page("console")
  :title("Console Head Scale")
  :range(0.2, 3.0, 0.05)
  :default(1.0)

avatar_controls:set("console.head_scale", 1.4)

events.TICK:register(function()
  local scale = avatar_controls:get("console.head_scale") or 1
  local head = models.model.Head

  if head then
    head:setScale(scale)
  end
end)
```

## YSM 模型：传值到 Molang 变量

YSM 模型优先使用 `binding("v.*")` 把控件值绑定到 Molang 变量，再由 controller、function 或 animation 消费变量。

```lua
avatar_controls:newPage("ysm.console")
  :title("YSM Console")

avatar_controls:newSlider("ysm.console_emotion")
  :page("ysm.console")
  :title("YSM Emotion")
  :range(0, 10, 1)
  :binding("v.roaming.emotion")
  :default(0)

avatar_controls:newToggle("ysm.console_car")
  :page("ysm.console")
  :title("Show Car")
  :binding("v.show_car")
  :default(false)

avatar_controls:set("ysm.console_emotion", 3)
avatar_controls:set("ysm.console_car", true)
```

## YSM 动作轮：添加 console 页面

如果要把 YSM 动作暴露到动作轮页面，可以先创建或注册一个 console 页，再把动作绑定到该页的槽位。

```lua
ysm_actions:addWheelPage("console")

for id, action in pairs(ysm_actions:getActions()) do
  print("action", id)
end

ysm_actions:bindAction("Wave", "extra_animation.wave", "key.keyboard.h", true)
ysm_actions:setWheelSlot("extra_animation.wave", "console", 1)
ysm_actions:trigger("extra_animation.wave")
```

动作 id 需要替换为当前模型真实存在的 id；可以用上面的 `ysm_actions:getActions()` 或 `ysm_model:getActions()` 打印确认。

## YSM 模型：临时直接写 Molang 变量

适合调试时跳过控件定义，直接验证某个变量是否能驱动动画、渲染条件或 controller 状态。

```lua
animations:setMolangVar("v.roaming.emotion", 2)
animations:setMolangVar("v.show_car", 1)
animations:setMolangVar("v.weapon_mode", 1)
```

## YSM 模型：触发动作和事件

动作 id 取决于当前 YSM 模型配置。建议先用 `ysm_model:getActions()` 查看真实 id，再传给 `ysm_actions:trigger(...)`。

```lua
for _, id in ipairs(ysm_model:getActions()) do
  print("action", id)
end

ysm_actions:trigger("extra_animation.wave")
animations:setMolangVar("v.roaming.emotion", 2)
```

## YSM 调试读取

用于确认当前 Avatar 是否为 YSM 模型，以及 controller、动画、动作和控件是否已注册。

```lua
print("is ysm:", ysm_model:isYsm())

for controller, state in pairs(ysm_model:getControllerStates()) do
  print("controller", controller, state)
end

for id, info in pairs(ysm_model:getActiveAnimations()) do
  print("animation", id, info.name, info.time, info.weight)
end

for _, id in ipairs(ysm_model:getActions()) do
  print("action", id)
end

for _, id in ipairs(ysm_model:getControls()) do
  print("control", id)
end
```

## 使用约定

- `avatar_controls:set(id, value)` 是 avatar_console 的通用传值入口。
- 普通 Figura 模型负责自己把控件值应用到 `models.*`、`animations.*` 或其他 Lua 逻辑。
- YSM 模型建议绑定到 `v.*`、`v.roaming.*` 等 Molang 变量，由 YSM 原生 controller/function 消费。
- 控件 id 建议加命名空间前缀，例如 `console.*`、`ysm.console_*`，避免和 Avatar 内置控件冲突。
