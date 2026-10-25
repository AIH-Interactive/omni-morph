# omnm-cloud 多人同步与默认可见性分析

- 分析日期：2026-10-25
- 分析对象：`D:\UserData\Desktop\Project\Figura\omnm-cloud`
- 性质：后端静态代码分析；未改代码，未执行真实双客户端/生产环境验证
- 重点：为什么 YSM 可以多人同步，而 Figura 模型和装饰不能；以及“提交形象后默认私有”的后端原因

---

## 1. 结论

1. **omnm-cloud 对 YSM 有专用的模型/状态分发链路**：
   - 通过 `x-omnimorph-models: ysm` 识别 YSM 客户端；
   - 装备投影区分 `primary`（YSM）和 `figura_compatibility`（Figura）；
   - 存储 `YsmSnapshot`/`YsmDelta`/`YsmEvent`，并在订阅连接上按模型身份、哈希、可见性转发；
   - YSM 模型本身仍以资产（`AvatarDescriptor`/blob）形式存在，状态包只带模型内索引。

2. **Figura/装饰没有等价的“可观察模型 + 状态事件”链路**：
   - `event_visible` 对 `OutfitStateChanged` 直接返回 `false`，装饰状态变化不会通过订阅流广播；
   - 非 YSM 的 `primary`（`model_type` 不是 2/3）在 `event_visible` 中被拒绝；
   - `OutfitInstanceState` 只有按实例读取/写入的 HTTP 端点，没有对应的模型资产投影；
   - 本地 `LOCAL` 资源没有进入 `library_models`/`asset_revisions`，观察者拿不到文件；
   - 套装发布服务只接受 CLOUD 依赖，本地模型 + 本地装饰无法形成可分发套装。

3. **“默认私有”不是 omnm-cloud 实时库上传主路径的唯一默认行为**：
   - 实时库 `library_models.visibility` 没有数据库默认值，值来自上传 metadata；
   - 旧 `spm-cloud`/Cloudflare 资产接口明确回退到 `PRIVATE`；
   - 因此实际看到 PRIVATE，必须结合部署的是哪套接口、`published`/`moderation_status` 以及上传请求的 metadata 判断。

---

## 2. 后端拓扑

omnm-cloud 同时存在两套相关接口：

| 接口族 | 作用 | 关键点 |
| --- | --- | --- |
| `/api/library/v1/*` | 实时模型库、上传、装备、装饰、实例状态、观察者 | 当前主链路；JSON，按资产/策略/可见性分发 |
| `/api/figura/*` | Figura 兼容协议、WebSocket 订阅、YSM 状态转发 | YSM 事件/快照的主要通道 |
| `spm-cloud`（Rust/Cloudflare） | 旧/兼容资产接口 | 默认可见性与实时库不一致 |

关键源码：

- `src/api/library/mod.rs`、`uploads.rs`、`outfits.rs`、`outfit_state.rs`
- `src/service/library/uploads.rs`、`outfit_uploads.rs`、`distribution.rs`
- `src/storage/library.sql`、`library.rs`、`library_equipment.rs`
- `src/api/figura/websocket/handler.rs`、`profile.rs`
- `src/service/ysm/*`、`src/storage/mod.rs`（`ysm_snapshot`/`publish_*_ysm`）
- `protocol/omnimorph/cloud/v1/cloud.proto`
- `spm-cloud/cloudflare/src/index.ts`、`migrations/0004_asset_visibility.sql`
- `spm-cloud/src/store.rs`、`models.rs`

---

## 3. YSM 为什么可以同步

### 3.1 客户端能力识别

`src/api/figura/websocket/handler.rs` 从请求头读取：

```text
x-omnimorph-models: ysm
```

只有声明该能力的连接，才会接收 YSM 快照/增量/事件。

`profile.rs` 同样按该头区分：

- YSM 客户端取 `equipped.primary`；
- 普通 Figura 客户端取 `equipped.figura_compatibility`。

### 3.2 后端确实持久化并转发 YSM 状态

- `storage::publish_legacy_ysm` / `YsmSync` 会把 YSM 帧解码后写入 `manifest.ysm`；
- `storage::ysm_snapshot(owner, now)` 生成 `YsmSnapshot`；
- `YsmSnapshot` 包含 `YsmIdentity` + `YsmState`（贴图索引、控件、动作、控制器、播放状态）；
- `YsmDelta`/`YsmEvent` 在订阅连接上转发；
- `protocol/.../cloud.proto` 的 `YsmState` 只有索引和值，没有模型字节。

### 3.3 可见性校验

`src/service/library/distribution.rs` 的 `event_visible` 对 YSM 事件要求：

- 观察者能看到 owner 的 **primary equipped model**；
- `avatar.model_type` 必须是 `2`（YSM_PLAIN）或 `3`（YSM_ENCRYPTED）；
- `YsmIdentity` 的 owner/revision/content hash/schema hash 必须与当前装备一致；
- 与当前快照的 publisher/generation/sequence 匹配。

也就是说，omnm-cloud 对 YSM 是**按装备模型 + 身份哈希 + 状态版本**做分发的。

### 3.4 模型本体从哪来

`AvatarDescriptor` 携带：

```text
asset_id, revision, model_type, distribution_format,
raw_sha256, raw_length, content_sha256, schema_sha256, legacy_figura_hash
```

模型本体是资产 blob；`library_project_equipped` 会对 `primary` 和 `figura_compatibility` 做可见性/ACL 过滤。YSM 订阅能工作的前提是观察者已能取得同一 `content_sha256`/`schema_sha256` 的模型资产。

**结论**：YSM 的同步 = 后端资产分发 + 后端 YSM 状态快照/增量；不是单独把模型塞进状态包。

---

## 4. Figura 模型为什么不能同样同步

### 4.1 装备投影把 Figura 放到兼容槽

`profile.rs` / `EquippedState`：

- `primary`：YSM 主模型；
- `figura_compatibility`：Figura 兼容模型。

YSM 状态事件只针对 `primary`，并且 `event_visible` 明确拒绝 `model_type` 不是 2/3 的情况。Figura `model_type = 1` 即使装备在 `figura_compatibility`，也不会进入 YSM 状态转发。

### 4.2 没有 Figura 状态事件

`event_visible` 中：

```rust
CloudEvent::OutfitStateChanged { .. } => return Ok(false),
```

装饰/配装实例的状态变化不会进入订阅流。`outfit_state.rs` 只有：

```text
GET  /api/library/v1/outfits/{owner}/{instance}/state
PUT  /api/library/v1/outfits/{instance}/state
```

这是按实例的 HTTP 状态端点，不是“观察者订阅模型状态”的通道；也没有把模型资产投影和状态一起下发。

### 4.3 本地 Figura 资源没有后端资产

`library_models`/`asset_revisions` 只登记上传的云端资产：

- 本地 `omnimorph/avatars/...` 文件没有对应 `asset_id`；
- 本地 `OutfitConfig.ResourceRef` 的 `LOCAL` 引用在服务端没有可解析的 blob；
- 观察者通过 `library_project_equipped` 只能拿到已上传、可见、已授权的资产。

因此本地 Figura 模型没有可同步的模型资产，状态通道也不覆盖它。

### 4.4 公开云端 Figura 仍然受“装备+可见性”约束

即使 Figura 模型已上传为 PUBLIC：

- 必须处于 `equipped`/`figura_compatibility` 投影；
- 观察者必须通过 `resource_access`；
- 还需要客户端按 `AvatarDescriptor` 下载/缓存模型。

当前没有“本地 Figura 模型装备后自动上传并推送”的通用流程，所以用户会看到 Figura 不同步。

---

## 5. 装饰为什么不能同步

### 5.1 装饰只存在于实例状态

装饰通过 `OutfitConfig.Instance` 存在：

- `instance_id`
- `resource`（LOCAL/CLOUD 引用）
- `anchor`/`transform`
- `controls`/`lua_state`

`outfit_state.rs` 的 `InstanceState` 只保存/读取这些状态；装饰的：

- `cosmetic.json`
- 模型几何
- 纹理
- `script.lua`

都不在状态端点里。

### 5.2 本地依赖被发布层拒绝

`src/service/library/outfit_uploads.rs` 的发布输入要求依赖是 CLOUD 资源；本地资源不能作为可分发套装依赖。客户端对应的 `OutfitUploadDraft` 也会抛：

```text
OUTFIT_DEPENDENCY_NOT_DISTRIBUTABLE
```

因此“本地基础 + 本地装饰”的套装无法成为可被观察者下载的云端套装。

### 5.3 结果

- 观察者最多拿到实例状态（而且当前 `OutfitStateChanged` 不广播）；
- 没有装饰资产，状态无法渲染；
- 本地装饰没有资产 ID，后端无法按 `library_project_equipped` 分发。

---

## 6. “默认私有”的后端原因

### 6.1 实时库上传主路径

`src/api/library/uploads.rs`：

- 上传请求的 `Metadata.visibility` 是必需字段（`service/library.rs` 的 `Metadata` 没有 serde 默认值）；
- `service/library/uploads.rs` 把 `input.metadata.visibility` 写入 `library_models.visibility`；
- `library_models` 的建表 SQL 没有默认值，只有 `CHECK(visibility IN ('PUBLIC','PRIVATE'))`；
- 新上传还有独立的 `published = 0` 和 `moderation_status`，提交后才发布。

因此**实时库主路径不会擅自把 PUBLIC 改成 PRIVATE**；如果客户端发送 PUBLIC，写入应为 PUBLIC。

### 6.2 旧/兼容资产接口默认 PRIVATE

`spm-cloud` 存在相反的默认：

- `spm-cloud/cloudflare/migrations/0004_asset_visibility.sql`：
  ```sql
  ALTER TABLE assets ADD COLUMN visibility TEXT NOT NULL DEFAULT 'PRIVATE';
  ```
- `spm-cloud/cloudflare/src/index.ts`：
  ```text
  requestedVisibility = x-asset-visibility
  visibility = requestedVisibility || current?.visibility || "PRIVATE"
  ```
- `spm-cloud/src/store.rs` 的新资产插入回退为 `PUBLIC`；
- `spm-cloud/src/models.rs` 的反序列化默认 `PRIVATE`。

如果生产环境仍走旧接口，或者上传没有携带 `x-asset-visibility`，就会得到 PRIVATE。

### 6.3 审核/屏蔽也会强制 PRIVATE

`library_models` 有约束：

```sql
CHECK(moderation_status != 'BLOCKED' OR visibility = 'PRIVATE')
```

`src/storage/library.rs`/`library_wardrobe.rs` 在屏蔽时也会：

```sql
UPDATE library_models SET visibility = 'PRIVATE', ...
```

因此“提交后私有”也可能来自**审核/屏蔽/发布状态**，而不只是上传默认值。

### 6.4 结论与核对方法

在 omnm-cloud 实时库路径上，默认可见性由**上传 metadata** 决定；后端没有把新上传默认改成 PRIVATE 的代码。要定位实际原因，需要核对：

1. 生产是否使用 `/api/library/v1/uploads`（实时库）还是 `spm-cloud` 资产接口；
2. 上传请求 metadata 的 `visibility` 实际值；
3. 返回模型行的 `visibility`、`published`、`moderation_status`；
4. 是否命中屏蔽/审核流程。

---

## 7. 关键差异总表

| 维度 | YSM | Figura | 装饰 |
| --- | --- | --- | --- |
| 后端模型资产投影 | `primary` | `figura_compatibility` | 无独立模型投影 |
| 状态事件 | `YsmSnapshot/Delta/Event` | `OutfitStateChanged` 不广播 | `OutfitStateChanged` 不广播 |
| `event_visible` 条件 | `model_type 2/3` + 哈希匹配 | `model_type 1` 被拒绝 | 直接 false |
| 本地资源 | 需要模型资产 | 本地文件无资产 ID | 本地依赖不可发布 |
| 观察者获取 | 资产 + 状态 | 云端资产才可能 | 云端依赖才可能 |
| 默认可见性 | 不涉及库上传 | metadata 决定 | metadata/依赖决定 |

---

## 8. 后端改进建议（本次不改代码）

### P0：明确部署与可见性语义

- 确认生产环境使用 `/api/library/v1` 还是 `spm-cloud`；
- 统一两套后端的 visibility 默认值，禁止“同一资产在旧接口默认 PRIVATE、新接口默认 PUBLIC”；
- 上传响应返回并持久化明确原因（visibility/published/moderation）。

### P1：为 Figura/装饰补齐分发投影

- 在 `EquippedState` 之外增加可观察的装饰资产投影；
- 允许本地模型/装饰显式上传后转为 `CLOUD` 资产，再进入装备分发；
- 或提供独立的“装饰资源资产”表与观察者授权查询，而不是只依赖 instance state。

### P2：状态事件

- 为 `OutfitStateChanged` 增加受可见性/授权约束的订阅事件；
- 事件中携带 model/asset identity、revision、content/schema hash，避免观察者拿到无法对应资源的状态；
- 对非 YSM 的 Figura 模型单独定义状态通道，而不是复用 YSM 的 `primary` 条件。

### P3：测试

- 双客户端 + 生产同版本后端：
  - YSM：同模型/不同 contentHash/无模型；
  - Figura：本地模型、公开云端模型、私有模型、无权限模型；
  - 装饰：本地依赖、云端依赖、私有展示开关；
  - 上传：metadata PUBLIC/PRIVATE、审核中、屏蔽、发布完成。

---

## 9. 证据索引

- `omnm-cloud/src/api/library/mod.rs`
- `omnm-cloud/src/api/library/uploads.rs`
- `omnm-cloud/src/api/library/outfit_state.rs`
- `omnm-cloud/src/service/library/uploads.rs`
- `omnm-cloud/src/service/library/outfit_uploads.rs`
- `omnm-cloud/src/service/library/distribution.rs`
- `omnm-cloud/src/storage/library.sql`
- `omnm-cloud/src/storage/library.rs`
- `omnm-cloud/src/storage/library_equipment.rs`
- `omnm-cloud/src/storage/mod.rs`（`publish_legacy_ysm`、`ysm_snapshot`）
- `omnm-cloud/src/api/figura/websocket/handler.rs`
- `omnm-cloud/src/api/figura/profile.rs`
- `omnm-cloud/src/service/ysm/*`
- `omnm-cloud/protocol/omnimorph/cloud/v1/cloud.proto`
- `omnm-cloud/spm-cloud/cloudflare/src/index.ts`
- `omnm-cloud/spm-cloud/cloudflare/migrations/0004_asset_visibility.sql`
- `omnm-cloud/spm-cloud/src/store.rs`
- `omnm-cloud/spm-cloud/src/models.rs`

---

## 10. 验证状态

- 本文是后端静态分析，没有改代码。
- 没有在真实生产服务上确认实际部署路径、上传响应和观察者分发结果；相应结论标记为需要联调核对。
- 真实双客户端、生产后端、审核/发布流程仍为 `MANUAL/PENDING`。
