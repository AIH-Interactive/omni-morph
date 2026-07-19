# Figura 原生 YSM 渲染引擎第二阶段计划书

## 1. 阶段定位

第一阶段已经完成 YSM 静态模型的基础闭环：

- 新版 `ysm.json` 与旧版 `main.json` 模型包识别。
- 文件夹或 zip 形式的无加密模型加载。
- 衣柜列表展示 YSM 模型，并支持右键选择贴图。
- 选中 YSM 后使用独立渲染管线，覆盖 Figura 模型与原版玩家模型；切回 Figura 后恢复原管线。
- 支持静态 Bedrock 几何、基础贴图渲染、基础骨架暴露给 Lua。
- 第三人称手持物品已经能挂到 YSM 手部 Locator 或手部骨骼上。

第二阶段的目标不是继续为单个模型补丁式修复，而是把 YSM 的运行时语义补到可持续扩展的程度：动画、Locator、PartMask、第一人称、Lua 世界矩阵、测试体系和调试工具都要开始成体系落地。

## 2. 第二阶段目标

第二阶段建议定义为“动态与交互兼容阶段”，核心目标如下：

1. 建立 YSM 动画运行时，支持静态模型之外的基础 idle/walk/sneak/fly/sit 等玩家动作。
2. 引入 Sparkle-Morpher/YSM 风格的 Locator 与 PartMask 体系，替代当前 `Background*`、`gui` 的临时隐藏规则。
3. 完善手持物品、背部挂点、刀鞘、额外挂点等特殊部件的渲染入口。
4. 实现第一人称 YSM 手臂模型和第一人称手持物品基础对齐。
5. 让 Figura Lua API 能读取真实 YSM 骨骼世界矩阵，并更接近 `FiguraModelPart` 的操作体验。
6. 建立批量模型加载与渲染回归测试工具，降低后续模型兼容靠截图排查的成本。

明确不建议纳入第二阶段：

- 加密 `.ysm` 包的完整解密链路。
- 网络同步、云端模型、账号体系。
- 完整 YSM 能力系统、技能系统、弹幕/投射物逻辑。
- 所有复杂 MoLang 表达式的 100% 兼容。
- 所有服务端同步动画或多人可见行为。

这些内容可以作为第三阶段或长期兼容阶段。

## 3. 当前实现基线

当前主要实现分布：

- `common/src/main/java/org/figuramc/figura/avatar/ysm/`
  - YSM 包识别、Manifest 读取、贴图选择、Avatar NBT 构建。
- `common/src/main/java/org/figuramc/figura/model/ysm/`
  - Bedrock geometry 解析、YSM runtime、渲染器、Lua 可见骨骼包装。
- `common/src/main/java/org/figuramc/figura/lua/api/ysm_model/`
  - `ysm_model:getPart(name)`、`ysm_model:getParts()` 等基础 API。
- `common/src/main/java/org/figuramc/figura/mixin/render/renderers/LivingEntityRendererMixin.java`
  - YSM 第三人称独立渲染管线和手持物品提交入口。

当前保留的技术债：

- 动画文件尚未进入运行时。
- `Background*`、`gui` 过滤仍是保守规则，不是完整 PartMask。
- `YsmModelPart.getWorldMatrix()` 仍未返回真实世界矩阵。
- 手持物品只有基础手部/Locator 挂载，缺少更多 YSM 特殊 item transform。
- 第一人称手臂仍未完整实现。
- 缺少自动化模型兼容测试。

## 4. 参考实现结论

第二阶段仍以 `refs/Sparkle-Morpher` 为首要参考，但不建议直接搬运它的整体架构。

优先参考：

- `YSMClientMapper`
  - 骨骼名称映射。
  - Locator 列表。
  - PartMask 规则。
  - 特殊槽位如 `BladeLocator`、`SheathLocator`、`BackpackLocator`、`ElytraLocator`。
- `ModelRendererBridge`
  - 骨骼矩阵顺序。
  - 骨骼运行时变换叠加逻辑。
- `RenderUtils.prepMatrixForLocator`
  - Locator 最后一段骨骼的矩阵处理。
- `CustomPlayerItemInHandLayer`
  - 手持物品默认变换。
  - 不同手、不同物品类型的入口拆分。
- YSM 动画相关加载与 controller 绑定逻辑
  - 只抽取数据模型和播放器语义，不直接复制 Sparkle-Morpher 的能力系统。

不建议直接搬运：

- `PlayerCapability` 生命周期。
- Sparkle-Morpher 的完整实体替换管理器。
- 绑定其自定义能力、技能、网络包的渲染分发层。

Figura 侧应继续保持以 `Avatar/UserData/LuaRuntime` 为生命周期中心。

## 5. 工作包一：YSM 资源模型补全

目标：让加载器不只读取主模型和当前贴图，而是建立完整但惰性加载的 YSM 包资源索引。

计划新增或扩展：

- `YsmPackage`
  - 统一文件夹、zip、未来 `.ysm` 解包目录。
  - 负责相对路径解析、资源存在性检测、字节读取。
- `YsmResourceIndex`
  - 记录 main model、arm model、animation、extra animation、texture、sound、icon、background、metadata。
- `YsmManifestReader`
  - 扩展新版 `ysm.json` 的 `files.player.animation`、`files.player.model.arm` 等字段。
  - 扩展旧版根目录 `main.animation.json`、`extra.animation.json`、`arm.json` 检测。
- `YsmAvatarLoader`
  - NBT 内写入资源索引的必要字段。
  - 大资源仍按需读入，避免一次性塞入过多未来阶段不用的数据。

验收标准：

- 新旧模型都能列出主模型、手臂模型、贴图、动画文件候选。
- zip 与文件夹表现一致。
- 缺少动画或 arm 模型时不影响第一阶段静态渲染。

## 6. 工作包二：PartMask 与 Locator 体系

目标：用 YSM 语义化槽位替换当前硬编码隐藏规则，避免大型背景、GUI、特殊装备误渲染，也让后续特殊挂点有统一入口。

建议新增：

- `YsmBoneRole`
  - `BODY`
  - `LEFT_HAND`
  - `RIGHT_HAND`
  - `HEAD`
  - `HELMET`
  - `BACKPACK`
  - `ELYTRA`
  - `BLADE`
  - `SHEATH`
  - `BACKGROUND`
  - `GUI`
  - `FIRST_PERSON_ARM`
  - `UNKNOWN`
- `YsmBoneMapper`
  - 按 Sparkle-Morpher 的名称表识别骨骼角色。
  - 大小写不敏感。
  - 支持别名和旧版模型命名差异。
- `YsmLocator`
  - 保存 Locator 骨骼、角色、左右手、默认物品变换类型。
- `YsmPartMask`
  - 控制当前渲染 pass 中哪些角色可见。

渲染 pass 建议拆分：

- `PLAYER_BODY`
  - 渲染身体、头发、衣服等正常第三人称模型。
  - 默认排除 `BACKGROUND`、`GUI`、纯特殊槽位。
- `HELD_ITEM`
  - 只用于手持物品矩阵准备，不直接渲染角色 mesh。
- `FIRST_PERSON_ARM`
  - 第一人称手臂。
- `WARDROBE_PREVIEW`
  - 衣柜预览，可选择显示或隐藏背景类部件。
- `DEBUG_ALL`
  - 调试用，显示所有骨骼和 Locator。

验收标准：

- `refs/01_taisho_maid` 这类带巨大背景/展示部件的模型，在玩家身体 pass 不显示背景或 GUI。
- 普通身体部件不被误隐藏。
- 手部 Locator、刀鞘、背包等能被索引出来并供后续 pass 使用。

## 7. 工作包三：动画运行时第一版

目标：支持 YSM 常见玩家动作的基础动画播放，使模型不再只有静态姿态。

建议范围：

- 读取 Bedrock animation JSON。
- 支持骨骼的 `position`、`rotation`、`scale` 三类通道。
- 支持常量 keyframe、时间 keyframe、线性插值。
- 支持基础 MoLang 变量的子集。
- 支持动画叠加优先级和权重。

第一版动画状态：

- `idle`
- `walk`
- `run`
- `sneak`
- `jump`
- `fall`
- `swim`
- `fly`
- `sit`
- `sleep`
- `hurt`

建议新增：

- `YsmAnimationClip`
- `YsmAnimationChannel`
- `YsmKeyframe`
- `YsmAnimationController`
- `YsmAnimationState`
- `YsmMolangContext`
- `YsmAnimationPlayer`

与 Figura 的关系：

- 动画每帧由 YSM runtime 根据 `LivingEntityRenderState` 或实体状态更新。
- Lua 对骨骼的修改应作为动画之后的覆盖层，或者提供明确优先级：
  1. 默认模型 pose。
  2. YSM 动画 pose。
  3. Figura Lua pose override。
  4. 渲染 pass 特殊变换。

验收标准：

- 有动画的 YSM 模型能播放 idle 和 walk。
- 没有动画的模型继续保持第一阶段静态渲染。
- Lua 修改骨骼不会被动画无条件覆盖。
- 动画错误只降级当前动画，不导致 Avatar 加载失败。

## 8. 工作包四：MoLang 子集

目标：为动画系统提供够用的表达式支持，而不是一开始追求完整引擎。

第一版建议支持：

- 数字、布尔、字符串基础字面量。
- 四则运算、比较、逻辑运算。
- `math.sin`、`math.cos`、`math.abs`、`math.clamp`、`math.min`、`math.max`。
- 常见 query：
  - `query.anim_time`
  - `query.life_time`
  - `query.modified_distance_moved`
  - `query.ground_speed`
  - `query.is_on_ground`
  - `query.is_sneaking`
  - `query.is_swimming`
  - `query.is_sleeping`
  - `query.is_using_item`
- 变量读写：
  - `variable.*`
  - `temp.*`

建议复用项目内已有 molang 包：

- `common/src/main/java/org/figuramc/figura/molang/`

但需要单独封装 YSM 的上下文对象，避免把 YSM query 污染到 Figura 现有 Molang 语义里。

验收标准：

- 动画文件中常见的三角函数、时间变量、移动速度变量可运行。
- 不支持的 query 有明确默认值和 debug 日志。
- 表达式异常不会中断整个渲染。

## 9. 工作包五：第一人称手臂与物品

目标：选中 YSM 模型后，第一人称视角也不再使用原版手臂模型。

实现建议：

- 读取 `arm.json`；缺失时从主模型的左右手骨骼裁剪渲染。
- 新增第一人称渲染入口，拦截 Figura/原版手臂提交。
- 建立 `YsmFirstPersonRenderer`：
  - 渲染左手。
  - 渲染右手。
  - 渲染第一人称手持物品。
- 第一人称 item transform 继续参考 Sparkle-Morpher，但要适配 Minecraft 26.1 submit/extraction 管线。

需要重点验证：

- 空手。
- 普通方块。
- 普通物品。
- 食物/使用中物品。
- 弓、弩、三叉戟、盾牌。
- 双持。

验收标准：

- 第一人称看不到原版 Steve/Alex 手臂。
- YSM 手臂贴图、位置和物品基本对齐。
- 切回 Figura 模型后原第一人称渲染恢复。

## 10. 工作包六：特殊挂点与装备层

目标：让 YSM 常见特殊部件不再被当成身体 mesh 直接渲染，而是进入正确的渲染层。

优先级：

1. 手持物品左右手。
2. `BackpackLocator`。
3. `BladeLocator`。
4. `SheathLocator`。
5. `ElytraLocator`。
6. 头部/帽子/头饰。
7. 未来扩展投射物或能力特效。

建议新增：

- `YsmAttachmentRenderer`
- `YsmAttachmentPoint`
- `YsmAttachmentType`
- `YsmEquipmentContext`

验收标准：

- 带背包、刀鞘、巨大武器展示部件的模型不会在身体 pass 炸开。
- 特殊挂点可以通过 debug overlay 或日志列出。
- 至少手持物品和背部挂点能进入正确矩阵。

## 11. 工作包七：Lua API 第二版

目标：让 Figura 脚本能更可靠地识别和控制 YSM 骨架。

建议扩展 `ysm_model`：

- `ysm_model:getPart(name)`
- `ysm_model:getParts()`
- `ysm_model:getRootParts()`
- `ysm_model:getLocator(name)`
- `ysm_model:getLocators()`
- `ysm_model:getTexture()`
- `ysm_model:setTexture(id)`
- `ysm_model:isYsm()`
- `ysm_model:getKind()`

建议扩展 `YsmModelPart`：

- `getName()`
- `getParent()`
- `getChildren()`
- `getPivot()`
- `getOriginRot()`
- `getAnimRot()`
- `getWorldMatrix()`
- `getWorldPos()`
- `part:setVisible(boolean)`
- `part:setPos(x, y, z)`
- `part:setRot(x, y, z)`
- `part:setScale(x, y, z)`
- `part:resetPose()`

优先解决：

- `getWorldMatrix()` 必须返回当前帧真实骨骼世界矩阵。
- Lua 覆盖层和动画层的优先级必须明确。
- 大小写别名不能导致 `getParts()` 重复返回。

验收标准：

- Lua 能读取并移动任意 YSM 骨骼。
- Lua 能拿到手部 Locator 的世界位置。
- Lua 设置可见性后，渲染 pass 正确响应。

## 12. 工作包八：测试与调试工具

目标：把“模型是否炸”从人工截图测试逐步转为批量检查。

建议新增测试工具：

- YSM 包扫描器：
  - 扫描 `refs/` 下所有新旧模型。
  - 输出模型种类、主模型、贴图、动画、骨骼数、cube 数、Locator 数。
- Geometry 解析测试：
  - 所有模型 JSON 都能 parse。
  - cube baked quad 数量合理。
  - UV 不出现 NaN/Infinity。
- 骨骼树测试：
  - 无循环父子关系。
  - orphan bone 有明确处理。
  - root 数量合理。
- Snapshot/debug dump：
  - 输出每个骨骼 pivot、rotation、role、parent。
  - 输出每个 Locator 的最终矩阵。
- 可选渲染 smoke test：
  - 在 headless 或可控环境中至少检查 renderer 不抛异常。

建议新增开发用配置：

- `figura.ysm.debugBones`
- `figura.ysm.debugLocators`
- `figura.ysm.showHiddenParts`
- `figura.ysm.dumpModelInfo`

验收标准：

- `refs/YSM_NEW_Model`、`refs/YSM_OLD_Model`、`refs/01_taisho_maid` 能全部通过解析检查。
- 模型加载失败时能定位到具体文件、骨骼、动画或表达式。
- 后续修复渲染问题时能先看 dump，而不是只靠截图猜。

## 13. 工作包九：性能与资源生命周期

目标：避免第二阶段引入动画和更多资源后造成卡顿、泄漏或 zip 文件占用。

重点：

- 纹理上传去重。
- 动画 clip 解析缓存。
- zip FileSystem 生命周期清理。
- Avatar 切换时释放 YSM texture、mesh、animation runtime。
- 避免每帧重新解析字符串或 JSON。
- 每帧矩阵计算使用脏标记或顺序缓存。

验收标准：

- 多次切换 Figura/YSM 模型后没有明显 GPU 资源泄漏。
- zip 模型切换后 Windows 下文件不被长期占用。
- idle 状态每帧分配量可控。

## 14. 实施顺序

建议按以下顺序推进：

1. 资源索引补全
   - 先让 loader 能稳定知道模型包里有什么。
2. PartMask 与 Locator
   - 先解决“哪些东西该显示，哪些只是挂点/预览/GUI”的根问题。
3. Lua 世界矩阵
   - Locator 和手持物品都依赖真实世界矩阵。
4. 手持物品与特殊挂点第二版
   - 用 Locator/PartMask 重构第一阶段的手持物品逻辑。
5. 动画 JSON 解析
   - 先 parse 和存储，不急着完整播放。
6. 动画播放器与 MoLang 子集
   - 先 idle/walk，再扩展其他状态。
7. 第一人称手臂
   - 在第三人称骨骼和 Locator 稳定后接入。
8. 批量测试与 debug overlay
   - 每个阶段都补，不要放到最后才做。

## 15. 阶段验收清单

第二阶段完成时，应满足：

- 新旧 YSM 模型仍保持第一阶段所有能力。
- 常见模型 idle/walk 动画能播放。
- 大背景、GUI、展示用特殊部件不会误出现在玩家身体上。
- 手持物品通过 Locator/骨骼矩阵稳定挂载，不依赖临时偏移。
- 第一人称不会回落到原版手臂。
- Lua 能拿到真实骨骼世界矩阵，并能控制 YSM 骨骼。
- 至少覆盖以下模型集：
  - `refs/YSM_NEW_Model`
  - `refs/YSM_OLD_Model`
  - `refs/01_taisho_maid`
  - 当前已手测通过的 Wine Fox 模型
- `:common:compileJava` 通过。
- YSM 加载失败有明确错误日志，不影响 Figura 普通 Avatar。

## 16. 主要风险

- Minecraft 26.1 的渲染 submit/extraction 管线和传统 Layer 渲染差异较大，第一人称和特殊装备层可能需要多处 mixin。
- YSM 模型命名并不完全统一，Locator 和 PartMask 必须允许别名、大小写差异和缺失降级。
- MoLang 完整兼容成本很高，第二阶段必须坚持子集策略。
- Lua 与动画同时修改同一骨骼时，若优先级不清，会出现用户脚本“时灵时不灵”的体验。
- 大型模型和动画如果每帧全量计算，可能引入明显分配和卡顿。

## 17. 推荐提交拆分

建议第二阶段按以下提交线拆分：

1. `Add YSM resource index and animation file discovery`
2. `Add YSM bone role and locator mapping`
3. `Replace static hidden branches with YSM part masks`
4. `Expose YSM world matrices to Lua`
5. `Refactor held item transforms through YSM locators`
6. `Add initial YSM animation clip parser`
7. `Add basic YSM animation playback and MoLang context`
8. `Add first-person YSM arm renderer`
9. `Add YSM model validation and debug dumps`

这样每个提交都有独立验收点，也方便回滚。

## 18. 第二阶段最小可交付版本

如果需要控制范围，第二阶段的最小可交付版本可以收缩为：

- 资源索引补全。
- PartMask/Locator 体系。
- 手持物品重构到 Locator 矩阵。
- Lua `getWorldMatrix()`。
- 动画只做到 `idle` 和 `walk`。
- 批量解析测试覆盖 `refs/` 中现有模型。

这个版本已经能明显提升兼容性，并为第三阶段完整动画、第一人称和特殊装备层打好地基。
