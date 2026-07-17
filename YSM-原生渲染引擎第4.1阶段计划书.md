# YSM 原生渲染引擎第 4.1 阶段计划书：完整函数包支持

## 目标

第 4.1 阶段的目标是把 YSM 模型包中的 `functions/*.molang` 做成完整、通用、可维护的运行时能力，而不是为某个模型、某个附件或某个变量写特例。

完成后，Figura 原生 YSM 引擎应支持 Sparkle-Morpher / Yes Steve Model 风格的函数包机制：

- 自动收集、打包、加载 `functions/*.molang`。
- 支持 `函数名@事件名.molang` 的双重语义：
  - `函数名` 可通过 Molang 中的 `fn.函数名(...)` 调用。
  - `事件名` 可作为 controller slot 事件入口执行，例如 `player_ctrl_parallel_6`。
- 支持纯事件文件，例如 `@player_ctrl_pre_main.molang`。
- 支持函数参数 `args`。
- 支持函数体中的多语句、赋值、`return`、变量读写与嵌套函数调用。
- 支持函数包驱动 controller、控件变量、附件默认隐藏、声音、键盘输入与同步变量。
- 与第三阶段控件/轮盘系统、第四阶段 animation controller runtime 保持一致的数据流。

本阶段要解决的代表性问题是：`refs/15_kluonoa` 中车辆、光环、表情等附件不应通过硬编码隐藏或显示，而应由模型自带的函数包、controller 与动画 scale 通道共同决定。

## 背景与当前问题

当前项目已经具备 YSM 原生渲染主链路：

- YSM 包识别与资源索引。
- 模型、贴图、动画、controller JSON 的打包与加载。
- YSM 动画 clip 解析与播放。
- animation controller 的初步解析与运行。
- 第三阶段控件/轮盘系统，可以把 UI 控件写入 Molang 变量。
- Molang 表达式运行时、变量存储、部分 `ctrl.*` / `ysm.*` 绑定。

但完整 YSM 模型并不只依赖 `animations` 与 `animation_controllers`。许多现代模型还依赖 `functions/*.molang` 做全局逻辑 glue code。`refs/15_kluonoa` 的车辆显示问题就是典型案例：

```molang
v.show_car = v.roaming.car && !(ctrl.tac_hold_gun || ctrl.playing_extra_animation || ctrl.carryon_is_princess);
```

`animations/主动画.json` 里再通过：

```json
"scale": "v.show_car&&v.anim_ctrl"
```

决定 `Car` 等骨骼是否显示。

如果函数包没有执行，`v.show_car`、`v.anim_ctrl` 等变量不会被正确设置；如果控件只写入 `v.roaming.car`，但函数包和 controller 没有接上，控件看起来就会“操作无效”。这类问题不能靠 `Car` / `show_car` / `roaming.car` 的硬编码解决，否则下一个模型会以另一个变量名、另一个骨骼名再次坏掉。

## Sparkle-Morpher 参考实现分析

### 资源层

Sparkle-Morpher 在 `RawYsmModel` 中把函数文件作为一等资源保存：

- `RawYsmModel.functionFiles`
- key 为函数文件名。
- value 保存 hash 与原始 `.molang` 数据。

文件夹模型读取时：

- 扫描 `functions/` 目录。
- 只接收 `.molang` 文件。
- 保存文件名而不是完整路径。

二进制模型读取/写入时：

- modern 格式先读取 sound files，再读取 function files，再读取 language files。
- function record 包含：
  - function name
  - hash
  - byte data

这说明函数包在 YSM 生态里不是附属脚本，而是模型包格式的一部分。

### 装配层

Sparkle-Morpher 的 `ModelAssemblyFactory` 把函数包拆成两个索引：

1. `buildMolangFunctions`
   - 遍历 extra resources 中的 functions。
   - 对文件名按 `@` 分割。
   - `foo@bar.molang` 注册为 `fn.foo`。
   - `@bar.molang` 不注册函数名，只作为事件。

2. `extractMolangEvents`
   - 同样遍历 functions。
   - `@` 后半部分注册为 event key。
   - event key 会转成小写。
   - 同一个 event 可对应多个 Molang 函数体。

这个设计非常关键：一个 `.molang` 文件既可以是普通函数，也可以是事件函数；事件函数不需要 controller JSON 显式引用，只要事件名匹配 slot 规则就会被执行。

### Controller slot 绑定层

Sparkle-Morpher 的 `ControllerSlotBinder` 同时扫描两类入口：

- controller 名：
  - `player.parallel_6`
  - `player.main`
  - `player.pre_main`
- Molang event 名：
  - `player_ctrl_parallel_6`
  - `player_ctrl_main`
  - `player_ctrl_pre_main`

事件名命中后，会被映射回 controller slot：

```text
player_ctrl_parallel_6 -> player.parallel_6
```

这解释了 `refs/15_kluonoa/functions/car_stuff@player_ctrl_parallel_6.molang` 的意义：它不只是一个可调用函数，而是挂在 `player.parallel_6` slot 更新前后的常驻逻辑。

### Molang 运行层

Sparkle-Morpher 的函数包依赖一整套 Molang 运行时能力：

- `fn.xxx(...)` 动态函数调用。
- `args` 参数访问。
- `v.*` / `variable.*` 持久变量读写。
- `t.*` / `temp.*` 临时变量。
- `ctrl.*` controller 状态、动作状态、工具函数。
- `ysm.*` 输入、声音、同步、环境、模型能力函数。
- 多语句表达式和 `return` 控制流。

因此 Figura 侧不能只把 `.molang` 文件读出来；必须把它放入正确的执行阶段，并提供足够的绑定。

## 与第三阶段、第四阶段的关系

### 第三阶段：控件/轮盘系统

第三阶段负责把 YSM 的 `extra_animation_buttons`、checkbox、radio、range 等配置转成 Figura controls，并同步到 Molang 变量，例如：

```text
control -> v.roaming.car
control -> v.roaming.emotion
control -> v.roaming.some_range
```

第 4.1 阶段必须保证这些变量能继续向后驱动函数包：

```text
控件值变化
  -> AvatarControlRuntime 写入 v.*
  -> functions/*.molang 读取 v.*
  -> 写入派生变量，例如 v.show_car
  -> controller / animation 条件读取派生变量
  -> scale/rotation/position 通道改变模型表现
```

如果函数包缺席，第三阶段控件只能改变原始变量，无法触发作者写在函数包里的派生逻辑。

### 第四阶段：Animation Controller

第四阶段负责 controller JSON 的解析、状态机、`on_entry` / `on_exit`、条件动画和 transition。

第 4.1 阶段与它的边界是：

- controller runtime 决定“当前哪个 state / 哪些 animation ref 生效”。
- function runtime 决定“每帧 controller slot 前后应执行哪些 Molang 逻辑”。
- 两者共享同一个 Molang context、变量存储和 animation player。

推荐执行顺序：

```text
同步实体/控件/环境查询
  -> 执行 player_ctrl_pre_main 事件
  -> 更新 main controller
  -> 按 slot 顺序执行 player_ctrl_parallel_* 事件
  -> 更新 parallel controllers
  -> 更新 post_swing / fp_arm / vehicle / projectile controllers
  -> 应用 animation player pose
  -> 派发动画 timeline event
```

MVP 可以先采用更简单顺序：

```text
同步实体/控件/环境查询
  -> 执行所有 player_ctrl_parallel_* 事件
  -> 执行 player_ctrl_pre_main 事件
  -> controller update
  -> animation update
```

但最终完整实现应回到 slot 绑定模型，避免事件与 controller slot 脱节。

## 当前项目状态

当前代码已经具备一个函数包 MVP：

- `YsmManifestReader` 已能收集 `functions/*.molang`。
- `YsmResourceIndex` 已记录 function paths。
- `YsmAvatarLoader` 已把 function 文件写入 avatar NBT。
- `YsmModelRuntime` 已加载函数包并在动画更新前调用。
- `YsmMolangFunctionRuntime` 已能：
  - 编译函数文件。
  - 按 `@` 拆分函数名与事件名。
  - 注册 `fn.xxx(...)`。
  - 执行 `player_ctrl_parallel*` / `player_ctrl_pre_main` 事件。
  - 提供 `args`。
- `MolangBindings` 已初步补充：
  - `fn`
  - `args`
  - `ctrl.set_animation`
  - `ctrl.set_beginning_transition_length`
  - `ysm.keyboard`
  - `ysm.sync`
  - `ysm.play_sound`
  - `ysm.stop_sound`
  - `ysm.first_order`
  - `ysm.second_order`

但这仍不是“完整函数包支持”，主要缺口如下：

- 事件执行还没有真正绑定到 controller slot 生命周期。
- 事件排序只覆盖了少数 `player_ctrl_*` 模式。
- 函数名、事件名、文件名规范还需要完整定义。
- `args` 生命周期、嵌套调用、临时变量隔离需要明确。
- `t.*` / `temp.*` 临时变量支持尚不完整。
- `ctrl.*`、`ysm.*`、`q.*` 绑定还缺少大量 YSM 常用能力。
- 声音、输入、同步、环境查询目前是 MVP 或 no-op。
- 函数报错缺少模型名、文件名、事件名、调用栈等可诊断信息。
- 缺少函数包级 debug UI 与回归测试样例。

## 设计原则

### 1. 不做模型特例

禁止在运行时硬编码：

- 具体骨骼名，例如 `Car`。
- 具体变量名，例如 `v.show_car`、`v.roaming.car`。
- 具体模型路径，例如 `15_kluonoa`。

所有行为必须从 YSM 包资源、Molang 表达式、controller、控件配置和动画通道自然推导。

### 2. 函数包是资源，不是 Lua 替代品

函数包应在 YSM runtime 内执行，不应转换成 Lua，也不应要求用户写 Lua glue code。

Lua API 可以提供调试和扩展入口，但不能成为 YSM 模型正确显示的必要条件。

### 3. 与 Sparkle-Morpher 行为兼容优先

文件名拆分、event key 规范、slot 映射、函数覆盖规则优先对齐 Sparkle-Morpher：

```text
functions/foo.molang
  -> fn.foo()

functions/foo@player_ctrl_parallel_6.molang
  -> fn.foo()
  -> event player_ctrl_parallel_6

functions/@player_ctrl_pre_main.molang
  -> event player_ctrl_pre_main only
```

### 4. 降级不崩溃

单个函数解析失败、执行失败、绑定缺失时：

- 模型不应整体加载失败。
- 记录 warning/debug 信息。
- 失败函数本帧跳过。
- 同包其他函数继续执行。

## 目标架构

### 资源索引层

涉及类：

- `YsmManifestReader`
- `YsmResourceIndex`
- `YsmAvatarLoader`
- `YsmPackage`

职责：

- 收集 `functions/*.molang`。
- 保留相对路径与文件名。
- 打包到 NBT：
  - `path`
  - `name`
  - `data`
  - 可选：`hash`
- `resource_index` 中记录 function paths，便于 debug。

建议补充：

- 对 zip / 文件夹包保持一致行为。
- 支持子目录：

```text
functions/foo.molang
functions/common/foo.molang
```

子目录函数名建议默认使用文件 stem，路径只用于 debug；如遇重名，按加载顺序后者覆盖或记录冲突，规则必须固定。

### 编译注册层

涉及类：

- `YsmMolangFunctionRuntime`
- `Avatar.getMolangEngine()`
- `ExpressionEvaluatorImpl`

建议内部结构：

```java
record YsmMolangFunction(
    String path,
    String fileName,
    String functionName,
    String eventName,
    List<Expression> expressions
) {}
```

运行时索引：

```text
functionsByName: Map<String, YsmMolangFunction>
eventsByName: Map<String, List<YsmMolangFunction>>
```

文件名解析规则：

```text
strip directories
strip .molang
split at first @
left  = function name
right = event name
```

规则细节：

- `foo.molang` 注册函数 `foo`。
- `foo@bar.molang` 注册函数 `foo` 和事件 `bar`。
- `@bar.molang` 只注册事件 `bar`。
- `foo@.molang` 只注册函数 `foo`，并记录 malformed event warning。
- `foo@bar@baz.molang` 以第一个 `@` 分割，事件名为 `bar@baz`，同时记录 warning，后续可考虑严格拒绝。
- event key 使用小写匹配。
- function name 保持原始大小写，同时可提供小写 fallback，避免模型作者大小写混用。

### 调用层

涉及 Molang 绑定：

- `fn`
- `args`
- `v` / `variable`
- `t` / `temp`

`fn.xxx(...)` 调用流程：

```text
Molang 访问 fn.xxx
  -> FnBinding 返回动态 Function
  -> YsmMolangFunctionRuntime.call("xxx", context, args)
  -> push args
  -> push call frame
  -> evalAll(function.expressions, allowReturn=true)
  -> pop call frame
  -> pop args
```

必须支持：

- 嵌套调用。
- 递归保护。
- 参数读取：
  - `args[0]`
  - `args.0`
  - `args.size` / `args.length`，如 Molang 语法可支持。
- 返回值：
  - 显式 `return expr` 返回 expr。
  - 无 return 时返回最后表达式值或 `0`，需要与当前 Molang engine 语义统一。

建议限制：

- 最大调用深度：32。
- 单帧函数执行预算：可先只 debug 统计，后续再硬限制。

### 事件层

事件执行不能只是“每帧全部扫一遍”。完整支持需要和 controller slot 绑定。

事件名规范：

```text
player_ctrl_main
player_ctrl_pre_main
player_ctrl_parallel
player_ctrl_parallel_1
player_ctrl_parallel_2
player_ctrl_post_swing
player_ctrl_fp_arm
vehicle_ctrl_main
projectile_ctrl_main
```

slot 映射：

```text
player_ctrl_parallel_6 -> player.parallel_6
player_ctrl_pre_main   -> player.pre_main
player_ctrl_main       -> player.main
```

建议新增：

- `YsmControllerSlot`
- `YsmFunctionEvent`
- `YsmFunctionEventBus`
- `YsmControllerSlotBinder`

事件执行点：

- `pre_main`：main controller 前。
- `main`：main controller tick 同步逻辑。
- `parallel_*`：对应 parallel controller 前。
- `post_swing`：攻击/挥手 controller 前后。
- `fp_arm`：第一人称手臂 controller 前后。
- `vehicle` / `projectile`：后续模型子实体支持阶段启用。

MVP 兼容策略：

- 如果 controller slot 尚不存在，但 event 存在，仍按稳定顺序执行。
- 如果 controller 与 event 都存在，event 必须绑定到对应 controller 的更新流程。

### Molang 绑定层

函数包完整支持依赖绑定完整度。建议按优先级补齐。

P0：影响 `refs/15_kluonoa` 附件/控件正确性的绑定：

- `fn.*`
- `args`
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
- `ctrl.playing_extra_animation`
- `ctrl.set_animation`
- `ctrl.set_beginning_transition_length`
- `ctrl.state_continue`
- `ctrl.state_break`
- `ysm.keyboard`
- `ysm.sync`
- `ysm.play_sound`
- `ysm.stop_sound`
- `ysm.stop_all_sounds`
- `ysm.first_order`
- `ysm.second_order`

P1：常用环境与物品查询：

- `ysm.weather`
- `ysm.is_open_air`
- `ysm.rendering_in_inventory`
- `ysm.food_level`
- `ysm.main_hand`
- `ysm.off_hand`
- `ysm.equipped_item`
- `ysm.is_local_player`
- `ysm.is_first_person`

P2：高级能力：

- `ysm.bone_param`
- `ctrl.hand_render`
- 车辆/投射物子实体相关 query。
- 网络同步节流与远端玩家变量同步。

### 变量作用域

需要明确变量生命周期：

- `v.*` / `variable.*`
  - avatar 持久变量。
  - 控件写入这里。
  - 函数包可读写。
  - 每帧不自动清空。

- `t.*` / `temp.*`
  - 单次函数调用或单帧临时变量。
  - 建议按函数调用 frame 隔离。
  - frame 结束自动释放。

- `args`
  - 函数调用参数栈顶。
  - 嵌套调用时覆盖，返回后恢复。

- `ctrl.*`
  - controller context 变量与 query。
  - `ctrl.set_animation` 等函数应作用于当前 controller slot，而不是全局乱播。

### 动画/可见性联动

函数包不直接隐藏骨骼。它通常写变量，再由 animation scale 通道驱动显示：

```text
function writes v.show_car
  -> animation scale expression reads v.show_car
  -> scale 0 hides part
  -> scale non-zero shows part
```

因此第 4.1 阶段必须与现有 `YsmAnimationPlayer` 可见性规则配合：

- 每帧先恢复模型默认可见性。
- 应用 base/controller/action 动画。
- 如果某骨骼 scale 通道求值为 0，则隐藏该骨骼子树或至少隐藏该 part。
- 如果后续动画把 scale 恢复为非 0，则应重新显示。
- 不把某个附件名放进 runtime 特例。

## 实施计划

### 4.1.1 资源与 NBT 完整化

任务：

- 确认 `YsmManifestReader.collectFunctions` 覆盖 zip 与文件夹包。
- `YsmResourceIndex` 保存 function paths。
- `YsmAvatarLoader` 写入：
  - `ysm.functions[].path`
  - `ysm.functions[].name`
  - `ysm.functions[].data`
  - 可选 `ysm.functions[].hash`
- `resource_index.functions` 用于 debug。

验收：

- `refs/15_kluonoa/functions/*.molang` 全部进入 NBT。
- 缺失或空函数文件只 warning，不阻塞模型加载。

### 4.1.2 函数文件解析规范化

任务：

- 抽出独立 parser：
  - `YsmMolangFunctionName`
  - `YsmMolangFunctionParser`
- 明确文件名拆分规则。
- 记录 function/event 冲突。
- 编译失败带上：
  - avatar/model id
  - function path
  - function name
  - event name

验收：

- 支持：
  - `foo.molang`
  - `foo@player_ctrl_parallel_6.molang`
  - `@player_ctrl_pre_main.molang`
- 函数名和事件名索引结果可 debug 输出。

### 4.1.3 `fn` 与 `args` 完整化

任务：

- `FnBinding` 支持任意 property 动态解析为函数。
- `args` 支持参数数组读取。
- 增加调用栈：
  - 当前函数名。
  - 当前事件名。
  - 当前 slot。
- 增加递归保护。
- 明确无返回值时的返回语义。

验收：

- 函数可调用另一个函数。
- 嵌套调用后 `args` 恢复正确。
- 未知函数返回 0 并记录 debug。

### 4.1.4 事件总线与 controller slot 绑定

任务：

- 新增 `YsmFunctionEventBus`。
- 将 `eventsByName` 从 function runtime 暴露给 controller runtime。
- 按 Sparkle-Morpher 规则映射：

```text
player_ctrl_parallel_6 <-> player.parallel_6
```

- controller 更新时调用对应 event。
- 没有 controller 的 event 按 legacy 顺序执行，保证兼容。

验收：

- `car_stuff@player_ctrl_parallel_6.molang` 在 `player.parallel_6` 逻辑阶段执行。
- `@player_ctrl_pre_main.molang` 在 main controller 前执行。
- 多个同名 event 按稳定顺序执行。

### 4.1.5 Molang 绑定补齐

任务：

- 梳理 Sparkle-Morpher 的 `ysm.*`、`ctrl.*` 函数。
- 按 P0/P1/P2 分层补齐。
- 对暂时无法实现的绑定提供 no-op + debug，而不是静默失败。
- `ctrl.set_animation` 从“全局播放 animation”升级为“当前 controller slot 设置 animation / state intent”。
- `ysm.sync` 从 no-op 升级为可记录同步键，并为后续网络同步留接口。

验收：

- `refs/15_kluonoa` 的车辆函数、声音函数、键盘函数不报错。
- 控件变化后函数派生变量在同帧或下一帧可见。

### 4.1.6 临时变量与作用域

任务：

- 补齐 `t.*` / `temp.*`。
- 函数调用 frame 中隔离临时变量。
- event 执行可共享“本事件 frame”的 temp。
- 每帧清理 frame-local temp。

验收：

- 函数中使用 `t.foo` 不污染下一帧。
- 嵌套函数不会破坏调用方 temp，除非 YSM 语义要求共享；若选择共享，需在文档里固定。

### 4.1.7 错误诊断与调试 UI

任务：

- Debug 日志输出：
  - 已加载函数数。
  - 已注册 event 数。
  - 每个 event 绑定到哪个 slot。
  - 函数执行异常。
- Avatar Controls 或 YSM debug 面板显示：
  - function list
  - event list
  - 最近执行事件
  - 当前 `v.*` 关键变量
  - 当前 controller state

验收：

- 用户报告“控件无效”时，可以看到是控件没写入、函数没执行、变量没派生、还是动画条件没命中。

### 4.1.8 回归测试与样例

任务：

- 建立最小测试模型或测试资源：
  - 普通函数调用。
  - event 函数。
  - `args`。
  - controller slot event。
  - scale=0 默认隐藏。
- 手工回归：
  - `refs/15_kluonoa`
  - `refs/Sparkle-Morpher` 内置 external YSM 样例。

命令：

```powershell
.\gradlew.bat :common:compileJava
.\gradlew.bat :fabric:compileJava
```

验收：

- common 编译通过。
- fabric 编译通过。
- 已知普通 YSM 模型无回归。
- 第三阶段控件/轮盘仍能正常写入变量。
- 第四阶段 controller 状态机仍能正常执行。

## 关键验收场景

### 场景 1：车辆默认隐藏

输入：

- `v.roaming.car = 0`
- 函数包包含 `car_stuff@player_ctrl_parallel_6.molang`
- 动画 scale 读取 `v.show_car && v.anim_ctrl`

期望：

- 函数包执行后 `v.show_car = 0`。
- 车辆相关骨骼 scale 为 0。
- 车辆默认不显示。

### 场景 2：车辆控件打开

输入：

- 用户在 controls 中打开车辆。
- `AvatarControlRuntime` 写入 `v.roaming.car = 1`。

期望：

- 下一帧函数包读取到 `v.roaming.car = 1`。
- 如无 `ctrl.tac_hold_gun`、`ctrl.playing_extra_animation`、`ctrl.carryon_is_princess` 等阻断条件，`v.show_car = 1`。
- 车辆 scale 动画恢复非 0。
- 车辆显示。

### 场景 3：纯事件文件

输入：

```text
functions/@player_ctrl_pre_main.molang
```

期望：

- 不注册空函数名。
- 注册事件 `player_ctrl_pre_main`。
- 在 main controller 前执行。

### 场景 4：普通函数调用

输入：

```text
functions/calc_flag.molang
functions/main@player_ctrl_parallel_1.molang
```

`main` 内调用：

```molang
v.flag = fn.calc_flag(args[0]);
```

期望：

- `fn.calc_flag` 可调用。
- `args` 在嵌套调用后恢复。
- 返回值正确写入 `v.flag`。

## 风险与注意事项

- 函数包事件执行顺序会影响模型表现，必须保持稳定。
- controller slot 绑定不能只靠字符串排序，应按 YSM/Sparkle-Morpher slot 语义排序。
- `ctrl.set_animation` 如果继续直接播放全局 animation，可能和 controller runtime 抢同一动画；需要收敛到 slot 局部语义。
- `ysm.sync` 牵涉网络同步，MVP 可以先本地 no-op，但必须留下可替换接口。
- `t.*` 临时变量作用域如果与 Sparkle-Morpher 不一致，复杂函数会出现幽灵状态。
- 多模型/远端玩家共享 Molang binding 时，必须保证函数 runtime 属于 avatar instance，不得全局共享变量。
- 中文函数名、中文动画名、中文 controller state 必须完整保留，不做英文化或不必要 lower-case；只对 event key 做匹配用 lower-case。

## 完成标准

第 4.1 阶段完成时，应满足：

- `functions/*.molang` 被完整收集、打包、加载、编译。
- `fn.xxx(...)`、`args`、嵌套调用和 `return` 可用。
- `@player_ctrl_*` 事件按 Sparkle-Morpher 规则接入 controller slot。
- 第三阶段控件变量能通过函数包派生变量并驱动动画。
- 第四阶段 controller runtime 能与函数包事件协同运行。
- `refs/15_kluonoa` 的车辆、光环、表情等附件不再依赖硬编码即可默认隐藏/按控件显示。
- 普通无函数包 YSM 模型不回归。
- common/fabric 编译通过。

