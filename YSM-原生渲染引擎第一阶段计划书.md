# Figura 原生 YSM 渲染引擎第一阶段计划书

## 1. 背景与目标

本计划面向 Figura 当前 `26.1.2 / Fabric API 0.145.4+26.1.2` 代码线，在 Figura 内实现原生 YSM 渲染引擎。第一阶段只实现：

- 加载无加密 YSM 模型包，来源可以是文件夹或 zip。
- 支持带贴图的静态玩家模型渲染。
- 支持新版 YSM 结构与旧版 YSM 结构。
- 在 Figura 衣柜列表对应模型的右键菜单选择贴图。
- 选中 YSM 模型后使用独立 YSM 渲染管线，完全覆盖 Figura 模型渲染和原版玩家渲染管线。
- 切回 Figura 模型或取消 YSM 模型后恢复 Figura 模型与原版渲染管线。
- 手持物品不因 YSM 模型启用而错位。
- Figura Lua API 能识别并操作 YSM 模型骨架。

明确不纳入第一阶段：

- 加密 `.ysm` 解密与 JNI/native 解析。
- 完整动画系统、Molang 运行时、音频、投射物、载具、额外轮盘动作。
- YSM 在线/云端模型同步。
- 完整兼容 Sparkle-Morpher 的所有扩展层。

## 2. 当前项目现状

Figura 当前主线是将本地 Avatar 编译为 NBT，再由 `Avatar` 和 `FiguraRenderer` 渲染：

- `LocalAvatarFetcher` 只把包含 `avatar.json` 的目录识别为 Figura Avatar；zip 也按同一规则打开。
- `LocalAvatarLoader` 加载 `.lua`、`.ogg`、`.bbmodel`、`avatar.json`，并生成 `models`、`textures`、`animations` 等 NBT。
- `Avatar.render(...)` 调用 `FiguraRenderer.render()`，实际实现是 `ImmediateFiguraRenderer`，它读取 Figura/Blockbench 模型树。
- 玩家渲染已通过 `LivingEntityRenderer`、`AvatarRenderer`、`ItemInHandRenderer`、`ItemInHandLayer`、`SubmitNodeCollector` 等 mixin 接入 26.1 的 extraction/submit 渲染模型。
- Lua 侧已有 `vanilla_model` 与 `FiguraModelPart` 操作体系，但它们默认面向原版模型部件或 Figura 模型树。

仓库中已经存在一批 `org.figuramc.figura.avatar.ysm` 和 `org.figuramc.figura.parsers.ysm` 代码。目前路线是：

- 通过 `YsmAvatarDetector` 检测 `ysm.json`。
- 通过 `YsmModelConverter` 把 YSM 几何转换成临时 `.bbmodel`。
- 通过 `YsmGeneratedScriptBuilder` 生成自动 Lua。
- 最终仍走 Figura/Blockbench 渲染器。

这条路线适合作为导入兼容或调试工具，但不满足“YSM 独立渲染管线”和“完全覆盖 Figura/原版渲染管线”的要求。第一阶段应将这些代码降级为参考和过渡工具，新增原生 YSM 数据模型与渲染器。

## 3. 参考项目结论

### 3.1 Sparkle-Morpher

Sparkle-Morpher 值得参考的部分：

- `RendererManager` 统一管理玩家渲染器、第一人称手部渲染器、投射物渲染器、载具渲染器；第一阶段可只实现玩家与手部两个分支。
- Fabric 26.x 下通过 `LivingEntityRenderer.submit(...)` HEAD 注入，在玩家 `AvatarRenderState` 上判断是否使用 YSM，成功渲染后取消原版提交。
- 第一人称手臂通过 `AvatarRenderer.renderRightHand/renderLeftHand` HEAD 注入，成功渲染自定义手臂后取消原版手臂。
- `GeoModel` 会把左右手、头、鞘、背包等骨骼组预先解析为索引。第一阶段至少需要左右手骨骼组与通用骨骼名索引，用来解决手持物品挂点。
- `HandItemRenderer` 的关键思想是：基于 YSM 手部骨骼组渲染第一人称手部，并用稳定的基准变换进入 Minecraft 的 `SubmitNodeCollector`。

不建议直接搬运的部分：

- Sparkle-Morpher 有完整能力系统、动画系统、兼容层与 native 加速，超出第一阶段。
- 它的模型运行时围绕 `PlayerCapability`、`ModelAssembly`、`AnimatedGeoModel` 设计，直接接入 Figura 会和 `AvatarManager/UserData/Avatar` 生命周期重叠。

### 3.2 refs/YSM_NEW_Model

新版结构以 `ysm.json` 为入口：

- `spec: 2`
- 元数据在 `metadata`。
- 玩家资源在 `files.player`。
- 主模型路径通常是 `files.player.model.main`，手臂模型可能是 `files.player.model.arm`。
- 贴图列表在 `files.player.texture`，可为字符串数组或对象数组。
- 动画、投射物、载具、语言、头像、GUI 背景等也在包内，但第一阶段只读取玩家模型与贴图。

### 3.3 refs/YSM_OLD_Model

旧版结构没有 `ysm.json`：

- 根目录直接包含 `main.json`、`arm.json`、`main.animation.json`、`extra.animation.json` 等文件。
- 贴图通常是根目录下的 `.png`。
- 元数据可能内嵌于 `main.json` 的 `description.ysm_extra_info`。
- 模型几何仍是 Bedrock `format_version: 1.12.0` 和 `minecraft:geometry`。

旧版第一阶段识别策略：

- 根目录有 `main.json` 且其 JSON 含 `minecraft:geometry`。
- 贴图候选为根目录 `.png`，优先排除明显 GUI/图标文件。
- `arm.json` 可作为第一人称手臂模型输入；若缺失则从主模型左右手骨骼渲染。

### 3.4 refs/YSMParser

YSMParser 支持加密 `.ysm` 的多版本解析与恢复项目文件，但第一阶段不依赖它。只预留后续接口：

- `YsmPackageSource` 后续可以增加 `.ysm` 文件输入。
- `YsmPackageExtractor` 后续可以接 JNI/native 或外部解析器。
- 当前只接受已经解包的无加密目录或 zip。

## 4. 总体架构

建议新增包：

- `org.figuramc.figura.avatar.ysm`
- `org.figuramc.figura.model.ysm`
- `org.figuramc.figura.model.ysm.rendering`
- `org.figuramc.figura.lua.api.ysm_model`

核心对象：

- `YsmPackage`: 表示一个 YSM 模型包，屏蔽文件夹与 zip 文件系统差异。
- `YsmPackageDetector`: 识别新版 `ysm.json`、旧版 `main.json`、未来 `.ysm`。
- `YsmManifest`: 统一后的模型元数据、模型路径、手臂模型路径、贴图列表。
- `YsmTextureOption`: 继续使用或扩展现有记录，保存 id、显示名、相对路径、透明性、尺寸。
- `YsmGeometry`: Bedrock geometry 的原生结构，包含 description、bones、cubes、uv、pivot、rotation。
- `YsmBone`: 运行时骨骼节点，包含父子关系、默认变换、当前 Lua 覆写变换、可见性。
- `YsmMesh`: 从 cube/face 编译出的静态网格，可按贴图和透明性拆分。
- `YsmModelRuntime`: 某个 Avatar 当前绑定的 YSM 模型状态，包含骨架、网格、贴图选择、左右手骨骼索引。
- `YsmRenderer`: 第三人称玩家 YSM 渲染器。
- `YsmHandRenderer`: 第一人称左右手与手持物品挂点渲染器。
- `YsmRenderDispatcher`: 根据 Avatar/玩家状态决定走 YSM 或回退 Figura/原版。
- `YsmModelAPI`: Lua 侧骨架访问入口。
- `YsmModelPart`: Lua 可操作的 YSM 骨骼包装，接口尽量贴近 `FiguraModelPart`。

生命周期：

1. 衣柜扫描发现 Figura Avatar 或 YSM 包。
2. 用户选择 YSM 条目时，`AvatarManager.loadLocalAvatar(path)` 进入 YSM 分支。
3. YSM 分支构造一个特殊 Avatar：保留 Figura 的权限、Lua runtime、metadata，但不生成 Figura `models` 树。
4. Avatar 持有 `YsmModelRuntime`，`renderer` 可以为空或为轻量占位，实际渲染由 `YsmRenderDispatcher` 分流。
5. 渲染 mixin 发现当前玩家 Avatar 是 YSM 时，调用 `YsmRenderer`，成功后取消原版/Figura 提交。
6. 切回 Figura Avatar 时释放 `YsmModelRuntime` 的 GPU 资源与 zip 文件系统，恢复原有 `Avatar.render(...)` 路线。

## 5. 加载与衣柜集成

### 5.1 Avatar 扫描

修改 `LocalAvatarFetcher`：

- `isAvatar(path)` 保持 Figura 原逻辑。
- 新增 `YsmPackageDetector.isYsmPackage(path)`。
- `FolderPath.fetch()` 中对目录和 zip 同时检查 Figura 与 YSM。
- `AvatarPath` 增加 `AvatarKind`：`FIGURA`、`YSM_NEW`、`YSM_OLD`。
- 对 YSM 条目读取 `YsmManifest` 来显示名称、描述、图标或默认图标。

zip 支持：

- 对 zip 用 `FileSystems.newFileSystem(path)` 打开。
- 如果 zip 根目录是 `ysm.json` 或 `main.json`，直接作为 YSM 包。
- 如果 zip 内第一层目录才是模型包，递归识别。
- 注意缓存并关闭不再使用的 zip FileSystem，避免 Windows 下文件被占用。

### 5.2 加载分支

修改 `LocalAvatarLoader.loadAvatar`：

- 规范化 path 后，先判断 YSM。
- 如果是 YSM，调用 `YsmAvatarLoader.loadNativeAvatar(path, target)`。
- 如果不是 YSM，保留现有 Figura 加载流程。

`YsmAvatarLoader.loadNativeAvatar` 不再生成 `.bbmodel`：

- 读取 `YsmManifest`。
- 读取主模型 geometry 与可选 arm geometry。
- 按当前贴图选择加载 PNG 为 Figura/Minecraft 可用的动态纹理。
- 构造 metadata NBT，至少写入 `format = "ysm-native"`、`ysm_kind`、`ysm_texture`、`uuid`。
- 初始化 `target.loadAvatar(nbt)` 后，将 `YsmModelRuntime` 附加到 Avatar。

建议给 `Avatar` 增加：

- `private YsmModelRuntime ysmRuntime;`
- `public boolean isYsmNative();`
- `public YsmModelRuntime getYsmRuntime();`
- `public void setYsmRuntime(YsmModelRuntime runtime);`
- 在 `clean()` 或 Avatar 卸载时释放 YSM runtime。

### 5.3 右键贴图菜单

修改 `AbstractAvatarWidget` 或 `AvatarWidget`：

- 鼠标右键点击 YSM 条目时创建 `ContextMenu`。
- 菜单项为 `YsmManifest.textures()`。
- 当前贴图显示勾选或高亮。
- 点击贴图后调用 `YsmTextureSelectionStore.set(path, textureId)`。
- 如果该模型是当前选中模型，立即重新加载当前 Avatar。

贴图选择存储：

- 现有 `YsmTextureSelectionStore` 可继续使用。
- key 建议保留绝对规范路径；zip 内子路径需追加 `!/<inner>`，否则同一 zip 内多个 YSM 包会冲突。

## 6. 原生模型解析

### 6.1 新版解析

`YsmManifestReader.readNew(path)`：

- 读取 `ysm.json`。
- `name`: `metadata.name`，缺失时用文件夹名。
- `description`: `metadata.tips` 或 `metadata.description`。
- `authors`: `metadata.authors`。
- `mainModel`: `files.player.model.main`。
- `armModel`: `files.player.model.arm`。
- `textures`: `files.player.texture`，字符串路径时 id 取文件名无扩展，对象时读取 id/name/path。
- `defaultTexture`: `properties.default_texture`，用于默认选中。

### 6.2 旧版解析

`YsmManifestReader.readOld(path)`：

- 主模型固定为 `main.json`。
- 手臂模型优先 `arm.json`。
- metadata 从 `main.json -> minecraft:geometry[0] -> description -> ysm_extra_info` 读取。
- 贴图从根目录 `.png` 枚举，优先 `*.png`，排除 `avatar`、`gui`、`background`、`foreground` 等。
- 默认贴图优先 `default.png`，否则第一张。

### 6.3 Geometry 解析

实现 `YsmGeometryParser`：

- 支持 `format_version: 1.12.0` 风格。
- 读取 `description.texture_width/texture_height/visible_bounds_*`。
- 读取 `bones[]`：`name`、`parent`、`pivot`、`rotation`、`mirror`、`inflate`、`cubes[]`。
- 读取 cube：`origin`、`size`、`pivot`、`rotation`、`uv`、`inflate`、`mirror`。
- UV 同时支持对象形式与数组形式。
- 不在第一阶段求解动画/Molang，只保留默认静态变换。

坐标约定：

- Bedrock/YSM 坐标需要转换到 Minecraft/Figura 的渲染坐标。现有 `YsmModelConverter` 中 `x` 翻转、pivot/rotation 转换可作为校验参考，但原生渲染器应统一在 `YsmTransform` 中处理，避免解析层和渲染层重复翻转。
- 第一阶段必须用 `refs/YSM_NEW_Model/models/main.json` 和 `refs/YSM_OLD_Model/main.json` 做视觉回归。

## 7. 渲染管线

### 7.1 第三人称玩家替换

新增 `YsmPlayerRendererMixin`，目标为 Fabric 26.1 的 `LivingEntityRenderer.submit(...)`：

- 在 HEAD 注入。
- 仅处理 `AvatarRenderState`。
- 通过 `Minecraft.level.getEntity(avatarState.id)` 拿到玩家。
- 通过 `AvatarManager.getAvatarForPlayer(player.getUUID())` 判断是否存在 YSM runtime。
- 若是 YSM：
  - 捕获 yaw、partialTick、packedLight、poseStack、collector。
  - 调用 `YsmRenderDispatcher.renderPlayer(...)`。
  - 成功后 `ci.cancel()`，从而覆盖原版玩家模型、原版层、Figura 模型提交。
- 若不是 YSM，不做任何事，保留现有 Figura/原版逻辑。

注意：现有 Figura 已有 `PlayerRendererMixin` 对 `AvatarRenderer`、`LivingEntityRenderer` 的多处注入。新增 mixin 要做单一职责的 HEAD 分流，不要破坏 nameplate、Figura callbacks、SubmitNode 扩展。

### 7.2 Figura 管线覆盖与恢复

YSM 启用时必须满足：

- 不调用 `Avatar.render(..., EntityModel, PartFilterScheme, ...)` 的 Figura 模型渲染分支。
- 不提交原版 `PlayerModel`。
- 不提交 Figura 模型节点。
- 保留 nameplate、基础实体状态、必要的光照和透明排序。

恢复规则：

- `Avatar.isYsmNative()` 为 false 时，所有新增 YSM mixin 直接 return。
- 切回 Figura Avatar 后，`YsmModelRuntime.close()` 释放纹理/mesh 缓存。
- `LocalAvatarLoader` 回到现有 Figura 加载逻辑，不持有旧 YSM runtime。

### 7.3 网格提交

`YsmRenderer` 应使用 26.1 的提交式渲染：

- 优先使用 `SubmitNodeCollector.submitCustomGeometry(...)` 提交自定义几何。
- 按 RenderType 分批：不透明/裁剪用 entity cutout，透明贴图用 translucent。
- 静态模型可以在加载时把每个 cube face 编译为顶点数组；渲染时只应用骨骼矩阵。
- 第一阶段可以 CPU 计算最终矩阵，不引入 Sparkle 的 native/GPU skinning。

### 7.4 第一人称手臂与手持物品

新增 `YsmFirstPersonHandMixin`，目标为 `AvatarRenderer.renderRightHand` 和 `AvatarRenderer.renderLeftHand`：

- HEAD 注入，和 Sparkle-Morpher 的 Fabric 26.x 写法一致。
- 当前玩家有 YSM runtime 且对应手臂骨骼存在时：
  - 调用 `YsmHandRenderer.renderArm(...)`。
  - 成功后取消原版手臂。

手持物品不偏移的实现要点：

- `YsmModelRuntime` 建立左右手骨骼组：
  - 新版优先读 arm model。
  - 旧版优先从 `LeftArm`、`RightArm`、`leftArm`、`rightArm`、`LArm`、`RArm` 等常见骨骼名匹配。
  - 如果模型声明了 Sparkle/GeoModel 风格的左右手组，优先使用声明。
- 渲染第三人称手持物品时，替换或增强现有 `ItemInHandLayerMixin`：
  - YSM 启用时不要让原版 `ArmedModel.translateToHand` 决定矩阵。
  - 使用 YSM 左右手骨骼世界矩阵作为 item pivot。
  - 再应用 Minecraft 手持物品默认偏移，使物品在视觉上与原版手部握持一致。
- 第一人称时参考 Sparkle `HandItemRenderer` 的基准：
  - 左手约 `translate(0.25, 1.8, 0)`。
  - 右手约 `translate(-0.25, 1.8, 0)`。
  - `scale(-1, -1, 1)`。
  - 具体数值需要在 Figura 预览与游戏内同时校准。

验收标准是：剑、镐、盾、弓等常见物品在左右手第三人称和第一人称都不会明显漂移、倒置或穿出手掌。

## 8. Lua API 支持

第一阶段提供 `ysm_model` API，同时尽量复用 `FiguraModelPart` 的命名习惯。

新增：

- `runtime.ysm_model` 或全局 `ysm_model`。
- `ysm_model.ALL`
- `ysm_model:getPart(name)` / `ysm_model[name]`
- `YsmModelPart:getName()`
- `YsmModelPart:getParent()`
- `YsmModelPart:getChildren()`
- `YsmModelPart:getPos()/setPos()/pos()`
- `YsmModelPart:getRot()/setRot()/rot()`
- `YsmModelPart:getScale()/setScale()/scale()`
- `YsmModelPart:getVisible()/setVisible()/visible()`
- `YsmModelPart:getOriginPos()/getOriginRot()/getOriginScale()`
- `YsmModelPart:getWorldMatrix()`，用于高级脚本读取骨骼矩阵。

兼容策略：

- 对 Figura 模型，`ysm_model` 为空或返回只读空表。
- 对 YSM 模型，现有 `models` API 可以保持为空，避免脚本误以为 Figura 模型树存在。
- `vanilla_model` 仍可存在，但 YSM 启用时它不应驱动实际玩家模型；可以保留用于脚本读取原版姿态或兼容旧脚本。
- 权限上复用 `VANILLA_MODEL_EDIT` 或新增 `YSM_MODEL_EDIT` 需要单独决策。第一阶段建议复用现有模型编辑权限，减少权限 UI 改动。

## 9. Mixin 清单

基于 Fabric 26.1.2 和当前项目混入结构，第一阶段预计需要：

- `render.renderers.YsmLivingEntityRendererMixin`
  - 目标：`LivingEntityRenderer.submit(LivingEntityRenderState, PoseStack, SubmitNodeCollector, CameraRenderState)`
  - 作用：YSM 玩家第三人称替换渲染，成功后 cancel。
- `render.renderers.YsmAvatarRendererHandMixin`
  - 目标：`AvatarRenderer.renderRightHand(...)` / `renderLeftHand(...)`
  - 作用：第一人称手臂替换，成功后 cancel。
- `render.layers.items.YsmItemInHandLayerMixin`
  - 目标：`ItemInHandLayer` 或 `PlayerItemInHandLayer` 中提交物品前的矩阵构造点。
  - 作用：YSM 启用时用 YSM 手骨矩阵替代原版 `ArmedModel` 手部矩阵。
- 可选 `gui` mixin 不建议第一阶段使用；衣柜右键菜单应优先直接改 Figura GUI 组件。

Fabric 文档与 mcmodding 查询确认：26.1 文档强调渲染 extraction/submit 分离；当前项目与 Sparkle-Morpher 都已经围绕 `SubmitNodeCollector` 工作，因此新增 mixin 应顺着 submit 阶段接入。

## 10. 实施阶段

### 阶段 A：基础识别与数据模型

1. 新增 `YsmPackageDetector`、`YsmManifestReader`、`YsmPackage`。
2. 支持文件夹和 zip 中的新/旧结构识别。
3. 扩展 `LocalAvatarFetcher`，衣柜能显示 YSM 条目。
4. 读取并缓存贴图候选。
5. 加载时能构造 `YsmModelRuntime`，但先不替换渲染。

验收：

- `refs/YSM_NEW_Model` 和 `refs/YSM_OLD_Model` 都能出现在衣柜列表。
- zip 打包后的两类模型也能出现。
- 衣柜显示名称不依赖 `avatar.json`。

### 阶段 B：静态几何解析与预览渲染

1. 实现 `YsmGeometryParser`。
2. 实现骨骼树、cube face、UV 编译。
3. 实现基础 `YsmRenderer`，先只渲染主模型。
4. 在 `LivingEntityRenderer.submit` 中 YSM 分支渲染并 cancel。

验收：

- 新/旧示例模型可以在第三人称显示。
- 切换回 Figura 模型后 Figura 渲染恢复。
- 取消模型后原版玩家渲染恢复。

### 阶段 C：贴图菜单与热切换

1. 在 YSM 衣柜条目右键菜单列出贴图。
2. 选择贴图写入 `YsmTextureSelectionStore`。
3. 当前模型热重载。
4. 渲染器释放旧纹理并绑定新纹理。

验收：

- `refs/YSM_NEW_Model` 可在 `default.png` 和 `blue.png` 间切换。
- 旧版模型可在根目录多个 PNG 间切换。
- 重启客户端后贴图选择保持。

### 阶段 D：手部与物品挂点

1. 解析 `arm.json` 或主模型左右手骨骼。
2. 实现 `YsmHandRenderer`。
3. 第三人称物品层使用 YSM 手骨矩阵。
4. 第一人称左右手替换原版手臂。

验收：

- 左右手空手渲染正常。
- 常见手持物品第三人称不偏移。
- 第一人称持剑/镐/盾不明显错位。

### 阶段 E：Lua 骨架 API

1. 新增 `YsmModelAPI` 和 `YsmModelPart`。
2. 注册到 `FiguraLuaRuntime`。
3. Lua 修改骨骼 pos/rot/scale/visible 后影响 YSM 渲染矩阵。
4. 加文档注解或最少 API 说明。

验收 Lua：

```lua
events.render:register(function(delta)
  local head = ysm_model.Head or ysm_model.head
  if head then
    head:setRot(0, world.getTime() % 360, 0)
  end
end)
```

该脚本能让 YSM 对应骨骼旋转；切回 Figura 模型后不报错。

## 11. 测试与验证

必须测试：

- `refs/YSM_NEW_Model` 文件夹加载。
- `refs/YSM_NEW_Model` zip 加载。
- `refs/YSM_OLD_Model` 文件夹加载。
- `refs/YSM_OLD_Model` zip 加载。
- Figura 普通 Avatar 加载、切换、重载不回归。
- 无模型/取消选择恢复原版玩家。
- 贴图切换和重启持久化。
- 第一人称左右手。
- 第三人称主手/副手物品。
- Lua API 修改骨骼。

建议增加的自动化测试：

- `YsmManifestReaderTest`: 新旧 manifest 读取。
- `YsmGeometryParserTest`: 骨骼数量、父子关系、贴图尺寸、cube 数。
- `YsmTextureSelectionStoreTest`: path key 与 zip inner path。
- `YsmPackageDetectorTest`: 文件夹/zip/非 YSM 排除。

手动视觉测试：

- 在衣柜预览中切换 YSM/Figura/无模型。
- 进入单人世界第三人称前后视角查看模型。
- 第一人称切换主副手物品。
- 使用 F5、潜行、骑乘、装备盔甲时确认第一阶段未支持的层不会破坏基础玩家渲染；盔甲层第一阶段可回退不显示或后续处理，但不能把原版玩家身体又渲染出来。

## 12. 风险与处理

- 坐标系风险：YSM/Bedrock 与 Minecraft/Figura 坐标不同。处理方式是集中在 `YsmTransform` 中做转换，并用现有 `YsmModelConverter` 结果作为对照。
- 透明排序风险：静态模型按贴图分批可能在透明面上排序不完美。第一阶段允许粗粒度 translucent 分批，后续再做骨骼/面级排序。
- zip FileSystem 泄漏：Windows 下会锁文件。`YsmPackage` 必须实现 `AutoCloseable`，Avatar 卸载时关闭。
- Mixin 冲突风险：Figura 已有大量 render mixin。YSM 新 mixin 要只在 `isYsmNative()` 时取消，避免影响 Figura 正常路径。
- Lua API 语义风险：`models` 与 `ysm_model` 双入口可能混淆。第一阶段建议新增 `ysm_model`，不伪装为 Figura `models`。
- 旧版贴图选择风险：旧版没有 manifest 标准。用根目录 PNG 枚举，并允许用户右键手动选择。
- 手持物品挂点风险：不同模型骨骼命名不一致。第一阶段实现名称启发式和手动 fallback，后续再引入配置/声明。

## 13. 第一阶段完成定义

第一阶段完成时应满足：

- 不再通过 `.bbmodel` 转换路线渲染 YSM。
- YSM 模型拥有独立 `YsmModelRuntime`、`YsmRenderer`、`YsmHandRenderer`。
- 新版和旧版 YSM 示例均可从文件夹和 zip 加载。
- 衣柜右键可选择贴图，选择可持久化。
- YSM 启用时原版玩家模型和 Figura 模型不会同时出现。
- 切回 Figura 后现有 Figura Avatar 行为恢复。
- 第一人称和第三人称手持物品基础位置正确。
- Lua 可以通过 `ysm_model` 找到并修改骨骼。

