# YSM 原生渲染引擎第二大阶段计划书：模型、动作、轮盘同步

> 范围：在第一大阶段“单人未加密 YSM 完整可用”的基础上，设计 YSM 模型、动作、轮盘与控件状态的多人同步。  
> 核心原则：YSM 必须兼容 Figura 后端同步体系；禁止改成 Sparkle-Morpher 那种要求 Minecraft 服务器安装服务端 Mod 的同步模式。  
> 后端边界：允许扩展 `sculptor` 的 HTTP/WebSocket 协议，允许新增 YSM 专用同步消息与限流规则，但必须保证原有 Figura Avatar、Lua ping、装备、订阅、reload 同步不回归。

## 1. 阶段定位

第二大阶段的目标不是把 Sparkle-Morpher 的网络层搬进 Figura，而是把 Sparkle-Morpher 的“可同步内容类型”和“增量编码思想”改造成 Figura 后端可承载的协议：

- YSM 模型资产继续作为 Figura Avatar 资产上传、下载、缓存和装备。
- YSM 动作、轮盘、控件、函数派生变量等运行时状态通过 Figura WebSocket 后端转发。
- 所有同步对象仍以玩家 UUID / Avatar 实例为中心，而不是 Minecraft 服务端 entityId。
- 普通 Minecraft 服务器不需要安装任何 Figura/YSM/Sparkle-Morpher 服务端模组。
- 原有 Figura 普通 Avatar 的上传、装备、订阅、WebSocket Event、Lua ping 语义保持不变。

完成后，安装 Figura YSM native 的玩家在普通多人服务器中，应能通过 Figura 后端看到其他玩家的 YSM 模型、贴图、轮盘动作和必要的动作状态，而无需游戏服务器参与。

## 2. 当前同步基线

### 2.1 Figura 客户端

当前客户端同步核心在：

- `common/src/main/java/org/figuramc/figura/backend2/NetworkStuff.java`
- `common/src/main/java/org/figuramc/figura/backend2/HttpAPI.java`
- `common/src/main/java/org/figuramc/figura/backend2/websocket/C2SMessageHandler.java`
- `common/src/main/java/org/figuramc/figura/backend2/websocket/S2CMessageHandler.java`

现有行为：

- HTTP：
  - `GET /api/:uuid` 查询用户资料、已装备 Avatar、hash。
  - `GET /api/:uuid/avatar` 下载 Avatar。
  - `PUT /api/avatar` 上传当前 Avatar。
  - `POST /api/equip` 装备 Avatar。
  - `GET /api/limits` 获取上传/下载限制。
- WebSocket C2S：
  - `Token`
  - `Ping(id, sync, data)`
  - `Sub(uuid)`
  - `Unsub(uuid)`
- WebSocket S2C：
  - `Auth`
  - `Ping(uuid, id, sync, data)`
  - `Event(uuid)`
  - `Toast`
  - `Chat`
  - `Notice`
- 客户端每 tick 根据当前联机玩家 UUID 订阅/退订。
- 收到 `Event(uuid)` 后 reload 对应 Avatar。
- 收到 `Ping(uuid, id, data)` 后调用 `Avatar.runPing(id, data)`。

### 2.2 sculptor 后端

当前后端同步核心在：

- `sculptor/src/api/figura/websocket/types/c2s.rs`
- `sculptor/src/api/figura/websocket/types/s2c.rs`
- `sculptor/src/api/figura/websocket/handler.rs`
- `sculptor/src/api/figura/profile.rs`
- `sculptor/src/state/state.rs`

现有行为：

- 后端认证 WebSocket token。
- 每个用户 UUID 有自己的 session channel。
- 每个被订阅 UUID 有 broadcast channel。
- 客户端 `Sub(uuid)` 后，后端把该 uuid 的 broadcast 消息转发到订阅者。
- Lua ping 由后端按订阅关系广播，后端不解析 Minecraft 世界状态。
- 上传、装备、删除 Avatar 后，后端广播 `Event(uuid)`，订阅者自行重新拉取 Avatar。

### 2.3 第一大阶段 YSM runtime

当前 YSM 运行时已有：

- `YsmModelRuntime`
- `YsmActionRuntime`
- `YsmAnimationPlayer`
- `YsmControllerRuntime`
- `YsmMolangFunctionRuntime`
- `YsmResourceIndex`
- `AvatarControlRuntime`
- `YsmModelAPI`

这些类提供了同步事件可落地的位置：

- 模型资产：Avatar NBT / YSM resource index。
- 贴图：`textureId`。
- 动作：`YsmActionRuntime` active action。
- 轮盘：`YsmActionWheelLayoutStore` local layout。
- 控件：`AvatarControlRuntime` / YSM control values。
- controller/function 派生变量：Molang `v.*` / action state。

## 3. 禁止照搬 Sparkle-Morpher 的内容

Sparkle-Morpher 可参考，但以下模式禁止迁移：

- 不注册 Minecraft custom payload 作为 YSM 同步主通道。
- 不要求 Minecraft 服务器安装 Sparkle-Morpher/Figura/YSM 服务端 Mod。
- 不使用服务器 `ServerPlayer`、entity tracking、entityId 作为同步源。
- 不通过游戏服务器广播 `S2CSetModelAndTexturePacket`。
- 不通过游戏服务器广播 `C2SSyncAnimationExpressionPacket` / `S2CSyncAnimationExpressionPacket`。
- 不把模型上传改成 Minecraft 服务器分片上传。
- 不默认同步血量、饥饿、药水、输入、经验等 Minecraft 实体状态。
- 不让后端执行 Molang、Lua、动画 controller。

Sparkle-Morpher 可借鉴的只有：

- 状态增量编码和 bit flags。
- full sync / delta sync 分层。
- 模型 hash / texture id / action id 的小消息同步。
- 动画表达式同步的“事件化”思想。
- 版本握手和能力协商。
- 限流、去重、过期丢弃、按订阅者广播。

## 4. 第二大阶段同步目标

### 4.1 模型同步

目标：远端玩家能看到本地玩家装备的 YSM Avatar。

原则：

- YSM 模型本体仍打包为 Figura Avatar 上传到 sculptor。
- 后端仍通过装备信息和 hash 通知订阅者 reload。
- 远端客户端下载 Avatar 后，本地判断这是普通 Figura Avatar 还是 YSM native Avatar。
- 普通 Figura Avatar 同步逻辑不变。

需要补齐：

- YSM Avatar NBT 必须包含足够资源：
  - kind/format。
  - resource index。
  - main/arm geometry。
  - texture entries 或 texture asset 引用。
  - animation/controller/functions/actions/controls。
  - projectile/vehicle 子资源。
- `GET /api/:uuid` 的 equipped hash 仍能代表完整 YSM Avatar。
- YSM 模型资源大小要进入 `maxAvatarSize` 限制评估。
- 如果模型过大，需要后续增量资产方案，但第二大阶段不先改 HTTP 上传主路径。

### 4.2 贴图同步

目标：远端看到的 YSM 贴图选择与本地玩家一致。

方案：

- 本地玩家切换贴图时：
  - 更新本地 `YsmModelRuntime.textureId`。
  - 若贴图选择属于 Avatar 持久选择，可写入 Avatar NBT 并触发普通 `Event(uuid)` reload。
  - 若贴图切换是运行时动作，可发送 YSM runtime delta。
- 远端收到：
  - 校验该 texture id 是否存在于已下载 Avatar 的 texture entries。
  - 存在则热切换远端 `YsmModelRuntime`。
  - 不存在则忽略并记录 debug。

建议分层：

- 装备/默认贴图：走 Avatar asset + Event reload。
- 轮盘/动作临时贴图：走 YSM runtime delta。

### 4.3 动作同步

目标：远端能看到本地玩家通过 YSM 轮盘触发的 extra animation/action。

同步内容：

- action id。
- trigger type：
  - press
  - hold start
  - hold end
  - toggle on
  - toggle off
  - stop
  - cancel all。
- logical time / sequence。
- optional category/page/slot。
- optional controller slot。

原则：

- 发送的是动作事件，不是每帧骨骼姿态。
- 远端客户端用本地已下载的 YSM Avatar 资源执行同一 action。
- 丢包或乱序时，用 sequence 和 TTL 丢弃过期事件。
- 远端缺少 action id 时忽略，不强制 reload。

### 4.4 轮盘布局同步

目标：区分“用户自己的轮盘布局”和“别人看到的动作表现”。

规则：

- 轮盘布局本质是本地 UI 配置，不需要实时广播给其他玩家。
- 需要同步的是动作触发结果，不是对方轮盘页面怎么摆。
- 可选同步：
  - 用户公开的动作 label/icon/color override。
  - 模型默认 action schema hash。
- 不同步：
  - 用户个人热键。
  - 用户个人轮盘页面布局。
  - 编辑器状态。

后端可支持一个“公开 action metadata profile”，但不能成为动作播放必要条件。

### 4.5 控件同步

目标：远端能看到影响模型外观和动作状态的 YSM controls。

控件分级：

- Public visual controls：
  - 表情。
  - 车辆显示。
  - 尾巴/耳朵模式。
  - 贴图/颜色。
  - 武器/附件显示。
- Local-only controls：
  - UI 布局。
  - debug 开关。
  - 第一人称只影响自己视角的配置。
  - 性能选项。
- Private controls：
  - 不应公开的用户输入、文本或按键。

同步规则：

- 只有 schema 标记为 `sync: public` 或默认分类为 visual 的 control 才可同步。
- 控件值发送为 typed delta：
  - bool
  - int
  - float
  - enum/string id
  - color rgba
- 远端只应用到 YSM runtime，不写入远端用户本地配置。
- 后端不解释具体控件含义，只限流和转发。

### 4.6 函数、controller 与派生变量同步

原则：

- 不同步每帧 `v.*` 全量变量。
- 同步源头事件：
  - action trigger。
  - public control value。
  - explicit ysm.sync key/value。
- 远端本地运行 functions/controller 计算派生变量。

`ysm.sync` 策略：

- 第一版不允许任意 Molang 表达式远端执行。
- 允许同步结构化 key/value：
  - namespace。
  - key。
  - typed value。
  - sequence。
  - ttl。
- 远端将其写入 YSM sync variable store，例如 `sync.<key>` 或指定 `v.*` 映射。
- 是否允许写入 `v.*` 由模型 schema 或 permission 决定。

禁止：

- 广播任意 Molang 源码让远端执行。
- 广播任意 Lua 代码。
- 高频同步全量骨骼矩阵。

## 5. 目标协议设计

### 5.1 协议兼容策略

当前 WebSocket 类型占用：

- C2S：0-3。
- S2C：0-5。

建议新增版本化扩展，避免破坏旧客户端：

- C2S `YsmState` = 4。
- C2S `Capabilities` = 5。
- S2C `YsmState` = 6。
- S2C `Capabilities` = 7。

旧客户端行为：

- 旧客户端不会发送 4/5。
- 旧后端收到 4/5 会 BadEnum，可能断开，因此客户端必须先通过 capability 判断后端是否支持 YSM sync。
- 新后端向未声明支持 YSM sync 的旧客户端，不发送 6/7。

能力协商：

```text
C2S Capabilities
  protocolVersion
  supportedFeatures bitset
  maxYsmMessageSize
  maxYsmMessagesPerSecond

S2C Capabilities
  acceptedProtocolVersion
  enabledFeatures bitset
  limits
```

特性 bit：

- `YSM_MODEL_KIND`
- `YSM_ACTION_EVENT`
- `YSM_CONTROL_DELTA`
- `YSM_TEXTURE_DELTA`
- `YSM_SYNC_VAR`
- `YSM_FULL_STATE`
- `YSM_COMPRESSED_PAYLOAD`
- `YSM_DEBUG_NOTICE`

### 5.2 YSM state 消息外壳

C2S：

```text
type: 4
avatarInstanceId: u64 or hash prefix
stateKind: u8
sequence: u32
flags: u16
payload: bytes
```

S2C：

```text
type: 6
sourceUuid: uuid
avatarInstanceId: u64 or hash prefix
stateKind: u8
sequence: u32
flags: u16
payload: bytes
```

`avatarInstanceId` 用途：

- 防止远端旧 Avatar 收到新 Avatar 的动作事件。
- 可由 Avatar hash、YSM modelKey、load generation 组合生成。
- 远端不匹配则丢弃，并可请求 full state 或等待普通 reload。

`stateKind`：

- `0 = FULL_STATE`
- `1 = ACTION_EVENT`
- `2 = CONTROL_DELTA`
- `3 = TEXTURE_DELTA`
- `4 = SYNC_VAR_DELTA`
- `5 = WHEEL_PUBLIC_META`
- `6 = REQUEST_FULL_STATE`
- `7 = RESET_RUNTIME_STATE`

### 5.3 Full state

用途：

- 订阅者刚加载远端 Avatar。
- 远端错过多条 delta。
- 本地切换 YSM Avatar 后初始化公开状态。

内容：

- protocol version。
- avatar hash/model key。
- texture id。
- active action list。
- public controls snapshot。
- sync variable snapshot。
- action sequence base。
- optional current controller/action time。

限制：

- 不包含模型二进制资源。
- 不包含轮盘个人布局。
- 不包含私有 controls。
- 不包含每帧骨骼矩阵。

发送时机：

- 本地 Avatar 加载完成并后端支持 YSM sync。
- 收到订阅者 `REQUEST_FULL_STATE`。
- 本地 YSM runtime 发生 reset。
- 可低频重发，作为状态自愈。

### 5.4 Action event

payload：

```text
actionId: string
trigger: u8
category: optional string
slot: optional u8
startedAtTick: optional u32
durationHint: optional u16
flags: u16
```

远端应用：

- 查找 `YsmActionRuntime`。
- 校验 action id。
- 按 trigger 执行 start/stop/toggle。
- 更新 remote action state。
- 不触发本地 UI，也不写入用户配置。

### 5.5 Control delta

payload：

```text
count: varint
entries:
  id: string
  type: u8
  value: typed
```

远端应用：

- 校验 control id 存在。
- 校验 control schema 允许 public sync。
- 写入 remote control runtime。
- 同步到 Molang variable binding。
- 触发 functions/controller 更新。

### 5.6 Texture delta

payload：

```text
textureId: string
reason: u8
```

远端应用：

- 如果 texture id 存在，热切换。
- 如果不存在，忽略。
- 若 reason 是 persistent mismatch，可等待普通 Avatar reload。

### 5.7 Sync var delta

payload：

```text
namespace: string
count: varint
entries:
  key: string
  type: u8
  value: typed
  ttl: optional u16
```

远端应用：

- 写入 YSM remote sync variable store。
- 默认不直接写任意 `v.*`。
- 只有 schema 声明允许时才映射到 `v.*`。

## 6. sculptor 后端改造计划

### 6.1 WebSocket 类型扩展

修改：

- `sculptor/src/api/figura/websocket/types/c2s.rs`
- `sculptor/src/api/figura/websocket/types/s2c.rs`

新增：

- `C2SMessage::Capabilities(...) = 4`
- `C2SMessage::YsmState(...) = 5`
- `S2CMessage::Capabilities(...) = 6`
- `S2CMessage::YsmState(Uuid, ...) = 7`

也可以反过来编号，但必须在客户端和后端统一，并保留现有 0-5 语义。

验收：

- 原消息 0-5 编码完全不变。
- 普通 Figura 客户端仍能认证、订阅、ping、reload。
- 新客户端连接后能获知后端是否支持 YSM sync。

### 6.2 Session capability

扩展 `WSSession`：

- `supports_ysm_sync`
- `ysm_protocol_version`
- `ysm_feature_flags`
- `ysm_rate_bucket`
- `ysm_max_message_size`

订阅转发时：

- 只向支持 YSM sync 的订阅者转发 `S2C::YsmState`。
- 不支持的订阅者仍收到普通 `Event` / `Ping`。

### 6.3 Rate limit 与 size limit

新增后端限制：

- 每用户每秒 YSM state 消息数。
- 每用户每秒 YSM bytes。
- 单条 YSM state 最大大小。
- full state 更低频率。
- sync var 数量上限。

超限处理：

- 丢弃消息。
- 发送 `Notice` 或新 `YsmDebugNotice`。
- 不断开连接，除非持续滥用。

### 6.4 广播模型

沿用现有 subscribe channel：

```text
source user session
  -> source user's broadcast channel
  -> subscribed clients
```

不新增 Minecraft 世界/服务器概念。

后端不做：

- 不保存世界位置。
- 不判断玩家是否同一 Minecraft 服务器。
- 不理解 entityId。
- 不执行 action/controller/molang。

后端只做：

- 鉴权。
- 能力协商。
- 限流。
- 广播。
- 可选最近 full state 短期缓存。

### 6.5 可选短期状态缓存

为解决订阅者刚加入时错过状态，可在 sculptor 内存中保留每个在线用户最近一份 YSM full state：

- key：source uuid。
- value：full state bytes + timestamp + avatarInstanceId。
- TTL：例如 30-120 秒。
- 用户断开后清除或等待 TTL。
- Avatar reload/event 后旧 full state 作废。

注意：

- 不持久化到数据库。
- 不替代 Avatar 资产。
- 不保存私有 controls。

## 7. 客户端改造计划

### 7.1 NetworkStuff 扩展

新增：

- `YsmSyncManager`
- `YsmSyncProtocol`
- `YsmSyncMessageWriter`
- `YsmSyncMessageReader`
- `YsmSyncRateLimiter`

`NetworkStuff` 新增入口：

- `sendYsmState(YsmSyncMessage msg)`
- `requestYsmFullState(UUID target)`
- `sendYsmCapabilities()`

保持：

- `sendPing` 不改语义。
- `uploadAvatar` 不改普通 Figura 流程。
- `subscribeAll` 仍按在线玩家 UUID 订阅。

### 7.2 S2CMessageHandler 扩展

新增处理：

- `CAPABILITIES`
- `YSM_STATE`

收到 YSM state：

- 读取 source uuid。
- 找到 `AvatarManager.getLoadedAvatar(uuid)`。
- 若 Avatar 不存在或不是 YSM，暂存短期队列或丢弃。
- 校验 avatarInstanceId。
- 分发给 `YsmSyncManager.applyRemoteState(uuid, message)`。

### 7.3 YsmModelRuntime 同步接口

新增：

- `YsmSyncState localSyncState`
- `YsmRemoteSyncState remoteSyncState`
- `buildFullSyncState()`
- `applyFullSyncState(...)`
- `buildActionEvent(...)`
- `applyActionEvent(...)`
- `buildControlDelta(...)`
- `applyControlDelta(...)`
- `buildTextureDelta(...)`
- `applyTextureDelta(...)`
- `buildSyncVarDelta(...)`
- `applySyncVarDelta(...)`

生命周期：

- 本地 YSM Avatar load 完成后发送 full state。
- 本地 action/control/texture/sync var 变化时发送 delta。
- 远端 Avatar reload 后请求 full state。
- Avatar unload 时清理 remote sync state。

### 7.4 ActionRuntime 接入

本地动作触发点：

- 轮盘点击。
- hotkey。
- Lua 调用。
- function/controller 触发的公开 action。

发送：

- 只发送 public action。
- 合并同 tick 重复 stop/start。
- 加 sequence。

远端应用：

- 远端 action runtime 标记为 remote source。
- 不回传，避免回声循环。
- 不触发本地玩家权限弹窗。

### 7.5 ControlRuntime 接入

本地 control change：

- 判断 schema `sync`。
- 判断权限和 privacy。
- 进入 dirty set。
- 每 tick 或固定间隔批量发送 delta。

远端 control：

- 写入 remote overlay。
- 不覆盖本地用户配置 store。
- Avatar reload 后清空 overlay，等待 full state。

### 7.6 `ysm.sync` 接入

Molang `ysm.sync(...)` 在第二大阶段升级为：

- 本地玩家：
  - 解析 key/value。
  - 写入本地 sync var。
  - 发送 `SYNC_VAR_DELTA`。
- 远端玩家：
  - 不允许再广播。
  - 只应用收到的 remote sync var。

限制：

- key 长度限制。
- value 类型限制。
- 每 tick 数量限制。
- 不允许源码表达式广播。

### 7.7 普通 Figura 兼容

必须保证：

- 普通 Figura Avatar 不创建 YSM sync runtime。
- Lua ping 仍按原 API 工作。
- 用户没有后端 YSM sync 支持时：
  - YSM 单人功能正常。
  - 多人只同步 Avatar 资产和普通 Lua ping。
  - 不崩溃，不断线。
- 普通 Avatar 的 upload/equip/reload 不因 YSM 协议扩展改变。

## 8. 数据权限与隐私

同步能力必须分级：

- `YSM_SYNC_ACTION`
  - 允许同步动作事件。
- `YSM_SYNC_VISUAL_CONTROL`
  - 允许同步公开视觉控件。
- `YSM_SYNC_TEXTURE`
  - 允许同步贴图热切换。
- `YSM_SYNC_VARIABLE`
  - 允许 `ysm.sync` 结构化变量。

默认建议：

- action：允许。
- visual control：允许，但 schema 可设 private。
- texture：允许。
- arbitrary sync var：需要模型 schema 或用户权限允许。
- keybind、debug、本地第一人称、私有文本：默认不允许同步。

后端限制：

- 不读取 payload 语义，但限制大小和频率。
- 不持久化运行时状态。

客户端限制：

- 发送前过滤 private controls。
- 接收后只作用于 remote avatar runtime。
- debug UI 能显示哪些字段被同步。

## 9. 与 Sparkle-Morpher 的同步能力对照

| Sparkle-Morpher 能力 | Figura 第二大阶段处理 |
| --- | --- |
| 服务器下发模型 | 不迁移；模型走 Figura HTTP Avatar 上传/下载 |
| C2S/S2C 模型切换 packet | 改为 Figura equip + WebSocket Event reload |
| entityId 定位目标 | 改为 UUID + Avatar instance id |
| tracking entity 广播 | 改为 sculptor subscription broadcast |
| 玩家状态 full/delta | 不同步 Minecraft 实体状态；只同步 YSM visual runtime |
| Molang 表达式广播 | 禁止源码广播；改为结构化 sync var/action/control |
| extra animation packet | 改为 `YSM_STATE/ACTION_EVENT` |
| 模型分片上传 | 不迁移；可后续改 HTTP 上传体验 |
| bit flags delta | 可借鉴，用于 control/action/sync var 编码 |
| full sync | 可借鉴，用于 YSM runtime public state 自愈 |
| 服务端限速 | 必须加入 sculptor WebSocket rate limit |

## 10. 实施里程碑

### M1：协议能力协商

任务：

- 后端新增 capability 消息。
- 客户端连接后发送 YSM sync capability。
- 后端返回支持特性和限制。
- 旧后端/旧客户端兼容路径。

验收：

- 新客户端连接旧后端不会发送 YSM state。
- 旧客户端连接新后端仍正常使用 Figura。
- debug 能显示 YSM sync enabled/disabled。

### M2：YSM state WebSocket 通道

任务：

- 后端新增 `YsmState` C2S/S2C。
- session 记录 YSM capability。
- 订阅广播只发给支持者。
- 客户端 reader/writer 实现。

验收：

- 本地发送测试 YSM state，订阅者能收到。
- 不支持 YSM sync 的客户端不会收到未知消息。
- 普通 ping/Event 不回归。

### M3：Full state 与 Avatar instance 校验

任务：

- 定义 avatarInstanceId。
- 本地 YSM Avatar load 后发送 full state。
- 远端加载 YSM Avatar 后请求 full state。
- mismatch 时丢弃 delta。

验收：

- 远端先加载 Avatar 再收到 full state，状态正确。
- Avatar 切换后旧动作事件不会应用到新模型。

### M4：Action event 同步

任务：

- `YsmActionRuntime` 触发时构造 action event。
- WebSocket 广播 action event。
- 远端应用 action event。
- 支持 stop/cancel/toggle/hold。

验收：

- 两个客户端在普通 Minecraft 服务器中，A 触发 YSM 轮盘动作，B 可见。
- 不需要服务器安装任何 Mod。
- action 不存在时远端安全忽略。

### M5：Control 与 texture delta

任务：

- public controls dirty batching。
- texture delta。
- 远端 remote overlay。
- full state 包含当前 public controls 和 texture。

验收：

- A 切表情/车辆/贴图，B 可见。
- B 不把 A 的 control 写进自己的本地配置。
- private/local control 不发送。

### M6：`ysm.sync` 结构化变量

任务：

- 定义 sync var schema。
- Molang binding 接入。
- 限流。
- 远端 sync var store。

验收：

- 模型用 `ysm.sync("pose", value)` 可让远端同变量变化。
- 不允许广播 Molang/Lua 源码。
- 超限有 Notice/debug。

### M7：后端限流与短期 full state 缓存

任务：

- per-session rate bucket。
- max message size。
- optional last full state cache。
- Avatar Event 后清理缓存。

验收：

- 高频 spam 被丢弃或警告。
- 新订阅者能快速拿到最近 full state。
- 后端内存不会无限增长。

### M8：回归测试与兼容验证

任务：

- sculptor message encode/decode tests。
- client message reader/writer tests。
- 普通 Figura ping/reload 回归。
- 双客户端实机测试。
- YSM action/control/texture/sync var 测试模型。

验收：

- 普通 Figura Avatar 上传、装备、下载、reload、Lua ping 正常。
- YSM Avatar 资产同步正常。
- YSM action/control/texture 远端可见。
- 旧后端/旧客户端兼容。

## 11. 协议风险与控制

- 旧协议兼容风险：必须 capability 先行，新客户端不能盲发新 opcode 给旧后端。
- 频率风险：动作和控件必须 delta 化、合并、限流，不做每帧姿态同步。
- 隐私风险：control schema 必须区分 public/local/private。
- 状态错配风险：avatarInstanceId 必须阻止旧模型事件污染新模型。
- 重放风险：sequence + TTL 丢弃过期事件。
- 回声循环风险：remote-applied action/control 不得再次广播。
- 后端压力风险：sculptor 只转发和短期缓存，不解析/执行 YSM。
- 普通 Figura 回归风险：原 opcode、HTTP API、Lua ping 路径不改语义。

## 12. 第二大阶段完成定义

第二大阶段完成时，应满足：

- YSM sync 通过 Figura/sculptor 后端完成，不依赖 Minecraft 服务器 Mod。
- 普通 Figura Avatar 原有同步全部正常。
- 后端支持 YSM capability 协商、YSM state 广播、限流和可选 full state 缓存。
- 客户端支持 YSM full state、action event、control delta、texture delta、sync var delta。
- YSM 模型资产仍走 Figura Avatar HTTP 上传/下载/hash 缓存。
- YSM 轮盘动作可被其他安装支持版本客户端看见。
- public controls 和贴图变化可被远端看见。
- `ysm.sync` 只同步结构化 key/value，不执行远端源码。
- 旧客户端/旧后端有安全降级路径。
- 双客户端普通服务器实测通过：无需服务器安装任何额外 Mod。

## 13. 推荐提交拆分

1. `Add YSM sync capability negotiation to websocket protocol`
2. `Add sculptor YSM state relay with per-session feature flags`
3. `Add client YSM sync message reader and writer`
4. `Add YSM full state and avatar instance matching`
5. `Sync YSM action events through Figura backend`
6. `Sync public YSM controls and texture deltas`
7. `Implement structured ysm.sync variable deltas`
8. `Add sculptor YSM rate limits and full state cache`
9. `Add YSM sync debug UI and regression tests`
10. `Verify legacy Figura sync compatibility`

每个提交都必须附带普通 Figura 回归说明，避免 YSM 同步扩展破坏现有用户。
