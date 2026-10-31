# MC 1.21.1 移植进度记录（M0 起）

> 日期：2026-10-31 · 分支：`1.21.1` · 基线标签：`pre-1.21.1-port`（= `c27f761`，本地标签）
> 对应计划书：[mc-1.21.1-port-plan.md](mc-1.21.1-port-plan.md) §5 M0、§9

## 1. 工具链（实测可用）

| 组件 | 版本 | 结论 |
| --- | --- | --- |
| JDK | Dragonwell 21.0.9（`usejdk 21`） | 可用 |
| Gradle | 9.4.1（wrapper 不变） | 可用 |
| Architectury Loom | 1.14-SNAPSHOT（不变） | 支持 1.21.1 混淆版；去掉 `noIntermediateMappings()` 后正常重映射 |
| 映射 | `loom.officialMojangMappings()` | `common` / `common:mojmap` / `fabric` / `neoforge` 四处一致 |
| Shadow | `com.gradleup.shadow` 9.0.0-beta12 | `neoforge` 由 `johnrengelman` 8.1.1 换成同一插件（旧版不支持 Gradle 9） |

不需要降级 Loom / Gradle。

## 2. 依赖（已解析成功）

| 依赖 | 版本 | 备注 |
| --- | --- | --- |
| Fabric Loader | 0.16.14 | — |
| Fabric API | 0.116.6+1.21.1 | 模块名回退：`fabric-key-binding-api-v1`、`fabric-resource-loader-v0` |
| NeoForge | 21.1.185 | — |
| GeckoLib | `software.bernie.geckolib:geckolib-fabric-1.21.1:4.9.3` | 坐标组从 `com.geckolib` 改回 `software.bernie.geckolib` |
| Iris | 1.8.8+1.21.1-fabric | 1.8.12 在 Modrinth 只有 NeoForge 构建 |
| ImmediatelyFast | 1.6.15+1.21.1-fabric | — |
| ModMenu | 11.0.5 | — |
| LambDynamicLights API | 4.8.11+1.21.1 | — |
| MixinExtras | 0.3.6（不变） | — |
| aircompressor | `io.airlift:aircompressor:0.27` | `aircompressor-v3` 3.10 的类文件要求 Java 22 以上，Java 21 无法读取；0.27 提供同一个纯 Java `ZstdInputStream`（包名 `io.airlift.compress.zstd`） |

## 3. `:common:compileJava` 首轮报错（编译器真值）

- 只看第一层（import / 符号解析）：**1817 处错误，分布在 293 个文件**。修完这一层后，javac 才会报出第二层的类型和签名错误，所以这个数字是下限。
- 采集方式：本地 init 脚本放开 `-Xmaxerrs`（只放在 `build/tmp`，不进仓库）。

缺失最多的符号（已去重）：

| 符号 | 处数 | 归属里程碑 |
| --- | --- | --- |
| `GuiGraphicsExtractor` | 267 | M4 |
| `Identifier` | 222 | M2 |
| `SubmitNodeCollector` / `SubmitNodeStorage` / `SubmitNodeCollection` | 86 / 14 / 9 | M3 |
| `RenderType` / `RenderTypes` / `RenderPipelines` | 56 / 19 / 28 | M3 |
| `MouseButtonEvent` / `KeyEvent` / `InputWithModifiers` | 55 / 18 / 7 | M4 |
| `EntityRenderState` 家族（含 `CameraRenderState`、`AvatarRenderState`） | ~150 | M3 |
| `ItemStackRenderState` | 21 | M3 |
| `CompoundTag.getStringOr` / `getCompoundOrEmpty` / `getListOrEmpty` / `getFloatOr` | 23 / 11 / 6 / 6 | M2 |
| `GpuDevice` / `GpuBuffer` / `GpuTexture(View)` | 10 / 8 / 15 | M5 |

按包统计，受影响最多的是：`figura/gui/widgets`（42 个文件）、`omnimorph/mixin/render`（29）、`omnimorph/gui/widgets`（23）、`figura/mixin/render`（22）。

## 4. M0 验收结论

| 验收项 | 结果 |
| --- | --- |
| 所有模块（`common`、`common:mojmap`、`fabric`、`neoforge`）配置与依赖解析 | ✅ 通过 |
| 1.21.1 客户端下载、重映射 | ✅ 通过 |
| minecraft-dev-mcp 1.21.1 mojmap 索引 | ✅ 5363 个文件，耗时 14.6 秒 |
| `:fabric:compileJava` / `:neoforge:compileJava` 骨架编译 | ⏸ 顺延：两个平台模块都依赖 `common`，而 `common` 要到 M2–M4 修完才能编译。如果现在建临时排除清单，几乎要排除全部 293 个文件，没有验证意义。改为在 M2 以 `common` 编译通过作为门槛 |

---

# M1 双加载器骨架

## 1. 已完成

| 项 | 改动 |
| --- | --- |
| Fabric 入口 | `ResourceLoader` 改为 `ResourceManagerHelper`；`FiguraResourceListenerImpl` 实现 `IdentifiableResourceReloadListener`，按 1.21.1 的 6 参数 `reload` 签名重写 |
| Fabric 按键 | `KeyMappingHelper` 改为 `KeyBindingHelper.registerKeyBinding` |
| Fabric `GuiMixin` | 注入点改为 1.21.1 的 `Gui.render(GuiGraphics, DeltaTracker)`（`analyze_mixin` 校验通过） |
| NeoForge 入口 | `AddClientReloadListenersEvent`（21.1 没有这个类）改为 `RegisterClientReloadListenersEvent.registerReloadListener`；GUI 层 ID 改用 `FiguraIdentifier.of(...)` |
| NeoForge `PlatformUtilsImpl` | 补上缺失的 `@ExpectPlatform` 实现：`getModLoader`、`loadFileFromRoot`（基于 FML 4 的 `IModFile.findResource`） |
| `fabric.mod.json` | `minecraft` 约束收紧为 `${minecraft_version}`；`fabricloader >= 0.16` |
| `neoforge.mods.toml` | `loaderVersion [4,)`、`neoforge [21.1.185,21.2)`、`minecraft [1.21.1]`；补上漏掉的 `figura-common.mixins.json` |
| `pack.mcmeta` | `pack_format` 15 → 34（1.21.1） |
| Access Widener | 35 条中有 27 条在 1.21.1 不存在，已删除；保留的 7 条经 `validate_access_widener` 校验全部通过 |

API 真值来源：minecraft-dev-mcp（1.21.1 mojmap 源码，以及 AW / mixin 校验），NeoForge 21.1.185 的 sources jar，FML 4.0.41 的 `javap` 输出。

## 2. 跨里程碑约定

- 1.21.1 的 `ResourceLocation` 是 `final` 类、构造方法为 private，`FiguraIdentifier extends Identifier` 无法沿用。M2 改为工厂方法 `FiguraIdentifier.of(String)`，返回 `ResourceLocation`；M1 的平台代码已经按这个接口编写。

## 3. 顺延到 M3 的平台渲染 mixin（`analyze_mixin` 判定失效）

| 平台 | mixin | 原因 |
| --- | --- | --- |
| 两个平台 | `ElytraLayerAccessor` | 1.21.1 没有 `WingsLayer`，要改为 `ElytraLayer` |
| 两个平台 | `HumanoidArmorLayerMixin*` | 1.21.1 没有 `submit` / `HumanoidRenderState` / `equipmentRenderer` |
| 两个平台 | `LevelRendererMixin*` | 1.21.1 没有 `addMainPass` 的 lambda、`submitNodeStorage`、`featureRenderDispatcher` |
| 两个平台 | `RenderUtilsImpl` | 依赖 `WingsLayer` / `HumanoidRenderState`，随 `common` 里的 `RenderUtils` 签名一起改 |

## 4. M1 验收

| 验收项 | 结果 |
| --- | --- |
| 平台 API 与元数据对照 1.21.1 / NeoForge 21.1 真值 | ✅ 完成（静态核对 + MCP 校验） |
| 两个加载器启动到标题界面、`/omnimorph` 注册成功、无 `Mixin apply failed` | **MANUAL/PENDING**：要等 `common` 在 M2–M4 编译通过后，用真实客户端窗口验证 |

---

# M2 基础 API 替换

## 1. 参考基线

- 上游 Figura `0.1.6-dev/1.21`（MC 1.21.1，Figura 0.1.6），与仓库基线 `641b23f`（Figura 0.1.6 / MC 1.21.11）功能版本一致。浅克隆在 `build/tmp/ref/`，只读，不入库。
- **保护 OmniMorph 独有功能（用户要求）**：只有同时满足以下两个条件的文件才整文件采用上游 1.21.1 版本：
  1. 仓库副本等于上游 1.21.11 基线；
  2. 此后的改动（`641b23f → b5caa5c` 的 26.1 同步，以及之后的提交）经逐个审计全部是 MC API 适配。
- 凡是 OmniMorph 改过的文件，都在当前代码上逐处移植，只接受 API 层面的差异。三方合并结果"干净"也必须看完整差异：例如 ParticleEngineMixin 的合并结果会把 OmniMorph 的 duck 方法换掉，因此没有采用。

## 2. 改动汇总

| 类别 | 改动 |
| --- | --- |
| `Identifier` | 全部改为 `ResourceLocation`（130 个文件）；`FiguraIdentifier` 改为工厂方法 `of(String)` |
| NBT 取值 | 零值默认的 `getXxxOr` / `getXxxOrEmpty` 直接改为 1.21.1 的同语义方法；非零默认值及不限元素类型的列表改用新增的 `NbtDefaults`（`getStringOr` / `getIntOr` / `getFloatOr` / `getListOrEmpty` / `findByteArray`），语义与 26.1 一致 |
| 包迁移 | `Util` → `net.minecraft.Util`；`LightCoordsUtil` → `LightTexture`；`ARGB` → `FastColor.ARGB32`；实体子包（`projectile.arrow`、`throwableitemprojectile`、`animal.parrot`、`vehicle.boat`）回到 1.21.1 的位置 |
| 用途动画 | `ItemUseAnimation` → `UseAnim`。26.1 的 `SPEAR`（长矛）和 `TRIDENT` 都映射到 1.21.1 的 `UseAnim.SPEAR`，因为 1.21.1 上第三方长矛和三叉戟只能用这一个值 |
| 船 / 木筏 | `AbstractBoat` → `Boat`，`AbstractChestBoat` → `ChestBoat`；木筏判断改为 `Boat.Type.BAMBOO` |
| 注册表 | `lookupOrThrow` → `registryOrThrow`；`get(id)` / `getOrThrow(key)` 按 1.21.1 语义改为 `getHolder` / `getHolderOrThrow` |
| 文本事件 | `HoverEvent.ShowText` / `ClickEvent.*` 的 record 写法 → `new HoverEvent/ClickEvent(Action, value)`；`FiguraClickEvent` 改为继承 `ClickEvent`，保留 `onClick()` |
| 其他 | 权限等级 `hasPermissions(n)`；实体 NBT `saveWithoutId(new CompoundTag())`；`Camera.getPosition()`；`getTimer()`；`GameProfile.getName()`；`InputConstants.isKeyDown(long, int)`；按键分类改为字符串 `key.category.omni-morph.keys`（翻译键不变） |
| 肩上鹦鹉 | PlayerAPI 恢复上游 1.21.1 行为（返回肩部实体 NBT）；YsmReferenceQuery 按 1.21.1 `ParrotOnShoulderLayer` 的规则解析变种，对外语义不变 |
| 鞘翅角度 | Molang `elytra_rot_*` 按 1.21.1 `ElytraModel` 的逻辑取值（玩家读取平滑后的状态，其他实体直接计算） |
| 音频 | `SoundBuffer` 在 1.21.1 没有 `size()`，YsmAudioResources 改为自行记录每个缓冲区的 PCM 字节数 |
| 缩略图 | `NativeImage.setPixelRGBA` 写入时做 ARGB → ABGR 转换 |
| 可选依赖 | LambDynamicLights API 改用 `modCompileOnly`（4.8.11 的 jar 是 intermediary 命名）；实现 4.8 版旧签名的入口方法 |
| AW | 新增 `Player.currentImpulseContextResetGraceTime`（Molang `mace_wind_bursting`） |
| 上游整文件采用 | 76 个审计通过的 Figura 文件，清单见 `build/tmp/adopt-upstream.txt`；`ClientAPI` / `PlayerAPI` 采用上游后把 import 指回 OmniMorph 迁移后的类；补回 `ModelManagerAccessor` |

## 3. 1.21.1 不适用的 1.21.11+ 功能（已移除，不是遗漏）

| 功能 | 原因 |
| --- | --- |
| 原版长矛 `KINETIC_WEAPON` 使用变换和蓄力动画采样 | 1.21.11 才引入的数据组件和物品；`lance_charge_progress` 退回到 `getUseDuration` |
| F3 调试条目注册（`DebugScreenEntries`） | 26.1 的调试屏 API；1.21.1 上的 F3 信息在 M4 用 1.21.1 的方式恢复（**待办**） |

## 4. M2 验收

| 验收项 | 结果 |
| --- | --- |
| 非渲染 / 非 GUI 区域（backend、molang、ysm 数据、outfit、Lua 数据 API）的 API 错误 | ✅ 清零。剩余 7 处是 `Avatar` / `AvatarManager` 的 `EntityRenderState` 重载歧义引起的连带错误，随 M3 渲染状态重构一并消失 |
| `common` 编译通过；backend / molang / ysm / outfit smoke 任务通过 | ⏸ 顺延：`common` 里的渲染层和 GUI 层（M3 / M4）修完之前无法整体编译 |
| `:common:compileJava` 错误总数 | 1835（主要分布：`mixin/render` 417、`gui/widgets` 316、`render/acceleration` 135） |

---

# M3 实体 / 模型渲染重建（进行中）

## 1. 已完成（WIP 提交 `212c3ae`、`3cbc914`、`429ef23` 及其后续）

| 项 | 1.21.1 做法 |
| --- | --- |
| 渲染帧键 | 新增 `top.aihmc.omnimorph.render.EntityFrame`：`begin` / `end` / `current` / `prepare`。renderer mixin 每次渲染新建一个帧，作为原 `EntityRenderState` 在 LibraryPreviewBinding、OutfitRenderBinding、`AvatarManager.getAvatar/getEntity(EntityFrame)` 中的身份键；GUI 预览通过 `prepare` 预先登记帧，其他渲染一律作为世界帧，并在 `begin` 中以 `world=true` 捕获装扮（等价于 26.1 的抽取钩子） |
| YSM 动画输入 | `YsmEntityFrame.capture(entity, partialTick)`，按原版 `LivingEntityRenderer.render` 的 setupAnim 输入计算；去掉对渲染状态字段的反射读取 |
| 渲染类型 | `FiguraRenderTypes` 采用上游 1.21.1 实现；YSM 映射：26.1 的 `entityCutout` 对应 1.21.1 的 `entityCutoutNoCull`，`entityCutoutCull` 对应 1.21.1 的 `entityCutout`，all_cutout 半透明使用 `entityTranslucentCull` |
| 纹理 | `FiguraTexture` 保留 OmniMorph 的租约、延迟关闭、排队移除、退役回退和可重试上传，改用 GL 纹理模型；删除 26.1 的 NativeImage duck |
| Iris | 保留 YSM PBR 材质注册和阴影通道判断；1.21.1 使用原版着色器程序，不需要 `assignPipeline` |
| 装扮 | Composition / NativeRenderer / FirstPersonScripts 改用 `YsmEntityFrame` 加显式光照参数；VanillaSkeletonAdapter 适配 1.21.1 没有根部件的 `PlayerModel` |
| 提交管线 | 删除继承自上游 Figura 1.21.11（MC 1.21.9+）的 submit-node 脚手架：feature renderer mixin、SubmitNodeStorage mixin、回调 duck、延迟枢轴物品。1.21.1 的图层是立即渲染的 |
| LivingEntityRendererMixin | 按 1.21.1 的 `render(entity, yaw, partialTick, ...)` 重写，保留 OmniMorph 的全部钩子（加载指示、YSM 原生渲染与手持物品、`render_layers_first` 顺序、原版皮肤装扮、Figura 基础装扮的预算作用域、Iris 阴影跳过、第一人称矩阵刷新） |
| EntityRendererMixin | 采用上游 1.21.1 版本，恢复 Figura 自定义名称牌（26.1 版本搬到了已删除的 NameTag feature mixin 里） |

## 2. 剩余（按依赖顺序）

1. `org/figuramc/figura/mixin/render/**` 的其余文件（逐个对照上游 1.21.1 合并，保留 OmniMorph 钩子）：PlayerRenderer、ItemInHandLayer、PlayerItemInHandLayer、CustomHeadLayer、CapeLayer、ElytraLayer、HumanoidArmorLayer、SkullBlockRenderer、ItemInHandRenderer、GameRenderer、LevelRenderer、EntityRenderDispatcher、TridentRenderer、ArrowRenderer 等。**GameRendererMixin 移植时必须带上上游 1.21.1 的 `extendedPickEntity` 更新**（它在 1.21.1 上从 MinecraftMixin 的 `pick` 移到了 `GameRenderer.pick`）。图层 mixin 通过 `EntityFrame.current()` 取当前帧。
2. `top/aihmc/omnimorph/mixin/render/**`：35 个以 26.1 专属类为目标的 mixin，清单见 `build/tmp/mixins-omp.txt`（`sh build/tmp/mixin-targets.sh` 可重新生成）。
3. 平台渲染 mixin（Fabric / NeoForge 的 Elytra、Armor、LevelRenderer）以及 `RenderUtils.getPlayerSkinTexture`。上游 1.21.1 的平台钩子是 `@ExpectPlatform RenderUtils.getArmorResource(entity, stack, ArmorItem, slot, isInner, ArmorMaterial.Layer)`（Fabric 返回 `layer.texture(isInner)`，NeoForge 走物品扩展的盔甲纹理），不需要 `ElytraLayerAccessor`；随 HumanoidArmorLayer 一起移植。
4. `AvatarLoadingRenderer.render(EntityFrame, PoseStack, MultiBufferSource, int)`：LivingEntityRendererMixin 已经调用，需要在 M4 的 GUI 部分实现。
5. **YSM GUI 无光照着色器**：`YsmPreviewRenderTypes` 目前回退到有光照的原版类型。要还原，需要自带两个核心着色器（复制原版实体着色器并去掉 `minecraft_mix_light`），再通过 Fabric `CoreShaderRegistrationCallback` 和 NeoForge `RegisterShadersEvent` 注册。
6. 粒子（`lua/api/particle/**`：`CustomParticlePipelines`、`RibbonRenderState` 等基于 26.1 管线的部分）。
