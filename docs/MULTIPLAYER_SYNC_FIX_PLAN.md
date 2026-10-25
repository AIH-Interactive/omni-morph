# omnm-cloud 多人同步修复计划

- 日期：2026-10-25
- 实际部署目标：`omnm-cloud`（`spm-cloud` 仅参考，不在本计划范围）
- 前提文档：[MULTIPLAYER_SYNC_ANALYSIS.md](MULTIPLAYER_SYNC_ANALYSIS.md)
- 本计划只定义修复顺序、接口/数据变更、验收与回滚，不改代码

---

## 1. 目标

让以下三类内容在多人环境中具备明确的可见性规则与可观察性：

1. **公开云端 Figura 模型**：观察者能拿到模型资产并渲染；
2. **公开云端装饰**：观察者能拿到装饰资产，而不只是实例状态；
3. **本地模型/装饰**：不能静默失败；要么明确“仅本人可用”，要么通过显式上传转换为云端资产后可分享。

同时保留：

- YSM 现有的主模型快照/增量/事件链路；
- 私有模型的 ACL、审核、封禁与“默认不向其他玩家展示”；
- 不把模型/纹理/脚本塞进 YSM 的 480 字节状态分片。

---

## 2. 非目标

- 不恢复 `spm-cloud` 旧资产接口作为生产路径；
- 不把本地文件直接暴露给任意观察者；
- 不改变 YSM 状态协议的既有语义；
- 不在本阶段实现完整编辑器 UI 重做，只做必要的可见性/分享入口。

---

## 3. 现状到目标的差距

| 差距 | 现状 | 修复方向 |
| --- | --- | --- |
| Figura 没有状态/资产投影 | `primary` 仅 YSM；`figura_compatibility` 不在 YSM 事件过滤内 | 为 Figura 资产建立可观察投影；不要求复用 YSM 状态 |
| 装饰只有实例状态 | `outfit_state` 只有 controls/lua/anchor | 为装饰资源建立资产绑定与下载 |
| 本地资源不可分发 | `outfit_local_sources` 仅 owner 可读；本地引用无 `asset_id` | 显式上传/转换，绑定到 `library_resource_bindings` |
| 本地依赖套装不可发布 | 发布服务要求 CLOUD 依赖 | 允许“先上传资源，再发布套装”的事务 |
| 私有语义混淆 | 模型库 visibility、published/moderation、装备展示默认三者独立 | 分开展示，禁止静默私有 |

---

## 4. 总体设计

### 4.1 统一资源投影

让装备投影同时包含“模型资产”和“装饰资产”：

```text
EquippedState
  primary              # 当前主模型（YSM 优先）
  figura_compatibility # Figura 兼容模型
  accessories[]        # 新增：公开/可观察的装饰实例资产
```

每个条目至少包含：

```text
asset_id
revision
model_type / resource_kind
content_sha256 / schema_sha256
raw_length
visibility / requires_authorization
```

原则：

- **状态与资产分离**：`YsmSnapshot`/`OutfitState` 继续传状态；资产通过现有 blob/download 链路；
- **可见性统一**：所有资产都走 `resource_access`/ACL，不在事件层自造例外；
- **本地资源显式化**：本地文件只有经过“上传/分享”才产生 asset，不自动暴露。

### 4.2 新增/扩展的数据

建议 additive migration，不破坏旧行：

1. `outfit_instance_assets`（或复用 `library_resource_bindings` + 扩展列）：
   ```text
   owner_uuid, instance_id, resource_kind, asset_id, revision,
   content_sha256, schema_sha256, visibility, created_at
   ```
2. `outfit_local_resources` 增加可选 `asset_id`/`revision`：
   - 未分享：保持 owner-only；
   - 已分享：指向 `library_models` / `asset_revisions`，允许按 ACL 读取。
3. `library_resource_kinds` 已存在 `ACCESSORY`，确认装饰上传复用它，不新增重复类型。

### 4.3 可见性语义

| 概念 | 含义 | 默认 |
| --- | --- | --- |
| `library_models.visibility` | 模型库条目是否公开 | 由上传 metadata 显式决定；不接受缺失 |
| `published` | 上传是否完成 | 新行 0，commit 后 1 |
| `moderation_status` | 审核/封禁 | `ACTIVE`；封禁强制 PRIVATE |
| `show_private_equipment` | 私有模型是否向观察者展示 | 0（默认不展示） |
| `show_equipment` | 公开模型是否向观察者展示 | 1（公开默认展示） |

**不在服务端把缺少 visibility 的请求静默当作 PRIVATE**；缺少字段直接拒绝，避免“提交后莫名私有”。

---

## 5. 分阶段修复

### Phase 0：契约与可观测性（P0）

目标：先让“为什么私有/为什么看不到”可定位。

任务：

1. 上传 API：
   - `Metadata.visibility` 保持必填；
   - 响应增加 `visibility`、`published`、`moderation_status`；
   - 错误码区分 `INVALID_VISIBILITY`、`MODERATION_PRIVATE`、`NOT_DISTRIBUTABLE`。
2. 客户端/日志：
   - 上传完成后显示服务端返回值，而不是只显示本地草稿值；
   - 观察者侧记录“模型未装备/未公开/无 ACL/资产未下载”。
3. 增加诊断端点或日志字段：
   - `owner, instance, asset_id, visibility, moderation, show_private_equipment`。

验收：

- 任意一次“提交后私有”都能从响应/日志定位到具体层；
- 不需要先改分发逻辑。

### Phase 1：Figura 模型资产投影（P1）

目标：公开云端 Figura 模型可被观察者下载/渲染；本地 Figura 明确提示“仅本人”。

后端任务：

1. `src/storage/library_equipment.rs`：
   - 确保 `figura_compatibility` 与 `primary` 一样进入 `library_project_equipped` 的可见性过滤；
   - 返回 `AvatarDescriptor`（已有 `asset_id/revision/content_sha256`）。
2. `src/api/figura/websocket/handler.rs` / `profile.rs`：
   - 对 Figura 模型发送 `AvatarChanged`/资产引用；不要求 `YsmState`；
   - 仅在 `resource_access` 通过时发送。
3. `src/service/library/distribution.rs`：
   - `event_visible` 对 Figura 模型不做 `model_type != 2|3` 的一刀切拒绝；
   - 仍拒绝无装备、无 ACL、私有未展示的情况。
4. 本地 Figura：
   - 增加“上传本地模型”入口，写入 `library_models`（`model_type=FIGURA`）和 `library_resource_bindings`；
   - 未上传时观察者侧明确显示“仅本人可见”。

验收：

- 双客户端：公开 Figura 模型可见；私有/无 ACL 不可见；本地未上传时观察者收到明确“不可分发”状态，而不是空白。

### Phase 2：装饰资源绑定（P1）

目标：装饰不再只有状态，观察者能取到资源。

后端任务：

1. 上传/绑定：
   - 装饰资源通过 `library_models.resource_kind=ACCESSORY` 上传；
   - `library_resource_bindings` 记录 `owner, instance_id, asset_id, revision`；
   - `outfit_local_resources` 未分享时保持 owner-only；分享后写 `asset_id/revision`。
2. 实例状态：
   - `outfit_state.rs::read` 返回实例引用的 asset descriptor（或独立的 `accessory_asset` 字段）；
   - `write` 只允许合法绑定，不接受客户端任意 asset_id。
3. 观察者分发：
   - 新增 `GET /api/library/v1/outfits/{owner}/{instance}/asset`（或复用 transfer）按 `resource_access` 返回装饰 blob；
   - `outfit_local_sources` 的 owner-only 检查改为“owner 或可观察的公开/ACL 观察者”，并保留私有/封禁拒绝。
4. 事件：
   - `event_visible` 允许 `OutfitStateChanged`/`OutfitAssetChanged` 在资源可见时通过；
   - 事件携带 `asset_id/revision`，观察者据此下载，不把模型字节塞进状态包。

验收：

- 公开装饰：观察者拿到状态 + 资源并渲染；
- 私有装饰：默认不可见；`show_private_equipment` 开启后才可见；
- 无资源绑定的旧实例：保持旧行为并显示 `NOT_DISTRIBUTABLE`。

### Phase 3：本地依赖套装发布（P2）

目标：本地基础 + 本地装饰可以显式转为可分享套装。

后端任务：

1. `src/service/library/outfit_uploads.rs`：
   - 发布前置条件从“依赖必须 CLOUD”改为“依赖必须已绑定 asset 且可见”；
   - 若依赖仍是本地，返回 `OUTFIT_DEPENDENCY_NOT_DISTRIBUTABLE` 并提示先上传。
2. 事务：
   - 先上传/绑定依赖 → 再发布套装；
   - 失败回滚，不留下半发布资产。
3. 客户端：
   - 上传表单增加“本地资源将转为云端资产”的明确确认；
   - 取消时保留本地文件，不删除源。

验收：

- 本地套装可显式上传后分享；
- 取消/失败不会修改本地源；
- 私有/封禁资源仍不可被观察者获取。

### Phase 4：一致性与测试（P2）

1. 可见性一致性：
   - 所有下载、事件、装备投影共用 `resource_access`；
   - 禁止事件层“先发后查”。
2. 观察者边界：
   - 断线/换装/撤销授权/屏蔽后立即停止分发；
   - 旧观察者不能继续读取缓存外的新内容。
3. 性能与限额：
   - 资产 blob 复用、缓存与清理；
   - 事件大小/频率上限不因装饰资源扩大。
4. 回滚：
   - 新表/列 additive；
   - 功能开关 `OUTFIT_ASSET_DISTRIBUTION`；
   - 关闭开关恢复“仅状态/仅 owner 本地资源”的旧行为。

---

## 6. 文件级任务清单

后端：

- `src/storage/library.sql`：新增 `outfit_instance_assets` 或扩展 `library_resource_bindings`；
- `src/storage/library_equipment.rs`：`figura_compatibility` + accessory 投影；
- `src/storage/outfit_resources.rs` / `outfit_local_resources`：asset 绑定、owner/observer 读取；
- `src/api/library/outfits.rs`、`outfit_state.rs`、`outfit_local_sources.rs`：状态与资产端点；
- `src/service/library/outfit_uploads.rs`：本地依赖上传/发布；
- `src/service/library/distribution.rs`：`event_visible`、`can_observe_model`；
- `src/api/figura/websocket/handler.rs`、`profile.rs`：Figura 资产引用/事件；
- `src/service/library/uploads.rs`：visibility 响应/诊断；
- 测试：`src/api/library/tests/*`、`src/service/library/tests/*`。

客户端（只做必要对接，非本计划重点）：

- `OutfitInstanceSync`：读取/下载 accessory asset；
- `OutfitUploadDraft`：本地依赖显式上传；
- `LibraryUploadDraft`/上传界面：显示服务端 visibility/published/moderation，不用本地草稿冒充结果。

---

## 7. 验收矩阵

| 场景 | 期望 |
| --- | --- |
| YSM 公开模型 + 同 contentHash | 状态与模型正常，现网行为不回归 |
| YSM 不同 contentHash | 拒绝/提示，不崩溃 |
| Figura 公开云端模型 | 观察者可下载并渲染 |
| Figura 私有/无 ACL | 观察者不可见 |
| 本地 Figura 未上传 | 观察者明确提示“仅本人/不可分发” |
| 装饰公开 + 有 asset | 状态 + 资源可见 |
| 装饰私有、`show_private_equipment=0` | 默认不可见 |
| 装饰私有、`show_private_equipment=1` | 仅授权观察者可见 |
| 本地依赖套装发布 | 先上传依赖，成功后可分享；失败不修改本地 |
| 封禁/撤销/换装 | 观察者立即停止获取新内容 |
| 上传 visibility 缺失 | 400，不静默 PRIVATE |

---

## 8. 风险与控制

1. **资源泄露**：所有新端点必须复用 `resource_access`/ACL，不得只凭 asset_id；
2. **本地文件暴露**：本地源仍走 owner-only，只有显式上传后才产生可分发资产；
3. **旧客户端**：新增字段保持可选；旧客户端忽略 accessory/Figura 资产，退回现有行为；
4. **存储增长**：装饰资产去重（content hash）、配额、清理策略与模型资产一致；
5. **事件风暴**：装饰状态事件按实例/版本节流，禁止每帧广播；
6. **审核**：公开装饰同样进入现有 moderation/blocked hash 流程；
7. **回滚**：功能开关关闭后，不删除已上传资产，仅停止分发/新绑定。

---

## 9. 交付顺序与提交策略

1. Phase 0：可观测性 + 契约测试 → 独立提交；
2. Phase 1：Figura 投影 → 独立提交；
3. Phase 2：装饰资产 + 事件 → 独立提交；
4. Phase 3：本地依赖发布 → 独立提交；
5. Phase 4：双客户端/生产回归 + 文档。

每个阶段：

- 先补测试再改行为；
- 提交前跑后端单元/集成测试；
- 阶段完成即更新 `MULTIPLAYER_SYNC_ANALYSIS.md` 的“已验证/待验证”状态；
- 真实双客户端验证未完成前，不把阶段标记为完成，只标记 `MANUAL/PENDING`。

---

## 10. 当前状态

- Phase 0 自动实施完成：上传 reserve/commit 返回持久化 visibility/published/moderation_status；缺失/非法 visibility 返回 400 INVALID_VISIBILITY；distribution_code 区分 MODERATION_PRIVATE/NOT_DISTRIBUTABLE；客户端显示服务端结果，新增观察者/资源投影诊断日志。
- Phase 0 自动验证：后端 `cargo test -- --test-threads=4`，191 passed / 0 failed / 1 ignored；客户端 `:common:libraryUploadSmokeTest :fabric:build` PASS（42 秒）。日志在 `build/reports/multiplayer-sync/phase0-backend.log`、`phase0-client.log`。
- Phase 0 真实 GUI、生产上传/审核与双客户端验证：`MANUAL/PENDING`，按用户要求注明并跳过，不阻塞后续阶段；不得视为通过。
- Phase 1～4 尚未实施；不重复实施既有 S1～S6，不进行无关修复、重构、优化或版本调整。
- 生产环境实际部署为 `omnm-cloud`；`spm-cloud` 不在目标内。
- **公开上传默认可见性** 仍保留现有客户端显式选择，不在修复中自行改变默认值；审核封禁仍可强制私有，响应明确报告实际状态。
