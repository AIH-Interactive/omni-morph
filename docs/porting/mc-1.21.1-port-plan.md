# OmniMorph 1.21.1 移植计划书（Fabric + NeoForge）

| 项目 | 内容 |
| --- | --- |
| 文档状态 | 草案 v0.1（待评审） |
| 生成日期 | 2026-10-31 |
| 代码基线 | 分支 `1.21.1`，提交 `c27f761`（与 `origin/1.21.1`、`origin/main` 一致） |
| 目标版本 | Minecraft **1.21.1**，Java 21，Fabric + NeoForge 双加载器 |
| 当前版本 | Minecraft 26.1.2，Java 25，仅 Fabric（`enabled_platforms = fabric`） |
| 交付物 | 双加载器可运行构建 + 本计划落地的分阶段提交 + 发布包 `omni-morph-1.0.0-alpha.7+1.21.1` |
| 附录 | [1.21.1 API 差距清单（133 项）](mc-1.21.1-api-gap.csv) |

---

## 0. 一页结论

1. **这不是"改版本号"，而是一次降级移植（26.1.2 → 1.21.1）。** 从 1.21.1 到 26.1.2，Minecraft 依次引入了三代渲染/GUI 架构，到 26.1.2 形成完整的两段式管线：
   - 实体渲染状态（`EntityRenderState` 家族）；
   - GPU 管线（`RenderPipeline` / `GpuDevice`）；
   - GUI 抽取器（`GuiGraphicsExtractor`）、提交节点（`SubmitNodeCollector`）与 `ResourceLocation` → `Identifier` 重命名。

   1.21.1 上这些**全都不存在**，只能回到 `PoseStack` + `MultiBufferSource` + `RenderType` + `GuiGraphics` 的立即模式。

2. **代价是可控的，因为内核与平台边界已经是干净的。** `common` 只依赖 Architectury 的 `@ExpectPlatform`（6 处），加载器代码全部在 `fabric` / `neoforge` 平台模块；Molang、YSM 解析、Lua、装扮、后端协议等 377 个纯 Java 文件与 MC 版本无关。真正需要重写的是 **109 个文件（约 33k 行）的渲染/GUI 绑定层**，而不是整个 13.3 万行的 `common`。

3. **量化差距（本次分析实测）：** 源码引用 399 个不同的 `net.minecraft.*` 类，其中 **133 个在 1.21.1 不存在**；157 个 Mixin 文件的 125 个注入目标里 **47 个在 1.21.1 无对应类**；Access Widener 的 16 个类目标里 **11 个不存在**。

4. **推荐路线：绑定层重建 + 内核复用（方案 B）。** 不做"逐 API 翻译"，而是以 1.21.1 原生 API 重写 `gui` / `render` / `mixin` 三层，`common` 的领域内核只做机械替换（`Identifier` → `ResourceLocation` 等）。

5. **MVP 建议关闭 GPU 加速子系统。** 该子系统（16 个文件、约 3k 行）建立在 1.21.5+ 的 `RenderPipeline`/`GpuDevice` 之上，在 1.21.1 属独立工程；先走已有的 Java 回退路径，M5 再单独评估。

6. **工作量估算：** 达到"双加载器可玩"的 MVP 约 **45–65 人日**；含 GPU 加速完整对齐约 **55–80 人日**。如果目标可以放宽到 **1.21.11**，渲染层工作量可下降约 40%（见 §15 决策 D1）。

---

## 1. 目标、范围与不做的事

### 1.1 目标

| 编号 | 目标 | 验收口径 |
| --- | --- | --- |
| G1 | 在 Minecraft 1.21.1 上提供 **Fabric** 与 **NeoForge** 两个可加载的客户端包 | 两个 jar 分别在真机客户端加载成功，无 mixin 应用失败、无注册表/入口点异常 |
| G2 | 功能对齐当前 26.1.2 版本（YSM 模型、Figura 模型、Lua/Molang 双运行时、衣柜/权限/模型库、云端装扮） | 功能对照矩阵逐项通过；不可移植项显式降级并记录 |
| G3 | 版本无关内核零回归 | `common` 现有 164 个测试源、约 100 个 Gradle smoke 任务在 1.21.1 基线上全部通过 |
| G4 | 构建与发布可重复 | CI 双加载器矩阵构建产物一致；`docs/CHANGELOG` 记录 1.21.1 线 |

### 1.2 范围

- 构建体系：Gradle / Architectury Loom / 映射 / 依赖版本 / 双平台模块。
- 源码：`common`、`fabric`、`neoforge` 三个模块（`forge` 模块不恢复，见 D3）。
- 资源：Access Widener、mixin 配置、`fabric.mod.json`、`neoforge.mods.toml`、语言文件。
- 测试与 CI：现有 smoke 任务适配、双平台构建矩阵。

### 1.3 明确不做

- 不新增玩法功能、不重构与移植无关的代码（遵守工作区 `AGENTS.md` 的范围控制）。
- 不在本次补齐 Forge / Quilt 平台。
- 不为了移植而修改生产行为、不引入仅为测试服务的特殊渲染分支。
- 不在 1.21.1 首次发布中恢复 GPU 加速（除非 D2 决策为"包含"）。

---

## 2. 现状基线（证据）

### 2.1 构建与平台

| 项 | 当前值 | 位置 |
| --- | --- | --- |
| Minecraft | `26.1.2` | `gradle.properties:7` |
| Java | `25` | `gradle.properties:6` |
| Gradle | `9.4.1` | `gradle/wrapper/gradle-wrapper.properties` |
| Architectury Loom | `1.14-SNAPSHOT` | `build.gradle:3` |
| architectury-plugin | `3.4-SNAPSHOT` | `build.gradle:2` |
| 启用平台 | `fabric`（`neoforge`、`forge` 在 `settings.gradle` 中被注释） | `gradle.properties:9`、`settings.gradle:12-14` |
| 映射 | `loom.layered {}`（26.1 未混淆，仅 intermediary） | `fabric/build.gradle:24`、`common/build.gradle:25` |
| Fabric API | `0.145.4+26.1.2`，Loader `0.18.6` | `gradle.properties:26-27` |
| NeoForge | `26.1.0.7-beta`（模块未启用） | `gradle.properties:57` |
| 关键依赖 | GeckoLib `26.1:5.5`、ModMenu `18.0.0-alpha.8`、Iris、ImmediatelyFast、LambDynamicLights `4.11.2+26.1.2` | `gradle.properties:36-42` |

### 2.2 代码规模与分层

| 模块 | 主源码 | 说明 |
| --- | --- | --- |
| `common` | 850 个 `.java`，132,777 行 | 领域内核 + 渲染/GUI 绑定层 |
| `common` 测试 | 164 个 `.java` | 约 100 个 Gradle smoke 任务挂在 `check` / `test` 上 |
| `fabric` | 14 个 `.java` | 入口点、命令、配置、按键、资源重载、4 个 mixin |
| `neoforge` | 17 个 `.java`（当前未参与构建） | 入口点、事件总线、GUI 层、5 个 mixin |
| `forge` | 16 个 `.java`（未参与构建） | 历史遗留，见 D3 |
| `native/` | 10 个 C++ 文件（约 800 行） | JNI/数学/打包，与 MC 版本无关 |

`common` 对 MC 的依赖分布（按引用文件数）：**473 个文件引用 `net.minecraft.*`，377 个文件完全不引用**。

平台隔离情况良好：`common` 仅导入 `dev.architectury.injectables.annotations.ExpectPlatform`（6 个文件：`PlatformUtils`、`RenderUtils`、`ConfigKeyBind`、`EntryPointManager`、`FiguraModMetadata`、`FiguraResourceListener`），没有任何 Fabric/NeoForge 类型泄漏。

### 2.3 与 Minecraft 的耦合面（实测）

- 不同 `net.minecraft.*` 类：**399 个**。
- 在 1.21.1 的 mojmap jar 中**存在**：266 个。
- **不存在**：**133 个**（构成见 §3.2，完整清单见 [附录 CSV](mc-1.21.1-api-gap.csv)）。

引用最密集的 15 个类：

| 文件数 | 类（26.1.2） | 1.21.1 处置 |
| ---: | --- | --- |
| 104 | `net.minecraft.resources.Identifier` | 重命名为 `ResourceLocation` |
| 90 | `net.minecraft.client.gui.GuiGraphicsExtractor` | 替换为 `GuiGraphics` |
| 33 | `net.minecraft.client.input.MouseButtonEvent` | 回退到 `mouseClicked(double,double,int)` |
| 30 | `net.minecraft.client.renderer.rendertype.RenderType` | 包移动到 `client.renderer.RenderType` |
| 27 | `net.minecraft.client.renderer.RenderPipelines` | 架构重建（`RenderType` + `RenderStateShard`） |
| 26 | `net.minecraft.client.renderer.SubmitNodeCollector` | 架构重建（`PoseStack` + `MultiBufferSource`） |
| 19 | `…renderer.entity.state.EntityRenderState` | 架构重建（直接 `render(...)`） |
| 18 | `net.minecraft.client.input.KeyEvent` | 回退到 `keyPressed(int,int,int)` |
| 15 | `…renderer.state.level.CameraRenderState` | 替换为 `Camera` |
| 15 | `…entity.state.HumanoidRenderState` | 架构重建 |
| 14 | `…entity.state.AvatarRenderState` | 架构重建 |
| 14 | `…renderer.SubmitNodeStorage` | 架构重建 |
| 11 | `…renderer.item.ItemStackRenderState` | 替换为 `ItemRenderer` |
| 11 | `net.minecraft.util.Util` | 包移动到 `net.minecraft.Util` |
| 10 | `net.minecraft.network.chat.FontDescription` | 替换为 `Style` 内字体 `ResourceLocation` |

### 2.4 Mixin 与 Access Widener

- Mixin 文件 **157 个**，去重注入目标 **125 个**。
- 目标可达性（按 1.21.1 jar 的类名核对）：
  - **70 个**目标在 1.21.1 存在（同名，含 `LevelRenderer`、`Gui`、`HumanoidArmorLayer`、`WingsLayer`、`CommandSuggestions`、`Font`、`Entity`、`Minecraft` 等）；
  - **8 个**外部类存在、但被引用的内部类需逐一核对（如 `BitmapProvider.Glyph`、`FontSet.Source`、`CommandSuggestions.SuggestionsList`、`ClickEvent.Action`）；
  - **47 个**无对应类，必须重定向或删除（`EntityRenderState` 家族、`SubmitNodeStorage`、`GuiGraphicsExtractor`、`GuiRenderer`、`FeatureRenderDispatcher`、`ItemStackRenderState`、`PlayerModelType`、`WingsLayer` 之外的 `EquipmentLayerRenderer` 等）。
- Access Widener：`omni-morph.accesswidener` 51 行、16 个类目标、**11 个在 1.21.1 不存在**，`accessWidener v2 named` 需要整体重写。

### 2.5 渲染 / GPU 子系统

- 直接使用 `com.mojang.blaze3d.pipeline.RenderPipeline` 或 `GpuDevice` 的文件：**16 个**，包括 `MinecraftGpuDeviceAdapter`（559 行）、`GpuShaderProgram`（317 行）、`GpuAccelerationBackend`（1017 行）、`FiguraRenderTypes`、`FiguraGuiRenderer`、`UIHelper`、`FiguraTexture`、`CustomParticlePipelines` 等。
- 该子系统自带 Java 回退（`FallbackReason`、`HardwareAcceleration` 开关、`NativeLibraryLoader`），因此"1.21.1 默认关闭 GPU 路径"在工程上是可行的，不需要推翻现有架构。

### 2.6 仓库历史给出的参考点

- 首个提交 `641b23f` 的版本是 **1.21.11**（`java_version=21`、`fabric_api 0.141.3+1.21.11`、`neoforge 21.10.56-beta`）；
- 提交 `b5caa5c`（"同步 26.1.2 版本代码"）完成 26.1.2 迁移，此后 400+ 次提交继续演进。

结论：**仓库内没有 1.21.1 基线**。1.21.11 已使用 `Identifier`、`SubmitNodeCollector`、`EntityRenderState`，因此不能作为 1.21.1 的直接参考；它只能作为"26.1 GUI 抽取器改造前"的局部参考（`GuiGraphics` 用法）。

### 2.7 本次分析所用工具

| 工具 | 用途 | 结果 |
| --- | --- | --- |
| `codegraph_explore` / `codegraph status` | 架构与调用半径（`PlatformUtils`、`EntryPointManager`、`FiguraMod`） | 索引 1,089 文件 / 35,892 节点 / 113,001 边，状态最新 |
| `minecraft-dev-mcp` `list_minecraft_versions` | 版本可用性 | 921 个可用版本，本地缓存为空 |
| `minecraft-dev-mcp` `get_minecraft_source(1.21.1)` | 取 1.21.1 源码 | **请求超时**（首次需下载 26.8 MB 客户端 jar + remap + 全量反编译） |
| `minecraft-dev-mcp` 缓存产物 + `javap` | 校验 1.21.1 签名 | 已确认 `GuiGraphics.fill/drawString/blit/pose`、`Screen.render(GuiGraphics,int,int,float)`、`keyPressed(int,int,int)`、`RenderType.create(...)`、`HumanoidArmorLayer.render(PoseStack,MultiBufferSource,int,...)`，且无 `Font$PreparedTextBuilder` |
| Modrinth / NeoForged Maven | 依赖版本 | GeckoLib 4.9.3（1.21.1 Fabric）、NeoForge 21.1.185、Fabric API 0.116.6+1.21.1 |

> 说明：`minecraft-dev-mcp` 1.21.1 客户端 jar 已缓存到 `%APPDATA%\minecraft-dev-mcp\jars\minecraft_client.1.21.1.jar`，mojmap remap 产物在 `%APPDATA%\minecraft-dev-mcp\remapped\1.21.1-mojmap.jar`。**全量反编译在 M0 需单独预热一次**（见 §16），否则后续 `get_minecraft_source` / `search_minecraft_code` 会持续超时。

---

## 3. 差距分析：为什么这不是改版本号

### 3.1 三代 API 的代差

| 能力 | 1.21.1（目标） | 26.1.2（现状） | 影响 |
| --- | --- | --- | --- |
| 资源 ID | `ResourceLocation` | `Identifier`（重命名） | 104 个文件机械改名 |
| GUI 绘制 | `GuiGraphics.fill/blit/drawString` 立即模式 | `GuiGraphicsExtractor` + `GuiTextRenderState` + `GuiRenderState` 抽取-提交两段式 | 90 个文件重写 |
| 输入事件 | `mouseClicked(double,double,int)`、`keyPressed(int,int,int)` | `MouseButtonEvent`、`KeyEvent`、`CharacterEvent` 记录类型 | 54 个文件回调签名回退 |
| 实体渲染 | `render(T, yaw, partialTick, PoseStack, MultiBufferSource, light)` | `EntityRenderState` 家族 + `SubmitNodeCollector` 提交 | 60+ 个文件重写 |
| 渲染管线 | `RenderType` + `RenderStateShard` + `ShaderInstance` | `RenderPipeline` / `RenderPipelines` / `RenderSetup` | 69 个"架构重建"类 |
| GPU 抽象 | `GlStateManager` / 直接 GL | `GpuDevice` / `CommandEncoder` / `GpuBuffer` | GPU 加速子系统需重写或关闭 |
| 装备渲染 | `HumanoidArmorLayer` + `ArmorTrim` | `EquipmentLayerRenderer` + `EquipmentClientInfo` + `Equippable`/`EquipmentAsset` | 装备/盔甲路径重写 |
| 版本标识 | 1.21.1 混淆 + mojmap | 未混淆 | 构建配置与映射策略变更 |

### 3.2 133 个缺失类的构成

| 迁移类型 | 数量 | 典型代表 |
| --- | ---: | --- |
| 架构重建（渲染状态/提交/管线/渲染特性） | 69 | `SubmitNodeCollector`、`RenderPipelines`、`EntityRenderState`、`FeatureRenderDispatcher`、`GuiRenderer` |
| 包移动（同名类换包） | 19 | `util.Util`→`Util`、`model.player.PlayerModel`→`model.PlayerModel`、`rendertype.RenderType`→`renderer.RenderType` |
| 删除（1.21.2+ 新增体系） | 15 | `Equippable`、`EquipmentAsset`、`Consumable`、`KineticWeapon`、`TriState`、`InputWithModifiers` |
| 替换（有等价旧类） | 6 | `ARGB`→`FastColor.ARGB32`、`LightCoordsUtil`→`LightTexture`、`FontDescription`→`Style.font` |
| 重命名 | 4 | `Identifier`、`ItemUseAnimation`→`UseAnim`、`PlayerSkin`（换包）、`AvatarRenderer`→`PlayerRenderer` |
| 签名回退（记录类型→基本类型） | 3 | `MouseButtonEvent`、`KeyEvent`、`CharacterEvent` |
| 其他（合并/待查证） | 16 | `PlayerCapeModel`、`AbstractScrollArea`、`ChannelAccess.ChannelHandle` |

完整逐项清单（含引用文件数与建议替代）：[mc-1.21.1-api-gap.csv](mc-1.21.1-api-gap.csv)。

### 3.3 受影响区域的量化

| 区域 | 文件数 | 引用 MC | 需替换 | 占比 |
| --- | ---: | ---: | ---: | ---: |
| `gui` | 149（23,738 行） | 142 | 112 | **75%** |
| `mixin` | 114（7,085 行） | 107 | 72 | **63%** |
| `model` | 177 | 73 | 37 | 21% |
| `other` | 139 | 64 | 23 | 17% |
| `lua` | 103 | 52 | 20 | 19% |
| `render` | 30（9,394 行） | 10 | 9 | 30% |
| `avatar` | 55 | 12 | 5 | 9% |
| `molang` | 50 | 3 | 2 | 4% |
| `backend` | 33 | 5 | 0 | 0% |

**结论：工作量集中在 `gui`（112 文件）与 `mixin`（72 文件）。** `backend`、`molang`、`avatar`、`lua` 的内核几乎不受版本影响，这正是方案 B 可行性的依据。

### 3.4 构建侧的差距

1. `settings.gradle` 未包含 `neoforge`；`gradle.properties` 的 `enabled_platforms` 只有 `fabric`。
2. 映射策略需从"未混淆 + intermediary"改为 **1.21.1 混淆 + `officialMojangMappings()`**（Fabric 与 NeoForge 统一 mojmap，避免 `common` 代码在两种命名空间间反复调整）。
3. Fabric 侧使用了 1.21.1 不存在的 v1 模块 API：`ResourceLoader`（`fabric-resource-loader-v1`）、`KeyMappingHelper`（`fabric-key-mapping-api-v1` 新版），需回退到 `ResourceManagerHelper` / `KeyBindingHelper`。
4. 两个平台模块的 shadow 插件不同版本（`com.gradleup.shadow 9.0.0-beta12` vs `com.github.johnrengelman.shadow 8.1.1`），需统一到与 Gradle/Loom 兼容的单一版本。
5. `common/mojmap` 子模块在"全量 mojmap"策略下失去意义（见 D6）。

---

## 4. 移植策略

### 4.1 备选方案

| 方案 | 做法 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- | --- |
| A. 逐 API 平移 | 在现有 26.1.2 代码上把 133 个类逐个替换 | 改动看似局部 | 渲染/GUI 的两段式架构无法"平替"，会产生 69 处伪适配；后续难维护 | 不推荐 |
| **B. 绑定层重建 + 内核复用** | 以 1.21.1 原生 API 重写 `gui`/`render`/`mixin`；`common` 内核只做机械替换 | 边界清晰、可并行、内核零回归 | 需要先搭好编译骨架才能看到效果 | **推荐** |
| C. 先降到 1.21.11 再降 1.21.1 | 利用首个提交的 1.21.11 状态作为中间站 | 1.21.11 的 `GuiGraphics` 可参考 | 1.21.11 已进入 render-state 时代，对 1.21.1 减少的工作有限，反而多一次迁移 | 不推荐 |

### 4.2 推荐方案的分层边界

| 层 | 目录 | 文件/规模 | 1.21.1 处置 |
| --- | --- | --- | --- |
| 领域内核（版本无关） | `model/ysm`、`molang`、`lua`、`avatar`、`backend2`、`model/outfit`、`model/library` | 377 个文件不引用 MC | **保持**；仅替换 `Identifier`、`NativeImage` 等少量 API |
| 资源/数据适配 | `model/cache`、`parsers`、`utils` | 约 60 个文件 | 机械替换 |
| 渲染绑定 | `render/acceleration`、`model/rendering`、`FiguraRenderTypes` 等 | 90 个文件 / 9,394 行 | **重写为 1.21.1 `RenderType` + `PoseStack` + `MultiBufferSource`** |
| GUI 绑定 | `gui`（149 文件 / 23,738 行） | 112 个文件需替换 | **重写为 `GuiGraphics`** |
| Mixin 层 | `common/.../mixin`、平台模块 mixin | 157 文件 / 125 目标 | 70 保留、8 核对、47 重定向/删除 |
| 平台层 | `fabric`、`neoforge` | 31 个文件 | 回退到 1.21.1 的 Fabric API / NeoForge 21.1 API |
| 原生层 | `native/` | 10 个 C++ 文件 | **不变**（仅构建脚本与打包路径核对） |

### 4.3 编码规则（全过程强制）

1. **映射统一**：所有平台使用 mojmap；`common` 中禁止出现 Yarn 名称；Access Widener 使用 `named`（= mojmap）命名空间。
2. **平台隔离**：`common` 不得出现 Fabric/NeoForge 类型；平台差异只能通过 `@ExpectPlatform` 或平台模块实现。
3. **Mixin 最小化**：仅注入 1.21.1 存在的 vanilla 目标；第三方（GeckoLib、Iris、Sodium）目标只放在对应平台模块并在缺失时安全跳过。
4. **不回退架构**：不为了少改代码而保留 26.1 的两段式渲染/提交抽象。
5. **可读性**：重写文件遵循仓库现有风格；新增代码保持小函数、显式命名（遵守 `AGENTS.md` 的可读性条款）。
6. **范围控制**：每个里程碑只做该里程碑范围；发现的其他问题记为后续项，不顺手重构。

---

## 5. 里程碑与工作分解（WBS）

> 估时单位为"人日"，按 1 名熟悉该仓库的开发者计；并行时的关键路径见 §14。

### M0 — 工具链与基线（2–3 人日）

- 调整 `gradle.properties`：`minecraft_version=1.21.1`、`java_version=21`、依赖版本（见 §9）、`enabled_platforms=fabric,neoforge`。
- `settings.gradle` 启用 `neoforge`，保持 `forge` 关闭。
- 映射：`officialMojangMappings()`（可选叠加 Parchment）。
- 锁定 Loom / Gradle / shadow 版本组合（当前 Loom `1.14-SNAPSHOT` + Gradle `9.4.1` 是否支持 1.21.1 需实测；不支持则降到 Loom 1.9/1.10 + Gradle 8.12）。
- **预热 `minecraft-dev-mcp` 1.21.1 索引**（`decompile_minecraft_version` + `index_minecraft_version`），为后续 API 查询提供 ≤10 秒的响应。
- 验收：`.\gradlew :fabric:compileJava`、`.\gradlew :neoforge:compileJava` 在空壳状态下通过；`build.log` 无映射错误。

### M1 — 双加载器骨架（4–6 人日）

- 平台模块：`FiguraModFabric` / `FiguraModForge`（NeoForge）、`EntryPointManagerImpl`、`PlatformUtilsImpl`、`ConfigKeyBindImpl`、`FiguraResourceListenerImpl`、`RenderUtilsImpl`。
- Fabric API 回退：`ResourceLoader` → `ResourceManagerHelper`；`KeyMappingHelper` → `KeyBindingHelper`。
- NeoForge 事件：`FMLClientSetupEvent`、`RegisterKeyMappingsEvent`、`RegisterGuiLayersEvent`、`AddClientReloadListenersEvent`、`RegisterClientCommandsEvent`、`IClientItemExtensions`、`IConfigScreenFactory`（全部存在于 21.1.x，需逐项核对签名）。
- 资源：重写 `omni-morph.accesswidener`；拆分 `figura-common.mixins.json` / `omni-morph-common.mixins.json` / 平台 mixin 配置；`neoforge.mods.toml` 与 `fabric.mod.json` 的版本占位符。
- 验收：两个加载器均能启动到标题界面；日志中无 `Mixin apply failed`、无 ClassNotFound；`/omnimorph` 命令注册成功。

### M2 — 基础 API 替换（5–7 人日）

- `Identifier` → `ResourceLocation`（104 文件，机械替换 + 构造方法差异）。
- `Util`、`ARGB`、`LightCoordsUtil`、`ItemUseAnimation`、`PlayerSkin`、`PlayerModel`、`ArmorTrim`、`BakedQuad` 等 19 个包移动。
- 数据组件降级：`Equippable`、`EquipmentAsset`、`Consumable`、`KineticWeapon`、`KineticWeapon` 相关逻辑改为 1.21.1 的 `ItemStack`/`ArmorItem`/`UseAnim` 路径。
- `TagValueOutput`、`EnvironmentAttributes`、`Permissions` 等 26.1 新增抽象的删除与替代。
- 验收：`common` 编译通过；`backend`、`molang`、`ysm`、`outfit` 相关 smoke 任务通过（这些区域本就不依赖渲染）。

### M3 — 实体/模型渲染重建（12–18 人日）

- 删除 `EntityRenderState` / `HumanoidRenderState` / `AvatarRenderState` / `CameraRenderState` 体系，回到 `render(T, yaw, partialTick, PoseStack, MultiBufferSource, light)`。
- `PlayerRenderer`（原 `AvatarRenderer`）、`HumanoidArmorLayer`、披风/翅膀层、`PlayerHeadSpecialRenderer` → `SkullBlockRenderer`、`ItemStackRenderState` → `ItemRenderer.renderStatic`。
- `SubmitNodeCollector` / `SubmitNodeStorage` / `SubmitNodeCollection` → 直接向 `MultiBufferSource` 写入；`FiguraRenderer.DeferredItemData` 等延迟结构重设计。
- `RenderPipelines` / `rendertype.RenderTypes` → `RenderType` 静态工厂 + `RenderStateShard`；`FiguraRenderTypes`、`YsmPreviewRenderTypes` 重写。
- 验收：单机与专用服务器多人环境下，YSM/Figura 模型、盔甲、披风、第一人称手部、旁观者视角全部正确；现有 `ysmBoneMatrix`、`ysmDeferredPose`、`gpuRigidData` 等 smoke 任务在 CPU 路径通过。

### M4 — GUI 重建（12–18 人日）

- `GuiGraphicsExtractor` → `GuiGraphics`（90 文件）；`fill/blit/drawString/pose/enableScissor` 直接调用。
- 输入事件回退：`MouseButtonEvent`/`KeyEvent`/`CharacterEvent` → `Screen`/`AbstractWidget` 的 `double/double/int` 与 `int/int/int` 重载（33 + 18 文件）。
- 组件重写：`WardrobeScreen`（3,929 行）、`ActionWheel`、`EntityPreview`、列表/滚动/权限/模型库/Toast/文本域。
- 字体与文本：`FontDescription`、`GlyphSource`、`GlyphStitcher`、`BakedSheetGlyph`、`TextAlignment` → 1.21.1 `Font`/`BakedGlyph`/`StringSplitter`。
- 验收：衣柜、权限、模型库、通知、上下文菜单在**真实窗口**中逐项可用（真窗口验证，符合工作区 GUI 规则；不以无窗口渲染结果冒充通过）。

### M5 — GPU 加速子系统（8–15 人日，可延后）

- 16 个 `RenderPipeline`/`GpuDevice` 文件评估：
  - 选项 A（MVP 默认）：保留 Java 回退，GPU 开关在 1.21.1 上默认关闭并显式提示"1.21.1 暂不支持硬件加速"。
  - 选项 B（完整）：基于 1.21.1 的 `GlStateManager`/`ShaderInstance`/直接 GL 重建 SSBO 路径，复用 `native/` 的 JNI 与打包层。
- 验收（选项 B）：`gpuAccelerationSmokeTest`、`nativeSmokeTest`、`accelerationPreparationBenchmark` 在 1.21.1 通过；真机帧时间对比记录在 `build/reports/acceleration/`。

### M6 — 双加载器打包、CI 与验收（6–10 人日）

- 打包：`fabric` 的 `shadowJar`/`remapJar`、`neoforge` 的 `shadowJar`/`remapJar`/AT 注入；`neoforge.mods.toml` 的 `[[mixins]]` 与依赖声明。
- CI：`.github/workflows/fabric-build.yml` 扩展为矩阵（fabric/neoforge × JDK 21）。
- 发布：`mod_version = 1.0.0-alpha.7`；`docs/CHANGELOG*.md` 四语更新。
- 验收：两个 jar 在干净客户端加载；功能对照矩阵签署；`MANUAL/PENDING` 项显式列出。

---

## 6. API 迁移矩阵（主表）

| # | 26.1.2 | 1.21.1 | 影响文件 | 处理方式 |
| ---: | --- | --- | ---: | --- |
| 1 | `net.minecraft.resources.Identifier` | `net.minecraft.resources.ResourceLocation` | 104 | 机械替换 + `fromNamespaceAndPath`/`parse` 语义核对 |
| 2 | `client.gui.GuiGraphicsExtractor` | `client.gui.GuiGraphics` | 90 | 两段式改立即模式 |
| 3 | `client.input.MouseButtonEvent` | `mouseClicked(double,double,int)` | 33 | 回调签名回退 |
| 4 | `client.renderer.rendertype.RenderType` | `client.renderer.RenderType` | 30 | 包移动 |
| 5 | `client.renderer.RenderPipelines` | `RenderType` + `RenderStateShard` | 27 | 自建管线改自建 RenderType |
| 6 | `client.renderer.SubmitNodeCollector` | `PoseStack` + `MultiBufferSource` | 26 | 提交模型改为立即写入 |
| 7 | `…entity.state.EntityRenderState` | 无（直接 `render`） | 19 | 渲染器基类重写 |
| 8 | `client.input.KeyEvent` | `keyPressed(int,int,int)` | 18 | 回调签名回退 |
| 9 | `…state.level.CameraRenderState` | `client.Camera` | 15 | 直接读取 Camera |
| 10 | `…entity.state.HumanoidRenderState` | 无 | 15 | 渲染器重写 |
| 11 | `…entity.state.AvatarRenderState` | 无 | 14 | `PlayerRenderer` 重写 |
| 12 | `client.renderer.SubmitNodeStorage` | `MultiBufferSource.BufferSource` | 14 | 提交队列改缓冲区 |
| 13 | `…item.ItemStackRenderState` | `ItemRenderer` | 11 | 物品渲染重写 |
| 14 | `net.minecraft.util.Util` | `net.minecraft.Util` | 11 | 包移动 |
| 15 | `network.chat.FontDescription` | `Style#font` | 10 | 字体引用重构 |
| 16 | `…rendertype.RenderTypes` | `RenderType` 工厂 | 10 | 同上 |
| 17 | `client.model.player.PlayerModel` | `client.model.PlayerModel` | 10 | 包移动 + 构造差异 |
| 18 | `client.input.InputWithModifiers` | 无 | 8 | 参数拆分 |
| 19 | `…model.cuboid.ItemTransform` | `…block.model.ItemTransform` | 8 | 包移动 |
| 20 | `…entity.layers.WingsLayer` | `…layers.ElytraLayer`（1.21.1 语义） | 8 | 层替换 |
| 21 | `…gui.render.pip.PictureInPictureRenderer` | 无 | 7 | 预览渲染改直接绘制 |
| 22 | `…multiplayer.chat.GuiMessage` | `client.GuiMessage` | 6 | 包移动 |
| 23 | `util.LightCoordsUtil` | `client.renderer.LightTexture` | 6 | 替换 |
| 24 | `world.item.ItemUseAnimation` | `world.item.UseAnim` | 6 | 重命名 |
| 25 | `…respons.model.EquipmentClientInfo` | 无（`HumanoidArmorLayer`） | 6 | 装备渲染路径重写 |
| 26 | `…layers.EquipmentLayerRenderer` | `HumanoidArmorLayer` | 5 | 同上 |
| 27 | `…rendertype.RenderSetup` | `RenderType.CompositeState` | 5 | 管线描述重构 |
| 28 | `…renderer.SubmitNodeCollection` | 无 | 5 | 删除 |
| 29 | `…gui.render.GuiRenderer` | 无 | 5 | 删除 |
| 30 | `…renderer.state.gui.pip.*` | 无 | 5 | 删除 |

> 完整 133 项见 [mc-1.21.1-api-gap.csv](mc-1.21.1-api-gap.csv)，其中"需查证"15 项必须在 M2/M4 用 `minecraft-dev-mcp` 或 `javap` 现场确认后再动手。

---

## 7. Mixin 迁移计划

### 7.1 分类规则

| 类别 | 判定 | 处理 |
| --- | --- | --- |
| 保留 | 目标类在 1.21.1 存在且注入点语义未变（70 个） | 只改描述符与映射名 |
| 核对 | 外部类存在、内部类/字段需比对（8 个） | 用 `javap`/反编译确认后改写 |
| 重定向 | 目标类消失但存在等价旧类（约 30 个） | 改注入到 1.21.1 旧类（如 `EntityRenderState` → 具体 `XxxRenderer`） |
| 删除 | 26.1 新架构独有、1.21.1 无对应行为（约 17 个） | 删除并同步移除 mixin 配置项与 AW 条目 |

### 7.2 重点目标

| 目标（26.1.2） | 文件数 | 1.21.1 处置 |
| --- | ---: | --- |
| `LevelRenderer` | 5 | 保留，注入点比对 |
| `HumanoidArmorLayer` | 4 | 保留（1.21.1 就是该层） |
| `WingsLayer` | 4 | 重定向到 `ElytraLayer`（1.21.1 的披风/鞘翅层） |
| `Gui` / `CommandSuggestions` / `Screen` | 3/3/2 | 保留，签名回退 |
| `EntityRenderer` / `AvatarRenderer` | 2/2 | 重定向到 `PlayerRenderer` / `LivingEntityRenderer` |
| `SubmitNodeStorage.*` | 5 | 删除 |
| `GuiRenderer` / `GuiGraphicsExtractor` | 2/1 | 删除 |
| `FeatureRenderDispatcher` 及 4 个 FeatureRenderer | 5 | 删除 |
| `ItemModelResolver` / `ItemStackRenderState` | 2/2 | 重定向到 `ItemRenderer` |
| `GeoArmorRenderer` / `GeoRenderer` | 1/1 | 随 GeckoLib 4.9.3 重写 |
| `GlBuffer` | 1 | 随 Iris/Sodium 目标版本重写或禁用 |

### 7.3 平台 mixin 差异

- Fabric 侧：`CommandSuggestionsMixinFabric`、`LevelRendererMixinFabric`、`HumanoidArmorLayerMixinFabric`、`GuiMixin`；
- NeoForge 侧：`ClientCommandSourceMixinNeoForge`、`CommandSuggestionsMixinNeoForge`、`LevelRendererMixinNeoForge`、`HumanoidArmorLayerMixinNeoForge`、`ElytraLayerAccessor`；
- 原则：**同名功能的 mixin 只保留一份在 `common`**，仅在事件/API 确实不同时才分平台实现，避免 157 个 mixin 再翻倍。

---

## 8. Access Widener / AT 重写

1. 现有 51 行中保留可用的 5 项（如 `ItemStackComponentizationFix$ItemStackData`、`MultiBufferSource$BufferSource` 若字段名不变）。
2. 删除 11 个失效类目标：`GuiTextRenderState`、`Font$PreparedTextBuilder`、`GuiGraphicsExtractor$ScissorStack`、`Identifier`、`EquipmentLayerRenderer$LayerTextureKey`、`EquipmentLayerRenderer$TrimSpriteKey`、`ParticleResources$MutableSpriteSet`、`AtlasManager$AtlasEntry`、`FontSet$SelectedGlyphs` 等。
3. 新增 1.21.1 需要的最小暴露项（按 M3/M4 实际需要逐条添加，不做"预防性"加宽）。
4. 校验：用 `minecraft-dev-mcp` 的 `validate_access_widener`（1.21.1）或 `javap` 逐条比对类/成员是否存在。
5. NeoForge 侧同样需要 `neoforge.accesstransformer.cfg`（若使用 AT 而非 AW），并用 `validate_access_transformer` 校验。

---

## 9. 依赖与版本矩阵（建议基线）

| 组件 | 26.1.2（现状） | 1.21.1（建议） | 备注 |
| --- | --- | --- | --- |
| Minecraft | 26.1.2 | **1.21.1** | — |
| Java | 25 | **21** | 1.20.5+ 要求 21；NeoForge 21.1 要求 21 |
| Gradle | 9.4.1 | 8.12 或 9.x（随 Loom 定） | M0 实测锁定 |
| Architectury Loom | 1.14-SNAPSHOT | 1.9.x/1.10.x（待实测） | 需支持 1.21.1 混淆 + mojmap |
| Fabric Loader | 0.18.6 | 0.16.x | — |
| Fabric API | 0.145.4+26.1.2 | **0.116.6+1.21.1** | 已验证存在 |
| NeoForge | 26.1.0.7-beta | **21.1.185** | 已验证存在 21.1.x 线 |
| GeckoLib | `26.1:5.5` | **4.9.3**（`geckolib-fabric-1.21.1-4.9.3` / `geckolib-neoforge-1.21.1-4.9.3`） | Modrinth 已验证 |
| ModMenu | 18.0.0-alpha.8 | 11.0.x | 1.21.1 线 |
| Iris | 1.10.8+26.1 | 1.8.x（1.21.1） | compileOnly |
| ImmediatelyFast | 1.15.2+26.1.2 | 1.3.x（1.21.1） | 注意 `fabric.mod.json` 当前声明与其冲突 |
| LambDynamicLights | 4.11.2+26.1.2 | 3.1.x API | compileOnly，可选 |
| MixinExtras | 0.3.6 | 0.4.x | 1.21.1 线 |
| protobuf / luaj / nv-websocket | 现状 | 不变 | 与 MC 无关 |

> 表中"建议"均需在 M0 用实际依赖解析验证；未验证项不得写入发布说明。
>
> **M0 实测校准（2026-10-31）**：Loom 1.14-SNAPSHOT + Gradle 9.4.1 可直接用于 1.21.1，无需降级。实际锁定的版本为 Fabric Loader 0.16.14、Iris 1.8.8、ImmediatelyFast 1.6.15、ModMenu 11.0.5、LambDynamicLights 4.8.11；另外 `aircompressor-v3` 换成 `aircompressor:0.27`（前者要求 Java 22 以上）。详见 [mc-1.21.1-port-progress.md](mc-1.21.1-m0-baseline.md)。

---

## 10. GPU 加速子系统专项

| 现状组件 | 行数 | 1.21.1 依赖 | 处置 |
| --- | ---: | --- | --- |
| `GpuAccelerationBackend` | 1,017 | `GpuDevice`、`GpuBuffer`、`CommandEncoder` | 关闭或基于 GL 重写 |
| `MinecraftGpuDeviceAdapter` | 559 | `GpuDevice` | 删除（1.21.1 无此抽象） |
| `GpuSubmission` / `GpuBatchData` / `GpuRigidData` / `GpuModelData` | 412/251/350/201 | 主要与 Java 侧数据结构相关 | 保留（CPU 记录路径仍可用） |
| `GpuShaderProgram` | 317 | `RenderPipeline` | 改 `ShaderInstance` 或直接 GL |
| `HardwareAcceleration` / `AccelerationService` | 222/215 | 开关与生命周期 | 保留，默认关闭 |
| `native/`（JNI + 数学 + 打包） | ~800（C++） | 无 MC 依赖 | 不变 |

决策建议：**MVP 默认关闭**，在设置界面显式提示；由 D2 决定是否在 M5 恢复。

---

## 11. 构建、CI 与发布

1. `settings.gradle`：`include("common")`、`include("common:mojmap")`（视 D6）、`include("fabric")`、`include("neoforge")`。
2. `gradle.properties`：新增/修改 `enabled_platforms`、`java_version=21`、各依赖版本；`mod_version` 升为 `1.0.0-alpha.7`。
3. 平台打磨：
   - `fabric`：`fabric.mod.json` 的 `depends.minecraft` 改为 `1.21.1`，`java` 改为 `>=21`；
   - `neoforge`：`neoforge.mods.toml` 的 `modLoader`/`loaderVersion`/`[[mixins]]`/`[[dependencies]]` 全部对齐 21.1；
   - 两个平台统一 shadow 插件版本（见 §9）。
4. CI：矩阵 `{fabric, neoforge} × {JDK 21}`，产物分别上传；`check` 依赖全部 smoke 任务（当前 `fabric:check` 已依赖 `libraryPreviewRuntimeSmokeTest`）。
5. 发布：`omni-morph-1.0.0-alpha.7+1.21.1-fabric-mc.jar` / `…-neoforge-mc.jar`；四语 `CHANGELOG` 同步。

---

## 12. 质量门与验证方法

| 门 | 内容 | 判定 |
| --- | --- | --- |
| Q0 | 编译 | `.\gradlew build`（双平台）+ `build.log` 无错误 |
| Q1 | 版本无关内核 | 164 个测试源中不依赖渲染的部分全部通过（molang/ysm/outfit/backend/protocol） |
| Q2 | 渲染正确性 | 真机客户端：模型、动画、盔甲、披风、第一人称、多人同步 |
| Q3 | GUI 正确性 | 真实窗口逐屏操作；**禁止**以无窗口渲染结果作为通过依据 |
| Q4 | 双加载器 | 两个 jar 在干净实例加载，无 mixin/WARN 级注册错误 |
| Q5 | 性能 | `accelerationPreparationBenchmark`、`ysmIdlePerformanceProbe` 与 26.1.2 基线对比记录 |
| Q6 | 发布就绪 | 版本号、CHANGELOG、依赖矩阵、已知限制四项齐全 |

验证纪律（来自工作区 `AGENTS.md`）：测试、模拟环境只证明其覆盖范围；未实机验证的项记 `MANUAL/PENDING`，不得宣称完成。

---

## 13. 风险登记册

| ID | 风险 | 概率 | 影响 | 缓解 |
| --- | --- | --- | --- | --- |
| R1 | 1.21.1 无 `RenderPipeline`/`RenderState`，渲染层重写超预期 | 高 | 高 | 方案 B 先建骨架；按 M3 分解为"实体渲染 / 模型提交 / RenderType"三条并行线 |
| R2 | GeckoLib 5.5 → 4.9.3 的 `GeoArmorRenderer`/`GeoRenderer` 接口变化导致 mixin 重写 | 中 | 中 | 锁定 4.9.3；mixin 目标只在对应版本存在时启用 |
| R3 | GPU 加速依赖 1.21.5+ 抽象 | 高 | 中 | MVP 默认关闭；M5 单独立项 |
| R4 | Fabric API v1（`ResourceLoader`/`KeyMappingHelper`）在 1.21.1 不存在 | 高 | 低 | M1 直接回退 v0 API |
| R5 | 双平台 mixin/AW/AT 配置差异 | 中 | 中 | 公共 mixin 只注入 vanilla；平台差异下沉 |
| R6 | 映射混用（mojmap/Yarn）导致反复返工 | 中 | 高 | M0 固定 mojmap，`common` 禁用 Yarn 名称 |
| R7 | 1.21.1 与 26.1.2 双线维护成本 | 中 | 中 | 以分层边界 + CI 矩阵隔离；版本差异集中在绑定层 |
| R8 | `minecraft-dev-mcp` 反编译/索引不可用，API 查询退化 | 中 | 低 | M0 预热；不可用时用缓存 jar + `javap` 兜底 |
| R9 | 100 个 smoke 任务在 1.21.1 失效（依赖 MC 内部类） | 中 | 中 | M2 先跑通非渲染子集，其余随 M3/M4 修复并登记 |
| R10 | 发布包在 NeoForge 侧因 AT/混合映射失败 | 中 | 中 | M6 前用 `validate_access_transformer` + 干净实例验证 |

---

## 14. 工作量估算与排期

| 阶段 | 人日 | 可并行 |
| --- | ---: | --- |
| M0 工具链 | 2–3 | — |
| M1 骨架 | 4–6 | 与 M2 部分并行 |
| M2 基础替换 | 5–7 | 与 M1 并行 |
| M3 渲染 | 12–18 | 关键路径 |
| M4 GUI | 12–18 | 与 M3 并行（不同文件域） |
| M5 GPU | 8–15（可延后） | 独立 |
| M6 打包验收 | 6–10 | 收尾 |
| **合计（MVP，不含 M5）** | **41–62** | — |
| **合计（含 M5）** | **49–77** | — |

关键路径：M0 → M1 →（M3 ↔ M4）→ M6。建议 M3 与 M4 各由 1 人并行推进，因二者文件域几乎不重叠（`render` vs `gui`）。

---

## 15. 待决策事项（需拍板）

| ID | 决策 | 选项 | 影响 |
| --- | --- | --- | --- |
| D1 | 目标是否严格为 **1.21.1** | (a) 1.21.1（本文档）/ (b) 1.21.11 | 选 (b) 渲染层工作量约降 40%，但 GUI 抽取器改造仍需回退 |
| D2 | GPU 加速是否进入首个 1.21.1 版本 | (a) 默认关闭，M5 后置 / (b) 首发即支持 | (b) 增加 8–15 人日与 R3 风险 |
| D3 | `forge` 模块去留 | (a) 不启用（推荐）/ (b) 同步恢复 Forge 1.21.1 | (b) 显著扩大范围，建议单独立项 |
| D4 | 版本号 | **已定：`1.0.0-alpha.7`** | 用户确认：版本适配统一使用该版本号，后续任何 MC 版本适配沿用，不再递增 `alpha.N`；`gradle.properties` 已修改 |
| D5 | 26.1.2 主线是否保留 | (a) 双线维护 / (b) 1.21.1 取代 | 影响 CI 与文档结构 |
| D6 | `common/mojmap` 子模块 | (a) 保留 / (b) 全量 mojmap 后移除 | (b) 简化发布产物 |

> D4 已关闭：`mod_version` 由 `1.0.0-alpha.6` 改为 `1.0.0-alpha.7`，四语 README 的版本表同步更新；该规则已写入工作区 `AGENTS.md` 的「OmniMorph 版本规范」。CHANGELOG 的 `1.0.0-alpha.7` 条目在首次发布时补写。

---

## 16. 实施前准备清单（P0）

1. **预热 1.21.1 反编译与索引**（本环境已缓存客户端 jar 与 mojmap remap 产物）：
   ```
   minecraft-dev-mcp: decompile_minecraft_version(version="1.21.1", mapping="mojmap")
   minecraft-dev-mcp: index_minecraft_version(version="1.21.1", mapping="mojmap")
   ```
   若 MCP 请求超时，可先让其在后台完成（`%APPDATA%\minecraft-dev-mcp\decompiled\1.21.1` 出现源码即成功）。
2. **建立编译骨架**：先让 `common` 在 1.21.1 上"能编译但大量 `Unresolved`"，用编译器输出反向驱动 M2 的替换清单（编译器是最终判据）。
3. **冻结基线**：在动手前对 `c27f761` 打标签（如 `pre-1.21.1-port`），保证可回退对比。
4. **对齐第三方版本**：用 Modrinth API 逐个确认 §9 中"建议"列的版本确实存在 1.21.1 构建。
5. **准备真机环境**：Fabric 1.21.1 + NeoForge 21.1 各自独立实例，用于 Q2/Q3/Q4 验证。

---

## 附录 A：缺失类 Top 40（按引用文件数）

| 文件数 | 类 | 类型 | 1.21.1 替代/说明 |
| ---: | --- | --- | --- |
| 104 | `resources.Identifier` | 重命名 | `ResourceLocation` |
| 90 | `client.gui.GuiGraphicsExtractor` | API 替换 | `GuiGraphics` |
| 33 | `client.input.MouseButtonEvent` | 签名回退 | `mouseClicked(double,double,int)` |
| 30 | `…rendertype.RenderType` | 包移动 | `client.renderer.RenderType` |
| 27 | `client.renderer.RenderPipelines` | 架构重建 | `RenderType` + `RenderStateShard` |
| 26 | `client.renderer.SubmitNodeCollector` | 架构重建 | `PoseStack` + `MultiBufferSource` |
| 19 | `…entity.state.EntityRenderState` | 架构重建 | 直接 `render(...)` |
| 18 | `client.input.KeyEvent` | 签名回退 | `keyPressed(int,int,int)` |
| 15 | `…state.level.CameraRenderState` | 架构重建 | `Camera` |
| 15 | `…entity.state.HumanoidRenderState` | 架构重建 | 渲染器重写 |
| 14 | `…entity.state.AvatarRenderState` | 架构重建 | `PlayerRenderer` |
| 14 | `client.renderer.SubmitNodeStorage` | 架构重建 | `MultiBufferSource.BufferSource` |
| 11 | `…item.ItemStackRenderState` | 架构重建 | `ItemRenderer` |
| 11 | `util.Util` | 包移动 | `net.minecraft.Util` |
| 10 | `network.chat.FontDescription` | 替换 | `Style#font` |
| 10 | `…rendertype.RenderTypes` | 架构重建 | `RenderType` 工厂 |
| 10 | `client.model.player.PlayerModel` | 包移动 | `client.model.PlayerModel` |
| 8 | `client.input.InputWithModifiers` | 删除 | 显式参数 |
| 8 | `…model.cuboid.ItemTransform` | 包移动 | `…block.model.ItemTransform` |
| 8 | `…entity.layers.WingsLayer` | 重定向 | `ElytraLayer` |
| 7 | `…gui.render.pip.PictureInPictureRenderer` | 架构重建 | 直接绘制 |
| 6 | `…multiplayer.chat.GuiMessage` | 包移动 | `client.GuiMessage` |
| 6 | `util.LightCoordsUtil` | 替换 | `LightTexture` |
| 6 | `world.item.ItemUseAnimation` | 重命名 | `UseAnim` |
| 6 | `…model.EquipmentClientInfo` | 架构重建 | `HumanoidArmorLayer` |
| 5 | `…layers.EquipmentLayerRenderer` | 架构重建 | `HumanoidArmorLayer` |
| 5 | `…rendertype.RenderSetup` | 架构重建 | `RenderType.CompositeState` |
| 5 | `client.renderer.SubmitNodeCollection` | 删除 | — |
| 5 | `…gui.render.GuiRenderer` | 删除 | — |
| 5 | `…state.gui.pip.PictureInPictureRenderState` | 删除 | — |
| 5 | `…model.geometry.BakedQuad` | 包移动 | `…block.model.BakedQuad` |
| 5 | `…state.gui.pip.GuiEntityRenderState` | 删除 | — |
| 4 | `…components.debug.DebugScreenEntries` | 删除 | 旧 `DebugScreenOverlay` |
| 4 | `world.item.equipment.EquipmentAsset` | 删除 | — |
| 4 | `world.entity.projectile.arrow.AbstractArrow` | 包移动 | `…projectile.AbstractArrow` |
| 4 | `world.item.equipment.Equippable` | 删除 | — |
| 4 | `world.item.equipment.trim.ArmorTrim` | 包移动 | `world.item.armortrim.ArmorTrim` |
| 4 | `…special.PlayerHeadSpecialRenderer` | 重定向 | `SkullBlockRenderer` |
| 4 | `…feature.FeatureRenderDispatcher` | 删除 | — |

完整清单：[mc-1.21.1-api-gap.csv](mc-1.21.1-api-gap.csv)（133 行，含"迁移类型"与建议替代）。

---

## 附录 B：分析工具使用手册

### B.1 CodeGraph（优先用于代码理解）

```
codegraph explore "PlatformUtils EntryPointManager FiguraMod 加载器桥接"
codegraph status                     # 索引状态
```
本次索引：1,089 文件 / 35,892 节点 / 113,001 边。

### B.2 minecraft-dev-mcp（优先用于 MC API 真值）

| 场景 | 工具 |
| --- | --- |
| 版本清单 | `list_minecraft_versions` |
| 类源码/签名 | `get_minecraft_source(version="1.21.1", mapping="mojmap")` |
| 全局检索 | `search_indexed` / `search_minecraft_code` |
| 版本差异 | `compare_versions_detailed(from="1.21.1", to="26.1.2")` |
| Mixin 校验 | `analyze_mixin(source=..., mcVersion="1.21.1")` |
| AW/AT 校验 | `validate_access_widener` / `validate_access_transformer` |

**已知限制**：本机 `decompiled` 缓存为空时，首次 `get_minecraft_source` 会因全量反编译而超时（本次实测）。解决：先跑一次 `decompile_minecraft_version` 预热（可放后台等待），或直接使用已缓存的 `%APPDATA%\minecraft-dev-mcp\remapped\1.21.1-mojmap.jar` 配合 `javap` 做签名核对。

### B.3 javap 兜底校验

```powershell
$jar = "$env:APPDATA\minecraft-dev-mcp\remapped\1.21.1-mojmap.jar"
javap -classpath $jar -p net.minecraft.client.gui.GuiGraphics
javap -classpath $jar -p net.minecraft.client.gui.screens.Screen
```
本次已用它确认：`GuiGraphics.fill/drawString/blit/pose/enableScissor`、`Screen.render(GuiGraphics,int,int,float)`、`keyPressed(int,int,int)`、`RenderType.create(...)`、`HumanoidArmorLayer.render(PoseStack,MultiBufferSource,int,...)`，并确认 `Font$PreparedTextBuilder` 不存在。

---

## 附录 C：术语

| 术语 | 含义 |
| --- | --- |
| 绑定层 | 与 Minecraft 版本强相关的渲染/GUI/实体代码 |
| 内核 | 与 MC 无关的领域逻辑（Molang、YSM 解析、Lua、后端、装扮） |
| 架构重建 | 26.1 的两段式（抽取 → 提交）类在 1.21.1 无对应，只能重写 |
| MVP | 双加载器可玩的最小集合（M0–M4 + M6，GPU 关闭） |
| `MANUAL/PENDING` | 需要人工真机确认、当前未验证的项 |

---

*本计划依据 2026-10-31 的代码基线（`c27f761`）与实测数据编写；所有"建议版本"与"估算"均需在 M0 以实际构建结果校准。*
