# NoFigura P1/P2 实施记录

## Alpha.11 公共草稿查询修复（2026-11-12）

- 新增仅用于客户端呈现/输入的 VisibleAvatar.forEntity/localPlayer：显式 GUI 预览（含原版）优先，然后本地可见配装草稿，最后原有 AvatarManager 查询。空基础草稿不回退到正式 Avatar；panic 保持禁用，其他实体不套用本地草稿。
- 接入：CameraMixin 的 FOV/旋转/位置，GameRendererMixin 的 roll/矩阵/后处理/晃动，键盘、鼠标、Lua KeyMapping 与解锁光标，火焰、方块描边；第一人称物品复用同一查询。
- 不修改 AvatarManager、UserData、装备发布、网络同步、权限保存及判定规则。HUD 动作轮盘继续用正式配装，编辑器轮盘保留独立预览；鼠标轮盘派发不依赖可见草稿有 Lua，避免原版草稿使正式轮盘失去入口。
- 相机 RED：实际 CameraMixin 回调中 80 度输入仍为 80，期望草稿倍率得到 40；`build/visible-camera-red.log`。最小实现后 GREEN，`build/visible-camera-green.log`。
- 输入 RED：实际 KeyboardHandlerMixin.charTyped 后草稿事件计数为 0；`build/visible-input-red.log`。迁移接收者后 GREEN，`build/visible-input-green.log`。
- 覆盖层 RED：实际 ScreenEffectRendererMixin 没按草稿 renderFire=false 取消；`build/visible-effects-red.log`。补齐 CPU 夹具的相机实体后重新确认旧代码失败，修复 GREEN 为 `build/visible-effects-green.log`；无相机的初期夹具不算功能证据。
- 最终覆盖实际生产回调：FOV、旋转/位移/轴映射、字符接收、鼠标滚轮取消、Lua 快捷键覆盖/释放、火焰，以及原版 GUI 优先、原版世界草稿、panic、正式 Avatar 查询身份不变。测试原生回调/对象，不宣称 OS 输入或画面验收。
- 最终任务：libraryPreviewRuntimeSmokeTest、cameraFovSmokeTest、firstPersonItemRenderSmokeTest、firstPersonArmPoseSmokeTest、ysmDeferredPoseSmokeTest、outfitApplyClientSmokeTest、cloudAdapterSmokeTest、cloudProtocolConfigSmokeTest、Fabric/NeoForge build（-Prelease）。全部 PASS，退出码 0，BUILD SUCCESSFUL in 1m 27s，`build/visible-routing-final.log`。
- 两平台发行包 CRC 和 VisibleAvatar 类检查 PASS；`build/visible-routing-artifacts.log`。版本保持 alpha.11，以下哈希覆盖此前交付包。源码对应本记录所在修复提交。

| 平台 | 字节数 | SHA256 |
| --- | --- | --- |
| Fabric | 19097608 | `315c3409202b1255ac806ac57a5420a43133f7643354e909a137836dc81e24f9` |
| NeoForge | 18711308 | `139ee28bf73a1dba71da3fb1b3592b07b70deeb42bf76681c16600b10cae3b20` |

实机相机/输入/光影与双客户端仍 MANUAL/PENDING。实现和自动交付无剩余工作；提交推送核对后，按用户追加要求只读分析其指定芙宁娜 .ysm 的第一人称武器替换链，不新增 YSM 功能或修改样本。额度周窗口已用 26%、剩余 74%，未触发交接。

## Alpha.11 修复：本地草稿第一人称物品（2026-11-12）

- 用户报告 `youling.zip` 手持钻石剑仍显示原版。已安装 NeoForge JAR SHA256 与最初 Alpha.11 交付完全一致，排除装错旧包。
- 原始 ZIP 正式编译/加载后，作者 weapons.lua 返回 model1.Item，直接事件和实际顶点消费输出 6584 个顶点；包内两张纹理查找警告独立存在，但没有阻止本次 Item 几何输出。没有修改或提交作者 ZIP。
- 根因：用户日志显示本地配装草稿正在预览 youling；手臂已有草稿选择，新增 ITEM_RENDER 入口却只用 AvatarManager.getAvatarForPlayer 的正式装备 Avatar。故可见模型与物品脚本不是同一个 Avatar，未命中替换时继续原版物品。
- RED：在现有真实草稿夹具中调用生产 ItemInHandRendererMixin 包装方法，有几何的 Item 返回仍不能替换；断言 `First-person item must use the visible local draft, not the resident Avatar`。日志 `build/firstperson-draft-red.log`。注册组件未初始化和空几何夹具错误不作为功能 RED。
- 最小修复：ItemInHandRendererMixin 在物品作用域和手部 RENDER 事件入口共用可见 Avatar 选择，优先 LocalOutfitPreview.state(player).base；没有本地草稿才用正式 Avatar。草稿为原版基础时返回 null，保持不被旧模型覆盖。没有改全局 Feature 消费顺序、协议、模型文件或纹理解析。
- GREEN：`libraryPreviewRuntimeSmokeTest`、`firstPersonAvatarSmokeTest`（`-PfirstPersonAvatar=<用户 ZIP>`）、`firstPersonItemRenderSmokeTest`、`firstPersonArmPoseSmokeTest`、`ysmDeferredPoseSmokeTest`、Fabric/NeoForge build 及 NeoForge 分发校验 PASS；退出码 0，BUILD SUCCESSFUL in 1m 16s，日志 `build/firstperson-draft-green.log`。
- `firstPersonAvatarSmokeTest` 是本地显式输入回放，默认发行构建不依赖作者模型；真实 ZIP 留在原目录。固定可发布回归位于 OutfitHandRenderingSmokeTest 的生产 Mixin 入口。
- 实机：修复后的 NeoForge 游戏画面 `MANUAL/PENDING`。本次没有启动或操作游戏，没有替换安装目录 JAR。此前手动消费队列的测试未覆盖草稿选择差异，本次已补足这一生产入口。
- 以下哈希替代下方 M6 初版产物哈希；CRC 和包含修复的 Mixin 字节码验证 PASS，日志 `build/firstperson-draft-artifacts.log`。对应源码为包含本记录的修复提交。

| 平台 | 字节数 | SHA256 |
| --- | --- | --- |
| Fabric | 19096877 | `183f2411882f76660b05ef6daea2d8eb198841f64027f4100c232521386b8d41` |
| NeoForge | 18710606 | `764c445d6d1ce01f7f0d5373ded8571a7d5ace98b7a3cc7127f36d1a80b7cc62` |

产物仍为两平台 `build/libs/omni-morph-1.0.0-alpha.11+26.1.2-<loader>-mc.jar`。周额度已用 26%、剩余 74%，未触发交接；当前修复的提交/推送以本记录所在提交和交付时远端核对为准。

用户已确定本轮发布版本 `1.0.0-alpha.11`；M0 保留 Alpha.10 基线证据，M1 起构建采用 Alpha.11。

## M0 基线（2026-11-12）

- 分支 `dev` / `origin/dev`，起点 `8faa4b5954be798d9a0979e14537b5a583d1d9b8`；既有未跟踪资料保留。
- Java：Gradle 使用 `JAVA_HOME=D:/Program Files/Java/25`；终端 PATH 的 java 为 17，不能据此判断 Gradle 使用错误 JDK。
- 首次沙箱执行因用户 Gradle 缓存锁写入受限失败，获准后正常前台运行；未创建第二套缓存或下载数据库。
- `firstPersonArmPoseSmokeTest`、`ysmDeferredPoseSmokeTest` 已 PASS。
- `ysmMaterialInputSmokeTest` 在未改生产代码的基线失败：测试将本地 `refs/avatars` 固定断言为 40 模型、5 加密；此前逐模型材质配对断言均通过，最终数量断言失败。此项是外部语料集固定数量约束，不据此修改产品；后续使用固定构造材质与原生材质对象验证。原始日志 `build/m0-initial.log`。
- 样本：`common/src/test/resources/nofigura/` 提供可解析的 Figura Item 节点、事件/FOV 脚本与嵌套 require。实际可见几何、PBR 通道组合和错误动画在各阶段测试中构造，避免私有模型进入仓库。
- F1 契约：复用 ITEM_RENDER；只替换有效 Item 部件，无替换保留原版，左右手隔离，递归/退出清理。
- F2 契约：现有 FOV 倍率 API，本地有效 Avatar 消费，nil 恢复原版实时值。
- F3 契约：Figura `_n/_s` 按纹理组配对，保留字节编码；无 Iris 普通绘制不变，YSM PBR 原有生命周期保留。
- F4 契约：增加定位与适用版本迁移提示，不改动画表达式结果或 Molang 分流。
- F5 契约：正式模型脚本索引中的模块名是虚拟名；必须实际 require 复现再实施，不修改真实 IO 边界。
- 当前未启动 Minecraft，目标 shader pack 未选定；所有真实画面与双客户端项目为 `MANUAL/PENDING`，不据此阻止自主实施。
- 未引入新的界面设计、协议、模型库权限规则或 GPU 几何加速。
- 后续基线 PASS：`ysmMaterialNativeSmokeTest`、`modelCacheCodecSmokeTest`、Fabric/NeoForge build 及 NeoForge 分发校验；`BUILD SUCCESSFUL`，退出码 0，日志 `build/m0-green.log`。

## RED / GREEN 约定

用户已要求 RED/GREEN；测试边界采用已批准计划中的正式入口与消费者，不为测试新增另一套业务路径。每次一条行为用例先 RED，再最小实现 GREEN，最后受影响回归。RED 必须断言目标功能失败；编译拼写错误、环境权限与外部语料计数失败不算功能 RED。每阶段保存命令、失败断言、修复后结果与日志路径；CPU/录制型测试不替代真实画面。

M0 检查点：`9d5b3bcf` 已推送，远端 SHA 已确认。

## M1 第一人称自定义物品

- RED：`:common:firstPersonItemRenderSmokeTest` 实际失败于 `right-hand ITEM_RENDER replaces a valid Item part`，`build/m1-red.log`。初期夹具初始化失败不作为 RED。
- GREEN：同任务经正式 Figura 编译/加载、真实 Lua ITEM_RENDER、NodeCollectorExtension 提交闭包及 Avatar.renderItem 发出四个顶点。补验左右手、nil/非 Item 返回、嵌套事件拒绝、异常作用域恢复。
- 实施：`FirstPersonItemRender` 管理手部绘制调用域及递归状态；既有 ItemInHandRenderer 以 WrapMethod 确保 finally 清理，ItemStackRenderState.submit 按显示模式路由。保留原 YSM/Outfit/头颅代码与全局 Feature 消费顺序。
- 用 Minecraft 26.1.2 实际字节码核对：GameRenderer.renderItemInHand 调用 renderHandsWithItems，renderLevel 后续调用 FeatureRenderDispatcher.renderAllFeatures；因此沿用已有队列消费，不照搬参考项目改变全局顺序。该静态路径证据与录制顶点不等于实机画面。
- `firstPersonArmPoseSmokeTest`、`ysmDeferredPoseSmokeTest` 和 Alpha.11 Fabric/NeoForge 构建及分发校验 PASS，退出码 0；`build/m1-final.log`。测试没有重置生产 UI、权限或资源规则。
- 游戏左右手/双持、地图/望远镜/头颅、ItemTask、混合装饰与粒子拖尾、两平台实际 Mixin 加载：`MANUAL/PENDING`。
- M1 检查点 `ea755aa5` 已推送并确认远端。

## M2 Lua FOV 倍率

- RED：`:common:cameraFovSmokeTest`，真实 Lua `renderer:setFOV(0.5)` 后，80 输入仍为 80，断言期望 40；日志 `build/m2-red.log`。
- GREEN：接入 `Camera.calculateFov` RETURN 后，同用例输出 40；实时原版输入 100 得到 50，nil 对 96 恢复 96，远端/预览和无权限不覆盖。无状态消费者避免逐 tick 累积。
- 与 NoFigura tickFov 写入平滑状态相比，当前选择在原版世界视野计算后相乘，明确保留奔跑/死亡/流体等原有计算；HUD 独立视野未改。没有改变 API 接受值范围或 roll/矩阵逻辑。
- `cameraFovSmokeTest`、M1 用例、手臂姿态及 Alpha.11 双加载器构建 PASS，退出码 0；日志 `build/m2-green.log`。
- 真实相机切换、奔跑/使用动作、M1 组合画面及双平台运行 Mixin：`MANUAL/PENDING`。
- M2 检查点 `f8f7562d` 已推送并确认远端。

## M3 Figura PBR

- RED：`:common:figuraPbrSmokeTest` 实际失败于 Figura 动态纹理没有 Iris PBR loader；`build/m3-red.log`。
- GREEN：正式 Blockbench 内嵌 PNG → NBT → FiguraTextureSet → 基色持有明确辅助图关联 → 真实 PBRTextureLoaderRegistry/consumer。无/单/双通道、两纹理组、Lua 对应图像更新、Iris holder close/reload 与不同 Avatar 隔离通过。
- `BorrowedPbrTexture` 仅委托源纹理的 GPU texture/view/sampler，不复制像素、不另分配资源；Iris close 不销毁作者纹理，实际退役由原 FiguraTexture 管理。现有 YSM loader 保持独立，按 Iris 精确类注册不会被覆盖。
- `figuraWithoutIrisSmokeTest` 在明确排除 Iris jar 的独立 JVM 中实际加载 Figura 并普通上传材质，PASS；`ysmMaterialNativeSmokeTest` 原生材质/换肤/回收等回归 PASS。
- Alpha.11 Fabric/NeoForge build、NeoForge 分发校验 PASS，退出码 0；日志 `build/m3-final.log`。
- 实施与自动交接验证完成；shader 实际法线/高光画面、光影切换、GUI/第一人称及双平台实机均 `MANUAL/PENDING`。没有指定或下载 shader pack，没有 GPU 画面 PASS 声明。
- M3 检查点 `33dae03f` 已推送并确认远端。

## M4 动画作者诊断

- RED：`:common:keyframeDiagnosticSmokeTest` 经正式模型 reader → AnimationPlayer → Avatar.errorText，原错误只有动画名和时间，缺少部件/通道/轴；日志 `build/m4-red.log`。
- GREEN：错误包含部件“腕骨”、1.25 秒和 ROTATION X；v5 语法编译失败附迁移提示，旧格式和未知版本不附提示。合法 Lua 表达式、语句块及 Molang 结果保持一致；继承读取、copy/newPart 版本传递 PASS。
- 最小实施：Blockbench v5 编译输出 `_v`，reader 向子节点传递；Keyframe 保留旧构造接口，沿原错误出口增加定位和中英文迁移提示，不重写作者表达式。
- `keyframeDiagnosticSmokeTest`、`ysmPrivateDiagnosticsSmokeTest`、`ysmControllerDiagnosticSmokeTest`、Fabric/NeoForge build 及分发校验 PASS，退出码 0；日志 `build/m4-final.log`。
- 中英文错误在实际客户端的显示为 `MANUAL/PENDING`；未启动游戏。M4 自动验收完成，提交检查点后进入 M5。
- M4 检查点 `4f485710` 已推送并确认远端。

## M5 Lua 虚拟模块路径

- 适用证据/RED：真实 ZIP 内 `avatar/feature:variant.lua` 经 LocalAvatarLoader 正式编译后保留名称，但 Avatar.createLuaRuntime 调用 Windows Path.of 抛 InvalidPathException，Lua 运行时创建失败。日志 `build/m5-red.log`；早期夹具编译错误和 ZIP 根目录命名差异不作为 RED。
- GREEN：`luaModulePathSmokeTest` 同一 ZIP 模块可实际 require；补验嵌套相对导入、`.`/`..`、斜杠/点名、模块缓存、空/根目录枚举、单层/递归枚举、缺失模块、大小写和两份 Avatar 隔离。
- 实施：小型 ScriptPath 只处理 Avatar 脚本索引，不实现整个 java.nio.Path，不接触磁盘。既有 PathUtils 的 Path 接口保留；脚本安全名和 require/listFiles 使用虚拟解析，真实 IO、导出、ZIP、缓存规则不变。
- M1/M2 回归、Fabric/NeoForge build 及发行包校验 PASS，退出码 0；日志 `build/m5-final.log`。实机模块脚本显示/双平台启动 `MANUAL/PENDING`。
- 范围外观察：ZIP 文件系统根目录直接交给编译器时，脚本名有前导点；样本改用正式 ZIP 内模型目录。该旧命名行为未在本阶段修改，留作后续独立调查。
- M5 检查点 `30198cb8` 已推送并确认远端。

## M6 组合回归与 Alpha.11 交付

- 同一正式加载的 Figura Avatar：ITEM_RENDER 提交/原生四顶点 + Lua FOV 倍率 + 作者 PBR 纹理组，消费后原始法线/高光对象仍有效。`figuraPbrSmokeTest` 复用第一人称用例覆盖，未新增生产测试专用入口。
- 交付补验：第一人称四次实际事件调用断言、第三人称/递归不增加次数、正式加载的 Avatar 退役后物品不替换且 FOV 恢复；`build/m6-retirement.log`。ItemTask 的实际 RenderUtils → GameRenderer/ItemModelResolver → submit 链需要游戏渲染环境，静态核对未改该链，完整自动/实机执行仍 `MANUAL/PENDING`，不把嵌套 helper 断言冒充 ItemTask 全链运行。
- 最终命令：`gradlew.bat :common:figuraPbrSmokeTest :common:firstPersonItemRenderSmokeTest :common:figuraWithoutIrisSmokeTest :common:keyframeDiagnosticSmokeTest :common:luaModulePathSmokeTest :common:modelCacheCodecSmokeTest :common:libraryPreviewCacheSmokeTest :common:libraryTransferSmokeTest :common:libraryEquipmentSmokeTest :common:outfitApplyClientSmokeTest :common:cloudAdapterSmokeTest :common:cloudProtocolConfigSmokeTest :common:navigationTreeSmokeTest :common:ysmMaterialNativeSmokeTest :fabric:build :neoforge:build -Prelease --no-daemon --console=plain`。
- 全部 PASS，`BUILD SUCCESSFUL in 1m 59s`，退出码 0；前台 PowerShell/Tee 原始日志 `build/m6-final.log`。负向装备样本的“YSM main model is empty”是拒绝错误输入用例，正式装备测试最终 PASS。未忽略测试失败。
- 构建所含 `libraryPreviewRuntimeSmokeTest` 同时通过原生 Figura 装饰、左右手、保留 Figura 基础姿态、YSM 基础/Figura/YSM 混合装饰、模型库装备与预览隔离。YSM 原生材质回归同时覆盖粒子、Ribbon、动态光源及控制器绑定；这些为 CPU/录制路径证据。
- 四语言 CHANGELOG 和 README 版本/产物路径更新，作者示例及通道/导入约定见计划书第 14 节；保留历史版本记录。
- 最终 JAR 全部条目 CRC、Alpha.11 元数据、新增生产类及中英诊断资源检查 PASS，日志 `build/m6-artifacts.log`。对应生产源码提交 `30198cb874ee0a2279b259328d60f23e7b39fa5a`；M6 只修改测试及文档，不改变生产树。

| 平台 | 最终产物 | 字节数 | SHA256 |
| --- | --- | --- | --- |
| Fabric | `fabric/build/libs/omni-morph-1.0.0-alpha.11+26.1.2-fabric-mc.jar` | 19096659 | `bfb00b85b865a1e41e108dbf0efe5ba81409ae028f6bd17337b420a8f2606f31` |
| NeoForge | `neoforge/build/libs/omni-morph-1.0.0-alpha.11+26.1.2-neoforge-mc.jar` | 18710403 | `c648b376d396e57790211fb59a62440e7bf760d9a1d95cd9839b18ec66bad15b` |

### 交付状态与证据边界

- F1～F5：均已实施并有功能 RED→GREEN；F5 为正式复现后的实施，未使用 NOT_APPLICABLE/DEFERRED 代替功能。
- 真实运行验收：`MANUAL/PENDING`。包括游戏/Mixin 启动、PBR shader 画面和切换、左右手特殊物品/ItemTask、混合配装特效实景、视野动作组合、中英文错误显示、双客户端与两加载器实机。未启动游戏，也未声明这些画面 PASS。
- 自动验证/文档/产物记录完成，M6 检查点 `0228f0b4c158709498ecfe41fa6ea28760d4f77d` 已推送，ls-remote 确认同值。补验事件次数/退役与组合用例 PASS，退出码 0，日志 `build/m6-retirement.log`。M0 外部语料数量基线失败与 ZIP 根目录旧命名行为已单独记录，无待修的本轮自动测试失败。
- 最近可靠额度：周窗口已用 25%、剩余 75%，未触发 Luna 额度交接。
