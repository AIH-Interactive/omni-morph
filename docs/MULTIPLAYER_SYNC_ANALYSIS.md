# omnm-cloud 多人同步与默认可见性分析

- 分析日期：2026-10-25
- 实际部署：`omnm-cloud`（`spm-cloud` 仅作参考，不纳入本文结论）
- 性质：后端静态代码分析；未改代码，未执行真实双客户端/生产环境验证
- 重点：为什么 YSM 可以多人同步，而 Figura 模型和装饰不能；以及“提交形象后默认私有”在 omnm-cloud 中的实际含义

---

## 1. 结论

1. **omnm-cloud 对 YSM 有专用链路**：
   - 通过 `x-omnimorph-models: ysm` 识别 YSM 客户端；
   - 模型装备投影区分 `primary`（YSM）与 `figura_compatibility`（Figura）；
   - 持久化并转发 `YsmSnapshot` / `YsmDelta` / `YsmEvent`；
   - 订阅端按模型身份、revision、content/schema hash、可见性和 ACL 过滤。

2. **Figura/装饰没有等价链路**：
   - `event_visible` 对 `OutfitStateChanged` 直接返回 `false`，装饰状态变化不广播；
   - `event_visible` 只接受 `model_type` 为 2/3（YSM_PLAIN/YSM_ENCRYPTED）的 `primary`，Figura 的 `model_type = 1` 被拒绝；
   - `outfit_state` 是实例状态 HTTP 端点，不携带装饰模型/纹理/脚本资产；
   - 本地 `LOCAL` 资源没有 `library_models` / `asset_revisions` 行，观察者拿不到文件；
   - 套装发布只接受 CLOUD 依赖，本地基础 + 本地装饰无法成为可分发的云端套装。

3. **omnm-cloud 实时库上传主路径没有“默认 PRIVATE”**：
   - `Metadata.visibility` 是必需字段，`library_models` 没有数据库默认值；
   - 上传服务把请求中的 visibility 原样写入；
   - 新上传初始 `published = 0`，commit 后发布；命中封禁哈希时 commit 会强制 `visibility = 'PRIVATE'`；
   - 因此“提交后是私有”需要区分：模型库 `visibility`、审核/封禁状态、以及**私有装备默认不向其他玩家展示**（`show_private_equipment` 默认 0）这三件事。

---

## 2. omnm-cloud 后端拓扑

与多人同步直接相关的路由/模块：

| 模块 | 作用 |
| --- | --- |
| `src/api/library/uploads.rs` | `/api/library/v1/uploads` 上传保留、写 body、commit |
| `src/service/library/uploads.rs` | 写入 `library_models`/`library_revisions`，决定 `published` 与封禁 |
| `src/service/library/distribution.rs` | 观察者可见性、YSM 事件过滤、装备投影 |
| `src/storage/library.sql` | `library_models` 的 visibility/published/moderation 约束 |
| `src/storage/library_equipment.rs` | 私有/公开装备的展示默认值 |
| `src/api/library/outfit_state.rs` | 装饰实例状态的 HTTP 读写 |
| `src/api/figura/profile.rs` | YSM/Figura 装备投影选择 |
| `src/api/figura/websocket/handler.rs` | YSM 快照/增量/事件订阅与转发 |
| `src/storage/mod.rs` | `ysm_snapshot` / `publish_legacy_ysm` |
| `src/service/ysm/*` | YSM 快照、增量、事件、恢复 |
| `protocol/omnimorph/cloud/v1/cloud.proto` | `YsmState`/`YsmSnapshot`/`YsmDelta`/`YsmEvent` 结构 |

---

## 3. YSM 为什么可以同步

### 3.1 客户端能力与装备投影

`src/api/figura/profile.rs`：

```text
请求头 x-omnimorph-models: ysm
  → 选择 equipped.primary
否则
  → 选择 equipped.figura_compatibility
```

`EquippedState`（`cloud.proto`）同时包含 `primary` 和 `figura_compatibility`；YSM 是主投影。

### 3.2 后端持久化 YSM 状态

`src/storage/mod.rs`：

- `publish_ysm` / `publish_legacy_ysm` 把 YSM 帧或 protobuf 状态写入 `manifest.ysm`；
- `ysm_snapshot(owner, now)` 生成当前快照；
- `YsmSnapshot` 包含 `YsmIdentity` + `YsmState`：
  - 贴图索引
  - 控件值
  - 动作状态
  - 控制器状态
  - 播放槽位/权重/时间

`YsmState` 只有模型内索引和值，没有模型文件字节。

### 3.3 订阅转发与可见性

`src/api/figura/websocket/handler.rs`：

- 只有声明 `x-omnimorph-models: ysm` 的连接才会收到 YSM 快照/增量；
- `sub_worker` 在订阅开始时检查 `can_observe_model`；
- 每条事件经过 `event_visible`；
- 发送边界再次检查，避免策略变化后继续发送。

`src/service/library/distribution.rs::event_visible` 对 YSM 要求：

- 观察者能看到 owner 的 **primary equipped model**；
- `avatar.model_type` 为 `2`（YSM_PLAIN）或 `3`（YSM_ENCRYPTED）；
- `YsmIdentity` 的 owner/revision/content hash/schema hash 与装备一致；
- 当前快照的 publisher/generation/sequence 不低于消息。

**结论**：omnm-cloud 的 YSM 同步 = 模型资产分发 + 后端 YSM 状态快照/增量；前提是观察者能取得同一模型资产。

---

## 4. Figura 模型为什么不能同样同步

### 4.1 Figura 不在 YSM 的 primary 投影

`profile.rs` 中普通 Figura 客户端取 `figura_compatibility`，不是 `primary`。  
`event_visible` 对 YSM 事件要求 `primary.model_type` 为 2/3，Figura 的 `model_type = 1` 直接被拒绝。

因此 Figura 即使已装备、已上传，也不会进入 YSM 状态订阅链路。

### 4.2 没有 Figura 状态事件

`event_visible` 中：

```rust
CloudEvent::OutfitStateChanged { .. } => return Ok(false),
```

配装/装饰实例的状态变化不进入订阅流。

`outfit_state.rs` 只提供：

```text
GET /api/library/v1/outfits/{owner}/{instance}/state
PUT /api/library/v1/outfits/{instance}/state
```

这是按实例的状态读写端点，不是观察者订阅模型状态的通道；也不包含模型/纹理/脚本资产。

### 4.3 本地 Figura 资源没有后端资产

`library_models` / `library_revisions` 只登记上传后的云端资产。  
本地 `omnimorph/avatars/...` 没有 `asset_id`，`OutfitConfig.ResourceRef` 的 `LOCAL` 引用在服务端无法解析为 blob。

观察者只能拿到 `library_project_equipped` 返回的、已上传且可见/授权的资产；本地文件不在其中。

### 4.4 公开云端 Figura 仍受装备和可见性约束

即使 Figura 模型上传为 PUBLIC：

- 必须在 `equipped`/`figura_compatibility` 投影中；
- 观察者必须通过 `resource_access`；
- 客户端还必须按 `AvatarDescriptor` 下载/缓存模型。

当前没有“本地 Figura 装备后自动上传并推送给同服玩家”的流程。

---

## 5. 装饰为什么不能同步

### 5.1 实例状态不等于资产

装饰实例包含：

- `instance_id`
- `resource`（LOCAL/CLOUD 引用）
- `anchor` / `transform`
- `controls` / `lua_state`

`outfit_state.rs` 的 `InstanceState` 只保存/读取这些状态。  
装饰的 `cosmetic.json`、模型、纹理、`script.lua` 都不在状态端点中。

### 5.2 本地依赖不可发布

`src/service/library/outfit_uploads.rs` 的套装发布输入要求依赖为 CLOUD 资源；本地资源不能作为可分发依赖。  
对应的客户端准备也会拒绝 `LOCAL` 依赖（`OUTFIT_DEPENDENCY_NOT_DISTRIBUTABLE`）。

### 5.3 结果

- 观察者最多拿到实例状态，且 `OutfitStateChanged` 当前不广播；
- 没有装饰资产，状态无法渲染；
- 本地装饰没有资产 ID，后端无法按装备投影分发。

---

## 6. “默认提交后私有”在 omnm-cloud 中的含义

### 6.1 模型库可见性不是后端默认私有

`src/service/library/uploads.rs`：

```rust
let visibility = match input.metadata.visibility {
    Visibility::Public => "PUBLIC",
    Visibility::Private => "PRIVATE",
};
```

- `library_models` 建表 SQL 没有默认值，只有 `CHECK(visibility IN ('PUBLIC','PRIVATE'))`；
- 新行初始 `published = 0`，commit 后 `published = 1`；
- commit 时若命中封禁哈希：

```sql
UPDATE library_models SET published = 1,
  moderation_status = CASE WHEN ?2 THEN 'BLOCKED' ELSE 'ACTIVE' END,
  visibility = CASE WHEN ?2 THEN 'PRIVATE' ELSE visibility END
```

即：**omnm-cloud 的上传主路径不会把 PUBLIC 默认改成 PRIVATE**；变成 PRIVATE 只能来自请求 metadata、封禁/审核，或旧数据。

### 6.2 私有装备默认不展示

`src/storage/library_equipment.rs`：

```sql
CASE WHEN m.visibility = 'PRIVATE'
     THEN COALESCE(s.show_private_equipment, 0)
     WHEN m.visibility = 'PUBLIC'
     THEN COALESCE(w.show_equipment, 1)
```

- 私有模型：`show_private_equipment` 默认 `0`，默认不向其他玩家展示；
- 公开模型：`show_equipment` 默认 `1`。

这容易和“模型库 visibility = PRIVATE”混淆：模型可能仍是 PUBLIC，但**私有装备默认不展示**；反之，模型库是 PRIVATE 时，装备展示默认关闭。

### 6.3 核对方法

如果实际看到提交后 PRIVATE，请核对：

1. 上传请求 metadata 的 `visibility` 值；
2. `POST /api/library/v1/uploads` 返回的 `model_id` 对应的 `library_models.visibility`；
3. 该行 `published`、`moderation_status` 是否为 `0`/`BLOCKED`；
4. 是否命中封禁哈希；
5. 当前装备的 `show_private_equipment` / `show_equipment` 状态。

在没有这些数据前，不能把“提交后私有”归因于 omnm-cloud 的实时库默认值——代码显示实时库默认由请求决定。

---

## 7. 关键差异总表

| 维度 | YSM | Figura | 装饰 |
| --- | --- | --- | --- |
| 模型资产投影 | `primary` | `figura_compatibility` | 无独立模型投影 |
| 状态事件 | `YsmSnapshot/Delta/Event` | `OutfitStateChanged` 不广播 | `OutfitStateChanged` 不广播 |
| 事件过滤 | `model_type 2/3` + 哈希匹配 | `model_type = 1` 被拒绝 | 直接 false |
| 本地资源 | 需可取得模型资产 | 本地文件无资产 ID | 本地依赖不可发布 |
| 观察者获取 | 资产 + 状态 | 云端资产才可能 | 云端依赖才可能 |
| 默认可见性 | 不涉及库上传 | 请求 metadata 决定 | 请求 metadata / 依赖决定 |

---

## 8. 后端改进建议（本次不改代码）

### P0：先核对“私有”来源

- 检查实际部署的 `/api/library/v1/uploads` 请求/响应与数据库行；
- 在 UI/日志中区分：模型库 `visibility`、`published`、`moderation_status`、装备 `show_private_equipment`；
- 避免把“私有模型默认不展示”误报成“提交后模型库私有”。

### P1：Figura/装饰的可观察资产投影

- 为 `figura_compatibility` 增加与 YSM 对等的状态事件；
- 为装饰实例增加模型资产投影/授权查询，而不是只保存 instance state；
- 允许本地资源显式上传并转为 CLOUD，再纳入装备分发。

### P2：状态事件补齐

- `event_visible` 不应一刀切拒绝 `OutfitStateChanged`；
- 对 Figura/装饰定义独立事件类型，事件携带 `asset_id`/`revision`/`content_sha256`/`schema_sha256`；
- 发送边界继续做可见性/ACL 复查。

### P3：测试

- 双客户端 + 实际 omnm-cloud：
  - YSM：同模型、不同 contentHash、无模型；
  - Figura：本地模型、公开云端模型、私有模型、无权限；
  - 装饰：本地依赖、云端依赖、私有展示；
  - 上传：metadata PUBLIC/PRIVATE、审核中、封禁、发布完成。

---

## 9. 证据索引

- `omnm-cloud/src/api/library/uploads.rs`
- `omnm-cloud/src/service/library/uploads.rs`
- `omnm-cloud/src/service/library/outfit_uploads.rs`
- `omnm-cloud/src/service/library/distribution.rs`
- `omnm-cloud/src/api/library/outfit_state.rs`
- `omnm-cloud/src/storage/library.sql`
- `omnm-cloud/src/storage/library_equipment.rs`
- `omnm-cloud/src/storage/mod.rs`
- `omnm-cloud/src/api/figura/profile.rs`
- `omnm-cloud/src/api/figura/websocket/handler.rs`
- `omnm-cloud/src/service/ysm/*`
- `omnm-cloud/protocol/omnimorph/cloud/v1/cloud.proto`

---

## 10. 验证状态

- 本文分析实际部署目标 omnm-cloud；修复计划 Phase 0～4 授权自动实施/核对完成。真实人工验收仍未完成，按用户要求注明MANUAL/PENDING并跳过，无剩余自动实施阶段。
- Phase 4：默认开启的OUTFIT_ASSET_DISTRIBUTION回滚开关已经隔离子进程红绿验证；关闭后观察者装饰投影、旧下载URL及实例状态拒绝，owner来源/状态保留且重新开启可恢复，新装饰绑定/上传和本地依赖转换受开关控制。最终后端197 passed / 1 ignored（实际Java/装饰夹具启用），客户端门禁和Fabric build通过。blob/缓存/限额与原独立事件复用，未做计划外优化。详细记录见修复计划第10节与phase4日志。
- Phase 3：本地依赖已支持明确确认后私有准备、原生/目录/套装单事务发布与CLOUD引用转换；原本地来源及绑定保留，失败无半发布目录。实际Java→Rust完整数据链路与事务故障注入通过，后端196 passed / 1 ignored；客户端上传/私有准备/Apply及Fabric build通过。实际联调发现并修复 Rust 可省略空 editable_bones 与 Java 严格JSON回执比较不兼容。GUI确认/取消和真实双客户端仍MANUAL/PENDING并跳过。
- Phase 2 实际阻断点：Rust Figura 描述符没有 YSM schema_sha256 是合法行为，Java OutfitObserverSnapshot 却要求所有格式该字段均为64位哈希，导致整个观察者配装被 INVALID_OUTFIT_OBSERVER_SNAPSHOT 拒绝。已红绿验证并仅对 Figura 接受空 schema；YSM 仍严格校验。实际 Java→Rust 基础与装饰资产读取通过，后端194 passed / 1 ignored（启用Java与装饰夹具）；客户端构建/回归通过。既有独立JSON资源/状态事件链路已具备资产分发，旧协议的 OutfitStateChanged 过滤不能等同于扩展通道不分发。
- Phase 1 核对修正：既有 primary 支持 Figura，figura_compatibility 已参与 resource_access 过滤，AvatarChanged 不依赖 YSM 状态；不能把仅针对 YSM Body 的类型检查误认为 Figura 资产通知被拒绝。实际 HTTP/WebSocket 回归覆盖公开 Figura 通知与下载；193 passed / 1 ignored。新增本地缺失来源 NOT_DISTRIBUTABLE 与客户端提示/状态读取。现有模型库本地 ZIP 投稿已写 library_models 与 library_resource_bindings，复用而非另建入口。
- 已验证 Phase 0：上传 PUBLIC/PRIVATE 持久化状态回执、缺失/非法 visibility 的 INVALID_VISIBILITY、已封禁内容再上传后的 PRIVATE/BLOCKED 回执；客户端保留服务端状态并显示。后端全套 191 passed / 1 ignored，客户端上传回归与 Fabric build PASS。日志见 `build/reports/multiplayer-sync/phase0-*.log`。
- 没有在真实生产服务上确认实际请求/响应和观察者分发结果；相关结论需联调核对。
- 真实双客户端、生产后端、审核/发布流程仍为 `MANUAL/PENDING`。
