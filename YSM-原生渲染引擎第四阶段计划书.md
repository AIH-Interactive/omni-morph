# YSM 原生渲染引擎第四阶段计划书：动画控制器支持

## 目标

第四阶段目标是让 Figura 原生 YSM 引擎支持 YSM/Bedrock 风格的 `animation_controllers`，补齐当前“只按动画名直接播放 clip”的能力缺口。重点解决部分 YSM 模型默认显示/隐藏部件不正确的问题，例如 `refs/15_kluonoa` 中光环、表情、车载附件、灯光等部件依赖控制器初始状态、条件动画和 `scale=0/1` 动画来控制可见性。

本阶段完成后，YSM 模型应能在不依赖转换成 Lua 脚本的前提下，原生执行：

- `ysm.json` 中声明的 `animation_controllers` 文件。
- controller 的 `initial_state`、`states`、`animations`、`transitions`。
- state 的 `on_entry`、`on_exit` Molang 表达式。
- 条件动画，例如 `{ "表情_疑惑": "v.roaming.emotion==1" }`。
- `q.all_animations_finished`、`ctrl.idle`、`ctrl.walk`、`ctrl.sleep` 等控制器查询。
- 通过默认/并行动画让附件初始隐藏或按变量显示。

## 当前代码状态

当前 Figura-YSM 已完成：

- YSM 几何解析与渲染。
- YSM 动画 JSON 解析到 `YsmAnimationClip`。
- `YsmAnimationPlayer` 可直接播放/停止 clip。
- 原生简化状态机：`idle / walk / run / sneak / swim / jump / fall / sleep` 等。
- extra_animation/轮盘/控件系统。
- 部分 `scale=0` base animation 默认隐藏逻辑。

当前缺口：

- `YsmAvatarLoader` 只打包 `animations` 和 `action_schemas`，没有把 `animation_controllers` 文件写入 NBT。
- `YsmModelRuntime.fromNbt` 只调用 `YsmAnimationParser.parse` 注册动画，不解析 controller。
- `YsmAnimationPlayer` 没有 controller runtime，只维护 `activeAnimations`。
- 默认隐藏附件如果依赖 controller 的 `initial_state` 或条件 state，不会正确执行。
- `ctrl.*`、`q.all_animations_finished` 等 controller 查询尚未系统化接入 Molang。

## 参考分析

### Sparkle-Morpher

Sparkle-Morpher 的相关结构可拆成四层：

- JSON 解析层：`JsonAnimationControllerUtils`
  - 解析 `initial_state`
  - 解析 `states`
  - 解析 state 内 `animations`
  - 解析 `transitions`
  - 解析 `sound_effects`
  - 解析 `on_entry` / `on_exit`
  - 解析 `blend_transition`

- 数据结构层：
  - `AnimationController`
  - `AnimationState`
  - state 保存 animations、transitions、entry/exit 表达式和 blend 配置。

- 执行层：
  - `PredicateBasedController`
  - `AnimationControllerInstance`
  - 每帧根据 predicate / transition 选择动画。
  - 支持 controller 上下文、transition、状态切换、表达式执行。

- 插槽绑定层：
  - `ControllerSlotBinder`
  - `PlayerAnimationController`
  - 按 `player.main / player.parallel_x / player.post_swing / player.fp_arm` 等命名绑定到不同 controller slot。

### refs/15_kluonoa

`refs/15_kluonoa/ysm.json` 中声明：

```json
"animation_controllers": [
  "controller/主动画修改.json",
  "controller/并行动画修改.json",
  "controller/车辆动画相关.json"
]
```

关键 controller 特征：

- `player.parallel_2`
  - `initial_state` 为隐藏光环状态。
  - 初始播放“光环隐藏”动画。
  - 后续根据 `ctrl.sleep` 切换开机、常态、关机。
  - 这类控制器正是默认隐藏附件的来源之一。

- `player.parallel_3`
  - `default` state 内包含多条条件动画：
    - `empty` 条件：`v.roaming.emotion==0 || ysm.rendering_in_inventory`
    - 其它表情动画条件：`v.roaming.emotion==1..5`
  - 说明控件变量不仅控制 UI，还应驱动 controller 条件动画。

- `player.parallel_5`
  - 默认 `empty`
  - 根据 `v.show_car && ysm.weather>=1 && ysm.is_open_air` 切换雨刮动画。

- `player.post_swing`
  - 攻击链 controller。
  - 使用 `on_exit` 重置 `v.swing_sword=0`。
  - 使用 `ctrl.jump`、`ctrl.idle` 判断具体攻击动画。

结论：仅靠当前 `YsmActionRuntime.trigger(animation)` 无法覆盖这些行为，必须引入 controller runtime。

## 阶段拆分

### 4.1 资源加载与 NBT 持久化

目标：把 YSM 包中的 controller JSON 带入 runtime。

改动点：

- `YsmManifestReader` / `YsmResourceIndex`
  - 确认并补齐 `animation_controllers` 路径读取。
  - 支持 ysm.json 中 `files.animation_controllers` 或等价字段。

- `YsmAvatarLoader`
  - 新增 `controllers` NBT list。
  - 每项保存：
    - `path`
    - `data`
  - 保持对 `.ysm` 包和文件夹模式一致。

- `YsmModelRuntime.fromNbt`
  - 在 `registerAnimations` 后解析 controllers。
  - controller 解析失败时降级，不阻断模型加载。

验收：

- `refs/15_kluonoa` 的三个 controller 文件可在 runtime 中枚举。
- 缺失 controller 文件只打印 warning，不崩溃。

### 4.2 Controller 数据结构

新增包建议：

```text
org.figuramc.figura.model.ysm.controller
```

新增类：

- `YsmAnimationController`
  - `String name`
  - `String initialState`
  - `LinkedHashMap<String, YsmControllerState> states`
  - controller 类型/slot 信息：`main / parallel / post_swing / fp_arm / vehicle / unknown`

- `YsmControllerState`
  - `String name`
  - `List<YsmControllerAnimationRef> animations`
  - `List<YsmControllerTransition> transitions`
  - `List<Expression> onEntry`
  - `List<Expression> onExit`
  - `YsmBlendTransition blendTransition`
  - `boolean blendViaShortestPath`

- `YsmControllerAnimationRef`
  - `String animation`
  - `Expression condition`

- `YsmControllerTransition`
  - `String targetState`
  - `Expression condition`

- `YsmControllerSlot`
  - `MAIN`
  - `PARALLEL`
  - `POST_SWING`
  - `FIRST_PERSON_ARM`
  - `VEHICLE`
  - `PROJECTILE`
  - `UNKNOWN`

验收：

- 能表示 `player.parallel_2` 的 initial_state 和多状态切换。
- 能表示 `player.parallel_3` 的同一 state 多条件动画。
- 能表示 `player.post_swing` 的 on_exit 和多 transition。

### 4.3 Controller JSON 解析器

新增：

- `YsmAnimationControllerParser`

支持 JSON：

- 根字段：
  - `format_version`
  - `animation_controllers`

- controller 字段：
  - `initial_state`
  - `states`

- state 字段：
  - `animations`
  - `transitions`
  - `on_entry`
  - `on_exit`
  - `blend_transition`
  - `blend_via_shortest_path`
  - `sound_effects`，第四阶段可先解析保存，播放放后续。

表达式解析：

- 复用当前 `Avatar.getMolangEngine().parse(...)`。
- 与 `YsmAnimationParser` 一致，解析失败降级为 null/false。

兼容格式：

```json
"animations": ["idle"]
```

```json
"animations": [
  { "empty": "v.roaming.emotion==0" },
  { "表情_疑惑": "v.roaming.emotion==1" }
]
```

```json
"transitions": [
  { "default": "q.all_animations_finished" }
]
```

验收：

- 能解析 `refs/15_kluonoa/controller/并行动画修改.json`。
- 能解析 `refs/15_kluonoa/controller/主动画修改.json`。
- 解析出的 controller/state 数量与 JSON 一致。

### 4.4 Controller Runtime

新增：

- `YsmControllerRuntime`
- `YsmControllerInstance`

核心职责：

- 初始化 controller 当前 state：
  - 优先 `initial_state`
  - 没有则 `default`
  - 没有 default 则第一个 state

- 每帧 update：
  - 执行当前 state 的条件动画。
  - 判断 transitions，按定义顺序命中第一个 true。
  - state 切换时执行：
    - old state `on_exit`
    - new state `on_entry`
  - 支持 `q.all_animations_finished`。

- controller slot 行为：
  - `main`：和当前原生状态机融合，后续逐步替代简化状态机。
  - `parallel_*`：并行动画，常驻执行。
  - `post_swing`：攻击/挥手链。
  - `vehicle`：车辆/坐骑相关。
  - unknown：先作为 parallel 运行，但记录 debug。

与 `YsmAnimationPlayer` 集成：

- `YsmAnimationPlayer` 增加：
  - `YsmControllerRuntime controllers`
  - `registerControllers(...)`
  - `controllers().update(state, entity, evaluator)`

- active animation 需要带来源：
  - native
  - action
  - controller name
  - controller state

建议新增：

```java
enum YsmAnimationSourceType {
    NATIVE,
    ACTION,
    CONTROLLER,
    BASE
}
```

验收：

- `player.parallel_2` 初始状态会播放“光环隐藏”。
- `player.parallel_3` 根据 `v.roaming.emotion` 切换表情动画。
- `on_exit` 可执行变量重置。

### 4.5 默认隐藏与可见性规则

当前问题：

- 有些部件默认应该隐藏，但模型加载后显示。
- 原因通常不是几何默认字段，而是 controller 初始状态播放了 `scale=0` 动画。

实现策略：

- Controller 初始化后，在第一帧渲染前执行一次 controller bootstrap。
- bootstrap 执行：
  - 每个 controller 进入 initial state。
  - 执行 initial state 的 `on_entry`。
  - 对 initial state 的无条件动画和条件为 true 的动画应用一次 t=0 pose。
  - 让 `scale=0` 立即影响 `YsmModelPart.visible`。

- `YsmAnimationPlayer.applyBaseHiddenDefaults()` 扩展为：
  - base hidden bones
  - controller hidden bones
  - action/runtime 临时显示覆盖

新增概念：

- `YsmVisibilityMask`
  - 记录 controller/base 推导出的默认隐藏骨骼。
  - 每帧 reset 后先应用默认隐藏，再由 active animation 显示。

验收：

- `refs/15_kluonoa` 车辆附件、光环、表情附件不再默认全部显示。
- 打开对应控件后，controller 条件触发的部件可显示。

### 4.6 Molang 查询与控制器变量

需要补齐的查询：

- `ctrl.idle`
- `ctrl.walk`
- `ctrl.run`
- `ctrl.sneak`
- `ctrl.sneaking`
- `ctrl.jump`
- `ctrl.fall`
- `ctrl.swim`
- `ctrl.sleep`
- `ctrl.attack`
- `q.all_animations_finished`
- `ysm.rendering_in_inventory`
- `ysm.weather`
- `ysm.is_open_air`

当前已有/相关：

- `Avatar.MolangContext`
- `AvatarControlRuntime.syncMolangValue`
- `YsmAnimationPlayer.selectState`

实现策略：

- 新增 `YsmControllerMolangBridge`
  - 每帧把当前 native state 写入 molang 变量或查询上下文。
  - controller evaluation 前更新：
    - 当前 movement state
    - 是否 inventory preview
    - 天气/露天状态
    - 是否动作播放完成

- `q.all_animations_finished`
  - 对当前 controller state 的 active animation refs 判断。
  - 全部非 loop 且 time >= length 为 true。

验收：

- `v.roaming.emotion==...` 能切表情。
- `ctrl.sleep` 能驱动光环开/关机。
- `q.all_animations_finished` 能让开机/关机过渡回常态/隐藏。

### 4.7 与第三阶段控件/轮盘系统联动

第三阶段已把 `extra_animation_buttons` 转成 Figura controls。

第四阶段要保证：

- 控件变化写入 `v.*` 后，controller 同帧或下一帧响应。
- 轮盘动作触发 controller 变量时能正确切换状态。
- 轮盘 action 与 controller animation 不互相错误覆盖。

规则建议：

- 控件变量优先驱动 controller。
- action 仍可直接播放 clip，但 action 播放前停止其它 action，不停止 parallel controller。
- 移动打断 action，但不打断 controller 常驻动画。
- controller 的默认隐藏优先级高于 action 的非相关骨骼。

验收：

- 控件页切换表情后预览模型立即变化。
- 车辆开关控件触发车体/车灯/雨刮状态。
- 轮盘动作播放结束后 controller 常驻状态恢复。

### 4.8 调试与 UI 支持

为降低后续修 bug 成本，新增调试面板信息：

- 当前 controller 列表。
- 每个 controller 当前 state。
- 当前 state 播放的动画。
- 命中的 transition。
- `q.all_animations_finished` 状态。
- 当前被默认隐藏的 bone 数量和名称过滤查看。

可放在：

- Avatar Controls 页面右侧 preview 下方。
- 或新增 Dev only 页面。

Lua API 建议：

- `ysm_controllers.get_controllers()`
- `ysm_controllers.get_state(name)`
- `ysm_controllers.set_state(name, state)`
- `ysm_controllers.reset(name)`
- `ysm_controllers.debug()`

### 4.9 测试计划

编译测试：

- `.\gradlew.bat :common:compileJava`
- `.\gradlew.bat :fabric:compileJava`

模型测试：

- `refs/15_kluonoa`
  - 初始加载附件隐藏正确。
  - 光环不应默认错误显示。
  - 表情控件切换有效。
  - 车辆控件切换有效。
  - 雨天/露天条件下雨刮 controller 可触发。

- 已知普通 YSM 模型
  - 无 controller 文件时不回退崩溃。
  - 旧 extra_animation 仍可使用。

回归测试：

- 第一人称手臂仍显示。
- 第三人称动作仍可播放。
- 轮盘动作互斥仍生效。
- 走动打断轮盘动作仍生效。
- 控件页无重叠。

## 实施顺序

1. 资源加载
   - `YsmResourceIndex` 支持 controller path。
   - `YsmAvatarLoader` 打包 controller JSON。
   - `YsmModelRuntime.fromNbt` 读取 controller NBT。

2. Parser
   - 新增 controller 数据结构。
   - 新增 `YsmAnimationControllerParser`。
   - 使用 `refs/15_kluonoa` 验证解析。

3. Runtime MVP
   - 实现 initial state。
   - 实现无条件/条件 animations。
   - 实现 transitions。
   - 实现 on_entry/on_exit。

4. 默认隐藏 MVP
   - controller bootstrap。
   - scale=0 默认隐藏 mask。
   - 修复 `15_kluonoa` 附件默认显示问题。

5. Molang 查询扩展
   - ctrl.*。
   - q.all_animations_finished。
   - ysm.rendering_in_inventory/weather/is_open_air。

6. UI/Debug
   - 控件页/预览页显示 controller 当前状态。
   - Lua debug API。

7. 联调与回归
   - `15_kluonoa`。
   - Sparkle-Morpher 行为对照。
   - 第一人称、轮盘、控件、动作互斥回归。

## 风险与注意事项

- 控制器和现有简化 native state machine 可能重复驱动同一骨骼，需要定义优先级。
- `scale=0` 不只是视觉隐藏，也可能用于附件启用/禁用；需要避免每帧 reset 后丢失隐藏。
- `q.all_animations_finished` 必须按 controller 当前 state 的动画集合判断，而不是全局 activeAnimations。
- 条件动画中 `empty` 是合法动画名，可能代表空动画/停用，不应报错。
- 中文动画名/状态名必须全程保留原始字符串，不能 normalize 成英文或小写。
- 第四阶段不要把 controller 转 Lua；仍以原生 runtime 为主，Lua 只提供调试和可选拦截。

## 完成标准

第四阶段完成时，应满足：

- `animation_controllers` 可被加载、解析、运行。
- `refs/15_kluonoa` 初始隐藏附件表现接近 Sparkle-Morpher。
- 控件变量能驱动 controller 条件动画。
- controller 状态切换、entry/exit、finished query 可用。
- 旧 YSM 模型、第三阶段轮盘/控件、第一人称手臂不回退。
- common/fabric 编译通过，并完成至少一轮实机验证。
