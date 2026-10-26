# D025 远端玩家显示状态同步：分阶段实施计划

日期：2026-10-27。状态：**计划，未授权实施**。依据：`docs/YSM_ANIMATION_ENGINE_COMPARISON.md` D025、
`docs/YSM_ALIGNMENT_PLAN.md`（D025 标“范围外，另行立项”），以及本次只读分析。

> 本计划改动跨两个仓库（主仓库 `ysm-alignment` 与 `omnm-cloud` 的 `master`）、冻结协议与隐私语义。
> 按 AGENTS.md，开工前需用户确认第 7 节决策；每阶段单独验证、提交、推送并核对远端。

## 1. 目标

观察端客户端上，其他玩家模型读取的输入/饥饿/飞行/经验/效果等值，与参考一样来自对方客户端同步的
“显示状态”，而不是原版客户端可见的默认/零值。本地玩家仍读本地实体。

## 2. 参考实现（`refs/Sparkle-Morpher`）

- `core/display/PlayerDisplayState`：`experienceLevel, health, maxHealth, foodLevel, effectAmplifiers,
  flying, strafeInput, verticalInput, forwardInput, shieldBlocking`，全部可空；构造时校验范围
  （等级 ≥0、饥饿 0..20、生命 0..1e6、输入 −1..1、效果 ≤256 项、id 格式、等级 1..256）。
- `client/entity/PlayerEntityFrameState.snapshot`：只对本机玩家采集；远端经 `applyDisplayState` 写入；
  各 getter 本地读实体、远端读显示状态，缺失时为 0/false。
- 发布：`cloud/client/CloudPlayerModelSync.localDisplay` 仅当服务端声明 `player_display_state_v1` 时，
  经 presence `display_state` 发布；接收端 `CloudPlayerDisplayLedger` 按 scope/epoch/维度隔离。
- 读取方：`YSMBinding` 的 xxa/yya/zza、food_level、盾牌冷却；`QueryBinding` 的 is_flying、
  player_level、health、max_health；`EffectLevel`；`ControllerActionResolver` 的 fly 判定。

## 3. Omni 现状与差距

| 字段 | Omni 读取点 | 远端现状 | 是否需要同步 |
| --- | --- | --- | --- |
| xxa/yya/zza | `MolangBindings.input` | 原版不同步，恒 0 | 需要 |
| 饥饿 | `MolangBindings.foodLevel` | 不同步 | 需要 |
| 飞行 | `QueryVariables`、`YsmPlayerActionState.snapshot` | `abilities.flying` 不同步 | 需要 |
| 经验等级 | `QueryVariables`（experienceLevel） | 不同步 | 需要 |
| 效果等级 | `MolangBindings.effectLevel`（D015） | 通常无等级 | 需要 |
| 生命/最大生命 | `QueryVariables.HEALTH/MAX_HEALTH` | 原版实体数据同步 | 推断不需要，需实机核对 |
| 盾牌格挡 | `in_shield_block_cooldown`（`isBlocking`） | 使用物品状态同步 | 推断不需要，需实机核对 |

同步链路：`YsmSyncMessage.State` 仅 texture/controls/actions/controllers/playbacks；
Figura Cloud 走 ping 转发 `YsmSyncCodec`（`VERSION=1`，未知段报错，32768 字节、480 字节/包分片）；
Protobuf 走 `protocol/omnimorph/cloud/v1/cloud.proto` 的 `YsmState`（冻结发布，`release.sha256`，
`omnm-cloud/protocol` 有一致副本）；后端 `omnm-cloud/src/service/ysm.rs` 的 `validate_state`/`merge`
与 `service/ysm/legacy.rs` 桥接校验并合并状态。

## 4. 设计要点

1. **新增 `YsmDisplayState` 记录**（客户端 `model/ysm/sync`）：字段与参考一致（生命/格挡按第 7 节决策
   决定是否保留），全部可空，构造校验沿用参考范围；只由本机玩家 `snapshot` 采集。
2. **同步段**：`YsmSyncMessage.State` 增加可选 `display` 段，增量语义与现有段一致（null=不变，
   present=替换）；不参与 `complete()` 必需项，以便旧发布方仍能产生完整快照。
3. **采集频率**：随现有 `YsmSyncConnection` 节奏（≥4 tick 一次、40 tick 快照），仅值变化时写入增量；
   输入量化（例如 1/127）以减少抖动与字节。
4. **读取规则（单一入口）**：`YsmDisplayQuery.of(context)`：本地玩家→实体；远端且有同步值→同步值；
   否则→原值。所有读取点改走该入口，不在各绑定里分散判断。
5. **隐私门控**：仅当用户允许 且 服务端声明能力时发布；接收端只接受已认证 owner 的状态（复用现有
   身份校验），不把对方值写回其实体。

## 5. 分阶段实施

| 阶段 | 内容 | 仓库 | 验证 |
| --- | --- | --- | --- |
| S1 | `YsmDisplayState` 记录、范围校验、本机 `snapshot`；`YsmDisplayQuery` 读取入口（暂无远端数据，行为不变） | 主 | 新单测：范围校验、本地读取不变；相关 YSM 单测 + `:fabric:build` |
| S2 | `YsmSyncMessage.State.display` 段与增量合并；`YsmSyncCodec` 升至 VERSION 2，解码兼容 VERSION 1（无 display）；编码按对端能力选择版本 | 主 | 编解码往返、v1↔v2 兼容、分片字节上限；Sync/SyncModel 单测 |
| S3 | 协议：`cloud.proto` `YsmState` 新增 `YsmDisplay display = 6`（可选，未知字段兼容）；重新发布并更新 `release.sha256`，同步 `omnm-cloud/protocol` 副本，`verify-release.ps1` 通过 | 主 + 后端 | 协议校验脚本、`YsmProtobuf` 往返单测 |
| S4 | 后端：`validate_state` 校验 display 范围/大小，`merge` 合并，legacy 桥接透传；能力表声明 `ysm_display_state_v1` | 后端 | Rust 单测（校验/合并/桥接/超限拒绝）、后端构建 |
| S5 | 客户端发布/接收接线：`YsmSyncConnection` 采集本机 display、按能力与用户设置发布；`YsmSyncReceiver` 写入远端 display | 主 | 双端 CPU 回放：远端读到同步值、本地不受影响、无能力时不发布 |
| S6 | 读取点切换：`MolangBindings`（xxa/yya/zza、food、effect_level、shield）、`QueryVariables`（flying、level、health）、`YsmPlayerActionState` fly 判定 | 主 | 绑定单测（本地/远端/缺省三路）、NativeState、VanillaState + `:fabric:build` |
| S7 | 用户设置项（是否分享显示状态）与 GUI 文案（中/英） | 主 | 配置读写单测；GUI 真实窗口验收 `MANUAL/PENDING` |

每阶段：先 RED → 最小实现 → 相关单测 + 编译（后端阶段为 Rust 测试与构建）→ 提交推送并核对远端 SHA →
更新 `AGENT_HANDOFF.md` → 查额度。双客户端实机验收记 `MANUAL/PENDING`。

## 6. 兼容与风险

- **新旧客户端混用**：v2 发布方遇 v1 接收方——Figura Cloud 路径需按对端能力降级为 v1（不带 display）；
  Protobuf 路径新增字段对旧端为未知字段，可安全忽略。
- **新客户端 + 旧服务端**：服务端无能力声明时不发布 display；旧服务端若对未知字段严格拒绝需确认
  （`validate_state` 现有行为待核）。
- **预算**：display 段最大约 256 个效果 × id 长度，需设单独上限并计入 32768 字节状态预算。
- **冻结协议**：S3 是对冻结发布的变更，需按 `protocol/README.md` 的发布流程处理，不得只改一侧副本。
- **隐私**：饥饿、经验、效果原本对他人不可见，发布即公开给能看到该模型的玩家。

## 7. 需要用户先决定

1. 是否实施 D025（涉及协议重新发布与后端）。
2. 字段范围：全部按参考，还是仅原版不同步的五项（输入、饥饿、飞行、经验、效果），去掉生命/格挡。
3. 隐私：默认开启还是默认关闭“分享显示状态”；是否仅对已授权观看者可见。
4. Figura Cloud（ping）路径是否也要支持，还是只做 Protobuf。
5. 后端改动是否由本 Agent 在 `omnm-cloud` 的 `master` 上实施并推送。

## 8. Figura Lua API 封装（用户 2026-10-27 追加）

原则：可查询的显示状态与 YSM 状态机，若 Figura 现有 Lua API 没有等价能力（平替），同时封装为
Figura 可调用的 Lua API。已有平替的不重复封装，只在文档中指明。Lua 读取与 Molang 读取共用
第 4 节的 `YsmDisplayQuery` / 同一份 `YsmPlayerActionState`，保证脚本、Molang 与实际动画不会互相矛盾。

### 8.1 现有 Figura / OmniMorph API 平替核对（已查源码）

| 能力 | 现有 API | 能否用于远端玩家 | 结论 |
| --- | --- | --- | --- |
| 生命/最大生命 | `LivingEntityAPI.getHealth/getMaxHealth` | 原版同步，可用 | 平替，不新增 |
| 格挡 | `LivingEntityAPI.isBlocking` | 使用物品状态同步，可用 | 平替，不新增 |
| 正在使用物品 | `LivingEntityAPI.isUsingItem` | 可用 | 平替，不新增 |
| 饥饿 | `PlayerAPI.getFood` | 读本地实体，远端为默认值 | **需封装**（同步值） |
| 经验等级 | `PlayerAPI.getExperienceLevel` | 同上 | **需封装** |
| 飞行 | `host:isFlying`、`ViewerAPI.isFlying` | 仅本机（`isHost`） | **需封装** |
| 状态效果 | `host:getStatusEffects` | 仅本机 | **需封装**（只含 id 与等级） |
| 移动输入 xxa/yya/zza | 无 | — | **需封装** |
| YSM 动作状态（ride/death/…/idle） | 无（仅 Molang `ctrl.*`） | — | **需封装** |
| 声明控制器当前状态 | `ysm_model:getControllerStates()` | 仅本模型 | 已有；补远端读取 |
| 原生状态机开关 / 播放 | `ysm_model:set/isNativeStateMachineEnabled`、`playAnimation/stopAnimation` | 本模型 | 已有，不新增 |
| 手部动作槽（hold/use/swing 当前片段） | 无 | — | **需封装** |
| 控制器状态时间、状态列表 | 无 | — | **需封装**（只读） |

不新增“强制切换控制器状态”的写接口：状态转换由作者控制器与条件决定，外部强写会破坏 D023 的
转换语义与同步一致性；需要时用已有 `playAnimation`/控件变量驱动条件。

### 8.2 新增 API（建议命名，开工前可调整）

全部 `@LuaWhitelist`，补 `LuaMethodDoc` 中/英文档条目（`assets/omni-morph/lang`）。

**A. `ysm_display`（新全局，读取任意玩家的显示状态）**

- `ysm_display:get(entity)` → 表 `{food, experience_level, flying, strafe, vertical, forward, effects = {[id]=level}}`；
  本地玩家读实体，远端读同步值，无数据的字段为 nil。
- 单项便捷方法：`getFood(entity)`、`getExperienceLevel(entity)`、`isFlying(entity)`、
  `getMovementInput(entity)`（返回 `FiguraVec3(strafe, vertical, forward)`）、`getEffectLevel(entity, id)`。
- `isSynced(entity)`：该玩家是否有有效同步值（区分“为 0”与“未知”）。
- 隐私：只返回对方已选择分享且服务端已转发的值；不新增任何发布/写入接口。

**B. `ysm_model` 扩展（本模型的状态机只读查询）**

- `getActionState()` → 当前原生动作状态名（`YsmPlayerActionState.State.animationName()`，与 `ctrl.*` 同源）。
- `getControllerState(name)`、`getControllerStateTime(name)`、`getControllerStateNames(name)`。
- `getHandActions()` → `{left = {hold=, action=, paused=}, right = {...}}`（`nativeHoldAnimations` /
  `nativeHandAnimations` / `PlayingAnimation.paused`）。
- `getNativeAnimation()` → 当前原生运动片段名。

**C. 远端玩家的 YSM 状态（只读）**

- `ysm_display:getActionState(entity)`、`ysm_display:getControllerStates(entity)`：从该玩家已加载的
  YSM 运行时读取（复用 `YsmSyncModel` 已同步的 controllers），无运行时返回 nil。

**D. 事件（可选，按需）**

- `events.YSM_ACTION_STATE_CHANGE(old, new)`、`events.YSM_CONTROLLER_STATE_CHANGE(controller, old, new)`：
  在状态机实际切换处触发，仅对本模型脚本；默认不开，避免每帧开销。

### 8.3 阶段归属

| 阶段 | 内容 |
| --- | --- |
| S1 追加 | B 组（本模型状态机只读查询）与 `ysm_display` 的本地读取路径；不依赖同步，可先交付 |
| S6 追加 | `ysm_display` 远端路径接入同步值；C 组远端只读查询 |
| S8（新） | D 组事件（若用户需要） |

验证：Lua API 单测覆盖本地/远端/无数据三路与 nil 语义；`FiguraDocsManager` 文档生成检查；
相关 YSM 单测 + `:fabric:build`；脚本实机调用 `MANUAL/PENDING`。

### 8.4 新增待决策

6. Lua API 命名：新增全局 `ysm_display`，还是并入 `ysm_model` / 扩展 `PlayerAPI`（扩展原版 API 会改变 Figura 既有语义，不建议）。
7. 是否需要 D 组状态变化事件。
8. B/C 组是否允许查询他人模型的控制器状态（只读，但会暴露对方模型的状态名）。
