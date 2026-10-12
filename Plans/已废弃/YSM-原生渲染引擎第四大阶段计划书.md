# YSM 原生渲染引擎第四大阶段计划书：加密模型与公模库支持

> 前置条件：第一大阶段“单人未加密 YSM 完整可用”、第二大阶段“Figura 后端同步可用”、第三大阶段“Native / SIMD / GPU 加速可用”均已完成。  
> 范围：在现有 Figura YSM 引擎、`sculptor` 后端、Sparkle-Morpher、YSMParser 分析基础上，支持加密 `.ysm` 模型，并新增同时服务 YSM 与 Figura 的通用公模库。  
> 核心目标：玩家可以通过 Figura 客户端直接上传、浏览、预览、下载、启用公模；公模库支持非加密 YSM、加密 YSM、普通 Figura Avatar；管理员可以在 Figura 可视化界面中完成删除、改名、授权等管理操作，且需要额外管理员密码。

## 1. 阶段定位

第四大阶段不是单纯“能打开加密 `.ysm` 文件”，而是把 YSM 加密包解析、公模资产托管、客户端公模浏览、衣柜同步、后端审核管理合并为一个完整分发闭环。

完成后应具备：

- Figura YSM 引擎可以识别并加载加密 `.ysm` 模型。
- 加密 YSM 的解密语义以 YSMParser 为准；Java 解密实现必须等价于 YSMParser 解密，Sparkle-Morpher 的 `YsmCrypt` 只作为加密写出和新版本 crypto 细节参考，最终落地到 Figura 的 `YsmAvatarLoader` / `YsmModelRuntime` 生命周期。
- 公模库作为 `sculptor` 后端的独立 API 模块存在，不复用个人 Avatar 上传端点。
- 玩家可从 Figura 界面上传公模、浏览公模、下载公模、预览模型、启用模型。
- 启用公模时，客户端把公模同步到玩家衣柜，再按第二大阶段已有 Avatar 上传/装备机制同步给其他玩家看。
- 公模库本身只负责公开模型的上传、下载、元数据、权限、缓存和管理，不负责替代玩家当前装备 Avatar。
- 管理员可由后端配置指定，并且在公模管理界面操作时必须额外输入管理员密码。

## 2. 非目标与边界

本阶段不做：

- 不把公模库变成 Minecraft 游戏服务器 Mod 模型仓库。
- 不使用 Sparkle-Morpher 的 Minecraft custom payload 分片上传作为主通道。
- 不让 `sculptor` 执行 Molang、Lua、animation controller 或模型渲染逻辑。
- 不绕过 Figura 现有 token、权限、限流、Avatar hash 缓存和 WebSocket Event 机制。
- 不在公模库中存储玩家私有衣柜状态、键位、私有控件值或运行时同步变量。
- 不默认公开玩家当前衣柜中的私有模型；上传公模必须是用户显式操作。
- 不在客户端界面中暴露加密模型密钥、解密中间文件或原始备份路径。

本阶段必须做：

- 公模库资源走 HTTP 上传/下载。
- 公模库 API 必须独立于现有 `/api/avatar`、`/api/equip`。
- 后端必须持久保存公模文件和元数据。
- 支持公模类型：
  - `YSM_UNENCRYPTED`
  - `YSM_ENCRYPTED`
  - `FIGURA_AVATAR`
- 支持模型预览所需的元数据、缩略图或可下载预览资源。
- 管理员管理必须在 Figura 可视化界面中完成。

## 3. 当前基线

### 3.1 Figura 客户端基线

当前客户端后端访问集中在：

- `common/src/main/java/org/figuramc/figura/backend2/HttpAPI.java`
- `common/src/main/java/org/figuramc/figura/backend2/NetworkStuff.java`
- `common/src/main/java/org/figuramc/figura/gui/screens/WardrobeScreen.java`
- `common/src/main/java/org/figuramc/figura/avatar/AvatarManager.java`

当前衣柜行为：

- `WardrobeScreen` 左侧显示本地 Avatar 列表。
- 中间使用 `EntityPreview` 预览当前玩家模型。
- 上传按钮调用 `NetworkStuff.uploadAvatar(avatar)`。
- reload / delete / equip 仍走 Figura 现有后端。

第四大阶段应在衣柜旁边增加“公模库”界面入口，而不是把公模条目混入本地 Avatar 列表。

### 3.2 YSM runtime 基线

当前 YSM 加载核心在：

- `YsmAvatarLoader`
- `YsmManifestReader`
- `YsmResourceIndex`
- `YsmModelRuntime.fromNbt(...)`
- `YsmAnimationParser`
- `YsmAnimationControllerParser`
- `YsmMolangFunctionRuntime`

当前 `YsmModelRuntime.fromNbt(...)` 已能从 NBT 读取：

- main model
- arm model
- textures / texture entries
- animations
- animation controllers
- functions
- action schemas
- controls
- sub entities

第四大阶段的重点是把“加密 `.ysm` 输入”转换成这套已有 NBT/runtime 结构，而不是为加密模型另建一套渲染管线。

### 3.3 sculptor 后端基线

当前 `sculptor` 负责：

- token 鉴权。
- 用户资料与 Avatar 文件存储。
- HTTP Avatar 上传/下载。
- WebSocket 订阅与 Event 广播。

第四大阶段需要新增公模库模块，但继续复用：

- token 鉴权提取器。
- 限流与大小限制体系。
- 文件 hash / metadata 思路。
- HTTP API 架构。

公模库列表必须支持实时刷新：

- 列表、详情、下载仍以 HTTP 为主。
- 后端公模 metadata 发生变化时，通过 WebSocket 向在线客户端广播 `PublicModelEvent`。
- 客户端公模库界面收到事件后，必须让当前列表实时更新。
- 下载行为默认不广播；下载次数如果作为 UI 字段展示，可以由后端按低频聚合事件刷新。

## 4. Sparkle-Morpher 与 YSMParser 参考边界

### 4.1 加密解密参考

Sparkle-Morpher 中 `YsmCrypt` 可参考的能力：

- 加密 `.ysm` 文件识别。
- `decryptYsmFile(byte[])`：
  - 查找 header terminator。
  - 校验文件尾部 hash。
  - 读取 crypto version。
  - XChaCha20 派生解密。
  - MT19937 xor。
  - 跳过随机 padding。
  - ZSTD 解压得到明文包数据。
- `encryptYsmFile(byte[])`：
  - 压缩明文。
  - 随机 padding。
  - XChaCha20 加密。
  - 写入 key/iv/hash。
- server cache / client cache 转码思路：
  - 后端存储和客户端缓存可以使用不同密钥壳。
  - 缓存 hash 与客户端身份绑定，避免明文长期裸露。

YSMParser 可参考的能力：

- 按版本创建 parser。
- 从内存或路径解析 `.ysm`。
- 输出解密后的数据。
- 保存到目录用于调试。

落地原则：

- 客户端加载加密 `.ysm` 时，统一输出为 `YsmPackage` / `YsmResourceIndex` 可消费的资源树。
- 解密中间明文只允许停留在内存或受控临时缓存中。
- 不把 Sparkle-Morpher 的类包结构直接搬进 Figura；需要抽象为 Figura 自己的 `YsmEncryptedPackageReader`、`YsmCryptoService`、`YsmDecryptedPackage`。
- 若 native YSMParser 不可用，必须提供 Java fallback 或明确报错，不影响非加密模型。

### 4.2 模型同步参考

Sparkle-Morpher 的模型上传/同步有价值的是：

- start / chunk / finish / result 的状态机。
- 上传进度、hash 校验、失败回滚。
- 模型元数据与二进制内容分离。
- 客户端缓存命中后不重复下载。

但本阶段采用 HTTP：

- 小文件可直接 `multipart/form-data` 或 `application/octet-stream` 上传。
- 大文件采用 HTTP 分片会话，而不是 Minecraft packet。
- 下载使用 hash / ETag / Last-Modified / range request。
- 启用公模后，仍通过 Figura 衣柜上传当前 Avatar 给其他玩家看。

## 5. 加密 YSM 支持方案

### 5.1 新增核心包

建议新增：

```text
org.figuramc.figura.avatar.ysm.crypto
```

核心类：

- `YsmEncryptedPackageDetector`
  - 判断输入是否为加密 `.ysm`。
  - 识别 magic/header/crypto version。
  - 返回 `UNKNOWN`、`UNENCRYPTED`、`ENCRYPTED_LEGACY`、`ENCRYPTED_V3`、`UNSUPPORTED`。
- `YsmCryptoService`
  - 统一入口：`decrypt(byte[] data, YsmCryptoContext context)`。
  - 封装 YSMParser 全版本解密语义、与 YSMParser 等价的 Java 解密实现、一致性校验和不可用 fallback。
- `YsmCryptoContext`
  - 来源路径。
  - 是否允许写入本地缓存。
  - 是否调试导出。
  - 模型 hash。
  - 后端公模 id，可选。
- `YsmDecryptedPackage`
  - 解密后原始数据。
  - 格式版本。
  - 原始 header metadata。
  - 内容 hash。
  - 临时资源生命周期。
- `YsmEncryptedCache`
  - 本地加密缓存。
  - key: source hash + parser version + Figura version + crypto version。
  - value: 后处理后的安全缓存，不直接保存裸明文。

### 5.1.1 旧版加密模型兼容

第四大阶段必须接入旧版加密 YSM 解密。YSMParser 作为全版本解密权威实现，负责通吃其支持的所有 YSGP / crypto 历史版本；Figura 自带 Java crypto 的定义就是“YSMParser 解密的 Java 等价实现”，必须按 YSMParser 中的实现补齐全版本解密，目标是与 YSMParser 对同一输入产出一致明文。

兼容要求：

- 必须支持 YSMParser 可识别的全部 `.ysm` 加密版本。
- `YsmCryptoService` 必须同时具备两条可校验的解密执行路径：
  - Java 全版本解密路径，语义等同 YSMParser。
  - YSMParser 权威路径。
- YSMParser 路径：
  - 从文件路径创建 parser。
  - 或从内存 bytes 创建 parser。
  - 调用 `parse()`。
  - 读取 `getDecryptedData()`。
  - 必要时读取 `getYSGPVersion()` 记录版本。
- Java 解密路径：
  - 逐版本复刻 YSMParser 各版本 parser 的解密实现，不能自行发明不兼容流程。
  - 对齐旧版 YSGP v1 / v2 与新版 v3 的 header、offset、校验、解压、资源包还原逻辑。
  - 对齐 YSMParser 的边界处理和错误分类。
  - 输出明文必须与 YSMParser `getDecryptedData()` 字节级一致，或在格式允许重排时通过 resource manifest/hash 等价校验。
- 检测到旧版加密模型时，不允许直接报 `UNSUPPORTED`；必须进入 YSMParser 语义的全版本解密。
- Java 路径失败时必须自动尝试 YSMParser 权威路径，确保运行时仍可加载。
- YSMParser 路径成功而 Java 路径失败，必须记录为 Java 解密等价性缺陷。
- 只有 Java 和 YSMParser 都无法识别或解密时，才标记为 `UNSUPPORTED_ENCRYPTED_VERSION`。

建议版本结果：

```text
YsmEncryptedFormat
  UNENCRYPTED
  ENCRYPTED_YSGP_V1
  ENCRYPTED_YSGP_V2
  ENCRYPTED_YSGP_V3
  ENCRYPTED_UNKNOWN_TRY_PARSER
  UNSUPPORTED
```

验收样例：

- 新版 crypto v3 `.ysm` 能解密。
- 旧版 YSGP v1 `.ysm` 能通过 Java 与 YSMParser 两条路径解密。
- 旧版 YSGP v2 `.ysm` 能通过 Java 与 YSMParser 两条路径解密。
- 未知但 YSMParser 可识别的加密包，Java 路径必须补齐到等价；若当前构建暂未覆盖，必须自动落到 YSMParser，且记录 Java 等价性缺口。
- Java 与 YSMParser 对同一测试包的明文输出必须一致。
- YSMParser 不可用时，Java 路径仍应覆盖已验证版本，不允许只支持 v3。

### 5.1.2 Java 解密实现对齐策略

目标：Java 解密实现等价于 YSMParser 解密，做到所有 YSMParser 可解版本稳定解密，避免运行时强依赖 native parser。

实现原则：

- 逐版本翻译 YSMParser 中对应 parser 的解密流程；Java 解密 = YSMParser 解密的 Java 实现。
- 不凭 Sparkle-Morpher 单一 `YsmCrypt.decryptYsmFile` 推断所有历史版本。
- Sparkle-Morpher 的 `YsmCrypt` 只作为 crypto v3、加密写出和缓存壳参考。
- 旧版格式以 YSMParser 为准。
- 每个版本必须有 fixture。

建议新增：

```text
org.figuramc.figura.avatar.ysm.crypto.java
  JavaYsmDecryptor
  JavaYsmDecryptorV1
  JavaYsmDecryptorV2
  JavaYsmDecryptorV3
  JavaYsmFormatProbe
  JavaYsmDecryptResult
  YsmDecryptCompatibilityTest
```

一致性验证：

```text
for each encrypted_fixture:
  javaPlain = JavaYsmDecryptor.decrypt(bytes)
  parserPlain = YSMParser.decrypt(bytes)
  assertEquivalent(javaPlain, parserPlain)
  assert YsmResourceIndex can scan javaPlain
  assert YsmResourceIndex can scan parserPlain
```

`assertEquivalent` 优先级：

1. 字节级一致。
2. 若 YSMParser 输出可能格式化 JSON 或重排资源，则比较：
   - manifest 字段。
   - resource path set。
   - resource SHA-256 map。
   - main/arm model hash。
   - texture hash。
   - animation/controller/function hash。

失败处理：

- Java 解密失败但 YSMParser 成功：
  - 客户端可继续加载模型。
  - debug 标记 `JAVA_DECRYPT_EQUIVALENCE_MISMATCH`。
  - 记录版本、hash、失败阶段。
- Java 和 YSMParser 都失败：
  - 显示真正的解密失败或不支持版本。
  - 不把模型误判为损坏 zip/非 YSM。

质量门槛：

- 第四大阶段合入前，Java 解密必须通过 YSMParser fixture 对照测试。
- “100% 成功解密”的定义以 YSMParser 当前可解密样例集为准。
- 后续 YSMParser 增加新版本支持时，必须同步补 Java 等价 decryptor 和 fixture；否则该版本只能临时走 YSMParser 权威路径，不能宣称 Java 解密已完成。

### 5.2 加载流程

输入来源：

- 本地文件夹。
- 本地 zip。
- 本地 `.ysm` 加密文件。
- 公模库下载的加密 YSM。
- 公模库下载的非加密 YSM。

目标统一流程：

```text
input
  -> YsmAvatarDetector
  -> YsmEncryptedPackageDetector
  -> YsmCryptoService.decrypt 可选
  -> YsmPackage
  -> YsmResourceIndex
  -> YsmAvatarLoader NBT
  -> YsmModelRuntime
```

要求：

- 非加密文件夹/zip 走现有逻辑。
- 加密 `.ysm` 必须走 YSMParser 语义的全版本解密路径；Java 实现必须与 YSMParser 解密等价。运行时可优先使用 Java 等价实现，debug/测试模式下必须与 YSMParser 权威路径做一致性校验；Java 等价实现失败时自动回退 YSMParser，解密后复用现有 resource index、animation、controller、function、controls 加载路径。
- 解密失败时：
  - UI 显示明确错误。
  - 不删除原模型。
  - 不覆盖当前衣柜选择。
  - 日志记录 crypto version、hash、错误阶段，不输出密钥。

### 5.3 加密备份、缓存与预览策略

第四大阶段不照搬 Sparkle-Morpher 的长期解密缓存。Sparkle-Morpher 的 server cache / client cache 转码适合其模型同步与服务端下发体系；Figura 公模库模型下载已经通过 HTTP / OSS hash 缓存解决传输复用，不需要再为预览复制一套长期加密解密缓存。

本阶段只保留必要的原始文件缓存、runtime prepared cache 和调试导出能力：

- `original`
  - 玩家选择或公模库下载的原始文件。
  - 加密 YSM 保持原样。
- `runtime-cache`
  - 解析后可快速加载的 Figura/YSM NBT 或 resource bundle。
  - 本地保存时必须使用 Figura 自有缓存壳加密或平台安全目录。
- `debug-export`
  - 仅在显式 debug 配置开启时生成。
  - 默认关闭。
  - 必须带醒目警告，不上传、不同步。

正式加载/启用缓存策略：

- 加密 YSM 默认缓存“解析结果”，不缓存裸明文目录。
- 缓存 key 包含：
  - 原始文件 SHA-256。
  - crypto version。
  - parser version。
  - Figura YSM resource index version。
  - selected texture id。
- 缓存失效条件：
  - 原文件 hash 变化。
  - parser 或格式版本变化。
  - native/JVM crypto 实现版本变化。
  - 用户清理 Figura 缓存。

客户端启用加密 YSM 策略：

- 客户端启用加密 YSM 时，也走与预览一致的 YSMParser 临时转换路径：
  - 读取原始加密 `.ysm`。
  - 调用 YSMParser 转为未加密 YSM 模型包或内存资源树。
  - 用未加密资源创建当前本地启用的 `YsmModelRuntime`。
- 如果玩家只是本地启用，尚未通过 Figura 衣柜上传同步接口发布：
  - 解密后的未加密 YSM 模型包只允许保留在内存中。
  - 该内存包用于当前本地运行、后续“上传到衣柜同步”的打包输入。
  - 不写入长期明文目录，不进入公模库缓存，不参与公模库上传。
- 如果玩家已通过衣柜上传同步接口完成上传：
  - 立即释放用于上传的未加密 YSM 内存包。
  - 后续多人可见性只依赖第二大阶段已有 Figura Avatar hash / equip 同步结果。
  - 本地继续渲染所需资源只能保留为当前 runtime 或受控 prepared cache，不能保留可直接还原的长期明文包。

预览策略：

- 模型预览使用轻量临时缓存，不使用长期解密缓存。
- 加密 YSM：
  - 用户选中该模型用于预览时，客户端读取已下载的原始加密对象。
  - 调用 YSMParser 将加密模型临时转换为未加密模型包或内存资源树。
  - 用转换后的临时资源创建预览用 `YsmModelRuntime`。
  - 临时未加密包只允许存在于内存或受控临时目录。
  - 用户切换到其它预览、关闭公模库界面或预览失败时，立即释放临时资源、纹理、mesh、YSM runtime 和临时目录。
- 未加密 YSM：
  - 直接从 zip / 文件对象读入内存资源树。
  - 创建预览 runtime。
  - 用户切换到其它预览或关闭界面时直接释放内存资源和 runtime。
- Figura Avatar：
  - 直接从下载对象或本地 zip 构建临时 Avatar 预览。
  - 切换预览时释放临时 Avatar。
- 预览临时缓存不参与多人同步、不写入公模上传、不写入玩家衣柜。

释放要求：

- 每次只允许一个 active preview runtime。
- 切换选中模型前必须 dispose 当前 preview runtime。
- dispose 必须释放：
  - texture。
  - mesh / accelerated mesh。
  - native/GPU handle。
  - zip filesystem。
  - YSMParser 临时输出目录。
  - Java heap 中的明文资源引用。

### 5.4 安全要求

- 不在日志输出 key、iv、完整明文路径、解密 payload。
- 对异常文件设置最大解压后大小，防止压缩炸弹。
- 对 header、padding、zstd offset、tail offset 做边界校验。
- 对 model、texture、animation、function 单项资源设置大小上限。
- 加密模型可以加载，不代表模型内容可信；Lua 权限、Molang 限制、材质大小限制仍按 Figura 原有规则执行。

## 6. 公模库后端设计

### 6.1 模块划分

建议在 `sculptor` 中新增：

```text
sculptor/src/api/public_models/
```

模块：

- `routes.rs`
  - API 路由。
- `types.rs`
  - request / response DTO。
- `service.rs`
  - 业务逻辑。
- `storage.rs`
  - 文件存储。
- `metadata.rs`
  - 数据库或 metadata 文件。
- `admin.rs`
  - 管理员鉴权与操作。
- `cache.rs`
  - hash、ETag、预览缓存。

### 6.2 数据模型

公模条目：

```text
PublicModel
  id: string
  owner_uuid: uuid              # 上传者 / 条目所有者 / 权限主体
  owner_display_name: string    # 上传者展示名，默认当前玩家名，可编辑
  name: string
  description: string
  kind: YSM_UNENCRYPTED | YSM_ENCRYPTED | FIGURA_AVATAR
  visibility: PUBLIC | UNLISTED | LOCKED | REMOVED
  license: string
  tags: string[]
  created_at: timestamp
  updated_at: timestamp
  size_bytes: u64
  content_hash: sha256
  manifest_hash: sha256
  preview_hash: sha256 optional
  metadata_verified: bool
  model_metadata_raw: json       # 模型内原始 metadata，包含模型作者
  public_metadata: json          # 公模展示字段，不包含额外作者名
  favorites: u64
  downloads: u64
  version: u32
  permissions:
    download: everyone | authorized | owner | admin
    remix: allowed | forbidden | attribution_required
    upload_update: owner | admin
```

文件存储：

```text
<public_models.storage_path>/
  objects/
    sha256/<hash>
  previews/
    sha256/<hash>.png
  metadata/
    <id>.json
  temp_uploads/
    <session_id>/
```

第一版默认使用本地文件系统；后端也必须预留可选 OSS / S3 兼容对象存储后端，用于把模型对象、预览图和上传临时对象存放到外部对象存储。

去重原则：

- 相同 `content_hash` 的模型对象只存一份。
- 不同条目可引用同一对象，但 metadata 独立。
- 删除条目默认软删除；对象无引用后由后台清理。

### 6.2.1 SQLite 数据库规划

`sculptor` 当前核心状态主要在内存 `AppState`、配置文件和 Avatar 文件存储中；第四大阶段公模库需要搜索、排序、收藏、授权、审计、上传会话和软删除，这些内容适合进入 SQLite，而不是继续散落为多个 JSON 文件。

建议新增：

```text
<public_models.storage_path>/
  public_models.sqlite3
  public_models.sqlite3-wal
  public_models.sqlite3-shm
```

SQLite 适合保存：

- 公模条目 metadata：
  - `id`
  - `owner_uuid`，上传者 / 条目所有者，不等于模型作者。
  - `owner_display_name`，上传者展示名，默认当前玩家名，可由上传者编辑，但不参与权限判断。
  - `name`
  - `description`
  - `category`
  - `kind`
  - `visibility`
  - `license`
  - `created_at`
  - `updated_at`
  - `deleted_at`
  - `locked_at`
  - `version`
  - `metadata_verified`
- 文件对象索引：
  - `content_hash`
  - `size_bytes`
  - `object_key`
  - `storage_backend`
  - `mime_type`
  - `ref_count`
  - `created_at`
- 预览图索引：
  - `preview_hash`
  - `object_key`
  - `storage_backend`
  - `width`
  - `height`
  - `created_at`
- 原始模型 metadata 快照：
  - `model_id`
  - `model_metadata_raw`，模型包、header、manifest 中的原始作者、名称、描述、license 等字段。
  - `public_metadata`，公模库展示字段，可由上传者或管理员编辑，但不得覆盖 `owner_uuid` 或 `model_metadata_raw`。
- 标签：
  - `tags`
  - `public_model_tags`
- 收藏：
  - `user_uuid`
  - `model_id`
  - `created_at`
- 授权：
  - `model_id`
  - `authorized_uuid`
  - `granted_by`
  - `created_at`
  - `expires_at` 可选。
- 上传会话：
  - `session_id`
  - `owner_uuid`
  - `expected_hash`
  - `chunk_size`
  - `total_chunks`
  - `received_chunks`
  - `temp_path`
  - `created_at`
  - `expires_at`
- 下载/收藏统计缓存：
  - `downloads`
  - `favorites`
  - `last_stats_update`
- 管理员审计日志：
  - `request_id`
  - `admin_uuid`
  - `action`
  - `model_id`
  - `old_value`
  - `new_value`
  - `created_at`

SQLite 不适合保存：

- 模型二进制文件。
- 预览 PNG 二进制。
- 分片上传 chunk 二进制。
- 客户端 prepared cache 大对象。
- 解密后的明文资源目录。

模型二进制、预览图和上传临时对象应保存在配置的对象存储后端中，并由 SQLite 记录 hash、对象 key、大小和引用关系。默认对象存储后端为本地文件系统 `<public_models.storage_path>`，可选后端为 OSS / S3 兼容对象存储。

后端不缓存已解密模型内容：

- 上传 metadata 由客户端解析/解密后预填到上传输入对话框，随上传请求提交。
- 后端持久化 `model_metadata_raw` 和 `public_metadata`，它们只是结构化 JSON 快照，不是解密后的模型包。
- 后端可以为了复验 metadata 临时调用 YSMParser 解密加密 YSM，但解密结果只允许在内存或上传会话临时空间中存在。
- 复验结束、上传失败、上传会话过期或请求中断时，后端必须释放解密明文、关闭临时目录并清理临时文件。
- 后端对象存储只保存原始上传模型对象：加密 YSM 仍以加密 `.ysm` 原样保存，非加密 YSM / Figura 以原始 zip 或对象原样保存。
- 公模库后端不保存可直接还原的明文 prepared cache；prepared cache 属于客户端本地加载优化或当前 runtime 生命周期。

### 6.2.2 对象存储后端

建议抽象：

```text
PublicModelObjectStore
  put_object(hash, stream, content_type)
  get_object(hash)
  stat_object(hash)
  delete_object(hash)
  put_preview(hash, stream)
  get_preview(hash)
  create_upload_session(...)
  write_upload_chunk(...)
  complete_upload_session(...)
  abort_upload_session(...)
```

后端类型：

```text
public_models.storage_backend = local | oss | s3
```

本地文件系统：

```text
public_models.storage_backend = local
public_models.storage_path = /var/lib/sculptor/public-models
```

对象 key 约定：

```text
objects/sha256/<content_hash>
previews/sha256/<preview_hash>.png
temp_uploads/<session_id>/<chunk_index>
```

OSS / S3 兼容对象存储：

```text
public_models.storage_backend = oss
public_models.oss.endpoint = ...
public_models.oss.bucket = ...
public_models.oss.region = ...
public_models.oss.access_key_env = PUBLIC_MODELS_OSS_ACCESS_KEY
public_models.oss.secret_key_env = PUBLIC_MODELS_OSS_SECRET_KEY
public_models.oss.prefix = figura-public-models/
public_models.oss.use_path_style = false
```

要求：

- Access key / secret key 必须从环境变量或安全 secret provider 读取，不写入普通配置文件。
- SQLite 保存的是 storage backend、bucket/prefix、对象 key、hash、大小和引用关系，不保存对象二进制。
- OSS 对象 key 必须包含 hash，避免同名覆盖。
- 上传完成前写入临时 key，hash 校验成功后 copy/rename 到正式 key。
- 使用 `local` 后端时，由 sculptor 读取本地对象并流式返回客户端。
- 使用 `oss` / `s3` 后端时，客户端必须直接通过短期 signed URL 从对象存储下载，sculptor 只负责鉴权、签发 URL、记录审计与返回下载信息。
- signed URL 必须短 TTL，建议 1-5 分钟。
- signed URL 只能绑定指定 object key、HTTP method、content hash，可选绑定 content-disposition。
- signed URL 不得长期缓存到客户端配置文件；客户端只在本次下载任务中使用。
- 删除公模默认软删除 metadata，不立即删除 OSS 对象；后台 GC 根据 SQLite `ref_count` 清理无引用对象。
- OSS 不可用时，列表 API 仍可返回 metadata，但下载/预览应返回明确存储错误。
- 本地 FS 与 OSS 后端必须共享同一套 hash、ETag、Range、cache header 语义。

存储选择建议：

- 小型私有后端：使用 `local`。
- 公共公模库、多人部署、需要 CDN：使用 `oss` / `s3`。
- 即使使用 OSS，SQLite 仍建议放在 sculptor 本地或正式数据库文件路径中，不放进 OSS。

推荐 schema：

```text
public_models(
  id TEXT PRIMARY KEY,
  owner_uuid TEXT NOT NULL,
  name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  category TEXT NOT NULL,          -- YSM | FIGURA
  kind TEXT NOT NULL,              -- YSM_UNENCRYPTED | YSM_ENCRYPTED | FIGURA_AVATAR
  visibility TEXT NOT NULL,
  license TEXT NOT NULL DEFAULT '',
  content_hash TEXT NOT NULL,
  preview_hash TEXT,
  metadata_verified INTEGER NOT NULL DEFAULT 0,
  model_metadata_raw TEXT NOT NULL DEFAULT '{}',
  public_metadata TEXT NOT NULL DEFAULT '{}',
  size_bytes INTEGER NOT NULL,
  downloads INTEGER NOT NULL DEFAULT 0,
  favorites INTEGER NOT NULL DEFAULT 0,
  version INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER,
  locked_at INTEGER
)

public_model_objects(
  content_hash TEXT PRIMARY KEY,
  storage_backend TEXT NOT NULL,
  object_key TEXT NOT NULL,
  bucket TEXT,
  size_bytes INTEGER NOT NULL,
  mime_type TEXT NOT NULL,
  ref_count INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
)

public_model_previews(
  preview_hash TEXT PRIMARY KEY,
  storage_backend TEXT NOT NULL,
  object_key TEXT NOT NULL,
  bucket TEXT,
  width INTEGER,
  height INTEGER,
  created_at INTEGER NOT NULL
)

public_model_tags(
  model_id TEXT NOT NULL,
  tag TEXT NOT NULL,
  PRIMARY KEY(model_id, tag)
)

public_model_favorites(
  user_uuid TEXT NOT NULL,
  model_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY(user_uuid, model_id)
)

public_model_authorizations(
  model_id TEXT NOT NULL,
  authorized_uuid TEXT NOT NULL,
  granted_by TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER,
  PRIMARY KEY(model_id, authorized_uuid)
)

public_model_upload_sessions(
  session_id TEXT PRIMARY KEY,
  owner_uuid TEXT NOT NULL,
  expected_hash TEXT,
  temp_path TEXT NOT NULL,
  chunk_size INTEGER NOT NULL,
  total_chunks INTEGER NOT NULL,
  received_chunks INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
)

public_model_admin_audit(
  request_id TEXT PRIMARY KEY,
  admin_uuid TEXT NOT NULL,
  action TEXT NOT NULL,
  model_id TEXT,
  old_value TEXT,
  new_value TEXT,
  created_at INTEGER NOT NULL
)
```

索引要求：

- `public_models(category, updated_at)`
- `public_models(kind, updated_at)`
- `public_models(owner_uuid, updated_at)`
- `public_models(visibility, updated_at)`
- `public_models(downloads)`
- `public_models(favorites)`
- `public_model_tags(tag)`
- `public_model_favorites(user_uuid)`
- `public_model_authorizations(authorized_uuid)`

搜索实现：

- 第一版可用 SQLite `LIKE` / `LOWER(...)` 实现 `name`、`description`、`model_author`、`owner_display_name`、`tags` 搜索。
- `owner_uuid` 只能作为管理员视图的搜索/过滤字段；普通玩家搜索不得暴露或依赖上传者 UUID。
- 如果后续模型数量变大，再升级到 SQLite FTS5。
- 排序、分页 cursor 必须基于 SQLite 查询结果生成，不能只在内存中过滤当前页。

事务要求：

- 上传完成时，文件对象落盘、hash 校验、SQLite metadata 插入必须在一个业务事务中完成。
- 先写临时文件或临时对象，校验成功后原子 rename/copy 到 `objects/sha256/<hash>`。
- SQLite 事务提交失败时，必须清理临时文件或临时对象。
- 文件 rename / OSS copy 成功但 SQLite 提交失败时，后台 orphan object cleanup 根据 ref_count 清理。

### 6.3 独立 API 端点

建议端点统一放在：

```text
/api/public-models
```

玩家端：

```text
GET    /api/public-models
GET    /api/public-models/:id
GET    /api/public-models/:id/download
GET    /api/public-models/:id/preview
PUT    /api/public-models/:id/favorite
DELETE /api/public-models/:id/favorite
POST   /api/public-models/upload
POST   /api/public-models/upload/start
PUT    /api/public-models/upload/:session/chunk/:index
POST   /api/public-models/upload/:session/finish
DELETE /api/public-models/:id
PATCH  /api/public-models/:id
```

管理员端：

```text
POST   /api/public-models/admin/login
GET    /api/public-models/admin/list
PATCH  /api/public-models/admin/:id
DELETE /api/public-models/admin/:id
POST   /api/public-models/admin/:id/authorize
POST   /api/public-models/admin/:id/revoke
POST   /api/public-models/admin/:id/rename
POST   /api/public-models/admin/:id/lock
POST   /api/public-models/admin/:id/unlock
```

列表端点必须支持搜索、过滤、排序和分页：

```text
GET /api/public-models?query=<keyword>&category=<YSM|FIGURA>&kind=<kind>&tag=<tag>&owner=<uuid>&visibility=<visibility>&favorite=<bool>&sort=<sort>&cursor=<cursor>&limit=<n>
```

搜索字段：

- `name`
- `description`
- `tags`
- `model_author`，来自模型内作者字段。
- `owner_display_name`，上传者玩家名展示字段。
- `owner uuid`，上传者 UUID，仅管理员视图可用。
- 后端可选支持 owner nickname 或 cached profile name。

过滤字段：

- `category`
  - `YSM`
  - `FIGURA`
- `kind`
  - `YSM_UNENCRYPTED`
  - `YSM_ENCRYPTED`
  - `FIGURA_AVATAR`
- `tag`
- `owner`
- `visibility`
- `downloadable`
- `authorized`
- `favorite`

排序字段：

- `relevance`
- `updated_desc`
- `created_desc`
- `downloads_desc`
- `favorites_desc`
- `name_asc`
- `size_asc`
- `size_desc`

分页要求：

- 使用 opaque cursor。
- 客户端不得自行构造 cursor。
- 搜索条件变化后必须丢弃旧 cursor，从第一页重新查询。

说明：

- 玩家删除/修改只允许 owner 自己的模型，且受后端策略限制。
- 管理员 API 必须同时校验 Figura token 与管理员密码会话。
- 管理员密码不替代 Figura token；它是二次确认。
- 下载端点必须支持 cache header。
- 收藏是每个玩家自己的公模库状态，不改变模型文件内容，也不触发普通模型列表实时刷新；如 UI 显示收藏数，可低频聚合为 `STATS_UPDATED`。

### 6.6 实时列表刷新

目标：公模被上传、删除、改名、锁定、解锁、授权变化后，所有正在打开公模库界面的在线玩家应实时看到列表变化。

WebSocket 建议新增消息：

```text
S2C PublicModelEvent
  eventId: u64
  modelId: string
  action: CREATED | UPDATED | DELETED | LOCKED | UNLOCKED | AUTH_CHANGED | PREVIEW_UPDATED | STATS_UPDATED
  version: u32
  changedAt: timestamp
  summary: optional PublicModelListItem
```

广播时机：

- 玩家上传新公模成功。
- owner 修改名称、描述、标签、可见性。
- owner 删除自己的公模。
- 管理员删除、恢复、改名、锁定、解锁。
- 管理员授权或撤销授权。
- 后端重新生成 preview。

不广播或低频广播：

- 单个玩家下载公模不触发普通列表刷新。
- 下载次数统计可延迟聚合为 `STATS_UPDATED`，避免高频下载导致全服列表抖动。

后端要求：

- `PublicModelEvent` 只发送给已声明支持公模库能力的客户端。
- 事件 payload 不携带模型二进制。
- 事件只携带列表增量或失效信号。
- 对 `AUTH_CHANGED`，如果权限判断复杂，客户端收到后应重新拉取对应条目或当前页。
- 后端必须为事件设置递增 `eventId`，方便客户端去重。

客户端要求：

- `PublicModelLibraryScreen` 打开时订阅或启用公模事件处理。
- 收到 `CREATED`：
  - 若符合当前过滤条件，插入或重新拉取第一页。
- 收到 `UPDATED` / `LOCKED` / `UNLOCKED` / `PREVIEW_UPDATED`：
  - 若当前列表存在该条目，更新条目。
  - 若权限或过滤条件可能变化，重新拉取当前页。
- 收到 `DELETED`：
  - 从当前列表移除或显示“已删除”状态。
  - 若详情页正在查看该模型，立即切换到不可下载状态。
- 收到 `AUTH_CHANGED`：
  - 重新拉取当前页和对应详情。
- 事件丢失、乱序或版本不匹配时，触发一次 HTTP 全量刷新。

缓存要求：

- metadata cache 收到相关事件后立即失效。
- object cache 不因 metadata 删除立即删除；已下载对象保留在本地缓存，除非用户清理。
- prepared cache 不因公模删除自动删除，但列表和详情必须显示远端已不可用。

### 6.4 上传协议

小文件直传：

```text
POST /api/public-models/upload
Headers:
  token: <figura token>
  content-type: multipart/form-data

fields:
  metadata: json
  file: bytes
  preview: png optional  # 默认由客户端上传预览窗自动截图生成
```

大文件分片：

```text
POST /api/public-models/upload/start
  -> session id, chunk size, max chunks

PUT /api/public-models/upload/:session/chunk/:index
  -> chunk hash accepted

POST /api/public-models/upload/:session/finish
  -> final hash, model id
```

校验：

- 文件大小。
- chunk hash。
- final SHA-256。
- kind 与内容检测结果一致。
- Figura Avatar 必须能被基础 NBT/Avatar parser 识别。
- YSM 非加密必须能读取 manifest/resource index。
- YSM 加密必须至少通过 header/hash/crypto version 检测；后端可不解密内容，但必须记录为 `YSM_ENCRYPTED`。

### 6.5 下载与缓存

下载策略：

- `GET /api/public-models/:id/download` 返回原始模型对象。
- 使用 `ETag: "<content_hash>"`。
- 支持 `If-None-Match` 返回 `304`。
- 支持 `Range`，便于大文件续传。
- 响应包含：
  - `X-Figura-Public-Model-Id`
  - `X-Figura-Public-Model-Kind`
  - `X-Figura-Public-Model-Hash`
  - `X-Figura-Public-Model-Version`

当后端使用 OSS / S3 存储时：

- `GET /api/public-models/:id/download` 不直接返回模型二进制。
- sculptor 返回一个短期 signed URL 下载描述：

```json
{
  "mode": "signed_url",
  "url": "https://...",
  "expiresAt": "2026-07-19T00:05:00Z",
  "contentHash": "sha256...",
  "size": 1234567,
  "headers": {
    "etag": "\"sha256...\""
  }
}
```

- 客户端随后直接访问 signed URL 下载模型文件。
- 下载完成后客户端必须校验 `contentHash`。
- signed URL 过期、403、404 或 hash 校验失败时，客户端重新向 sculptor 请求新的下载描述。
- preview 如果也存放在 OSS / S3，同样使用短期 signed URL。

当后端使用本地存储时：

- `GET /api/public-models/:id/download` 由 sculptor 直接流式返回模型二进制。
- `GET /api/public-models/:id/preview` 由 sculptor 直接流式返回预览图。

客户端缓存：

```text
figura/cache/public_models/
  objects/<sha256>
  metadata/<id>.json
  previews/<preview_hash>.png
  prepared/<sha256>-<parserVersion>.nbt
```

缓存命中：

- 列表 metadata 使用短 TTL。
- 模型对象按 hash 永久缓存，直到用户清理。
- prepared cache 按 parser/runtime version 失效。

客户端下载流程：

```text
request download descriptor from sculptor
  -> local backend: stream response body from sculptor
  -> oss/s3 backend: receive signed URL, download directly from OSS/S3
  -> verify SHA-256
  -> store in local object cache
  -> prepare/import
```

## 7. 公模库客户端设计

### 7.1 衣柜旁入口

在 `WardrobeScreen` 右侧工具区新增公模库按钮：

- 图标按钮，不使用大文本按钮。
- tooltip：`gui.public_models.tooltip`。
- 点击打开 `PublicModelLibraryScreen`。

界面关系：

```text
WardrobeScreen
  -> PublicModelLibraryScreen
       -> PublicModelDetailScreen
       -> PublicModelUploadScreen
       -> PublicModelAdminScreen 可选
```

`PublicModelLibraryScreen` 打开期间必须监听后端 `PublicModelEvent`，让列表、详情、权限状态和不可下载状态实时刷新。

### 7.2 公模库主界面

布局：

- 左侧：分类、搜索、标签、筛选、排序。
- 中间：公模列表。
- 右侧或中间预览区：模型预览。
- 底部：下载、启用、上传、刷新。

公模库列表布局必须采用类似 Sparkle-Morpher 资源站界面的卡片列表/网格布局，但视觉和控件必须保持 Figura UI 风格：

- 顶部仍使用 Figura 的页签/标题栏结构，在衣柜 `模型` 页旁新增 `公模库` 入口，不做独立风格窗口。
- 左侧过滤栏沿用 Figura 的 `Button` / `SwitchButton` / `SearchBar` / `ContextMenu` 风格，按钮高度、描边、悬停态、禁用态和工具提示与当前衣柜一致。
- 中间列表实现为 `PublicModelCardListWidget`，继承或参考 `AbstractList` 的滚动、裁剪和 `ScrollBarWidget` 行为。
- 卡片实现为 `PublicModelCardWidget`，参考 `AvatarWidget` 的按钮外观、选中白色描边、收藏星标、右键菜单和文本省略逻辑，但条目高度改为卡片式。
- 卡片背景、列表面板和详情面板统一使用 `UIHelper.OUTLINE_FILL` / `UIHelper.OUTLINE` / `Button` 默认贴图，不引入圆角卡片、渐变背景或 Web 风格控件。
- 图标按钮使用 Figura 现有 16/20px 图标按钮规格；上传、下载、启用、收藏、刷新、管理入口都必须有 tooltip。
- 搜索、筛选、排序控件必须放在列表上方或左侧过滤栏，不能把关键操作藏在右键菜单里。

推荐桌面布局：

```text
PublicModelLibraryScreen
  top tabs:
    [模型] [公模库] [设置]

  content:
    left filter panel       center card grid/list                  right detail/preview
    260-300 px              remaining width                        280-360 px
    category tabs           search bar + sort row                  static preview / EntityPreview
    tags/filter buttons     card grid                              metadata
    favorites toggle        pagination/loading row                 action buttons
```

卡片尺寸与内容：

- 列表宽度足够时使用 2 到 4 列网格；宽度不足时自动降级为 1 列。
- 单卡推荐高度 `72-96 px`，宽度由列数计算；必须保持固定高度，避免名称、作者或标签过长导致列表跳动。
- 左侧显示 `64x64` 静态预览图；没有预览图时显示 Figura `unknown_icon` 或专用占位图。
- 右侧第一行显示公模名称，使用 `TextUtils.trimToWidthEllipsis` 裁剪。
- 第二行只显示两项名字信息：`上传者: ownerName` 与 `作者: modelAuthor`。普通玩家不显示 `owner_uuid`。
- 第三行显示徽标：`YSM` / `Figura`、`加密` / `未加密`、授权状态、已下载、已收藏。
- 右上角显示收藏星标；管理员模式下可额外显示锁定/删除/私有状态小图标。
- 选中卡片时绘制 Figura 当前选中描边；鼠标悬停使用 `Button` hover 贴图状态。
- 右键菜单提供收藏、下载到衣柜、复制模型 ID、打开详情；管理员会话下额外提供改名、删除、授权、锁定。

右侧详情/预览栏：

- 未选中时显示“选择一个模型”的 Figura 风格空状态。
- 选中后先展示静态预览 PNG，再按需加载本地 `EntityPreview` 预览。
- 详情栏显示名称、上传者玩家名、模型作者、类型、加密状态、大小、更新时间、下载数、收藏数、标签、权限状态。
- 管理员详情在通过管理员密码会话后额外显示上传者 UUID、content hash、manifest hash、存储 backend/object key。
- 底部固定操作区包含 `下载到衣柜`、`启用/同步到衣柜`、`收藏`、`刷新`；按钮不足时使用图标按钮并通过 tooltip 表达含义。

分页与加载：

- 卡片列表底部保留 Figura 风格分页栏，显示 `1-24/xxx`、上一页、下一页。
- 后端返回 cursor 时，分页按钮只保存 opaque cursor，不在客户端构造页码条件。
- 收到 `PublicModelEvent` 时保留当前 query/filter/sort/category，刷新当前页；如果当前选中条目被删除，详情栏切换为已删除状态。
- 加载中在列表中央使用 `UIHelper.renderLoading`；空列表显示简短空状态，不显示营销式说明。

小屏/低 GUI scale 布局：

- 窗口宽度不足时右侧详情栏收起为详情按钮或切换页签，中间卡片列表占主要区域。
- 过滤栏可压缩为顶部横向按钮行，但 `YSM` / `Figura` 一级分类、搜索框、收藏过滤必须仍可见。
- 文本必须全部裁剪或滚动显示，不允许覆盖预览图、徽标或按钮。

实现可行性：

- 当前 Figura 已有 `AvatarList`、`AvatarWidget`、`SearchBar`、`Button`、`SwitchButton`、`ContextMenu`、`ScrollBarWidget`、`EntityPreview` 和 `UIHelper`，足以实现该卡片列表。
- 需要新增的是数据驱动的 `PublicModelLibraryScreen`、`PublicModelCardListWidget`、`PublicModelCardWidget`、`PublicModelFilterPanelWidget`、`PublicModelDetailPanelWidget`。
- Sparkle-Morpher 只作为布局密度、资源站式卡片排列和左右栏比例参考；渲染材质、按钮、描边、字体、tooltip 和交互状态必须走 Figura 自己的控件体系。

过滤项：

- 全部。
- YSM。
- Figura。
- 我上传的。
- 已下载。
- 已收藏。
- 授权可用。

分类显示要求：

- 公模库模型列表必须按类型分类显示，一级分类只分 `YSM` 和 `Figura`。
- `YSM` 分类下包含非加密 YSM 与加密 YSM。
- `YSM` 分类在模型列表中不再做二级分类，不再拆成“非加密 YSM / 加密 YSM”两个列表分组。
- 加密状态只作为条目徽标或详情字段显示，例如 `加密` / `未加密`，不能作为列表二级分类。
- `Figura` 分类显示普通 Figura Avatar。
- 搜索、排序、收藏、标签筛选必须在当前一级分类内生效；切换 `YSM` / `Figura` 时保留关键词但重置分页 cursor。

列表条目展示：

- 名称。
- 模型作者，来自模型内 metadata。
- 上传者展示名，默认当前玩家名，可编辑，来自 `owner_display_name`。
- 类型。
- YSM 条目的加密状态徽标。
- 大小。
- 更新时间。

可见性：

- 普通玩家的列表和详情名字区域只显示上传者玩家名与模型作者。
- `owner_uuid` 不在普通玩家名字区域展示。
- 管理员登录管理员密码会话后，可在管理界面和管理员详情中查看上传者 UUID。
- 下载状态。
- 收藏状态。
- 权限状态。

实时刷新行为：

- 上传新公模后，其他正在浏览公模库的玩家无需手动刷新即可看到新条目。
- 管理员删除公模后，其他玩家列表中的该条目立即消失或标记为已删除。
- 管理员改名、锁定、授权后，列表和详情立即更新。
- 当前玩家正在下载的模型如果被删除，已开始的下载可继续完成或由后端返回取消错误；UI 必须显示远端状态已变化。
- 下载动作本身不改变其他玩家列表，除非后端选择低频刷新下载次数。

搜索要求：

- 搜索框输入关键词后查询后端，不只过滤本地当前页。
- 普通玩家支持按模型名、描述、标签、上传者玩家名、模型作者搜索。
- 管理员视图在通过管理员密码会话后，额外支持按上传者 UUID 搜索和过滤。
- 支持 `YSM` / `Figura` 一级分类与关键词组合查询。
- 支持排序切换。
- 支持收藏过滤。
- 搜索结果必须分页加载。
- 搜索条件应显示在 UI 状态中，刷新列表或收到 `PublicModelEvent` 时保留当前搜索条件。
- 当 `PublicModelEvent` 影响当前搜索结果时，客户端应按当前 query/filter/sort 重新拉取当前页或增量更新。

### 7.3 模型预览

预览必须支持：

- 后端缩略图优先。
- 下载后可用本地 `EntityPreview` 加载 runtime 预览。
- 未下载或加密解析失败时显示静态 preview + 错误状态。

YSM 预览流程：

```text
metadata/preview
  -> object cache miss 时先展示缩略图
  -> 下载模型
  -> 加密 YSM: YSMParser 临时解密/转换为未加密模型包
  -> 未加密 YSM: 直接读入内存资源树
  -> 创建临时 Avatar/UserData
  -> EntityPreview 渲染
```

要求：

- 预览不能自动装备模型。
- 预览失败不能影响当前衣柜 Avatar。
- 加密 YSM 解密失败时只标记该模型，不破坏列表。
- 预览 runtime 必须有独立释放路径，关闭界面或选中其它模型时释放纹理、mesh、native/GPU 缓存引用、YSMParser 临时输出和明文资源引用。
- 预览不使用 Sparkle-Morpher 风格长期解密缓存。
- 未加密模型包预览只放入内存，切换预览后直接释放。

### 7.4 上传界面

玩家上传步骤：

1. 选择文件或当前衣柜 Avatar。
2. 客户端检测类型。
3. 客户端提取 metadata、可用动画列表和预览候选。
4. 在上传信息输入对话框中填写名称、描述、标签、授权方式，并选择用于预览截图的动画。
5. 在预览窗中调整视角、缩放、姿态和动画播放进度。
6. 上传时自动从当前预览窗截取静态预览图。
7. 上传。
8. 显示进度与结果。

支持来源：

- 本地 `.ysm` 加密文件。
- 本地 YSM 文件夹/zip。
- 当前 Figura Avatar。
- 当前已加载的 YSM Avatar 导出为公模包。

上传预览图策略：

- 静态预览图默认由客户端从上传界面的 `EntityPreview` 当前画面截取。
- 玩家可以在上传前旋转视角、调整缩放、切换纹理或动作帧，让截图达到合适展示角度。
- 点击上传时，如果玩家未手动上传预览 PNG，则自动截取当前预览窗 framebuffer，生成固定尺寸 PNG。
- 自动截图需要记录到 metadata：
  - `previewSource = "capture"`。
  - `previewCamera`：yaw、pitch、zoom、offset。
  - `previewAnimation`：动画 id、display name、时间点或 normalized progress。
- 如果模型无法创建 runtime 预览，则允许使用模型包内 icon / preview 或玩家手动选择 PNG。
- 后端只保存最终 PNG 对象和 `preview_hash`，不需要理解客户端截图视角。

上传动画选择：

- 上传信息输入对话框中增加“预览动画”下拉框。
- 下拉框用于驱动上传预览窗播放指定动画，也用于决定自动截图时的姿态。
- YSM 模型：
  - 动画列表取决于可用轮盘动画和已解析出的动作/控制入口。
  - 优先展示玩家实际可在 YSM 轮盘中选择的动画名称。
  - 如果轮盘没有可用动画，则提供 `默认站立/idle` 和 `当前姿态`。
- Figura Avatar：
  - 直接从 `.bbmodel` 或 Avatar 包内可解析动画列表获取。
  - 如果有多个 `.bbmodel`，按当前 Avatar 实际加载模型合并并去重。
  - 无可用动画时提供 `默认站立/idle` 和 `当前姿态`。
- 动画下拉框只影响上传预览图和公模展示 metadata，不改变模型文件本身。

上传前客户端校验：

- 文件存在。
- 类型可识别。
- 大小不超过后端 limits。
- 非加密 YSM 可以扫描资源。
- 加密 YSM 可以识别 crypto header。
- Figura Avatar 可以打包为当前后端可接受格式。

### 7.4.1 上传时模型信息提取

公模上传必须优先从模型自身提取 metadata，减少玩家手填，并保证公模列表中的作者、名称、描述、版本、预览等信息尽量来自模型包。

提取职责：

- 客户端上传前先提取一次，用于预填上传表单。
- 后端接收上传后必须再提取或校验一次，用于防止客户端伪造 metadata。
- 客户端填写的名称、描述、标签只能覆盖公模展示字段，不能覆盖模型原始 metadata。
- 模型原始 metadata 必须由客户端提取器和后端复验器产生，上传信息输入对话框只能只读展示，不允许玩家编辑、删除或伪造。
- 客户端 metadata 提取器还必须返回可用于上传预览的动画候选列表，供上传信息输入对话框的下拉框使用。
- 动画候选只作为 `public_metadata.previewAnimation` 建议值保存，不作为模型内容校验依据。

#### Figura Avatar

Figura Avatar 可直接读取 Avatar 包内已有信息：

- avatar metadata。
- name。
- author。
- version。
- description。
- badges 或 capabilities，可选。
- preview / icon，如果包内存在。
- `.bbmodel` 或 Avatar 包内可解析的 animation 列表。
- 文件 hash 与 avatar hash。

建议新增：

```text
PublicModelMetadataExtractor
  -> FiguraAvatarMetadataExtractor
```

提取来源应复用现有 Figura Avatar NBT / metadata 读取逻辑，不重新发明格式解析。若来源包含 `.bbmodel`，必须读取其 `animations` 列表，生成上传预览动画下拉框候选。

#### 非加密 YSM

非加密 YSM 文件夹或 zip 可直接读取对应 JSON：

- `ysm.json`
- 旧版 `main.json` 旁的 `description.ysm_extra_info`
- texture / icon / background 字段。
- `files.player.model.main`
- `files.player.model.arm`
- `files.player.texture`
- `files.player.animation`
- `files.player.animation_controllers`
- `functions/*.molang`

需要提取：

- 模型名称。
- 作者信息。
- 描述。
- license / free / link / contact，如果存在。
- YSM 格式版本。
- texture 列表。
- icon / preview 候选。
- 可用轮盘动画候选。
- 资源 hash 摘要。

建议新增：

```text
PublicModelMetadataExtractor
  -> YsmPlainMetadataExtractor
```

读取方式应复用 `YsmManifestReader` / `YsmResourceIndex`，保证上传 metadata 与实际 runtime 加载结果一致。动画候选应从 YSM 可用轮盘动画、animation controller 入口或已映射动作中提取；上传 UI 不再对 YSM 加密/非加密做二级分类。

#### 加密 YSM

加密 `.ysm` 必须先解密，再读取模型包内包含作者信息的 JSON 或 header metadata。

参考路径：

- YSMParser：
  - 根据 YSGP 版本创建 parser。
  - 作为加密 `.ysm` 全版本解密主路径。
  - 从路径或内存解析。
  - 输出 decrypted data。
  - 可保存为目录用于 debug。
- Sparkle-Morpher：
  - `YsmCrypt.decryptYsmFile(byte[])` 校验 hash、crypto version、XChaCha20/MT19937/ZSTD 解密链路。
  - encrypted file header 中可能包含 `<name>`、`<author>`、`<license>`、`<link-home>`、`<free>`、`<format>`、`<crypto>` 等文本 metadata。
  - 解密后仍需要读取 YSM 明文包内 `ysm.json` 或等价 manifest。

加密 YSM metadata 提取顺序：

1. 读取加密文件 header 文本 metadata。
2. 使用 `YsmCryptoService` 调用 YSMParser 全版本解密到内存。
3. 从解密后的明文包读取 `ysm.json`、`description.ysm_extra_info` 或等价 JSON。
4. 提取可用轮盘动画候选，供上传预览动画下拉框使用。
5. 合并 metadata：
   - 明文 manifest 优先作为结构化来源。
   - 加密 header 作为作者、license、link、format、crypto 的补充来源。
   - 两者冲突时保留两份原始值，并在 UI 显示可确认字段。
6. 解密中间数据只进入客户端临时内存，不默认写明文目录，也不作为上传内容提交给后端。

建议新增：

```text
PublicModelMetadataExtractor
  -> YsmEncryptedMetadataExtractor
```

安全要求：

- 后端可以选择只校验加密 header，不强制解密；但如果后端支持 YSMParser/native crypto，则必须服务端复验 metadata。
- 客户端预填 metadata 不可信，后端保存时必须以服务端提取结果为准或记录差异。
- 服务端复验加密 YSM metadata 时，只允许临时解密到内存或上传会话临时目录，复验结束后立即释放，不写入后端长期 cache。
- 后端持久化的 `model_metadata_raw` 是 header/manifest/包内 JSON 的字段快照，不是解密后的模型包、资源树或明文 zip。
- 解密失败时，上传界面允许玩家手填基础字段，但条目应标记 `metadataVerified=false`，并等待管理员审核或后端后续解析。
- 不在日志输出密钥、iv、解密明文、完整作者联系方式之外的敏感字段。

#### Metadata 合并规则

公模条目保存两组 metadata：

```text
model_metadata_raw
  来自模型包、header、manifest 的原始字段快照
  包含模型作者 model_author / authors，不代表上传者
  上传者不可编辑，用于防冒充和审计

public_metadata
  公模库展示用字段，可由上传者或管理员编辑
  可包含 owner_display_name / previewSource / previewCamera / previewAnimation
  不包含额外作者名；名字区域只显示上传者玩家名和模型作者

owner_uuid
  来自 Figura token 的上传者 / 条目所有者 / 权限主体
  不从模型 metadata 中读取，也不随模型作者变化

owner_display_name
  上传者展示名，默认填入当前玩家名
  允许上传者在上传信息输入对话框中修改
  不参与 owner 权限、删除权限、授权权限或审计主体判断
```

初次上传：

- `public_metadata.name` 默认取模型内 name。
- `public_metadata.description` 默认取模型内 description。
- `owner_uuid` 必须取当前上传者的 Figura 身份，不允许由上传表单或模型 metadata 填写。
- `owner_display_name` 默认取当前玩家名，可由上传者修改展示文本。
- `model_metadata_raw` 中的 name、author、description、license、link 等原始字段必须只读展示，不允许上传者在输入对话框中修改。
- `public_metadata.tags` 可由提取器根据 kind / format / texture / license 生成建议标签，但需要用户确认。
- `public_metadata.previewAnimation` 默认取上传对话框中选择的动画。
- `public_metadata.previewCamera` 默认取上传预览窗当前视角。
- `preview_hash` 默认由上传时自动截取的静态预览图生成。

后续管理：

- 管理员改名只改 `public_metadata.name`。
- 上传者或管理员修改上传者展示名时，只改 `owner_display_name`，不改 `owner_uuid`。
- 如果 `model_metadata_raw.model_author` 与 `owner_display_name` 不一致，列表和详情页必须同时显示“模型作者”和“上传者”两项。
- 只有重新上传/重新解析原始模型时，才允许更新 `model_metadata_raw` 中的模型作者快照。
- 原始 `model_metadata_raw` 不被覆盖。
- 详情页可显示“模型内作者”和“上传者”两个不同概念。

上传失败处理：

- 显示后端错误码。
- 保留表单内容。
- 分片上传可重试失败 chunk。
- 不自动重传无限循环。

### 7.5 启用模型

启用不是直接让公模库替换远端装备，而是：

```text
PublicModelLibraryScreen.select(id)
  -> download/cache
  -> prepare as local wardrobe avatar
  -> import into wardrobe
  -> select local wardrobe entry
  -> user clicks existing upload/equip flow 或提供明确“启用并上传”按钮
```

推荐第一版提供两个按钮：

- `下载到衣柜`
  - 只导入本地，不上传后端。
- `启用并上传`
  - 导入本地。
  - 调用现有 `NetworkStuff.uploadAvatar(...)`。
  - 触发现有装备/同步流程。

这样可以保持公模库职责独立，并保证其他玩家看到的是玩家当前装备 Avatar，而不是公模库条目引用。

### 7.6 模型导出

第四大阶段需要在 Figura 侧补齐明确的模型导出能力，服务于本地备份、公模上传前整理、跨实例分享和 YSM 生态兼容。

当前 Figura 基线：

- 衣柜上传路径主要是 `NetworkStuff.uploadAvatar(Avatar avatar)`。
- 当前上传把 `avatar.nbt` 通过 `NbtIo.writeCompressed(...)` 压缩为 bytes，再调用 HTTP `PUT /api/avatar`。
- `WardrobeScreen` 目前提供上传、reload、delete、打开本地 avatar 目录等入口。
- 这说明当前 Figura 已有“打包给后端”的能力，但没有形成面向玩家的通用“导出为 zip / .ysm”能力。

Sparkle-Morpher 参考：

- `RawYsmModel` 作为 YSM 原始模型中间表示。
- `YSMBinarySerializer.serialize(RawYsmModel, format, writeFooter)` 写出现代 YSM 二进制结构。
- `writeModern(...)` 写出：
  - sound files。
  - function files。
  - language files。
  - vehicles / projectiles。
  - animation files。
  - animation controller files。
  - textures。
  - main / arm geometry。
  - `ysm.json`。
- `YsmCrypt.encryptYsmFile(...)` 把明文 YSM 二进制压缩、padding、XChaCha20/MT19937 加密，并写出 `.ysm` 文件壳。

#### 7.6.1 普通 zip 导出

普通 zip 导出必须同时支持 Figura 与 YSM。

建议导出类型：

```text
ExportFormat
  FIGURA_ZIP
  YSM_ZIP
  AUTO_ZIP
```

行为：

- 当前 Avatar 是普通 Figura Avatar：
  - 导出为 Figura zip。
  - 保留 Figura Avatar 原始目录结构、metadata、scripts、textures、models、sounds 等资源。
  - 不尝试伪装成 YSM。
- 当前 Avatar 是 YSM native Avatar：
  - 导出为 YSM zip。
  - 保留 `ysm.json`、geometry、textures、animations、animation_controllers、functions、sounds、language、projectile、vehicle 等资源。
  - 导出结果应能被本项目 YSM loader 重新导入。
- `AUTO_ZIP`：
  - 根据当前 Avatar kind 自动选择 Figura zip 或 YSM zip。

要求：

- zip 内路径必须稳定，不能写入本机绝对路径。
- zip 必须包含导出 metadata：
  - exporter。
  - Figura version。
  - YSM runtime version。
  - original kind。
  - content hash。
  - export time。
- zip 导出不加密，适合作为普通备份和公模上传源。
- zip 导出后可被公模上传界面直接识别并预填 metadata。

#### 7.6.2 加密 `.ysm` 导出

Figura 侧新增导出为加密 `.ysm`，但只允许 YSM 结构模型使用。

硬性限制：

- 只有当前 Avatar 是 YSM native Avatar，且资源结构可还原为 YSM 包时，才能导出 `.ysm`。
- 普通 Figura Avatar 不能导出为 `.ysm`。
- 如果当前 Avatar 是 Figura 模型、Lua 驱动模型、混合 Figura/YSM 但无法还原完整 YSM 结构，导出按钮必须置灰或报错。
- 不能把 Figura `.bbmodel` / Lua / FiguraModelPart 强行转换为 `.ysm`，否则导出的文件在 YSM 生态中不可用。

导出流程：

```text
YsmModelRuntime / YsmPackage
  -> YsmExportBundle
  -> RawYsmModel-like intermediate
  -> YSM modern binary serializer
  -> YsmEncryptor.encryptYsmFile
  -> output .ysm
```

建议新增：

```text
org.figuramc.figura.avatar.export
  AvatarExportService
  AvatarExportFormat
  AvatarExportRequest
  AvatarExportResult
  FiguraZipExporter
  YsmZipExporter
  YsmEncryptedExporter

org.figuramc.figura.avatar.ysm.export
  YsmExportBundle
  YsmExportBundleBuilder
  YsmBinaryWriter
  YsmEncryptedWriter
```

`YsmBinaryWriter` 应参考 Sparkle-Morpher `YSMBinarySerializer`，但输入来自 Figura 的 `YsmPackage` / `YsmResourceIndex` / `YsmModelRuntime`，不直接依赖 Sparkle-Morpher runtime。

`.ysm` 加密写出要求：

- 可以参考 Sparkle-Morpher `YsmCrypt.encryptYsmFile(...)` 的文件结构、header 写入、padding、压缩、XChaCha20、MT19937 xor 和校验流程。
- 可以复用或移植 YSMParser 中已经验证过的算法实现，保证导出的 `.ysm` 能被 YSMParser 原生解析。
- Java 侧导出算法必须与 YSMParser 解密语义闭环：`export encrypted .ysm -> YSMParser decrypt -> 原始 YSM payload` 必须成立。
- 写入 header metadata：
  - `<name>`
  - `<author>`
  - `<license>`
  - `<link-home>` 可选。
  - `<free>` 可选。
  - `<format>`
- `<crypto>`
  - source hash 摘要。
- 明文 payload 应为合法 YSM modern binary。
- 导出后必须立即用本项目 Java 等价解密路径和 YSMParser 权威路径做回读校验。
- 回读校验通过后才提示导出成功。

验收：

- YSM native Avatar 可导出普通 YSM zip。
- YSM native Avatar 可导出加密 `.ysm`。
- 导出的 `.ysm` 可以被 Java 等价解密路径加载。
- 导出的 `.ysm` 可以被 YSMParser 解密。
- 解密后的资源可被 `YsmResourceIndex` 扫描，并能重新进入 `YsmModelRuntime`。
- 普通 Figura Avatar 导出 `.ysm` 时被拒绝，并显示“只有 YSM 结构模型可导出为 .ysm”。

#### 7.6.3 导出 UI

导出入口建议放在：

- `WardrobeScreen` 当前 Avatar 工具区。
- `PublicModelDetailScreen` 已下载模型详情页。
- `PublicModelUploadScreen` 上传前整理步骤。

UI 行为：

- 对 Figura Avatar：
  - 显示 `导出 zip`。
  - `.ysm` 导出置灰。
- 对 YSM Avatar：
  - 显示 `导出 YSM zip`。
  - 显示 `导出加密 .ysm`。
- 对导入自公模库的模型：
  - 遵守公模 license / remix 权限。
  - 若模型声明禁止再分发，导出按钮应提示限制；本地备份与公开再上传需要分开处理。

## 8. 管理员管理功能

### 8.1 管理员来源

后端配置：

```text
public_models.admins = [uuid...]
public_models.admin_password_hash = argon2id(...)
public_models.admin_session_ttl = 15m
public_models.admin_session_persistent = false
```

管理员判定：

- Figura token 对应 UUID 必须在管理员列表中。
- 操作前必须通过管理员密码登录。
- 管理员 session 独立于 Figura token，短 TTL。
- 高风险操作必须重新确认密码或要求 session 未超过短确认窗口。
- 管理员密码会话只在本次游戏进程内有效；退出游戏后必须重新输入。

### 8.2 管理界面

在 `PublicModelLibraryScreen` 中，管理员账号显示管理入口：

- 打开时要求输入管理员密码。
- 密码只提交给 `admin/login`，客户端不保存明文。
- 登录后进入 `PublicModelAdminScreen`。
- 管理员会话只保存在内存中，不写入配置文件、磁盘缓存、NBT、日志或系统凭据。
- 退出 Minecraft、重启游戏、切换账号、后端 token 变化、服务器地址变化、WebSocket 重新鉴权失败后，必须清空管理员会话并要求重新输入密码。

管理功能：

- 删除模型。
- 恢复软删除模型。
- 改名。
- 修改描述、标签、license。
- 锁定/解锁模型。
- 修改可见性。
- 授权指定玩家下载。
- 撤销授权。
- 查看上传者玩家名、上传者 UUID、hash、大小、版本、下载次数。

操作要求：

- 所有管理操作必须有确认对话框。
- 删除默认软删除。
- 硬删除第一版不开放给 UI，后端可保留维护命令。
- 管理日志必须记录：
  - admin uuid
  - action
  - model id
  - old value
  - new value
  - timestamp
  - request id

### 8.3 安全策略

- 管理员密码只在 HTTPS API 中传输。
- 后端只存 Argon2id hash。
- 登录失败限流。
- 管理 API 不允许仅凭管理员密码访问，必须同时有 Figura token。
- 管理员 session token 不写入长期配置。
- 管理员 session token 只保存在当前游戏进程内存。
- 客户端关闭界面、超时、退出游戏、切换账号、后端 token 变化、后端地址变化或 WebSocket 认证状态重置后清空 session。
- 不提供“记住管理员密码”或“下次自动登录管理员”的选项。

## 9. 与现有 Figura 同步的关系

公模库不改变第二大阶段同步原则：

- 模型资源仍通过 HTTP 下载。
- 玩家当前装备 Avatar 仍由现有 `/api/avatar` 与 `/api/equip` 管理。
- 其他玩家仍通过 Avatar hash 下载玩家装备的 Avatar。
- 公模库条目 id 不作为远端玩家渲染的必要条件。
- 加密 YSM 公模被客户端启用时，先在本地通过 YSMParser 临时转换为未加密 YSM 模型包或内存资源树。
- 本地启用但尚未上传到 Figura 同步接口时，客户端在内存中保留该未加密 YSM 模型包，作为当前本地 runtime 与后续衣柜上传输入。
- 一旦玩家通过衣柜完成上传同步，客户端必须释放该未加密 YSM 内存包；远端同步只依赖已上传的 Avatar 内容和现有装备状态。

公模库与衣柜关系：

```text
公模库
  负责：公开模型存储、检索、下载、管理

衣柜
  负责：玩家本地 Avatar 选择、编辑、上传、装备

后端 Avatar API
  负责：当前玩家装备 Avatar 分发
```

这种拆分可以避免：

- 公模被删除后导致已装备玩家不可见。
- 管理员修改公模名称影响玩家当前 Avatar。
- 远端客户端为了看某玩家模型必须额外访问公模库。

## 10. 工作包拆分

### 工作包 A：加密 YSM 检测与解密基础

目标：让加密 `.ysm` 可以进入现有 YSM resource index。

任务：

- 新增 `YsmEncryptedPackageDetector`。
- 新增 `YsmCryptoService`。
- 接入 YSMParser 作为全版本权威参考路径：
  - path input。
  - memory input。
  - `getYSGPVersion()`。
  - `getDecryptedData()`。
- Java 解密必须实现为 YSMParser 解密的 Java 等价实现：
  - YSGP v1。
  - YSGP v2。
  - YSGP v3。
  - YSMParser 已支持的其它历史格式。
- 移植或重写必要 crypto / compression 算法：
  - CityHash。
  - MT19937 xor。
  - XChaCha20 modified state。
  - ZSTD decompress。
- 给 `YsmAvatarDetector` 增加 `.ysm` 加密识别。
- 给 `YsmAvatarLoader` 增加解密后资源树输入。

验收：

- 加密 `.ysm` 能被识别为 YSM。
- YSMParser 支持的旧版和新版加密模型都可以通过 Java 解密路径进入现有 runtime。
- Java 解密输出与 YSMParser 输出通过 fixture 一致性校验。
- Java 失败但 YSMParser 成功时可自动回退，并记录 Java 解密等价性缺陷。
- 非加密 YSM 行为不变。
- 解密失败有明确错误，不崩溃。

### 工作包 B：加密缓存与备份

目标：正式加载/启用时避免重复解析，同时预览阶段不保留长期明文缓存。

任务：

- 新增 `YsmEncryptedCache`。
- prepared cache 保存正式加载/启用所需的解析后 bundle。
- 增加 cache version。
- 增加 debug export 开关。
- 增加清理入口。
- 预览阶段不写长期解密缓存。
- 加密模型预览通过 YSMParser 临时转换为未加密模型包或内存资源树。
- 未加密模型预览直接放入内存资源树。
- 切换预览或关闭界面时释放临时资源。
- 加密 YSM 本地启用也走 YSMParser 临时转换路径。
- 本地启用但尚未上传同步时，在内存中保留未加密 YSM 模型包。
- 上传到 Figura 衣柜同步接口完成后，立即释放该未加密 YSM 内存包。

验收：

- 第二次加载同一加密模型命中缓存。
- 修改模型文件后缓存失效。
- 默认不会在磁盘生成明文解包目录。
- 清理缓存后可重新解密加载。
- 加密模型预览不会留下长期明文缓存。
- 未加密模型预览切换后内存资源被释放。
- 加密 YSM 本地启用后、上传同步前，未加密模型包只存在于内存。
- 上传同步完成后，未加密模型包引用被释放，不再可被公模库或缓存系统复用。

### 工作包 C：后端公模库 API

目标：`sculptor` 提供独立公模库服务。

任务：

- 新增 `public_models` 路由。
- 新增 SQLite metadata / index / relation 存储。
- 新增对象文件存储，模型文件落盘到 `<public_models.storage_path>/objects/sha256/<content_hash>`。
- 新增 preview 文件存储，预览图落盘到 `<public_models.storage_path>/previews/sha256/<preview_hash>.png`。
- 新增上传临时目录 `<public_models.storage_path>/temp_uploads/<session_id>/`。
- 新增 `PublicModelObjectStore` 抽象。
- 支持 `local` 对象存储后端。
- 预留并计划支持 `oss` / `s3` 兼容对象存储后端。
- 新增 SQLite migration。
- 实现列表、详情、下载、预览。
- 实现搜索、过滤、排序、分页查询。
- 实现收藏/取消收藏端点与每用户收藏状态。
- 实现上传时服务端 metadata 提取与校验。
- 服务端 metadata 复验只临时解密，不缓存已解密模型内容。
- 实现直传和分片上传。
- 实现 owner 修改/删除。
- 返回 limits。
- 实现 `PublicModelEvent` WebSocket 广播。
- 公模 metadata 变化后广播 `CREATED` / `UPDATED` / `DELETED` / `LOCKED` / `UNLOCKED` / `AUTH_CHANGED` 等事件。

验收：

- 玩家 token 可上传公模。
- SQLite 保存公模 metadata、标签、收藏、授权、上传会话、统计和管理员审计。
- 模型二进制和预览图保存在文件对象目录，不写入 SQLite。
- 后端不保存已解密 YSM 模型包、明文资源目录或可还原明文的 prepared cache。
- `owner_uuid` 必须由 Figura token 推导，上传者展示名来自 `owner_display_name`，模型作者必须来自 `model_metadata_raw`，两者不能混用。
- 使用 `local` 后端时模型对象写入 `<public_models.storage_path>`；使用 `oss` / `s3` 后端时对象写入 bucket/prefix，SQLite 只保存对象 key。
- 列表可搜索、过滤、排序、分页查询。
- 列表可按 `YSM` / `Figura` 一级分类查询。
- 玩家可收藏和取消收藏公模。
- 后端能从 Figura Avatar、非加密 YSM、加密 YSM 中提取或校验模型 metadata。
- 后端对加密 YSM 的 metadata 复验完成后会释放临时明文资源。
- 下载按 hash 缓存。
- 重复上传相同文件不重复存储对象。
- 公模 API 不影响现有 Avatar API。
- 多个客户端同时打开公模库时，一个客户端上传或管理员删除模型，其他客户端列表实时刷新。

### 工作包 D：客户端 HTTP API 与缓存

目标：客户端能访问公模库并管理本地缓存。

任务：

- 扩展 `HttpAPI` 或新增 `PublicModelHttpAPI`。
- 新增 DTO：
  - `PublicModelInfo`
  - `PublicModelDetails`
  - `PublicModelUploadSession`
  - `PublicModelDownloadResult`
- 新增 `PublicModelCache`。
- 新增 `PublicModelImporter`。
- 新增 `PublicModelSearchQuery`。
- 新增 `PublicModelMetadataExtractor` 客户端预填流程。
- 新增 `PublicModelFavoriteState`。
- 支持 `ETag` / `If-None-Match`。
- 接入 WebSocket `PublicModelEvent`。
- 事件触发 metadata cache 失效、列表增量更新或当前页重新拉取。

验收：

- 客户端可列出公模。
- 客户端可按关键词、标签、`YSM` / `Figura` 分类、作者、收藏状态、排序条件搜索公模。
- 客户端可收藏和取消收藏公模。
- 上传界面能从 Figura Avatar、非加密 YSM、加密 YSM 中预填名称、作者、描述等模型信息。
- 上传界面能提取可用预览动画候选：YSM 使用可用轮盘动画，Figura 使用 `.bbmodel` / Avatar 包内动画。
- 上传信息输入对话框提供预览动画下拉框，并驱动上传预览窗播放对应动画。
- 上传时若未手动提供 PNG，客户端从当前预览窗自动截取静态预览图并上传。
- 可下载并缓存模型。
- 重启游戏后缓存仍可使用。
- 网络失败时能使用已缓存对象。
- 公模列表打开期间，上传、删除、改名、锁定、授权变化能实时反映。

### 工作包 E：公模库界面与预览

目标：衣柜旁边可视化浏览、预览、下载、启用公模。

任务：

- 在 `WardrobeScreen` 增加入口按钮。
- 新增 `PublicModelLibraryScreen`。
- 新增 `PublicModelCardListWidget`，实现类似 Sparkle-Morpher 资源站界面的卡片列表/网格。
- 新增 `PublicModelCardWidget`，复用 Figura `Button` 外观、选中描边、hover 状态、收藏星标、右键 `ContextMenu` 和文本省略逻辑。
- 新增 `PublicModelFilterPanelWidget`，承载 `YSM` / `Figura` 分类、搜索、标签、收藏、授权、排序等控件。
- 新增 `PublicModelDetailPanelWidget`，承载静态预览、`EntityPreview`、metadata 和操作按钮。
- 新增 `YSM` / `Figura` 一级分类切换。
- 新增搜索框、标签过滤、收藏过滤、授权过滤、排序控件。
- 新增 `PublicModelPreviewWidget`。
- 新增 `PublicModelUploadScreen`。
- 上传界面分为“模型内原始信息”和“公模展示信息”：
  - “模型内原始信息”只读展示，来自 `model_metadata_raw`，禁止上传者修改。
  - “公模展示信息”可编辑，写入 `public_metadata`，但不得伪装成原始模型 metadata。
- 上传信息输入对话框增加预览动画下拉框。
- 接入 `EntityPreview` 临时 Avatar 预览。
- 上传预览窗支持调整视角、缩放、动画和截图帧。
- 上传时从当前预览窗自动截取静态预览 PNG。
- 增加下载到衣柜、启用并上传。
- 公模库界面接收 `PublicModelEvent` 后刷新列表、详情页和当前预览状态。

验收：

- 公模列表可搜索、过滤、分页。
- 搜索请求走后端 API，不能只过滤当前页。
- 公模列表使用卡片列表/网格布局，整体密度可参考 Sparkle-Morpher 资源站界面，但背景、按钮、描边、tooltip、字体和滚动条必须保持 Figura UI 风格。
- 卡片必须显示静态预览图、名称、上传者玩家名、模型作者、`YSM` / `Figura` 类型徽标、YSM 加密状态徽标、下载状态、收藏状态和权限状态。
- 卡片宽度随窗口自适应，桌面宽度使用 2 到 4 列，小屏降级为 1 列；卡片高度固定，长文本必须省略或 tooltip 展示，不能挤压布局。
- 公模列表按 `YSM` / `Figura` 一级分类显示；YSM 列表不再二级拆分加密/非加密。
- 收藏按钮和收藏过滤可用。
- 上传表单可自动读取模型内名称、作者、描述、license、预览候选。
- 上传表单中的模型原始 metadata 必须只读，不能让上传者修改模型原始作者、license、link、描述等字段。
- 上传者展示名默认填入当前玩家名，允许玩家修改展示文本，但不影响 `owner_uuid`。
- 如果模型作者和上传者展示名不一致，列表和详情必须同时显示两者。
- 上传表单可列出预览动画候选：YSM 来自可用轮盘动画，Figura 来自 `.bbmodel` / Avatar 包内动画。
- 上传表单选择动画后，预览窗播放该动画并允许玩家调到合适截图角度。
- 上传时自动截取当前预览窗作为静态预览图，并上传到后端 preview 对象存储。
- 模型预览可用。
- 加密 YSM 预览通过 YSMParser 临时转换/解包后本地预览。
- 未加密 YSM 预览直接从内存资源树创建 preview runtime。
- 未下载模型显示缩略图。
- 下载后可导入衣柜。
- 启用并上传后其他玩家按现有同步看到模型。
- 加密 YSM 启用但尚未上传时，未加密模型包只保留在客户端内存。
- 加密 YSM 上传到衣柜同步接口成功后，未加密模型包被立即释放。
- 模型被远端删除或锁定时，当前界面立即禁用下载/启用按钮。
- 选中其它模型或关闭公模库后，当前 preview runtime 和临时资源被释放。

### 工作包 F：模型导出

目标：补齐 Figura 侧普通 zip 导出和加密 `.ysm` 导出。

任务：

- 新增 `AvatarExportService`。
- 新增 `FiguraZipExporter`。
- 新增 `YsmZipExporter`。
- 新增 `YsmEncryptedExporter`。
- 新增 `YsmExportBundleBuilder`。
- 可以参考 Sparkle-Morpher `YSMBinarySerializer` 实现 `YsmBinaryWriter`。
- 可以参考 Sparkle-Morpher `YsmCrypt.encryptYsmFile(...)` 实现 `.ysm` 加密写出。
- `.ysm` 加密导出允许复用或移植 YSMParser 中已验证的压缩、混淆、版本识别、header/offset 处理等算法。
- 导出 `.ysm` 后用 Java 等价解密路径和 YSMParser 权威路径回读校验。
- 在 `WardrobeScreen` / 公模详情页增加导出入口。
- 对非 YSM 结构模型禁用 `.ysm` 导出。

验收：

- Figura Avatar 可导出普通 Figura zip。
- YSM Avatar 可导出普通 YSM zip。
- YSM Avatar 可导出加密 `.ysm`。
- 普通 Figura Avatar 不能导出为 `.ysm`。
- 导出的 `.ysm` 能被 Java 等价 decryptor、YSMParser、`YsmResourceIndex` 和 `YsmModelRuntime` 完整回读。
- 导出的普通 zip 能被公模上传界面识别。

### 工作包 G：管理员后端与可视化管理

目标：管理员能在 Figura 界面管理公模。

任务：

- 后端配置管理员 UUID。
- 后端配置管理员密码 hash。
- 实现 admin login。
- 实现 admin session。
- 实现删除、改名、授权、锁定、解锁。
- 实现审计日志。
- 新增 `PublicModelAdminScreen`。

验收：

- 非管理员看不到或无法打开管理入口。
- 管理员必须输入密码。
- 错误密码被限流。
- 删除、改名、授权都能在 UI 完成。
- 管理员详情可见上传者 UUID；普通玩家列表和详情不展示上传者 UUID。
- 后端审计日志记录完整。

### 工作包 H：兼容性与回归测试

目标：保证三类模型和旧功能不回归。

测试矩阵：

| 类型 | 上传 | 下载 | 预览 | 导入衣柜 | 启用上传 | 后端缓存 | 客户端缓存 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 非加密 YSM 文件夹/zip | 必须 | 必须 | 必须 | 必须 | 必须 | 必须 | 必须 |
| 加密 YSM `.ysm` | 必须 | 必须 | 必须 | 必须 | 必须 | 必须 | 必须 |
| Figura Avatar | 必须 | 必须 | 必须 | 必须 | 必须 | 必须 | 必须 |

导出测试矩阵：

| 来源 | Figura zip | YSM zip | 加密 `.ysm` | 回读校验 |
| --- | --- | --- | --- | --- |
| 普通 Figura Avatar | 必须 | 禁止 | 禁止 | Figura zip 必须可重新导入 |
| YSM native Avatar | 可选 | 必须 | 必须 | YSM zip / `.ysm` 必须可重新导入 |
| 公模库下载的 YSM | 按 license | 必须 | 按 license | 必须 |

回归点：

- 普通衣柜上传不变。
- 普通 Avatar 下载不变。
- YSM runtime 动画/controller/function/control 不变。
- 第二大阶段 YSM 同步不变。
- 第三大阶段 acceleration fallback 不变。

## 11. API 响应草案

### 11.1 列表响应

普通玩家响应只需要返回名字展示字段；管理员视图可额外返回 `owner` / `owner_uuid`。

```json
{
  "items": [
    {
      "id": "pm_01h...",
      "name": "Example Model",
      "kind": "YSM_ENCRYPTED",
      "ownerName": "UploaderName",
      "modelAuthor": "Original Model Author",
      "tags": ["ysm", "public"],
      "size": 1234567,
      "contentHash": "sha256...",
      "previewHash": "sha256...",
      "updatedAt": "2026-07-19T00:00:00Z",
      "permissions": {
        "downloadable": true,
        "manageable": false
      }
    }
  ],
  "nextCursor": "opaque"
}
```

### 11.2 详情响应

普通详情只显示 `ownerName` 与 `modelAuthor`；管理员详情在通过管理员密码会话后额外显示 `owner` / `owner_uuid`。

```json
{
  "id": "pm_01h...",
  "name": "Example Model",
  "description": "Model description",
  "kind": "YSM_ENCRYPTED",
  "license": "author-defined",
  "tags": ["ysm"],
  "ownerName": "UploaderName",
  "modelAuthor": "Original Model Author",
  "contentHash": "sha256...",
  "manifestHash": "sha256...",
  "previewHash": "sha256...",
  "size": 1234567,
  "version": 1,
  "visibility": "PUBLIC",
  "createdAt": "2026-07-19T00:00:00Z",
  "updatedAt": "2026-07-19T00:00:00Z"
}
```

管理员详情扩展：

```json
{
  "owner": "00000000-0000-0000-0000-000000000000",
  "ownerName": "UploaderName",
  "modelAuthor": "Original Model Author",
  "contentHash": "sha256...",
  "manifestHash": "sha256..."
}
```

### 11.3 上传 metadata

```json
{
  "name": "Example Model",
  "description": "Model description",
  "ownerName": "UploaderName",
  "kind": "YSM_ENCRYPTED",
  "license": "author-defined",
  "tags": ["ysm"],
  "visibility": "PUBLIC",
  "previewSource": "capture",
  "previewAnimation": {
    "id": "idle",
    "displayName": "Idle",
    "source": "YSM_WHEEL",
    "time": 0.0,
    "normalizedProgress": 0.0
  },
  "previewCamera": {
    "yaw": 35.0,
    "pitch": -10.0,
    "zoom": 1.2,
    "offsetX": 0.0,
    "offsetY": 0.0
  },
  "permissions": {
    "download": "everyone",
    "remix": "forbidden"
  }
}
```

上传 metadata 不接受客户端传入 `owner` / `owner_uuid`；上传者身份必须由 Figura token 在后端解析得到。`ownerName` 是上传者玩家名展示字段，默认填入当前玩家名，允许上传者修改，但不参与权限、审计主体或所有权判断。上传 metadata 也不接受客户端直接传入 `model_metadata_raw` 或原始模型作者字段；原始 metadata 必须由客户端提取器预览展示、后端复验生成并只读保存。名字区域只显示两项：上传者玩家名 `ownerName` 和模型作者 `modelAuthor`；如果两者不一致，列表和详情页必须同时展示两者。

## 12. 权限与限制

玩家权限：

- 上传公模。
- 修改自己上传的公模 metadata。
- 删除自己上传的公模，后端可配置是否允许。
- 下载公开或授权模型。

管理员权限：

- 修改任意公模 metadata。
- 删除/恢复任意公模。
- 锁定/解锁任意公模。
- 修改授权。
- 查看管理字段，包括上传者 UUID。

限制项：

- `maxPublicModelSize`。
- `maxPublicPreviewSize`。
- `maxPublicModelsPerUser`。
- `maxUploadSessionsPerUser`。
- `maxChunksPerUpload`。
- `publicModelUploadRateLimit`。
- `publicModelDownloadRateLimit`。
- `adminLoginRateLimit`。

## 13. 失败处理

上传失败：

- `400`：metadata 或文件类型错误。
- `401`：token 无效。
- `403`：无权限。
- `409`：hash 冲突或 session 状态不匹配。
- `413`：文件过大。
- `429`：限流。
- `500`：后端存储失败。

下载失败：

- 已有缓存则提示“使用本地缓存”。
- 无缓存则显示失败状态。
- hash 校验失败必须删除临时下载文件。

解密失败：

- 公模条目保留。
- 本地 prepared cache 标记失败原因和 parser version。
- 用户可重试。
- 管理员可锁定问题模型。

## 14. 迁移与兼容

后端迁移：

- 默认关闭公模库。
- 配置开启：

```text
public_models.enabled = true
public_models.storage_backend = local
public_models.storage_path = ...
public_models.sqlite_path = <storage_path>/public_models.sqlite3
public_models.max_model_size = ...
public_models.admins = [...]
public_models.admin_password_hash = ...
```

存储位置约定：

- 公模模型文件存放在 `<public_models.storage_path>/objects/sha256/<content_hash>`。
- 公模预览图存放在 `<public_models.storage_path>/previews/sha256/<preview_hash>.png`。
- 分片上传临时文件存放在 `<public_models.storage_path>/temp_uploads/<session_id>/`。
- SQLite 数据库存放在 `<public_models.sqlite_path>`，默认是 `<public_models.storage_path>/public_models.sqlite3`。
- SQLite 只保存 metadata、索引、关系、统计、审计和上传会话，不保存模型二进制。
- 后端不持久化解密后的 YSM 模型包、明文资源目录或 prepared cache；加密 YSM 在对象存储中保持原始加密 `.ysm`。
- 当 `public_models.storage_backend = oss | s3` 时，模型文件、预览图和上传临时对象存放在配置的 bucket/prefix 下；SQLite 仍存放在 `public_models.sqlite_path`，并记录对象 key。
- 当 `public_models.storage_backend = oss | s3` 时，客户端下载必须通过 sculptor 签发的短期 signed URL 直连 OSS/S3；sculptor 不代理模型二进制下载。

客户端兼容：

- 后端不支持公模库时，入口按钮置灰或隐藏。
- 通过 `GET /api/limits` 或新增 `GET /api/public-models/capabilities` 探测能力。
- 旧客户端不访问新端点，不受影响。

数据兼容：

- 公模对象按 hash 存储，metadata 可增量迁移。
- API response 增加字段必须可选。
- 客户端忽略未知 kind 或未知 permission 字段。
- SQLite schema 必须有 migration 版本号，后续新增字段通过 migration 升级。

## 15. 验收标准

本阶段完成标准：

1. 本地加密 `.ysm` 可以在 Figura YSM 引擎中加载、预览、装备。
2. 非加密 YSM、加密 YSM、Figura Avatar 都能上传到公模库。
3. 上传时能自动提取模型信息：Figura 和非加密 YSM 直接读取包内 metadata/json；加密 YSM 先通过 YSMParser 全版本解密，再读取 header 与明文 manifest 中的作者、名称、描述、license 等信息。
4. 上传信息输入对话框提供预览动画下拉框：YSM 来自可用轮盘动画，Figura 来自 `.bbmodel` / Avatar 包内动画；玩家可在预览窗调视角和动画帧，上传时自动截取静态预览 PNG。
5. 上传者和模型作者必须解耦：`owner_uuid` 只来自 Figura token，`owner_display_name` 默认当前玩家名且可由上传者修改展示文本，模型作者来自 `model_metadata_raw`；名字区域只显示上传者玩家名和模型作者，如果两者不一致，列表和详情必须同时显示两者。
6. 模型原始 metadata 禁止上传时修改：上传信息输入对话框只能只读展示 `model_metadata_raw`，上传请求不能直接提交或覆盖 `model_metadata_raw`，后端必须复验或保存服务端提取结果，防止冒充原作者。
7. 公模库后端独立 API 可列表、详情、上传、下载、预览。
8. 后端使用 SQLite 保存公模 metadata、标签、收藏、授权、统计、上传会话和管理员审计；模型二进制、预览图和分片临时文件保存在配置的对象存储后端。
9. 后端不缓存已解密模型内容；加密 YSM 的 metadata 复验只允许临时解密，复验完成后释放明文资源，持久化时只保留原始加密 `.ysm` 对象和 JSON metadata 快照。
10. 默认本地存储时，公模模型文件明确存放在 `<public_models.storage_path>/objects/sha256/<content_hash>`，预览图存放在 `<public_models.storage_path>/previews/sha256/<preview_hash>.png`；可选 OSS/S3 时存放在 bucket/prefix 对应 object key，并由客户端通过 sculptor 签发的短期 signed URL 直连下载。
11. 客户端衣柜旁存在公模库界面。
12. 公模库模型列表使用 Figura 风格卡片列表/网格布局，可参考 Sparkle-Morpher 资源站界面的布局密度，但不得脱离 Figura 的按钮、描边、字体、tooltip、滚动条和面板样式。
13. 公模库模型列表按 `YSM` 和 `Figura` 一级分类显示；YSM 列表不再二级拆分加密/非加密，加密状态只作为条目徽标或详情字段显示。
14. 公模库支持后端搜索：玩家可以按关键词、标签、`YSM` / `Figura` 分类、上传者、收藏状态、排序条件搜索想要的模型，并分页加载结果。
15. 公模库支持收藏和取消收藏，并支持只看已收藏模型。
16. 公模库支持排序和筛选：相关度、更新时间、创建时间、下载量、收藏量、名称、大小等排序，以及标签、上传者、授权、可下载等筛选。
17. 玩家可以从公模库下载模型到衣柜。
18. 玩家可以选择公模并通过现有衣柜上传同步给其他玩家看。
19. 公模库列表支持实时刷新：上传、删除、改名、锁定、授权变化会同步到所有正在打开公模库界面的在线客户端。
20. 玩家下载公模不会广播给所有玩家，除非后端低频刷新下载次数统计。
21. 客户端和后端都有 hash 缓存，重复下载/重复解析可命中缓存。
22. 公模库预览不照搬 Sparkle-Morpher 长期解密缓存：加密 YSM 选中后通过 YSMParser 临时转换/解包预览，未加密模型直接内存预览，切换预览或关闭界面后释放。
23. 加密 YSM 客户端启用也走 YSMParser 临时转换路径；本地启用但未上传同步时只在内存保留未加密 YSM 模型包，上传到 Figura 衣柜同步接口成功后立即释放。
24. 普通 Figura Avatar 可导出为普通 zip，但不能导出为 `.ysm`。
25. YSM native Avatar 可导出为普通 YSM zip 和加密 `.ysm`。
26. 导出的加密 `.ysm` 必须通过 Java 等价 decryptor、YSMParser、`YsmResourceIndex` 和 `YsmModelRuntime` 回读校验。
27. 管理员由后端配置指定。
28. 管理员在 Figura 可视化界面输入额外密码后，可以删除、改名、授权、锁定模型。
29. 管理员可在管理界面查看上传者 UUID；普通玩家列表和详情只显示上传者玩家名与模型作者。
30. 管理员密码会话只在当前游戏进程内有效；退出游戏、重启、切换账号或后端 token 变化后必须重新输入密码。
31. 管理操作有后端审计日志。
32. 普通 Figura Avatar 上传/装备/下载不回归。
33. 第二大阶段 YSM 多人同步不回归。
34. 第三大阶段 native/SIMD/GPU fallback 不回归。

## 16. 建议实施顺序

1. 加密 YSM 检测与解密最小闭环。
2. 加密 YSM prepared cache。
3. 后端公模库 metadata/object storage。
4. 后端 `PublicModelEvent` 实时广播。
5. 客户端 public model HTTP API、WebSocket 事件处理与缓存。
6. 公模库浏览和下载到衣柜。
7. 公模库上传。
8. 模型预览 runtime。
9. 启用并上传到个人衣柜。
10. 普通 zip 与加密 `.ysm` 导出。
11. 管理员 API。
12. 管理员可视化界面。
13. 安全、限流、审计、回归测试。

这样排序可以先保证“加密模型能加载”，再保证“公模资源能托管”，最后补齐“管理与运营安全”。
