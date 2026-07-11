# YSM-原生渲染引擎第三阶段计划书：YSM 原生轮盘与动作系统升级

> 日期：2026-07-11  
> 范围：暂停当前第一人称渲染 bug 线，第三阶段优先规划 YSM 原生轮盘、动作触发、动作控制器、可扩展动作能力。

## 1. 阶段定位

第一阶段解决 YSM 模型接入，第二阶段推进原生渲染、动画、材质、挂点、Molang 查询和第一人称等基础能力。第三阶段的目标不是继续补单点渲染 bug，而是补齐 YSM 后续扩展动作所需要的“动作入口层”：

- 让 YSM 模型包可以声明自己的动作、额外动画、分类、默认轮盘布局和触发条件。
- 让 YSM 模型包可以声明轮盘控件，例如开关、滑条、枚举选择、颜色、文本/数值输入、按键绑定、按钮。
- 让玩家可以像 Sparkle-Morpher 一样用强轮盘系统调用 YSM 动作，而不是只能依赖 Figura 原有 Lua action wheel。
- 让轮盘动作可以进入 YSM 动画运行时、Molang 查询、输入状态、装备/武器状态、第一人称手臂动画、声音/粒子等后续扩展链路。
- 让 Figura Lua API 也能动态添加 YSM 轮盘控件，模型包默认声明和脚本扩展共用同一套控件运行时。
- 让原生 Figura avatar 也能通过 Lua 脚本使用这些控件；YSM 是模型包声明来源之一，而不是控件系统的唯一使用者。
- 保持现有 Figura 轮盘语义：轮盘格仍对应 `Action`，点击格子执行该 action 的 function；打开控件页也是一个 action function 的行为。
- UI 风格遵循 Figura 设置页面体系，避免引入 Sparkle-Morpher 那套独立玻璃风 UI。

第三阶段完成后，YSM 动作不再只是“能播放某段动画”，而是成为可配置、可持久化、可热键触发、可被 Molang 和 Lua 感知的统一动作系统。

## 2. 当前项目基线

### 2.1 Figura 现有轮盘

CodeGraph 显示当前 Figura 轮盘以 `org.figuramc.figura.lua.api.action_wheel.Page` 和 `Action` 为核心：

- `Page` 使用 `HashMap<Integer, Action>` 存动作。
- 每组 8 个槽位，`slotsShift` 用于翻页，`getGroupCount()` 按 8 槽分页。
- `keepSlots`、`newAction()`、`setAction()`、`getActions()` 等接口面向 Lua avatar 脚本。
- `ActionWheel.execute()` 会取当前页对应槽位的 `Action`，再调用 `action.execute(avatar, left)`。
- `ActionWheel` 主要服务 Figura Lua action wheel，缺少 YSM 模型包原生动作语义。

这套系统优点是稳定、已接入 Figura 权限和 Lua 生态；短板是：

- 动作来源依赖 Lua，YSM 模型包无法声明官方默认轮盘。
- 轮盘槽位、分类、热键、动画控制器之间没有 YSM 语义桥。
- 无 YSM extra animation 分类、模型默认布局、用户自定义布局合并逻辑。
- 无 YSM 风格的动作状态查询，例如当前动作是否播放、动作时间、动作槽位、控制器状态。
- 无通用轮盘控件模型，YSM 模型包或普通 Figura Lua avatar 都无法声明 slider/toggle/select/color/input/keybind 等可持久化控件。
- 现有 wheel 不需要直接理解控件类型；缺的是“Action function 打开通用控件页”的标准 API 和控件 runtime。

### 2.2 Figura 设置页面风格

`ConfigScreen` / `ConfigList` 体系使用：

- `AbstractPanelScreen` 作为设置页骨架。
- `ConfigList`、`CategoryWidget`、搜索框、底部 `Cancel` / `Done` 按钮。
- `UIHelper.blitSliced(... OUTLINE_FILL)`、滚动条、分类列表、配置控件。
- `ConfigType` 已有 Bool、Enum、String、Int、Float、Color、Folder/IP、Keybind、Button 等配置类型。

第三阶段的轮盘编辑器和 YSM 控件页应遵循这个风格：列表、分类、搜索、开关、输入绑定、下拉/分段控件、图标按钮，而不是复制 Sparkle-Morpher 的独立玻璃面板视觉。YSM 控件应优先复用或仿照这些现有元素，保证视觉和交互与 Figura 设置页一致。

### 2.3 当前 YSM 基础能力

当前项目已经具备第三阶段可复用的基础：

- `YsmAvatarLoader` / `YsmAvatarDetector`：识别并加载 YSM avatar。
- `YsmModelRuntime`：承载模型运行时状态。
- `YsmAnimationPlayer` / `YsmAnimationParser`：YSM 动画解析与播放基础。
- `YsmAttachmentPoint` / `YsmAttachmentType`：挂点基础。
- `QueryVariables`：Molang 查询扩展入口。
- `YsmModelAPI`：Lua 侧 YSM API 扩展入口。
- `YsmModelScanner` / validator：模型扫描与诊断基础。

第三阶段应该优先复用这些入口，不另起一套孤立系统。

## 3. Sparkle-Morpher 参考结论

### 3.1 轮盘数据与 UI

Sparkle-Morpher 相关文件：

- `UnifiedRouletteScreen`
- `CustomRouletteStore`
- `CustomRouletteLayout`
- `CustomRouletteGroup`
- `CustomRouletteEntry`
- `AnimationRouletteKey`
- `ExtraAnimationKey`

可借鉴能力：

- 模型默认 extra animation 作为根轮盘。
- `#group` 形式的分类子菜单。
- 每页 8 槽，超过 8 槽分页。
- 用户自定义布局覆盖模型默认布局。
- 轮盘热键打开/关闭。
- 额外动画槽位热键直接触发。
- 热键遇到子菜单时可直接打开对应子菜单。
- 自定义布局持久化到 per-model JSON。

不直接移植的部分：

- Sparkle-Morpher 的 `RouletteTheme` / `RoulettePanelStyle` 视觉风格。
- 直接 C2S 播放包的协议细节。
- 与 TLM、模型切换、服务器模型分发强绑定的行为。

### 3.2 动作控制器体系

Sparkle-Morpher 相关文件：

- `UnifiedPlayerActionController`
- `ModelActionProviderRegistry`
- `PlayerActionProvider`
- `YsmActionProvider`
- `VanillaHumanoidActionProvider`
- `HybridActionProvider`
- `ControllerSlotBinder`
- `FirstPersonArmAnimationController`
- `PlayerAnimationController`

可借鉴能力：

- 按模型动作配置选择 provider：YSM authored、vanilla fallback、hybrid fallback。
- 统一动作控制器 key：主姿态、vanilla fallback、cap/额外动画、GUI hover/focus。
- 控制器槽位绑定到动画数据 provider。
- 第一人称手臂控制器与第三人称玩家控制器分离。
- 动作系统不直接写死到单个渲染器，而是由 provider 构建控制器。

### 3.3 YSM Molang/输入/状态能力

Sparkle-Morpher 的 `YSMBinding` 和 `CtrlBinding` 显示了 YSM 侧可迁移能力面：

- 输入：键盘、鼠标、移动输入、左右手、第一/第三人称状态。
- 环境：天气、维度、亮度、生物群系、水中、空气、冰冻、梯子、乘坐、睡眠、潜行。
- 装备：头盔、胸甲、护腿、靴子、主手、副手、鞘翅。
- 武器：三叉戟、长枪、矛、重锤、攻击/使用/蓄力/突刺/坠落等状态。
- 玩家：饥饿、属性、肩上生物、贴图名、伤害时间、攻击时间。
- 控制器：是否播放额外动画、控制器状态、动作槽状态。
- 事件：声音、粒子、延迟执行、同步变量等能力入口。

第三阶段不需要一次性实现所有查询，但计划书必须按能力矩阵设计，避免后续继续补丁式扩展。

## 4. 第三阶段总目标

第三阶段最终交付一个“通用控件核心 + YSM 原生动作/轮盘适配层”：

1. YSM 模型包可以声明默认动作、分类、轮盘、热键建议、动画绑定和条件。
2. YSM 模型包可以声明默认控件，例如 toggle、slider、enum、color、text、number、keybind、button。
3. 普通 Figura avatar 可以通过 Lua API 动态声明同类控件，并挂到现有 action wheel 或新控件页。
4. 用户可以在 Figura 风格设置页面中编辑某个 avatar 的轮盘布局和控件值。
5. 运行时轮盘兼容 Figura 原有 8 槽交互：槽位仍执行 action function；控件页通过 action function 打开。
6. 动作触发进入统一 `YsmActionRuntime`，控件值进入通用 `AvatarControlRuntime` / YSM 适配的 `YsmControlRuntime`，共同驱动动画、Molang 变量、Lua 事件和可选效果。
7. 后续动作扩展只需要新增 action/control/effect/condition，而不是改 GUI、渲染器和动画播放器多处代码。

## 5. 核心架构设计

### 5.1 资源层：YSM 动作声明

新增 YSM 动作声明读取能力。建议支持以下来源，按优先级合并：

1. 用户 per-model 覆盖布局。
2. YSM 模型包内 `ysm.actions.json` 或 `action_wheel.json`。
3. YSM manifest / properties 中的 extra animation 字段。
4. 扫描动画文件自动生成的 fallback 动作。

建议 schema：

```json
{
  "version": 1,
  "actions": [
    {
      "id": "drink_wine",
      "title": "饮酒",
      "description": "播放饮酒动作",
      "icon": "item:minecraft:potion",
      "color": "#D45A8C",
      "category": "emote",
      "mode": "press",
      "animation": "drink_wine",
      "controller": "extra",
      "priority": 20,
      "cooldown": 10,
      "visible_when": "query.is_player",
      "enabled_when": "!query.is_sleep",
      "effects": [
        { "type": "sound", "id": "minecraft:item.honey_bottle.drink" }
      ]
    }
  ],
  "controls": [
    {
      "id": "ear_mode",
      "title": "耳朵模式",
      "type": "enum",
      "default": "auto",
      "options": ["auto", "folded", "raised"],
      "category": "appearance",
      "bind": "variable.ear_mode"
    },
    {
      "id": "tail_sway",
      "title": "尾巴摆动",
      "type": "slider",
      "default": 0.65,
      "min": 0.0,
      "max": 1.0,
      "step": 0.05,
      "bind": "variable.tail_sway"
    }
  ],
  "pages": [
    {
      "id": "root",
      "title": "YSM",
      "slots": ["drink_wine", "#combat", "open_controls", "quick_tail_toggle"]
    },
    {
      "id": "combat",
      "title": "战斗",
      "slots": ["attack_pose", "guard", "#return"]
    }
  ]
}
```

第一版不强制模型包使用新文件；必须兼容 Sparkle-Morpher/原 YSM 的 extra animation/classify 结构，并把它转换成内部 action/page。

控件 schema 第一版应覆盖 YSM 后续动作扩展最常用的配置：

- `toggle`：布尔开关，对应 Figura `BoolConfig` 风格。
- `slider`：浮点/整数滑条，带 min/max/step。
- `enum` / `select`：枚举选择，对应 Figura `EnumConfig` 风格。
- `color`：颜色选择/十六进制输入，对应 Figura `ColorConfig` 风格。
- `text` / `number`：文本、整数、浮点输入，对应 Figura `InputConfig` 风格。
- `keybind`：按键/鼠标绑定，对应 Figura `KeybindConfig` 风格。
- `button`：瞬时按钮，可触发 action/effect/lua callback。
- `separator` / `label`：仅展示控件，用于分组说明，运行时不产生状态。

### 5.2 数据层：新增动作模型

建议新增包：

`org.figuramc.figura.model.ysm.action`

核心类型：

- `YsmActionDefinition`：模型包声明的不可变动作定义。
- `YsmActionPageDefinition`：页面/分组/槽位声明。
- `YsmActionRegistry`：某个 YSM avatar 的动作定义索引。
- `YsmActionLayout`：当前生效布局，合并模型默认和用户覆盖。
- `YsmActionEntry`：轮盘槽位条目，可指向 action、submenu、return、empty。
- `YsmActionRuntime`：玩家本地动作状态机。
- `YsmActionState`：active、pressed、heldTicks、startedAt、cooldown、lastResult。
- `YsmActionTrigger`：wheel、hotkey、lua、script、auto condition。
- `YsmActionCondition`：Molang/Java 条件。
- `YsmActionEffect`：animation、sound、particle、texture、visibility、variable、lua event。
- `YsmActionContext`：avatar、player、runtime、hand、camera、input、target。
- `YsmControlDefinition`：模型包或 Lua 声明的控件定义。
- `YsmControlValue`：控件当前值，支持 bool、number、string、color、keybind。
- `YsmControlRuntime`：控件状态、变更事件、默认值、用户覆盖值。
- `YsmControlBinding`：控件值到 Molang variable / action condition / Lua callback 的绑定。
- `YsmControlWidgetFactory`：把控件定义映射到 Figura 风格 widget。

`YsmModelRuntime` 持有 `YsmActionRegistry` 和 `YsmActionRuntime`，避免动作状态散落到 GUI 或 mixin。

控件系统建议拆成两层：

- 通用层：`AvatarControlDefinition`、`AvatarControlRuntime`、`AvatarControlWidgetFactory`，供所有 Figura avatar 使用。
- YSM 层：`YsmControlDefinition`、`YsmControlRegistry`、`YsmControlBinding`，负责模型包 schema、Molang 绑定和 YSM action 条件。

这样原来的 Figura 模型可以只通过 Lua 注册控件，不需要引入 YSM runtime；YSM 模型则在通用控件基础上增加模型包默认声明、Molang 查询和动作控制器绑定。

控件和动作使用同一个 registry 命名空间，但 id 前缀分开：

- `action:<id>`：触发动作。
- `control:<id>`：控件定义和值，不直接要求 wheel 渲染为独立槽位类型。
- `open_controls:<page>`：一个生成出来的 action，function 是打开控件页。
- `#page`：进入子页。
- `#return`：返回上级。

重要约束：运行时 wheel 不应被改造成“识别所有控件类型的大型控件容器”。更贴合当前 Figura 的方式是：控件页入口表现为普通 `Action`，用户点击轮盘格时执行这个 action 的 function，然后打开控件页或执行某个快速控件操作。

### 5.3 UI 层：Figura 风格 YSM 轮盘

新增或改造：

- `YsmActionWheelScreen`：运行时轮盘。
- `YsmActionWheelConfigScreen`：轮盘编辑器。
- `YsmActionListWidget`：可用动作列表。
- `YsmActionPageListWidget`：页面/分类列表。
- `YsmActionSlotWidget`：槽位编辑控件。
- `YsmActionKeybindWidget`：动作热键编辑控件。

UI 原则：

- 遵循 `ConfigScreen` / `ConfigList` 视觉：设置页、列表、分类、搜索、底部按钮。
- 运行时轮盘可以保留圆形 8 槽交互，但颜色、按钮、提示和编辑入口使用 Figura UI 元素。
- 编辑器不做花哨玻璃风；用紧凑、可扫描、设置页一致的布局。
- 搜索动作、过滤分类、拖拽/按钮移动槽位、重置为模型默认。
- 支持根页、子页、分页、返回项、空槽、禁用状态显示。
- 每个动作显示：标题、图标、来源、绑定动画、触发模式、热键、条件状态。
- 控件显示：标题、当前值、默认值、来源、是否被 Lua 覆盖、是否持久化、绑定变量。

### 5.4 控件系统：YSM 模型包控件与 Lua 动态控件

第三阶段必须把“轮盘控件”作为一等能力，而不是把所有东西都伪装成动作按钮。控件用于后续动作扩展中的参数调节，例如表情强度、尾巴摆动、耳朵模式、武器姿态、动作循环模式、第一人称开关、特效强度等。

控件来源：

1. YSM 模型包声明的默认控件。
2. Lua API 动态添加的控件。
3. 用户在编辑页中的覆盖值。
4. 运行时临时控件状态。

控件类型：

- `toggle`：开关。
- `slider_int` / `slider_float`：整数/浮点滑条。
- `stepper`：小步进数值。
- `enum`：枚举/分段选择。
- `color`：颜色。
- `text`：文本。
- `keybind`：键鼠输入绑定。
- `button`：瞬时按钮。
- `action_ref`：引用一个动作，用于把动作嵌入控件页。
- `page_ref`：引用一个页面，用于控件页跳转。

控件运行时要求：

- 每个控件有默认值、当前值、用户覆盖值、临时值。
- 控件值可持久化，也可声明为 session-only。
- 控件变更触发 `onChange`，可通知 Molang/Lua/动作条件。
- 控件值可绑定到 `variable.<name>` 或 `query.ysm_control_value("<id>")`。
- 控件可设置 `visible_when` 和 `enabled_when`，条件使用同一套 Molang/Java condition。
- 控件可被 action effect 修改，例如动作触发后切换 toggle 或设置 enum。

UI 映射：

- `toggle` 对应 Figura 设置页开关风格。
- `slider` 使用 Figura 风格横向滑条；若当前没有通用 slider widget，第三阶段新增一个设置页一致的控件。
- `enum` 使用循环按钮或分段控件。
- `color` 使用颜色输入 + 色块预览。
- `text/number` 使用 `InputElement` 风格。
- `keybind` 使用 `KeybindElement` 风格。
- `button` 使用图标或短文本按钮。

Lua API 设计：

```lua
local controls = ysm_actions:get_controls()

controls:new_toggle("tail_enabled")
    :title("尾巴")
    :default(true)
    :bind("variable.tail_enabled")
    :on_change(function(value) end)

controls:new_slider("tail_sway")
    :title("摆动强度")
    :range(0, 1)
    :step(0.05)
    :default(0.6)
    :bind("variable.tail_sway")

controls:new_enum("ear_mode")
    :title("耳朵模式")
    :options({"auto", "folded", "raised"})
    :default("auto")

controls:new_button("reset_pose")
    :title("重置姿态")
    :on_press(function()
        ysm_actions:trigger("reset_pose")
    end)

-- 当前 Figura 轮盘格依然是 Action：点击格子执行 function，
-- 这个 function 可以打开控件页。
local page = action_wheel:newPage("YSM")
page:newAction()
    :title("外观设置")
    :item("minecraft:brush")
    :onLeftClick(function()
        avatar_controls:open_page("appearance")
    end)

page:newAction()
    :title("尾巴开关")
    :item("minecraft:lever")
    :onLeftClick(function()
        local value = not avatar_controls:get("tail_enabled")
        avatar_controls:set("tail_enabled", value)
    end)
```

Lua 动态控件规则：

- Lua 可以添加 client-only 控件。
- 普通 Figura avatar 也可以添加这些控件，不要求 avatar 是 YSM。
- Lua 可以读取模型包控件值。
- Lua 默认不能删除模型包控件，只能 hide/disable 或注册 override。
- Lua 动态控件可选择是否持久化；默认 session-only，避免脚本污染用户配置。
- Lua 控件需要遵循 Figura 权限和 avatar 生命周期，avatar 卸载时自动清理。
- 非 YSM avatar 的控件只能绑定 Lua callback / Lua variable / 通用 action wheel 状态；YSM 专属 Molang query 和 YSM animation controller 绑定只在 YSM runtime 存在时启用。

Molang 查询：

- `query.ysm_control("tail_sway")`
- `query.ysm_control_bool("tail_enabled")`
- `query.ysm_control_number("tail_sway")`
- `query.ysm_control_enum("ear_mode")`
- `query.ysm_control_changed("tail_sway")`

控件页与动作轮盘关系：

- 运行时轮盘仍以 8 槽为核心，槽位语义保持 action/function。
- 控件入口由 action function 打开，例如 `avatar_controls:open_page("appearance")`。
- 快速控件也由 action function 完成，例如点击格子切换 toggle、循环 enum、或打开 slider 页面。
- 复杂控件进入 Figura 风格控件面板；wheel 本身只负责调用函数。
- 编辑页中控件和值与动作布局一起展示，但持久化分开：layout 保存槽位，control profile 保存值。

### 5.5 输入层：轮盘键与槽位热键

新增配置项：

- `ysmActionWheelKey`：打开/关闭 YSM 轮盘。
- `ysmActionWheelLockKey`：锁定动作/动作轮盘状态，可选。
- `ysmActionSlot0` 到 `ysmActionSlot7`：根页 8 个槽位热键。
- `ysmActionModifier`：可选修饰键。

触发模式：

- `press`：按下触发一次。
- `hold`：按住保持 active，松开停止。
- `toggle`：按一次开启，再按关闭。
- `cycle`：同组动作轮换。
- `submenu`：进入子菜单。
- `return`：返回上一级。

输入适配：

- 键盘与鼠标按钮都要支持。
- 屏幕打开时由轮盘消费输入。
- 游戏内热键触发时必须检查 player ready、当前 avatar 是否 YSM、动作是否可用。
- 与 Figura 原 action wheel 避免冲突：如果当前 avatar 是 YSM 且存在 YSM actions，优先打开 YSM 轮盘；否则保持原 Figura action wheel。

### 5.6 动画控制器层

参考 Sparkle-Morpher 的 provider 体系，建立 Figura/YSM 版本：

- `YsmActionControllerProvider`
- `YsmAuthoredActionProvider`
- `YsmVanillaFallbackActionProvider`
- `YsmHybridActionProvider`
- `YsmControllerSlotBinder`

建议控制器槽位：

- `ysm.pose`：YSM authored 主姿态。
- `ysm.vanilla_fallback`：vanilla pose fallback。
- `ysm.extra`：轮盘/额外动作。
- `ysm.first_person_arm.left`
- `ysm.first_person_arm.right`
- `ysm.gui.hover`
- `ysm.gui.focus`

与 `YsmAnimationPlayer` 的连接：

- action 触发后调用统一 `playAction(actionId, context)`。
- 支持 layer/priority/blend/loop/hold。
- 支持动作结束回调，用于关闭 toggle/hold 状态。
- 支持 first-person arm 专用动画，不污染第三人称主体动画。
- 支持 GUI hover/focus 预览动作，后续可用于衣柜/模型选择界面。

### 5.7 Molang 查询与变量

新增 action/controller 查询，优先在 `QueryVariables` 接入：

- `query.ysm_action_active`
- `query.ysm_action_time`
- `query.ysm_action_cooldown`
- `query.ysm_action_slot`
- `query.ysm_action_group`
- `query.ysm_action_trigger`
- `query.ysm_controller_active`
- `query.ysm_controller_state`
- `query.ysm_first_person`
- `query.ysm_wheel_open`
- `query.ysm_control("<id>")`
- `query.ysm_control_changed("<id>")`

兼容 Sparkle-Morpher 思路的 `ctrl` 能力：

- `ctrl.playing_extra_animation`
- `ctrl.extra_animation_time`
- `ctrl.current_action`
- `ctrl.action_pressed`
- `ctrl.action_held`

YSMBinding 能力迁移优先级：

1. 输入和视角：keyboard、mouse、person_view、movement input。
2. 装备和手持：has_mainhand、has_offhand、has_armor、weapon_type。
3. 动作和攻击：swinging、attack_time、using_item、charged_crossbow。
4. 环境状态：water、sleep、sneak、passenger、ladder、light。
5. 高级武器兼容：trident、lance、spear、mace 等细分状态。
6. 声音、粒子、defer、sync 等事件能力。

### 5.8 Lua API 兼容与扩展

新增 `ysm_actions` Lua API，同时给普通 Figura avatar 提供通用控件入口，保持 Figura 原 `action_wheel` 不破坏：

- `ysm_actions:get_actions()`
- `ysm_actions:get_pages()`
- `ysm_actions:trigger(id)`
- `ysm_actions:set_active(id, bool)`
- `ysm_actions:is_active(id)`
- `ysm_actions:get_time(id)`
- `ysm_actions:set_visible(id, bool)`
- `ysm_actions:set_enabled(id, bool)`
- `ysm_actions:on_trigger(function)`
- `ysm_actions:get_controls()`
- `ysm_actions:new_toggle(id)`
- `ysm_actions:new_slider(id)`
- `ysm_actions:new_enum(id)`
- `ysm_actions:new_color(id)`
- `ysm_actions:new_text(id)`
- `ysm_actions:new_keybind(id)`
- `ysm_actions:new_button(id)`
- `ysm_actions:get_control_value(id)`
- `ysm_actions:set_control_value(id, value)`

普通 Figura avatar 的建议入口：

- `action_wheel` 继续负责 page/action/slot。
- 控件建议通过 action 的 function 打开，例如 `avatar_controls:open_page(pageId)`。
- 不建议把 `new_slider` 等直接挂在 `action_wheel` 上作为槽位类型，以免破坏当前“格子调用函数”的模型。

新增更中性的 API：

- `avatar_controls:new_toggle(id)`
- `avatar_controls:new_slider(id)`
- `avatar_controls:new_enum(id)`
- `avatar_controls:new_color(id)`
- `avatar_controls:new_button(id)`
- `avatar_controls:open_page(id)`
- `avatar_controls:get(id)`
- `avatar_controls:set(id, value)`

最终命名在实现前确认，但原则是：控件核心不能只暴露在 `ysm_actions` 下，否则普通 Figura avatar 会被排除在外；同时控件不应强行变成 ActionWheel 的新槽位类型，而应该由现有 action function 打开。

桥接策略：

- 原 Figura Lua action wheel 继续可用。
- YSM action 可以生成临时 `Action` 适配到旧轮盘，也可以由新 `YsmActionWheelScreen` 原生渲染。
- Lua 自定义动作可以注册到 YSM action registry，但默认不能覆盖模型包动作 id，除非显式开启 override。
- Lua 自定义控件可以注册到 YSM control registry；默认不覆盖模型包控件，除非显式声明 override，并且必须能在 avatar 卸载时清理。
- 普通 Figura avatar 的 Lua 自定义控件注册到通用 avatar control registry；如果当前 avatar 不是 YSM，不创建 YSM action/runtime，也不暴露 YSM 专属查询。

### 5.9 持久化

参考 Sparkle-Morpher `CustomRouletteStore`，新增 Figura/YSM 版本：

- `YsmActionLayoutStore`
- 存储目录建议：`config/figura/ysm_action_wheel/<avatarId>.json`
- JSON 内容：
  - model/avatar id
  - root entries
  - groups/pages
  - slot hotkeys override
  - hidden actions
  - renamed labels
  - icon/color override
  - control values
  - control visibility/enabled overrides
  - schema version

要求：

- 原子写入，避免配置损坏。
- 加载失败回退模型默认布局。
- 资源包更新后尽量保留用户布局，失效动作标记为 missing，而不是直接删除。
- 提供“重置当前页”“重置整个模型布局”。
- 提供“重置控件值”“仅重置 Lua 临时控件”“导出/导入控件 profile”。

### 5.10 权限与安全

第三阶段必须控制动作能力边界：

- 客户端本地动作默认允许：动画、UI 状态、Molang 变量、客户端声音/粒子。
- 影响服务器状态的动作默认禁止，除非走 Figura 权限或明确网络协议。
- 模型包声明的声音/粒子需要限频。
- 用户热键不能被模型包强行绑定，只能提供 suggested key。
- 模型包控件可以提供默认值，但不能强制覆盖用户持久化选择，除非 schema version 明确迁移。
- Lua 动态控件默认 session-only，持久化需要显式声明。
- Lua 注册动作遵循现有 Figura 权限体系。
- 远端玩家动作同步后续阶段再做，不阻塞本地轮盘。

## 6. 实施里程碑

### M1：动作资源与扫描

目标：先让系统知道 YSM 模型支持哪些动作。

- 新增 action schema 解析。
- 新增 control schema 解析。
- 从 extra animation/classify 生成默认 action/page。
- 扩展 `YsmModelScanner`，输出 actions/pages/controllers 诊断。
- 扩展 `YsmModelScanner`，输出 controls/control bindings 诊断。
- `YsmModelValidator` 校验 animation id、重复 action id、非法 submenu、缺失 icon。
- `YsmModelValidator` 校验 control id、类型、默认值范围、绑定变量、条件表达式。

完成标准：

- 扫描 YSM 包时能列出所有可用动作。
- 没有新 schema 的旧 YSM 包也能生成默认轮盘。
- 带控件 schema 的 YSM 包能列出控件定义和默认值。
- 无效动作不会导致模型加载失败，只产生 warning。
- 无效控件不会导致模型加载失败，只产生 warning 并禁用该控件。

### M2：运行时 Registry 与 State

目标：把动作状态从 UI 中抽离出来。

- 新增 `YsmActionRegistry`、`YsmActionRuntime`。
- 新增 `YsmControlRegistry`、`YsmControlRuntime`。
- `YsmModelRuntime` 持有动作 registry/runtime。
- `YsmModelRuntime` 持有控件 registry/runtime。
- 支持 trigger、hold、toggle、cooldown。
- 支持控件默认值、当前值、用户覆盖值、onChange。
- 暴露基础查询给 `QueryVariables`。

完成标准：

- 不打开 UI，也能通过调试/API 触发动作。
- 动作状态可被 Molang 查询读取。
- 控件值可被 Molang 查询读取。
- 切换 avatar 后 runtime 正确重建。
- Lua 动态控件随 avatar 生命周期正确清理。

### M3：Figura 风格轮盘编辑器

目标：做用户可用的配置界面。

- 新增 `YsmActionWheelConfigScreen`。
- 使用 `ConfigScreen` 风格布局：搜索、分类列表、动作列表、槽位编辑、底部按钮。
- 增加控件页：控件列表、控件值、来源、持久化状态、重置按钮。
- 支持添加、移除、移动、重命名、重置、保存布局。
- 支持 group/page 编辑。
- 支持用户编辑控件值，但模型包只读控件定义不可被误删。

完成标准：

- 用户能为某个 YSM 模型定制根页和子页。
- 用户能为某个 YSM 模型调整控件值。
- 保存后重进游戏仍保留布局。
- 保存后重进游戏仍保留控件 profile。
- 加载失败可回退模型默认布局。
- 控件值加载失败可回退默认值。

### M4：运行时 YSM 轮盘

目标：实际可在游戏内使用。

- 新增/改造 `YsmActionWheelScreen`。
- 支持 8 槽、分页、子菜单、返回、禁用态、缺失态。
- 控件页入口生成普通 action；点击轮盘格执行 function 打开控件页。
- 简单控件快捷操作也生成普通 action，例如 toggle/enum cycle。
- 接入 wheel key、slot key。
- 与原 Figura action wheel 做选择逻辑：YSM avatar 优先 YSM wheel，非 YSM avatar 走原 wheel。

完成标准：

- YSM 模型包动作能从轮盘触发。
- 热键能触发根页槽位。
- 热键触发到控件入口 action 时能打开控件页或执行快捷控件操作。
- 子菜单和分页行为稳定。

### M5：动画控制器接入

目标：轮盘动作真正驱动 YSM 动画系统。

- 新增 action controller provider。
- action effect `animation` 接入 `YsmAnimationPlayer`。
- 支持优先级、循环、hold/toggle 停止、blend。
- 第一人称手臂动作预留独立 controller。

完成标准：

- extra animation 可通过轮盘播放。
- hold/toggle 动作能正确开始/停止。
- 第一人称和第三人称动作槽位不会互相污染。

### M6：YSM 能力矩阵迁移

目标：逐步补齐 Sparkle-Morpher/YSM 支持的动作相关查询。

优先顺序：

1. 输入、视角、移动。
2. 装备、手持、攻击/使用。
3. 动作/controller/control 状态。
4. 环境状态。
5. 武器细分兼容。
6. 声音、粒子、defer/sync 事件能力。

完成标准：

- 常见 YSM 轮盘动作表达式无需 Lua 补丁即可运行。
- Scanner 能报告未支持 query。
- 未支持能力有清晰 fallback，而不是静默错误。

### M7：Lua API 与兼容层

目标：让 Figura 用户可以继续脚本化扩展 YSM 动作。

- 新增 `ysm_actions` API。
- 新增 `ysm_controls` 或 `ysm_actions:get_controls()` API。
- YSM action 与原 `action_wheel.Action` 互通。
- YSM control 与 Figura 设置页控件风格互通。
- Lua 可监听 YSM action trigger。
- Lua 可追加 client-only action。
- Lua 可追加 client-only control。

完成标准：

- 旧 avatar 不受影响。
- YSM avatar 可以同时使用模型包默认动作和 Lua 扩展动作。
- YSM avatar 可以同时使用模型包默认控件和 Lua 扩展控件。
- 权限检查明确。

### M8：测试、诊断与文档

目标：让后续 bug 修复有工具支撑。

- 扩展 scan task，输出 action report。
- 扩展 scan task，输出 action/control report。
- 增加 debug dump：actions、controls、pages、layout source、control profile、runtime states、active controllers。
- 增加示例 YSM action/control schema。
- 写用户文档：如何声明动作、如何声明控件、如何用 Lua 添加控件、如何定制轮盘、如何绑定热键。

完成标准：

- 一个 YSM 包的轮盘问题可以通过 dump 快速定位。
- 用户能照文档写出最小动作包。
- 用户能照文档写出最小控件包，并通过 Lua 动态加控件。
- 编译通过，旧轮盘回归正常。

## 7. YSM 能力迁移矩阵

| 能力 | 第三阶段处理方式 | 优先级 |
| --- | --- | --- |
| extra animation 根轮盘 | 转成 `YsmActionPageDefinition(root)` | P0 |
| extra animation classify | 转成子菜单/page | P0 |
| 每页 8 槽分页 | 保持兼容 | P0 |
| 自定义布局 | `YsmActionLayoutStore` | P0 |
| 轮盘键打开/关闭 | 新 key mapping | P0 |
| 槽位热键 | 0-7 slot key | P0 |
| press/hold/toggle/cycle | `YsmActionRuntime` | P0 |
| YSM 控件声明 | `YsmControlDefinition` / schema parser | P0 |
| Lua 动态控件 | `avatar_controls` / `action_wheel` / `ysm_actions:get_controls()` | P0 |
| 普通 Figura avatar 控件 | 通用 `AvatarControlRuntime` | P0 |
| 控件值持久化 | `YsmControlProfileStore` | P0 |
| toggle/slider/enum/color/text/keybind/button | Figura 风格 widget factory | P0 |
| 动画播放 | `YsmAnimationPlayer` effect | P0 |
| action/controller Molang 查询 | `QueryVariables` | P0 |
| control Molang 查询 | `QueryVariables` | P0 |
| 输入/视角查询 | 迁移 YSMBinding 子集 | P1 |
| 装备/武器查询 | 迁移 YSMBinding 子集 | P1 |
| 第一人称手臂动作槽 | 独立 controller | P1 |
| 声音/粒子事件 | action effects | P1 |
| Lua action 扩展 | `ysm_actions` API | P1 |
| defer/sync | 先本地 defer，sync 后续 | P2 |
| 服务器广播动作 | 后续网络阶段 | P2 |
| 第三方武器 mod 兼容 | 逐个 compat 后续接入 | P2 |

## 8. 与当前 bug 修复线的边界

当前第一人称 matrices、YSM 手臂遮挡等修复暂时放下。第三阶段计划只定义动作轮盘系统，不直接改：

- `ItemInHandRendererMixin`
- `PlayerRendererMixin`
- `LivingEntityRendererMixin`
- 平台 `LevelRendererMixin*`
- 当前第一人称矩阵修复逻辑

但第三阶段会为后续第一人称动作留出接口：

- `ysm.first_person_arm.left`
- `ysm.first_person_arm.right`
- action context 中记录 camera/hand/item 状态
- 第一人称动作 effect 可选择只作用于 arm controller

这样后续统一修 bug 时，第一人称问题可以接入更清晰的动作/controller 框架。

## 9. 风险与控制

- 范围过大：先完成本地轮盘和动作 runtime，服务器同步延后。
- UI 复杂：运行时轮盘保持 8 槽，编辑器走 Figura 设置页列表，不做大型新设计系统。
- Lua 冲突：原 action wheel 不改语义，YSM wheel 只在 YSM avatar 且存在 actions 时优先。
- 动作定义分裂：所有来源先统一转换成 `YsmActionDefinition`。
- Molang 缺口：scanner 必须报告 unsupported query，避免用户误以为动作坏了。
- 权限风险：模型包不能强绑用户热键，不能默认执行服务器影响动作。

## 10. 最终验收清单

- 非 YSM avatar 的 Figura action wheel 行为不变。
- YSM avatar 能自动生成默认动作轮盘。
- YSM 模型包能声明动作、分类、默认布局、动画绑定。
- 用户能在 Figura 风格设置页编辑并保存轮盘。
- 根页 8 槽热键可直接触发动作。
- 子菜单、分页、返回、禁用态、缺失态行为正确。
- 动作能驱动 `YsmAnimationPlayer`。
- Molang 能读取动作状态和基础输入/装备状态。
- Lua 能查询和触发 YSM actions。
- Scanner/dump 能定位动作、布局、动画引用和 unsupported query 问题。
- 当前已完成的第二阶段模型加载、材质、动画基础能力不回退。

## 11. 建议文件落点

新增：

- `common/src/main/java/org/figuramc/figura/model/ysm/action/YsmActionDefinition.java`
- `common/src/main/java/org/figuramc/figura/model/ysm/action/YsmActionPageDefinition.java`
- `common/src/main/java/org/figuramc/figura/model/ysm/action/YsmActionRegistry.java`
- `common/src/main/java/org/figuramc/figura/model/ysm/action/YsmActionRuntime.java`
- `common/src/main/java/org/figuramc/figura/model/ysm/action/YsmActionLayout.java`
- `common/src/main/java/org/figuramc/figura/model/ysm/action/YsmActionLayoutStore.java`
- `common/src/main/java/org/figuramc/figura/model/ysm/action/YsmControlDefinition.java`
- `common/src/main/java/org/figuramc/figura/model/ysm/action/YsmControlRegistry.java`
- `common/src/main/java/org/figuramc/figura/model/ysm/action/YsmControlRuntime.java`
- `common/src/main/java/org/figuramc/figura/model/ysm/action/YsmControlProfileStore.java`
- `common/src/main/java/org/figuramc/figura/model/ysm/action/YsmControlWidgetFactory.java`
- `common/src/main/java/org/figuramc/figura/avatar/control/AvatarControlDefinition.java`
- `common/src/main/java/org/figuramc/figura/avatar/control/AvatarControlRuntime.java`
- `common/src/main/java/org/figuramc/figura/avatar/control/AvatarControlProfileStore.java`
- `common/src/main/java/org/figuramc/figura/avatar/control/AvatarControlWidgetFactory.java`
- `common/src/main/java/org/figuramc/figura/model/ysm/action/YsmActionControllerProvider.java`
- `common/src/main/java/org/figuramc/figura/gui/screens/YsmActionWheelConfigScreen.java`
- `common/src/main/java/org/figuramc/figura/gui/YsmActionWheelScreen.java`
- `common/src/main/java/org/figuramc/figura/lua/api/ysm_actions/YsmActionsAPI.java`
- `common/src/main/java/org/figuramc/figura/lua/api/ysm_actions/YsmControlsAPI.java`
- `common/src/main/java/org/figuramc/figura/lua/api/avatar_controls/AvatarControlsAPI.java`

扩展：

- `YsmManifestReader`
- `YsmModelRuntime`
- `YsmAnimationPlayer`
- `YsmModelScanner`
- `YsmModelValidator`
- `QueryVariables`
- `YsmModelAPI`
- key mapping / client input 注册入口

## 12. 推荐推进顺序

1. 先写 action schema parser 和 extra animation 转换器。
2. 同步写 control schema parser 和控件默认值模型。
3. 再写 action/control runtime registry/state，不碰 UI。
4. 接入 scanner/dump，确保每个模型能看清动作和控件数据。
5. 做 Figura 风格配置页，保存/加载自定义布局和控件 profile。
6. 做运行时轮盘、control 槽位和热键触发。
7. 接动画播放器和 controller provider。
8. 补 Molang action/controller/control/input 查询。
9. 最后补 Lua API、声音/粒子、文档和回归。

这条顺序能保证每一步都有可验证产物，也能避免一开始就陷入 GUI 和渲染细节。
