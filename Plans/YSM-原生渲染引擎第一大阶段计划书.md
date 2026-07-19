# YSM 原生渲染引擎第一大阶段计划书：单人未加密 YSM 完整可用

> 范围：整合前序第一、第二、第三、第四、4.1 阶段计划与当前实现进度，对齐 Sparkle-Morpher 的 YSM 单机体验。  
> 目标版本线：Figura `26.1.x` / Fabric 26.x submit/extraction 渲染管线。  
> 核心目标：不依赖加密模型解析、不依赖 SIMD/native 加速、不引入 Sparkle-Morpher 的模型/动作同步协议，让单人模式下的未加密 YSM 模型尽可能达到 Sparkle-Morpher 的完整使用体验。

## 1. 阶段定位

第一大阶段不是单一“静态模型加载”阶段，而是把前序小阶段统一收束为一个可验收的大目标：

- 在 Figura 衣柜中识别、加载、切换未加密 YSM 模型。
- 原生解析 YSM 几何、贴图、动画、controller、functions、extra animation、controls、projectile/vehicle 资源。
- 进入游戏后用 YSM 原生运行时替换原版玩家模型和 Figura 模型渲染。
- 在单人模式下让 YSM 模型的身体、贴图、动作、轮盘、控件、附件显隐、第一人称、手持物品、投射物/载具静态或基础动画表现尽量对齐 Sparkle-Morpher。
- 保持 Figura 原有 Avatar、Lua、权限、后端和本地生命周期，不把 Figura 改造成 Sparkle-Morpher 的服务器模型加载器。

第一大阶段完成后，用户应能把已经解包的 YSM 文件夹或 zip 放入 Figura 本地 Avatar 目录，在单人模式中完成正常游玩、动作触发、控件切换、第一/第三人称观察和基础调试。

## 2. 明确排除项

以下内容不属于第一大阶段，即使 Sparkle-Morpher 已支持，也不在本阶段强行对齐：

1. 模型加密与 `.ysm` 二进制解密
   - 不实现 YSMParser/OpenYSM 加密包完整解密。
   - 不要求导入加密 `.ysm` 文件。
   - 仅支持已经解包的未加密目录或 zip。
   - 代码中只预留 `YsmPackage` / `YsmResourceIndex` 后续接入点。

2. SIMD/native/GPU 加速
   - 不迁移 Sparkle-Morpher 的 native 压缩、音频或渲染加速路径。
   - 不要求 ZSTD native、SIMD skinning、GPU bone queue。
   - 第一大阶段优先正确性和可维护性，CPU 矩阵计算可接受。

3. 模型和动作同步
   - 不迁移 Sparkle-Morpher 的 Minecraft 服务器 C2S/S2C 模型同步、玩家状态同步、动画表达式广播和模型分片上传。
   - 不要求游戏服务器安装 Figura/YSM 服务端 Mod。
   - 不改变 Figura “中心后端 + HTTP Avatar 资产 + WebSocket 订阅事件 + 受限 Lua ping”的基本架构。
   - YSM 动作若后续需要多人可见，应走 Figura 既有 ping/backend 权限和限流体系，另开后续阶段。

这些排除项之外，Sparkle-Morpher 对 YSM 单人体验有价值的行为都需要在本计划中给出对齐方式或差异处理。

## 3. 当前完成进度基线

结合前序计划和当前代码结构，第一大阶段已有基础如下：

- YSM 包识别与资源索引：
  - `YsmAvatarDetector`
  - `YsmManifestReader`
  - `YsmResourceIndex`
  - `YsmPackage`
  - `YsmAvatarLoader`
- 贴图与衣柜：
  - `YsmTextureOption`
  - `YsmTextureSelectionStore`
  - 文件夹/zip 贴图读取和持久化选择。
- 原生模型运行时：
  - `YsmModelRuntime`
  - `YsmGeometryParser`
  - `YsmRenderer`
  - `YsmModelPart`
  - `YsmRenderPass`
- PartMask、Locator 和挂点：
  - `YsmBoneRole`
  - `YsmBoneMapper`
  - `YsmBoneClassifier`
  - `YsmLocator`
  - `YsmPartMask`
  - `YsmAttachmentPoint`
  - `YsmAttachmentType`
- 动画与 controller：
  - `YsmAnimationParser`
  - `YsmAnimationPlayer`
  - `YsmAnimationClip`
  - `YsmAnimationChannel`
  - `YsmAnimationEvent`
  - `YsmControllerRuntime`
  - `YsmAnimationControllerParser`
  - `YsmControllerSlotBinder`
- 轮盘与控件：
  - `YsmActionRuntime`
  - `YsmActionDefinition`
  - `YsmActionSchemaParser`
  - `YsmActionWheelLayoutStore`
  - 通用 `AvatarControlDefinition` / control runtime 接入。
- 函数包：
  - `YsmMolangFunctionRuntime`
  - `YsmMolangFunctionParser`
  - `YsmMolangFunctionName`
  - `YsmFunctionEventBus`
- 子实体资源：
  - `YsmSubEntity`
  - projectile/vehicle 资源已进入运行时基础结构。
- Lua：
  - `lua/api/ysm_model/YsmModelAPI`
  - YSM 骨骼访问和部分运行时调试入口。

当前状态已经超过原始第一阶段静态闭环，进入了 Sparkle-Morpher 运行时语义对齐阶段。但仍需要系统化补齐 slot、binding、transition、第一人称、子实体动画、诊断工具和回归测试，避免后续继续靠单模型硬编码修复。

## 4. Sparkle-Morpher 对齐原则

### 4.1 直接对齐的能力

这些能力属于 YSM 单人体验核心，应尽量对齐 Sparkle-Morpher：

- YSM 文件夹/zip 内容识别。
- 新版 `ysm.json` 与旧版 `main.json` 结构兼容。
- player main/arm 模型、贴图、animation、animation_controller、functions、sound、language、projectile、vehicle 资源索引。
- Bedrock geometry 骨骼、cube、UV、pivot、rotation 解析。
- PartMask 与 Locator 语义。
- `extra_animation`、`extra_animation_classify`、`extra_animation_buttons` 转换为动作轮盘和控件。
- animation clip 的 position/rotation/scale/timeline/sound/particle 基础执行。
- `pre_parallel` / `parallel` 默认隐藏与 extra 动作非零 scale 显示。
- animation controller 的 `initial_state`、`states`、`animations`、`transitions`、`on_entry`、`on_exit`。
- `functions/*.molang` 的 `fn.xxx(...)`、`@player_ctrl_*` 事件、`args`、多语句、return。
- Molang `ctrl.*`、`ysm.*`、`q.*` 常用绑定。
- 第一人称手臂、手持物品、主副手、盾/弓/弩/三叉戟基础表现。
- projectile/vehicle 子模型的本地渲染和基础动画入口。
- debug dump、scanner、模型兼容报告。

### 4.2 必须按 Figura 架构改写的能力

这些能力不能照搬 Sparkle-Morpher 的生命周期，但要保留用户可见效果：

- Sparkle `PlayerCapability` 改为 Figura `Avatar` / `UserData` / `YsmModelRuntime` 生命周期。
- Sparkle `RendererManager` 改为 Figura render mixin + `YsmModelRuntime.renderer()` 分流。
- Sparkle `UnifiedRouletteScreen` 的交互语义保留，视觉和配置页改为 Figura 风格。
- Sparkle 自定义服务端状态同步不迁移，单人只走本地 runtime。
- Sparkle 模型导入管理器不迁移，衣柜入口继续由 Figura 本地 Avatar 管理。
- Sparkle 完整能力/技能系统不迁移，只接收其动作、控件、函数、动画语义。

### 4.3 禁止的修复方式

第一大阶段禁止用以下方式“修好某个模型”：

- 按模型目录名写分支，例如 `15_kluonoa`。
- 按具体骨骼名写附件特例，例如 `Car`、`Halo`、`Backpack`。
- 按具体变量名写硬编码逻辑，例如 `v.show_car`、`v.roaming.car`。
- 把 YSM functions 转换成 Lua 作为必要运行条件。
- 让原版玩家身体、Figura 模型和 YSM 模型同时提交，只靠透明或隐藏掩盖。

所有行为必须从资源索引、骨骼角色、Locator、animation、controller、function、control 和 Molang binding 推导。

## 5. 能力矩阵

| 能力 | Sparkle-Morpher 行为 | Figura 第一大阶段目标 | 当前状态 | 优先级 |
| --- | --- | --- | --- | --- |
| 未加密文件夹模型 | 自动识别 YSM 文件夹 | 衣柜直接加载 | 已有 | P0 |
| zip 模型 | 嗅探 zip 内容 | 文件夹/zip 表现一致 | 已有 | P0 |
| 加密 `.ysm` | YSMParser/native 链路 | 排除，仅预留接口 | 不做 | 排除 |
| 主模型几何 | RawYsmModel -> GeoModel | `YsmGeometry` -> `YsmRenderer` | 已有 | P0 |
| 手臂模型 | arm model 独立处理 | `armGeometry` + first-person renderer | 部分 | P0 |
| 贴图选择 | texture/subTexture 管理 | 衣柜右键 + 持久化 | 已有 | P0 |
| PartMask | 角色/渲染 pass 控制 | `YsmBoneRole` / `YsmPartMask` | 部分 | P0 |
| Locator | 手、背包、刀鞘等挂点 | `YsmLocator` / attachment point | 部分 | P0 |
| 静态第三人称 | 替换原版玩家 | YSM submit 后 cancel 原管线 | 已有 | P0 |
| 第三人称物品 | Locator/手骨矩阵 | 不依赖原版 `ArmedModel` | 部分 | P0 |
| 第一人称手臂 | 自定义手臂替换 | 不显示 Steve/Alex 手臂 | 部分 | P0 |
| 基础动画 | idle/walk/run 等 | native state machine + clip 播放 | 已有/需回归 | P0 |
| extra animation | 轮盘触发 | Figura action wheel/control 适配 | 已有/需完善 | P0 |
| 默认隐藏 | base/pre_parallel 零 scale | base hidden mask + 非零 scale 恢复 | 已有/需回归 | P0 |
| controller | 完整 slot/state runtime | JSON controller + slot binder | 部分 | P0 |
| functions | function/event 双语义 | `YsmMolangFunctionRuntime` 完整化 | 部分 | P0 |
| Molang binding | 大量 `ctrl.*` / `ysm.*` | 分 P0/P1/P2 补齐 | 部分 | P0/P1 |
| sound/particle | timeline executor | 本地播放基础支持 | 部分 | P1 |
| projectile | 子模型 + controller | 本地 projectile 模型和动画 | 部分 | P1 |
| vehicle | 子模型 + origin/ride/move | 本地 vehicle 模型和基础 controller | 缺口较大 | P1 |
| Geckolib transition | bone queue + begin/end transition | 可维护的简化队列，逐步逼近 | 缺口较大 | P1 |
| 模型/动作同步 | 服务器 Mod packet | 排除，不迁移 | 不做 | 排除 |
| native/SIMD | 原生加速 | 排除，不迁移 | 不做 | 排除 |

## 6. 第一大阶段工作包

### 工作包 A：资源索引与包格式闭环

目标：让未加密 YSM 包中所有单人运行所需资源都能被发现、打包、加载和诊断。

任务：

- 新版 `ysm.json`：
  - `files.player.model.main`
  - `files.player.model.arm`
  - `files.player.texture`
  - `files.player.animation`
  - `files.player.animation_controllers`
  - `files.projectiles`
  - `files.vehicles`
  - sounds/language/icon/background/functions。
- 旧版目录：
  - `main.json`
  - `arm.json`
  - `main.animation.json`
  - `extra.animation.json`
  - `*.png`
  - `functions/*.molang`
  - `description.ysm_extra_info`。
- zip：
  - 根目录模型包。
  - 第一层子目录模型包。
  - Windows 文件占用释放。
- NBT：
  - main/arm model data。
  - selected texture 和 texture entries。
  - animation/controller/function/action schema。
  - sub entity resource。
  - debug resource_index。

验收：

- `refs/YSM_NEW_Model`、`refs/YSM_OLD_Model`、`refs/01_taisho_maid`、`refs/15_kluonoa` 均能扫描出资源报告。
- 缺少可选资源只 warning，不阻断主模型加载。
- 文件夹和 zip 扫描结果一致。

### 工作包 B：原生几何、坐标与渲染提交

目标：YSM 主体不再经过 `.bbmodel` 转换路线，而是由原生 runtime 渲染。

任务：

- `YsmGeometryParser` 支持 Bedrock `format_version: 1.12.0` 常见字段。
- `YsmRenderer` 按 render pass 提交自定义几何。
- 坐标转换集中在 YSM transform/part 矩阵层。
- 按贴图透明性选择 cutout/translucent。
- YSM 启用时取消原版玩家模型和 Figura 模型提交。
- 切回 Figura 时释放 YSM 纹理、mesh、zip filesystem。

验收：

- 单人第三人称只看到 YSM 身体，不叠原版 Steve/Alex。
- 切回普通 Figura Avatar 后原有 Figura 渲染恢复。
- 透明贴图不导致整个人物消失或严重遮挡。

### 工作包 C：PartMask、Locator 与附件语义

目标：用通用角色和 pass 解决背景、GUI、背包、刀鞘、武器、手部挂点问题。

任务：

- 完善 `YsmBoneRole`：
  - body/head/left_hand/right_hand/first_person_arm。
  - background/gui。
  - backpack/blade/sheath/elytra/helmet。
  - projectile/vehicle special。
- 完善 `YsmBoneMapper` 别名表，对齐 Sparkle `YSMClientMapper`。
- `YsmPartMask` 拆分：
  - `PLAYER_BODY`
  - `WARDROBE_PREVIEW`
  - `FIRST_PERSON_ARM`
  - `HELD_ITEM`
  - `DEBUG_ALL`
- Locator 世界矩阵必须稳定、可被 Lua 和 item renderer 读取。

验收：

- 大背景和 GUI 部件不会在玩家身体 pass 中误显示。
- 手持物品使用 Locator 或手骨世界矩阵挂载。
- debug dump 能列出每个 Locator 的 role、bone、world matrix。

### 工作包 D：动画 clip 与默认隐藏

目标：让模型不只是静态站立，而是支持 YSM 常用动画和 scale 显隐。

任务：

- `YsmAnimationParser` 支持：
  - position/rotation/scale。
  - 常量 keyframe、时间 keyframe。
  - linear/step/catmullrom 基础插值。
  - timeline、sound_effects、particle_effects。
  - loop/once/hold 基础模式。
- `YsmAnimationPlayer` 支持：
  - native idle/walk/run/sneak/jump/fall/swim/ride/sleep。
  - extra animation trigger/stop。
  - base/pre_parallel/parallel 零 scale 默认隐藏。
  - extra 非零 scale 重新显示。
  - 动画停止后恢复 base hidden。
- Lua 覆写优先级明确：
  1. 模型默认 pose。
  2. base/controller/native 动画。
  3. extra/action 动画。
  4. Lua pose override。
  5. render pass 特殊变换。

验收：

- 普通模型 idle/walk 动画正常。
- 带默认隐藏附件的模型初始不炸开。
- 触发 extra 动作后附件可以显示，动作停止后恢复隐藏。
- timeline 变量赋值能影响后续条件。

### 工作包 E：Animation Controller 运行时

目标：支持 YSM/Bedrock 风格 controller，让复杂模型不用 Lua glue code 也能正常显隐和切动作。

任务：

- `YsmAnimationControllerParser` 支持：
  - `initial_state`
  - `states`
  - `animations`
  - `transitions`
  - `on_entry`
  - `on_exit`
  - `blend_transition`
  - `blend_via_shortest_path`
  - `sound_effects` 保留。
- `YsmControllerRuntime` 支持：
  - initial state bootstrap。
  - state transition。
  - `q.all_animations_finished`。
  - on_entry/on_exit 表达式执行。
  - controller source 标记。
- `YsmControllerSlotBinder` 对齐 Sparkle slot：
  - player `pre_main/main/post_main`
  - `pre_parallel/parallel`
  - `post_swing`
  - `hold/use`
  - `fp_arm`
  - `vehicle`
  - `projectile`
- 缺少 slot 的 controller 降级为 parallel，并记录 debug。

验收：

- `refs/15_kluonoa` 的光环、表情、车辆类附件可由 controller 初始状态和条件动画控制。
- `ctrl.sleep`、`v.roaming.emotion`、`v.show_car` 等变量链路能驱动 controller 条件。
- controller 解析失败只禁用对应 controller，不导致模型加载失败。

### 工作包 F：Functions Molang 包完整化

目标：把 `functions/*.molang` 做成 YSM runtime 的一等能力。

任务：

- 文件名规则：
  - `foo.molang` -> `fn.foo()`
  - `foo@player_ctrl_parallel_6.molang` -> `fn.foo()` + event。
  - `@player_ctrl_pre_main.molang` -> event only。
- 函数能力：
  - 多语句。
  - 赋值。
  - return。
  - `fn.xxx(...)` 嵌套调用。
  - `args`。
  - 递归保护。
- 事件能力：
  - `player_ctrl_pre_main`
  - `player_ctrl_main`
  - `player_ctrl_parallel_*`
  - `player_ctrl_post_swing`
  - `player_ctrl_fp_arm`
  - vehicle/projectile 预留。
- 与 controller slot 绑定：
  - event 在对应 controller slot update 前后执行。
  - 无 controller 但有 event 时按 legacy 稳定顺序执行。
- 作用域：
  - `v.*` / `variable.*` avatar 持久变量。
  - `t.*` / `temp.*` 调用或事件临时变量。
  - `args` 调用栈隔离。

验收：

- `refs/15_kluonoa/functions/*.molang` 全部加载、编译、注册。
- 车辆控件写入原始变量后，function 派生变量能驱动 animation scale。
- 纯事件文件能执行。
- 函数报错包含模型、文件、函数名、事件名和调用栈信息。

### 工作包 G：Molang Binding 对齐

目标：补齐 YSM 模型常见条件所需的 `ctrl.*`、`ysm.*`、`q.*`。

P0 绑定：

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
- `q.all_animations_finished`
- `ysm.rendering_in_inventory`
- `ysm.keyboard`
- `ysm.play_sound`
- `ysm.stop_sound`
- `ysm.stop_all_sounds`
- `ysm.sync` 本地 no-op + debug + 后续接口。

P1 绑定：

- `ysm.weather`
- `ysm.is_open_air`
- `ysm.food_level`
- `ysm.main_hand`
- `ysm.off_hand`
- `ysm.equipped_item`
- `ysm.is_local_player`
- `ysm.is_first_person`
- 装备/武器/使用物品/攻击状态基础查询。

P2 绑定：

- 船、木筏、箱船。
- 三叉戟、长枪、钉锤等武器细分。
- mod compat 查询。
- 子实体专用 query。

验收：

- Scanner 能列出 unsupported query。
- 未实现绑定有明确 fallback，不静默失败。
- 常见模型不因缺少 `ctrl.*` / `ysm.*` 导致动作完全不可用。

### 工作包 H：轮盘、控件与 Figura UI 适配

目标：保留 Sparkle-Morpher 轮盘功能，交互和视觉服从 Figura。

任务：

- `extra_animation` 自动生成动作。
- `extra_animation_classify` 生成分组/子页。
- `extra_animation_buttons` 生成 controls。
- 用户自定义布局持久化到 per-model store。
- 每页 8 槽、分页、返回、禁用、缺失状态。
- 控件类型：
  - toggle
  - slider
  - enum/select
  - color
  - text/number
  - keybind
  - button
- 控件写入 Molang 变量：
  - `v.roaming.*`
  - schema 指定 bind。
- 非 YSM Avatar 不受影响，仍走 Figura 原 action wheel。

验收：

- YSM 动作能从轮盘触发。
- 控件修改后同帧或下一帧进入 function/controller/animation 链路。
- 退出重进后布局和控件值保持。
- 普通 Figura action wheel 行为不变。

### 工作包 I：第一人称与手持物品

目标：单人实际游玩时第一人称体验可用。

任务：

- 第一人称手臂：
  - 优先使用 `arm.json`。
  - 缺失时从主模型手臂骨骼裁剪。
  - HEAD 拦截原版/Figura 第一人称手臂提交。
- 第一人称物品：
  - 主手/副手。
  - 空手、方块、普通物品、食物。
  - 盾、弓、弩、三叉戟。
- 第三人称物品：
  - 使用 YSM 手骨/Locator world matrix。
  - 不让原版 `ArmedModel.translateToHand` 决定最终矩阵。
- 手部动作：
  - 攻击、使用、拉弓、举盾时至少不严重错位。

验收：

- 第一人称不再看到原版 Steve/Alex 手臂。
- 第三人称剑、镐、盾、弓不明显漂移、倒置或穿掌。
- 切回 Figura Avatar 后第一人称恢复原行为。

### 工作包 J：Projectile 与 Vehicle 本地支持

目标：对齐 Sparkle-Morpher 单人模型包中常见子实体资源，不处理同步。

任务：

- projectile：
  - 加载 model/texture/animation/controller。
  - arrow/trident 基础替换渲染。
  - 独立 `YsmSubEntity` animation player。
  - projectile Molang context。
- vehicle：
  - 加载 model/texture/animation/controller。
  - vehicle 本地 renderer hook。
  - origin/move/ride/main/parallel slot。
  - 与玩家 ride/passenger state 联动。
- 子实体资源缺失时降级原版渲染。

验收：

- 有 projectile 配置的模型在单人射箭/三叉戟时可显示自定义子模型或安全回退。
- 有 vehicle 配置的模型不会因资源存在而加载失败。
- 子实体动画错误不影响玩家主模型。

### 工作包 K：动画过渡与事件执行器

目标：补齐 Sparkle-Morpher/Geckolib 风格行为中对复杂模型影响最大的部分。

任务：

- loop 边界事件补发。
- once/hold on last frame。
- beginning/ending transition。
- scale transition special 的等价简化。
- per-bone pose queue 或可维护的分层叠加队列。
- timeline instruction/sound/particle 执行顺序。
- action、controller、native 动画互斥与优先级。

验收：

- extra 动作结束后不会残留旧 pose。
- controller transition 不会让附件闪烁或永久隐藏。
- sound/particle 不在 loop 边界重复爆发或漏发关键事件。

### 工作包 L：Lua API 与调试

目标：让 Figura 用户和开发者能检查、控制、定位 YSM runtime。

任务：

- `ysm_model`：
  - `getPart`
  - `getParts`
  - `getRootParts`
  - `getLocator`
  - `getLocators`
  - `getTexture`
  - `setTexture`
  - `isYsm`
  - `getKind`
- `YsmModelPart`：
  - parent/children。
  - pos/rot/scale/visible。
  - origin pose。
  - anim pose。
  - world matrix/world pos。
  - resetPose。
- 调试 API：
  - controllers 列表。
  - 当前 controller state。
  - active animations。
  - functions/events。
  - controls。
  - unsupported query。

验收：

- Lua 能读取真实骨骼世界矩阵。
- Lua 能控制骨骼，但不破坏 YSM 默认动画链路。
- 非 YSM Avatar 调用 YSM API 安全返回空/false，不报错。

### 工作包 M：测试、扫描器与回归模型集

目标：把“模型是否可用”从截图猜测变成可重复检查。

任务：

- `YsmModelScanner` 输出：
  - 模型类型。
  - 主/手臂模型。
  - 贴图。
  - animations。
  - controllers。
  - functions/events。
  - actions/controls。
  - projectile/vehicle。
  - unsupported query/binding。
- `YsmModelValidator` 检查：
  - JSON parse。
  - 骨骼树无循环。
  - orphan bone 降级。
  - UV 无 NaN/Infinity。
  - controller state 引用存在。
  - animation ref 存在。
  - function 编译。
- 回归模型集：
  - `refs/YSM_NEW_Model`
  - `refs/YSM_OLD_Model`
  - `refs/01_taisho_maid`
  - `refs/15_kluonoa`
  - 已手测通过的 Wine Fox。
  - 当前问题模型：发光鱿鱼娘、08_sta、14_momo 等。

验收：

- `.\gradlew.bat :common:compileJava` 通过。
- `.\gradlew.bat :fabric:compileJava` 通过。
- 扫描器能定位模型失败属于 resource、geometry、animation、controller、function、binding、render pass 哪一层。

## 7. 与 Sparkle-Morpher 的差异清单

除“模型加密/SIMD/模型和动作同步”三个明确排除项外，本阶段仍存在以下架构差异，必须在计划中显式处理：

| 差异点 | Sparkle-Morpher | Figura 第一大阶段处理 |
| --- | --- | --- |
| 生命周期中心 | `PlayerCapability` / `ModelAssembly` | `Avatar` / `UserData` / `YsmModelRuntime` |
| 模型管理 | 独立模型管理器、收藏、服务器下发 | Figura 本地 Avatar 衣柜，后续不阻断扩展 |
| UI 风格 | 独立轮盘和管理界面 | Figura 风格 action wheel / config screen |
| 渲染入口 | 自有 renderer manager | Figura 现有 render mixin 分流 |
| Figura Avatar 兼容 | 作为导入格式之一 | Figura 原 Avatar 是主线，YSM 是 native runtime 分支 |
| Lua | 非核心脚本平台 | 必须保持 Figura Lua 和权限体系 |
| controls | Sparkle 自有配置/轮盘 | 通用 Avatar controls + YSM schema 适配 |
| Molang runtime | YSM 专用上下文 | 嵌入 Figura Molang engine，隔离 YSM binding |
| controller queue | Geckolib 风格完整队列 | 先实现等价语义，再逐步补 per-bone queue |
| sound | Opus/native 音频系统 | 先接 Figura/Minecraft 本地音频，native 音频排除 |
| mod compat | Sparkle 内置大量 compat | 以 binding fallback + 分层补齐方式推进 |
| 子实体 | projectile/vehicle 完整 runtime | 本地单人优先，先静态/基础动画，再完整 slot |
| 错误处理 | 模型管理器诊断 | Avatar/YSM scanner/debug panel 诊断 |

## 8. 实施顺序

建议按以下顺序收束第一大阶段：

1. 资源索引与 scanner 补齐
   - 先保证能看清每个模型包里有什么，缺什么。
2. PartMask/Locator/默认隐藏回归
   - 先消除身体炸背景、附件默认乱显的问题。
3. Animation clip 与 extra action 回归
   - 保证常见动作、默认隐藏、动作显示稳定。
4. Controller slot 与 function event 绑定
   - 解决复杂模型“控件改了但模型不变”的根因。
5. Molang binding P0/P1
   - 按 scanner 报告补齐常见缺口。
6. 轮盘/controls 持久化与 Figura UI 完整化
   - 让单人用户真正可操作。
7. 第一人称手臂和手持物品
   - 面向实际游玩体验校准。
8. Projectile/vehicle 本地支持
   - 不做同步，只做单人可见和安全回退。
9. Transition/event executor
   - 对复杂动作做最后一轮机制对齐。
10. Lua/debug/docs
   - 交付可诊断、可扩展的第一大阶段。

## 9. 第一大阶段最终验收清单

第一大阶段完成时，必须满足：

- 未加密 YSM 文件夹和 zip 能在衣柜中显示、加载、卸载。
- 新版和旧版 YSM 模型结构均可用。
- 贴图可选择、热切换、持久化。
- YSM 启用时不再渲染原版玩家身体和 Figura 模型身体。
- 切回 Figura Avatar 后普通 Figura 行为完全恢复。
- 主模型、手臂模型、贴图、animation、controller、functions、actions、controls、projectile、vehicle 资源能被扫描和诊断。
- PartMask 能阻止背景、GUI、展示附件误进玩家身体 pass。
- Locator 能稳定驱动手持物品和 Lua world matrix。
- idle/walk/run/sneak/jump/fall/swim/ride/sleep 等基础动作可用或安全降级。
- extra animation 能从轮盘触发、停止、互斥和恢复默认隐藏。
- animation controller 能执行 initial_state、state、transition、on_entry、on_exit。
- functions 能执行 `fn.*` 和 `@player_ctrl_*` 事件，并接入 controller slot。
- controls 写入的变量能经 functions/controller/animation 驱动模型表现。
- 第一人称看不到原版手臂，常见物品不明显错位。
- projectile/vehicle 资源存在时不破坏模型加载，已接入的子实体能本地显示或安全回退。
- `ysm_model` Lua API 可读写骨骼并读取真实世界矩阵。
- scanner/debug 能定位资源、动画、controller、function、binding、render pass 问题。
- `.\gradlew.bat :common:compileJava` 通过。
- `.\gradlew.bat :fabric:compileJava` 通过。

## 10. 最小可交付版本

若需要先收敛范围，第一大阶段最小可交付版本定义为：

- 文件夹/zip 未加密 YSM 加载。
- 新/旧结构识别。
- 贴图选择。
- 原生第三人称渲染。
- PartMask/Locator。
- idle/walk + extra animation。
- controller initial_state + transitions。
- functions `fn.*` + `player_ctrl_parallel_*`。
- controls 写入变量并驱动模型显隐。
- 第一人称手臂和基础手持物品。
- scanner 能覆盖 `refs/YSM_NEW_Model`、`refs/YSM_OLD_Model`、`refs/01_taisho_maid`、`refs/15_kluonoa`。
- common/fabric 编译通过。

这个版本可以先宣告“单人未加密 YSM 可用”，后续再把 projectile/vehicle 完整动画、Geckolib 队列、更多 binding、sound/particle executor 作为增强项推进。

## 11. 风险与控制

- 范围膨胀：以 scanner 输出和回归模型集驱动，不按单模型临时补丁扩散。
- 坐标系风险：所有 Bedrock/Figura/Minecraft 转换集中在 YSM 矩阵层，禁止解析层和渲染层重复翻转。
- 动画优先级风险：明确 base/controller/native/extra/Lua/pass 的顺序，不让不同层互相污染。
- functions 顺序风险：event key 和 controller slot 映射必须稳定，不能靠 Map 随机顺序。
- binding 缺失风险：unsupported query 必须可见，默认值必须可解释。
- zip 泄漏风险：`YsmPackage` 生命周期必须跟 Avatar 卸载绑定。
- Figura 回归风险：所有 YSM mixin 只在 `Avatar.isYsmNative()` 时接管，普通 Avatar 路径不改语义。
- 第一人称风险：先保证不显示原版手臂和物品不严重错位，再逐步对齐复杂使用动画。

## 12. 推荐提交拆分

1. `Complete YSM resource scanner and debug report`
2. `Stabilize YSM part masks and locator matrices`
3. `Fix base hidden and extra animation visibility semantics`
4. `Bind YSM function events to controller slots`
5. `Expand P0 Molang ctrl and ysm bindings`
6. `Complete YSM action wheel controls and persistence`
7. `Stabilize first-person YSM arms and held items`
8. `Add local projectile and vehicle YSM runtime hooks`
9. `Improve controller transitions and animation event executor`
10. `Add YSM Lua/debug APIs and regression tests`

每个提交都应附带 scanner 输出或回归模型验证结果，避免只凭单次实机截图判断完成。
