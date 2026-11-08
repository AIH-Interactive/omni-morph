# OmniMorph 撤销 SPM 式硬件加速计划

日期：2026-11-08。状态：阶段一至四实施与自动验证已完成，实机验收按用户要求SKIPPED。详细实施证据见docs/acceleration-revert。

目标：撤销当前借鉴 SPM 思路形成的 GPU 与 JNI/SIMD 加速体系及其专用基础设施，恢复双引擎普通 Java 顶点提交，保留当前 OMP 其他功能。用户已反馈多次尝试无提升、部分场景倒退；这是撤销决策的依据，不将其扩写为未经测量的具体性能归因。后续硬件加速另行依据 OMP 的实际瓶颈设计，本次不引入替代后端。

## 对比基线与结论

当前工作树：`D:\UserData\Desktop\Project\OMP\OMP_26.1.2`，分支 `revert_acceleration`，HEAD `12e1e96fc71cfa91d6476d1d749429aba89e8f46`。原始参考：`D:\UserData\Desktop\Project\OMP\Figura` 的现有磁盘源码。比较以实际文件为准，不假设两个工作树均等于各自提交。

已用两边 CodeGraph 定位渲染入口，并逐块对比原始 `org/figuramc/figura/model/rendering/ImmediateFiguraRenderer.java` 与当前迁移后的 `top/aihmc/omnimorph/model/rendering/ImmediateFiguraRenderer.java`。仅按同名目录比较会将包迁移误判为删除，实施不得照此覆盖文件。

| 对比项 | 原始 Figura 或当前源码事实 | 撤销决策 |
| --- | --- | --- |
| Figura 顶点提交 | 原始代码经过主层、次层 VertexBuffer，再由 VertexConsumer 提交；当前增加 Lease、DrawBatch、GPU enqueue 和数值后端 | 以当前普通分支为主体移除加速分支，参考原始提交顺序 |
| 蒙皮 | 原始已有权重蒙皮；当前另有 FiguraSkinning.setup 与 bind-aware deltas、衣装骨骼覆盖 | 保留当前蒙皮语义，不能恢复原始整个方法或文件 |
| 当前 Figura 扩展 | 姿态捕获、OutfitInstructionBudget、PreviewBoneHighlight、预览粒子相关矩阵更新与加速代码共处 | 原样保留对应产品行为 |
| YSM | 当前 YsmRenderer 同时存在逐 cube/quad 的普通路径和 MeshHandle/DrawBatch 路径 | 保留 YSM 引擎，以其普通路径为基线；不以 Figura 缺少对应功能为删除依据 |
| 生命周期 | AccelerationOwner 的退役状态已被模型库、衣装、UserData 使用；Lease 被 FiguraRenderPose/YsmRenderPose 使用 | 迁出并保留普通渲染仍需的有效性检查，再删除加速服务 |
| 打包 | 根 build.gradle 引入 native/packaging.gradle；CI 构建 JNI 与六平台 native，再合成双加载器 JAR | 恢复普通 Fabric/NeoForge 构建和产物上传，移除加速专用 native 链 |

这里的“撤销硬件加速”指移除 OMP 自建加速体系。Minecraft 正常图形渲染、普通 RenderType/VertexConsumer、纹理上传继续保留。`native` 字样也不等于 JNI 加速：例如 OutfitNativeRenderer 的原生引擎渲染和 AVIF 解码依赖属于保留功能。

## 撤销与保留范围

以下路径以 `common/src/main/java` 为 Java 根目录。

| 文件或模块 | 计划动作 | 保留边界 |
| --- | --- | --- |
| `top/aihmc/omnimorph/render/acceleration/` | 删除 GPU 后端、设备适配、shader 生成、UBO/SSBO、姿态 arena、合批、fence 回收、JNI/SIMD、探测、模式控制、诊断及专用数值抽象 | 先迁出普通功能所需的 owner/lease 语义；最终不保留空壳后端体系 |
| `model/rendering/ImmediateFiguraRenderer.java` | 删除加速 imports、staticMeshes、skinnedMesh、DrawBatch/transform/GpuSubmission 分支；恢复直接消费普通顶点 | 纹理选择、透明高亮、alpha、UV、光照、overlay、vertexOffset、复杂度与主次层顺序 |
| `model/rendering/FiguraStaticMeshCache.java` | 删除加速专用缓存及调用 | 不触碰普通模型、纹理缓存 |
| `model/ysm/YsmRenderer.java`、`YsmAccelerationMeshes.java`、`YsmModelRuntime.java` | 移除加速网格及提交；清理专用缓存失效调用；保留逐面提交 | 动画/Molang、材质、面显隐、面预算、挂点、第一人称、模型缩放与服饰 |
| `org/figuramc/figura/avatar/Avatar.java`、`UserData.java`、`AvatarManager.java` | owner/资源失效调用迁移；删后端重试 | 实例退役、模型切换、资源重载、旧实例拒绝复用 |
| `model/outfit/FiguraRenderPose.java`、`OutfitComposition.java`、`OutfitNativeRenderer.java` 与 `model/ysm/YsmRenderPose.java` | 改用中立生命周期检查 | 姿态快照、有效期、衣装绑定与指令预算，不删除整个类 |
| `model/library/LibraryFiguraPreviewRenderer.java`、`LibraryPreviewSession.java`、`OutfitObserverService.java`、`OutfitInstanceSync.java` | 仅迁移 owner 检查 | 内置模型库预览、衣装同步、驻留实例管理全部保留 |
| `org/figuramc/figura/config/Configs.java`、`ConfigManager.java` | 删除硬件加速分类、enabled/mode/status、ssbo/simd_pack/merge_pass/model_mesh 及设置回调 | 其他配置和用户现有数据；旧加速键只按现有未知键机制忽略，不重置整个配置 |
| `org/figuramc/figura/FiguraMod.java`、`mixin/render/GameRendererMixin.java`、`mixin/MinecraftMixin.java` | 删加速通知、探测/帧驱动、关闭后端钩子 | 普通生命周期所需的帧推进继续保留，其余 tick/render/close 行为不变 |
| `top/aihmc/omnimorph/mixin/render/GlBufferAccessor.java` 与 `omni-morph-common.mixins.json` | 确认只服务被删后端后，删除 accessor 及注册 | 不删除其他 mixin |
| `common/src/main/resources/omni-morph.accesswidener` | 核对末尾加速材质字段的剩余引用，再按项删除 | 普通材质、其他功能仍用的访问开放保留 |
| `assets/omni-morph/lang/en_us.json`、`zh_cn.json` 及设置界面 | 删除硬件加速专用文案；审查 OutfitControlsScreen 相关文字 | 衣装、调试及普通设置文案保留 |
| `native/`、根 `CMakeLists.txt`、`CMakePresets.json`、`build.gradle` | 撤销加速 C++/JNI 构建、打包脚本及 apply 入口 | AVIF 等其他 native 依赖、许可证保留 |
| `common/build.gradle`、`.github/workflows/fabric-build.yml` | 移除专用加速测试任务、JNI headers、stageNativeBase/assembleNativeJar 链；接普通加载器产物 | Fabric/NeoForge 编译、普通验证、上传及失败日志仍可用；不启用已注释的 Forge |

必须保留全部其他 OMP 功能，包括模型导入、YSM、衣装、模型库、云端协议、脚本、音频、粒子、纹理格式、身份迁移及当前 Minecraft 版本适配。粒子物理 `acceleration` 不在撤销范围。`refs` 参考代码和历史分析不删除。

## 核心验收标准

用户明确要求：模型库、缓存加密、YSM 引擎等原始 Figura 没有的 OMP 功能，在撤销后依旧正常。这是最终功能验收标准；仅编译成功、删除加速引用或恢复原始 Figura 的画面均不足以判定通过。

| 保留能力 | 必须验证的实际行为 | 已有自动验证入口及补充要求 |
| --- | --- | --- |
| 模型库 | 列表/元数据、导入下载、缓存复用、预览、装备/卸下、模型切换、上传与权限行为保持原状 | libraryMetadataSmokeTest、libraryClientSmokeTest、libraryTransferSmokeTest、libraryPreviewRuntimeSmokeTest、libraryEquipmentSmokeTest、libraryManagementSmokeTest、libraryUploadSmokeTest；按实际覆盖选择并保留相关回归 |
| 缓存加密 | 既有加密缓存可读取、新写缓存仍按原格式保护、损坏或错误授权输入按原规则拒绝；加载与预览不新增明文模型落盘 | modelCacheCodecSmokeTest、libraryPreviewCacheSmokeTest、libraryPackagePreparationSmokeTest；核对现有测试覆盖，缺失的受影响行为补最小回归 |
| YSM 引擎 | 加密源/二进制模型加载、纹理解码、动画/Molang、控制器切换、第一人称、挂点、状态同步与多实例隔离保持原状 | ysmDefaultAnimationSmokeTest、ysmControllerTransitionSmokeTest、ysmBuiltinControllerSmokeTest、ysmPublicPoseSmokeTest 及当前 YSM 导入/解码测试 |
| 衣装及其他 OMP 扩展 | Figura/YSM 混合衣装、骨骼覆盖、预算、粒子/音频、云端协议和现有用户配置继续工作 | 保留现有业务测试；受生命周期迁移影响的衣装/模型库用例必须执行 |

这些条目用于保护既有行为，不要求新增原本没有的功能。自动测试的 PASS 只覆盖实际断言；真实模型库操作和游戏渲染作为最终人工验收手段，按用户要求记录为 SKIPPED（用户决定，未执行）并跳过，不据此把任务设为受阻。已有缓存格式、密钥/授权规则、持久化位置和业务协议不属于撤销改动范围。

## 分支与提交历史核对

用户要求确认后再次读取 Git：当前分支仍为 `revert_acceleration`，上游仍为 `origin/revert_acceleration`，HEAD 仍为 `12e1e96fc71cfa91d6476d1d749429aba89e8f46`，未切换提交。

当前 HEAD 可达历史中，`34dcd142` 引入 YSM 加密源与二进制管线，`3e42d20d` 涉及云端衣装集合与包装模型缓存，`91b2a416` 修复 YSM ImageIO 包装异常后的取消与限制，`96f811d2` 保留远端 YSM setup layers 与播放连续性。这些业务能力属于保留范围。`11b22e92`、`13ffa618` 分别涉及整模加速路由/选项和 SIMD 姿态打包，可辅助定位撤销差异。

实施前使用当前分支可达历史和具体文件 diff 识别改动；`git log --all` 只用于发现线索，其他分支提交不能当作当前实现。不得整批 revert 混合提交，避免一起撤销业务修复。每个阶段写入前复核分支和工作树；若与记录不符，先重新核对，不自动切换或重置。

## 硬件加速提交审计与优先撤回策略

用户最新要求：能够通过 commit 撤回时，优先考虑 commit 撤回。因此实施采用“审核历史提交 → 在隔离工作区逆序试撤 → 验证保留功能 → 提交”的方式；人工删除只处理混合提交、路径迁移、后续业务依赖及撤回后残留。当前仅完成提交记录、文件清单及部分关键补丁核对，尚未执行试撤，不将候选描述为已验证可无冲突撤回。

### 加速提交主线

以下提交均来自当前 HEAD 可达历史。表按引入顺序组织，实际撤回按选定提交的反向拓扑顺序执行，不直接按表从上往下 revert。

| 提交 | 已核对的加速改动 | 处理建议 |
| --- | --- | --- |
| `119ded25`、`d1b54b31` | 最初计划与 H0 基线/绘制通路验证 | 历史证据保留；专用测试入口按需清理，不撤回历史文档作为功能动作 |
| `93f6e33c` H1 | 两引擎共享批次；引入 AccelerationOwner、Lease 和 YSM 延迟姿态失效 | 原始接入补丁是重要撤销依据；完成生命周期保全后按块逆向，不整笔删掉当前依赖 |
| `295c5483` H2 | Windows SIMD、JNI、native loader、CMake 与打包 | 专用部分优先逆向撤回；构建文件按现状合并 |
| `8f444cf9` H3 | 加速配置、回退、状态 UI、ReadOnlyConfig/ReadOnlyElement | 优先撤回专用配置和接线；只读控件无其他引用才删 |
| `8c3955c1` H4 | GPU 后端、设备适配、shader、资源退休与关闭钩子 | 专用后端优先撤回；保留迁出的生命周期 |
| `76b29910` H5 | 双引擎 GpuSubmission、访问开放、混合材质测试修改 | 按现有渲染逻辑合并逆向补丁；保留后续业务修复和普通测试 |
| `6692eb33`、`5aabd41f` H6 | 四平台再扩到六平台 native 内核、校验打包与 CI | 专用 native 撤回；CI 直接构建普通双加载器，不整体还原旧流水线 |
| `fde16660` H7、`562ccfe3` | 有界刚性合批、YsmAccelerationMeshes、刚性 shader 修复 | 专用代码候选；普通 YSM 逻辑与历史证据保留 |
| `b3168d47`、`f1f78930` | 加速网格校验缓存、刚性组索引与对象分配优化 | 加速专用，优先 commit 撤回 |
| `9a54c885`、`db8e516e`、`20c00a73`、`a5aaccf2`、`4f8579f6` | Figura 加速网格复用、GPU fence 后缓冲复用、组布局缓存、pose 视图、UBO 尺寸分档 | 优先 commit 撤回；9a54c885 同时触及当前 Figura renderer 与 Gradle，必须核对冲突内容 |
| `4b05711a`、`c934a552`、`c2ddd95d`、`64aedaba` | SSBO S1～S4 探测、自检、frame arena、绘制路由与诊断 | 专用增强，优先 commit 撤回；同步 mixin 和文案 |
| `13ffa618` | SSBO S5、SIMD pose pack、ABI 2、native 工具及元数据 | 优先 commit 撤回；不恢复用户已删除 tools 文件 |
| `41d24eab`、`c12fb261` | R4-1 单区间刚性组绘制、R4-2 材质边界合并 pass | 加速专用，优先 commit 撤回 |
| `18ec5963`、`1c72f862`、`11b22e92` | R4-3 整模布局、shader、路由与开关 | 依 M3→M2→M1 撤回；开关及测试一起核对 |
| `de10c949`、`226cc9cd` | P4 加速静态网格/刚性部件检查、YSM 加速网格检查与 GPU 透明面排序 | 文件清单均落在加速缓存/提交及测试，优先 commit 撤回 |
| `869be295`、`0ec53ca1` | GPU 探测日志、等待游戏 shader 就绪 | 最新加速专用修复，优先在底层实现之前撤回 |
| `1efe3bc5`、`c95dcf55`、`44e3bcf5` | 双加载器 native CI、生产构建策略、缺失平台打包 | 只撤 native 接线，保留普通加载器构建/产物发布职责 |

### 禁止随加速提交整体撤回的内容

- `5841581a`：YSM 普通姿态只读视图复用，修改 YsmModelRuntime/YsmRenderPose/YsmRenderer 与延迟姿态测试；保留，不因属于性能优化时期而撤销。
- `dcd7a8db`：衣装 SkeletonIndex 在部件图不变时复用骨骼绑定，只有该模块及对应业务测试；保留。
- `6f930cf8`：模型库集成提交包含 48 个文件，加速部分仅夹杂 NativeLibraryLoader 版本变化；不能因触及加速目录而整体撤回模型库。
- `006e1fd2`：帽子挂点、Iris 粒子偏移、版本展示等业务修复与 native loader 版本变化混合；保留业务修复。
- `f91dcc7f`：恢复上游 Figura 包名的迁移；保留。H1～H5 补丁包含旧 `top/aihmc/omnimorph/avatar` 等路径，必须映射到现有 `org/figuramc/figura` 路径后审查。
- `e826e441` 及版本/多语言更新：不回退产品版本，不删除无关变更日志。

H1 父提交为 `d1b54b31975ebae55cadeea9c43030e5198f6edf`，可用来查看 OMP 接入共享加速前的普通渲染。这比把当前项目整体恢复成原始 Figura 更接近业务基线，但仍不能覆盖其后新增模型库、衣装、缓存保护及 YSM 修复。

### 试撤与落地办法

1. 实施开始先复核当前分支、HEAD、已有删除。创建隔离临时 worktree 做试撤，使用用户规则允许的无 `codex/` 前缀分支名；不 checkout/reset 当前工作树，不自动 stash 用户改动。试撤环境的结论须最终在保留当前删除的交付工作树验证。
2. 第一阶段先完成普通生命周期保全。之后对上述专用增强按反向拓扑分组使用 `git revert --no-commit <commit>`，保留原提交 ID 到新提交说明中；阶段内的候选逆序撤回可合为一个阶段提交，不跨已完成阶段积攒。
3. Git 冲突是处理信号，不使用全局 ours/theirs 强行解决。逐块保留业务代码，拒绝恢复已删除工具、历史路径或旧配置。若一笔 commit 需要大幅重写才可撤，记录原因并改用其逆向补丁做最小定点修改。
4. H1～H5 涉及当前业务依赖，优先采用历史逆向补丁而不是重新猜写普通算法；保留第一阶段生命周期及当前蒙皮、预算、衣装、预览行为。不得使用连续提交范围批量 revert，因为加速提交之间穿插大量业务提交。
5. 每组检查最终文件差异与普通功能测试，只有撤回补丁实际适用且测试通过才标记“可通过 commit 撤回”。依然先完成第二阶段双引擎撤销，再完成第三阶段构建/专用基础设施清理；专用增强的逆序撤回允许在第二阶段内作为准备动作，阶段边界由可编译、可验证的最终状态确定。

本次没有运行 git revert、没有创建 worktree，也没有修改分支。优先 commit 撤回是实施策略，不代表现有脏工作树可直接执行全部历史 revert。

## 生命周期解耦的最小设计

当前 `AccelerationOwner` 实际只提供单实例 generation 与不可逆 retire；`AccelerationService.Lease` 保存 owner、ownerGeneration、resources、frame、view。`isCurrent` 检查 owner 未退役、owner generation 相同、资源代次与帧相同；它没有比较当前 view，迁移时不新增跨视图拒绝规则。

把这些必要数据和检查迁到普通渲染生命周期模块，拟用 `RenderOwner` 与 `RenderLifetime`，最终命名遵循届时仓库结构。迁移保持原调用时点、并发可见性、捕获与失效语义，不引入新的服务框架。`YsmRenderPose` 原有 lease 可为空的路径保持其现有含义。

需要迁移的入口包括：Avatar 退役、AvatarManager 资源失效、GameRendererMixin 帧推进、YsmModelRuntime 姿态捕获、OutfitComposition 姿态捕获，以及模型库/衣装/UserData 的实例可用性检查。先完成全部调用方迁移并验证，再删原类型。GPU fence、显存退休队列和后端切换失效只属于被撤销实现，不迁入普通生命周期模块。

这项改动是保留既有功能所必需的解耦，不开展其他生命周期重构。不能把 `isCurrent` 替换为恒真，也不能只看 `avatar.loaded`。

## 实施顺序与阶段验收

以下是未来实施步骤，本次未执行。严格依序推进；每阶段完成自动验证后，更新记录、检查 git status/diff、仅提交本阶段文件、推送同名当前分支并确认成功，然后开始下一阶段。不得将当前已有用户删除顺带暂存或恢复。

### 第一阶段 保全普通功能依赖

1. 重新核对工作树与本文清单，记录现有普通渲染和混合功能测试入口。
2. 将 owner/lease/frame/resource 语义迁出加速包，迁移所有普通功能调用；加速层在本阶段暂时引用迁出后的类型，以保持可编译。
3. 验证实例退役后拒绝绘制与复用、资源重载拒绝旧姿态、跨帧快照失效、有效当前帧姿态仍能绘制，以及 YSM 空 lease 既有路径。
4. 执行受影响的衣装、YSM 延迟姿态和模型库测试；确认两加载器编译及 testClasses 不因 API 迁移失败。

通过标准：普通功能不再依赖加速 owner/lease 类型；等价性测试通过；没有删除任何产品能力。

### 第二阶段 撤销双引擎加速提交

1. Figura 采用当前 `!acceleration.accelerated()` 分支的顶点算法，删除分流和加速网格。保留必要的生命周期拒绝旧提交检查。
2. VertexBuffer 仅在 GPU 分支移除后确认是否恢复 `Consumer<VertexConsumer>`；主层、次层、材质插入顺序及回调时序必须保持。保留高亮需要的 texture 字段。
3. YSM 让 renderGeometry 使用当前 renderCube→quad→vertex；保留可见面预算消费，不能重复扣预算或跳过预算。
4. 删除两引擎不再使用的专用网格缓存；删 GPU enqueue/transform 调用。此时可暂留未引用的后端实现，下一阶段清理。
5. 比较修改前关闭加速与修改后实际顶点流：位置、法线、UV、颜色/alpha、light、overlay、vertexOffset、顶点数及顺序。Figura 刚性、权重蒙皮、负/非均匀缩放；YSM 显隐、透明、预算截断均应覆盖。

通过标准：双引擎生产渲染不再调用 GPU/JNI 数值路径；普通顶点与预算行为回归通过；衣装骨骼覆盖、姿态捕获及模型库高亮测试继续通过。

### 第三阶段 删除专用基础设施并恢复普通打包

1. 删除整个剩余加速包、JNI/C++、加速配置、专用探测通知及关闭逻辑，清理 mixin/访问开放和翻译。
2. 删除加速 Gradle 接线和 native 包装。CI 直接构建并上传 Fabric/NeoForge 普通发行 JAR，保持原产物用途与失败日志，不重构其他流水线。
3. 清理被删除测试的任务与 dependsOn。旧测试中覆盖普通行为的断言迁移保留，禁止整目录删除测试导致蒙皮、材质、衣装、延迟姿态覆盖丢失。
4. 用含旧 hardware_acceleration 键的配置验证正常读取，确认其他配置值不变；不删除用户缓存目录，不新增兼容后端。
5. 检查生产 JAR：不含本项目加速类、omnimorph_accel 库、加速 manifest 或专用 shader；普通资源、双引擎与合法第三方 native 仍存在。

通过标准：生产源码、配置、mixin、构建任务与产物无悬空加速引用；普通 Fabric/NeoForge 构建和相关自动测试通过。CI 若尚未运行，记录远端验证状态，不能以本地打包冒充 CI 成功。

### 第四阶段 回归记录与交付

自动回归覆盖前述生命周期、顶点提交与保留功能，完成差异审查和文档更新。同步 README 中确实描述已删除能力的段落，历史方案保留并注明已被本撤销方向取代，不把历史记录改写成从未实现。

实机验收仅作为最终人工验收手段。验收清单保留：Figura/YSM、同模型多实例、混合衣装、模型切换、重载、世界/第一人称/内置预览、半透明/发光与常用兼容环境。按用户明确要求，以上实机项统一记录为 `SKIPPED`（用户决定，未执行）并跳过，不启动游戏、不等待人工结果、不反复请求验收，不作为阶段推进、提交推送、交付或目标完成的阻塞条件；不得记为 PASS。自主实施和自动验证继续按计划完成。后续用户自行验收时采用真实 Minecraft 窗口；Qt 若用于相关验证，只能使用真实 Windows 窗口，严禁 offscreen。

通过标准：自动验收达到前述范围，剩余实机项状态如实记录，完成阶段提交推送和交接。无需新增优化或证明 FPS 提升才能结束撤销。

## 验证执行与性能记录

所有构建、打包、测试均使用前台 PowerShell 和日志管线，示例：

```powershell
& .\gradlew.bat :common:testClasses :fabric:build :neoforge:build --no-daemon --console=plain 2>&1 | Tee-Object -FilePath "build.log"
$buildExitCode = $LASTEXITCODE
Get-Content -LiteralPath "build.log" -Tail 80
if ($buildExitCode -ne 0) { exit $buildExitCode }
```

实施时检查现有任务实际注册与依赖，逐项加入受影响 smoke tests。加速专用测试可随实现移除；`CosmeticBoneOverrideSmokeTest`、`OutfitNativeRenderSmokeTest`、`YsmDeferredPoseSmokeTest` 等混合测试应迁移普通行为覆盖。每阶段运行 `git diff --check`，不因格式整理改动无关代码。

性能对照以“修改前加速关闭”和“撤销后”为同功能基线；原始 Figura 仅辅助确认算法和提交顺序，不能用缺少 OMP 功能的 FPS 当作等价验收。若执行性能测试，固定模型、实例数、视角、分辨率、渲染距离、模组/光影、预热和采样时长，记录帧耗时中位数及高分位、CPU 分配与顶点/draw 数。对明确可复现的退化先检查本次改动，不因此引入新硬件方案。没有采样就不写提升百分比。

## 工作树保护与回滚

编写时已有 122 个已跟踪 tools 文件删除、`refs` 及两份历史分析/计划未跟踪。此次不恢复工具、不清理引用目录、不提交这些既有改动。独立 tools/preview 已被用户删除；内置 model/library 预览是保留的产品功能，二者不得混淆。

未来每阶段仅提交授权范围。回滚针对该阶段提交做定向恢复，保护用户后续改动，不 reset 整个仓库、不复制 Figura 覆盖 OMP。阶段之间存在依赖时按逆序回滚；普通生命周期的迁移与调用方保持配套。

`加速方案组合修改计划.md` 的 R0～R7、A～F、P0～P9 不再作为本次执行路线；不实施其中替代优化。本计划结束后没有自动启动的新加速阶段。后续如另立任务，应先测 OMP 的真实瓶颈，再决定方案，不预留当前 SPM 式后端接口作为强制约束。

## 本次计划书交付状态

已完成源码静态对比、撤销清单、保留边界和阶段验收定义。生产代码未修改，撤销未实施；构建、测试与游戏未运行。实际撤销工作待后续实施指令。交接记录见根目录 `AGENT_HANDOFF.md`。

分支记录更新：按用户要求，原 preview-acceleration-compare 已重命名为 revert_acceleration，并成功设置同名 origin 上游；HEAD 不变。此操作仅推送已有提交，计划书和工作区改动未提交，旧远端分支保留。
