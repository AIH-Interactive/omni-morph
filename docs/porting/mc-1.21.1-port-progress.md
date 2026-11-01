# MC 1.21.1 移植进度记录（M0 起）

## 当前验收摘要（2026-11-01，优先于下文历史记录）

- 阶段：用户批准的 M3–M5 合并检查点已提交推送（`ddf2bcf8`）；M6 候选实现完成并通过本地检查，双平台打包/CI矩阵/发布记录已适配；远端六平台CI待运行验证。分支 `1.21.1`，提交/推送最终结果见项目根目录 `AGENT_HANDOFF.md`。
- 已验证：common 全量生产/测试编译、Fabric/NeoForge 生产编译；正式 CPU 动画/控制器/Molang/同步与库/协议任务已有逐项日志，详见文末各接续记录。不能由此推断所有 Q1 入口或实机加载通过。
- 实际语料：`refs/avatars` 通过 21 个文件硬链接和 4 个目录 junction 访问相邻原生项目 `models`；22 个 YSM（4 明文、18 加密）和 3 个 Figura ZIP，源只读。数量断言按用户要求适应实际资源，逐资源内容/行为断言保留。
- 已验证：完整模型数据重建、手部/公开姿态/持物几何、材质、语言、AVIF；音频真实语料为 Vorbis 123 份，Opus 仅夹具覆盖。100 次切换/10 批次/全部 25 源，逐批 retained=0，图像/PCM释放通过，报告 `build/reports/ysm/m8-stability.json`。
- GPU：按已批准 MVP，默认关闭并保留 CPU；不宣称 GPU 绘制、整帧性能或 26 基线性能对比通过。
- 默认动画/配件来源完整入口已通过：用户批准同步 TAC 后，指定 neo1.21.1 参考十四文件全部一致。未验证：03_astronaut、16_tactics 等缺失模型专属场景。`tlm.is_sitting` 在原基线亦无根绑定，记既有联动跟进，其他加密controller警告未全部定位。
- 用户已明确客户端验收由人工执行，记为非阻塞 `MANUAL/PENDING`，不再等待 Agent 启动授权。实机证据缺口：真实 GUI/CJK、游戏模型渲染、Mixin 加载和多人；数据边界符号链接逃逸因权限不足仍待验证。静态 AW 15条有效不等价于加载通过。不得把这些项目标为 PASS。
- 下一步：完成 M3–M5 检查点提交推送并核对远端 HEAD，随后实施独立 M6 双加载器打包/CI/发布记录；保留上述人工与缺资源证据限制，不宣称所有 Q1 入口或最终交付完成。


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

## 2026-11-01 接续 WIP（未完成 M3）

- PlayerItemInHandLayerMixin 已改为 1.21.1 的 `renderArmWithSpyglass` 立即渲染路径，保留 EntityFrame 绑定、可见性和 SpyglassPivot；Lua ITEM_RENDER 经 ItemRendererMixin 派发。
- ItemRendererMixin 改用现有 `Avatar.itemRenderEventDirect`。
- Java 21 前台 `:common:compileJava` 失败（退出 1）：1528 错误 / 158 文件，日志 `build.log`；上述两文件无本轮编译诊断。`git diff --check` 通过。真实运行与 Mixin 应用 MANUAL/PENDING。
- 尚未提交/推送；后续继续 ItemInHandLayerMixin 与其余 M3 项。旧错误统计仅为历史记录。

### 普通手持图层接续

- ItemInHandLayerMixin 移植立即渲染与实体泛型，保留 YSM 锚点/多手链、可见性、Figura 枢轴。ItemRendererMixin 通过同实体的当前帧获取 Avatar，并传递头颅物品渲染上下文。
- javap 核实注入签名。Java 21 前台 common 编译失败（退出 1）：1510 错误 / 157 文件；本次两文件无诊断。git diff --check 通过；真实运行 MANUAL/PENDING。M3 未完成，未提交/推送。

### 暂停检查点

- ArrowRendererMixin 已恢复上游 1.21.1 的立即注入，并保留 YSM projectile 替换；移除 ArrowRenderState/提交节点回调依赖。
- 最新 common 编译失败：1503 个错误（退出 1，build.log），ArrowRendererMixin 无本轮诊断；git diff --check 通过；运行时 MANUAL/PENDING。
- 用户暂停，所有累计改动保留未提交，HEAD af23872；M3 未完成。

### 头部物品图层（恢复后 WIP）

- CustomHeadLayerMixin 移植实体泛型、ArmorItem、Map skullModels、renderSkull 和立即物品渲染，保留当前帧绑定、可见性及枢轴。
- CodeGraph + minecraft-dev 原版源码核对；analyze_mixin 未解析泛型擦除而报告描述符不匹配，javap -s 确认 method 描述符一致；真实 Mixin 应用 MANUAL/PENDING。
- Java 21 前台 common 编译退出 1：1480 错误 / 155 文件，CustomHeadLayerMixin 无诊断；git diff --check 通过。M3 仍未完成，累计修改未提交/推送。

### 头颅渲染接续 WIP

- SkullBlockRendererMixin、BlockEntityWithoutLevelRendererMixin 改为 1.21.1 立即渲染，保留头颅 Lua 事件、复杂度预算、GUI 原版回退及方块/物品/实体上下文。
- CodeGraph + minecraft-dev 原版源码核对；SkullBlockRendererMixin 的 MCP analyze_mixin 返回 valid，无错误/警告。
- Java 21 前台 common 编译退出 1：1448 错误 / 153 文件，本次两文件无诊断；diff 空白问题修复后检查通过。真实运行 MANUAL/PENDING；M3 未完成，累计改动未提交/推送。

### 第一人称手持入口 WIP

- ItemInHandRendererMixin 改为 MultiBufferSource/BufferSource；YSM 动画输入使用 YsmEntityFrame.capture，保留装扮脚本回放、事件及手臂/物品可见性。
- CodeGraph 与 minecraft-dev 查询；MCP analyze_mixin 返回 valid 无错误/警告。
- Java 21 前台 common 编译失败（退出 1）：1439 错误 / 152 文件，修改文件无诊断；git diff --check 通过。PlayerRenderer 手臂替换尚待适配，真实第一人称 MANUAL/PENDING。M3 未完成，未提交/推送。

### PlayerRenderer WIP

- 按 1.21.1 PlayerRenderer 恢复名称牌/分数板与根部旋转控制；第一人称同步绘制 YSM/Figura/原版皮肤装扮，保留草稿基底、附件、预算与原版部件恢复。
- CodeGraph + minecraft-dev 原版源码核对；MCP analyze_mixin valid，无 errors/warnings。
- Java 21 前台 common 编译失败（退出 1）：1371 错误 / 151 文件，PlayerRendererMixin 无诊断；git diff --check 通过。真实显示与交互 MANUAL/PENDING，M3 未完成，累计修改未提交/推送。

### 盔甲渲染 WIP

- Fabric 盔甲 mixin 改用 1.21.1 实体/立即渲染，保留帧绑定、权限、枢轴、可见性、GeckoLib 回退；新增 common 和 Fabric/NeoForge 盔甲纹理平台入口。
- CodeGraph + minecraft-dev 查证原版；analyze_mixin 的 render 描述符受泛型/导入解析限制，javap 核对实际擦除签名。
- :fabric:compileJava 退出 1，依赖 common 仍报 1371 错误，未执行 Fabric Java 编译。diff 检查通过，真实行为 MANUAL/PENDING。NeoForge 图层及其补丁签名待处理，M3 未完成，未提交/推送。

### NeoForge 盔甲 WIP

- 用 CodeGraph 梳理旧实现；minecraft-dev 从本地 21.1.185 patched jar 反编译并读取 HumanoidArmorLayer，javap 双重核对六个动画参数的重载。
- NeoForge 盔甲图层移植立即渲染，回退保留当前动画参数，染色使用 IClientItemExtensions，纹理使用 ClientHooks。
- :neoforge:compileJava 退出 1，依赖 common 仍 1371 错误，平台 Java 编译尚未执行；diff 检查通过。实机 MANUAL/PENDING，M3 未完成，未提交/推送。
- patched 版本 analyze_mixin 仍有导入/泛型描述符解析报错及 priority 900 提示；使用 javap 实际描述符交叉核实，不记静态分析 PASS。

### RenderUtils / ItemTask WIP

- 移除无调用的新版提交节点辅助与平台 WingsLayer accessor/注册；保留盔甲平台钩子。火焰纹理查询改为 1.21.1 图集 API。
- ItemTask 恢复直接 ItemRenderer 渲染和 BakedModel 复杂度查询，保留缓冲区、左右手、世界、light/overlay/seed。CodeGraph 与 minecraft-dev 核对。
- Java 21 前台 common 编译失败：1333 错误，RenderUtils/ItemTask 无诊断；diff 检查通过，实机 MANUAL/PENDING。M3 未完成，累计修改未提交/推送。

### GameRenderer WIP

- 按 1.21.1 恢复相机矩阵、bobbing、FOV、后处理与 extendedPickEntity；保留 OmniMorph 帧开始与 finally 资源退役。
- CodeGraph + minecraft-dev 原版源码核对，analyze_mixin valid 无 errors/warnings。
- common Java 21 前台编译退出 1：1311 错误，GameRendererMixin 无诊断；diff 检查通过。真实行为 MANUAL/PENDING，GUI 预览替代注册留 M4。M3 未完成，未提交/推送。

### EntityRenderDispatcher WIP

- 火焰/阴影/载具显示改用 1.21.1 接口，保留 YSM vehicle 子实体即时替换、纸娃娃火焰朝向及装扮/预览 Avatar 绑定。
- CodeGraph + minecraft-dev 原版源码核对；MCP 静态检查 valid。Java 21 前台 common 编译退出 1：1291 错误 / 147 文件，该文件无诊断；diff 检查通过。
- 实机 MANUAL/PENDING，M3 未完成，累计修改未提交/推送。后续继续 LevelRenderer 与平台世界渲染钩子。

### 世界渲染钩子 WIP

- 公共 LevelRenderer 钩子在 vanilla/NeoForge patched 两版静态检查均 valid；移除平台失效 addMainPass 重复钩子和注册，保留 YSM 跳过规则，并 finally 恢复第一人称矩阵栈/标志。
- CodeGraph + minecraft-dev 两版源码核对；mixin JSON 解析通过。Java 21 common 编译仍失败：1291 错误，公共文件无诊断；diff 检查通过。
- 真实世界渲染、两加载器启动 MANUAL/PENDING，M3 未完成，累计修改未提交/推送。

### 模型/纹理绑定 WIP

- 公共鞘翅默认纹理 accessor 恢复并注册；模型部件包名与 overlay duck 引用修正。纹理清理改用始终入队的 tell；精灵去除 1.21.1 不支持的逐顶点 lineWidth。
- CodeGraph + minecraft-dev 查询调度/API 真值；新 accessor 静态检查 valid。Java 21 common 编译退出 1：1278 错误 / 143 文件，本批错误消失（加载指示器仍待 M4）。diff 检查通过，实际行为 MANUAL/PENDING。
- M3 未完成，累计修改未提交/推送。

### 渲染状态脚手架 WIP

- 清点调用方后移除九个已由直接 renderer 参数/EntityFrame/物品与头颅入口替代的状态 mixin，以及旧物品状态接口；同步删除注册。未移除仍有实际调用的 GUI/粒子部分。
- CodeGraph + minecraft-dev 核对；JSON 解析及引用检查通过。Java 21 common 前台编译退出 1：1216 错误 / 133 文件；diff 检查通过。
- 替代路径真实行为 MANUAL/PENDING，M3 未完成，累计改动未提交/推送。

### 模型姿态/状态钩子 WIP

- 保留 ModelPart/PartPose 可见性保存恢复，移除 PartPose 上无目标的 equals 注入；清理无调用的模型提交容器、相机状态和新版装备层 accessor/注册。
- CodeGraph 与 minecraft-dev 核对，保留 mixin 静态检查均 valid；JSON 与 diff 检查通过。
- Java 21 common 前台编译退出 1：1194 错误 / 129 文件，模型文件无诊断；真实渲染 MANUAL/PENDING。M3 未完成，累计修改未提交/推送。

### 图集/告示牌 WIP

- 删除无调用的新版图集 accessor；告示牌表情迁移到 SignRenderer，使用字节码核实的 Fabric/NeoForge 两个 lambda alias。保留配置与表情转换。
- CodeGraph + minecraft-dev 源码查询、两平台 javap 核对；JSON、引用和 diff 检查通过。
- Java 21 common 前台编译退出 1：1189 错误 / 127 文件，新告示牌文件无诊断；实际显示及 Mixin 应用 MANUAL/PENDING。M3 未完成，未提交/推送。

### 粒子适配起步 WIP

- 已查证 1.21.1 粒子即时 render/ParticleRenderType 路径。补齐 SingleQuadParticle 的 RGBA/alpha 扩展，sprite 查询改为 ParticleEngine.spriteSets；移除失效 ParticleResources accessor，补回 removeParticle 接口声明。
- CodeGraph + minecraft-dev 查询，SingleQuad mixin 静态检查 valid；Java 21 common 编译退出 1：1181 错误。JSON、diff 检查通过。
- 自定义粒子的材质/顶点/ribbon/预览及 engine extract 替代仍未完成，真实功能 MANUAL/PENDING；测试替身需随接口迁移适配。M3 未完成，未提交/推送。

## 粒子构造与光照 WIP（2026-11-01）

- CodeGraph 核对 CustomParticle 与引擎/预览调用；minecraft-dev 查询 1.21.1 SingleQuadParticle、Particle 与 ParticleEngine.render；javap 核实 LevelRenderer.getLightColor 签名。
- CustomParticle 改用四参数位置构造器，速度仍由原有 setParticleSpeed 设置，不引入原版速度构造器的随机扰动；光照覆盖与 ribbon 调用改为 getLightColor。保留预览全亮、逐层 emissive、锚点世界光照和普通粒子原版回退。修正 SingleQuadParticleMixin 的方法/类换行。
- Java 21 前台 common:compileJava（build.log）退出 1：1177 个错误；本轮构造器/光照诊断消失，CustomParticle 仍有旧 QuadParticleRenderState、Layer、extract/getLayer 与缺失 getRenderType 的诊断。git diff --check 通过；实际粒子与预览 MANUAL/PENDING。
- 原版 ParticleEngine.render(LightTexture,Camera,float) 只遍历固定 RENDER_ORDER，ParticleRenderType.begin 返回 null 时不会调用粒子 render；不能简单返回 CUSTOM 就声称自定义材质可用。下一步完整实现材质/顶点输出、全局作者层顺序、ribbon 与预览捕获，再一起接回引擎；须核对 NeoForge patched 重载与 frustum。
- M3 未完成；分支 1.21.1 / HEAD af23872，累计修改未提交/推送，未跟踪配置保留。未开始 M4/M5。可靠额度接口显示 10080 分钟池已用 8%，未触发交接阈值。

## 自定义粒子立即绘制管线 WIP（2026-11-01）

- 本轮继续 M3；CodeGraph 查询 CustomParticleLayer/Pipelines/Runtime、RibbonRenderState、PreviewParticleFrame、旧 ParticleGroup/QuadParticleRenderState mixin 与测试。minecraft-dev 核实 vanilla / NeoForge 21.1.185 ParticleEngine.render、tick、RenderStateShard；javap 确认 tick 的 Queue.add 调用。
- CustomParticlePipelines 使用有界 Pipeline(layer,blend) + 声明级 Material，延迟创建 1.21.1 RenderType，世界输出 PARTICLES_TARGET、预览 MAIN_TARGET；保留 PARTICLE shader/格式、alpha/additive/opaque（additive RGB SRC_ALPHA/ONE，alpha ONE/ONE_MINUS_SRC_ALPHA）、深度测试且不写深度。材质不进入永久纹理缓存，绑定不调用 setFilter，保留 FiguraTexture 的 blur/clamp。
- CustomParticle.extract 现在同步输出材质和原版 super.render 的顶点 writer，保留逐层 UV/颜色/自发光、预算与 frame texture lease；原版 getRenderType=NO_RENDER 仅承担引擎 ticking，不承担绘制。实际几何由引擎新增 renderWorld 钩子绘制，未禁用粒子。
- RibbonRenderState 保留不可变 mesh/layers、相机相对顶点和 writeLayer，移除不存在的 GPU prepare/submit；PreviewParticleFrame 捕获同一顶点路径，稳定按作者层序排序，每批显式 flush；预览 Camera 改 getPosition，删除无生产调用的 SubmitNodeCollector.submit 方法与逐顶点 lineWidth。
- CustomParticleRuntime.renderWorld 排除预览/旧世界，先分配 billboard 再 ribbon 的面预算；拖尾保留视锥与共享 vertex quota。专属临时 BufferSource 不冲刷全局实体队列，世界绘制前去除 model-view 相机平移并 finally 恢复；Iris shadow pass 跳过绘制。
- 公共 ParticleEngineMixin 直接钩住三参数 render；NeoForge 新增专用 ParticleEngineMixinNeoForge，钩五参数重载并尊重 translucent predicate/frustum，公共 wrapper 跳过 NeoForge，避免重复。两者静态 analyze_mixin 各自在 vanilla/patched 源码上 valid 无错误警告；不再靠 require=0 隐藏缺失目标。NeoForge 新文件未跟踪，后续提交必须纳入。
- 原 ParticleGroupMixin 的挤出清理迁入 ParticleEngine.tick 的 Queue.add WrapOperation：释放被挤出 CustomParticle 的纹理/预算/光照；tick TAIL 清理死亡 particleMap 条目。删除旧 ParticleGroupMixin、QuadParticleRenderStateMixin 和注册，后者的相机/层序职责已在新直接路径实现。
- 两轮 Java 21 前台 common:compileJava 均退出 1，最终 1074 错误（build.log）。本轮六个粒子实现文件和公共引擎 Mixin 无编译诊断；这不等于全模块通过。JSON 解析与 git diff --check 通过。NeoForge Java 编译仍受 common 阻断，实际 Mixin 应用、材质混合、层序/遮挡、Fabulous/Iris、拖尾/预览/队列挤出全部 MANUAL/PENDING。
- 未完成：现有 CustomParticleNative/Layered、CreatedTrail、ParticleMotion、AnchorTrailNative、PreviewParticleNative、OutfitParticleFaceBudget 等测试仍引用 QuadParticleRenderState/SingleQuadParticle.Layer/ParticlesRenderState/extractRibbons 或旧队列钩子，需要迁移到真实 VertexConsumer 捕获和新的引擎路径；尚未编译或运行这些 smoke，不能声称通过。不要为兼容测试重新加入旧 MC 状态 API。
- 下一步建议：先继续粒子动态光照的 ParticleWorldLightMixin/ParticleLightRegionAccessor/ParticleDynamicLights（仍有编译诊断），并迁移上述粒子测试；随后按 build.log 继续 M3 剩余渲染绑定。GUI/M4 与 GPU/M5 保持原计划顺序，真实窗口验证后续执行，严禁 offscreen。
- M3 未完成；分支 1.21.1，HEAD af23872，累计修改未提交/推送；原 .codex/.mcp.json/.omc 未动。新增本轮文件是 neoforge/src/main/java/top/aihmc/omnimorph/mixin/neoforge/ParticleEngineMixinNeoForge.java。继续遵守阶段通过后才提交/推送的检查点要求，未把错误减少当阶段验收。

## 粒子动态光照绑定 WIP（2026-11-01）

- CodeGraph 读取 ParticleDynamicLights、ParticleWorldLightMixin、ParticleLightRegionAccessor、ParticleEntityLightMixin 及 ParticleLightNativeSmokeTest；minecraft-dev 核对 vanilla/patched RenderChunkRegion、LevelRenderer.getLightColor 与 EntityRenderer.getPackedLightCoords；javap 核实 renderLevel 与 LevelRenderer 四参数构造器。
- 世界光照接口 BlockAndLightGetter 改为 1.21.1 BlockAndTintGetter；地形混入改为 getLightColor 的三参数具体重载（两参数原版调用该重载，避免重复注入）。每帧更新从 extractLevel 改到 renderLevel HEAD，完整接收 1.21.1 参数。
- region accessor 改到 RenderChunkRegion，level 字段返回类型必须是 Level（不能错误声明为 ClientLevel）；工作线程仍只读取现有 immutable terrainPublished，原共享容量、插值实体快照、tick 地形快照、旧/新区块 dirty 和外部提供者逻辑未改。
- ParticleEntityLightMixin 无需改动；实体光探针 getPackedLightCoords 和 getLightProbePosition 在 1.21.1 保留。ParticleLightNativeSmokeTest 同步代理接口、反射方法参数和记录 renderer 构造器，原有光照/生命周期断言保留，未声称测试运行。
- 三个光照 Mixin 在 vanilla 与 NeoForge patched 上各自 analyze_mixin valid 无错误警告。最初工具无法解析拼接注解字符串，改为同义单个描述符字符串后通过，并用原版源码/javap 交叉核对，未改变注入目标含义。
- Java 21 前台 common:compileJava 退出 1：1067 个错误（build.log）；三个修改的生产光照文件无编译诊断。git diff --check 通过；测试编译/运行仍受 common 和其余旧 API 测试阻断。实际地形/实体照明、区块重建、移动/禁用/退出清理、LambDynamicLights 联动均 MANUAL/PENDING。
- M3 未完成；分支 1.21.1 / HEAD af23872，累计修改仍未提交/推送，原未跟踪配置保留。本轮修改三个生产光照文件与 ParticleLightNativeSmokeTest；没有新阶段或范围扩展。
- 下一步：迁移 CustomParticleNative/Layered、CreatedTrail、ParticleMotion、AnchorTrailNative、PreviewParticleNative、OutfitParticleFaceBudget 等旧状态测试到真实顶点路径；继续清理 M3 剩余渲染诊断。全部 smoke 待 common 可编译后前台验证，GUI/M4、GPU/M5 仍按原计划顺序。

## 粒子原生顶点测试迁移 WIP（2026-11-01）

- 本轮推进 M3 测试兼容；CodeGraph 查询 CustomParticleNative/Layered、CreatedTrail、ParticleMotion 测试；minecraft-dev 核对 SingleQuadParticle.renderRotatedQuad 的真实顶点顺序，javap 核对 VertexFormat.getOffset、MeshData、ClientLevel/ParticleEngine 构造器和 RenderType 深度写字段。
- 新增未跟踪测试辅助类 ParticleVertexCapture：同步消费生产 CustomParticle.extract writer，使用真正 1.21.1 BufferBuilder/DefaultVertexFormat.PARTICLE，解析原生 MeshData 的位置、UV、颜色和 light；按 format 元素偏移读取，不构造旧 QuadParticleRenderState，也不伪造原版顶点转换。
- CustomParticleNativeSmokeTest 的中心/UV/light/纹理和 negotiated layer count 断言迁到真实顶点；挤出测试改为调用生产 ParticleEngineMixin 的 Queue.add wrapper，核对原始队列插入结果和真实 particleMap；保留 owner/resource reload/world reset 清理断言。FixtureCamera/ClientLevel 构造器改为 1.21.1；删除不存在的 ParticleResources 测试接口。
- CustomParticleLayeredSmokeTest 的 UV/tint/light 使用原生缓冲捕获，mixed-blend depth-write 检查使用真实 RenderType 状态；跨粒子层序测试调用生产 PreviewParticleFrame.extract/draw，将五个 author layer 写到真实 BufferBuilder 并检查请求材质的顺序，替换已删除旧 QuadParticleRenderStateMixin 的反射测试。无 chunk 时非自发光现在按 1.21.1 Particle.getLightColor 返回 0，不能沿用 26.1 无 chunk sky 满亮断言。实际 GPU blend factors/遮挡/像素仍待真实运行验证。
- ParticleMotionNativeSmokeTest 的 360 度跨界插值改检查原版写出的顶点边方向（错误的反向 180 度会失败），不再读取不存在的提交 quaternion 字段；CreatedTrailParticleSmokeTest 以原生顶点中心和边长检查位置/尺寸/UV/light，保留作者行为与资源断言。
- 验证：临时、被忽略的 build/tmp/particle-capture-compile.init.gradle 注册仅编译 CustomParticlePipelines + ParticleVertexCapture 的任务；Java 21 前台 :common:compileParticleVertexCapturePort 退出 0，BUILD SUCCESSFUL。日志保存在 build/reports/porting/particle-capture-compile.log，当前 build.log 也是该隔离编译日志。此 PASS 仅覆盖两份文件的真实 1.21.1 API 编译，不代表 common、修改的四个 smoke 或整个测试套件通过。
- 未重新执行全模块编译（本轮仅测试变动）；上一轮 full common compile 仍失败 1067 错误，详见上一检查点。四个迁移 smoke 尚未编译/运行，原有 native 测试设备还依赖新版渲染 API，需要随 1.21.1 测试基座适配继续处理。git diff --check 通过；目标四个文件中旧 QuadParticleRenderState/Layer/ParticleResources/ParticleGroupMixin 引用搜索为空。
- 下一步继续 AnchorTrailNative、PreviewParticleNative、RibbonNative、OutfitParticleFaceBudget 等剩余旧状态测试，保持覆盖而非删除断言；随后继续 M3 生产渲染诊断。必要时将世界几何抽取与提交分离以保留原测试的无 GL 几何验证，但不得增加产品特殊测试路径或启用 offscreen。
- M3 未完成；分支 1.21.1 / HEAD af23872，累计工作区未提交/推送。新 ParticleVertexCapture.java 后续提交必须纳入；原未跟踪 .codex/.mcp.json/.omc 保留。可靠额度池（10080 分钟）已用 8%，未触发 Luna 交接。真实游戏验证均 MANUAL/PENDING。

## 拖尾/预览/面预算测试迁移 WIP（2026-11-01）

- CodeGraph 查询 RibbonNativeSmokeTest、OutfitParticleFaceBudgetSmokeTest、OutfitComposition.Frame.draw、YsmEntityFrame；minecraft-dev 读取 1.21.1 MultiBufferSource，核实直接 VertexConsumer 写入接口。
- CustomParticleRuntime 将世界几何抽取拆为包内 extractWorld，生产 renderWorld 调用同一方法再绘制；旧世界/预览筛选、Iris shadow 跳过、billboard-before-ribbon 面预算顺序、视锥与作者层序保持在该唯一路径，没有新增产品测试分支。空 frame 不再创建绘制缓冲/修改 GL 矩阵。
- RibbonNativeSmokeTest 改调用生产 extractWorld 并用 PreviewParticleFrame.draw 写原版 BufferBuilder，检查剔除、UV/light、旋转、冻结重放、原共享顶点/轨迹容量、离线清理。以实际 4/8 顶点区分单/双层 ribbon，非自发光无 chunk 期望改为原版返回 0；不再使用旧 submit/prepare/EngineHook。
- AnchorTrailNativeSmokeTest 同步改为生产 world frame 和真实顶点重放，保留 Lua/controller history、目标丢失、瞬移、清理和独立/共享槽位断言；PlayerModel 包与 ClientLevel 构造器、Camera.getPosition、OutfitComposition.draw 的 YsmEntityFrame 参数一并对齐 1.21.1。
- PreviewParticleNativeSmokeTest 改为实际 frame.draw 材质请求序列（作者层 0/0/1）而非新版 submit node；保留旋转/反射/冻结数据、私有预算和生命周期断言。世界抽取现在包含健康 live Billboard（4 顶点），断言预览 Ribbon 不混入世界帧。
- OutfitParticleFaceBudgetSmokeTest 使用公开测试辅助类 ParticleVertexCapture 的原生缓冲捕获/count；保留模型/粒子的同一面账本、权限和实例共享断言。ParticleVertexCapture 仅测试源，新增 quadCount 供跨测试包复用。
- 这四个文件及粒子/outfit 测试目录中的 QuadParticleRenderState、ParticlesRenderState、SingleQuadParticle.Layer、extractRibbons、ParticleResources、ParticleGroupMixin、QuadParticleRenderStateMixin 引用搜索为空；这仅证明相关符号迁移，不证明全部测试可编译。
- Java 21 前台 common:compileJava 退出 1，仍 1067 个错误，build.log 恢复为全模块失败日志；CustomParticleRuntime 无诊断。git diff --check 通过。上述 smoke 尚未编译/运行，真实 engine Mixin hook/绘制提交未在运行时覆盖，不能把几何抽取验证说成端到端通过。游戏内粒子、GUI、GPU/Iris 等均 MANUAL/PENDING。
- M3 未完成；分支 1.21.1 / HEAD af23872，累计工作区未提交/推送，原未跟踪配置保留。下一步继续 build.log 的 M3 生产渲染诊断，并适配现有新版 native 测试基座；待 common/testClasses 可构建后运行原 smoke。M4/M5 不提前启动，完成阶段验收后才提交/推送。

## 剩余模型/动画绑定清点 WIP（2026-11-01）

- CodeGraph 读取 AvatarRendererMixin、已迁移 PlayerRendererMixin、FiguraRenderer、Avatar.MolangContext、ClientAvatarStateAccessor；minecraft-dev 核实 1.21.1 renderRightHand/renderLeftHand 都委托 PlayerRenderer.renderHand。javap 确认 Entity.walkDist 是公开字段；编译核实 getMoonPhase 与 NBT byte[] API。
- 删除未注册且无人调用的旧 AvatarRendererMixin（它仍依赖 AvatarRenderer/SubmitNodeCollector）；YSM 第一人称职责已由当前 PlayerRendererMixin.renderHand 的 runtime.renderFirstPersonArm、动画帧、装扮预算路径承担，未删除手部功能。旧文件没有 mixin 注册，故无对应注册变动。
- FiguraRenderer 的纹理 NBT 读取去掉新版 Optional.get：1.21.1 getByteArray 直接返回 byte[]。Avatar 物品 Lua 回调复制 PoseStack 时分别复制 pose/normal，保留调用方矩阵隔离；动画查询从公开 acp.walkDist 读取距离，从 level.getMoonPhase 读取月相。删除 ClientAvatarStateAccessor 与注册（其余 cloak/bob getter 无调用，1.21.1 不存在 ClientAvatarState 类）。
- Java 21 前台 common:compileJava 退出 1：1048 个错误（build.log），本轮 texture/pose/walk/moon 诊断消失；FiguraRenderer 无诊断。Avatar 剩余诊断是 GUI portrait 的 GuiGraphicsExtractor/新版 Lighting/GlStateManager 引用，按原计划留 M4。GUI/preview 与 M5 GPU 仍占大量错误，不能以剩余错误减少宣称 M3 验收通过。
- mixin JSON 解析、引用清点和 git diff --check 通过；实际纹理/物品 Lua 回调/行走与月相查询、第一人称手部均 MANUAL/PENDING。原 smoke 与两加载器完整运行尚未通过。
- M3 未完成，1.21.1 / HEAD af23872；累计修改未提交/推送，原未跟踪配置保留。下一步继续 M3 native 测试基座及尚未验证的直接渲染路径；M4 GUI 与 M5 GPU 依赖是全模块构建剩余主要障碍，需要按计划处理，不能提前标记本阶段完成或借删除 GUI/GPU 功能让 common 通过。

## M3 验收依赖核查 / 待确认检查点调整（2026-11-01）

- 现有计划的 M3 验收要求 CPU smoke（ysmBoneMatrixSmokeTest、ysmDeferredPoseSmokeTest、gpuRigidDataSmokeTest）及真实单机/多人渲染。common/build.gradle 中这三个任务都 dependsOn testClasses，进而依赖同一 sourceSet 的 common:compileJava；当前真实全模块编译失败 1048 错误。GUI/M4、GPU/M5 的缺失 API 同在 common，无法仅凭 M3 文件无诊断就完成原验收。
- 原生材质/粒子测试基座 YsmMaterialNativeSmokeTest 还直接反射 RenderSystem.DEVICE 并使用 PublicationDevice（新版 GPU API），本身也需要 1.21.1 适配。禁止以删除 GUI/GPU 源码、排除生产 sourceSet、复制替代实现或缩减原验收来宣称 M3 完成。隔离编译可以提供局部证据，不能替代计划要求的真实客户端与完整 smoke。
- 本轮仍在授权的 M3 范围内迁移 YsmDeferredPoseSmokeTest 的睡姿 root 验证：使用原版 ArmorStand 子类提供 getPose/getBedOrientation/getDimensions，生产 YsmRenderer.applyEntityRotation 读取真实 LivingEntity 参数，保留四个床方向、两种 eyeHeight、父位移、无床方向和站姿矩阵断言。javap 核实 getEyeHeight(Pose) 通过 getDimensions(Pose).eyeHeight 读取（方法 final，不应伪覆盖）。未运行测试；git diff --check 通过。其余测试与全模块状态不变，不能声称新增 PASS。
- 待用户确认的具体建议：将 M3–M5 的 1.21.1 兼容实现/构建恢复合为一个检查点，继续原有功能范围，GPU 仍按原 MVP 方案默认关闭并保留 CPU 路径；共同恢复 common/testClasses、双加载器构建后按原功能矩阵验证，再提交推送。M6 发布验收仍独立。这样解决同一 common sourceSet 的阶段依赖循环，不减少测试或功能。
- 用户确认前不启动 M4/M5，不修改正式计划的阶段规则、不提交不通过的阶段。可继续独立 M3 测试源码迁移，但不能无限以重复失败构建冒充进展。需要确认的依据是 AGENTS.md “不要开始下一阶段，直到当前阶段 checkpoint 完成”和计划 M3 的完整 smoke/实机验收要求，两者与已证实的共同编译依赖形成阻塞。
- 当前 1.21.1 / HEAD af23872，累计修改未提交/推送；本轮新增修改 YsmDeferredPoseSmokeTest。原未跟踪文件保留。M3、完整编译、smoke、客户端/服务器运行均未完成。

## M3 动作状态测试继续迁移（2026-11-01，检查点调整仍待用户确认）

- 没有收到 M3–M5 合并检查点的批准；未启动 M4/M5。继续独立 M3 测试源码适配，不重复运行已知被 common GUI/GPU 阻断的全模块构建。
- CodeGraph 核对 YsmBoneMatrixSmokeTest、YsmBuiltinControllerSmokeTest、YsmNativeStateSmokeTest、生产 updateNativeStateMachine 和 YsmPlayerActionState.snapshot。骨骼差分测试本身已调用当前实际生产矩阵方法，无须改动，不能因此声称已运行通过。
- YsmNativeStateSmokeTest 的 LivingEntityRenderState 改为实际 YsmEntityFrame；每次 scenario setup 后构造不可变帧的 walkAnimationSpeed，保留参考动作表、优先级、Lua nativeAnimation/actionState 一致性断言。夹具继承原版 ArmorStand，避免 1.21.1 LivingEntity 新增抽象装备接口问题，原 resolver 输入 overrides 保持。
- YsmBuiltinControllerSmokeTest 同步反射方法描述符；原 null entity 会在当前生产 updateNativeStateMachine 的 state/entity null guard 直接返回，根本不能证明 idle/custom/idle 的实际选择。改为复用动作表测试提供的非 null grounded/standing 实体夹具，并进行原版 Bootstrap 初始化；保留 native/controller 来源互斥与重新进入 builtin 时新时钟断言，不改生产逻辑以迁就测试。
- git diff --check 通过。两个测试尚未编译/运行；最后全模块编译仍为上一轮1048错误，无新增测试 PASS。M3 验收、真实客户端/服务器运行和全套 smoke 未完成。
- 当前 1.21.1 / HEAD af23872，累计修改未提交/推送，原未跟踪文件保留。本轮仅修改两个动作/控制器 smoke 文件；后续仍可适配独立 M3 测试。正式 M4/M5 实施仍等待前述 AGENTS 阶段规则例外确认，不能把自动续行当批准。

## M3 头部/同步/回放测试帧迁移（2026-11-01）

- 阶段调整仍无明确批准，继续 M3 独立测试变动，未启动 M4/M5。CodeGraph 核对 YsmFunctionContractSmokeTest、YsmSyncModelSmokeTest、YsmSyncConnectionSmokeTest、YsmIdlePerformanceProbe、YsmSubEntitySmokeTest 及生产 updateYsmHeadRotation/updateNativeStateMachine。
- 头部 query 测试从旧 state.yRot/xRot 改为 YsmEntityFrame.netHeadYaw/headPitch，保留反轴、85度夹角、Avatar隔离/reset 和普通 Figura query 回退断言；生产 head capture 明确允许 entity=null 并使用帧值，未更改该行为。
- 同步模型/连接测试使用新的不可变 ageInTicks 帧（20/22、300/301），保留远端控制器时钟推进、控制消息不回退动画、owner/observer 权限与姿态一致性断言。短原生 idle 回放子测试改传动作解析实体夹具并归零 ground_speed；避免生产 null entity guard 跳过 idle/walk 选择，保留持续 idle 与切换 walk 的实际动画断言。
- 子实体 controller callback 测试仅替换空帧类型，保留 null subject 的原有 domain-event 场景；CPU idle 回放探针逐帧构造 YsmEntityFrame，删除 1.21.1 VertexConsumer 不存在的 setLineWidth。探针仍只输出 CPU 数据，不提供GPU/FPS/实机结论。
- 新发现并保留待办：YsmAstronautVisibilitySmokeTest 虽通过改变 walkAnimationSpeed 声称覆盖 idle→walk，但 updateAnimations 仍传 null entity，当前生产原生动作方法会提前返回，不能据此证明原生状态切换。需要在 native 测试基座中提供足够的真实实体输入，再验证 parallel1 可见性；本轮未以换帧名掩盖此覆盖缺口。
- git diff --check 通过，五个修改文件的 LivingEntityRenderState 引用清点为空。未进行测试编译/运行，未产生新增 PASS；生产源码未改，不重复运行已知被 GUI/GPU 阻断的 full build。最后 common 编译仍失败1048错误。
- M3 未完成，分支1.21.1 / HEAD af23872；累计工作区未提交/推送，原未跟踪文件保留。本轮修改上述五个测试文件。下一步仍可继续独立测试适配与覆盖缺口修复；M4/M5依赖实施等待用户对检查点调整的明确回复。

## 用户已批准 M3–M5 合并检查点 / GUI 入口迁移（2026-11-01）

- 用户明确回复“允许合并 M3–M5 检查点（推荐）”。正式计划 §5 已记录：M3/M4/M5 兼容实现与验收/提交门槛合并，功能/测试范围不减，GPU 仍按原 MVP 默认关闭并保留 CPU/Java 路径，M6 独立。此前等待批准、禁止启动M4/M5的历史状态不再适用，不再重复请求该授权。
- 原先计划尝试隔离执行 GpuRigidDataSmokeTest，CodeGraph 确认它还调用 GpuShaderProgram（M5 API），不能在未迁移该类时原样运行，未删除 shader 断言或宣称通过；用户此时批准共同实现，因此转入实际 GUI 构建恢复。
- CodeGraph 核对 FiguraWidget/GuiGraphicsAccessor 及入口，minecraft-dev 核对1.21.1 GuiGraphics 与 Renderable.render(GuiGraphics,int,int,float)。58个生产源文件精确迁移 GuiGraphicsExtractor 类型、GUI render入口/对应调用；保留实体 renderer 的 extractRenderState 调用待独立重写，未用 GUI 机械替换错误覆盖实体路径。GameScreen 单参数 cell 绘制调用随后同步改为 render。
- 本轮是 GUI API 入口机械迁移，尚未完成 PoseStack（原 Matrix3x2f）、fill/blit/drawString 重载、输入事件、屏幕/模型预览和原 mixin 的行为适配。不能据此宣称 GUI 可用。
- Java21前台 common:compileJava 退出1，902个错误（build.log）；其后仅修正GameScreen已重命名cell的调用，无再次构建。git diff --check通过。真实窗口、smoke及完整双平台构建均未通过。
- 当前1.21.1 / HEAD af23872，累计修改未提交/推送，原 .codex/.mcp.json/.omc 保留；变动文件清单临时存于被忽略的 build/tmp/m4-gui-api-files.txt。根交接手册顶部状态已更新为已批准的合并检查点，避免下个Agent误读旧等待状态。
- 下一步继续GUI基础矩阵/绘制接口与旧状态preview管线重建，随后M5默认关闭适配和完整编译/原smoke。合并检查点的验证全部达到要求后再提交、推送、核对远端HEAD；不以局部错误下降替代验收。

## GUI 基础控件绘制与入口（2026-11-01，M3–M5 合并检查点）

- CodeGraph 核对 Label/Button/UIHelper，minecraft-dev 核对 1.21.1 Button 与 Screen 点击链接路径，并参照本地上游1.21实现的基础API段。
- Label 改用原版 PoseStack pushPose/translate(x,y,0)/scale/popPose，保留原缩放/对齐/alpha/hover样式；点击转为(mouseX,mouseY,button)并使用 screen.handleComponentClicked，保留原hover/click检查。
- Button 改用原版鼠标参数、无RenderPipeline参数的blit、renderWidget背景和无参数onPress；同步23个生产文件的renderWidget实现/调用与gui.drawString入口，避免派生控件仍覆盖不存在的extractContents。
- Java21前台common:compileJava退出1，853错误（build.log）；Label/Button无编译诊断，其他GUI矩阵/输入/预览和GPU兼容尚未完成。git diff --check通过；无完整smoke、无真实窗口验证，不能宣称控件可用。顶部最新编译状态已同步。
- 当前1.21.1 / HEAD af23872，累计修改未提交/推送。下一步优先统一旧鼠标/键盘事件签名及派生控件调用，再处理UIHelper文本/贴图与预览。继续已批准M3–M5范围，不重复申请阶段合并权限；M6保持独立。

## GUI 原生输入签名迁移（2026-11-01，M3–M5 合并检查点）

- CodeGraph 核对容器、TextField、BoneTreeWidget上下文菜单、LibraryRowList与AbstractPanelScreen；minecraft-dev核对1.21.1 GuiEventListener原生鼠标/键盘/charTyped签名。
- 结构化迁移22文件45个标准输入方法，保持方法体的焦点/拖动/控件命中/键值与modifiers转发。遇到自定义事件使用不自动替换，后续手动处理TextField坐标夹取、PermissionsScreen按钮onPress、AvatarControlsScreen的InputConstants.getKey(keyCode,scanCode)；骨骼树右键回调传目标和坐标，WardrobeScreen直接使用openBoneContext方法引用。
- 修复迁移产生的重复坐标局部声明，原LibraryRowList的Button局部变量改名actionButton，避免与原生button编号冲突；AbstractPanelScreen上下文菜单helper也改成原生参数，保留优先分发/父菜单处理/关闭逻辑。
- Java21前台common:compileJava退出1，728错误（build.log）；该次结果暴露的LibraryRowList同名局部与contextMenuClick参数错误已随后修正，无重复构建。git diff --check通过。实际点击/拖动/输入/快捷键、完整smoke、真实窗口仍MANUAL/PENDING。
- 尚存：WardrobeCloudList与AvatarWidget的全限定旧事件签名、输入Mixin、IME PreeditEvent及MultiLineEditBox新版builder；这些未被机械迁移掩盖，继续按原生1.21.1行为核对。GUI截图和真实行为必须待可构建后验证，不以源码迁移宣称通过。
- 当前1.21.1 / HEAD af23872，累计修改未提交/推送；原未跟踪配置保留。已批准合并检查点持续有效。下一步收尾旧输入事件与多行文本框，再继续GUI共用矩阵/贴图/预览和M5构建兼容。

## GUI 输入收尾与粒子图标查询（2026-11-01，M3–M5 进行中）

- 已核对实际文件：LibraryMultilineField 使用 1.21.1 原生 MultiLineEditBox 构造和背景/装饰覆盖点，保留文本、焦点及拖动调整高度；LibraryTagField、WardrobeCloudList、AvatarWidget 已移除旧输入事件依赖。
- 多行框使用原生文本/光标配色；1.21.1 无新版 PreeditEvent，保留原生提交字符输入。IME 组合预览及 CJK 实际显示未验证，不声称与新版一致。
- CodeGraph 核对 ParticleWidget/ParticleEngineAccessor，minecraft-dev 核对原生 ParticleEngine 的 spriteSets。ParticleWidget 改为调用已有 figura$getParticleSprite，消除对已删除 ParticleResourcesAccessor 的引用。
- Java 21 前台 common:compileJava 两次退出 1：先 710 错误，粒子图标修正后 707 错误，最新 build.log 为后者；本批输入控件与 ParticleWidget 未再出现编译诊断。这不代表完整编译或运行通过。
- git diff --check 通过。完整 smoke 和真实客户端 GUI/输入/预览仍 MANUAL/PENDING。当前分支 1.21.1，本地 HEAD 与本地 origin/1.21.1 引用均 af23872；本轮未提交或推送，也未联网核对远端。
- 下一步：继续 GUI PoseStack、文本/贴图、UIHelper 和预览 API，再完成 M5 默认关闭 GPU 的 CPU 回退兼容；合并检查点未验收，不启动 M6。原未跟踪配置和累计修改保留。

## 向导与权限列表原生 GUI API（2026-11-01，M3–M5 进行中）

- CodeGraph 核对 AvatarWizardList、SwitchButton、LibraryDropdown、AbstractAvatarWidget、PlayerPermPackElement；minecraft-dev 核对 AbstractButton.onPress() 和 Style.withFont(ResourceLocation)，javap 核对原生 GuiGraphics.blit 重载。
- 模型向导开关和模型库下拉框改用无参数 onPress，保留开关状态写回与分页选择逻辑；向导使用原生 drawCenteredString 和 PoseStack，平移补零 Z，矩阵在 finally 中恢复。
- 权限列表使用原生 PoseStack，保留二维缩放、命中坐标换算和绘制顺序；皮肤头部与帽子使用原生 blit 参数顺序，保留 8x8 区域到 32x32 输出；GlStateManager 使用 platform 包。收藏星号的字体样式直接使用相同 UI_FONT 资源位置。
- Java21前台 common:compileJava 两次退出1：699错误，随后690错误；最新 build.log 为690，本轮四个修改文件均未出现在编译诊断。完整编译仍失败，不能据此宣称 GUI 运行正确。定向 git diff --check 通过。
- 当前分支仍1.21.1，累计修改未提交/推送；M3–M5尚未验收。完整smoke、真实客户端及UI操作/截图仍MANUAL/PENDING。
- 下一步继续剩余GUI矩阵、UIHelper贴图/文本与实体预览迁移，随后M5兼容；不扩大GPU恢复范围，不启动M6。

## 屏幕层与世界内菜单绘制迁移（2026-11-01，M3–M5 进行中）

- CodeGraph 核对 AbstractPanelScreen、GameScreen、PermissionsScreen、SliderWidget、ContextMenu 和 PopupMenu；minecraft-dev 核对 GuiGraphics 的 PoseStack/flush，javap 核对 Screen.renderBlurredBackground(float)。
- AbstractPanelScreen 使用原生 flush 分隔背景/内容，并以 Z=500 绘制上下文菜单，finally 恢复矩阵；无模糊背景覆盖改用原生方法签名。GameScreen 网格和 PermissionsScreen 标题迁到 PoseStack，保留二维缩放和零Z平移。
- 权限滑块匿名控件改为 renderWidget；精细权限开关改用原生 onPress，先调用 SwitchButton 切换状态再更新列表。帮助图标保留相同字体资源。
- PopupMenu 使用原生矩阵、贴图、字体资源及批次提交，保留按钮/徽章/权限文本和尺寸；过近退出发生在入栈前，正常绘制用 finally 恢复。纹理常量采用 ResourceLocation。
- Java21 前台 common:compileJava 依次退出1：680、670、666错误；最新 build.log 为666。本批四个文件不再出现编译诊断。定向 git diff --check 通过。完整构建/smoke未通过，UI层级、深度、贴图与交互均需真实客户端验证，仍MANUAL/PENDING。
- 分支1.21.1，累计修改未提交/推送，M3–M5尚未验收。下一步继续ActionWheel及剩余矩阵/共用UIHelper和实体预览，再完成M5默认关闭GPU的CPU兼容；M6独立。

## ActionWheel 原生绘制迁移（2026-11-01，M3–M5 进行中）

- CodeGraph 核对 ActionWheel 当前实现，并参考本地1.21原生实现；minecraft-dev 核对 GuiGraphics.setColor 的实际颜色调制行为。
- ActionWheel 主轮盘、模型库预览与文本绘制改用 PoseStack；二维平移补0Z、缩放保留Z=1，轮盘片旋转使用 Axis.ZP.rotationDegrees。分页指示/标题使用原生Z层级；保留装扮动作轮、YSM按压释放、预览状态恢复、选择与分页算法。
- 迁移原生 blit 参数顺序、renderItem/renderItemDecorations；轮盘片及操作图标用原生颜色调制保留选中色，绘制前提交已有批次，finally 恢复白色及片段矩阵。未移除自定义动作纹理或物品装饰。
- Java21前台 common:compileJava 退出1，651错误（build.log）；ActionWheel 未再出现编译诊断。定向 git diff --check 通过。完整构建/smoke仍未通过；轮盘布局、选中色、物品遮挡、预览与真实输入仍MANUAL/PENDING。
- 分支1.21.1，累计修改未提交/推送。下一步继续剩余GUI矩阵、UIHelper和实体预览，随后M5默认关闭GPU的CPU回退兼容；M3–M5合并检查点未完成，M6不启动。

## 实体预览控件与共用贴图迁移（2026-11-01，M3–M5 进行中）

- CodeGraph 核对 EntityPreview、UIHelper，minecraft-dev 核对原生GuiGraphics贴图/精灵接口，并参考本地1.21实现的九宫格顶点路径。
- EntityPreview 改用原生PoseStack及缺省模型动画贴图blit，保留本地/库预览会话、独立Figura预览、装扮作用域、选中骨骼与交互入口。
- UIHelper 背景和九宫格改为原生POSITION_TEX顶点/BufferUploader；九宫格九块区域仍单批次，保留小数坐标、UV及背景Z=-999。普通/半幅贴图和精灵改用原生blit，移除这部分新版GUI渲染状态依赖。
- renderWithoutScissors 提交前后批次并用物理disableScissor暂时解除裁剪，finally通过临时逻辑条目恢复父裁剪。真实嵌套裁剪行为仍需客户端验证。
- Java21前台common:compileJava退出1，628错误，日志build.log。EntityPreview未再出现编译诊断；UIHelper实体绘制、lighting、字体与其余GUI调用仍未完成，不能宣称该类编译通过。定向git diff --check通过。
- 完整构建/smoke及真实预览、贴图、裁剪验证仍未完成；真实客户端验收MANUAL/PENDING。分支1.21.1，累计修改未提交/推送。
- 下一步继续UIHelper实体绘制与字体、剩余GUI矩阵，再完成M5默认关闭GPU的CPU回退兼容。M3–M5合并检查点持续进行，M6独立。

## UIHelper 字体与预览光照（2026-11-01，M3–M5 进行中）

- CodeGraph 核对UIHelper文本调用与依赖；minecraft-dev核对1.21.1 Font.drawInBatch8xOutline源码（包含逐字形shadowOffset描边及正文）和Lighting原生接口。
- renderOutlineText改用原生drawInBatch8xOutline，保留文字/描边adjustColor、字体样式、矩阵、全亮光照及裁剪；提交前后GUI批次。移除只有该方法使用的新版OutlinedGuiTextRenderState及其GlyphSource/PreparedText依赖，没有删去描边功能。
- 提示框使用PoseStack与Z=999，finally恢复矩阵，保留原位置夹取、换行、背景及低动态效果设置；居中滚动文本使用drawCenteredString。箭头与loading图标使用相同ResourceLocation字体资源。
- useFiguraLighting改用原生flat-item准备及双光源setShaderLights，保持(-0.2,-1,1)、(-0.2,0.4,0.3)归一化方向；移除不存在的GPU UBO分配。物品/实体库存光照调用对应静态Lighting方法。这里是原生GUI光照迁移，不是恢复M5 GPU加速。
- Java21前台common:compileJava两次退出1，602后584错误，最新build.log为584。定向git diff --check通过。UIHelper实体drawEntity仍为新版状态提取路径，尚不能编译；不要将文本部分迁移当作预览可运行证明。
- 完整构建/smoke和真实字体、描边、提示层级及光照均未验证，客户端MANUAL/PENDING。分支1.21.1，累计修改未提交/推送。
- 下一步将UIHelper.drawEntity迁到原生EntityRenderDispatcher，并保留LibraryPreviewBinding的当前预览作用域，再接续独立Figura预览及剩余GUI/M5兼容。M3–M5未完成，M6独立。

## 原生实体 GUI 绘制入口（2026-11-01，M3–M5 进行中）

- CodeGraph 核对UIHelper、LibraryPreviewBinding、EntityFrame、OutfitRenderBinding及LivingEntityRenderer/Dispatcher mixin；minecraft-dev核对1.21.1 InventoryScreen原生同步实体绘制，javap核对dispatcher相机、阴影及hitbox接口/字段。
- UIHelper.drawEntity使用原生PoseStack（Z=250、负Z缩放）、旋转、offset、裁剪、EntityRenderDispatcher.render、全亮光照和同步GUI批次。保留paperdoll/GUI/库存模式的旋转、可见性、配置和位置行为；补齐已有调用方需要的无显式裁剪参数重载。
- 绘制前以非world EntityFrame捕获当前预览/装扮并prepare，供已迁移LivingEntityRenderer读取，避免把预览当世界装备。finally恢复实体head/visibility、avatar模式、paperdoll/fire/scale、hitbox、阴影、相机、矩阵及裁剪。
- 新增EntityFrame.clearPrepared，仅清理相同未消费frame；失败仍活跃的该frame也结束。新duck EntityRenderDispatcherAccessor由现有已注册Mixin实现，读取阴影原值用于准确恢复，不新增Mixin注册。
- Java21前台common:compileJava退出1，557错误（build.log）；本批UIHelper/EntityFrame/EntityRenderDispatcherMixin没有编译诊断。随后仅整理一个import，未重复构建。定向git diff --check通过。整体编译仍失败，未运行完整smoke或客户端，不代表预览可用。
- 仍需接续：新版GuiEntityRenderer/独立Figura预览与GuiRenderer生命周期清理；预览粒子与骨骼高亮路径；LivingEntityRenderer的OutfitNativeRenderer硬编码RENDER需按GUI frame核对。真实渲染、装扮、裁剪、异常恢复均MANUAL/PENDING。
- 分支1.21.1，累计修改未提交/推送，M3–M5合并检查点尚未完成；继续上述原范围内迁移，M6独立。

## 预览装扮模式与帧清理入口（2026-11-01，M3–M5 进行中）

- CodeGraph核对LivingEntityRendererMixin、OutfitRenderBinding、GuiRendererMixin、GameRendererMixin；minecraft-dev及javap核对1.21.1 GameRenderer生命周期。
- Figura底模、原版底模、YSM底模三条装扮绘制路径改用OutfitRenderBinding.renderMode(currentFrame)，不再固定RENDER；正常世界frame仍由现有binding返回RENDER，模型库GUI返回FIGURA_GUI。
- 已有GameRenderer.render WrapMethod在finally执行LibraryPreviewBinding/OutfitRenderBinding/粒子frame清理；沿用这一权威原生入口。关闭预览frame清理迁到GameRenderer.close HEAD。
- 删除目标类在1.21.1不存在的GuiRendererMixin并去除注册：其paperdoll绘制已由UIHelper原生同步路径承接，帧退休/关闭职责已在GameRenderer承接。独立Figura预览与其他旧GUI类仍待迁移，没有将它们的功能宣称完成。
- Java21前台common:compileJava退出1，537错误（build.log）；LivingEntityRendererMixin仍报AvatarLoadingRenderer.render缺少原生重载，属于尚未迁移的loading renderer。GameRendererMixin无编译诊断。本轮定向git diff --check通过，Mixin配置JSON可解析。
- 完整构建/smoke及真实GUI/装扮渲染验证仍未通过；真实客户端MANUAL/PENDING。分支1.21.1，累计修改未提交/推送。
- 下一步迁移独立Figura预览、预览粒子和高亮，以及AvatarLoadingRenderer，清理有对应原生承接的新版GUI绑定，再推进剩余M5。M3–M5未验收，M6独立。

## 模型加载提示原生绘制（2026-11-01，M3–M5 进行中）

- CodeGraph核对AvatarLoadingRenderer与AvatarLoadProgress；minecraft-dev核对1.21.1 EntityRenderer.renderNameTag的附件位置、相机旋转、缩放和两遍字体绘制。
- AvatarLoadingRenderer.render接收现有EntityFrame/原生PoseStack/MultiBufferSource/light，直接读取Player加载进度；保留仅玩家、隐藏实体、64格距离、panic及无世界/无进度过滤。
- 保留label算法：12段、已知总量百分比及未知总量三格移动动画。原生NAME_TAG附件加0.5名牌偏移再加0.45加载行间距，缺附件保留高度+0.6回退；使用原生透明背景/透视与正常两遍字体，finally恢复矩阵。
- Java21前台common:compileJava退出1，524错误（build.log）；AvatarLoadingRenderer与LivingEntityRendererMixin不再出现编译诊断。定向git diff --check通过；已有AvatarLoadProgressSmokeTest尚未执行，不能声明标签测试通过。
- 真实位置、遮挡、字体、预览及加载进度显示均MANUAL/PENDING；完整构建/smoke仍失败/未运行。可靠额度接口显示10080分钟池已用10%，不触发预警/交接。
- 分支1.21.1，累计修改未提交/推送。下一步继续独立Figura预览、粒子/高亮和剩余旧GUI绑定，再推进M5兼容。M3–M5检查点未完成，M6独立。

## 独立 Figura 原生同步预览（2026-11-01，M3–M5 进行中）

- CodeGraph核对LibraryFiguraPreviewRenderer/State、LibraryPreviewSession、OutfitNativeRenderer和YsmEntityFrame；minecraft-dev核对原生PlayerModel/HumanoidModel站立动画，javap核对PlayerSkin字段。
- LibraryFiguraPreviewState使用原生皮肤与不可变PlayerPose快照（frame、age、皮肤部件开关），不依赖不存在的AvatarRenderState/PiP接口。绘制时不查询/分发实体，显式恢复原生站立姿态、手臂idle bob、衣服部件和皮肤可见性，避免采入真实游泳/骑乘/用物品/攻击状态。
- LibraryFiguraPreviewRenderer不再继承不存在的PiP renderer；使用原生GUI矩阵、裁剪、光照和同步批次，保留wide/slim模型、Figura事件、原版底模、装扮预算与原版部件恢复。装扮使用站立YsmEntityFrame，粒子仍读取同一frame的LibraryPreviewBinding快照。
- LibraryPreviewSession.submitIndependentFigura直接提交上述原生绘制，保留原有是否启用/模型类型/实体可用检查、view偏移、binding capture和当前资源生命周期。选择骨骼由binding保留，PreviewBoneHighlight自身仍待迁移。
- Java21前台common:compileJava退出1，497错误（build.log）；本批三个文件无编译诊断。随后仅展开同值链式赋值为独立语句，未重复构建。定向git diff --check通过。
- 既有LibraryPreviewRuntimeSmokeTest还使用旧AvatarRenderState/PiP拦截，须迁移为原生实际顶点/绘制断言；本轮未运行，不得把新路径称为测试通过。完整构建仍失败，真实站立姿态、皮肤、装扮、粒子、裁剪与宽窄模型效果MANUAL/PENDING。
- 分支1.21.1，累计修改未提交/推送。下一步继续PreviewBoneHighlight、其他GUI实体路径与原生预览测试，再推进M5兼容。M3–M5未验收，M6独立。

## 预览骨骼高亮原生材质（2026-11-01，M3–M5 进行中）

- CodeGraph核对PreviewBoneHighlight、FiguraRenderTypes、ImmediateFiguraRenderer线框及调用边界；minecraft-dev核对原生RenderType线框/CompositeState结构。
- 高亮线框改用原生RenderType/CompositeState：POSITION_COLOR_NORMAL、LINES、原生线框shader、线宽1、透明混合、无剔除、无深度测试、仅写颜色、主目标。去掉不存在的逐顶点setLineWidth，保留原骨架段/边界框几何、预算、选中子树及坐标轴颜色。
- 半透明淡化读取原生CompositeRenderType的真实透明/剔除状态；已有混合材质保持原类型，只有无混合且有纹理的材质转换到相应剔除语义的entityTranslucent类型。未知非Composite类型保持原类型，不靠名称或sortOnUpload猜测混合。
- accesswidener增加本调用所需CompositeRenderType/CompositeState可访问类及state/transparencyState/cullState读取，未修改状态值；Loom重新应用后编译未报访问/字段错误。
- Java21前台common:compileJava先478错误（暴露Matrix4fc重载差异），复制成Matrix4f后477错误；最新build.log为477，高亮文件无诊断。定向git diff --check通过。整体构建仍失败，完整smoke未运行，真实线框深度/淡化/色彩/材质效果MANUAL/PENDING。
- 分支1.21.1，累计修改未提交/推送。下一步接续其他GUI实体/粒子路径和预览测试，再推进M5兼容；M3–M5合并检查点未完成，M6独立。

## GUI实体粒子桥接承接（2026-11-01，M3–M5 进行中）

- CodeGraph核对GuiEntityRendererMixin、GuiEntityRenderStateMixin、FiguraGuiEntityRenderer、duck、UIHelper、PreviewParticleFrame和LibraryPreviewBinding；minecraft-dev核对原生PoseStack变换。
- UIHelper.drawEntity在FIGURA_GUI同步实体绘制后，沿用同一EntityFrame与modelRotation提取预览粒子，使用同一实体绘制偏移矩阵和原生buffer绘制，随后提交。保留现有layer顺序/预算/粒子frame冻结逻辑。
- paperdoll标志按旧桥接语义只对非MINECRAFT_GUI启用，避免库存预览错误启用Figura预览的名牌/高亮行为；finally仍恢复原值。
- 移除已由原生UIHelper承接全部职责的旧GuiEntityRendererMixin/StateMixin、对应duck和FiguraGuiEntityRenderer，以及两条Mixin注册。另核对GuiRendererAccessor已无调用，删除它与注册；GuiGraphicsAccessor仍被头像/GUI入口使用，未删除，待迁移。
- Java21前台common:compileJava两次退出1，452后450错误，最新build.log为450；UIHelper无诊断。定向git diff --check通过，Mixin JSON可解析。完整构建/smoke仍未通过，真实GUI粒子、偏移、billboard、裁剪及模式隔离MANUAL/PENDING。
- 分支1.21.1，累计修改未提交/推送。下一步迁移Avatar头像和FiguraGui剩余PiP状态入口，再适配原生预览测试及M5。M3–M5合并检查点未完成，M6独立。

## 头像原生同步绘制（2026-11-01，M3–M5 进行中）

- CodeGraph核对Avatar.submitPortraitDraw、FiguraPortraitRenderer/State及FiguraGui；minecraft-dev核对原生GuiGraphics裁剪，并参考本地1.21 Avatar原生portrait路径。
- Avatar.submitPortraitDraw直接使用GUI PoseStack、屏幕坐标裁剪、modelScale、倒置轴、portrait偏移、原生光照及buffer绘制；仍受AVATAR_PORTRAIT/renderer/loaded门控。无自定义头像几何时返回false，权限行已有皮肤/帽子或UNKNOWN回退承接，不把空头像当绘制成功。
- renderHeadForPortrait保留PORTRAIT特殊部件及HEAD回退筛选，去掉PiP专用补偿2倍缩放；finally恢复allowPivotParts、paperdoll、dollScale及矩阵。HUD中的两处光照改为原生静态Lighting方法，GlStateManager改为platform包。
- 删除已由直接绘制及调用方fallback承接的FiguraPortraitRenderer/State；不再为头像使用不存在的GPU纹理/PiP状态/投影UBO。相关生产引用搜索无残留；FiguraGui仍有HUD PiP入口，留待接续。
- Java21前台common:compileJava先364错误（包含新Vector3f导入遗漏与旧HUD光照），修正后358错误，最新build.log为358；Avatar无编译诊断。定向git diff --check通过。完整构建/smoke仍未通过，真实头像尺寸、倒置、裁剪、透明与fallback效果MANUAL/PENDING。
- 分支1.21.1，累计修改未提交/推送。下一步迁移FiguraGui HUD及剩余GuiGraphicsAccessor依赖，再处理预览测试和M5兼容。M3–M5检查点未完成，M6独立。

## 原生 HUD 绘制入口（2026-11-01，M3–M5 进行中）

- CodeGraph核对FiguraGui、FiguraGuiRenderer/State和Avatar.hudRender；minecraft-dev核对原生Gui绘制阶段，并参考本地1.21直接HUD调用。
- FiguraGui在popup之后直接调用avatar.hudRender(gui.pose, gui.bufferSource)，前后提交GUI批次；保留相机实体选择、panic门控、脚本renderHUD取消原版HUD以及纸娃娃/动作轮叠加顺序。
- Avatar.hudRender保留HUD筛选、16倍坐标和Z反射、全亮光照与无深度绘制；finally恢复深度、光照、矩阵及该方法profiler。FiguraGui各层profiler也配对恢复。
- 删除已无生产调用的FiguraGuiRenderer/State以及GuiGraphicsAccessor及其注册，所有相关生产PiP职责已有原生承接。LibraryPreviewRuntimeSmokeTest仍实现旧GuiGraphicsAccessor，连同旧PiP测试需适配，尚未运行，不能视作通过。
- Java21前台common:compileJava两次退出1，最后301错误（build.log）；Avatar和FiguraGui无编译诊断。定向git diff --check通过，Mixin配置JSON可解析。整体构建仍失败；真实HUD位置/透明、脚本隐藏、叠加顺序及异常恢复MANUAL/PENDING。
- 分支1.21.1，累计修改未提交/推送。下一步清点剩余GUI/input/font错误，完成原生预览测试适配及M5兼容；M3–M5合并检查点未验收，M6独立。

## 鼠标、滚动条与通知原生接口（2026-11-01，M3–M5 进行中）

- 从最新build.log归类剩余错误，GPU适配占大头，继续先处理GUI同类差异。CodeGraph核对KeyboardHandlerMixin、MouseHandlerMixin、ScrollBarWidget、FiguraToast；minecraft-dev核对原生MouseHandler和Toast接口。
- MouseHandlerMixin两处残余mouseButtonInfo改用已传入的原生button，保留动作轮YSM左键释放及YSM设备状态采集。
- ScrollBarWidget改用原生renderWidget、PoseStack及Axis.ZP(-90度)横向旋转、原生blit参数顺序；保留滚动/拖动/插值/尺寸算法，finally恢复横向矩阵。
- FiguraToast适配原生Toast.render(GuiGraphics,ToastComponent,long)返回Visibility；每次绘制前更新显示时长/重复通知刷新和声音，保留标题/正文切换、淡入、换行、动画帧、token合并。发送端使用Minecraft.getToasts，贴图改原生blit。
- Java21前台common:compileJava退出1，最终278错误（build.log），本批三个文件无编译诊断。定向git diff --check通过；完整构建/smoke仍未通过，真实输入释放、横纵滚动条、通知动画与时序均MANUAL/PENDING。
- 分支1.21.1，累计修改未提交/推送。下一步剩余GUI/font/debug接口与预览测试，随后M5默认关闭GPU的CPU回退兼容；M3–M5检查点未完成，M6独立。

## 模型库贴图与旋转标签裁剪（2026-11-01，M3–M5 进行中）

- CodeGraph核对LibraryRowList、VerticalWardrobeTab及UIHelper滚动文本调用；minecraft-dev核对GuiGraphics.enableScissor原生实现，确认它直接使用屏幕坐标而不转换当前PoseStack。
- LibraryRowList卡片/表格缩略图和操作图标使用原生blit，保留尺寸、图集区域、帧偏移及现有列/行/按钮布局。
- VerticalWardrobeTab改用原生PoseStack和Z轴-90度旋转，保留原命中矩形和阅读方向。
- UIHelper两种滚动文本在裁剪前转换局部矩形四角到屏幕边界，支持当前旋转/缩放标签，裁剪finally恢复；不改变文本滚动算法和布局。此变更由原生裁剪接口差异所必需，避免只改签名后长标签被局部负坐标裁掉。
- Java21前台common:compileJava退出1，270错误（build.log）；本批三个文件无编译诊断。定向git diff --check通过。整体构建/smoke仍未通过；真实长标签、旋转裁剪、缩略图和图标MANUAL/PENDING。
- 分支1.21.1，累计修改未提交/推送。下一步继续剩余GUI/font/debug接口、原生预览测试及M5默认关闭GPU的CPU回退兼容；M3–M5未验收，M6独立。

## 卡片、帮助页与控件编辑器接口（2026-11-01，M3–M5 进行中）

- CodeGraph核对ModelCardWidget、WardrobeCollectionGrid、HelpScreen及AvatarControlsScreen；minecraft-dev核对原生GuiGraphics.blit，javap核对CAT声音和Screen.setTooltipForNextRenderPass。
- 模型卡片/收藏网格的缩略图、授权/可见性状态图标改用原生blit，保留图集UV、缩放、布局、资源句柄和交互。帮助页猫叫彩蛋使用1.21.1 CAT_PURR/CAT_AMBIENT，保留概率与心形粒子。
- AvatarControlsScreen控件滑块改原生renderWidget/PoseStack，保留数值同步/布局；ControlSwitch改无参数onPress，仍先验证写值成功后切换。物品图标用renderItem，提示改Screen原生延后提示，背景用flush分隔；下拉菜单Z=500并finally恢复矩阵。
- Java21前台common:compileJava两次退出1，261后251错误，最新build.log为251。本轮四个文件无编译诊断；定向git diff --check通过。完整构建/smoke未通过，真实图片/声音/控件输入/菜单层级与提示MANUAL/PENDING。
- 分支1.21.1，累计修改未提交/推送。下一步剩余font/debug/click接口、原生预览测试和M5默认关闭GPU的CPU回退兼容；M3–M5检查点未验收，M6独立。

## 原生点击事件类兼容（2026-11-01，M3–M5 进行中）

- CodeGraph核对FiguraFunctionClickEvent、ScreenMixin、ClickEventActionMixin及TextUtils；minecraft-dev核对1.21.1 ClickEvent为class、动作/字符串构造和Action编解码。
- FiguraFunctionClickEvent从新版record实现接口改为继承原生ClickEvent；保留chunk()与现有MapCodec，构造仍使用FIGURA_FUNCTION动作和原字符串。ScreenMixin通过FiguraClickEvent.onClick()公开访问器执行现有回调；未改变脚本事件授权门控。
- Java21前台common:compileJava退出1，126错误（build.log），本批文件无编译诊断；核对本地init仍为-Xmaxerrs 100000，未缩减错误输出。错误数量受javac诊断阶段影响，不据此推断全部剩余实现量或功能覆盖。定向git diff --check通过。
- 已确认待接续语义差异：TextUtils.tryParseJson仍调用将legacy clickEvent/hoverEvent转成新版snake_case格式的转换器；1.21.1原生仍读旧字段，需在当前移植范围内适配并验证，不能只以点击事件类编译无诊断宣称链接可用。
- 整体构建/smoke未通过，真实回调及JSON事件行为MANUAL/PENDING。分支1.21.1，HEAD af23872，累计修改未提交/推送。
- 下一步先处理文本事件JSON原生兼容，再剩余font/debug接口、预览测试及M5默认关闭GPU兼容；M3–M5检查点未验收，M6独立。

## 文本事件 JSON 转换方向（2026-11-01，M3–M5 进行中）

- CodeGraph核对TextUtils.tryParseJson及事件转换；minecraft-dev核对原生Style/ClickEvent/HoverEvent编解码。1.21.1使用clickEvent.value、hoverEvent.contents及show_entity的type/id。
- 原生legacy事件保持不变；新版click_event/hover_event在缺少原生同名字段时转换回1.21.1格式。点击url/path/command/page转value字符串，show_text转contents，show_item保留完整JSON载荷，show_entity将类型id/uuid转type/id并保留名字组件。
- 递归处理extra/with/separator、悬停文本与实体名字，保留嵌套组件事件；不遍历物品components/NBT任意载荷。移除原先把旧字段转新版的反向转换。原生CODEC与脚本事件授权门控保持不变。
- Java21前台common:compileJava退出1，仍126错误（build.log）；TextUtils无编译诊断。随后仅将hover名字递归限定到show_entity，未重复构建。定向git diff --check通过。
- 尚未运行实际原生CODEC回归测试：需要覆盖legacy点击/悬停、新版命令/URL/page、嵌套hover实体名字、物品components保留及不允许脚本事件的门控。整体构建仍失败，不得仅因无编译诊断宣称链接可用。真实点击/悬停MANUAL/PENDING。
- 分支1.21.1，累计修改未提交/推送。下一步继续剩余font/debug兼容与M5默认关闭GPU的CPU回退，再完成原生预览/文本回归及合并检查点。M6独立。

## 2026-11-01 最新接续：字体、F3 统计与残余图标绑定

- 当前阶段仍为用户已批准的 M3–M5 合并检查点；M6 独立。分支 `1.21.1`，HEAD `af23872`，本批未提交/推送，累计工作区改动保留。
- 字体生产链已改为原生 FontSet/BitmapProvider.Glyph/BakedGlyph：在 provider 选择时设置表情 advance，在 computeBakedGlyph 的有界上下文中传递码点，在 stitch 后附加动画元数据；原生 BakedGlyph.render 使用 Matrix4f 与 RGBA 浮点顶点。删除不存在的 GlyphStitcher/FontSet.Source 绑定及注册。Fabric、NeoForge synthetic 方法名已按各自 jar 静态核对，运行时 Mixin 尚未验证。
- F3 统计改为 DebugScreenOverlay.getGameInformation 返回列表追加；保留 panic/RENDER_STATS 条件、原有复杂度/脚本/ping 内容。删除新版 DebugScreenEntries 注册与状态绑定，改为一个原生 overlay Mixin。
- 六处图标/资源徽章/上传封面/登录页 blit 改为 1.21.1 API；缩放调用按原生参数顺序保留目标尺寸与源区域尺寸。GeckoLib GeoBone 修正为已安装 4.9.3 jar 内的 software.bernie.geckolib.cache.object.GeoBone。
- 删除无调用方且目标不存在的 PlayerModelTypeAccessor、PlayerHeadRenderInfoMixin 及其 duck/注册；已有原生头部和皮肤预览路径保留。
- 已验证：CodeGraph 与 minecraft-dev MCP 实际调用成功；Java21 前台 common:compileJava（-Xmaxerrs 100000，Tee-Object build.log）最终退出 1、72 个错误。本批文件无编译诊断，但整个 common 未通过，错误数不代表完整剩余工作量。Mixin JSON 可解析；git diff --check 通过。
- 未验证：实际 Mixin 应用、F3 切换与统计显示、表情宽度/动画/斜体粗体/资源重载、图标与封面实际绘制均 MANUAL/PENDING；完整 smoke 与双加载器构建仍未执行成功。未启动游戏、未复制 mods。
- 下一步最小任务：继续修复 common 剩余原生绑定与 M5 默认关闭 GPU 的编译兼容，保留 CPU 路径；恢复编译后迁移剩余 smoke fixture，再按正式计划完成合并验收及提交推送。
- 额度检查：可靠接口返回 10080 分钟窗口已使用 11%，剩余 89%；未触发额度交接，不委派 Luna。

## 2026-11-01 接续：原生权限及 CPU 材质元数据

- M3–M5 合并检查点仍未通过，分支 `1.21.1`、HEAD `af23872`，本轮未提交/推送，未进入 M6。
- minecraft-dev 核对 LivingEntity.triggerItemUseEffects 中 isUsingItem 的实际调用；现有 LivingEntityMixin 已在该处触发可取消的 USE_ITEM 事件。删除目标不存在的 ConsumableMixin 及注册，保留原生事件入口，没有删掉食用事件功能。
- 按原生源码补回 SubtitleOverlay.Subtitle 与 BookEditScreen.LineInfo 类访问权限；保留字幕与书籍表情逻辑。LibraryPreviewPlayer 的 PlayerSkin 改用 client.resources 原生类型。
- AccelerationService/DrawSemantics/AccelerationFrameDiagnostics 改用原生 RenderType；DrawSemantics.cull 从原生 CompositeState.cullState 读取，使用同一个 CULL 状态常量，补充其访问权限；保留 outline/sortOnUpload 与 Java transform 回退。
- 已核对 GPU 生产链：GpuSubmission 的 BufferSource 包装、GpuDrawContext、MinecraftGpuDeviceAdapter、GpuShaderProgram 和 GpuAccelerationBackend 仍依赖新版管线。MVP 默认关闭是正式计划 §5/§10 已批准方案；本轮尚未完成该模块的编译兼容，不宣称 GPU 关闭路线已经可运行。
- 已验证：Java21 前台 :common:compileJava（完整日志 build.log，-Xmaxerrs 100000）退出 1、59 个错误，当前报告均位于 acceleration 和 GlBufferAccessor。本轮修改文件未出现诊断，但 javac 阶段可能继续暴露错误；common 整体编译失败。git diff --check 通过，随后仅调整两处 import 顺序。
- 未验证：Lua USE_ITEM 取消行为、字幕/书籍表情、预览皮肤及材质剔除的客户端实际表现均 MANUAL/PENDING；未执行成功的全量 smoke、Mixin 应用、双加载器构建不得视为通过。没有启动游戏或部署 mods。
- 下一步：以已批准 MVP 为准落实不支持的 GPU 绑定与生产编译链分离，明确设置支持状态，保留 Java/CPU 路径与其有效测试；不得重写完整 GL GPU 后端或用假 GPU 测试替代验收。之后恢复 common 编译、更新剩余 native fixture 并跑正式计划的自动验收。GPU 选项 B 的完整性能验证不属于当前 MVP。

## 2026-11-01 接续：GPU 激活入口的 1.21.1 支持门控

- 仍为已批准的 M3–M5 合并检查点；分支 `1.21.1`、HEAD `af23872`，累计改动未提交/推送。
- HardwareAcceleration 移除新版 MinecraftGpuDeviceAdapter 的创建、探测、延迟初始化、回收轮询和设备关闭生命周期。AUTO/GPU 探测明确返回新增 UNSUPPORTED_MINECRAFT_VERSION 原因，不创建 GPU 设备；独立 SIMD 探测/原生自检及 Java 回退保留。
- GPU 实验选项的已保存偏好保留；应用设置时实际 GPU 能力保持关闭。默认开关仍为 false。禁用状态与版本回退原因均有中英文提示，明确 1.21.1 GPU 暂不支持、Java 默认渲染。
- CodeGraph/当前源码核对两条生产回退：ImmediateFiguraRenderer 和 YsmRenderer 在 enqueue 未接收时均通过 acceleration.transform 与原生 VertexConsumer 发出真实顶点。这里只证明存在静态回退路径，不证明当前客户端已可运行；GpuSubmission 等遗留类仍有不存在的类型，尚须解除编译与类加载依赖。
- HardwareAccelerationSmokeTest 增加真实 AUTO/GPU probe 拒绝与不加载 native 的断言；配置断言改为核对偏好保留、实际 GPU 开关关闭。保留原数值回放、状态机及通知测试。该 smoke 未运行，不记 PASS。
- 已验证：Java21 前台 common:compileJava（Tee-Object build.log，完整 -Xmaxerrs 100000）退出 1，仍 59 个错误；HardwareAcceleration 无诊断，GPU 原实现/GlBufferAccessor 仍阻止 common 编译。中英文 JSON 解析与新增支持/原因键检查通过，git diff --check 通过。
- 下一步最小任务：按已批准 MVP 关闭路线解除 GpuSubmission/GpuDrawContext/GpuDeviceAdapter/GpuAccelerationBackend/GpuShaderProgram/MinecraftGpuDeviceAdapter/GpuStorageSupport/GlBufferAccessor 对 1.21.1 不存在 API 的依赖；保留 Java 变换与真实原生顶点输出，以及 GpuRigidData 等 CPU 数据测试。不得以仅关闭入口冒充完成编译兼容。
- 完整 smoke、真实客户端、双加载器构建与最终发布仍未通过；所有本轮界面/运行时行为 MANUAL/PENDING。无游戏启动、无 mods 部署、无 M6 实施。

## 2026-11-01 关键进展：common 完整编译恢复，CPU 布局 smoke 定向通过

- 当前仍为 M3–M5 合并检查点，分支 `1.21.1`、HEAD `af23872`，累计工作区未提交/推送；M6 未开始。以下最新验证替代此前 common 59 错误状态，历史记录仅用于追溯。
- 按已批准 MVP 去掉 1.21.1 不具备的 GPU 设备实现：GpuAccelerationBackend/GpuDeviceAdapter/GpuDrawContext/GpuRetirement/GpuPoseArena/GpuStorageSupport/MinecraftGpuDeviceAdapter 与 GlBufferAccessor；移除后者注册和已不需要的 BufferSource.fixedBuffers widening。没有重建 GL GPU 后端。
- AccelerationBackend/Service/Controller 解除上述设备类型引用，保留 Java/SIMD 变换、过期 lease 拒绝、后端失败回放、资源/帧/所有者失效处理。GpuSubmission.enqueue 明确返回未接收，由原 Figura/YSM fallback 发出全部真实顶点，不产生占位缓冲或冒称 GPU 已提交。
- 保留 GpuBatchData/GpuRigidData/GpuModelData 与纯着色器文本变换；GpuShaderProgram 只移除新版 RenderPipeline 构造和顶点格式注册。Cpu 数据测试没有删除。完整 GPU 绘制功能仍不支持，不能将布局/文本测试当作像素证据。
- 修复最后九处生产诊断：纸娃娃 native debug overlay、Wardrobe Util、AvatarInfo 居中文字、MOTD 高度门槛、Tab 头像转到已有原生 portrait 路径、动画屏幕 flush、密码 native setFormatter。minecraft-dev/javap 核对原生接口与 scrollbarHeight 私有实现（clamp 到至少32，因此高度>=48时原条件恒真）。
- 已验证 PASS：Java21 前台 :common:compileJava，退出0、完整生产源码编译成功；日志 build/tmp/common-compile-pass.log。非阻塞 LambDynamicLights 过时 API 警告保留，不扩大范围修复。
- 已验证 FAIL：完整 :common:compileTestJava，退出1、1,046个错误；日志 build/tmp/common-test-compile-failure.log，-Xmaxerrs 100000。主要文件：LibraryPreviewRuntimeSmokeTest 180、GpuAccelerationSmokeTest 151、YsmMaterialNativeSmokeTest 140、YsmSubEntitySmokeTest 90、GpuSubmissionSmokeTest 60、YsmVanillaStateSmokeTest 54。不得把生产编译通过视为测试全部通过。
- 已验证有限 PASS：GpuRigidDataSmokeTest 将 RenderTypes/rendertype fixture 换成原生 RenderType，运行 :common:gpuRigidDataSmokeTest --init-script build/tmp/rigid-smoke.init.gradle，退出0，八组既有断言通过；日志 build/tmp/gpu-rigid-data-pass.log。临时脚本只编译这一测试源，正式 common/build.gradle 未缩减测试集，故仅证明该CPU数据测试，不等于全量 testClasses 或合并验收。
- git diff --check 通过。可靠额度接口：10080分钟窗口已使用11%、剩余89%，未触发交接阈值；未委派Luna。
- 下一步：迁移其余旧测试夹具，特别是预览状态/GUI管线、原生实体与旧GPU设备测试；针对当前不支持的GPU能力验证真实拒绝及CPU输出，不伪造设备成功，不缩减正式测试门槛。全量测试恢复后运行原计划 smoke 与两加载器检查，再执行合并检查点提交/推送并核对远端。
- 未验证：所有实际客户端/Mixin应用/交互/GUI/多人行为仍 MANUAL/PENDING；本轮没有启动游戏或部署 mods。GPU选项B不属于MVP，不宣称其绘制或性能通过。

## 2026-11-01 接续：NBT 测试 API 迁移及两项 CPU smoke 通过

- 当前 M3–M5 合并检查点未完成；分支 `1.21.1`、HEAD `af23872`，本轮仍未提交/推送，M6 未开始。
- CodeGraph 核对 NbtDefaults、YsmPayload/YsmLocalCorpus、非零默认值断言与 native skeleton fixture；minecraft-dev 核对 1.21.1 CompoundTag 原生读取语义。复用 build/tmp/nbt-rewrite.pl 对25个测试源机械迁移 getCompoundOrEmpty 和默认值为零/空串/false 的 getter；非零默认值另行处理，不替换成原生空值以免弱化测试。
- 15个测试源的原生包路径机械迁移：PlayerModel、PlayerSkin、RenderTypes/RenderType、Util；没有删去功能断言或正式测试源。
- YsmPayload/YsmLocalCorpus 的 CompoundTag.keySet 改为 getAllKeys，列表读取复用 NbtDefaults.getListOrEmpty，字节数组的 orElseThrow 复用 findByteArray 保留缺失即失败语义。LibraryPackagePreparation 的 authors '?' fallback 保留；YsmMultilineExpression 的 merge 标志明确断言存在 TAG_BYTE 且布尔值相等，不能把缺失false当成正确。
- 已验证 FAIL：完整 Java21 前台 :common:compileTestJava 退出1，893个错误（此前1046）；生产 common 编译通过。完整诊断日志 build/tmp/common-test-compile-893.log，仍使用 -Xmaxerrs 100000。不得以错误数减少推断功能验收完成。
- 已验证有限 PASS：前台 :common:ysmPayloadSmokeTest :common:ysmMultilineExpressionSmokeTest --init-script build/tmp/nbt-smoke.init.gradle 退出0。实际密文白名单、原始密文字节一致性、压缩磁盘回读/泄漏检查、时间线与 controller entry/exit 多行执行、loader/runtime 属性传递断言通过。日志 build/tmp/nbt-smoke-pass.log。
- 临时脚本只定向编译 YsmPayloadSmokeTest、YsmLocalCorpusSmokeTest、YsmMultilineExpressionSmokeTest；前两项命令只运行 payload 与 multiline，LocalCorpus 仅编译，没有运行用户本地资源库。正式 common/build.gradle 没有删减全量测试集；上述PASS不代表完整testClasses通过。
- git diff --check 通过。当前完整测试仍需迁移旧预览/实体状态/GPU设备夹具、剩余NBT list/optional读取等；继续保留真实CPU数据和顶点输出验收，不恢复本轮不支持的GPU设备、不伪造GPU成功。
- 实际客户端/Mixin应用、真实GUI交互、双加载器构建及多人验收仍 MANUAL/PENDING；本轮没有启动游戏、部署mods或进入M6。

## 2026-11-01 接续：投射物/载具夹具与 native image 生命周期

- M3–M5 合并检查点仍未完成；分支 `1.21.1`、HEAD `af23872`，累计工作区保留，未提交/推送，M6 未开始。
- YsmSubEntitySmokeTest 使用原生 projectile.Arrow/ThrownTrident、vehicle.Boat、EntityType.BOAT 与 minecraft:boat，移除新版木种独立实体ID和三参数构造器；原有跨世界身份、实体ID复用、tag变更、乘客归属、独立时间/变量/控制器及资源释放断言保留。
- 新测试辅助类 common/src/test/java/top/aihmc/omnimorph/test/NativeImageState.java 缓存反射读取 NativeImage.pixels。minecraft-dev 核实 close() 释放分配后置0，checkAllocated()也以0判定。使用它替代不存在的 NativeImage.isClosed：子实体六处、material九处、stability集合断言；没有用宽高或模拟boolean冒充实际分配状态。辅助类是测试代码，生产纹理逻辑未为测试改造。
- 核对期间发现 AbstractArrowAccessor 原有 @Invoker("isInGround") 在1.21.1不存在对应方法。CodeGraph与本地1.21.1 javap核对 protected boolean inGround / protected int inGroundTime 后，将其改为 @Accessor("inGround")，保留duck方法签名。编译通过只能证明源码，不代表Mixin运行时应用通过。
- 已验证有限PASS：前台定向 :common:compileTestJava --init-script build/tmp/subentity-compile.init.gradle 退出0，编译完整YsmSubEntitySmokeTest和NativeImageState；日志 build/tmp/subentity-test-compile-pass.log。未执行run()，没有把定向编译记作子实体功能PASS。
- 已验证FAIL：完整测试编译从893→810→803→795，差异复查纠正一处限定表达式后最终794个错误，退出1；最终日志 build/tmp/common-test-compile-794.log，仍-Xmaxerrs 100000。正式测试源集未删减。最后仅生产Accessor改变后未重复全量测试编译。
- 已验证PASS：Accessor修正后前台 :common:compileJava 退出0，日志 build/tmp/common-compile-arrow-accessor-pass.log。git diff --check通过。
- 未完成：YsmMaterialNativeSmokeTest共享夹具仍有旧GpuDevice/TextureView/RenderSetup等，不能运行；其生命周期断言本轮只迁移接口，尚未执行。LibraryPreviewRuntime、旧GPU设备测试、YsmVanillaState等夹具仍需继续移植。下一步优先恢复共享原生夹具与相应真实CPU/顶点输出测试，不伪造GPU设备成功，不缩减正式验收门槛。
- 无新增运行时PASS；真实客户端/Mixin/GUI/多人、双加载器构建仍MANUAL/PENDING。本轮没有启动游戏、部署mods或进入M6。

## 2026-11-01 接续：原生骨架根变换与装扮帧夹具

- M3–M5 合并检查点继续；分支 `1.21.1`、HEAD `af23872`，累计改动未提交/推送，M6未开始。
- NativeSkeletonSmokeTest 按真实1.21.1模型接口移植：PlayerModel没有root部件，原(1,2,3)模型单位根平移改由传给VanillaSkeletonAdapter.capture的modelToEntity矩阵提供（除以16转为block单位）。保留原完整矩阵、x=3/下一帧x=10、宽细模型定义一致与旧帧不受修改等断言，没有把预期值改成迁移后的实际输出。
- 已验证有限PASS：前台 :common:vanillaSkeletonPortProbe --init-script build/tmp/vanilla-skeleton.init.gradle 退出0；临时入口反射运行原NativeSkeletonSmokeTest.testVanilla全部断言。日志 build/tmp/vanilla-skeleton-pass.log。仅证明原版CPU骨架部分；YSM/Figura子项和原完整run未执行，不记完整NativeSkeletonSmokeTest PASS。
- OutfitAutomaticAnchorSmokeTest、OutfitNativeRenderSmokeTest、CosmeticBoneOverrideSmokeTest、OutfitTrailRuntimeSmokeTest改用YsmEntityFrame。光照从旧state.lightCoords独立传入现有OutfitNativeRenderer原生方法，保持0x00f000f0、delta=.5、原矩阵和顶点断言不变；没有删除原装扮依赖、预算、旧帧回放或失败清理断言。
- OutfitTrailRuntime的ClientLevel构造按原生javap核对改为Supplier/LevelRenderer/boolean/long顺序；Wearer公开getPermissionLevel并委托原生super实现，以满足EntityAccessor可见性，不编造权限。
- 已验证FAIL：完整 :common:compileTestJava 从794→750→749，最终退出1，日志 build/tmp/common-test-compile-749.log，完整-Xmaxerrs 100000。本轮五个骨架/装扮文件已无编译诊断，但不据此宣称其完整功能通过。生产common编译保持通过。正式common/build.gradle未缩减测试源集。
- git diff --check通过。可靠额度查询：10080分钟窗口使用12%、剩余88%，未达到交接阈值，未委派Luna。
- 下一步继续共享原生材质/预览夹具和旧GPU测试的移植，恢复完整testClasses及计划smoke。当前共享YsmMaterialNativeSmokeTest仍引用旧GPU/纹理视图接口；本轮没有伪造GPU成功，也没有为测试修改生产GUI。
- 实际客户端/Mixin/GUI交互/多人及双加载器完整构建仍MANUAL/PENDING；未启动游戏、部署mods或进入M6。合并检查点未通过，不提交推送。

## 2026-11-01 接续：真实 GL 原生纹理基础及共享客户端夹具

- 继续已批准的 M3–M5 合并检查点；M6 独立且未开始。分支 `1.21.1`、HEAD `af23872`，累计修改未提交、未推送，共同验收未通过。
- 已使用 CodeGraph 查询共享夹具及 MinecraftAccesor，使用 minecraft-dev 实际读取 1.21.1 User、ToastComponent 源码，配合原生 javap 核对 getToasts/tell。
- 新增测试辅助 NativeGlWindow，创建真实可见 GLFW/OpenGL 窗口；嵌套作用域借用当前上下文，只有所有者负责销毁。没有 Qt offscreen，没有修改生产界面或启动游戏。
- 已验证有限 PASS：前台 Java21 :common:nativeTexturePortProbe --init-script build/tmp/native-texture.init.gradle 退出0。真实窗口可见属性、NativeImage 原生分配、实际 GL 纹理上传/逐字节 RGBA 读回、NativeImage/GL 资源释放、嵌套借用及窗口最终关闭断言通过。日志 build/tmp/native-texture-pass.log。此临时脚本仅定向编译辅助类与探针，不代表完整 testClasses 或 GUI 外观验收通过。
- YsmMaterialNativeSmokeTest.nativeTextureContext 替代已不存在的 RenderSystem.DEVICE 注入，7处预览/装扮调用同步改名；整个共享 run 持有窗口，排空清理队列后关闭，避免内层资源退出先销毁上下文。共享夹具仍有旧 GPU/纹理视图代码，完整运行尚未验证。
- RecordingToasts 改为 ToastComponent，用户构造保留原身份并补 User.Type.MSA，客户端 getToasts/tell 对齐原生接口，移除不存在的文本输入焦点回调。未删除正式功能断言，未缩减 common/build.gradle 测试集。
- 已验证 FAIL：完整 :common:compileTestJava 退出1，749→739个错误，日志 build/tmp/common-test-compile-739.log；生产 :common:compileJava 通过。旧 PublicationDevice、GpuTextureView、RenderSetup 等测试接口仍待迁移。
- git diff --check 通过。下一步继续共享材质纹理发布/生命周期测试与旧 GPU 测试迁移，恢复完整 testClasses 后执行原计划 smoke。真实客户端/Mixin、GUI交互、多人及双加载器完整构建仍未验证；不宣称阶段完成，不提交推送。

## 2026-11-01 接续：修复原生纹理注册递归并保留真实 GL 回归测试

- 当前仍为已批准的 M3–M5 合并检查点；分支 `1.21.1`、HEAD `af23872`，本轮和累计修改未提交/推送。M6未开始。
- CodeGraph 查询 FiguraTexture 与共享材质夹具；minecraft-dev 实际读取1.21.1 TextureManager.register/loadTexture。发现原版 register 同步调用 texture.load，当前 FiguraTexture.load 再调用 uploadIfDirty/register，静态路径形成注册递归。
- 已修复：load 只更新原生像素，不再次注册；后台重载通过渲染队列上传，并在执行时同步检查纹理是否已关闭，避免排队期间释放后的重用。未变更GPU MVP范围。
- 新增仓库内测试 `common/src/test/java/top/aihmc/omnimorph/test/FiguraTextureRegistrationSmokeTest.java` 及正式 `:common:figuraTextureRegistrationSmokeTest` 任务。使用真实 TextureManager.register/load、真实可见 GL 窗口和实际 FiguraTexture；仅 Minecraft 实例/调度队列隔离，无伪造纹理设备。
- 已验证有限PASS：前台 Java21 :common:figuraTextureRegistrationSmokeTest --init-script build/tmp/figura-texture-smoke.init.gradle 退出0，日志 build/tmp/figura-texture-smoke-pass.log。覆盖注册完成及对象身份、四通道像素读回、重载保持ID、后台重载像素更新、租约保持资源、最后租约释放、释放前排队的重载不重用已释放资源、队列移除和GL_NO_ERROR；worker异常经FutureTask.get传播。临时init仅定向编译3个测试类，不能替代完整testClasses和原计划smoke。
- 共享材质夹具底部的原生图像生命周期断言改用 NativeImageState（检查pixels==0）；像素断言读取1.21.1 ABGR，再转换为原有ARGB预期，未改变源PNG/预期颜色或资源释放断言。完整共享夹具仍有旧GPU、渲染状态等接口，尚不能运行。
- 已验证FAIL：完整 :common:compileTestJava 退出1，739→727个错误；日志 build/tmp/common-test-compile-727.log，保留完整-Xmaxerrs 100000。生产common编译通过。正式测试源集未缩减。
- git diff --check通过，已审查本轮生产修复和新任务diff；无相关仓库Java格式器配置。下一步继续共享材质 PublicationDevice/GpuTextureView/RenderSetup 夹具的原生替换及剩余实体状态测试迁移；全量smoke、双加载器完整构建、实际客户端/Mixin/GUI/多人仍未完成，不提交推送，不宣称检查点完成。

## 2026-11-01 接续：共享材质夹具 NBT 与回放入口迁移

- 当前 M3–M5 合并检查点未通过；分支 `1.21.1`、HEAD `af23872`，累计修改仍未提交/推送，M6未开始。
- CodeGraph 查询共享材质夹具、YsmEntityFrame 和 NbtDefaults。共享夹具6处NBT读取改为1.21.1语义：任意类型列表复用NbtDefaults，动画审计字节读取保留缺失时空数组，GUI必填资源与子实体纹理复用findByteArray(...).orElseThrow保留类型/存在性要求。没有更改原预期数据或断言。
- 移除 --acceleration、--figura-acceleration、--held-items 三个回放入口的旧 RenderSystem.DEVICE 反射/假设备注入与恢复；实际纹理现在由外层真实GL上下文承载，保留各入口原加载、回放、断言和finally清理。Figura入口的旧debugEntries尚待迁移，不能据此宣称这些入口已能运行。
- 已验证FAIL：完整Java21前台 :common:compileTestJava 退出1，727→721个错误；日志 build/tmp/common-test-compile-721.log，完整-Xmaxerrs 100000。生产common编译保持通过/本次UP-TO-DATE；没有运行完整共享材质测试。
- git diff --check通过。旧 PublicationDevice/GpuTextureView/RenderSetup 的纹理发布、失败与租约测试仍未迁移，不能简单删掉这些断言；剩余动画帧、debug和GPU测试也尚未通过。下一步继续这些最小未完成项。双加载器完整构建、客户端/Mixin/GUI/多人仍未验证，当前不提交推送。

## 2026-11-01 接续：三组共享纹理契约迁移并实际通过

- 仍为已批准的 M3–M5 合并检查点；分支 `1.21.1`、HEAD `af23872`，累计修改未提交/推送，M6未开始。
- CodeGraph 查询 FiguraTextureRegistrationSmokeTest，minecraft-dev读取原生NativeImage源码。将共享材质 testTexturePublication/testLateTextureBindings/testTextureUploadLifetime 迁移到仓库内原生纹理测试，并由原共享入口委托调用；正式测试集和原入口保留。
- 移除共享夹具中不存在于1.21.1的 PublicationDevice/PublicationView/PublicationGpuTexture/PublicationTexture 假设备实现和默认入口SamplerCache/DEVICE注入；注册消费者检查改为真实 glIsTexture。CPU控制器、粒子、预算、几何等原调用保留。
- 已验证有限PASS：前台Java21 :common:figuraTextureRegistrationSmokeTest --init-script build/tmp/figura-texture-smoke.init.gradle 退出0，日志 build/tmp/native-texture-contracts-pass.log。覆盖：真实注册后可读纹理；干净纹理不访问源像素；显式releaseId后重建；关闭后不能重发布；实际NativeImage分配检查导致上传失败、失败时无发布、恢复源后真实上传重试、最终释放失败时保留的目的纹理；延迟原生lookup上传脏像素；原生reload保留源图像；退休/迟到注册/getId/bind同一fallback；64次同位置替换不被旧清理误删；manager.close队列安全；64次脏像素更新逐字节读回并保留ID/双轴clamp/linear；多层particle/frame租约、拒绝新租约、重复release及最终原生/GL释放；GL_NO_ERROR。
- 失败注入为测试内临时置NativeImage私有pixels=0，触发原生checkAllocated，finally恢复原指针再关闭/重试；没有替换GL设备或增加生产测试钩子。1.21.1没有GPU view对象，旧view创建失败不再作为可用平台行为伪造；原生失败后目的ID可保留用于重试，测试明确验证最终释放，不宣称立即释放。
- 临时init只定向编译3个测试类，上述PASS不能替代完整testClasses。共享3个委托的执行环境仍未整体运行；独立入口已执行相同测试方法，但不证明其余共享夹具通过。
- 已验证FAIL：完整 :common:compileTestJava 退出1，721→648个错误，日志 build/tmp/common-test-compile-648.log，完整-Xmaxerrs 100000；生产common编译通过。剩余旧debug、动画帧、RenderPipelines和GPU加速测试仍需迁移。
- 已审查移除块和委托边界，git diff --check通过。下一步继续共享材质剩余渲染帧/debug/管线断言与其他旧GPU测试；全量smoke、双加载器构建、实际客户端/Mixin/GUI/多人仍未完成，不提交推送，不宣称M3–M5完成。

## 2026-11-01 接续：预览动画帧和原生调试分发器

- M3–M5 合并检查点继续；分支 `1.21.1`、HEAD `af23872`，累计修改未提交/推送，M6未开始。
- CodeGraph 查询共享材质夹具、YsmAnimationPlayer、YsmNativeStateSmokeTest/YsmPlayerActionState、YsmRenderer、YsmPreviewRenderTypes；minecraft-dev核对原生LivingEntity构造，原生javap确认EntityRenderDispatcher.setRenderHitBoxes/shouldRenderHitBoxes。
- 共享材质的GUI默认姿态/站立顶点和controlled-base静态帧迁移为YsmEntityFrame，保留原age、零头部/攻击/步态输入及原矩阵/顶点/隔离断言。新增RecordingClient原生EntityRenderDispatcher夹具并关闭hitbox debug，替换不存在的debugEntries/DebugScreenEntryList反射块，使Figura实际渲染读取原生shouldRenderHitBoxes。仅测试夹具改动，未改生产行为。
- 已验证FAIL：完整Java21前台 :common:compileTestJava 退出1，648→644个错误，日志 build/tmp/common-test-compile-644.log。本轮改动未运行共享smoke；生产common本轮UP-TO-DATE。git diff --check通过。
- 重要未完成：移动/冲刺测试仍用旧RunningRenderState且传null实体；当前原生状态机明确要求LivingEntity，不应仅换类型后丢失冲刺输入。需提供正确的实体输入并保留walk/run/idle原断言。
- 重要未完成：YsmPreviewRenderTypes目前明确回退有光照原版材质；从1.21.1原生jar核对rendertype_entity_translucent_emissive.vsh，仍调用minecraft_mix_light，不能将emissive当作已实现gui_no_lighting。此问题先前进度记录已列出；当前保留原无光照测试要求，尚未迁移RenderPipelines断言，不能为通过测试接受有光照回退。后续需在既定M4兼容范围恢复原无光照行为及原生材质断言。
- 编译诊断去重摘要保存在build/tmp/common-test-error-summary.txt：LibraryPreviewRuntimeSmokeTest168、GpuAccelerationSmokeTest127、YsmVanillaStateSmokeTest50、GpuSubmissionSmokeTest30、YsmMaterialNativeSmokeTest29；仅用于定位，不能代替javac总错误数644。
- 下一步按共享夹具的移动实体/GUI无光照及库预览/GPU测试迁移继续；全量smoke、双加载器完整构建、客户端/Mixin/GUI/多人仍未完成，检查点未达标，不提交推送。

## 2026-11-01 接续：恢复 GUI 无光照材质并通过真实窗口像素测试

- 当前仍为已批准的M3–M5合并检查点；分支 `1.21.1`、HEAD `af23872`，累计修改未提交/推送，M6未开始。
- 通过CodeGraph核对YsmPreviewRenderTypes、Fabric/NeoForge客户端入口和两侧盔甲Mixin；minecraft-dev核对ShaderInstance构造和原版shader加载语义，直接读取已安装Fabric Rendering 5.1.0和NeoForge21.1.185源码jar核对注册API。
- 新增 assets/omni-morph/shaders/core/ysm_gui_unlit.{json,vsh,fsh}。顶点色直接使用Color，不读取法线、方向光、lightmap；保留纹理、ColorModulator、overlay、雾淡出和alpha<0.1裁剪。YsmPreviewRenderTypes改为原生CompositeState材质，四种cull/translucent组合使用有界缓存；保留NEW_ENTITY、透明排序、实体深度写入、OVERLAY、MAIN_TARGET，NO_LIGHTMAP；纹理绑定保留FiguraTexture自己的blur/clamp。世界材质路径未改。
- Fabric CoreShaderRegistrationCallback和NeoForge RegisterShadersEvent注册同一shader，load callback更新引用，旧实例由加载器管理。Fabric显式纳入现有Fabric API的fabric-rendering-v1模块，已核对其仅依赖当前已有的fabric-api-base。没有新增独立图形依赖。
- 生产平台编译暴露同检查点内两侧盔甲Mixin遗留变量figura不存在，修正为已有figura$avatar字段，仅2行，恢复原取Avatar意图。
- 已验证PASS：前台Java21 :common:compileJava :fabric:compileJava :neoforge:compileJava 退出0，日志 build/tmp/unlit-platform-compile-pass.log。这证明三模块编译，不代表完整打包、Mixin实际应用或客户端启动。
- 已验证有限PASS：新增正式 :common:ysmPreviewLightingSmokeTest，前台配临时 build/tmp/unlit-preview.init.gradle 定向编译2个测试类并实际运行，退出0，日志 build/tmp/unlit-preview-pixels-pass.log。真实可见GLFW窗口/默认窗口buffer，四种材质原生状态及缓存、实际GLSL编译链接、颜色调制逐通道像素、透明裁剪背景保留、GL_NO_ERROR通过。GLSL导入使用Minecraft真实GlslPreprocessor；链接后确认Normal/Light0_Direction/Light1_Direction/Sampler2均不存在。没有offscreen/FBO替代窗口，也不宣称完整GUI外观通过。
- 初始测试失败已诊断并修正：独立测试未bootstrap注册表；手拼fog导入重复#version，改为原版预处理器；applyImport按原生public签名覆盖。最终日志为PASS，不能沿用先前失败推断shader生产故障。
- 共享testPreviewLighting不再引用不存在的RenderPipelines，委托同一原生材质状态断言；保留提交时GUI材质、world材质不受后续GUI模式影响的原断言。方向光/lightmap语义由真实GPU链接和像素测试覆盖，没有把测试改为接受有光照回退。
- 已验证FAIL：完整 :common:compileTestJava 退出1，644→625个错误，日志 build/tmp/common-test-compile-625.log。正式测试源集未缩减，完整共享smoke尚不能运行。最新生产文件后续仅调整import/换行，不改行为；git diff --check通过，未发现仓库Java formatter配置。
- 实际Fabric/NeoForge游戏进程的shader注册、资源热重载、混入应用、完整模型/GUI渲染仍MANUAL/PENDING；本轮GL测试直接编译生产GLSL资源，不冒充加载器启动验证。剩余移动实体、库预览及旧GPU测试继续迁移；全量smoke与双加载器打包未完成，不提交推送。
- 安全检查点额度接口返回10080分钟窗口已用12%、剩余88%，未触发预警/交接阈值，未调用Luna。

## 2026-11-01 接续：库预览输入 API 和原生外观测试

- 当前M3–M5合并检查点未通过；分支 `1.21.1`、HEAD `af23872`，累计修改未提交/推送，M6未开始。
- CodeGraph查询LibraryPreviewRuntimeSmokeTest、Button/LibraryFormScreen及LibraryPreviewPlayer；minecraft-dev读取1.21.1 DefaultPlayerSkin、PlayerSkin，原生javap核对ClientLevel/LocalPlayer构造。
- 仅迁移库预览测试夹具：50处onPress(null)→onPress()、4处Font(null,false)、13处键盘按下/释放原生参数、5处charTyped字符/零修饰键、所有旧MouseButtonEvent/MouseButtonInfo构造与派发改为原坐标和按钮。保留CJK输入、换行/退格、禁用和失焦、卡片命中/过滤、上下文菜单回调重建、数字轮盘回调、错误右键释放不能结束左键动作等原断言；没有删除交互测试或把断言改成永真。
- OtherPlayerLevel/CurrentPreviewPlayer构造按真实1.21.1签名迁移；两个EntityAccessor夹具公开getPermissionLevel并委托super，不编造权限。PlayerSkin换为原生6字段，保留textureUrl、cape/elytra和secure；默认原生皮肤使用稳定UUID(0,9)选择真实宽臂模型，并明确断言宽臂→细臂切换，未用细臂作为初始值弱化检查。
- 已验证FAIL：前台完整 :common:compileTestJava 退出1，625→541→526个错误；最终日志 build/tmp/common-test-compile-526.log。正式common/build.gradle未缩减测试源集。生产common本轮实际编译通过。库预览仍有旧EntityRenderState、GuiGraphicsExtractor/GuiRenderState、debug和deferred queue夹具，不宣称完整libraryPreviewRuntimeSmokeTest通过。
- 已验证有限PASS：前台 :common:libraryAppearancePortProbe --init-script build/tmp/library-appearance.init.gradle 退出0，日志 build/tmp/library-appearance-pass.log。临时probe从当前源码原样提取testPreviewAppearance、AppearancePlayer及原require/field辅助方法，在真实1.21.1 LibraryPreviewPlayer上执行全部原断言，含新增的宽臂基线和细臂模型检查。证明原生皮肤、实时切换、层偏好、缺失源玩家fallback；仅定向编译临时单类，不替代testClasses或库预览完整run，也不证明中文GUI像素显示。
- git diff --check通过；仅检查/调整本轮修改范围，保留既有工作区。下一步继续库预览剩余原生EntityFrame/即时渲染/独立Figura绘制夹具和旧GPU测试。全量smoke、实际客户端/Mixin/GUI/多人、双加载器完整打包仍未完成；不提交推送。

## 2026-11-01 接续：库预览原生帧绑定和装扮绘制参数

- 仍处于已批准的M3–M5合并检查点；分支 `1.21.1`、HEAD `af23872`，累计修改未提交/推送，M6未开始。
- CodeGraph核对LibraryPreviewBinding、LibraryFiguraPreviewRenderer、EntityFrame、LibraryFiguraPreviewState和OutfitNativeRenderer，按实际输入用途迁移，没有全局把旧AvatarRenderState替换成一个假类型。
- 预览/原版选择/本地世界草稿/骨骼选中等绑定键改为EntityFrame(原实体,1)，与capture默认partialTick=1一致；保留重复捕获、异步选择清空后旧帧绑定、独立选中快照及finishFrame等原断言。原生PlayerPose使用自身.frame()作为composition键，冻结皮肤和全部原可见层/默认age输入。
- 装扮/顶点计数改用零初值YsmEntityFrame；光照独立传入原生方法：原先显式满亮的3组仍0x00f000f0，直接new旧状态的原版夹具保留默认光照0；delta=.5和原矩阵、实际YSM/Figura顶点比较断言保留。countComposedFigura/countComposedYsm等调用原生Frame.draw/OutfitNativeRenderer，不删减几何比较。
- 四处旧debugEntries/DebugScreenEntryList夹具改为保存原生EntityRenderDispatcher.shouldRenderHitBoxes，关闭调试并在原finally恢复，不修改生产GUI。
- 已验证有限PASS：前台 :common:libraryFramePortProbe --init-script build/tmp/library-frame.init.gradle 退出0，日志 build/tmp/library-frame-pass.log。真实LibraryPreviewBinding/EntityFrame验证实体id及partialTick、重复capture、关闭选择后的两帧保留、GUI/world身份、最后一帧清理和重复finish。Avatar.clean为计数回调夹具，明确只证明清理调度/保留计数，不能冒充原生Avatar/GPU资源释放或完整库预览运行。临时init只定向编译单个probe，正式测试源集未缩减。
- 已验证FAIL：完整Java21前台 :common:compileTestJava 退出1，526→502→489→487，最终日志 build/tmp/common-test-compile-487.log。最后纠正原版默认光照后重跑仍487。生产common实际编译通过；仍不能运行完整LibraryPreviewRuntimeSmokeTest。
- git diff --check通过。下一步仍需库预览GuiGraphics即时渲染/独立Figura/旧deferred queue、Cape/Particle接口夹具和其他GPU测试迁移；完整smoke、实际客户端/Mixin/GUI/多人、双加载器完整打包未完成，不宣称检查点完成，不提交推送。

## 2026-11-01 接续：独立 Figura 即时 GUI 测试迁移

- 当前M3–M5合并检查点未完成；分支 `1.21.1`、HEAD `af23872`，累计修改未提交/推送，M6未开始。
- CodeGraph查询LibraryPreviewRuntimeSmokeTest、LibraryPreviewSession、ParticleEngineAccessor；minecraft-dev核对1.21.1 GuiGraphics原生scissor/flush逻辑，javap核对BufferSource构造与可覆写消费边界。
- 独立Figura测试从不存在的GuiGraphicsExtractor/GuiRenderState/PictureInPicture队列改为实际GuiGraphics即时入口。真实FRAMES集合前后差集验证一次入口只捕获一个实体绑定；同步检查body顶点和首个Lua render角5，后续两次原直接绘制的角度依次10/15（因为入口现在实际绘制一次，不能再按未绘制的队列计数）。原世界矩阵、Lua tick位置、身体隐藏、模型可见性恢复和无装备/披风层检查保留。
- 组合代际测试在capture之后的首次flush通过测试夹具回调修改root位置，验证真实即时绘制仍读捕获姿态并恢复后续编辑；保留purpose失败、原组合保留、worker代际取消/最新发布和旧帧仍引用原组合等原断言。此完整Figura/组合方法尚未运行，不能标为PASS。
- PreviewGraphics使用原生GuiGraphics/PoseStack/scissor；其Window值对象绑定当前真实GLFW窗口句柄和物理尺寸，finally恢复Minecraft.window，不销毁借用的真实窗口。只将BufferSource顶点目的地及一次性flush回调作为记录夹具，没有修改生产GUI。
- 旧DeferredFiguraQueue/SubmitNodeCollection/FiguraFeatureRenderer夹具删除，世界/GUI粒子坐标检查改为1.21.1实际即时updateMatrices顺序，并恢复原mode/paperdoll/allowMatrixUpdate。披风层进入哨兵改为原生CAPE部件查询；移除已不存在且未使用的ParticleResources getter，保留原有原生SpriteSet getter及不支持路径语义。复核发现一处重复getter后已修正。
- 已验证有限PASS：前台Java21 :common:libraryGuiPortProbe --init-script build/tmp/library-gui.init.gradle 退出0，日志 build/tmp/library-gui-pass.log。probe原样提取新PreviewGraphics/PreviewBuffers及计数辅助，在真实可见窗口执行原生GuiGraphics构造、实际GL scissor启用/坐标/禁用、矩形4顶点消费、flush回调一次性和Minecraft窗口引用恢复，GL_NO_ERROR。没有生成离屏/FBO验证，没有宣称完整GUI像素或Lua/Figura组合通过。
- 已验证FAIL：完整 :common:compileTestJava 最终退出1，487→468→455（重复getter）→454，最终日志 build/tmp/common-test-compile-454.log；LibraryPreviewRuntimeSmokeTest当前0条编译诊断，但全量仍失败，不宣称其完整class/run已通过。正式测试源集未缩减；生产common本轮实际编译通过。
- git diff --check通过。下一步迁移剩余旧GPU/实体/共享材质测试，恢复全量testClasses后实际运行独立Figura、组合、输入等完整库预览原测试，依据真实失败修正夹具/实现。客户端/Mixin/GUI/多人和双加载器完整打包未验证，不提交推送，不宣称检查点完成。

## 2026-11-01 接续：MVP GPU 提交拒绝与真实原生 CPU 批次验证

- M3–M5合并检查点继续；分支 `1.21.1`、HEAD `af23872`，累计修改未提交/推送，M6未开始。
- 重新核对正式计划M5：已批准选项A/MVP保留Java并禁用GPU；旧gpuAcceleration/nativeSmoke/性能验收属于选项B。当前没有实现GPU设备/围栏/合并pass，不把这些能力标为通过。
- CodeGraph核对GpuSubmission/GpuSubmissionSmokeTest、AccelerationService、旧GpuAccelerationSmokeTest和Java/MeshHandle数据接口；对staleness提示的Java/MeshHandle读取当前源码。minecraft-dev核对1.21.1 MeshData/VertexSorting真实批次索引和透视/正交排序。
- GpuSubmissionSmokeTest迁移为已批准MVP的提交契约，正式任务名保留，描述同步更新；不再依赖已删除GpuDeviceAdapter/GpuDrawContext/GpuRetirement等FakeDevice框架。GpuAccelerationSmokeTest及其余旧GPU测试尚未迁移，不能据此标为通过。
- 已验证有限PASS：前台Java21 :common:gpuSubmissionSmokeTest --init-script build/tmp/mvp-submission.init.gradle 退出0，日志 build/tmp/mvp-submission-pass.log。真实MultiBufferSource/BufferBuilder构造、真实MeshData与原生索引排序，仅最终RenderType.draw以检查器替代GPU绘制；不需要/不宣称GPU像素证据。
- 该完整MVP测试入口覆盖GPU拒绝不消费caller emitter或提前flush；caller用实际AccelerationService/JavaBackend变换并向真实VertexConsumer完整输出一次；原生opaque面序、透视透明4/3/2/1与正交1/2/3/4跨提交排序；65姿态/3帧直接几何编辑；请求merge开/关均保持CPU结果；owner/frame/resource/backend失效在caller发射前拒绝；100次真实本机内存批次周期，MeshData.close后访问被拒绝；JFR一帧真实CPU输出不伪报GPU draws/poseUploads/poseBytes/passes。
- 旧GPU分组、围栏、部分已issue GPU失败、GPU缓存和共享pass测试不适用于MVP，不伪造其PASS；如果后续选择B恢复GPU，这些原设备/部分issue/fence验收必须重新实现。当前原始测试历史可从HEAD af23872查看。仍保留支持的CPU几何、排序、变换、所有权、回退与诊断要求，没有将GPU成功视为MVP可用能力。
- 临时init仅定向编译GpuSubmissionSmokeTest单类，不能替代完整testClasses。正式测试源集没有排除文件。
- 已验证FAIL：完整 :common:compileTestJava 退出1，454→408，最终日志 build/tmp/common-test-compile-408.log；生产common本轮实际编译通过。剩余摘要 build/tmp/common-test-error-summary.txt：GpuAccelerationSmokeTest127、YsmVanillaState50、YsmHandAnimation28、AccelerationGpuRouteProbe25、YsmMaterialNative14等。
- git diff --check通过，已审查新增测试与旧设备依赖移除边界。下一步继续其余GPU MVP/真实实体状态测试迁移，恢复完整testClasses及所有适用smoke；完整库预览/GUI输入/客户端Mixin/多人和双加载器完整打包未验证。检查点未完成，不提交推送。

## 2026-11-01 接续：已有硬件配置与共享数值 smoke 实际通过

- M3–M5合并检查点未完成；分支 `1.21.1`、HEAD `af23872`，累计修改未提交/推送，M6未开始。本轮没有修改生产或正式测试Java源，补充了原有适用测试的真实执行证据。
- CodeGraph核对HardwareAcceleration、HardwareAccelerationSmokeTest、AccelerationContractSmokeTest、GpuBatchData/GpuShaderProgram及ConfigManager/PlatformUtils；确认已有配置/控制器和数值契约测试覆盖MVP通用要求，后续不重复编造这些断言来替换旧GPU测试。
- 第一次定向配置测试FAIL于缺少测试平台PlatformUtilsImpl，补入后又缺FiguraModMetadataImpl。根因是临时init只选了单个测试源而漏掉仓库已有的JVM辅助类；读取已有test/fabric实现，补回4个现有辅助类。未新增平台假实现、未改生产配置路径、未改变测试断言。目录由已有figura.test.directory隔离，不读写实际用户配置。
- 已验证有限PASS：前台Java21 :common:hardwareAccelerationSmokeTest --init-script build/tmp/hardware-mvp.init.gradle 退出0，日志 build/tmp/hardware-mvp-pass.log。运行原完整main，包括默认disabled/auto、临时修改不触发probe、撤销/保存/旧配置迁移/默认重置、偏好保留而GPU选项实际关闭、生产HardwareAcceleration.probe的AUTO/GPU版本拒绝、控制器帧边界激活/去重、延迟聊天去重、结构化fallback、资源重载重试、运行失败Java重放、英文/中文原因本地化和NativeLibraryLoader未加载断言。SIMD状态探针为原有合成backend，不能冒称真实JNI/SSE内核通过。
- 已验证有限PASS：前台 :common:accelerationContractSmokeTest --init-script build/tmp/acceleration-contract.init.gradle 退出0，日志 build/tmp/acceleration-contract-pass.log。原完整main验证独立系数矩阵、负缩放/法线、输入/输出/UV/骨骼所有权、五权重不截断、vanilla PoseStack数值、frame/resource/model以及捕获期间失效、残缺面拒绝、三角绕序。
- 临时init分别只编译对应测试与已有平台辅助类，不能替代完整testClasses；正式测试源集未排除文件。本轮未改正式源码，因此没有重复运行已知失败的全量编译；最新全量结果仍为408错误、build/tmp/common-test-compile-408.log，不宣称已经减少或全量通过。
- 旧GpuAccelerationSmokeTest仍127处诊断，DualEngineGpuReplay还共享其FakeDevice/FakeResource，后续须按MVP与受支持的CPU数据/实际渲染逐项迁移，不能简单删辅助类导致回放断言丢失。旧GPU绘制/围栏/缓存/SSBO能力未实现，仍不标为PASS。
- git diff --check通过。下一步继续这些GPU专用夹具及真实实体状态测试，恢复完整testClasses后运行完整库预览/材质/输入等适用smoke；实际客户端/Mixin/GUI/多人、双加载器完整打包未验证，不提交推送。


## 2026-11-01 接续：原版船只状态迁移与定向验证

- 用户再次确认允许合并 M3–M5 验收/提交检查点；正式计划 §5 已记录该批准，M6 保持独立。分支 `1.21.1`，本地 HEAD 与 origin/1.21.1 均为 `af23872b6c1909779ce829db4dd833f18da7b4bb`。累计工作区修改未提交，检查点未完成，未提交或推送。
- CodeGraph 与 minecraft-dev MCP 均实际调用成功：前者返回当前 YsmVanillaStateSmokeTest 的船只测试，后者返回 MC 1.21.1 Boat 原版源码，确认竹筏由 BAMBOO 变种表示。
- YsmVanillaStateSmokeTest 的机械迁移已写入：移除旧版本不需要的空 bindComponents 初始化、原版箭包名/float fallDistance/状态效果名/ClientLevel 构造签名回退；船只改为 Boat/ChestBoat × OAK/BAMBOO，保留四种组合和原有断言。尚未迁移完整鞘翅动画与新物品组件语义，不能宣称该测试完整通过。
- 已验证 FAIL：最近一次完整 :common:compileTestJava 375 错误（此前408）；构建完整结束，证据 build/tmp/common-test-compile-375.log。未截断正式源集或编译器错误数量。
- 已验证有限 PASS：前台 Java21 :common:vanillaBoatPortProbe --init-script build/tmp/vanilla-boat.init.gradle，退出0，日志 build/tmp/vanilla-boat-pass.log。本次 common 生产编译通过。探针直接提取当前正式测试的完整 testBoatQueries 方法，在原生 Boat/ChestBoat 与原生 SynchedEntityData 上执行四种组合，覆盖标量签名、空实体门控、非船对象、左右划桨、部分 tick 插值、竹筏/箱船偏移、直接船只主体、跨上下文隔离与控制乘客缺失。
- 探针是临时选择源编译，仅限船只查询证据，不是完整 testClasses、完整 YsmVanillaStateSmokeTest 或真实客户端验收 PASS；正式 common/build.gradle 源集未因该探针排除文件。探针与日志位于 gitignored build/tmp。
- 下一步最小任务：按 1.21.1 的真实鞘翅状态和受支持武器语义继续迁移剩余实体状态测试，然后恢复完整 testClasses 与所有适用 smoke；GUI/客户端/Mixin/多人及 M6 双加载器打包仍未验证。禁止 offscreen；保持 CPU 回退、GPU MVP 关闭，不扩大到完整 GPU 恢复。


## 2026-11-01 接续：原版鞘翅模型与手部查询验证

- M3–M5 合并检查点继续进行，M6 未开始；分支 `1.21.1`，HEAD `af23872`，累计修改仍未提交/推送。上轮船只迁移与定向运行属于有效进展。
- 安全检查点可靠额度接口返回 primary usedPercent=13、windowDurationMins=10080、resetsAt=1793800823，即该窗口剩余87%；未触发额度交接，不调用 Luna，不从 planType 推断 Plus/Pro。
- CodeGraph 核对 YsmVanillaStateSmokeTest 与 MolangBindings 鞘翅查询；minecraft-dev 实际读取 1.21.1 ElytraModel 和 FoodProperties。未改变生产语义来适配测试。
- 正式测试移除不存在的 ElytraAnimationState/GLIDER 夹具，改为原版 ElytraItem 装备判定与原生 ElytraModel 的实际模型角度对照。覆盖空装备、原版鞘翅、零数量、普通胸甲；非玩家 standing/crouching × flying/nonflying × 四种速度；玩家原版模型两帧平滑后的 X/Y/Z 角度转度数，并检查不同渲染时间及 NaN 不重复平滑。旧26.1 GLIDER组件和旧/新角度插值不属于1.21.1 API，未冒称旧组件能力通过。
- 已验证有限 PASS：原正式 testElytraQueries 方法与原 FixtureEntity、FixtureElytraPlayer 提取到临时探针，前台 :common:vanillaElytraPortProbe --init-script build/tmp/vanilla-elytra.init.gradle 退出0；日志 build/tmp/vanilla-elytra-pass.log。该项使用原版 ElytraModel.setupAnim 与真实模型角度，不是公式自复制；仍不是游戏画面/客户端集成验收。
- 三叉戟恢复直接使用1.21.1原生物品用法，去除不存在的Consumable覆写；手部耗时夹具将2秒/40tick输入改为栈级DataComponents.FOOD与原生FoodProperties，避免改变全局APPLE注册组件。原40tick、7已用、33剩余等断言保留。
- 已验证有限 PASS：提取完整原 testHandQueryContracts 与原FixtureEntity，前台 :common:vanillaHandPortProbe --init-script build/tmp/vanilla-hand.init.gradle 退出0；日志 build/tmp/vanilla-hand-pass.log。覆盖实体门控、标量类型、原生食用动画/使用耗时/挥动/受伤、控制器捕获、睡眠抑制、NaN、卸载和上下文清理。正式测试源集没有排除文件。
- 已验证 FAIL：完整 :common:compileTestJava 错误375→362，日志 build/tmp/common-test-compile-362.log；本轮 common生产编译通过。完整 YsmVanillaStateSmokeTest 仍受两处新版 KineticWeapon/WOODEN_SPEAR 夹具阻塞，不能宣称全类通过；其他GPU/材质等测试也仍未迁移完。
- 已审查修改差异；交付前 git diff --check。下一步最小任务仍为受支持的1.21.1武器/兼容标签测试迁移（不得用不等价物品假称新版原生spear kind4已验证），随后完整testClasses与适用smoke。GUI/客户端/Mixin/多人和双加载器打包仍未验证。GPU采用原MVP关闭/CPU回退，禁止Qt offscreen，不自动启动游戏或覆盖mods。


## 2026-11-01 接续：完整原版状态 smoke 恢复

- 上轮鞘翅/手部迁移与验证为有效进展。本轮继续当前 `1.21.1` 分支，M3–M5 合并检查点仍未完成，M6 未启动；HEAD `af23872`，累计工作区修改未提交/推送。
- CodeGraph 核对 YsmWeaponQuery、YsmHandItemType、YsmVanillaStateSmokeTest、YsmHandAnimationSmokeTest 与 YsmAnimationPlayer；minecraft-dev 核对原版 BuiltInRegistries 的引导/冻结顺序。没有通过解除注册表冻结、伪造新版物品或修改生产判定来满足夹具。
- YsmVanillaStateSmokeTest 剩余两组 KineticWeapon/WOODEN_SPEAR 夹具迁移为真实 sparkle_morpher:pike 兼容标签。保留 kind2/长枪分类、0.35突刺边界、jab/lunge优先级、左右手隔离和标签恢复；新增确认标签不会凭空给普通物品 SPEAR 使用动画或可蓄力状态。原生三叉戟仍覆盖正向原生SPEAR使用动作。旧26.1原生spear kind4与KineticWeapon组件在1.21.1不存在，不将此替代测试标成这些能力的PASS；带自定义SPEAR使用动画的第三方长枪正向蓄力尚未验证。
- 已验证 PASS（完整单类 main）：前台 Java21 :common:ysmVanillaStateSmokeTest --init-script build/tmp/vanilla-state.init.gradle 退出0，日志 build/tmp/vanilla-state-pass.log。执行原完整runTests调用序列，覆盖时间/眨眼/鞘翅/武器/参考查询、相对方块、世界/生物/运动/实体状态、输入、手部/装备函数、标签、身份、四种船只、相机几何、附魔、遍历、核心动作、控制器装备、类型匹配、骑乘与投射物发射物品。
- 临时init仅选中该正式测试及仓库已有4个测试平台辅助类，未删减该类main或正式Gradle源集。此单类通过不能替代全量testClasses或真实客户端验收。
- 已验证 FAIL：全量 :common:compileTestJava 完整退出1，362→354错误，日志 build/tmp/common-test-compile-354.log；YsmVanillaStateSmokeTest已无编译诊断。common生产编译本次再次通过。剩余文件诊断摘要 build/tmp/common-test-error-summary.txt。
- 已核查下一项 YsmHandAnimationSmokeTest（本轮未修改）：旧DIAMOND_SPEAR/KineticWeapon贯穿main、分类、资源回放与蓄力时间断言；须保留其持有/使用/释放、左右手、控制器阶段所有权、时间线等适用要求，不能简单删除原断言。共享YsmMaterialNativeSmokeTest仍阻止这些完整main编译；后续按1.21.1实际能力逐项处理，不伪称其整体通过。
- git diff --check已通过修改的实体状态测试；交付前继续核对文档差异。下一步继续手部动画与共享材质夹具迁移，恢复完整testClasses及适用smoke；GUI/客户端/Mixin/多人/M6仍未验证，不提交推送，不自动启动游戏或覆盖mods，保持GPU MVP关闭和CPU回退。


## 2026-11-01 接续：共享材质夹具与脱离世界预览 idle

- 上轮完整原版状态 smoke 与武器迁移为有效进展。本轮继续 `1.21.1` / HEAD `af23872`，M3–M5 检查点仍未完成，累计修改未提交/推送，M6未开始。
- CodeGraph 核对 YsmMaterialNativeSmokeTest、YsmEntityFrame、YsmRenderer、YsmModelRuntime、YsmAnimationPlayer 与 Avatar.MolangContext；minecraft-dev实际读取1.21.1 RenderType。共享材质测试从不存在的LivingEntityRenderState迁移为不可变YsmEntityFrame+显式RunningEntity输入，保留walk/run/idle切换、控制器相位、行走动画回退和姿态断言。
- 共享fixture把Bootstrap提前到EntityRenderDispatcher初始化之前；为Unsafe构造的运动实体补齐原生position/deltaMovement/walkAnimation。未修改生产实体构造。旧SubmitNodeStorage排序桶不属于1.21.1，去除该不存在API的断言，保留renderWithAttachments真实调用顺序断言与独立body顺序元数据核对；不假称覆盖新版sorted feature buckets。
- 材质断言改用原版CompositeState透明/输出状态及原版entityCutout/entityTranslucentCull对象。关闭all_cutout的不透明路径断言修正为1.21.1现有entityCutoutNoCull；未改变生产剔除行为。修复默认预览替代声明分支引用已越域的state变量。
- 已验证有限PASS：前台Java21 :common:materialStatePortProbe --init-script build/tmp/material-state.init.gradle，直接提取原正式测试的7个完整方法：testControlledBasePlayback、testControllerChannelOwnership、testRunningControllerPhase、testWalkingUsesAvailableLocomotion、testLayerOrder、testCullingProperty、testPreviewLighting，保留原Fixture/RecordingClient/RecordingTextures等；退出0，日志build/tmp/material-state-pass.log。NativeGlWindow实际创建可见GL窗口并正常清理，测试限于模型/材质状态和调用顺序，不宣称游戏画面正确。
- 扩展执行原testPreviewDefaultPose时，发现真实行为FAIL：脱离世界预览的null实体被updateNativeStateMachine直接拒绝，独立默认idle不启动。当前M4要求恢复预览，故做最小生产修复：仅当runtime.isPreviewRuntime且存在frame时允许无实体选择IDLE；普通无实体世界更新仍返回，实体路径继续原Snapshot解析。
- 已验证有限PASS：包含上述7方法及原完整testPreviewDefaultPose的扩展探针退出0，日志build/tmp/material-state-default-pose-pass.log。覆盖独立idle、禁止启动GUI声明动画、世界动画时钟不前进、嵌套/循环变量隔离、几何/alpha/纹理隔离、同帧不重复更新、控件变更重建、借用纹理更新、设置预览和省略/无效preview声明回退。初次扩展确实失败后修复，不以减弱该断言换PASS。
- 已验证：本轮 :common:compileJava、:fabric:compileJava、:neoforge:compileJava 均完成；同一前台命令随后全量 :common:compileTestJava FAIL，354→339→337错误，最终日志build/tmp/common-test-compile-337.log。共享YsmMaterialNativeSmokeTest已无编译诊断，但完整共享main仍未运行；临时提取探针不替代正式完整testClasses。正式Gradle测试源集未排除文件。
- 已审查生产最小修复diff并运行git diff --check通过。下一步继续YsmHandAnimationSmokeTest的新版KineticWeapon/DIAMOND_SPEAR夹具，以及共享main引用的其他未迁移测试，恢复完整testClasses与适用smoke。真实客户端/Mixin/GUI视觉/多人与M6仍未验证，保持GPU MVP关闭/CPU回退；禁止offscreen，不自动启动游戏或覆盖mods。


## 2026-11-01 接续：加载进度即时字体入口迁移

- 上轮共享材质与预览idle修复为有效进展。本轮继续M3–M5合并检查点，`1.21.1` / HEAD `af23872`，累计修改仍未提交/推送；M6未开始。
- CodeGraph核对AvatarLoadProgressSmokeTest、AvatarAsyncLoadSmokeTest、ParticleEngineAccessor、AvatarLoadingRenderer、AvatarManager.getLoadingProgress与EntityFrame；minecraft-dev读取1.21.1 Font和EntityRenderDispatcher。javap核对原版final字体/附件入口与EntityDimensions.withAttachments。
- AvatarLoadProgressSmokeTest去除不存在的SubmitNodeCollector/AvatarRenderState/CameraRenderState夹具，直接调用生产AvatarLoadingRenderer.render。使用原生EntityFrame/RemotePlayer子类、原生EntityDimensions附着点与Camera；已有Minecraft fixture中临时替换Font/Options/level，结束恢复所有字段、摄像机、panic和LOADED_USERS原值。Font只记录真实渲染入口的边界调用，不冒称像素/字体视觉验证。
- 保留原字节计数、已知/未知总量、过时请求、网络清理与HTTP大小限制断言。即时入口验证一次标签恰好产生SEE_THROUGH和NORMAL两通道，50%文本、居中宽度、NAME_TAG上方2.45位置、0.025缩放、两通道颜色/背景、输入光照、调用方PoseStack恢复；隐形/panic/65格距离/完成加载均不再绘制。原新版单次队列提交断言改为1.21.1实际双通道行为，未改生产渲染。
- AvatarAsyncLoadSmokeTest移除1.21.1不存在且生产接口已删除的ParticleResources导入/只抛异常getter；保留粒子清理/创建/生成/精灵/删除接口与全部异步发布断言。本轮只验证其已无编译诊断，尚未执行完整异步main，不冒称通过。
- 已验证有限PASS：前台Java21 :common:materialStatePortProbe --init-script build/tmp/material-state.init.gradle退出0，日志build/tmp/loading-label-pass.log。临时入口执行正式AvatarLoadProgressSmokeTest完整run，并重跑上轮8个材质/预览方法；已有NativeGlWindow创建可见窗口，测试限于调用/数值/材质状态。原3处夹具编译失败(final font/getAttachments)及附件字段定位失败已修复；没有修改生产final访问限制或新增测试专用生产路径。
- 已验证FAIL：完整 :common:compileTestJava退出1，337→322错误，日志build/tmp/common-test-compile-322.log；AvatarLoadProgressSmokeTest与AvatarAsyncLoadSmokeTest已无诊断，common生产编译完成。临时入口不替代完整testClasses；正式测试源集未排除文件。
- 验证安全检查点可靠额度接口返回primary已用14%、10080分钟、重置1793800823，即该窗口剩余86%，不触发Luna交接；不推断订阅类型。
- 已审查修改并git diff --check。下一步继续手部动画/粒子/声音等剩余共享依赖与GPU MVP测试迁移，恢复完整testClasses、完整共享材质及库预览smoke。实际客户端/Mixin/GUI视觉/多人/M6仍未验证，GPU默认关闭保留CPU，禁止offscreen，不自动部署mods或启动游戏。


## 2026-11-01 接续：完整声音绑定 smoke 恢复

- 上轮加载进度即时入口迁移为有效进展。本轮继续M3–M5合并检查点；分支1.21.1、HEAD af23872，累计工作区修改未提交/推送，M6未开始。
- CodeGraph核对YsmSoundNativeSmokeTest、YsmStabilitySmokeTest、YsmAudioResources和SoundEngineMixin；minecraft-dev实际读取1.21.1 SoundBuffer，确认该版本没有size()，尚未上传的PCM保存在私有data ByteBuffer。
- 新增仅测试用NativeSoundBufferState.pcmBytes，读取原生PCM.remaining；数据缺失明确报错，不猜测大小、不改变生产SoundBuffer/音频路径。声音/稳定性夹具复用该查询，保留5360字节解码断言与原释放字节计数。
- YsmSoundNativeSmokeTest迁移原版Arrow包、User.Type.MSA、NBT列表默认查询、SoundEngineMixin原生getVolume签名；移动中断从已删除LivingEntityRenderState改为YsmEntityFrame+显式AudioEntity，继续验证移动取消动作/BGM。记录客户端补标准InactiveProfiler，修复完整main实际运行时的空profiler初始化失败；未改生产profiler。
- 已验证PASS（完整单类main）：前台Java21 :common:ysmSoundNativeSmokeTest --init-script build/tmp/sound-native.init.gradle退出0，日志build/tmp/sound-native-pass.log。执行原完整runTests，覆盖ID/参数/flags/短路、权限与配额、音量增益更新、零音量抑制、同名跨Avatar/控制器/动画作用域隔离、播放替换与停止、位置跟随、控制器刷新保留实例/代际、movement/switch/restart/stop/fade/finish/remote中断、真实Opus资源装配与5360字节PCM、SoundAPI、子实体共享PCM/独立声音循环、物理移除、保留对象失效及容器清理。
- 该测试使用真实LuaSound和记录SoundEngine/ChannelHandle，未打开OpenAL/音频设备，不冒称可听播放验证；输出中的历史M4/M5/M6标签不表示本移植检查点完成。
- 临时init仅编译该正式声音测试、已有Opus测试支持与4个已有平台辅助类及NativeSoundBufferState；正式测试源集未排除文件，不替代完整testClasses。
- 已验证FAIL：全量 :common:compileTestJava退出1，322→307错误，日志build/tmp/common-test-compile-307.log；声音与稳定性文件均已无诊断，common生产编译完成。完整语料稳定性100次切换/语料音频解码尚未运行，不将其统计和资源上限改为较小验收。
- 已审查修改并git diff --check；收尾仅整理新增helper导入与行宽，不改变行为。下一步继续手部动画/粒子/GPU MVP等其余依赖，恢复完整testClasses、共享材质与库预览smoke。实际客户端/Mixin/GUI视觉/多人/M6仍未验证，不提交推送，GPU默认关闭并保留CPU，禁止offscreen，不自动部署mods或启动游戏。


## 2026-11-01 接续：原生粒子完整main与自定义粒子生命周期

- 上轮完整声音测试为有效进展。本轮继续M3–M5合并检查点；1.21.1分支、HEAD af23872，累计修改未提交/推送，M6未启动。
- CodeGraph核对YsmParticleNativeSmokeTest、CustomParticleNativeSmokeTest与FiguraTexture持有/关闭逻辑；minecraft-dev读取1.21.1 Particle。YSM fixture实现原版getRenderType/render，回退ClientLevel构造，去掉已不存在且只抛异常的ParticleResources getter；保留完整生成/绑定断言。
- 已验证PASS（完整单类main）：前台Java21 :common:ysmParticleNativeSmokeTest --init-script build/tmp/ysm-particle-native.init.gradle退出0，日志build/tmp/ysm-particle-native-pass.log。真实YsmParticleRuntime生产spawn/LuaParticle调用覆盖原生及Wake寿命、精确ownerUUID与配额/拒绝/补充、缺失provider、暂停、公共Molang粒子参数解析、异步队列数量与坐标/速度、世界变化/模型替换取消、GUI参数副作用抑制、加密诊断隐私与失败恢复；引擎为记录实现，不是实机可视粒子PASS。
- 自定义粒子测试的旧GpuTexture关闭查询迁移为实际GL纹理ID与NativeImageState：原生四边形提取后关闭作者texture并移除粒子，帧持有期间纹理/图片仍存活；finishFrame后GL对象和原生图片均释放。没有更改生产纹理或粒子行为。
- 已验证有限PASS：前台 :common:customParticlePortProbe --init-script build/tmp/custom-particle.init.gradle退出0，日志build/tmp/custom-particle-port-pass.log。直接提取当前正式CustomParticleNativeSmokeTest的完整testLuaAndExtraction、testSharedLimits、testLifecycleHooks（含testNegotiatedLayerLimits与原辅助类），在真实可见GL窗口运行。覆盖Lua包装、native PARTICLE BufferBuilder四顶点/UV/全亮、纹理生命周期、权限/暂停/退役世界/提交失败、协商共享容量/1与8层上限/零上限/草稿禁止、驱逐队列包装、重载与世界重置清理。
- 临时探针只选这些正式方法与原native辅助类，没有修改正式Gradle测试源集。其余CustomParticleNativeSmokeTest.run调用的Ribbon/Anchor/Light/Preview等子组未运行；完整共享材质main未通过，不能将此探针标为全组PASS或GUI外观验收。
- 已验证FAIL：完整 :common:compileTestJava退出1，307→300错误，日志build/tmp/common-test-compile-300.log；上述两份粒子测试已无诊断，common生产编译完成。收尾审查仅去掉修改区域多余空行，git diff --check。
- 下一步继续其余手部动画/粒子子组/GPU MVP等依赖，恢复完整testClasses、共享材质与库预览smoke。实际客户端/Mixin/GUI视觉/多人/M6仍未验证，不提交推送，GPU默认关闭保留CPU，禁止offscreen，不自动部署mods或启动游戏。


## 2026-11-01 接续：GPU MVP smoke与CPU布局回放解耦

- 上轮原生/自定义粒子迁移为有效进展。本轮继续M3–M5合并检查点，1.21.1 / HEAD af23872，累计修改未提交/推送，M6未开始。用户已批准MVP方案A；不是恢复方案B完整GPU。
- CodeGraph核对GpuAccelerationSmokeTest、DualEngineGpuReplay、AccelerationPreparationBenchmark共享设备依赖，以及AccelerationService/Backend、GpuBatchData/GpuRigidDataSmokeTest、FiguraAccelerationReplay、HardwareAcceleration/GpuSubmission；minecraft-dev核对1.21.1 RenderType构造/原生draw入口。
- DualEngineGpuReplay不再继承旧假GPU设备。独立CPU布局解码器通过现有VERTEX_TRANSFORM数值后端接口接入生产发射器，使用真实GpuBatchData顶点/姿态编码并保留原位置/法线/UV数学解码；每批包括齐次w逐项比较JavaAccelerationBackend结果。GPU_DRAW仍不支持，service.gpuDrawEnabled始终false。YSM分支比较原生Java基线与CPU布局路径的实际几何；不再伪要求不存在的GPU分组提交。刚性palette/多部件布局仍由原GpuRigidDataSmokeTest完整验证。
- 已验证有限PASS：前台Java21 :common:figuraLayoutPortProbe --init-script build/tmp/figura-layout.init.gradle，提取当前原DecodingBackend并运行真实FiguraAccelerationReplay.replay。4次刚性/动态编辑/恢复/加权蒙皮发射共16顶点确实进入解码器，位置/法线/UV与独立Java结果一致，退役提交取消。修复FiguraAccelerationReplay的旧hasBlending查询，改原版CompositeState检查；随后原replay(null)完整Java路径也通过，包括高亮alpha/材质与选中后代行为。最终日志build/tmp/figura-layout-and-java-pass.log，旧初次布局日志build/tmp/figura-layout-port-pass.log。YSM语料回放完整入口尚未运行。
- AccelerationPreparationBenchmark移除已不存在的CountingDevice/GpuAccelerationBackend/retirement假GPU模式，明确GPU_PREPARATION为1.21.1 MVP不支持；保留JAVA/SIMD原始90样本、30预热、3轮轮换与显式QUICK配置，GPU/上传CSV计数为0。DiscardingMaterial按1.21.1原生RenderType格式/模式/大小/排序构造，继续只测CPU准备并关闭MeshData。该基准已无编译诊断，但本轮未运行真实JNI/语料基准、不宣称性能改善或接受任何GPU时间结果。
- 两个共享调用方均移除对GpuAccelerationSmokeTest.RigidDevice/FakeResource的继承后，旧GPU smoke按方案A迁移为原命令的MVP契约入口：实际调用已有HardwareAccelerationSmokeTest、AccelerationContractSmokeTest、GpuRigidDataSmokeTest、GpuSubmissionSmokeTest完整main，避免自创更弱的替代断言。输出明确区分PASS CPU/MVP与N/A GPU执行/设备着色器初始化/缓存/围栏/pose arena/SSBO绘制。原方案B设备测试历史保留在Git HEAD af23872，不冒称这些能力已实现或通过。
- 已验证PASS（完整MVP入口）：前台 :common:gpuAccelerationSmokeTest --init-script build/tmp/gpu-mvp.init.gradle退出0，日志build/tmp/gpu-mvp-pass.log。四组原完整main均实际完成：配置拒绝/迁移/保存与运行回退、不可变数值/生命周期、64-part与65交错part/存储/蒙皮布局、原生BufferSource排序/65姿态/100buffer周期/过期lease/零GPU诊断。此入口通过不能替代全量testClasses、旧方案B验收或实机画面。
- 已验证FAIL：全量 :common:compileTestJava退出1，300→272→127错误，日志build/tmp/common-test-compile-127.log；本轮4份GPU/回放/基准文件均无诊断，common生产编译完成。减少包含移除不适用于MVP的假GPU设备代码，不表示恢复GPU执行。正式Gradle源集没有排除测试。
- 已审查4文件diff并git diff --check；收尾仅移除不再使用的导入。安全检查点可靠额度primary已用14%、10080分钟、重置1793800823，剩余86%，未触发Luna交接，不推断订阅类型。
- 下一步优先迁移剩余YsmHandAnimationSmokeTest（28处）、AccelerationGpuRouteProbe（25处）及其他实体/NBT/粒子夹具；恢复完整testClasses后运行完整共享材质/库预览、YSM语料回放与适用smoke。实际客户端/Mixin/GUI视觉/多人/M6仍未验证，不提交推送，GPU默认关闭保留CPU，禁止offscreen，不自动部署mods或启动游戏。


## 2026-11-01 接续：旧设备探针显式不支持与函数夹具编译恢复

- 上轮GPU MVP/布局回放迁移为有效进展。本轮仍M3–M5合并检查点；1.21.1 / HEAD af23872，累计修改未提交/推送，M6未开始。
- CodeGraph核对AccelerationGpuRouteProbe、YsmFunctionContractSmokeTest、YsmAnimationPlayer控制器起始过渡与YsmControllerRuntime声明槽位；minecraft-dev核对1.21.1 PlayerSkin。未扩大为直接GL GPU加速后端。
- 原H0设备路由探针依赖不存在的GpuDevice/RenderPipeline，属于延后方案B。保留原accelerationGpuRouteProbe命令和报告输出路径，实际调用生产HardwareAcceleration.probe核实AUTO/GPU均以UNSUPPORTED_MINECRAFT_VERSION拒绝，核实GPU绘制关闭。报告明确status=UNSUPPORTED_MINECRAFT_VERSION、indexedDraws=0、translatedPixels=NOT_RUN、visual=NOT_RUN；不伪造窗口/GPU像素/成功路由。
- 已验证报告生成与拒绝检查完成：前台 :common:accelerationGpuRouteProbe --init-script build/tmp/gpu-route-mvp.init.gradle退出0，日志build/tmp/gpu-route-mvp-report.log；这不是原GpuDevice三角像素探针PASS，而是MVP不支持的诊断结果。
- 函数契约测试迁移PlayerSkin六字段/ResourceLocation cape/Model.WIDE、LocalPlayer原生构造签名、FoodProperties栈级40tick输入；移除不存在的Consumable组件和全局APPLE组件改写。保留本地输入隔离/时长和披风可见性断言。
- 函数完整main可编译运行，但尚未PASS。先发现旧fadeSpeed/weight断言与已存在的起始过渡语义冲突；根据生产playInSlot和原YsmBeginningTransitionSmokeTest，将0.25秒/2秒和重载起始过渡改为实际advanceAnimationTimeline时钟冻结/边界/推进检查，保留退出槽淡出断言，未改生产逻辑。
- 已验证PASS（完整已有专门测试）：前台 :common:ysmBeginningTransitionSmokeTest --init-script build/tmp/beginning-transition.init.gradle退出0，日志build/tmp/beginning-transition-pass.log，确认起始过渡冻结时钟、零/中点事件、刷新、速度、超出边界与取消语义。
- 已验证FAIL（完整函数main）：前台 :common:ysmFunctionContractSmokeTest --init-script build/tmp/ysm-function.init.gradle最终失败于testAnimationCompletionQueries的“completed once clip retires through the real controller update”，日志build/tmp/function-contract-runtime-fail.log。当前YsmControllerRuntime会保留声明槽位，旧断言要求立即null；需后续核对既有完成/条件复激活生命周期测试与技术要求，不为消除失败直接修改生产状态机或跳过测试。该函数main尚未运行到后续全部用例，尤其新皮肤/输入夹具目前仅编译验证。
- 已验证FAIL：全量 :common:compileTestJava退出1，127→90错误，日志build/tmp/common-test-compile-90.log；本轮探针/函数文件已无诊断，common生产编译完成。正式Gradle源集未排除测试；临时init不替代完整testClasses。
- 已审查修改并git diff --check。下一步优先剩余28处手部动画夹具及NBT/粒子/实体源迁移；函数main的完成槽位预期仍为明确后续失败项，必须在共同验收前处理。完整共享材质/库预览、YSM语料/准备基准、客户端/Mixin/GUI视觉/多人/M6仍未验证。不提交推送，GPU默认关闭保留CPU，禁止offscreen，不自动部署mods或启动游戏。


## 2026-11-01 接续：库包准备/上传与装扮夹具API

- 上轮GPU诊断/函数夹具迁移为有效进展。本轮继续M3–M5合并检查点；1.21.1 / HEAD af23872，累计修改仍未提交/推送，M6未开始。
- CodeGraph核对OutfitApplyServiceSmokeTest、OutfitFormalPreparationSmokeTest、LibraryPackagePreparationSmokeTest、LibraryUploadSmokeTest、OutfitRenderBinding和OutfitSourceCacheSmokeTest。库包/上传的source_ciphertext与正式准备main_model改用已有NbtDefaults.findByteArray(...).orElseThrow，保留缺字段失败语义，未直接删掉严格检查。
- 正式装扮准备去掉新版空bindComponents重置，使用1.21.1注册物品组件；实体accessor夹具补public getPermissionLevel=0，与原默认无权限一致；ClientLevel/LocalPlayer构造回退原生签名。Apply世界/原版/预览键迁移为EntityFrame，首个partialTick=.5与捕获一致，两处默认capture帧用1。保留resident base/组合身份、frame退役和预览隔离断言。本轮这两份装扮测试仅无编译诊断，尚未执行完整run，不能标PASS。
- 已验证FAIL：完整 :common:compileTestJava退出1，90→74错误，日志build/tmp/common-test-compile-74.log；本轮4文件均已无诊断，common生产编译完成。收尾只补NbtDefaults导入及使新增帧partial与原捕获参数一致，未改生产功能或正式源集。
- 第一次库包定向运行已通过部分缓存分组，后失败于临时入口缺声音管理器、Avatar.clean空指针；正式共享入口原本会安装声音与粒子辅助对象。临时探针补回已有YsmStabilitySmokeTest.soundManager(unsafe,true)和原AvatarAsyncLoadSmokeTest.RecordingParticles，不新增生产假通道，不改变清理逻辑。
- 已验证PASS（完整正式run）：前台Java21 :common:materialStatePortProbe --init-script build/tmp/material-state.init.gradle，调用完整LibraryPackagePreparationSmokeTest.run，退出0，日志build/tmp/library-package-preparation-pass.log。覆盖Figura/plain/encrypted YSM包、原密文/字节哈希、资源/非常规ZIP路径、私有native准备、大小/取消/变更/不可变数据；同时实际运行原OutfitSourceCacheSmokeTest所有调用，覆盖4类source/资源类型组合、本地HTTP保护缓存、新鲜授权、重启/损坏、header/hash错误、读取后撤权竞态、观察者投影与配置修订缓存复用、私有首次上传冻结操作重试与draft退役。
- 已验证PASS（完整正式run）：同一临时入口加入LibraryUploadSmokeTest.run(client::drain)，最终退出0，日志build/tmp/library-package-and-upload-pass.log；完整包准备也再次通过。上传覆盖实际Figura/encrypted YSM请求体、编辑附件声明和native哈希、body/image/commit/JSON/cover丢失响应、稳定CAS、冻结重试、能力/额度变化与取消。原localOutfitPublication外部Rust入口未调用，不冒称真实Rust/生产后端端到端通过。
- 两组测试通过本地HTTP契约服务，窗口为原NativeGlWindow真实可见上下文，但验证限于调用/数据/资源，不表示GUI外观或多人实机通过。临时init不替代完整共享main或testClasses，正式测试源集无排除。
- 已审查修改并git diff --check。后续优先剩余28处手部动画、其余NBT/实体/粒子夹具；函数契约完整main仍失败于完成ONCE声明槽位预期（build/tmp/function-contract-runtime-fail.log），本轮未改该失败或生产状态机。完整Apply/正式装扮准备/共享材质/库预览、语料/客户端/Mixin/GUI/多人/M6仍未验证。不提交推送，GPU默认关闭保留CPU，禁止offscreen，不自动部署mods或启动游戏。


## 2026-11-01 接续：三份 NBT 夹具迁移

- M3–M5 合并检查点继续，M6 独立且未开始；1.21.1 分支，累计修改未提交/推送。
- CodeGraph 核对 YsmMaterialInputSmokeTest、YsmAvifSmokeTest、YsmDataBoundarySmokeTest 与 NbtDefaults；本轮仅修改这三份正式测试的 NBT 读取 API。列表使用已有 NbtDefaults.getListOrEmpty，字节数组使用 findByteArray(...).orElseThrow，保留原资源字节/材质配对/动画与边界断言和缺失字段失败语义。没有更改生产代码或正式源集。
- 已验证 FAIL：前台 Java21 :common:compileTestJava --init-script build/tmp/maxerrs.init.gradle --console=plain 退出1，74→65错误，完整日志 build/tmp/common-test-compile-65.log；这三份文件无编译诊断，common 生产编译通过。三份完整 main/40模型语料运行尚未执行，不能据此标运行时 PASS。
- 已审查本轮修改并执行限定文件 git diff --check。既有函数契约运行失败仍未解决；下一步继续手部动画、原生实体/粒子夹具，恢复完整 testClasses 后执行正式测试。GPU MVP 默认关闭保留CPU，禁止Qt offscreen，不自动启动游戏或部署mods。


## 2026-11-01 接续：延迟姿态完整测试恢复

- 当前 M3–M5 合并检查点继续；分支1.21.1，累计修改未提交/推送，M6未开始。
- CodeGraph核对YsmDeferredPoseSmokeTest与YsmRenderer.applyEntityRotation；minecraft-dev实际读取1.21.1 LivingEntity/Entity，确认getDimensions(Pose)与getEyeHeight(Pose)为final，尺寸通过getDefaultDimensions和getScale取得。
- 本轮仅修正正式测试SleepingEntity：改用public getDefaultDimensions，显式scale=1防止Unsafe夹具读取未初始化属性；入口先SharedConstants.tryDetectVersion/Bootstrap.bootStrap。没有改生产旋转路径、断言或正式源集。
- 中间失败：初次protected覆盖与ArmorStand public不匹配，修正访问性；随后完整main报Not bootstrapped，补标准注册表启动后重跑。
- 已验证PASS：前台Java21 :common:ysmDeferredPoseSmokeTest --init-script build/tmp/deferred-pose.init.gradle --console=plain退出0，日志build/tmp/deferred-pose-pass.log。运行完整正式main，覆盖body/arm延迟矩阵、独立可见性/缩放、只读快照复用与骨骼覆盖隔离、隐藏祖先、过期提交、四床朝向与standing眼高偏移/父位置/无床方向/站立旋转。CPU数值验证，不冒称GUI或实机画面PASS。
- 已验证FAIL：全量 :common:compileTestJava --init-script build/tmp/maxerrs.init.gradle --console=plain退出1，65→64错误，日志build/tmp/common-test-compile-64.log。临时单类init不等于完整testClasses；正式源集未排除测试。
- 已审查diff并git diff --check。下一步继续手部动画与原生实体/粒子夹具；已知函数契约运行失败尚未解决，完整测试/实机/Mixin/GUI/多人仍未验证。GPU默认关闭保留CPU，不自动启动游戏或部署mods，Qt offscreen禁止。


## 2026-11-01 接续：粒子权限与纹理夹具迁移

- M3–M5合并检查点继续，1.21.1分支，累计修改未提交/推送；M6未开始。
- CodeGraph核对CosmeticParticleEntrySmokeTest、AnchorTrailNativeSmokeTest、CreatedTrailParticleSmokeTest与FiguraTexture；minecraft-dev读取1.21.1 SingleQuadParticle确认quadSize仍位于原生类。
- 本轮仅三份测试夹具：Anchor Wearer补public getPermissionLevel返回原默认0；CreatedTrail反射引用补全原生SingleQuadParticle类名；CosmeticParticleEntry以native getId保存纹理ID，增加上传后glIsTexture断言，清理/finishFrame/drain后检查同一GL对象已删除，替代不存在的GpuTexture/getTexture/isClosed。未改生产API、清理顺序或正式源集。
- 已验证FAIL：前台Java21 :common:compileTestJava --init-script build/tmp/maxerrs.init.gradle --console=plain退出1，64→60错误，日志build/tmp/common-test-compile-60.log，三份文件无诊断，common生产编译保持通过。完整三组run尚未执行，GL上传/释放断言尚无运行时PASS证据。
- 已审查修改并限定文件git diff --check；保留原authored Lua/animation marker、world/preview路由、trail样本与资源退役断言。下一步继续剩余手部动画/实体/粒子夹具后恢复完整共享测试；已知函数契约运行失败与实机/Mixin/GUI/多人验证缺口仍存在。GPU默认关闭保留CPU，禁止Qt offscreen，不自动启动游戏或部署mods。


## 2026-11-01 接续：分层粒子与Ribbon完整分组通过

- M3–M5合并检查点继续；1.21.1分支，累计修改未提交/推送，M6未开始。
- CodeGraph读取CustomParticleLayeredSmokeTest和RibbonNativeSmokeTest。本轮两份正式测试的旧getTexture/GpuTexture.isClosed迁移为保存原生getId并用GL11.glIsTexture判断同一GL对象；保留失败替换回滚、旧层帧持有、帧完成与粒子移除释放断言。生产代码与正式源集未改。
- 已验证PASS：前台Java21 :common:customParticlePortProbe --init-script build/tmp/custom-particle.init.gradle --console=plain退出0，日志build/tmp/layered-ribbon-native-pass.log。已有真实可见NativeGlWindow及记录客户端入口直接调用正式CustomParticleLayeredSmokeTest.run和RibbonNativeSmokeTest.run完整分组；原3组自定义粒子方法也再次通过。
- 分层完整覆盖Lua配置、原生UV/tint/light、生产材质与native绘制顺序、拒绝输入、原子纹理lease回滚、帧持有与退役；Ribbon完整覆盖Lua设置、world提取、真实PARTICLE顶点、冻结回放、旋转/teleport、分层光照、共享vertex/trail额度与纹理清理。不是实际游戏像素/GUI/Mixin通过，完整共享main仍未运行；临时init不是完整testClasses。
- 已验证FAIL：全量 :common:compileTestJava --init-script build/tmp/maxerrs.init.gradle --console=plain退出1，60→55错误，日志build/tmp/common-test-compile-55.log；本轮两文件无诊断，common生产编译完成。
- 已审查本轮修改并限定文件git diff --check。下一步剩余手部动画与实体等夹具，恢复全量编译及正式测试；已知函数契约运行失败、客户端/GUI/多人验证仍未完成。GPU默认关闭保留CPU，不自动启动游戏或部署mods，Qt offscreen禁止。


## 2026-11-01 接续：动作与控件完整main通过

- M3–M5合并检查点继续；1.21.1分支，累计修改未提交/推送，M6未开始。
- CodeGraph核对YsmActionRuntimeSmokeTest/YsmControlRuntimeSmokeTest/YsmDefaultAnimationSmokeTest。本轮仅修改前两份：Action移除新版空bindComponents，使用原生注册物品组件；ItemInput.createItemStack增加原版boolean参数，mouseDragged回退五参数签名，ClientLevel构造回退原版；Control列表读写使用已有NbtDefaults.getListOrEmpty。保留原测试断言。minecraft-dev实际读取ItemInput确认boolean只控制最大堆叠检查，夹具数量仍为1。
- 已验证PASS：前台Java21 :common:ysmActionRuntimeSmokeTest :common:ysmControlRuntimeSmokeTest --init-script build/tmp/actions-controls.init.gradle --console=plain退出0，日志build/tmp/actions-controls-pass.log。两个正式完整main均执行：动作按住/释放、冷却/速度/复用代际、跨模型回调、轮盘布局落盘与恢复、所有tab水平wheel/drag/bounds、装扮轮盘无base导航/重复分组/owner/脚本页面/退役；控制器完整Molang表达式、选项读写、循环预算中断与恢复、客户端副作用作用域、重置、模型隔离持久化恢复。
- 此为CPU逻辑/受控客户端测试，不代表原生输入或GUI渲染通过；临时init仅选择正式两类与已有平台helper，不代替完整testClasses。生产代码和正式源集未变。
- 已验证FAIL：全量 :common:compileTestJava --init-script build/tmp/maxerrs.init.gradle --console=plain退出1，55→49错误，日志build/tmp/common-test-compile-49.log，两个文件无诊断。默认动画文件本轮仅查询未修改，仍需实体夹具迁移。
- 已审查修改并限定文件git diff --check。下一步手部动画/默认动画/公共姿态与其余实体夹具，恢复全量测试；既有函数契约运行失败与实机验收仍未完成。GPU默认关闭保留CPU，不自动启动游戏或部署mods，Qt offscreen禁止。


## 2026-11-01 接续：装扮与广播夹具API

- M3–M5合并检查点继续，1.21.1分支，累计工作未提交/推送；M6未开始。
- CodeGraph核对OutfitCompositionSmokeTest和AvatarBroadcastSmokeTest；minecraft-dev读取1.21.1 Minecraft确认toast入口为getToasts。
- 本轮仅两份正式测试：EventWearer补public getPermissionLevel=0匹配原生默认权限；广播失败toast检查使用getToasts，保持429错误toast与localUploaded/progress断言不变。未改生产逻辑或正式测试源集。
- 已验证FAIL：前台Java21 :common:compileTestJava --init-script build/tmp/maxerrs.init.gradle --console=plain退出1，49→47错误，日志build/tmp/common-test-compile-47.log；这两份文件无诊断。完整两组run尚未执行，不能标运行时PASS。
- 已审查diff并限定文件git diff --check。另CodeGraph核对YsmEntityFrame，读取默认动画若干未展示调用区间，确认后续需迁移真实实体/帧输入而非删掉状态机断言；本轮未修改这些文件。
- 下一步手部动画/默认动画/公共姿态等夹具，恢复完整testClasses和正式共享测试；函数契约已知运行失败、实机/Mixin/GUI/多人仍未验证。GPU默认关闭保留CPU，不自动启动游戏或部署mods，禁止Qt offscreen。


## 2026-11-01 接续：手部动画基础夹具回退

- M3–M5合并检查点继续，1.21.1分支，累计修改未提交/推送；M6未开始。
- CodeGraph核对YsmHandAnimationSmokeTest，窄读未展示的分类/变换/HandEntity区间；minecraft-dev读取1.21.1 FoodProperties。尚未迁移不存在的KineticWeapon、DIAMOND_SPEAR与Consumable用例，不删去它们冒充完成。
- 本轮仅该正式测试：去掉旧的空bindComponents调用及对应导入，使用1.21.1内建物品组件；HandEntity改继承原生ArmorStand，保留main/offhand/using/usedHand/主手/时长等显式输入和全部断言，避免LivingEntity新增抽象装备方法；移除不存在且原只返回Float.MAX_VALUE的kinetic反馈override。未改生产逻辑或正式源集。
- 已验证FAIL：前台Java21 :common:compileTestJava --init-script build/tmp/maxerrs.init.gradle --console=plain退出1，47→39错误，日志build/tmp/common-test-compile-39.log。该类仍有新版武器/组件编译错误，完整main未运行，不能标PASS；其余默认动画/公共姿态/宇航员夹具仍待迁移。
- 已审查本轮diff并限定文件git diff --check。下一步按1.21.1真实可支持行为迁移剩余手部用例，保留弓/三叉戟/手选取/hold暂停/循环/pulse/阶段所有权/泛用回退覆盖，对缺失原生长矛能力明确界定而不假造通过。既有函数契约运行失败和实机验收仍未完成。GPU默认关闭保留CPU，不自动启动游戏或部署mods，Qt offscreen禁止。
- 最近安全检查点额度接口返回primary usedPercent=15/windowDurationMins=10080/resetsAt=1793800823，即剩余85%；未触发Luna交接，不据此推断订阅类型。


## 2026-11-01 接续：默认动画原生实体输入与五组运行验证

- M3–M5合并检查点继续，1.21.1分支，累计修改未提交/推送；M6未开始。
- CodeGraph核对YsmDefaultAnimationSmokeTest、YsmEntityFrame与YsmPlayerActionState.snapshot。本轮仅默认动画正式测试：使用测试AnimationInput承载原age/walk值，正常构造原生ArmorStand并显式onGround=true/onClimbable=false，通过YsmEntityFrame和实体驱动生产状态机。保留所有原断言；NBT通过已有NbtDefaults保持strict byte array和列表原位编辑。未改生产代码或正式源集。
- 已验证FAIL：尝试完整YsmDefaultAnimationSmokeTest.run，因工作区不存在refs/Sparkle-Morpher/common/src/main/resources/assets/sparkle_morpher/builtin/default/animations而NoSuchFileException。日志build/tmp/default-animation-missing-reference.log；未用bundled资源冒充独立参考，完整资源一致性/14文件字节比对与引用采样尚未通过。
- 已验证有限PASS：临时MaterialStatePortProbe直接调用正式完整testNativeRuntime、testPostSwingFallback、testBuiltinFallback、testBeginningTransitionPose、testAuthoredDomains五个方法，前台Java21 :common:materialStatePortProbe --init-script build/tmp/material-state.init.gradle --console=plain退出0，日志build/tmp/default-animation-runtime-pass.log。真实Avatar/原生实体路径覆盖作者空idle优先、缺失walk默认骨骼姿态、v1 inventory/schema稳定、显式禁用、损坏声明保护、postswing与builtin/custom同帧切换、起始过渡冻结/姿态/事件时点、主/手臂域隔离。未改或删除完整入口中的参考资源验收。
- 原材质8方法、加载进度、库包与上传完整run也在同入口再次通过；不代表完整共享main/testClasses/GUI/实机/Mixin通过。窗口使用既有NativeGlWindow真实可见上下文。
- 已验证FAIL：全量 :common:compileTestJava --init-script build/tmp/maxerrs.init.gradle --console=plain退出1，39→33错误，日志build/tmp/common-test-compile-33.log；默认动画文件无诊断，common生产编译完成。
- 已审查本轮修改并限定文件git diff --check。下一步手部新版武器、公共姿态和宇航员实体夹具，以及参考资源位置恢复；既有函数契约运行失败与实机验收仍未完成。GPU默认关闭保留CPU，不自动启动游戏或部署mods，Qt offscreen禁止。


## 2026-11-01 接续：公共姿态与宇航员夹具编译迁移

- M3–M5合并检查点继续，1.21.1分支，累计修改未提交/推送；M6未开始。
- CodeGraph核对YsmPublicPoseSmokeTest与YsmAstronautVisibilitySmokeTest。本轮仅两份正式测试：旧LivingEntityRenderState替换为YsmEntityFrame与正常构造的原生ArmorStand，明确地面/非攀爬输入；保留40帧idle/walk切换、6帧双手、三个pitch各60帧、40度相对yaw及重复body pass的原断言。实体tickCount与帧时钟保持对应，未改生产动画逻辑、模型或正式源集。
- 已验证FAIL：前台Java21 :common:compileTestJava --init-script build/tmp/maxerrs.init.gradle --console=plain退出1，33→28错误，日志build/tmp/common-test-compile-28.log；上述两文件无诊断，common生产编译通过。
- 未验证：两份完整模型回放main未运行，不能将编译无诊断视为头部追踪/左右手隔离/FOX可见性PASS；当前工作区refs目录缺失，实际模型与Sparkle独立参考资源需恢复或定位。保留全部原模型路径和断言，不以合成模型替代真实语料验收。
- 已审查本轮diff并限定文件git diff --check。下一步处理剩余手部新版API，恢复全量testClasses与正式共享测试；既有函数契约运行失败、独立参考资源和客户端/GUI/多人证据缺口仍未解决。GPU默认关闭保留CPU，不自动启动游戏或部署mods，禁止Qt offscreen。


## 2026-11-01 接续：三叉戟变换真实1.21.1契约

- M3–M5合并检查点继续；1.21.1分支，累计修改未提交/推送，M6未开始。
- CodeGraph核对YsmHandItemType、YsmAnimationPlayer手部选择与YsmHeldItemTransforms；minecraft-dev核对Bootstrap/BuiltInRegistries/ItemStack。原生ItemStack为final，注册表在标准bootstrap中冻结；不通过解冻注册表或伪造新原版物品来冒称支持26.1长矛。
- 正式计划第285行要求KineticWeapon/Consumable改为1.21.1 ItemStack/UseAnim路径；进度表第139行已记录原版KINETIC_WEAPON变换降级。当前生产YsmHeldItemTransforms只有握持基底和弓缩放，没有旧长矛raising/tired/disengaged阶段旋转。
- 本轮仅YsmHandAnimationSmokeTest.verifyUseTransforms迁移：保留全部主手方向/使用手/渲染手组合，额外覆盖directHandBone真/假；对真实Items.TRIDENT独立构造精确基底矩阵，检查0/1/5/10/25/40/55/70/100使用tick不叠加动能旋转。旧26.1长矛阶段明确输出N/A，不冒称该能力PASS；其余长矛分类/选择用例仍未迁移，正式类仍编译失败。生产路径和正式源集未改。
- 已验证有限PASS：临时HandTransformPortProbe从当前正式文件原样提取完整verifyUseTransforms、HandEntity与require，标准bootstrap后运行生产YsmHeldItemTransforms；前台Java21 :common:handTransformPortProbe --init-script build/tmp/hand-transform.init.gradle --console=plain退出0，日志build/tmp/hand-transform-pass.log。纯CPU矩阵测试，无GUI/实机画面声明，不是完整手部main PASS。
- 已验证FAIL：全量 :common:compileTestJava --init-script build/tmp/maxerrs.init.gradle --console=plain退出1，28→23错误，日志build/tmp/common-test-compile-23.log。剩余手部KineticWeapon/DIAMOND_SPEAR/Consumable相关用例需继续迁移。
- 已审查本轮修改并限定文件git diff --check。独立参考与实际模型语料路径缺失、函数契约运行失败及客户端/GUI/多人验证仍未解决。GPU默认关闭保留CPU，不自动启动游戏或部署mods，禁止Qt offscreen。


## 2026-11-01 接续：手部分类分组原生契约通过

- M3–M5合并检查点继续，1.21.1分支，累计修改未提交/推送；M6未开始。
- CodeGraph核对手部正式文件；minecraft-dev读取Item/ItemStack确认原生getUseAnimation由Item方法提供，标签只分类，ItemStack为final。未伪造新版Consumable数据或解冻注册表。
- 本轮仅迁移verifySpearClassification，保留并加强实际可验证的原生路径：普通bow精确ID、pike标签分类为lance不为spear、原生NONE使用动画不被标签改变、无SPEAR时generic use而非charge、lance_stand握持优先、三叉戟独立分类。借用STICK holder的测试标签在finally恢复。正向第三方注册SPEAR物品蓄力明确输出PENDING，不冒称该断言已验证；旧原版DIAMOND_SPEAR/KINETIC_WEAPON/Consumable专项不适用于1.21.1。
- 已验证有限PASS：临时HandTransformPortProbe原样提取正式verifySpearClassification、player/clips/targeted及原HandEntity，调用生产updateNativeHandAnimations；前台Java21 :common:handTransformPortProbe --init-script build/tmp/hand-transform.init.gradle --console=plain退出0，日志build/tmp/hand-classification-and-transform-pass.log。上一轮完整变换方法也再次通过。该受控原生物品/标签契约不是第三方模组集成、完整手部main或实机GUI验证。
- 已验证FAIL：全量 :common:compileTestJava --init-script build/tmp/maxerrs.init.gradle --console=plain退出1，23→11错误，日志build/tmp/common-test-compile-11.log。剩余原版长矛的main/实际模型资源选择与采样代码仍需迁移，正式源集没有排除测试。
- 已审查修改并限定文件git diff --check。既有函数契约运行失败、独立参考/实际模型路径及客户端/GUI/多人验证仍待解决。GPU默认关闭保留CPU，不自动启动游戏或部署mods，禁止Qt offscreen。


## 2026-11-01 接续：完整测试源集编译恢复与手部核心通过

- M3–M5合并检查点继续，1.21.1分支，累计修改未提交/推送；M6未开始。这里只恢复编译及部分验证，不满足整个检查点验收。
- CodeGraph核对YsmHandAnimationSmokeTest。本轮剩余原版DIAMOND_SPEAR/KINETIC_WEAPON代码按正式计划第285行移植：main以原生TRIDENT验证握持不误入use和缺失use保留grip，明确新版动能长矛N/A；模型回放改为原生三叉戟throw clip、elapsed clock、骨骼姿态/复用/release；第二模型改为pike标签但NONE使用动画的实际资源负向检查，禁止凭分类借用charge/trident。正向第三方注册SPEAR物品蓄力仍PENDING。保留弓/三叉戟/手选取/hold pause/loop/pulse/阶段所有权/默认泛用回退与所有受支持核心断言。生产代码与正式源集未改。
- 已验证PASS：全量前台Java21 :common:compileTestJava --init-script build/tmp/maxerrs.init.gradle --console=plain退出0，日志build/tmp/common-test-compile-pass.log，之前11错误归零。maxerrs脚本只提高javac诊断数量，没有选择/排除测试文件。
- 已验证有限PASS：正式YsmHandAnimationSmokeTest.main无语料参数的全部核心分组，前台 :common:ysmHandAnimationSmokeTest --init-script build/tmp/hand-core.init.gradle --console=plain退出0，日志build/tmp/hand-core-pass.log。init仅清空缺失refs模型参数并设置figura.test.directory，未改compileTestJava源集；首次缺目录系统属性导致PlatformUtils初始化失败，补临时测试属性后成功。两个实际模型分支未运行，不能标完整带语料task PASS。
- 未验证：迁移后的实际模型三叉戟回放/非SPEAR负向资源检查；独立参考与语料目录缺失。既有函数契约ONCE槽位运行失败、完整共享材质/库预览、全量正式smoke、客户端/Mixin/GUI/多人仍未完成。
- 已审查本轮修改并限定文件git diff --check。下一步从已恢复的完整testClasses运行正式共享测试并修复真实运行失败，定位参考/语料路径。GPU默认关闭保留CPU，不自动启动游戏或部署mods，禁止Qt offscreen。


## 2026-11-01 接续：正式共享入口恢复推进

- M3–M5合并检查点继续，1.21.1分支，累计修改未提交/推送；M6未开始。
- 正式前台Java21 :common:ysmMaterialNativeSmokeTest --console=plain首次退出1，TextureManager.close在prefixRegister.clear处NPE；日志build/tmp/material-full-prefix-register-fail.log。CodeGraph核对共享root与FiguraTextureRegistrationSmokeTest，minecraft-dev核对1.21.1 TextureManager构造字段；独立纹理测试正常构造，而共享root Unsafe跳过prefixRegister初始化。
- 使用diagnosing-bugs技能，已有正式命令提供确定失败反馈；按字段遗漏/重复close/manager替换三种假设核对。原生close允许空map，字段声明直接new HashMap，root确实Unsafe创建。仅把共享测试client.manager创建改为new RecordingTextures()，使用原生构造初始化全部字段；未修改生产纹理管理器、close行为或断言。
- 重新运行同一正式命令，完整testClasses编译通过；原prefixRegister失败消失，正式入口实际通过Layered、Ribbon、ParticleMotion、ParticleLight、AnchorTrail完整分组（日志原PASS）。不需要临时source选择或探针。
- 当前正式入口仍FAIL：PreviewParticleNativeSmokeTest.run第128行“native panel draw preserves author layer order across Billboard and Ribbon”断言；日志build/tmp/material-full-preview-order-fail.log。其前出现preparationFence故意空main模型路径的IOException，不将该错误日志自动当作此次根因；尚需核对预览顺序断言与实际提交顺序。未改断言以绕过失败。
- 本轮只修改YsmMaterialNativeSmokeTest的一处构造语句，已审查并限定文件git diff --check。NativeGlWindow真实可见上下文，未宣称完整共享main或GUI画面PASS。
- 下一步诊断PreviewParticle绘制顺序，继续正式入口；既有函数契约ONCE槽位失败、独立参考/模型路径和客户端/Mixin/GUI/多人验证仍未完成。GPU默认关闭保留CPU，不自动启动游戏或部署mods，Qt offscreen禁止。


## 2026-11-01 接续：正式共享材质入口完整通过

- M3–M5合并检查点继续，1.21.1分支，累计修改未提交/推送；M6未开始。此次共享入口通过不代表整个检查点完成。
- CodeGraph核对PreviewParticleNativeSmokeTest/PreviewParticleFrame/CustomParticlePipelines；minecraft-dev读取1.21.1 RenderType，确认CompositeRenderType.toString返回RenderType[name:state]。正式命令重现层顺序失败，加实际材质列表诊断后确认三批顺序实际0/0/1，旧断言错误假定裸名称前缀。
- 仅迁移正式测试的三处前缀检查为精确RenderType[omnimorph_particle_0:/1:，保持3批数量与0/0/1顺序断言；保留失败信息的实际材质列表。生产排序/批次/渲染代码未改。修正后PreviewParticleNativeSmokeTest完整run通过，失败推进至记录客户端getProfiler为null。
- 共享RecordingClient用Unsafe创建，未初始化原生profiler；仅增加getProfiler返回原版InactiveProfiler.INSTANCE，和已有声音/进度等夹具一致，不修改生产profiler。一次编辑定位标记不匹配未写入却重跑了同样失败，随后修正标记并在编辑退出码检查后执行，避免重复无效构建。
- 已验证PASS（正式完整入口，无临时init）：前台Java21 :common:ysmMaterialNativeSmokeTest --console=plain退出0，日志build/tmp/material-full-pass.log；全量compileTestJava/testClasses完成，正式源集无排除。包括原生纹理注册/late binding/生命周期、材质与动画、CustomParticleNative完整子组（Layered/Ribbon/Motion/Light/AnchorTrail/Preview/CosmeticEntry/CreatedTrail等）、Avatar异步准备、broadcast、cloud refresh、加载进度。以日志实际输出为证据，不把历史M5标签算作移植里程碑完成。
- 失败/中间证据：build/tmp/preview-order-native-name-fail.log包含实际包装名称和0/0/1次序；build/tmp/material-full-profiler-fail.log记录后续profiler失败。日志中的空main模型、异步加载失败及503/429来自原失败注入场景，后续相应retention/cleanup断言及正式入口通过；未隐藏错误输出。
- 真实可见NativeGlWindow使用，以上为原生几何/资源/受控客户端逻辑验证，不宣称真实GUI像素、Mixin应用、可听声音或多人实机通过。
- 已审查两文件diff并限定文件git diff --check，无临时DEBUG日志。下一步正式库预览/装扮与函数契约ONCE失败，恢复独立参考/实际模型语料位置，随后检查合并M3–M5余下自动门槛；GPU默认关闭保留CPU，不自动启动游戏或部署mods，Qt offscreen禁止。


## 2026-11-01 接续：库预览原生输入窗口夹具

- M3–M5合并检查点继续，1.21.1分支，累计修改未提交/推送；M6未开始。
- 正式库预览任务名为libraryPreviewRuntimeSmokeTest；首次误用libraryPreviewSmokeTest导致Gradle任务查找失败，未编译/运行测试，随后按build.gradle更正。
- 已验证FAIL：正式前台Java21 :common:libraryPreviewRuntimeSmokeTest :common:libraryEquipmentSmokeTest --console=plain退出1，预览testReportInput原生MultiLineEditBox.mouseClicked→Screen.hasShiftDown需要Minecraft.getWindow().getWindow，记录客户端返回null，日志build/tmp/library-preview-window-fail.log。CodeGraph核对LibraryPreviewRuntimeSmokeTest、NativeGlWindow；minecraft-dev读取1.21.1 Window字段。
- 本轮仅共享测试夹具：RecordingClient提供Window包装器，借用NativeGlWindow已创建的真实可见GLFW current context句柄，供原生modifier查询；不创建隐藏/offscreen窗口，不修改生产控件/键盘逻辑。包装器不负责关闭借用窗口，原NativeGlWindow finally清理；收尾把包装器分配放入既有try/finally范围，保证初始化异常也关闭窗口。包装器当前仅用于句柄，不冒称完整原生Minecraft窗口配置或GUI外观PASS。
- 重跑正式命令，testClasses全量编译通过，报告输入完整点击/释放/CJK字符/换行退格/禁用/外部焦点检查通过；卡片pointer/keyboard/filter、横向骨骼滚动、plus资源选择/独立值/几何、All source/use筛选分组通过。
- 当前FAIL：testCreationMenuAndForm第5058行“Create opens a visible menu anchored below the clicked button”；日志build/tmp/library-preview-create-menu-fail.log。未修改该断言，需核对菜单布局与测试窗口尺寸。libraryEquipmentSmokeTest因为前项失败尚未执行。
- 已审查本轮修改并限定文件git diff --check。正式共享材质此前完整通过，本轮新增窗口包装后尚未重跑材质入口；既有函数契约ONCE失败、实际语料/独立参考和客户端/Mixin/GUI/多人验收仍未完成。GPU默认关闭保留CPU，不自动启动游戏或部署mods，禁止Qt offscreen。


## 2026-11-01 接续：创建菜单窗口替换恢复

- M3–M5合并检查点继续，1.21.1分支，累计修改未提交/推送；M6未开始。
- CodeGraph核对ContextMenu.setX/setY和库预览testCreationMenuAndForm；确认菜单使用Minecraft.getWindow().getGuiScaledWidth/Height，testWardrobeActionMenus已有通过反射临时替换Minecraft.window的640等尺寸夹具并finally恢复。
- 上轮共享RecordingClient.getWindow覆盖绕过该原生字段，返回只设置GLFW句柄、尺寸为0的独立包装器，导致原生ContextMenu约束到0尺寸。最小修复仅共享测试：删除覆盖和独立字段，把已创建包装器写入Minecraft原生window字段，继续借用真实可见GLFW窗口句柄；保留原生getter和各测试的窗口替换/恢复，不改菜单布局或断言。
- 已验证：前台Java21 :common:libraryPreviewRuntimeSmokeTest :common:libraryEquipmentSmokeTest --console=plain全量testClasses编译通过，原菜单visible/x/y断言通过，继续执行创建表单字段与构建检查。
- 当前仍FAIL：LibraryPreviewRuntimeSmokeTest.testCreationMenuAndForm第5143行“Actual failure callback preserves the form and restores retry/cancel controls”，日志build/tmp/library-preview-create-retry-fail.log。libraryEquipmentSmokeTest仍因前项失败未执行。未改该断言，下一步检查异步失败回调与表单状态；不将推进到下个断言视为完整库预览PASS。
- 已审查共享测试一处修改并限定文件git diff --check。真实窗口仅用于原生输入句柄/资源上下文，GUI视觉仍未验证。此前共享材质完整PASS在此变更后未重跑；函数契约ONCE失败、语料/参考位置及客户端/Mixin/多人验收仍待解决。GPU默认关闭保留CPU，不自动启动游戏或部署mods，Qt offscreen禁止。


## 2026-11-01 接续：创建回调和装扮选择屏幕夹具

- M3–M5合并检查点继续，1.21.1分支，累计修改未提交/推送；M6未开始。
- CodeGraph核对AvatarWizardScreen.startBuild/tick；minecraft-dev读取1.21.1 Screen，构造函数只赋title，公开init(Minecraft,w,h)才设置minecraft/font。创建失败测试注入按钮并直接startBuild，没有走公开init，whenComplete中的minecraft.execute因null失败，building未复位，旧断言随之失败；记录客户端execute在渲染线程同步执行，排除排队延迟。
- 本轮仅LibraryPreviewRuntimeSmokeTest：创建回调夹具明确设置Screen.minecraft；继续正式运行发现testWardrobeCosmeticSelection手动构造WardrobeScreen同样缺该字段，在该fixture补齐。生产创建回调/表单/按钮条件与全部断言未改。
- 正式前台Java21 :common:libraryPreviewRuntimeSmokeTest :common:libraryEquipmentSmokeTest --console=plain两次运行全量编译通过；创建表单失败恢复、重复提交/关闭保护、元数据字段完整分组通过，随后wardrobe menus/collection actions/visibility lifecycle/普通与expanded设置/隔离appearance/玩家routing/offline permissions copy等实际通过。Expected creation failure fixture错误日志为测试注入，失败后断言确实恢复。
- 中间证据build/tmp/library-preview-cosmetic-client-fail.log（首个夹具修复后推进至cosmetic选择）；当前仍FAIL，日志build/tmp/library-preview-cloud-client-fail.log：Screen.setInitialFocus→rebuildWidgets→WardrobeScreen.rebuild→selectCloud又读到另一个手动屏幕minecraft=null。下一步定位这个调用方，只修正测试初始化。libraryEquipmentSmokeTest仍未执行，完整库预览不标PASS。
- 已审查两处fixture赋值并限定文件git diff --check；无生产功能或范围扩张。GUI视觉/原生输入实机/Mixin/多人、函数契约ONCE槽位失败与语料/独立参考仍待验证/解决。GPU默认关闭保留CPU，不自动启动游戏或部署mods，Qt offscreen禁止。


## 2026-11-01 接续：同类WardrobeScreen夹具原生初始化

- M3–M5合并检查点继续，1.21.1分支，累计修改未提交/推送，M6未开始。
- 核对最新正式日志：空引用位于testCollectionOutfitImport第729行selectCloud→rebuildWidgets→setInitialFocus，而非先前推测的testVanillaSelection。CodeGraph和窄读调用区间确认手动匿名WardrobeScreen只覆写protected init，并未设置原生Screen.minecraft。
- 先仅该fixture补字段，正式重跑推进至testWardrobeDraftActions另一个独立WardrobeScreen同样null。此初始化问题已在多个同类fixture复现；本轮检查同一测试文件所有手动new WardrobeScreen，给尚未就近设置客户端的13处补Screen.minecraft引用，保留已有设置/还原流程、屏幕init抑制和业务断言；未改生产Screen构造或客户端生命周期，也未抽象/重构测试逻辑。
- 已验证：两次前台Java21 :common:libraryPreviewRuntimeSmokeTest :common:libraryEquipmentSmokeTest --console=plain全量testClasses编译通过。第二次不再在收藏/草稿入口空引用，推进到testInstanceTransformFields→testTreeAttachmentActions→testCosmeticControls。
- 当前FAIL：testCosmeticControls第2975行，另一种控制面板屏幕的this.minecraft=null，具体异常“Cannot read field screen because this.minecraft is null”；日志build/tmp/library-preview-controls-client-fail.log。下一步定位该控件屏幕fixture初始化，不修改生产功能或放宽断言。装备测试仍因库预览前项失败未执行。
- 已审查新增字段赋值并限定文件git diff --check。记录的是逻辑推进和编译通过，不是整个库预览/GUI视觉通过；已知函数契约ONCE、语料/参考与实机门槛仍待解决。GPU默认关闭保留CPU，不自动启动游戏或部署mods，Qt offscreen禁止。


## 2026-11-01 接续：记录屏幕绑定与上传夹具

- M3–M5合并检查点继续，1.21.1分支，累计修改未提交/推送；M6未开始。
- CodeGraph核对AvatarControlsScreen与实际失败栈指向的CosmeticAnimationScreen；recordingScreens符号查询未定位，窄读共享helper，确认其screenDispatcher只调用removed与替换screen，未执行原生Screen.init的minecraft赋值。测试后续手动调用protected init不会补该字段。
- 本轮共享测试recordingScreens在每个非null新屏幕上通过原生Screen.minecraft字段绑定记录客户端，保留removed/恢复原dispatcher和screen，反射失败显式AssertionError；方法声明ReflectiveOperationException，调用方全量编译通过。未改生产setScreen/控件/业务回调或启动真实游戏。
- 重跑正式库预览，动画轮盘/控制面板分组通过，推进至testAccessoryUploadScreen的同类客户端空引用。最初误补testOutfitUploadScreen，原失败仍在附件上传；按完整调用栈补附件夹具。两处实际ModelUploadScreen fixture均保留原protected init与业务路径、只补Screen.minecraft。
- 已验证：前台Java21 :common:libraryPreviewRuntimeSmokeTest :common:libraryEquipmentSmokeTest --console=plain全量testClasses编译通过；正式预览已通过之前的输入/菜单/草稿/动画控制、两类上传与大量后续分组，日志build/tmp/library-preview-composition-pose-fail.log。中间空引用日志build/tmp/library-preview-upload-client-fail.log。
- 当前仍FAIL：testIndependentFigura→testCompositionPreparation第4040行“actual GUI draw uses the captured base pose and restores an edit made at the flush boundary”；不是空客户端初始化问题，下一步核对捕获/flush/绘制/恢复时序，保留断言，不把编译或前面分组替代该验收。libraryEquipmentSmokeTest仍未执行。
- 已审查共享helper和两处fixture差异并限定文件git diff --check。真实可见GL窗口只证明原生上下文，GUI外观/实机/Mixin/多人仍未验证；函数契约ONCE与独立语料/参考问题未解决。GPU默认关闭保留CPU，不自动启动游戏或部署mods，Qt offscreen禁止。


## 2026-11-01 接续：组合姿态flush边界修复，正式库预览/装备通过

- M3–M5合并检查点继续，1.21.1分支，累计修改未提交/推送；M6未开始。本轮通过两个正式门槛，不代表全部M3–M5验收完成。
- CodeGraph核对LibraryFiguraPreviewRenderer/State、LibraryPreviewBinding与OutfitComposition.Frame prepare/draw/captureFiguraBase；窄读testCompositionPreparation/PreviewGraphics和submitIndependentFigura实际路径。正式失败断言补三个值后再现submitted=0.0、observed=123.0、restored=123.0，日志build/tmp/library-preview-late-pose-capture-fail.log；证明flush回调执行且恢复正常，基底捕获发生在flush之后。
- 最小生产修复仅LibraryFiguraPreviewRenderer.render：查找本帧composition，在首次gui.flush前prepareFiguraBase(state.avatar)。原draw里的幂等prepare仍保留供直接draw调用者；无组合路径不新增捕获。原有FiguraRenderPose负责绘制提交姿态及恢复flush后的live edit，未放宽断言或更改模型。正式测试只增强失败信息，保留原比较值和geometry/evaluated/lifetime检查。
- 已验证PASS（正式完整入口，无临时init）：前台Java21 :common:libraryPreviewRuntimeSmokeTest :common:libraryEquipmentSmokeTest --console=plain退出0，日志build/tmp/library-preview-and-equipment-pass.log。全量compileTestJava/testClasses完成；完整库预览使用项目内crypto1.ysm，不依赖缺失外部refs；核心UI回调/草稿/控制/上传/组合预算/异步准备/独立Figura捕获和draw/加密源/延迟帧/当前玩家路径均执行。正式库装备完整run覆盖实际本地HTTP下载、先prepare后equip、缓存来源、lost response重试、身份guard、取消与失败保留。
- 日志中Expected creation failure及空YSM main来自正式失败注入，后续retention/cleanup断言及进程最终通过；未压制stdout/stderr。使用真实可见NativeGlWindow上下文和受控客户端逻辑，不冒称GUI视觉/Mixin/多人实机PASS。
- 已验证PASS：前台 :fabric:compileJava :neoforge:compileJava --console=plain退出0，日志build/tmp/preview-pose-platform-compile-pass.log；common生产编译也完成。
- 已审查生产5行与测试诊断改动并限定文件git diff --check。下一步修复已知函数契约ONCE槽位失败，审计M3–M5其余正式smoke及独立参考/实际模型语料路径，实机门槛继续MANUAL/PENDING；GPU默认关闭保留CPU，不自动启动游戏或部署mods，Qt offscreen禁止。


## 2026-11-01 接续：函数声明槽位契约与玩家身份夹具

- M3–M5合并检查点继续，1.21.1分支，累计修改未提交/推送；M6未开始。
- 正式前台Java21 :common:ysmFunctionContractSmokeTest --console=plain复现ONCE完成后getControllerAnimation==null断言。CodeGraph核对YsmControllerRuntime.update/clock和YsmAnimationPlayer.playControllerAnimation/playInSlot；当前生产声明槽位明确保留时钟，包括条件为0；完成ONCE的targetWeight/weight归0但不删除，再启用同一声明不重启。此轮不改生产状态机。
- 正式testAnimationCompletionQueries改为检查完成实例保留、hasCompletedCycle与零贡献；条件0时仍验证查询不计入，重新启用检查同实例/同clock/完成标记与0targetWeight，并检查completion query恢复true/false。保留精确长度边界、跨controller/跨Avatar隔离、loop wrapping及fresh function playback断言。不是只删断言。
- 正式运行已通过该完成查询分组，推进到flying/jumping/food。三处Unsafe玩家缺UUID，生产YsmDisplayQuery远端ConcurrentHashMap读null键失败；本轮给这三个fixture各setUUID随机身份，保留原Abilities/FoodData输入及全部查询断言，未对生产加空UUID绕过。
- 中间证据build/tmp/function-contract-declared-slot-fail.log（原ONCE断言），build/tmp/function-contract-flying-uuid-fail.log（后续UUID失败）。全量testClasses持续编译通过。
- 当前仍FAIL：正式同一命令推进至testFilterPreviewDomain第1353行“preview advances its own state: 90.0”，日志build/tmp/function-contract-preview-filter-fail.log。ONCE和三个身份错误已不再阻断，函数完整main仍不能标PASS；下一步核对预览filter domain时钟及实际插值预期，保留失败断言。
- 已审查本轮单文件diff并限定文件git diff --check。库预览/装备、跨平台编译已有PASS；其余M3–M5自动门槛、独立参考/语料、客户端/Mixin/GUI/多人未全部完成。GPU默认关闭保留CPU，不自动启动游戏或部署mods，禁止Qt offscreen。


## 2026-11-01 接续：函数契约完整通过与 M3 正式核心门槛

- 用户批准的 M3–M5 合并检查点继续；M6 未开始。当前分支 1.21.1，HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，工作区累计 269 条状态，未提交/推送，保留全部既有修改及本地工具配置。
- CodeGraph 核对 YsmFilterRuntime，绑定代码确认 first_order 缺省 response=1，首次以输入初始化。旧 testFilterPreviewDomain 在创建 10/40/90 后没有切换输入，却期待从零积分的 0.5/2/4.5；时钟 100→101 为 0.05 秒，未触发恢复间隔。仅修正该测试：保留三个不同初值，分别切换目标至 20/80/180，增加同帧仍保持初值断言，下一帧检查 10.5/42/94.5；保持 preview 优先于残留 arm 标志及三个域隔离。生产滤波器与绑定未修改。
- 已验证 PASS：正式完整 :common:ysmFunctionContractSmokeTest --console=plain，前台 Java21、全量 testClasses，无临时 init/source 排除，退出0；日志 build/tmp/function-contract-full-pass.log。此前 ONCE、UUID 与 preview filter 阻断均已解除。
- 已验证 PASS：正式 :common:ysmBoneMatrixSmokeTest :common:ysmDeferredPoseSmokeTest :common:ysmFilterSmokeTest :common:outfitContractSmokeTest :common:outfitDraftSmokeTest :common:gpuRigidDataSmokeTest --console=plain，前台 Java21、无临时 init，退出0；日志 build/tmp/m3-core-formal-gates-pass.log。涵盖实际矩阵差分、不可变延迟姿态、滤波数值与 frame/bone/view/Avatar 隔离、装扮数据契约/草稿与 CPU 刚体打包布局；不代表真实游戏渲染或 GPU 绘制通过。
- 已审查本轮测试修改并限定文件 git diff --check；没有生产行为变更。M3–M5 尚未达到完整检查点，不提交/推送，不开始 M6。
- 下一步最小工作：继续审计并执行剩余原有正式 smoke；核实 OutfitApplyService / OutfitFormalPreparation 的真实入口覆盖；定位缺失的 refs 独立参考与模型语料。真实客户端/Mixin/GUI/多人仍 MANUAL/PENDING，不能以受控逻辑测试替代。GPU 保持 MVP 默认关闭与 CPU 路径，不自动启动游戏或覆盖 mods，禁止 Qt offscreen。


## 2026-11-01 接续：正式装扮准备与 Apply 三格式验证

- 上轮属于进展：函数契约与六项正式核心任务退出0。本轮继续 M3–M5 合并检查点，M6 未开始；分支 1.21.1，HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计269条工作区状态，未提交/推送。
- CodeGraph确认真实调用链 cosmeticSourceSmokeTest → CosmeticSourceSmokeTest.testSources → OutfitFormalPreparationSmokeTest.run → OutfitApplyServiceSmokeTest.run，故库装备入口通过不能替代本链覆盖。
- 正式前台Java21运行 :common:cosmeticSourceSmokeTest --console=plain，全量testClasses无临时init/排除。第一处失败为testWardrobe手动Screen.minecraft=null；minecraft-dev核对1.21.1 Screen.init(Minecraft,w,h)才赋字段。仅给该夹具显式绑定客户端，不启动完整游戏或修改生产Screen。
- 下一处失败为反射仍查旧setControlValue(definition,value)；CodeGraph确认现有生产签名为setControlValue(owner,definition,value)。测试查找与5处调用补明确owner；跨副本旧控件检查继续以first owner搭配second definition，保留拒绝断言。未添加生产兼容重载、未改业务校验。
- 已验证分组PASS：FIGURA、YSM_PLAIN、YSM_ENCRYPTED的正式原生准备及Apply均完成，包含native-before-CAS、驻留原子配对、same-base保留、冻结重试、冲突/回执后权限与过期拒绝；衣柜retry/refresh/cancel、HUD轮盘、正式控件预算与同ID归属、world/hand绑定、实例持久化、observer发布及网络派发分组均执行通过。正式骨架/手臂锚点/轨迹/组合controller后续分组亦执行通过。证据 build/tmp/cosmetic-source-reference-missing.log。
- 整体入口仍FAIL（退出1）：后续YsmDefaultAnimationSmokeTest.testReferenceResources缺少 refs/Sparkle-Morpher/common/src/main/resources/assets/sparkle_morpher/builtin/default/animations。未删除参考检查，也未以项目自带资源伪造独立参考。CosmeticSourceSmokeTest.testSources第90行以后的源/创建/catalog/cache等流程此次未到达；不得标完整cosmeticSourceSmokeTest PASS。
- 初始失败证据 build/tmp/cosmetic-source-wardrobe-client-fail.log 与 build/tmp/cosmetic-source-controls-signature-fail.log。已审查本轮单文件改动，git diff --check通过。可靠额度接口主窗口使用16%/10080分钟，剩余84%，未达交接阈值，不推断订阅类型。
- 下一步：定位真实refs参考与模型语料，继续其余原有正式smoke门槛；真实客户端/Mixin/GUI/CJK/多人仍MANUAL/PENDING。M3–M5未完成不提交推送，不进入M6；GPU MVP默认关闭保留CPU，不自动启动游戏/覆盖mods，Qt offscreen禁止。


## 2026-11-01 接续：原模型定位、访问链接及正式验收

- 本轮属于进展。用户指出model目录；只读搜索确认实际语料位于 D:/UserData/Desktop/Project/OmniMorph-Core-Native/models，含Figura、YSM_ENCRYPT、YSM_PLAIN。桌面另一个model为语音项目，不是此语料。上述模型目录共25个顶层资产：21文件、4明文模型目录，名字无冲突；不代表旧40模型语料已全部恢复。Sparkle-Morpher独立参考仍未找到。
- 已验证PASS：正式前台Java21 cloudCosmeticSourceSmokeTest、cloudOutfitPresetsSmokeTest、networkProtocolSmokeTest、figuraStaticMeshCacheSmokeTest、ysmAccelerationMeshesSmokeTest，无临时init，退出0；日志build/tmp/cloud-protocol-mesh-formal-pass.log。HTTP为本地契约fixture，不宣称生产后端通过。
- 已验证PASS：正式九项controller/runtime任务，ysmControllerIsolation/ControllerTransition/BuiltinController/BeginningTransition/BlendTransition/AnimationFrameClock/ControllerPoseLayer/FirstPersonController/FunctionStageSmokeTest，无临时init退出0；日志build/tmp/controllers-formal-pass.log。
- 先通过仅替换模型参数的本地init实际运行完整ysmHandAnimationSmokeTest，两份原指定模型芙宁娜v3.14中文配音.ysm、末影龙娘.ysm均执行；退出0，日志build/tmp/hand-real-model-pass.log。没有排除源集或合成替代实际模型。
- 用户明确授权建立符号链接方便访问。Windows New-Item SymbolicLink报Administrator privilege required，未提权。改用同卷21个HardLink文件及4个Junction目录，扁平映射到被忽略的refs/avatars，全部存在且可访问，git check-ignore确认忽略。源数据未编辑/复制；硬链接写入会影响同一文件，后续只读使用，禁止通过链接修改源模型。
- 已验证PASS：直接原命令 :common:ysmHandAnimationSmokeTest --console=plain（不带init）通过原refs路径加载两份真实模型，退出0；日志build/tmp/hand-default-linked-models-pass.log。实际弓姿态/重拉、三叉戟时钟/手臂/释放和核心分组均通过；正向第三方SPEAR-use物品蓄力仍明确PENDING，26原生kinetic部分N/A。
- 当前分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计WIP未提交/推送，M3–M5检查点未完成，M6未开始。无生产代码改动。
- 下一步：沿已恢复refs/avatars运行实际语料/公开姿态等原任务，按真实覆盖记录不足模型；继续定位Sparkle-Morpher独立参考。实机GUI/CJK/Mixin/多人MANUAL/PENDING；GPU默认关闭保留CPU，不自动启动游戏/覆盖mods，Qt offscreen禁止。


## 2026-11-01 接续：实际语料加载与公开姿态验证

- 上轮为进展：原始手部任务通过真实访问链接退出0。本轮继续M3–M5，分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计WIP未提交/推送；M6未开始。
- 已验证PASS：正式前台Java21 :common:ysmLocalCorpusSmokeTest，原refs/avatars参数，无临时init/源集排除；3个Figura ZIP资产经原生编译、4个明文YSM数据组装、18个加密YSM组装/压缩NBT传输/逐资源重建比对及取消传播完成。实际25个资产不是旧40个YSM语料。
- 已验证PASS：同次正式 :common:ysmPublicPoseSmokeTest，真实DS鲸鱼娘共享手臂树左右隔离与少女-哥伦比娅持续头部pitch/yaw、重复pass不累积和authored query隔离通过。回放期间存在一条Encrypted YSM operation failed: runtime/controller-parse警告，尚未定位，不能因姿态断言通过便认定所有controller有效；记录后续诊断。
- 已验证FAIL：同次 :common:ysmMaterialInputSmokeTest运行至testCorpus末尾第194行，所有被遍历模型的base/normal/specular字节与私有载荷断言均未触发，最终固定require(models==40 && encrypted==5)失败。CodeGraph核对确认当前为22个YSM/18加密，语料组成与旧基线不同；未修改/跳过/降低此验收断言。前置folder/binary渠道测试也已执行。完整命令最终退出1，不宣称整批或材质任务PASS。
- 三项共同证据 build/tmp/corpus-public-pose-material-count-fail.log，含前两任务完成和第三任务失败。源模型只读、无生产代码变更。
- 下一步最小工作：定位回放controller-parse警告及影响；继续其他可运行正式任务；旧40/5语料（包含缺失03_astronaut、16_tactics等）和Sparkle-Morpher独立参考仍未恢复，当前22/18不能替代。真实GUI/CJK/Mixin/多人MANUAL/PENDING。GPU MVP默认关闭保留CPU，不自动启动游戏或覆盖mods，禁止Qt offscreen。refs/avatars为21硬链接+4junction，禁止写入源模型。


## 2026-11-01 接续：按实际资源验收与 TLM 控制器警告定位

- 用户明确更正“验证取决于实际情况”。据此材质输入验收不再绑定旧机器40模型/5加密的固定数量；仅将YsmMaterialInputSmokeTest末尾数量断言替换为必须同时存在明文和加密YSM、必须有实际审计材质，继续遍历全部检测出的YSM且保留每份base/normal/specular字节、身份顺序及私有载荷边界断言。未更改生产实现或GPU范围。
- 已验证PASS：正式前台Java21 :common:ysmMaterialInputSmokeTest --console=plain，全量testClasses、无临时init，退出0。实际22个YSM、18加密、27份材质、0真实normal、2真实specular；normal覆盖来自既有folder/binary夹具，不宣称真实语料含normal。日志build/tmp/material-actual-corpus-pass.log。该用户更正替代旧固定数量阻断，但不把缺失模型专属场景标为通过。
- 控制器警告诊断进展：按项目约束使用已安装YSMParser0.3.5，仅将DS鲸鱼娘.ysm复制到build/tmp/controller-warning-in，导出到build/tmp/controller-warning-out；源模型未改，产物不入库。工具-v输出过量，后续同类分析应写日志文件后窄读，不全量打印。
- 可重复失败入口：前台 :common:scanYsmModels -PysmScanPath=build/tmp/controller-warning-out --console=plain，退出1；日志build/tmp/controller-warning-tlm-source-fail.log。确切3个诊断均位于player.parallel_7的default2/default3/default4，向default的条件包含 !tlm.is_sitting，错误Failed to get property: tlm。由原始导出controller窄读确认非时间/GL/Screen夹具错误。尚需核对原支持范围与26基线；未实现TLM桥、未将未支持状态伪装为false，未宣称迁移新增回归或整个controller丢失。
- 已审查材质测试小范围改动并git diff --check。当前分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计WIP未提交/推送，M3–M5未完成，M6未开始。
- 下一步：核对tlm命名空间在原支持矩阵中的地位，若为未支持第三方联动记独立follow-up，不扩大本轮移植；继续以实际资源执行剩余门槛并标清缺失场景。Sparkle独立参考、实际GUI/CJK/Mixin/多人仍未验证。GPU默认关闭保留CPU，不自动启动游戏/覆盖mods，Qt offscreen禁止；refs硬链接/junction只读。


## 2026-11-01 接续：语言、AVIF 与边界验收

- 上轮为进展：实际语料材质任务通过且解析警告已缩小到tlm。当前分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计WIP未提交/推送；M3–M5未完成，M6未开始。
- CodeGraph核对当前MolangBindings根命名空间；git show pre-1.21.1-port中的MolangBindings与Avatar同样没有tlm注册。现有证据支持原基线未注册该联动，而非本次版本替换删除；未实际运行26原基线，不宣称已做跨版本运行差分。DS鲸鱼娘3条tlm.is_sitting表达式解析失败列为既有联动follow-up，不新增TLM桥，不伪造false消除诊断。该模型上述转移仍不具备受支持的实际状态读取。
- 已验证PASS：正式前台Java21 :common:ysmLanguageSmokeTest，实际22模型/18加密、15份字典、1278 primitive keys，共享Figura/YSM文本消费者、实时locale、fallback、raw值与隔离通过；日志build/tmp/language-pass-avif-corpus-fail.log（同批后续AVIF旧固定数断言失败，语言任务自身完成）。
- 用户已明确按实际情况验证；YsmAvifSmokeTest仅把最终models==40改为models>0，保留images>0和guiImages>0以及全部解码/字节/尺寸/截断/重复结果/私有载荷断言，失败信息添加实际models/images。未把没有AVIF当作通过。
- 已验证PASS：正式 :common:ysmAvifSmokeTest :common:ysmDataBoundarySmokeTest --console=plain，全量testClasses、无临时init，最终退出0；日志build/tmp/avif-pass-boundary-symlink-pending.log。AVIF实际22模型、16图、262016像素、7份GUI图、19个preview引用，独立rgba/alpha向量及取消测试亦完成。
- 数据边界为有限PASS：目录/ZIP/memory等价、资源副本、NBT、直接面片、取消、budget/path/case、generation及Figura编译执行；唯一符号链接逃逸fixture因Windows不允许Files.createSymbolicLink而输出SKIPPED/MANUAL_BY_USER，此场景仍MANUAL/PENDING，不能报告全部边界通过。核对catch仅跳过该fixture，后续generation/Figura仍执行，无提前返回；未新增跳过逻辑。
- 已审查AVIF小范围修改并git diff --check。下一步继续原有可执行验收/核对合并检查点剩余门槛；缺失独立Sparkle参考及真实GUI/CJK/Mixin/多人继续未验证。GPU默认关闭保留CPU，不自动启动游戏或覆盖mods，Qt offscreen禁止，模型硬链接/junction只读。


## 2026-11-01 接续：库与后端正式任务验收

- 上轮为进展：语言/AVIF及有限边界验证已完成。本轮按计划Q1补原有内核/库/后端入口，不新增功能或下一阶段。
- 已验证PASS：正式前台Java21、全量testClasses、无init/排除，11项任务ysmPackageSmokeTest、boneTreeSmokeTest、wardrobeTransitionSmokeTest、modelCacheCodecSmokeTest、libraryMetadataSmokeTest、libraryClientSmokeTest、libraryPreviewCacheSmokeTest、libraryTransferSmokeTest、libraryThumbnailSmokeTest、modelLibraryLayoutSmokeTest、libraryUploadDraftSmokeTest退出0；证据build/tmp/library-core-formal-pass.log。GUI布局为数据/命中逻辑覆盖，不代表真实窗口像素或CJK。
- 已验证PASS：同样正式入口13项localOutfitPresetsSmokeTest、outfitObserverClientSmokeTest、outfitApplyClientSmokeTest、outfitPrivateUploadSmokeTest、libraryManagementSmokeTest、cloudAdapterSmokeTest、outfitEventsSmokeTest、cloudUploadProgressSmokeTest、cloudProtocolConfigSmokeTest、cloudReconnectSmokeTest、cloudModelSwitchSmokeTest、cloudEndpointSmokeTest、omniMorphIdentitySmokeTest退出0；证据build/tmp/backend-core-formal-pass.log。HTTP/TLS为本地契约fixture，outfitEvents仅framing/parser，真实Rustlistener未带参数未运行，不能标生产后端端到端通过。
- 私有上传日志包含两次worker AssertionError:frozen native bytes；CodeGraph核对run第108–121行明确构造changedBytes并断言不能accepted，服务端第341行只接受original expected。异常对应两个auth adapter的刻意变更分发拒绝路径，调用方确认失败，不是未检查的正常上传成功。既有fixture用抛断言/断连接拒绝；可维护性改进记follow-up，不因本次移植顺手重构或压制日志。
- minecraft-dev validate_access_widener以1.21.1 mojmap验证当前common/src/main/resources/omni-morph.accesswidener：valid=true，15条、0 errors、0 warnings；仅静态目标有效，不代表实际加载。
- 当前分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，269条累计工作区状态，未提交/推送。本轮未改生产/测试源码；进度记录更新并git diff --check。M3–M5尚未完整验收，M6未开始。
- 下一步继续核对剩余Q1原任务及Mixin真实加载证据；Sparkle独立参考缺失、实际GUI/CJK/模型渲染/多人、符号链接逃逸场景仍MANUAL/PENDING。依用户更正以实际资源验证，缺失专属场景不冒称通过；TLM既有未支持表达式已记follow-up。GPU MVP默认关闭保留CPU，不自动启动游戏/覆盖mods，Qt offscreen禁止，模型链接只读。


## 2026-11-01 接续：Molang/动画核心批量验收与时间线差分

- 上轮为进展：24项库/后端任务退出0与AW静态验证。本轮分支1.21.1，HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计WIP未提交/推送；M3–M5继续，M6未开始。
- 正式前台Java21、全量testClasses、无init/排除，--continue批量19项。18项PASS：ysmDisplayStateSmokeTest、ysmSamplingAlignmentSmokeTest、ysmPoseSamplingSmokeTest、ysmAnimationEventWindowSmokeTest、ysmControllerSoundSmokeTest、ysmChildControllerSmokeTest、ysmControllerContextSmokeTest、ysmControllerPhaseSmokeTest、ysmSyncDeferSmokeTest、ysmMolangMathSmokeTest、ysmMolangBudgetSmokeTest、ysmPerlinSmokeTest、ysmBoneQuerySmokeTest、ysmInputSmokeTest、ysmParticleRequestSmokeTest、ysmParticleDispatchSmokeTest、ysmUnavailableBindingSmokeTest、ysmControllerDiagnosticSmokeTest。整体退出1因为ysmPlayerTimelineSmokeTest失败；证据build/tmp/animation-core-timeline-owner-fail.log，不宣称批量全部PASS。
- 首个时间线错误：CPU反射runtime构造owner=null，notifyAnimationEvent核对runtime.owner().getYsmRuntime()时空引用。CodeGraph确认fixture与事件调用路径；仅cpuRuntime用Unsafe分配非null Avatar owner，仍为无纹理/实体的CPU矩阵fixture，不改生产notification，也未将真实Lua事件分发说成已覆盖。
- 正式单独重跑ysmPlayerTimelineSmokeTest：全量编译通过，owner空引用解除，推进到第85行frame0/pass0矩阵coefficient12实际-0.010000001，原期待-(0.05*1.2)=-0.06；退出1。证据build/tmp/timeline-matrix-differential-fail.log。未更改矩阵/事件顺序预期；下一步核对controller layer同通道ownership（原测试按1+0.2叠加）与当前生产后层覆盖语义，再决定测试过时还是实际回归。完整时间线仍FAIL。
- 已审查小范围fixture修改并git diff --check。缺失独立参考及真实GUI/CJK/Mixin/多人、符号链接逃逸仍未验证；GPU默认关闭保留CPU，不自动启动游戏/覆盖mods，Qt offscreen禁止，模型链接只读。


## 2026-11-01 接续：时间线通道归属与手臂资源夹具

- 本轮进展：CodeGraph核对sampleAnimationPoses/applyControllerLayers/applyControllerPose；git show pre-1.21.1-port确认原基线亦明确later stage替换写入通道、仅同controller内部累加，未将现输出直接当规范。原时间线测试1+0.2叠加预期已过时。
- 仅YsmPlayerTimelineSmokeTest：初始body/arm位置公式改为后层0.2贡献；转换中位置由parallel保有，主层rotation仍45度；完成后position不再加主层4。保留全部矩阵系数、事件序列、0/.2/1权重、nested body/arm snapshot和clock精度检查。生产composition未改。
- 首次正式运行body通过而arm为0；核对生产arm路径显式使用authoredArmClips，fixture先前仅注册body。两套timeline fixture均补player.registerArmAnimations(player.getClips())，不改生产fallback；后续正式运行初始五帧×三pass矩阵和完整loop事件轨迹均通过。
- 当前仍FAIL：state transition后期望exit紧接next.zero，实际trace末尾只有player.main:exit，next.zero未同步发出。增强失败消息输出trace，未放宽断言。正式前台Java21 :common:ysmPlayerTimelineSmokeTest --console=plain退出1，全量testClasses编译通过；日志build/tmp/timeline-incoming-initial-event-fail.log。中间arm失败日志build/tmp/timeline-arm-domain-fail.log。转换中/后、nested后续分组未到达，不能标PASS。
- 下一步核对beginning transition延迟初始timeline事件的现有契约与测试时序；不先假定生产缺事件。当前分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计WIP未提交/推送，M3–M5未完成，M6未开始。
- 已审查修改并git diff --check。GPU默认关闭保留CPU，实际GUI/CJK/Mixin/多人与缺失独立参考未验证，不自动启动游戏/覆盖mods，禁止Qt offscreen，模型链接只读。


## 2026-11-01 接续：完整时间线正式通过

- 本轮为进展，解除ysmPlayerTimelineSmokeTest剩余时序失败。CodeGraph核对既有YsmBeginningTransitionSmokeTest，明确声明/函数controller的beginning transition冻结clip clock/events、边界zero事件一次、随后按原时间表运行；生产advanceAnimationTimeline与playInSlot匹配该契约。
- 仅测试改动：原要求状态切换立即exit+next.zero，改为先检查exit与time0；0.1秒中点无新增事件且time0；精确0.2秒边界只增加next.zero且time0；再0.05秒只增加next.mid。保留旧退场clip时间不变和事件不再发出、全部body/arm矩阵、0/.2/1 live weight、nested child退场/重入与loop时间精度，不改生产timeline或放宽事件断言。
- 已验证PASS：正式前台Java21 :common:ysmPlayerTimelineSmokeTest --console=plain，完整main及全量testClasses、无init/排除，退出0；日志build/tmp/timeline-full-pass.log。CPU内部步骤/实际矩阵差分证据，不代表游戏窗口、真实Lua通知接收或多人同步通过。
- 已审查本轮差异并git diff --check。分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计修改未提交/推送，M3–M5仍在验收，M6未开始。
- 下一步继续核对其余正式同步/二进制/资源任务覆盖，整理合并检查点证据；独立Sparkle参考、实际GUI/CJK/Mixin/多人、符号链接逃逸仍未验证。依实际语料记录覆盖，TLM原基线未支持记follow-up；GPU默认关闭保留CPU，不自动启动游戏/覆盖mods，Qt offscreen禁止，模型链接只读。


## 2026-11-01 接续：同步、加密与二进制资源正式验收

- 上轮完整时间线通过属于进展。本轮正式前台Java21、全量testClasses、无init/源集排除，批量9项（--continue，-PysmCryptoCorpus=refs/avatars）。8项PASS：ysmBaselineSmokeTest、ysmCryptoSmokeTest、ysmBinaryInputSmokeTest、ysmBinaryFormatSmokeTest、ysmPayloadSmokeTest、ysmPrivateDiagnosticsSmokeTest、ysmAnimationWeightSmokeTest、ysmSyncSmokeTest。首批整体退出1因二进制资源旧数量断言，证据build/tmp/binary-sync-corpus-count-fail.log。
- 固定crypto1/2/3向量、边界负例及当前真实加密语料已执行；binary格式固定向量覆盖1/3/4/5/9/10/14/15/16/23/24/25/26/27/31/32与no-DWORD等价。载荷白名单/压缩磁盘泄漏、日志捕获、动态权重与同步codec/replay亦通过；同步未连接真实多人后端。
- CodeGraph核对YsmBinaryResourcesSmokeTest：原testCorpus遍历已实际处理17个crypto3样本，却固定require(files==4)。按用户“验证取决于实际情况”，仅改为files>0且输出实际数量；逐模型密文原字节、crypto/format身份、geometry/texture/keyframes及负例全部保留，old.ysm既有crypto2资源collection分支仍由其他用例覆盖，不改生产解密。
- 已验证PASS：正式 :common:ysmBinaryResourcesSmokeTest -PysmCryptoCorpus=refs/avatars --console=plain，完整main退出0；日志build/tmp/binary-resources-actual-corpus-pass.log。17个crypto3模型原始资源映射与后续testMappedCorpus均执行，全部BINARY_RESOURCE_OK及BINARY_MAPPING_OK，不再只停在数量断言。
- 已审查单文件小范围修改并git diff --check。分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计WIP未提交/推送，M3–M5继续，M6未开始。
- 下一步核对剩余正式音频/多行表达式/原生state等任务及检查点证据；独立Sparkle参考和真实GUI/CJK/Mixin/多人仍未验证，符号链接逃逸MANUAL/PENDING。GPU默认关闭保留CPU，不自动启动游戏/覆盖mods，Qt offscreen禁止，模型链接只读。


## 2026-11-01 接续：音频与真实模型异步/几何验证

- 正式前台Java21、全量testClasses、无init/排除批量5任务，4项PASS：ysmMultilineExpressionSmokeTest、ysmNativeStateSmokeTest、ysmControlledBaseGeometrySmokeTest、ysmAsyncEncryptedLoadSmokeTest。首批整体退出1仅音频旧语料数量断言；日志build/tmp/native-geometry-async-pass-audio-count-fail.log。
- 末影龙娘实际受控geometry：31个movement绑定无未解析、793表达式、27/28 active clips无重复及bind/多帧/跑动顶点bounds检查完成。异步任务实际加密model→portable→Avatar.load→client publication通过，覆盖generation/scope/failure retention、broadcast、cloud refresh；空main/HTTP503/429为既有失败注入，最终断言通过。仍不代表真实游戏渲染/后端/GUI或GPU通过。
- CodeGraph核对YsmAudioDecodeSmokeTest；按用户实际资源验收，仅将固定40/5与>=50Vorbis/>=4Opus改为必须覆盖明文+加密且有实际PCM音频；继续遍历所有真实声音，保留完整PCM/预算/损坏输入、单/双声道Opus预跳过和尾裁剪数值断言。缺失codec明确输出NOT_COVERED_BY_CORPUS。
- 已验证PASS：正式 :common:ysmAudioDecodeSmokeTest --console=plain，退出0；日志build/tmp/audio-actual-corpus-pass.log。实际22模型/18加密，Vorbis123份，197620404 PCM字节；真实语料无Opus，日志明确NOT_COVERED_BY_CORPUS: OPUS。Opus正向证据仅前置合成编码夹具，不能称真实模型Opus或可听播放通过。中间无Opus失败日志build/tmp/audio-corpus-no-opus.log保留。
- 已审查单文件修改并git diff --check。分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计WIP未提交/推送；M3–M5未完成，M6未开始。
- 下一步核对实际held-item/stability及剩余检查点证据，避免重复已通过项；独立Sparkle参考、缺失专属模型、真实GUI/CJK/Mixin/多人、符号链接逃逸仍未验证。GPU默认关闭保留CPU，不自动启动游戏/覆盖mods，禁止Qt offscreen，模型链接只读。


## 2026-11-01 接续：实际持物几何与100次稳定性验收

- 已验证PASS：正式前台Java21 :common:ysmHeldItemGeometrySmokeTest --console=plain，完整main、全量testClasses、无init/源集排除，退出0；日志build/tmp/held-items-actual-corpus-pass.log。附件fixture及全部22个YSM（18加密）实际CPU vertex/attachment检查完成。出现加密controller-parse警告，几何PASS不证明对应所有controller表达式受支持；保留既有TLM跟进及未定位的其他警告，不静默忽略。
- CodeGraph核对YsmStabilitySmokeTest。按用户实际资源验收，仅将旧40YSM+5Figura类别数量改为两类必须非空，最终输出真实candidate数量。100次切换、每批10次、每个source必须被访问、逐个图像关闭/PCM恰好释放一次、finite pose与bounded GC后retained=0全部保留，未缩短压力规模或改清理门槛。
- 已验证PASS：正式 :common:ysmStabilitySmokeTest --console=plain，前台Java21、全量testClasses、无init，2m25s退出0；日志build/tmp/stability-actual-corpus-pass.log。10批/100次，全部25source参与，所有批retained=0，最终PCM released=663710036 bytes。
- 已检查实际报告build/reports/ysm/m8-stability.json（42005bytes）：iterations=100、batchSize=10、corpusSources=25。报告保留CPU采样/heap/direct pool记录；未做26基线性能差分，不能宣称性能改善、整帧/GPU/可听音频/真实GUI通过。真实GLFW窗口仅为原生texture上下文。
- 已审查本轮稳定性数量/输出改动并git diff --check。当前分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计WIP未提交/推送，M3–M5检查点仍未完整验收，M6未开始。
- 下一步整理剩余正式任务与原检查点要求的证据矩阵，补可运行入口；独立Sparkle参考、缺失专属场景、实际GUI/CJK/Mixin/多人、符号链接逃逸仍未验证。GPU默认关闭保留CPU，不自动启动游戏或覆盖mods，Qt offscreen禁止，模型链接只读。


## 2026-11-01 接续：创建与库剩余入口通过、验收摘要

- 正式前台Java21 :common:ysmAvatarCreationSmokeTest :common:ysmPhase41SmokeTest :common:libraryPackagePreparationSmokeTest :common:modelLibraryCatalogSmokeTest :common:libraryUploadSmokeTest :common:libraryEquipmentVisibilitySmokeTest --continue --console=plain，全量testClasses、无init/排除，退出0；日志build/tmp/creation-library-remaining-formal-pass.log。common jar仅创建资源测试的输入，不代表M6双加载器发布打包开始或通过。
- 创建检查实际built common JAR全部42 bundled assets、正式package解析、credits/license、Unicode目录和失败复制清理；库准备含固定来源缓存与实际HTTP upload/readback、catalog实际callbacks、上传丢响应/CAS/cancel及公私装备持久化readback均完成；真实GUI/生产后端仍未验证。
- 只按*-pass.log扫描会漏掉失败批次中的已通过任务（如language/core/binary组），初筛不是权威总数，不据此重复全部已验证项。本轮为进展：补六个正式入口与可读当前摘要；未声称Q1全通过。
- 已在docs/porting/mc-1.21.1-port-progress.md顶部加入当前验收摘要，集中列明actual语料、自动证据、缺资源入口及GUI/Mixin/多人限制，保留历史记录。
- 分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计WIP未提交/推送，M3–M5未完成，M6未开始；本轮无生产/测试源码修改，文档git diff --check通过。
- 下一步继续核对剩余正式入口和实机证据，不以累计PASS数量替代检查点验收；GPU默认关闭保留CPU，不自动启动游戏/覆盖mods，禁止Qt offscreen，模型链接只读。


## 2026-11-01 接续：Mixin 静态核对与书籍界面注入修复

- 本轮进展为真实生产迁移修复。minecraft-dev analyze_mixin扫描renderer目录10类、GUI目录19类、Fabric3类、NeoForge4类；工具结果不能直接当实际加载通过。
- Renderer告警：ArrowRenderer/LivingEntityRenderer泛型descriptor被扫描器显示LT;，缓存1.21.1 Mojmap JAR的javap -s确认实际AbstractArrow/LivingEntity erased descriptor存在；BlockEntityWithoutLevelRenderer.renderRaw为原OptiFine兼容注入，require=0、remap=false，原生缺少不构成必须注入失败。未改这些代码。
- NeoForge盔甲额外6float renderArmorPiece及粒子Frustum/Predicate render由21.1.185 patched JAR javap -p -s确认实际存在；扫描器只查vanilla产生误报。通用render泛型同理。GUI四个nested class class_not_found仍待字节码逐项核实，不能统称通过。
- 确认并修复BookViewScreenMixin（列在figura-common.mixins.json的required配置）：旧visitText/ActiveTextCollector为26API，1.21.1 BookViewScreen原生render使用GuiGraphics.drawString。CodeGraph读取mixin与minecraft-dev原生源码核对后，仅将FormattedCharSequence及Component两处ModifyArg重定向render里的对应drawString完整descriptor，index=1，保留emoji处理函数与原priority1100。
- 已验证PASS：minecraft-dev单文件analyze_mixin isValid=true、errors=[]，仅原priority1100兼容提示；正式前台Java21 :fabric:compileJava :neoforge:compileJava --console=plain退出0，common生产重新编译也通过；日志build/tmp/book-mixin-platform-compile-pass.log。仍非实际Mixin apply或书籍GUI视觉PASS。
- 已审查本轮两处注解修改并git diff --check。分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计WIP未提交/推送；M3–M5未完成，M6未开始。
- 下一步继续核实配置内GUI nested targets与剩余Mixin静态问题，实际客户端加载/GUI/CJK/多人仍MANUAL/PENDING；独立参考与缺失模型专属场景未验证。GPU默认关闭保留CPU，不自动启动游戏/覆盖mods，Qt offscreen禁止，模型链接只读。


## 2026-11-01 接续：嵌套GUI及字体/图层注入字节码核实

- 本轮为验证进展，无生产/测试源码改动。CodeGraph核对LineInfoMixin、ClickEventActionMixin、GuiMessageLineMixin、SuggestionsListMixin、FontSetMixin、SignRendererMixin；缓存1.21.1 Mojmap JAR javap -p -s证实四个GUI嵌套目标类均存在。
- 字节码进一步确认BookEditScreen$LineInfo构造器有MutableComponent.setStyle调用；CommandSuggestions$SuggestionsList.render确有GuiGraphics.drawString(Font,String,III)I调用；ClickEvent$Action的CODEC/UNSAFE_CODEC、构造invoker擦除签名、isAllowedFromServer/filterForSerialization存在。GuiMessage$Line仅添加接口与Unique状态，类存在。静态工具class_not_found是嵌套类解析限制，不改代码绕过。
- minecraft-dev新增font目录2类扫描、Omni目录15类扫描：BitmapProvider.Glyph为nested误报；PlayerModel.setupAnim的LivingEntity泛型擦除由javap确认。FontSet method_57035与SignRenderer method_45799在Fabric映射JAR真实存在；NeoForge21.1.185 patched JAR对应lambda$selectProviders$5及lambda$renderSignText$2存在，现有双名称remap=false选择已覆盖，未盲目替换名称。
- 完整org.figura.mixin.render目录扫描28类，工具22valid/6invalid；其中Arrow/Living及可选OptiFine已前轮核实；新增CustomHeadLayer、ElytraLayer的LivingEntity与ParrotOnShoulderLayer的Player FFFFZ descriptor均由javap确认存在，属于扫描器泛型假警报。静态方法/类存在不证明注入ordinal、局部捕获、实际Mixin apply或视觉正确。
- 分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计WIP未提交/推送。上轮BookViewScreen两处修复的双平台编译PASS仍有效；本轮无新增代码无需重复编译。记录更新并git diff --check。
- 下一步核对尚未审计的输入/粒子/其余配置目标以及实际加载证据条件；真实GUI/CJK/Mixin/多人、独立参考、缺失模型场景、符号链接逃逸仍未验证。M3–M5未完成，M6未开始；GPU默认关闭保留CPU，不自动启动游戏/覆盖mods，Qt offscreen禁止，模型链接只读。


## 2026-11-01 接续：输入/粒子及配置完整性静态核对

- 本轮为验证进展，无生产/测试源码修改。minecraft-dev扫描输入5类（4valid，InputConstants.Type嵌套误报）、粒子3类（全valid）；完整Figura mixin目录77类（60valid/17工具告警）。已知泛型/嵌套/patch/optional问题不凭工具计数直接判定运行失败。
- CodeGraph与javap证实InputConstants$Type.addKey(Type,String,int)、Display$TextDisplay.DATA_TEXT_ID/getText及ChannelAccess$ChannelHandle真实存在；TextDisplay/ChannelHandle工具告警为nested解析限制。第三方GeckoLib目标不在vanilla源码中，静态Minecraft工具无法裁定，仍需对应依赖与实际加载证据。
- 四份实际配置逐项源码存在性核对：figura-common80项、omni-morph-common15项、Fabric3项、NeoForge4项，missing=[]。类存在性不等价注入签名/应用成功；不将未扫描accessor视作已验证行为。
- 已向用户发出异步确认：是否允许通过Gradle启动独立开发客户端进行Mixin加载/真实GUI验证，不改现有实例或mods；依据项目“不自动启动/关闭游戏、不覆盖mods”约束，未收到答复前不启动。当前确认仍待用户答复，不把等待视为批准。
- 分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计WIP未提交/推送；M3–M5未完成，M6未开始。记录更新并git diff --check。
- 下一步在获准后准备隔离run目录并启动实际开发客户端，未获准时继续可执行的剩余自动检查。真实GUI/CJK/Mixin/多人、独立参考、缺失模型场景、符号链接逃逸仍未验证；GPU默认关闭保留CPU，Qt offscreen禁止，模型链接只读。


## 2026-11-01 接续：独立原生接口 Java21 编译

- 本轮为进展：正式前台Java21 :common:compileNativeInterface --console=plain退出0，JNI接口/loader/fallback及NativePlatformSmokeTest源码的独立Java编译与header输出完成；日志build/tmp/native-interface-java21-pass.log。非JNI调用/CPU SIMD执行证明，不标nativeSmokeTest或nativePlatformSmokeTest PASS。
- 核对原任务依赖：两项native smoke依赖packageLocalNative，后者依赖:fabric:remapJar并强制build/native/windows-x64-release/manifest.json及DLL。当前工作区无本地native产物或Fabric libs，未为跑测试跳过依赖、伪造JAR或提前展开M6打包。原M5选项A未要求选项B的native/GPU/benchmark验收；原生运行/发布完整证据仍待对应后续范围完成。
- 客户端启动异步确认仍待用户答复，未启动游戏。已核对run配置当前无自定义runDir覆盖；获准后需先设置隔离开发run目录，不访问现有游戏实例或mods。
- 当前分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，git状态实测273条（此前269为历史快照），保留全部累计修改与本地配置，未提交/推送；本轮无生产/测试源码修改。M3–M5未完成，M6未开始。文档git diff --check。
- 下一步获准后实际客户端加载验证；等待期间继续可独立完成的证据核对，不把静态/编译当GUI/Mixin/多人PASS。独立参考、缺失模型场景、符号链接逃逸仍未验证；GPU默认关闭保留CPU，禁止Qt offscreen，模型链接只读。


## 2026-11-01 接续：GeckoLib4.9.3可选联动静态证据

- 本轮为验证进展，无生产/测试源码修改。CodeGraph读取GeckolibGeoArmorRendererMixin/GeckolibGeoRendererMixin；找到本机4.9.3正式dependency JAR及Loom remapped JAR，使用javap -p -s对照第三方真实字节码，不以vanilla工具class_not_found裁定第三方失效。
- 已核实实际GeoArmorRenderer含entityRenderTranslations/modelRenderTranslations Matrix4f、scaleWidth/scaleHeight float字段；4参数prepForRender(Entity,ItemStack,EquipmentSlot,HumanoidModel)存在，另有增加buffer与5float的重载。GeoRenderer的actuallyRender/renderRecursively/updateAnimatedTextureFrame及GeoAnimatable擦除形式存在。原始Fabric发布JAR使用intermediary MC类名，Loom本地有对应remapped依赖；不将原始namespace字符串差异当生产错误。
- 尚未验证：实际Mixin apply/overload选择、无模组安全跳过与GeoArmor实机pivot。当前gradle.properties run_with_geckolib=false，普通开发客户端即使启动也不覆盖安装GeckoLib场景；需单独可选依赖运行证据，未随意打开配置或扩展联动。
- 联动中右腿分支既有allFailed赋值一致性疑点属非当前移植范围，未证明实机影响，留follow-up，不顺手重构或给出超证据Finding。
- 当前分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，273条累计状态保留、未提交/推送；M3–M5未完成，M6未开始。客户端启动授权仍待答复，未启动游戏；文档git diff --check。
- 下一步实际客户端确认到达后准备独立run目录并验证基础加载；可选GeckoLib另记未验证。独立参考、缺失模型专属场景、真实GUI/CJK/Mixin/多人与符号链接逃逸仍有证据缺口。GPU默认关闭保留CPU，不自动启动游戏/覆盖mods，Qt offscreen禁止，模型链接只读。


## 2026-11-01 接续：隔离开发客户端配置准备

- 本轮为准备进展，不是运行通过。先对实际Architectury Loom1.14.476 JAR javap核实RunConfigSettings.runDir(String)/getRunDir存在，再生成被忽略的build/tmp/client-validation.init.gradle，仅对Fabric/NeoForge现有client运行配置设绝对隔离目录build/tmp/client-validation/fabric与neoforge，不修改生产Gradle配置、不操作现有游戏实例/mods。
- 已验证配置解析与任务图：正式前台Java21 :fabric:runClient :neoforge:runClient --init-script build/tmp/client-validation.init.gradle --dry-run --console=plain退出0，输出两份正确独立runDir；日志build/tmp/client-isolation-dry-run-pass.log。全部run/build依赖标SKIPPED，游戏未启动、未下载assets、未将dry-run记Mixin或GUI PASS。
- 启动确认仍待用户答复。获准后分别运行单平台runClient（去掉--dry-run），保留同init与前台Tee日志；不能同时启动两个平台争用桌面/模型资源。关闭测试客户端也应遵照授权范围。
- 分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，273条累计状态保留，未提交/推送，M3–M5未完成，M6未开始。文档git diff --check。
- 剩余真实GUI/CJK/Mixin/多人、可选GeckoLib运行、独立参考与缺失模型场景、符号链接逃逸仍未验证；GPU默认关闭保留CPU，Qt offscreen禁止，模型链接只读。下一步待启动授权后执行真实加载，不能重复dry-run冒充进展。


## 2026-11-01 接续：累计差异交付检查

- 全工作区git diff --check独立捕获返回码0，累计tracked diff为261文件/6058增/11589删（记录时快照），不自动格式化无关代码；Git既有LF→CRLF提示存build/tmp/final-diff-check-warnings.log，未发现whitespace错误。
- 核实最新书籍mixin平台编译、完整timeline、100次stability、创建/库剩余入口、Java21 native interface与隔离dry-run六份证据文件均实际存在且有BUILD SUCCESSFUL。此为日志存在/终态核对，不重复执行已通过任务，不把dry-run或编译当实机PASS。
- 273条工作区状态保留，分支1.21.1/HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，未提交/推送；M3–M5未完成，M6未开始。启动授权尚无答复，无运行中的游戏或构建任务需要等待。
- 本轮完成累计格式检查；后续不能通过重复同一检查制造进展。当前主要下一步仍是用户确认后的独立客户端实际加载/GUI验证，以及独立参考资源/缺失专属场景。禁止把未回答视为授权，不自动启动游戏/覆盖mods，GPU默认关闭，禁止Qt offscreen。


## 2026-11-01 用户更正：客户端验收由人工执行

- 用户明确“客户端验收也是人工”。此前将客户端启动授权列为自动工作阻塞不成立，撤回待启动请求的依赖；Agent不启动游戏，客户端加载/Mixin实际应用/GUI/CJK/实机渲染/多人统一MANUAL/PENDING，不冒称PASS。
- 计划书合并检查点与进度顶部摘要已同步该分工。依AGENTS规则，非阻塞人工验收不阻止自动门槛通过后的检查点提交推送；不因此跳过仍可执行的自动验证或把独立参考缺失当作已通过。
- 当前分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计工作区保留，未提交/推送；M3–M5正在收尾核对，M6未开始。下一步核对合并检查点自动门槛及提交清单，然后按阶段规则提交/推送当前分支；不再等待客户端启动确认。
- 线程goal工具仍返回旧blocked状态，该接口不支持由Agent恢复active；此为调度状态限制，不再作为实施阻塞，不标complete。GPU默认关闭保留CPU，Qt offscreen禁止，模型链接只读。


## 2026-11-01 接续：三模块正式 check 通过

- 用户已明确客户端验收为人工非阻塞MANUAL/PENDING；本轮不再等待启动权限、不启动游戏。
- 已验证PASS：正式前台Java21 :common:check :fabric:check :neoforge:check --continue --console=plain，52秒退出0，日志build/tmp/combined-checkpoint-check-pass.log。包含common全量testClasses、network/cloud/config/identity/math check依赖、完整libraryPreviewRuntimeSmokeTest及三模块validateAccessWidener；两平台test为NO-SOURCE，不能称有双平台JUnit或客户端加载验证。
- 创建失败/空YSM及预算warn为正式预览失败注入，完整入口继续完成并最终通过；不压制stderr。M3骨骼/延迟/时间线与M5A CPU/GPU拒绝任务已有前轮独立PASS证据，不因check未依赖它们便遗漏其证据。
- 当前阶段进入提交前范围/证据核对：累计273条状态需保留本地.codex/.mcp.json/.omc等工具配置，不盲目git add -A。独立Sparkle参考缺失导致默认动画/配件来源完整入口尚不能称通过，实际已验证子组已记录；客户端/Mixin/GUI/多人全部人工待验，不以check替代。
- 当前分支1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，尚未提交/推送；M6未开始。下一步核对合并检查点提交范围与仍缺自动证据并完成阶段检查点，不扩展功能。GPU默认关闭保留CPU，Qt offscreen禁止，模型链接只读。


## 2026-11-01 接续：fallback assets 与独立参考恢复

- 用户提醒默认fallback动画在assets；确认生产实际目录common/src/main/resources/assets/omni-morph/default_animation完整存在，之前缺失的是测试外部对照，不是运行时资源。
- 只读核对本机YesSteveModel独立参考14文件仅10相同，不能冒充Sparkle。git ls-remote确认AIH-Interactive/Sparkle-Morpher可读，已在gitignored refs/Sparkle-Morpher进行depth1/filter=blob:none sparse checkout，仅取所需动画目录；上游HEAD f5a6d8fa0576694c3fe8496786c685fb0b9ad46e。无源模型/生产assets修改。
- 正式前台Java21 :common:ysmDefaultAnimationSmokeTest :common:cosmeticSourceSmokeTest --continue --console=plain退出1；缺目录阻断已解除，当前失败是tac.animation.json逐字节不同。日志build/tmp/default-reference-current-upstream-mismatch.log。
- 精确比较14文件：13文件byte-identical（含main/arm），仅tac不同，且JSON也不同；差异集中tac rifle姿态参数。git历史显示assets由cf7c233首次对齐引入，当前工作区assets无修改，不能把此差异归因本轮1.21.1移植，也不盲目覆盖资源或放宽断言。
- 下一步核对该参考差异历史与fallback实际加载范围，区分完整资源一致性检查和生产main/arm默认fallback行为；保留既有golden/runtime验证，不再把生产assets说成缺失。
- 当前分支1.21.1/HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，未提交/推送，M3–M5收尾，M6未开始。客户端人工MANUAL/PENDING非阻塞，Agent不启动游戏，GPU默认关闭保留CPU，Qt offscreen禁止，模型链接只读。


## 2026-11-01 接续：TAC差异为移植前未加载资源

- CodeGraph确认YsmDefaultAnimationResolver.Domain仅MAIN/ARM两个域，source()只加载对应assets文件并验证固定SHA256，tac不进入fallback resolver；main/arm与独立Sparkle参考byte-identical，生产加载并非缺资源。
- git show pre-1.21.1-port:tac.animation.json与当前bundled字节完全相同，SHA256=4594de398c4d34311d540caa2c1b314f631307fd68d810dfc84a8ac1552b3e88。独立Sparkle浅仓deepen30后tac历史仅到浅边界，无已证明的精确来源修订；不伪造匹配或改参考副本。
- 默认动画完整任务的14文件一致性断言仍FAIL，未修改测试/生产assets；完整cosmetic来源入口亦被相同断言阻断。已向用户发出范围确认：是否把既有未加载tac资源一致性失败登记为非阻塞follow-up并保留assets、完成M3–M5检查点；若需同步tac会改变第三方动画资源，不擅自实施。
- 当前分支1.21.1/HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，未提交/推送。common/fabric/neoforge check已有PASS；客户端人工MANUAL/PENDING非阻塞；等待仅涉及此旧自动断言的处置，不再等待游戏启动。
- 下一步用户答复后按其范围处理检查点，当前不改tac、不降低断言。M6未开始，GPU默认关闭保留CPU，不启动游戏或覆盖mods，Qt offscreen禁止，模型链接只读。


## 2026-11-01 用户指定 Sparkle neo1.21.1 参考仓库

- 按用户明确指定，已clone https://github.com/sdf123098/Sparkle-Morpher.git 的neo1.21.1分支到refs/Sparkle-Morpher-neo1.21.1，提交6272e67149ae0cb0ed8d77e00d77414a68f1d10e；保留原refs/Sparkle-Morpher参考，不覆盖/切换原仓库。新仓库git status干净，refs被主仓库gitignore忽略。
- 实际动画路径为src/main/resources/assets/sparkle_morpher/builtin/default/animations（不是旧测试硬编码common/src路径），14文件。只读逐字节比对：13份相同，仍仅tac.animation.json不同；main/arm一致。未修改生产assets、旧测试断言或参考文件，未将该clone当作运行测试PASS。
- 后续可将用户指定neo分支作为1.21.1源码参考；tac既有差异的处置仍需按实际范围决定，不能因为换仓库便声称失败解除。当前主项目分支1.21.1，HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计WIP未提交/推送，M6未开始；客户端人工验收非阻塞。


## 2026-11-01 接续：指定 neo 参考的 fallback 完整运行时与粒子 CPU 验证

- 将 YsmDefaultAnimationSmokeTest 两处外部参考目录统一改为用户指定 refs/Sparkle-Morpher-neo1.21.1/src/main/resources/assets/sparkle_morpher/builtin/default/animations。保留十四文件逐字节一致性断言，仅将其放在运行时检查之后；任何不一致仍使任务失败，没有跳过或降级该门槛，生产 assets 未修改。
- 正式前台 Java21 :common:ysmDefaultAnimationSmokeTest :common:cosmeticSourceSmokeTest --continue --console=plain，退出1；日志 build/tmp/default-neo-reference-runtime.log。两入口均已实际执行并通过 main/arm 外部采样比较、固定 bow golden、原生 fallback 组装/姿态、作者空声明优先、禁用控制、损坏声明诊断、post-swing、builtin 切换、beginning transition、域隔离与缓存身份检查，输出 PASS default animation runtime；最终仍在 tac.animation.json 一致性断言 FAIL。不能将两个完整任务标为 PASS，也不能称 cosmeticSource 后续所有内容执行完成。
- 已验证 PASS：正式 :common:customParticleSmokeTest --console=plain，退出0，日志 build/tmp/custom-particle-cpu-pass.log。包含 sprite UV/播放、Ribbon geometry/options、particle motion/light field；不代表实机绘制或 GUI 通过。cloud-refresh 已在 material-full-pass.log 与 native-geometry-async-pass-audio-count-fail.log 具有实际子组证据，本轮未重复运行。
- 原计划 Q1 尚不能声明全通过；TAC 既有差异的处理仍待用户决定，不擅自覆盖 assets 或豁免自动门槛。03_astronaut/16_tactics 专属输入缺失仍未验证。客户端人工 MANUAL/PENDING 非阻塞，M6 未开始。
- 分支 1.21.1，HEAD af23872b6c1909779ce829db4dd833f18da7b4bb；累计 WIP 保留，未提交/推送。本轮仅测试参考路径、检查顺序及记录修改；相关 git diff --check 通过。下一步按用户对 TAC 处置的决定完成检查点；GPU 默认关闭保留 CPU，不启动游戏或覆盖 mods，禁止 Qt offscreen，模型链接只读。


## 2026-11-01 接续：配件来源剩余检查与既有骨骼索引契约

- CodeGraph 核对 CosmeticSourceSmokeTest，原默认动画调用位于 testSources 中途，会使 TAC 差异阻断 ZIP/内容变更拒绝、持久 ID、模板创建、catalog、索引和缓存后续检查。将该调用移至所有来源检查之后，仍传入同一 LocalCosmeticSource.prepare 返回的 encrypted wrapper NBT；所有原断言保留、最终 TAC 失败仍传播，不冒称完整任务通过。
- 首次正式 :common:cosmeticSourceSmokeTest 退出1，日志 build/tmp/cosmetic-preparation-neo-reference.log：九种创建模板和 catalog 已通过，暴露 testIndexes 旧循环拒绝断言失败。
- 已核实根因：SkeletonIndex 生产文件与 pre-1.21.1-port 无差异，0ecd27f2a6d6f08009fc20ab873984e956314036 明确为 native hand/arm 重复节点保留首次路径。旧测试仍要求重复节点抛异常，和当前基线不一致；仅改为精确验证循环截断为一个 part/node、一个 root、无循环 child edge，保留深度超限拒绝和实例绑定/结构缓存断言，没有修改生产遍历或放开预算。
- 第二次正式前台 Java21 :common:cosmeticSourceSmokeTest --console=plain 已执行全部来源、九模板、catalog、索引和 .oms/cache 检查并输出 PASS cosmetic source preparation；随后同一 wrapper 的 default runtime 也 PASS，最终仅 tac.animation.json parity FAIL，整体退出1。证据 build/tmp/cosmetic-preparation-index-contract.log；全量 testClasses 编译通过，无 init/源集排除，不能称完整 cosmeticSourceSmokeTest PASS。
- 本轮修改仅 CosmeticSourceSmokeTest 的执行顺序/既有契约断言及文档；git diff --check 通过。分支 1.21.1、HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计 WIP 保留、未提交推送。TAC 既有资源差异处置仍需用户决定；缺失专属模型场景未验证，M6 未开始。客户端人工 MANUAL/PENDING 非阻塞；GPU 默认关闭/CPU 保留，不启动游戏或覆盖 mods、禁止 Qt offscreen、模型链接只读。


## 2026-11-01 接续：neo 参考完整分支历史核实

- 对用户指定 refs/Sparkle-Morpher-neo1.21.1 完成 fetch --deepen=100 后 fetch --unshallow origin neo1.21.1；rev-parse --is-shallow-repository=false，工作树/分支 HEAD 未改变。引用分支的 tac 文件历史为 5ef949f（2026-06-23）及 df86b5d（2026-08-02）；当前上游 blob=68a1012eed4bd0d7f136951ad255b4ce72c4e75f，本项目 bundled blob=bceeed8d823631cdc2be1b2a64f9d1c645fdc6bf，git log --all --find-object 未找到 bundled blob。此结论仅针对已获取的 neo1.21.1 可达历史，不声称所有其他分支/仓库也不存在。
- 当前没有可据以恢复原十四文件精确一致的已证明参考修订；不再反复重跑同一失败断言或继续无界寻找历史。main/arm runtime 与配件 preparation 的实际通过证据见前两节，TAC 最终断言仍 FAIL，生产资源保持不变。
- 已基于新证据向用户请求具体处置：保留 assets 并明确将旧 TAC parity FAIL 登记为非阻塞后完成 M3–M5 检查点，或先同步 TAC；原因是 AGENTS 检查点自动门槛仍存在，不能自行豁免或更改动画资源。待答复，未将请求或经过时间当作批准。
- 当前分支 1.21.1/HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，273条工作区状态保留，未提交/推送，M6未开始。本轮无生产/测试源码修改、无新增构建测试；仅参考历史读取及本记录。客户端人工 MANUAL/PENDING 非阻塞，GPU 默认关闭/CPU 保留，禁止 Qt offscreen，不启动游戏或覆盖 mods，模型链接只读。


## 2026-11-01 阻塞审计：等待 TAC 处置决定

- 完整参考历史核实后，连续三轮仍为同一阻塞：TAC parity 自动断言 FAIL，用户尚未批准将其登记为非阻塞，也未要求同步资源。自动续接不构成具体选项批准；不重复执行同一失败测试，不自行更改资源或检查点门槛，不提前实施 M6。
- 当前最小后续操作：收到用户处置决定后，按批准范围处理 TAC；检查 M3–M5 diff/status、更新计划，完成验证要求后提交推送当前分支并核对远端 HEAD，才进入 M6。客户端人工验收继续非阻塞 MANUAL/PENDING。
- 分支 1.21.1，HEAD af23872b6c1909779ce829db4dd833f18da7b4bb，累计修改全部保留、未提交/推送。此次只核对日志和保存阻塞状态，没有新增实现或测试，也未完成总目标。目标按三轮阻塞审计标记 blocked，等待用户决定。


## 2026-11-01 用户批准同步 TAC：M3–M5 自动检查点收尾

- 用户明确回复“同步”。已将指定 refs/Sparkle-Morpher-neo1.21.1（neo1.21.1，6272e67149ae0cb0ed8d77e00d77414a68f1d10e）的 tac.animation.json 复制到 bundled default_animation；逐行审查18行替换，涉及 rifle 姿态/locator 和末尾换行，未修改其他动画。当前工作树文件 SHA256=08522ce3a68de9bc4ec7e30c81f0b5e0246ed7d4c0c67463d6fa0de72c0f2707，十四文件逐字节一致14/14。
- 已验证 PASS：前台 Java21 :common:ysmDefaultAnimationSmokeTest :common:cosmeticSourceSmokeTest --continue --console=plain，完整入口退出0，日志 build/tmp/tac-sync-default-cosmetic.log。保留资源parity与运行时/来源所有断言，无豁免、无源集排除；此前 TAC 阻塞已解决，不再等待旧处置请求。
- 已验证 PASS：随后 :common:check :fabric:check :neoforge:check --continue --console=plain，46秒退出0，日志 build/tmp/m3-m5-final-check.log。common 全量测试编译与 check 依赖包括完整 libraryPreviewRuntimeSmokeTest；两平台 test 仍 NO-SOURCE，不能声称真实客户端或双平台JUnit通过。前轮独立M3 CPU骨骼/延迟/时间线与M5 GPU拒绝/Java路径已有正式PASS日志，见历史记录。
- 合并实施与规定可执行自动门槛完成，进行提交前 diff/status 审查；本检查点保留真实客户端/Mixin apply/GUI/CJK/多人为人工 MANUAL/PENDING（用户已指定非阻塞），专属缺失模型/符号链接权限与实际GPU性能覆盖限制仍明确保留。M6不在本次实现检查点中，不声称最终发布完成。
- 提交范围限定 common、fabric、neoforge 和两份 porting 记录；.codex/、.mcp.json、.omc/ 本地工具配置保留不提交，refs/build/交接手册均为ignored。git diff --check已通过；额度可靠接口主要池 used18%/10080min，未触发预警/交接阈值，不推断Plus/Pro。


## 2026-11-01 M6 接续：双平台正式转换、依赖与 NeoForge 标识

- 已在M3–M5提交推送核对成功后开始M6；当前分支1.21.1/HEAD ddf2bcf8b1f769f26df659bf2bc25adeb4044505。M6尚未完成、未提交/推送，所有修改保留；本地.codex/.mcp.json/.omc不动。
- 首次正式双平台remap：Fabric通过但common未转换；NeoForge失败缺AW（build/tmp/m6-first-dual-package.log）。查明common/build.gradle显式禁用transformProduction*生成空JAR，Fabric临时namedElements绕开转换；恢复转换并让Fabric使用transformProductionFabric。
- 随后转换器稳定FAIL：ASM handlerRangeBlock=null（m6-transformed-dual-package.log、m6-transform-stacktrace.log）。本地Architectury5.2.91 javap确认TransformExpectPlatform清空instructions但未清理旧tryCatchBlocks，原common五个桥接类的带反射try/catch入口触发该缺陷。仅将原反射body提取private *Fallback方法，入口保留@ExpectPlatform/委托，保持raw common测试行为与异常语义。正式双平台transform PASS（m6-transform-method-fix.log），未升级/修补第三方工具。
- common保留org.figuramc.figura命名空间，平台实现为top.aihmc.omnimorph；转换器按前者生成调用。Fabric/NeoForge shadowJar定向relocate三组utils/config/entries平台实现包到org桥接目标，未全仓命名迁移。正式双remap PASS（m6-bridge-dual-package.log）。实际javap确认最终两包PlatformUtils八个入口invokestatic指向各自fabric/neoforge Impl；每包六种桥接owner存在。
- 检查真实JAR发现NeoForge原仅内嵌Lua/WebSocket3库；补齐common已有Protobuf/aircompressor/Concentus/Vorbis/AVIF/WebP及TwelveMonkeys传递库。:neoforge:remapJar PASS（m6-neoforge-runtime-libraries.log）。Zip核查Fabric20、Neo14个nested JAR，声明指向存在文件、Neo jarjar条数匹配；own classes major<=65，Neo META-INF/accesstransformer.cfg存在。minecraft-dev AW校验15条有效、0错误/警告。
- 已验证PASS：上述桥接/依赖修改后 :common:check :fabric:check :neoforge:check :fabric:remapJar :neoforge:remapJar --continue --console=plain，1m11s退出0（m6-bridge-check-and-packaging.log）。仍两平台JUnit NO-SOURCE，完整preview等check依赖实际执行，不是游戏加载证明。
- 本地NeoForge FML4.0.41 loader-sources ModInfo.java明确VALID_MODID=^[a-z][a-z0-9_]{1,63}$，会拒绝原omni-morph。将仅NeoForge加载器ID改omni_morph，namespace保持omni-morph；入口常量/两个subscriber、ModList自身查询和metadata alias一致，资源/存储/common协议品牌不改；toml modproperties/dependencies同步，mandatory换实际type=required。:neoforge:remapJar重编译/打包PASS（m6-neoforge-loader-id.log），最终TOML静态解析确认ID正则/namespace/三mixins/required依赖/资源路径；真实FML加载仍MANUAL/PENDING。最后ID修改在完整check之后，完整check证据不能冒称包括此变更；Neo编译/打包证据有效。
- 修改文件：common/build.gradle；common org桥接ConfigKeyBind/EntryPointManager/FiguraModMetadata/FiguraResourceListener/PlatformUtils；fabric/build.gradle；neoforge/build.gradle、FiguraModForge、FiguraModClientNeoForge、FiguraCommandsNeoForge、PlatformUtilsImpl、FiguraModMetadataImpl和META-INF/neoforge.mods.toml；另进度/交接记录。git diff --check通过。
- 下一步M6最小工作：适配现有.github/workflows/fabric-build.yml为Java21双加载器矩阵，保留既有六平台native组装验证；native/packaging.gradle当前硬编码Fabric且七位commit假设与当前八位git short可能不一致，必须按实际产物核对；四语CHANGELOG新增1.0.0-alpha.7移植/已知限制，版本不递增。随后最终check/双打包/产物审计、更新计划、审查diff并提交推送核对远端。此轮未动CI/native组装/CHANGELOG，不把M6标完成。
- 当前remap警告包含可选renderRaw、跨平台lambda别名及Gecko变量名，先前有静态字节码核实，仍不能称实际Mixin apply通过。客户端人工验收非阻塞MANUAL/PENDING，不自动启动游戏/覆盖mods；GPU默认关闭/CPU保留，Qt offscreen禁止，模型链接只读。


## 2026-11-01 M6 候选：双加载器 CI、原生组装与发布记录

- 完成现有 fabric-build.yml 的双加载器改造：base/assemble/publish 两loader矩阵；native仍六平台一次构建共享；final验证为两loader×六平台，避免artifact重名和基包误混入native输入。构建、头文件和实际JNI执行使用Java21；保留原Java25 JEP472拒绝访问检查为独立步骤，未用Java21不支持的参数冒充负例。
- native/packaging.gradle 支持 -PnativeLoader=fabric|neoforge（默认Fabric）、分loader输出目录、对应mod descriptor/jarjar核查；base-manifest记录loader。校验实际7–40位十六进制提交前缀且须匹配完整sourceCommit，修复原固定7位错误；原六平台/ABI/source/run/哈希/许可证/原ZIP内容不变门槛保留，新增错误loader拒绝用例。
- stageNativeBase依赖所选remapJar、common:check及compileNativeInterface，CI不调用绑定可视GL窗口的两平台check；完整三模块check/preview已在本机真实GL窗口夹具执行，不能因此宣称人工客户端验收通过。NeoForge JAR补齐项目LICENSE/CREDITS，final verifier增加Neo TOML/AT/jarjar和匹配提交前缀核查。
- 已验证PASS：nativePackagingSmokeTest两loader正式入口；最后Neo组合 :fabric:check :neoforge:check + nativePackagingSmokeTest（含common:check）退出0，日志m6-final-neoforge-check-packaging.log；最后Fabric入口含重编译/remap/错误loader断言退出0，m6-final-fabric-packaging.log。两个最终verifier对合成原生包也PASS：m6-final-neoforge-artifact-verifier.log、m6-final-fabric-artifact-verifier.log。合成包不可作JNI/真实性能证据，不发布。
- workflow由官方actionlint v1.7.12检查退出0（m6-workflow-actionlint.log）；九个run块经替换矩阵表达式后的PowerShell AST解析无错误。actionlint仅置ignored build/tmp，quiet工具日志已预清空避免残留旧输出误作证据。四语CHANGELOG新增1.0.0-alpha.7，明确人工/缺语料/性能限制；DISPLAY_VERSION从旧Alpha5改Alpha7，规范版本保持不递增。
- 本地可执行门槛通过，准备在当前1.21.1提交推送M6候选并启动实际远端CI；远端结果仍PENDING，不标M6/最终发布完成。只读GitHub API已确认workflow 387386689 active且非交互凭据可用；无凭据值输出或落盘。额度used18%/10080min，无交接触发。
- 下一步：候选提交推送并核对远端HEAD，dispatch指定1.21.1提交的fabric-build.yml，记录run ID/URL并检查实际job结果/最终产物；失败按具体日志修复，不重复启动同一活跃run。人工客户端/Mixin/GUI/CJK/多人仍非阻塞MANUAL/PENDING，不自动启动游戏/覆盖mods；GPU默认关闭保留CPU，Qt offscreen禁止，模型链接只读。
