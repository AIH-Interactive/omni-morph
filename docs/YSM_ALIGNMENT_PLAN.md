# OmniMorph 对齐 Sparkle-Morpher 动画行为：优先级与实施顺序

日期：2026-10-26。依据：`docs/YSM_ANIMATION_ENGINE_COMPARISON.md`（36 项静态差异，编号沿用）。
目标：OmniMorph 的 YSM 动画**实际行为**与 `refs/Sparkle-Morpher` 一致。

> 本文只是计划，不授权实施。按 AGENTS.md 的范围控制，每一档/每一项开工前需用户确认；
> 每项完成后单独提交推送（阶段检查点规则）。`docs/*` 被 `.gitignore` 忽略，本文需 `git add -f`
> 才会进入版本库。

## 0. 先读这几条（对齐的边界）

1. **差异是静态源码对比，不是已复现的 BUG。** 开工前每项先写失败测试（RED），用真实 YSM 资源
   （如 `refs/avatars/` 下的模型）经生产路径复现，再改；复现不了的降级或放弃。
2. **“与 Sparkle 一致”不等于“Sparkle 一定对”。** 下列项对齐会复制 Sparkle 的缺陷或删减
   OmniMorph 现有能力，列入“需用户决策”，默认**不做**：D024、D028、D029、D019、D018（部分）、
   SAMP-02、SAMP-07 的 Sparkle 特有行为、Kipfel。
3. **数据层与逻辑层分开。** D027（默认动画资源内容不同）会改变所有原生状态机的姿态，必须先于
   任何“逻辑对齐后再看画面”的验收，否则画面对不齐时分不清是数据还是逻辑。
4. 已有 `YsmAnimationEventWindow`、冻结协议、Figura 路径、`ysm_default` 资源哈希
   （`YsmDefaultAnimationResolver` 内钉死的 `47f07b6b…`）等**有测试/钉死约束**，改动要同步更新
   对应测试与哈希，不得静默改。
5. 真实窗口/GPU/双客户端验收照旧标 `MANUAL/PENDING`，不以 CPU 回归冒充。

## 1. 优先级总览

| 档 | 含义 | 项 |
| --- | --- | --- |
| **P0** | 影响几乎所有模型的原生动画；且彼此耦合，需最先一起对齐 | D027、D001、D002、D003、D004、D005、D006、D023、D012 |
| **P1** | 作者控制器/动画的确定性语义（表达式、过渡、事件、声音） | D010、D011、D007、D008、D009、D020、D015、D022、D016、SAMP-01、03、04、05、06 |
| **P2** | 条件性/小众路径 | D013、D014、D021、D026、D017、D018、D025、SAMP-07（有限范围） |
| **不对齐/需决策** | 复制 Sparkle 缺陷、删能力、或范围外 | D024、D028、D029、D019、SAMP-02、Kipfel、AnimationProgramAnalyzer、D017 的 bbmodel/gltf 部分 |

## 2. P0：原生状态机与默认动画（先做，且按顺序）

顺序依据：D027 是数据；D006/D001/D002 决定“哪个槽、什么时候、播什么”；D003/D004/D005 是手部细节；
D023/D012 是状态机转移与起播时刻。前一项不稳，后一项的行为对比没有意义。

### P0-1　D027：内置默认动画资源与 Sparkle 同步
本项已按用户 2026-10-26 更正完成：运行时回退资源位于 `assets/omni-morph/default_animation/`，
14 份动画与 Sparkle 逐字节一致；`ysm_default` 及其 42 项创建模板清单保持原样，专用于新建 YSM。
已有作者动画优先，缺失的受支持主动作/手部动作回退到新资源；显式空动画、禁用与损坏诊断规则保留。
主动作白名单移除参考中不存在的 `gui`；`new`、`vehicle$…` 等仍保留在资源中，不在本项扩张自动选片机制。
以下差异数量为对齐前的审计记录。
- 内容：`main`(57 vs 50)、`arm`(56 vs 57)、`boat`(仅 `water`)、`extra`(17 vs 8) 四个文件与 Sparkle
  `assets/sparkle_morpher/builtin/default/animations/` 不同；其余 10 个字节相同。
- 做法：评估是否直接采用 Sparkle 的四份文件（含中文命名片段、`totem_of_undying`、`minecart`、
  `slashblade` 等条目）；更新 `YsmDefaultAnimationResolver` 内钉死的哈希、`Domain` 枚举与
  `MAIN_ACTIONS` 名单（Sparkle 独有 `new`/`vehicle$…`、Omni 独有 `gui`）。
- 风险：Omni 独有的 `gui`、`swing:fishing_rod`、`use_*$brush/goat_horn`、`extra6` 是否应保留，
  需用户决策（对齐 = 删除；保留 = 与 Sparkle 不一致）。
- 验证：哈希核对测试 + 采样同名片段与 Sparkle 逐骨对比（同一骨骼同一时刻姿态差 < 容差）。

### P0-2　D006：原生运动状态选择补全
- Sparkle 的 `PlayerAnimationController`/`CompositeAnimationController` 谓词链比 Omni
  `updateNativeStateMachine`（`YsmAnimationPlayer.java:1928` 起）覆盖更多状态且优先级不同。
- 做法：按报告 D006 的状态/优先级表逐项补，保持 projectile/vehicle 谓词不动（已核对一致）。

### P0-3　D001：hold/use 前后阶段独立排序
- Sparkle 的 hold/use 槽独立排序、晚于 parallel；Omni 现为统一排序。改 `YsmControllerSlot.executionOrder`
  及 `applyControllerLayers` 的前后分段（`YsmAnimationPlayer.java:1055-1074`）。

### P0-4　D002：`ysm-builtin` 状态的原生/作者控制器互斥切换
- 补 Sparkle 的 builtin 状态互斥逻辑；注意与已修的 `setNativeStateMachineEnabled`/子实体
  `syncSubEntityAnimation` 的显式控制器优先规则共存。

### P0-5　D003 / D004 / D005：手部动画三项
- D003：使用物品时**另一只手**的 hold 不应被淡出；同手 hold 引入 PAUSE/恢复生命周期（注意：
  Sparkle 的 PAUSE **不冻结时钟**，见报告“PAUSE 不等于冻结持握时钟”）。
- D004：原生 hand 动画补版本/来源控制的 loop 覆盖分支（`YsmAnimationPlayer.updateNativeHandAnimations`）。
- D005：原生 hand 选择与查询层统一使用**本地输入脉冲**（`YsmHandQuery`），消除查询与播放取不同输入。
- 这三项刚好落在最近的拉弓修复区域（`48d7b933`），务必保留 `YsmHandAnimationSmokeTest` 已覆盖的
  “同手使用优先于挥手”“松弓重拉重新起播”两条回归，不得回退。

### P0-6　D023：控制器转移的自转移与环路
- 对齐 Sparkle `evaluateTransitions`：visited 不含当前状态；条件为真且目标已访问 → **终止本轮搜索**；
  自转移允许重入一次（重置片段、`on_exit/on_entry`、声音）；链仅在新状态无动画槽时继续。
- 改点：`YsmControllerRuntime.update/firstTransition/transition`（`:133-342`）。
- 风险：自转移重入会重复触发 `on_entry`，需配合 D007（声音）一起验证。

### P0-7　D012：`blend_transition`/`set_beginning_transition_length` 的起播时刻
- Sparkle：新片段在 BEGINNING_TRANSITION 期间时钟冻结在 0、事件被抑制，过渡结束后才起跑；
  状态机路径（`transitionToEntry`）与函数控制器路径都如此。
- Omni：新片段立即起播并淡入。对齐需要给 `PlayingAnimation` 增加“起播延迟”，
  `playInSlot` 的 `fadeSeconds` 语义由“权重淡入”改为“延迟起播 + 姿态快照混合”。
- 这是 P0 里改动面最大的一项，建议放在 D001–D006 稳定之后单独一档。

## 3. P1：作者表达式/过渡/事件的确定性语义

### P1-1　D020：`math.*` 实现体（纯函数，最先做，风险最低、收益直接）
逐项对齐 `MathFunctions.java`：
1. `min_angle(x)` 改为单参数：`x%360` 折到 [-180,180)（现为双参数，`min_angle(200)` 得 360）。
2. `hermite_blend(x)`：按 Sparkle 做 `ceil` 后取整量化（`floor(3m²−2m³)`）。
3. `random_integer(a,b)`：区间 `[min,max)`，边界颠倒时交换；随机源改用实体 `random()`。
4. `die_roll(n,lo,hi)` / `die_roll_integer(n,lo,hi)`：三参数求和（现为单参数 sides）。
5. `random(a,b[,seed])`：边界颠倒时交换，随机源同上。
6. `asin/acos` 去掉夹取（越界得 NaN）；`pow` 非有限→0；`lerprotate` 先 `wrapDegrees`；
   `sin/cos` 评估是否用 `Mth.sin/cos`（仅数值微差，可不做）。
- 验证：每个函数一组固定输入的黄金值测试（Sparkle 实现为期望值来源，注意“独立期望值”原则：
  用 Sparkle 源码手算/运行得到常量，不依赖 Omni 自己的输出）。

### P1-2　D015：补齐 98 个缺失的 `ysm.*` 名字（按价值分批）
- 批 1（与 P0 手部/武器强相关，**建议随 P0-5 一起**）：`weapon_*`(11)、`trident_*`、`lance_*`、
  `mace_*`（共 42），数据源已在 Omni 的 `YsmHandQuery/YsmHandItemType`，只需暴露为 `ysm.` 变量。
- 批 2（简单查询）：`on_ladder`、`ladder_facing`、`is_riptide`、`arrow_count`、`stinger_count`、
  `swinging_arm`、`swim_amount`、`item_use_normalized`、`map_angle`、`is_holding_left/right`、
  `tcos0`、`item_is_charged`、`mainhand/offhand_charged_crossbow`、`is_maid`、`sleep_rotation`。
- 批 3（属性/鹦鹉/投射物/钓钩/船）：属性类 12 个（含 Forge 属性默认值 `4.5/3.0/1.0/0.08/0.0/64.0`）、
  `*_shoulder_parrot*`、`projectile_owner`、`throwable_item`、`hooked_in`、`is_biting`、`is_fishing`、
  `in_ground`、`on_ground_time`、`is_spectral_arrow`、`boat_is_chest/raft`、`boat_chest_passenger_offset`、
  `hit_target_*`、`nametag_distance`、`biome_category`、`delta_movement_length`、`target_x/y_rotation`。
- 批 4（函数）：`effect_level`、`get_equipped_item_name`、`get_root_locator_offset`(恒0)、
  `dump_equipped_item`、`dump_relative_block`（调试输出，可最后/不做）。
- 现状后果：`ysm.*` 未知名求值会**抛异常**（`UnavailableBinding`），条件→false、权重→0，
  所以补齐后作者控制器的行为会明显变化，属必须验证项。

### P1-3　D022：`time_of_day` 相位
- `Avatar.java:690` 改为 `((t+6000)/24000)%1`；同时检查所有读取 `time_of_day` 的 Omni 内部用途
  （`QueryVariables.TIME_OF_DAY` 之外无其它写入点，已核）。`moon_phase` 与原版属性路径一致，可不动。

### P1-4　D016：Bedrock `this` 关键字
- 在 Molang 解析器增加 `this` 节点，在关键帧求值前写入当前通道值（Sparkle
  `ExpressionEvaluatorImpl.setCurrentChannelValues` + `BoneBlendState` 写入点）。
- 波及解析器/求值器/采样器三处，建议单独一档，并放在 SAMP 系列之后（共享采样路径）。

### P1-5　D010：过渡旋转混合改为 ZYX 四元数 nlerp
- `YsmBonePose.blend` 的旋转改为：起止欧拉角各加骨骼初始旋转 → ZYX 四元数 → nlerp → 回转欧拉 → 减初始旋转
  （Sparkle `MathUtil.nlerpEulerAngles`）；位置/缩放保持线性。`blend_via_shortest_path` 标志在 Sparkle
  无消费者，对齐后该标志对旋转**不再生效**（需决策是否保留字段做兼容）。

### P1-6　D011：循环回绕/长步长的事件语义
- 对齐 Sparkle：回绕时指令事件 `executeRemaining` 补跑一轮、声音事件仅 `reset()+stopAll()`（**不补播**尾部声音）、
  长步长跨多周期只一轮；倒播窗口语义取消。
- **注意：这会撤销 `YsmAnimationEventWindow` 的多周期/倒播语义和相关测试
  （`YsmAnimationEventWindowSmokeTest`）。** 属“删能力换一致”，需用户确认后再做。

### P1-7　D007 / D008 / D009
- D007：声明控制器每帧刷新播放时**不要**无条件停止该片段的声音（Sparkle 只在片段切换/重置时停）。
- D008：身体与第一人称使用两套独立名称表，取消混表（`defaultClips`/`defaultArmClips` 的回退规则）。
- D009：条件表达式编译失败的处理对齐 Sparkle（Omni 现可退化为无条件跳转/满权重）；
  与 `YsmControllerRuntime.evalBool/animationWeight` 的失败语义一起改。

### P1-8　SAMP-01/03/04/05/06（采样层，同一文件集，合并成一档）
- 01 缺省 `animation_length` 推导；03 JSON 对象关键帧缺省/字符串插值；04 前键 step 与目标键
  catmullrom 的优先级（Omni 相反）；05 LOOP 恰到终点时的采样；06 每骨内通道表达式求值顺序。
- 改点集中在 `YsmAnimationParser`、`YsmAnimationSampler`；均为确定性数值，适合黄金值测试。
- SAMP-07（非有限值）见 P2。

## 4. P2：条件性/小众

| 项 | 对齐内容 | 备注 |
| --- | --- | --- |
| D013 | 滤波器间隔 > 8/60 s、时间回退或 recovery 时以当前输入重建滤波器 | `YsmFilterRuntime.update`；同时 first/second_order 默认值已一致 |
| D014 | 滤波器非有限输入：保持上值（不归 0） | 与 D013 同文件，一起做 |
| D021 | 取消骨骼 `visible/hidden/default_visible/neverRender` 初始隐藏（Sparkle 不读）；是否解析 `visible_bounds_*` | 取消可见键会改变既有显示行为，需决策 |
| D026 | `coloredTranslucent` 去掉 RGB 非零条件；多贴图遍历 | `YsmGeometry.scanQuadAlpha` |
| D017 | 旧格式目录要求 `main.json`+`arm.json` 同时存在 | `.bbmodel/.gltf/.glb` 单文件加载属范围外，不做 |
| D018 | 手持物：多组附加手部骨链各画一份、sword 锚点；去掉按骨名的回退名单 | 名单回退是 Omni 的兼容手段，删除前需决策 |
| D025 | 远端玩家 `xxa/yya/zza/food_level/盾牌` 取同步值 | 需要扩展 `YsmSyncMessage`，涉及协议/后端，**范围外**，单独立项 |
| SAMP-07 | 非有限采样值回退上次有效值（Sparkle `PoseValueCache`） | 仅对齐 Sparkle 的“保持上值”，不照搬实现 |

## 5. 不建议对齐（需用户明确要求才做）

- **D024 `is_close_eyes`**：Sparkle 条件恒假（永不眨眼），对齐等于关掉 Omni 现有眨眼。
- **D028 `keyboard/mouse`**：Sparkle 对任何模型返回本机按键；Omni 只对本地玩家，更安全。
- **D029 动画 `particle_effects`**：Sparkle 实际不发射；对齐 = 删除 Omni 现有功能。
- **D019 执行步数预算**：Sparkle 无；Omni 的更严防护应保留（可评估阈值是否过严，但不删除）。
- **SAMP-02**：报告标注为 Sparkle 侧解析问题候选，不应复制。
- **Kipfel / AnimationProgramAnalyzer**：Sparkle 的内容指纹白名单特化与性能优化层，违背 Omni 的
  “无模型名/hash/作者分支”约束。

## 6. 推荐批次与验收

| 批 | 内容 | 依赖 | 主要验证 |
| --- | --- | --- | --- |
| A | P1-1 D020（math 函数）、P1-3 D022 | 无 | 黄金值单测（最快见效、零耦合） |
| B | P0-1 D027 默认动画资源 | 需用户决策 Omni 独有条目 | 哈希 + 同名片段逐骨采样对比 |
| C | P0-2/3/4 D006、D001、D002 | B | 受控实体的状态选择矩阵 + 真实模型状态序列 |
| D | P0-5 D003/D004/D005 + P1-2 批1（weapon/lance/mace/trident） | C | 保留拉弓两条回归；新增手部/武器黄金序列 |
| E | P0-6 D023 + P1-7 D007/D008/D009 | C | 转移图测试（自转移/环路）+ 声音重入 |
| F | P0-7 D012 起播延迟 | E | 起播时刻/事件抑制序列测试 |
| G | P1-8 SAMP 系列 + P1-5 D010 | F | 采样黄金值 + 过渡中间帧对比 |
| H | P1-2 批2/3/4、P1-4 D016、P1-6 D011（需确认） | G | 逐名字查询测试；`this` 表达式测试 |
| I | P2 全部（D025 单独立项） | H | 各自单测 |

每批：先 RED → 最小修改 → `:common` 相关 SmokeTest + `:fabric:build` 前台 PowerShell `Tee-Object`
日志 → 提交推送并核对远端 SHA → 再进下一批。真实游戏验收（拉弓、持武器、过渡、声音）按
`docs/ALPHA4_ACCEPTANCE.md` 记 `MANUAL/PENDING`，由用户实机确认。

## 7. 需要用户先决定的事项

1. D027：已决定运行时回退资源完整采用 Sparkle；`ysm_default` 新建模板保留原样（2026-10-26）。
2. P1-6 D011：是否接受撤销多周期/倒播事件窗口能力以换取与 Sparkle 一致？
3. P1-5 D010：对齐后 `blend_via_shortest_path` 对旋转不再生效，是否接受？
4. D021：骨骼 `visible/hidden` 初始隐藏是否取消？
5. 第 5 节“不建议对齐”的各项是否维持不做？
6. D025（远端输入同步）是否立项，涉及协议/后端。

## 8. 差异处理记录表

状态取值：`待实施`＝已纳入计划、等待开工；`待决策`＝需用户先回答第 7 节问题；`不对齐`＝默认不做（需用户明确要求才改）；`范围外`＝超出本任务，需单独立项。
当前已完成项在“状态”列标 `已完成（提交号）`，其余项尚未实施。批次对应第 6 节。

| 差异 | 状态 | 决策理由 |
| --- | --- | --- |
| D001 hold/use 前后阶段缺独立排序 | 已完成（d26fb8ae） | 决定原生槽执行顺序，影响所有持物/使用动画，属 P0 |
| D002 缺 `ysm-builtin` 原生/作者控制器互斥 | 不对齐（用户 2026-10-26 决定） | 用户无内置模型，不考虑此项 |
| D003 使用时另一只手 hold 被淡出、同手无 PAUSE | 已完成（f0342ef0） | 直接影响双手持物观感；须保留拉弓两条回归，属 P0 |
| D004 原生 hand 缺 loop 覆盖分支 | 已完成（4259a113） | 决定持握/使用片段是否循环，属 P0 |
| D005 hand 选择绕过本地输入脉冲 | 已完成（d2f1c34e） | 查询层与播放层输入不一致，属 P0；P1-2 批 1 的武器名仍待补 |
| D006 原生运动状态覆盖少、优先级不同 | 已完成（c7a343c8） | 原生状态机改用与 `ctrl.*` 同一张参考动作表；P0 仅余 D023/D012，D015 批 1 的武器名仍待补 |
| D007 控制器每帧刷新无条件停声 | 待实施（E 批） | 声音被反复打断，确定性差异，属 P1 |
| D008 身体与第一人称混表 | 待实施（E 批） | 两套名称表混用会串动画，属 P1 |
| D009 条件解析失败退化为无条件/满权重 | 待实施（E 批） | 失败时 Sparkle 与 Omni 行为相反，属 P1 |
| D010 过渡旋转混合算法不同 | 待决策（G 批） | 对齐后 `blend_via_shortest_path` 对旋转不再生效，需确认是否接受 |
| D011 回绕/长步长事件语义 | 待决策（H 批） | 对齐会撤销 `YsmAnimationEventWindow` 多周期/倒播能力及其测试 |
| D012 过渡期新片段时钟冻结/事件抑制 | 待实施（F 批） | 改动面最大，须在 D001–D006、D023 稳定后单独一档 |
| D013 滤波器长间隔恢复策略 | 待实施（I 批） | 仅影响使用 first/second_order 的模型，属 P2，与 D014 同文件 |
| D014 滤波器非有限输入归零 | 待实施（I 批） | 同上；对齐为“保持上值” |
| D015 98 个 `ysm.*` 名字缺失 | 待实施（D/H 批） | 未知名求值会抛异常，条件变 false/权重变 0；武器 42 个随手部做，其余分批 |
| D016 缺 Bedrock `this` 关键字 | 待实施（H 批） | 改动涉解析/求值/采样三处，放在 SAMP 之后单独做 |
| D017 包识别矩阵 | 待实施（I 批，部分） | `main.json`+`arm.json` 判定可对齐；`.bbmodel/.gltf/.glb` 属范围外，不做 |
| D018 手持物挂点解析/多挂点 | 待决策（I 批） | 多链各画一份与 sword 锚点可对齐；删除按骨名回退名单会失去兼容手段，需确认 |
| D019 Molang 步数预算仅 Omni 有 | 不对齐 | Omni 更严的防护，删除无收益且有风险；可另评估阈值 |
| D020 `math.*` 实现体语义 | 待实施（A 批） | 纯函数、确定性、零耦合，最先做 |
| D021 骨骼可见键/visible_bounds | 待决策（I 批） | 取消骨骼 `visible/hidden` 初始隐藏会改变既有显示，需确认 |
| D022 `time_of_day` 相位 +0.25 日 | 待实施（A 批） | 单点改动，已确认无其它写入点 |
| D023 自转移与环路处理 | 待实施（E 批） | 影响状态机转移，需配合 D007 验证声音重入，属 P0 |
| D024 `is_close_eyes` 恒假 | 不对齐 | 是 Sparkle 缺陷（条件互斥永不眨眼），对齐等于关掉 Omni 现有眨眼 |
| D025 远端玩家输入/饥饿/盾牌取值 | 范围外 | 需扩展 `YsmSyncMessage`，涉及协议与后端，另行立项 |
| D026 纹理半透明判定（黑色半透明像素） | 待实施（I 批） | 去掉 RGB 非零条件即可对齐，属 P2 |
| D027 内置默认动画资源不同 | 已完成（本次 D027 检查点） | 独立 default_animation 与 Sparkle 14/14 字节一致，运行时哈希/主动作名单同步；ysm_default 创建模板保留 |
| D028 `keyboard/mouse` 输入归属门控 | 不对齐 | Sparkle 对任何模型返回本机按键，Omni 仅本地玩家更安全 |
| D029 动画 `particle_effects` 事件 | 不对齐 | Sparkle 实际不发射，对齐等于删除 Omni 现有功能 |
| SAMP-01 缺省 `animation_length` 推导 | 待实施（G 批） | 采样层确定性数值，黄金值测试 |
| SAMP-02 明文 JSON 字符串 loop 解析 | 不对齐 | 报告判为 Sparkle 侧解析问题候选，不应复制 |
| SAMP-03 关键帧缺省/字符串插值 | 待实施（G 批） | 同 SAMP-01 |
| SAMP-04 前键 step 与目标键 catmullrom 优先级 | 待实施（G 批） | 两侧优先级相反，影响中间采样 |
| SAMP-05 LOOP 恰到终点采样 | 待实施（G 批） | 边界帧差异，确定性 |
| SAMP-06 通道表达式求值顺序 | 待实施（G 批） | 条件性影响（表达式有副作用时） |
| SAMP-07 非有限采样值恢复策略 | 待实施（I 批，有限范围） | 仅对齐“保持上次有效值”语义，不照搬 `PoseValueCache` 实现 |
| 设计差异：Kipfel 按内容指纹白名单特化 | 不对齐 | 违背 Omni“无模型名/hash/作者分支”约束 |
| 设计差异：AnimationProgramAnalyzer 优化层 | 不对齐 | Sparkle 性能优化，无行为语义要对齐 |
| 设计差异：多人同步协议（peer/cloud vs YsmSync） | 不对齐 | 两套协议与后端互不相通，无互通要求 |
