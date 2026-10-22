# 装饰系统实施记录

依据：`ACCESSORY_SYSTEM_IMPLEMENTATION_PLAN.md`。仅执行阶段 1～6；人工验收统一记录 `MANUAL/PENDING`，不作为阻塞。自动检查失败不能跳过，也不能据此进入下一阶段。

## 基线

- 客户端：`main@90b1ca2`，当前版本 `1.0.0-alpha.3`。
- 独立后端：`omnm-cloud`，`master@a6b130b`。
- 初始未跟踪：计划本身、客户端 `ysm_default/`；后端 `build.log`、`spm-cloud/`。保留已有内容，不作清理。
- 冻结 Protobuf v1 不修改；仅阶段六调整版本。

## 阶段状态

| 阶段 | 状态 | 自动验证 | 人工验收 | 提交/推送 |
| --- | --- | --- | --- | --- |
| 1 资源/配置契约和后端数据 | 含用户补充，自动验收 PASS | PASS | MANUAL/PENDING：旧业务真实界面回归 | 客户端 81dcbe1 / 后端 f404a5b 已确认推送 |
| 2 解析/原生渲染/Lua 实例 | 自动验收 PASS；人工项按用户要求跳过 | PASS | MANUAL/PENDING：真实游戏/GPU/性能矩阵 | main@7d26956，远端 SHA 已核对；后端无改动 |
| 3 挂载/骨骼/拖尾/粒子 | 自动验收 PASS；含 CustomParticle 和 Ribbon 增量要求 | PASS | MANUAL/PENDING：真实游戏/GUI/GPU/光照/性能，按用户要求跳过 | main/master 阶段三检查点，见本阶段验收记录 |
| 4 衣柜/投稿/预设/创建 | NOT STARTED | PENDING | MANUAL/PENDING | PENDING |
| 5 原子应用/权限/同步 | NOT STARTED | PENDING | MANUAL/PENDING：双真实客户端 | PENDING |
| 6 整体验收/性能/交付 | NOT STARTED | PENDING | MANUAL/PENDING：第 10 节真实渲染/性能矩阵 | PENDING |

人工项只标记未验证，不冒充 PASS，也不宣称 alpha.4 全部完成。

## 阶段一验证（2026-10-21，Asia/Seoul）

- 锁定独立 JSON v1、资源用途/精确引用、稳定骨骼/实例、编号水位、变换与预算；契约见 `ACCESSORY_SYSTEM_CONTRACT.md`。
- 锁定 ysm-default-1 的 42 个模板文件及 28 个基础动作，源文件未变，未在阶段一提交用户模板正文。
- 客户端 `usejdk 25` 后 `:fabric:compileJava` PASS。
- `:common:outfitContractSmokeTest` PASS：双副本往返、稳定身份、未知类型、无效数值/重复 ID、元数据缺失/冲突、默认锚点，以及真实生产 Transform.modelMatrix 的非对称轴标记矩阵（仅局部契约）。
- `:common:libraryClientSmokeTest`、`libraryMetadataSmokeTest`、`libraryUploadDraftSmokeTest`、`libraryUploadSmokeTest`、`networkProtocolSmokeTest` 全部 PASS。覆盖原投稿/权限/取消/旧协议，上传用途与格式分离。
- 后端 `cargo test`：121 PASS / 0 FAIL / 1 原有 ignored 压力测试；含本人预设真实 HTTP、装饰投稿、迁移旧库/备份/重复启动/失败回滚、授权/成员/候选保留及单形象回归。
- 两仓库 `git diff --check` PASS；冻结 v1 文件与版本号未改。共享装扮 fixture 两仓库 SHA256 一致。
- 自动测试不证明 GUI、原生 Figura/YSM 坐标适配或跨端动态渲染；这些按所属阶段记 MANUAL/PENDING。
- 独立后端阶段一提交 `1f182a0` 已成功推送至 `origin/codex/accessory-system`。

## 阶段一用户补充

- 将装饰上限集中到后端配置文件 `[outfit.limits]`，协商后在客户端/服务端使用；默认值及 v1 安全边界保持明确。
- 粒子新增独立动态光源开关、亮度/半径设置与预算；阶段一锁定契约，按顺序在阶段三实现实际照明及设置项，不能以自发光替代。
- 阶段二尚未开始；本补充编译测试、提交并确认推送后才继续。
- 补充验证：客户端 `:fabric:compileJava :common:outfitContractSmokeTest :common:libraryClientSmokeTest` PASS；后端全量 124 PASS / 0 FAIL / 1 原有 ignored，动态光源边界追加用例 `cargo test outfit_limits` 2 PASS；两仓库 diff 检查 PASS。动态光源实际渲染尚未实现，不作为阶段一 GUI PASS。
- 后端补充提交 `f404a5b` 已成功推送至 `origin/codex/accessory-system`。
- 客户端补充提交 `81dcbe1` 已成功推送并核对远端 SHA，阶段一检查点完成；开始阶段二。

## 阶段二进行中（2026-10-21，Asia/Seoul）

- 已接通装饰用途的预览/正式 `.oms` 加密缓存，使用 `cache/cosmetic`；本地创作资源仍位于独立 `cosmetics`。
- 本地目录、ZIP、原加密 YSM 包装复用原生准备器，显式选择 `cosmetic.json`；扫描使用目录外的稳定 UUID 清单，源文件内容不改写。
- 稳定骨架索引保留空节点、同名兄弟序号及完整路径，缓存只保存不可变结构；不同实例绑定自己的运行时部件，结构解析计数覆盖重复命中。
- 已增加实例上下文和独立执行 UUID，复用原生 Avatar/Lua/Molang/controls；`cosmetic_controls` 的持久值归属账号/装扮/实例，草稿不写旧偏好文件，观察端恢复不回调、不持久化。
- 装饰 Lua 当前开放实例事件、模型/动画及数学/JSON/controls；内部渲染所需 API 对象不等于 Lua 可见全局。host、网络、世界、文件、基础模型等全局不可见；穿戴者查询使用只读包装。粒子、声音及动作聚合的受限入口尚待对应接入，不能据此宣称装饰脚本能力已完整。
- 仅装饰上下文统一原有动画 API 的变量前缀、嵌套路径和有限数值规则；无效批量更新先拒绝再写入。原单 Avatar 变量语义保持原样。
- 自动验证 PASS：`:fabric:compileJava`、`:common:cosmeticSourceSmokeTest`、`ysmControlRuntimeSmokeTest`、`ysmActionRuntimeSmokeTest`、`libraryPreviewRuntimeSmokeTest`；最后的控制绑定调整另跑前两项相关 smoke tests PASS。包含副本变量/回调隔离、指令超限隔离、恢复不触发回调、配置为零时拒绝注册/保存、原加密字节不变。
- 此前缓存相关 `libraryPreviewCacheSmokeTest`、`libraryTransferSmokeTest`、`libraryThumbnailSmokeTest`、`libraryPackagePreparationSmokeTest` PASS。测试使用现有 CPU/原生调用记录工具，不证明真实 Minecraft 画面。
- **剩余必做：** 三种适配器在真实组合路径的矩阵桥/第一人称接入、组合预览/帧快照及释放、内置 Lua 编排、穿戴者整套预算、YSM 默认动作解析/映射/缓存，以及相应自动验收。当前工厂和局部索引尚未接成可用的组合预览。
- 阶段二保持 `IN PROGRESS`，尚未达到提交/推送检查点，不进入阶段三。真实 GUI、动态组合和性能基线为 `MANUAL/PENDING`。

### 原生姿态桥局部进展

- 新增只读 `SkeletonPose`，按稳定引用/实例解析矩阵，转换到实体根模型单位，帧间矩阵不共享可变对象。
- 原版适配器直接取真实 `PlayerModel` 的 Root/Body/Head/双臂/双腿；不要求基础 Avatar。宽/细手臂使用同一稳定路径。
- YSM 适配器复用原生骨骼变换及提交时 `YsmRenderPose`，分别计算枢轴/父几何继承；不覆盖原有世界空间 Lua 查询矩阵。结构缓存用于不同原生运行时副本。
- Figura 适配器在原生绘制中捕获当前矩阵：保留 preRender 到 postRender 的真实顺序，隐藏/过滤掉的空节点从本帧父矩阵求值，不读取旧 `savedPartToWorldMat`。捕获容量受协商节点/深度预算限制，失败后清理捕获状态。
- 数值 fixture 明确使用原生 YSM 解析器翻转作者 X 轴后的坐标；Figura 则包含其原生根变换。测试用非对称坐标、90 度旋转和非均匀缩放的独立数值系数比较，另验证隐藏父级、后回调重置/隐藏和旧帧隔离。
- 自动验证 PASS：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest :common:ysmBoneMatrixSmokeTest :common:ysmDeferredPoseSmokeTest`，最后一轮 `BUILD SUCCESSFUL`。`git diff --check` PASS。
- 上述只证明局部原生调用和矩阵；实际 GUI/世界外层矩阵、第一人称挂载及普通/加速组合绘制尚待接通和验证，不能据此判定阶段二完成。

### 内置控制器与组合代际局部进展

- 新增内置 Lua 控制器及 `OutfitComposition`：静态资源不要求作者脚本，Lua 选择明确骨骼/唯一标准锚点并生成绘制命令；私有原生桥不暴露给作者脚本。作者初始化超时只停用自身，禁用/未解析实例不运行作者脚本。
- 组合快照接入既有预览 binding 的帧引用释放：退役代际等待最后帧，绘制回调中关闭帧也等待回调返回再清理。原生 YSM 姿态及输出矩阵按帧复制。
- `:common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest` PASS，另跑 `:fabric:compileJava` PASS。上述测试覆盖控制器命令与原生实例生命周期，实际组合绘制和整套权限预算仍未完成，阶段二保持进行中。

### 用户补充：后台/权限页的其他玩家预览

- 后台衣柜管理的当前装备预览及权限页使用同一条件路由：在线玩家列表存在目标 UUID，且当前世界存在对应有效实体时直接借用原生 Figura 预览；任一条件不满足则准备隔离实例。主衣柜保持现有行为，其他管理资源/证据预览保持其原有选择语义。
- 实体到达或离线时切换预览代际、取消旧请求，不独立 tick 在线玩家，不销毁借用实体/正式 Avatar。权限页隔离副本在后台模型 NBT 更新后重新准备，展开预览沿用同一会话。
- `:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest` PASS；追加权限页隔离加载/刷新/释放用例后重跑 smoke test PASS。覆盖在线+实体、在线缺实体、有实体但离线、过期准备取消和隔离副本释放。测试使用既有 CPU/原生调用记录工具，真实游戏内切换/展开画面为 `MANUAL/PENDING`。

### 组合原生提交与异步准备局部进展

- 新增 `OutfitNativeRenderer`，把 Lua 输出的实体根模型单位矩阵转换为原生 Figura/YSM 输入；Figura 抵消原生默认根基变换后继续使用原有几何/材质/加速提交，YSM 保持其原生模型缩放与提交姿态。实例绘制结束恢复 renderMode。
- 既有 LivingEntityRenderer 的 Figura/YSM 回调及独立管理预览回调已接入组合帧。Figura 在实际基础绘制中捕获锚点，YSM 使用提交时姿态；绘制消费同一帧保留的组合。没有组合时继续原逻辑。
- 新增工作线程 `OutfitPreparation` 和预览会话 `prepareComposition`：验证装饰用途/格式，逐实例加载独立 Avatar，全部成功后在客户端线程替换；取消/切基础/快速切换拒绝过时代际并释放自有资源。失败保留当前组合；共享骨架缓存的绑定/清理访问已同步，避免准备线程与延迟绘制同时写入定义表。
- 渲染准备在当前 renderMode 下按 Lua render → clear/apply animations → YSM/Molang 姿态捕获的顺序运行。该顺序接入了组合调用，但完整作者回调/第一人称时序仍需后续验收。
- 自动验证 PASS：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest`，最终 `BUILD SUCCESSFUL`。新增真实原生 Figura 顶点输出断言：两个副本各提交一个四边形，独立数字预期覆盖非对称坐标、90 度旋转和非均匀缩放；另覆盖既有 GUI 绘制进入组合帧、异步错误保留旧代际、快速切换和原帧持有。
- 验证边界：以上是 CPU/原生提交证据，不证明真实像素或 GPU 输出。YSM 组合提交已接线但实际组合几何尚无新增顶点断言；无基础 Avatar 的原版组合、第一人称、Figura 跨 pass 可变姿态快照、整套预算和 YSM 默认动作回退仍未完成。阶段二继续 `IN PROGRESS`，不提交完成检查点，不进入阶段三。

### 原版基础组合接入

- 预览会话新增显式 `selectVanilla`：使用真实玩家/隔离玩家的原生皮肤，binding 不创建空 Avatar/Lua；组合准备不再要求基础 Avatar 非空，并拒绝草稿基础类型与当前准备结果不一致。
- binding 明确区分“未绑定”与“显式原版”，实体/玩家 UUID/延迟帧查询在原版预览范围内不回落正式装备。原范围之外的装备查询保持原样，关闭/帧末清理不销毁借用玩家或正式 Avatar。
- 原版 PlayerModel 的真实绘制前回调接入组合原生绘制，从该帧模型姿态和外层矩阵取得标准骨骼。原版不可变定义复用会话缓存；每帧重新绑定实际 ModelPart，宽/细手臂仍共享稳定路径。
- 自动验证 PASS：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest`，最终 `BUILD SUCCESSFUL`。覆盖无占位 Avatar、范围内屏蔽装备/范围外恢复、无基础 Avatar 时异步准备装饰、基础类型冲突拒绝、延迟帧清理；两个 Figura 副本经 `drawVanilla` 输出独立数值预期顶点，下一帧不重新解析骨架。
- 真实原版 GUI/动态姿态/展开画面仍为 `MANUAL/PENDING`；测试验证 native helper/生命周期，不冒充完整游戏 Mixin 与 GPU 验收。阶段二仍有第一人称、跨 pass 姿态快照、整套预算、YSM 默认动作回退及组合矩阵自动覆盖需要完成。

### YSM 默认动作回退的当前核对

- 核对了锁定的 28 个 main 基础动作及独立 arm 动作资源；`fp.arm.animation.json` 只有 parallel0～7 空片段，不能自动注入为手部基础动作。模板源文件与 hash 未修改。
- main 白名单片段中的变量引用为各片段 `molang` 通道内的 `v.bv` 赋值；读取 `v.bv` 的胸部物理位于未选的 parallel 动画，不能为其整表导入物理/眨眼。arm 的剑/重锤片段另有自身 timeline 初始化与变量依赖，后续解析须保留，不能只抽取 bones。
- 现有动画名解析区分普通命名空间与手部条件分隔符，默认片段必须沿用该行为；原 parser 会吞掉解析异常，回退不能将损坏的作者动作误当成缺失。默认动作尚未注入原生有效表，也未改变任何同步索引。

### YSM 默认动作回退接入与原生自动验证

- 已增加版本锁定模板解析器，校验 main/arm 原文件 hash，main 仅导入锁定的 28 个基础动作；arm 保留自身 timeline/变量依赖，不导入 parallel/物理/眨眼。模板变量使用私有前缀，缓存不可变映射/定义，原生 clip 逐实例编译，资源重载清理缓存。
- 完整 YSM Avatar 装配接入默认动作；装饰包装及子实体不自动获得人形动作。作者声明（包括空动作、损坏动作）、明确禁用和作者控制器保持优先；缺骨记录诊断，默认模板不进入作者动作表和冻结 v1 同步索引。第一人称使用独立 arm 几何映射；远端模板身份协商仍按阶段五实施。
- 本轮自动验证 PASS：`:fabric:compileJava :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:ysmSyncSmokeTest`，`BUILD SUCCESSFUL`，`git diff --check` PASS。新增实际原生 Avatar 装配及步行骨骼数值验证，覆盖空 idle 优先、单动作禁用、实际 controls 入口关闭状态机、损坏动作诊断、实例 clip 隔离、别名/缺骨和稳定缓存签名。
- 以上为 CPU/原生调用证据。真实预览/世界/第一人称与作者控制器对照为 `MANUAL/PENDING`；阶段二仍待第一人称组合接入、Figura 跨 pass 姿态快照、整套预算和组合矩阵覆盖，保持 `IN PROGRESS`，未到提交/推送检查点。

### 第一人称组合的原版 / Figura 局部接入

- 姿态快照增加 pass 范围筛选，保留同一稳定身体骨骼引用，仅允许当前手臂的挂点解析；左右手分别保留组合帧，不复用另一侧的绘制命令。
- 原版手部 native ModelPart 绘制前回调接入组合，继续使用真实 PlayerModel 和既有缓冲来源，无需空 Avatar；Figura 原手部提交回调接入 draw-time 姿态捕获和组合。装饰资源仍整体走自身原生 renderer，不把其内部骨骼当作基础手臂过滤。
- Figura 捕获同时保留实际遍历时的 ParentType，作者 postRender 改变类型不会重写刚绘制帧的左右手归属；隐藏空节点继续保留，不重新解析结构。
- 自动验证 PASS：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest`，`BUILD SUCCESSFUL`。新增 slim 原版左右手原生顶点数值断言，每侧仅一个装饰四边形，身体/另一侧不泄漏；另验证 Figura postRender 修改 ParentType 后当前帧归属、稳定骨架 hash 与缓存命中。
- 验证边界：尚未实际启动 Minecraft 检查第一人称 Mixin/feature 调度及像素，记 `MANUAL/PENDING`。YSM 手部独立几何到稳定身体引用的映射、完整手部回调/帧生命周期、Figura 跨 pass 可变姿态快照、整套预算和更多组合提交覆盖仍未完成；阶段二保持 `IN PROGRESS`，不进入下一阶段。

### YSM 第一人称挂点与帧末清理

- 增加 YSM 第一人称原生枢轴捕获，使用该模型实际 arm 几何及提交姿态，复用原生骨骼变换，不施加身体 width/height/model scale；无独立 arm 几何时沿用原生身体几何的手臂分支。
- 组合保留稳定身体骨骼引用，独立 arm 中同名身体骨骼使用实际手部枢轴；当前侧之外、身体专有或无对应关系的节点保持未解析，不套用身体的旧姿态。稀疏姿态不会把缺失节点暴露为单位矩阵。
- YSM 原手部 custom geometry 提交保持原行为，装饰经已有 Figura feature 通道使用各自原生材质。核对当前 Minecraft 字节码：手部提交后存在 FeatureRenderDispatcher.renderAllFeatures 调用；这只证明静态调度路径，实际游戏像素仍待验收。GameRenderer.render 增加 finally 清理，GUI 跳过或异常时也结束组合帧。
- 内置 Lua 区分“有效挂载”与“当前 pass 有矩阵可画”，避免手部 pass 停掉身体/另一侧仍有效实例的 tick；真正无效挂点仍不初始化作者脚本。
- 自动验证 PASS：`:fabric:compileJava :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:ysmDeferredPoseSmokeTest`；追加跨 pass 实例 tick 用例后重跑前三项 PASS，最终 `BUILD SUCCESSFUL`，`git diff --check` PASS。新增独立 body/arm 原生加载、非对称枢轴/旋转/非均匀缩放的数值断言、左右手范围、缺失节点、旧姿态隔离与共享结构计数。无实体夹具通过原生 WORLD_TICK 验证实例 tick，未冒充有玩家实体的 TICK 或真实客户端测试。
- 第一人称原生 Mixin/材质/像素与异常退出实际运行仍记 `MANUAL/PENDING`；Figura 跨 pass 可变姿态快照、整套穿戴预算、YSM 组合实际顶点及普通/加速组合覆盖仍待完成。阶段二保持 `IN PROGRESS`，未到提交/推送检查点。

### Figura 装饰命令的原生姿态快照

- 装饰准备完成后保存自己的原生部件姿态，包括作者/动画通道、矩阵、可见性、颜色/UV/材质选择、ParentType 与部件回调；仅在原生绘制范围内使用该快照，继续调用原 Figura renderer。没有复制渲染器或改写源资源。
- 普通当前姿态的原生 postRender 修改继续保留；若实例已进入较新姿态，旧帧绘制结束恢复借用前的新状态。异常也通过 finally 恢复；快照沿用原加速 owner/frame lease，组合帧退役时清理持有。
- 新增完整 render-pose 复制入口包含动画通道；原 `copyTo` 及其原版变换备份调用语义保持原样。装饰的 postRenderEvent 纳入同一快照范围，避免旧帧回写较新部件姿态。
- 自动验证 PASS：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest`；追加异常恢复用例后重跑 `:common:libraryPreviewRuntimeSmokeTest` PASS，最终 `BUILD SUCCESSFUL`，`git diff --check` PASS。原生顶点用例覆盖两实例、新帧→旧帧→新帧交错提交、作者位置与动画位置叠加、当前帧回调持久效果及异常后恢复；共享骨架解析计数保持不变。
- 此处证据针对装饰绘制命令，不证明基础 Figura 的整段延迟回调已拥有独立快照；基础回调的 renderEvent/原生绘制/postRenderEvent 范围仍需接入核对。真实游戏/GPU 保持 `MANUAL/PENDING`。整套预算及 YSM/普通/加速组合提交覆盖也仍待完成，阶段二保持 `IN PROGRESS`，未到提交/推送检查点。

### 基础 Figura 延迟回调快照接线（仍进行中）

- 组合帧新增基础 Figura 姿态准备与绘制范围，世界实体及第一人称的已有延迟回调在提交前保存部件姿态，再在整段回调范围内使用快照；未绑定组合的路径继续直接执行原回调。
- 基础回调作用域增加独立绘制标记，帧在回调中关闭时等到整个基础回调退出后再释放组合资源。
- 当前接线编译与既有回归 PASS：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest`，`BUILD SUCCESSFUL`，`git diff --check` PASS。独立管理 PIP 预览的同范围接入及基础/装饰交错顶点专用用例仍需补齐；本轮结果不证明这些尚未验证的行为。阶段二保持 `IN PROGRESS`。

### 基础 Figura 及管理预览快照验证

- 独立管理 PIP 在 `submitIndependentFigura` 保存组合基础姿态；绘制时把 PlayerModel 准备、基础 renderEvent、原生几何、挂点/装饰及 postRenderEvent 放在同一快照范围。无组合时仍直接执行原绘制回调。
- 自动验证 PASS：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest`；补管理提交路径与关闭回调用例后分别重跑相关 `libraryPreviewRuntimeSmokeTest` PASS，最终 `BUILD SUCCESSFUL`，`git diff --check` PASS。
- 新增基础/装饰真实原生四边形数值断言：新帧→旧帧→新帧均使用各自姿态与 Lua 前后回调，装饰顶点匹配基础挂点，较新回调结果不被旧帧覆盖，骨架解析计数不增加。此用例显式执行 Lua/native 回调范围，不冒充完整 Minecraft 实体渲染测试。
- 既有管理预览用例改为走实际 `submitIndependentFigura`→PIP draw：提交后修改基础位置，原生 preRender 中实际 Lua 查询仍读到提交时的位置，回调结束恢复后改值。另覆盖基础回调中关闭最后帧，装饰资源等回调结束再释放，借用基础 Avatar 保持有效。
- 上述补齐了此前注明的基础/独立管理预览快照自动验证缺口。真实窗口、GPU 和游戏 Mixin 路径仍为 `MANUAL/PENDING`；阶段二接下来完成整套穿戴预算及 YSM/普通/加速组合提交覆盖，保持 `IN PROGRESS`，尚未到提交/推送检查点。

### 阶段二：共享 Molang 帧预算（部分预算接线完成）

- Figura/YSM 装饰实例的 Molang 上下文共用组合帧额度；存在基础 Avatar 时复用其预算，保留基础模型已消耗的额度，原版基础使用组合自身预算。组合绘制及基础 Figura 延迟回调进入同一帧预算范围；重复绘制不重置额度。
- 自动验证 PASS：`:fabric:compileJava :common:cosmeticSourceSmokeTest :common:ysmMolangBudgetSmokeTest`，`BUILD SUCCESSFUL`。新增实际组合帧内跨 Figura/YSM 上下文消耗、同帧耗尽不重置及下一帧恢复断言。Lua 指令、复杂度及其他权限总预算仍待完成，阶段二保持 `IN PROGRESS`。

### 用户要求的分支归并与清理

- 客户端阶段一两个提交已快进合入并推送 `main`；后端对应两个提交已快进合入并推送其默认分支 `master`。远端提交号已核对一致。
- `AGENTS.md` 已加入在当前分支开发、提交、推送以及未经用户明确要求不得新建或切换分支的规则，单独提交 `ce9676c` 并推送到 `main`。
- 两个仓库此次创建的本地及远端 `codex/accessory-system` 分支均已在确认合入后删除。阶段二未提交改动已恢复，42 个用户默认动画模板文件 SHA-256 保持一致；Git 恢复产生的少量既有源码换行差异已确认无逻辑内容差异。
- 后续在客户端 `main`、后端 `master` 继续阶段二，未将未完成的阶段二作为完成阶段提交。

### 阶段二：共享 Lua 指令与粒子/声音速率预算

- 新增组合级 Lua 指令账本，沿用初始化、tick/worldTick、render/worldRender、动画各权限通道及逻辑 tick/渲染帧边界。作者实例共用额度；基础 Figura 延迟回调以临时作用域加入，导入基础已消耗计数，单个实例或基础回调重置自己的计数不能退款。初始化与失败/超时的指令均计费；正常未绑定组合的 Avatar 保持原执行路径。重入的同 VM 控件回调沿用外层 hook，避免重复计费。
- 组合粒子/声音新增共享速率桶，保留各实例的原有权限桶；有基础时共用基础原桶，原版基础仅每组合 tick 补充一次。LuaParticle.spawn、LuaSound.play、YSM 原生粒子检查和 AvatarAPI 的剩余量读取均走共享入口。没有给作者新增粒子/声音 API 权限。
- 自动验证 PASS：`:fabric:compileJava :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`；基础计数重置补充用例后，两项 smoke 重跑 PASS。新增实际 Lua 初始化总量、跨 VM 超时计费、单实例计数重置、健康实例下一帧恢复、基础已有消耗/回调消费及作用域退出断言。
- 特效接线及用例最终 PASS：`:common:cosmeticSourceSmokeTest :common:ysmSoundNativeSmokeTest :common:ysmParticleNativeSmokeTest`，`BUILD SUCCESSFUL`。新增真实 LuaSound/LuaParticle 额度拒绝入口、跨实例消费、单次补充、借用基础桶与预览禁声保留断言；声音/粒子的既有 CPU 原生引擎回归 PASS，不代表可听播放或游戏视觉验收。
- 中间一次失败源于测试基础使用默认禁声的隔离预览；用例已先验证禁声，再明确为测试基础配置声音额度并补充桶后测试共享消费，未修改生产预览权限。格式与 `git diff --check` PASS。
- 阶段二仍为 `IN PROGRESS`：渲染复杂度/纹理总预算及 YSM 实际组合顶点、普通/加速路径覆盖待补。尚未完成阶段二检查点，未提前进入阶段三；真实游戏/GPU/可听播放继续 `MANUAL/PENDING`。

### 阶段二：原生组合渲染复杂度与 YSM 顶点验证

- 现有穿戴者账本接入 `COMPLEXITY` 面数通道。Figura 在原生 commonRender 遍历前读取剩余额度，继续复用原逐面裁剪及普通/加速提交，结束时扣减实际遍历提交面数；同帧重绘、实例自身计数重置均不补额度。
- YSM 普通几何逐面扣减；加速整网格先申请面数，额度不足时沿用原生 quad 入口提交允许的前缀，保留模型轴向/缩放及其他实例状态。未绑定组合的原有 YSM 记录行为保留。YSM 世界基础与第一人称基础回调新增临时预算/生命周期作用域，借用基础不被永久绑定或销毁；触达额度时记入已有 noPermissions。
- 自动验证 PASS：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest` 中生产编译 PASS；新增 YSM 用例首次运行因测试没有纹理设备失败。随后复用已有 PublicationDevice 作为局部纹理分配记录器，不修改生产绘制或启动真实窗口；两项 smoke 重跑 `BUILD SUCCESSFUL`。
- 最终数值断言与回归 PASS：`:common:libraryPreviewRuntimeSmokeTest :common:ysmDeferredPoseSmokeTest :common:ysmAccelerationMeshesSmokeTest`，`BUILD SUCCESSFUL`。真实 native 顶点断言覆盖两个 Figura 实例共用一面额度、重复帧零增量，以及 YSM 基础六面 + Figura 一面 + YSM 部分一面共八面；普通及加速网格准备分支均执行，核对 Figura 挂载点位置及 YSM 原生轴向/两级缩放范围，重复帧不超额，骨架解析次数不增加。
- 加速准备测试使用独立 Java 数值后端进入实际加速网格分支，验证真实 Mesh/顶点准备与回退；不声称 JNI/GPU/像素已验证。真实客户端全部引擎组合、第一人称、GPU、性能继续 `MANUAL/PENDING`。
- 纹理约束核对：现有 `TEXTURE_SIZE` 是自定义纹理单边尺寸权限，`TextureAPI.TEXTURE_LIMIT` 是每 Avatar 128 张自定义纹理数量，原生资源纹理并未采用一个总像素权限。下一步汇总现有自定义纹理额度并核对加载/运行时接线，保留既有资源纹理行为，不自行发明总像素阈值。阶段二保持 `IN PROGRESS`，完成纹理及阶段审计后才执行提交/推送检查点。

### 阶段二最终审计与检查点（2026-10-21，Asia/Seoul）

本节为阶段二当前结论，前文进行中条目保留为过程记录。阶段二规定实现与自动验证已完成；未提前实现阶段三的完整挂点/拖尾/贴图粒子、阶段四编辑界面或阶段五对外应用/同步。

| 阶段二要求 | 当前实现及权威验证 |
| --- | --- |
| 本地 cosmetics 与 `.oms` | LocalCosmeticSource/Inventory、现有准备器/缓存用途接线；目录/ZIP/原加密 YSM、稳定 UUID/监视/取消、来源与用途隔离、原字节不改写用例 PASS |
| 原版/Figura/YSM 姿态与骨架缓存 | 三种 SkeletonAdapter + SkeletonIndex/Cache；非对称坐标/轴/缩放、空节点、同名路径、深度/节点拒绝、隐藏/后回调、重复实例索引复用断言 PASS |
| 静态资源与 Lua 主导组合 | 内置 cosmetic_controller.lua 选择与编排，OutfitComposition/NativeRenderer 使用原生引擎；实际 Figura 和 YSM 顶点、原版/模型基础、第一人称手骨可用范围及普通/加速准备分支断言 PASS |
| 实例 Globals/Molang/controls 隔离 | CosmeticInstanceContext、CosmeticPlayerAPI、受限 API 与独立控制/变量存储；双副本状态、Lua→Molang→姿态顺序、恢复无回调、禁用/缺骨、超时只停自身用例 PASS |
| 异步代际及原生帧生命周期 | LibraryPreviewSession.prepareComposition 原子替换；取消/失败保留旧预览、快速切换、提交后姿态隔离、新→旧→新交错、回调中关闭最后帧及借用基础存活用例 PASS |
| 穿戴者总预算 | 原指令/复杂度通道、Molang 帧预算、粒子/声音速率桶及 128 自定义纹理槽汇总；跨作者额度、超时计费、单实例/基础计数重置不退款、下一帧恢复、纹理替换/释放/拒绝、基础共享边界断言 PASS |
| YSM 默认动作解析/缓存 | ysm-default-1 的 42 原文件与 28 main 白名单、arm 域、作者/空动作/禁用优先、缺骨/别名/依赖、实际原生播放和冻结 v1 索引断言 PASS；模板正文随本阶段提交，哈希不变 |
| 后台衣柜/权限其他玩家预览 | 在线且有实体时借用原 Figura 机制，离线/无实体时独立预览；在线切换、取消、草稿刷新、无额外 tick/借用生命周期回归 PASS，主衣柜原预览回归 PASS |

- 纹理接线沿用原有 128 自定义纹理数量与 TEXTURE_SIZE 单边尺寸语义，不发明总像素阈值、不限制原生资源纹理的新尺寸；组合成员及基础作用域共用槽位，拒绝时关闭本次未接收图像并保留旧纹理。基础 tick/提取准备沿用原执行次数，并进入组合预算范围。
- 正式 YSM GUI 路径核对并修正预算归属：`forRenderPass()` 的分离运行时使用 `renderBudgetOwner()` 的原 Avatar 面数预算，预览 Molang 亦复用原预算而保留私有变量/控制器。新增用例明确经过实际 forRenderPass，断言 owner 分离但额度相同；不再只以直接原运行时绘制证明 GUI 路径。
- 最终前台 PowerShell/JDK 25 命令：`:fabric:compileJava :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:outfitContractSmokeTest :common:libraryPreviewCacheSmokeTest :common:libraryTransferSmokeTest :common:libraryThumbnailSmokeTest :common:ysmControlRuntimeSmokeTest :common:ysmMolangBudgetSmokeTest :common:ysmSyncSmokeTest :common:ysmDeferredPoseSmokeTest :common:ysmAccelerationMeshesSmokeTest :common:networkProtocolSmokeTest`。退出码 0，`BUILD SUCCESSFUL in 30s`；完整日志另保留于 `build/reports/outfit/stage2-checkpoint.log`。
- 手工检查新增/修改范围的可读性与局部格式；仓库无适用 Java formatter 任务。暂存后的 `git diff --cached --check` PASS；冻结 protocol 与 gradle.properties 未修改。后端本阶段无源码修改，保留其既有未跟踪文件。
- **MANUAL/PENDING，按用户要求跳过不阻塞：** 真实 Minecraft 窗口中的三种基础×两种装饰、旋转/缩放/展开、行走/潜行/游泳/飞行、第一人称、实际普通/JNI/GPU 路径、可听声音/粒子视觉、后台/权限页实际交互、真实资源卸载和帧时间基线/可接受退化阈值。CPU/native 顶点与记录设备不冒充这些结果。
- 暂存模板字节验证发现 core.autocrlf 转换了 26 个原始 CRLF JSON；对该锁定模板目录添加局部 `.gitattributes`：`-text whitespace=cr-at-eol`，保存原始字节并正确识别 CRLF 行尾。重新暂存后全部 42 个 Git blob 的字节数及 SHA-256 与冻结 manifest 完全一致；不改模板或 manifest，验证日志保留于 `build/reports/outfit/stage2-staged-template-check.log`。
- 保持 `1.0.0-alpha.3`；公共 bug 1～3 按用户指定仍排在阶段 1～6 后通用修复。阶段二提交与远端 SHA 核对完成后，才开始阶段三。

## 阶段三：标准/虚拟锚点与装饰导出骨骼挂载（进行中）

- 阶段二已提交 `7d26956b7bbb87ddc576f19075acb2dc67b786c8`，推送 `main` 并核对远端 SHA 一致后开始本阶段；未新建分支。
- 标准名完整保留。作者的同名空 Group 使用精确路径，重名返回多个候选，不暗选第一个；原版从真实 Head/Body/Leg 局部姿态派生头顶、面部、头发、背部、翅膀、髋部、双腿与双脚挂点，不虚构 tailtip。局部点随原生父级旋转/缩放组合。
- 基础骨骼上的虚拟锚点以稳定 UUID 共享父级和 T/R/S，实例偏移独立组合；改名不改变挂载身份，骨架 hash 失效时停止依赖且不初始化作者效果。配置层原有的缺失引用/循环拒绝继续生效。
- 装饰导出的路径从 cosmetic.json 的 exported_bones 读取，拒绝重复、玩家 instanceId、无骨骼却导出等声明。加载后的骨架 hash/路径与当前协商的导出数量/深度交叉检查；资源声明不能自行取得用户授权。当前本地用户资源可用，云端默认拒绝导出挂载，需后续授权加载入口明确提供资源策略。
- 内置 Lua 选择本轮已就绪的依赖；原生绘制捕获父装饰本帧枢轴后，再准备并绘制下一轮子装饰。支持带 instanceId 的装饰骨骼及其虚拟锚点，多层链不依赖配置列表的先后顺序。没有导出骨骼的装饰沿用直接绘制，不额外捕获全树。
- Figura 捕获放在保留姿态作用域内，使用实际 preRender/native draw 的变换；postRender 改值保持原行为而不回写刚捕获的挂点。YSM 使用同一提交姿态、原生镜像轴/缩放与空骨骼枢轴，继续走原生引擎。保留帧重放使用已确定命令，不重做挂载；首次原生绘制异常仍可重放已准备命令，不重复 emit 或毒化控制器。
- 自动验证 PASS：`:fabric:compileJava :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:outfitContractSmokeTest`，前台 JDK 25 / PowerShell，`BUILD SUCCESSFUL`，退出码 0。新增原版旋转点、重名空 Group、共享虚拟点/改名/骨架失效、资源导出声明、反序三层 Figura 依赖链、未导出私有路径/权限/hash 拒绝、YSM 动画空枢轴挂 Figura 的真实原生顶点及异常重放断言。
- 首次新增 Figura 依赖用例因在实例 Lua 初始化前调用 loadScript 失败；修正测试夹具的初始化顺序后重跑 PASS，没有改生产初始化时机来迁就测试。
- **仍未完成：** 拖尾可视绘制/预览及样式设置、自定义贴图/精灵粒子、动态光源和设置、受限动画事件回传。内部覆盖及自动尾尖/世界采样的运行时补齐见后续记录。阶段三保持 IN PROGRESS，未提交完成阶段、未进入阶段四。
- **MANUAL/PENDING：** 真实 Minecraft 视觉、GPU/加速硬件、动作中的挂点/多尾巴与生命周期验收。上述 CPU/native 顶点结果不代表真实窗口或完整游戏端到端 PASS。

### 阶段三：内部骨骼覆盖运行时及范围/权限校验

- cosmetic.json 和后端装饰版本元数据增加可选 `editable_bones`，默认空且空列表不改变后端旧序列化结果。范围使用不含玩家 UUID 的资源相对 BoneRef，必须声明 has_bones/edit_bones、匹配声明 hash；数量/深度继续使用 `[outfit.limits]` 的资源节点/深度，实际覆盖使用原整套 max_bone_overrides。导出插槽不自动取得编辑权限。
- `CosmeticBoneOverrides` 按实际实例和缓存索引解析，联合检查作者 flag/明确范围、原生路径/hash/支持与调用者编辑策略；拒绝返回明确错误，正确授权和路径可重新绑定。本地用户资源默认可编辑，云端默认只读，导出使用策略和编辑策略可以分别传入；后续界面/授权同步入口仍按各自阶段接线。
- Figura 在原生 preRender/动画变换之后、当前骨骼枢轴处追加局部 T/R/S，只在保留绘制作用域中生效。网格、隐藏空骨骼捕获、子骨骼/导出挂点及既有加权蒙皮位置通道使用同一覆盖。普通与加速准备路径共用；非均匀缩放的法线沿用 Figura 的体积归一化约定。作者位置/旋转/缩放和动画通道不吸收覆盖。
- YSM 把覆盖保存进不可变提交姿态，仅为该姿态的原生临时部件施加枢轴局部矩阵；绘制、原生世界/导出枢轴读取共用变换，保留原镜像轴与模型缩放。未绑定装饰覆盖的旧模型路径不增加变换，未修复计划之后的公共 bug。
- 新增原生顶点用例 PASS：Figura/YSM 各两个副本与依赖子装饰，非零枢轴 T/Rz/S、作者/动画位置保持、非均匀缩放法线、保留帧晚到姿态、异常作用域清理、移除覆盖后原生默认绘制、原 NBT 不变；普通/Java 加速准备分支与 Figura 加权网格位置均有数值断言。拒绝用例覆盖无元数据、作者只读、无声明范围、用户策略拒绝及运行时旧骨架，随后正确重绑成功。
- 客户端最终前台验证：`:fabric:compileJava :common:libraryPreviewRuntimeSmokeTest :common:cosmeticSourceSmokeTest :common:ysmDeferredPoseSmokeTest :common:outfitContractSmokeTest`，JDK 25，`BUILD SUCCESSFUL in 30s` / 退出码 0。日志保留 `build/reports/outfit/stage3-internal-client-progress.log`。
- 后端前台 `cargo test --manifest-path omnm-cloud/Cargo.toml service::outfit`，5 项 PASS；覆盖旧元数据往返、可编辑范围往返、重复/所属 UUID/hash/节点及深度限制拒绝。旧上传/候选/预设/CAS 和配置协商回归 `api::library::tests::outfits`，3 项 PASS。日志分别为 `build/reports/outfit/stage3-internal-backend-progress.log` 与 `stage3-internal-backend-api-progress.log`。首次新夹具以整数 JSON 比较浮点变换序列化导致失败，改用对应浮点夹具后通过，没有改变生产数字语义。
- Rust 仅格式化修改的 service/outfit.rs；Java 按现有局部格式检查。阶段三仍未到提交/推送检查点，客户端保持 main、后端保持 master；后端原有未跟踪 build.log/spm-cloud 保留。
- 以上是 CPU/native 调用证据。真实游戏像素、实际 JNI/GPU、动作及树编辑完整交互仍为 `MANUAL/PENDING`，不冒充这些验证已通过；下一步继续同阶段自动尾尖选择和世界空间拖尾。

### 阶段三：Lua 自动尾尖与有界世界空间采样

- 内置 Lua 优先使用明确选定且启用尾巴的授权导出 tailtip；未选择时按稳定实例 UUID 顺序确定默认，同一组合不逐帧更换默认。重名尾尖不暗选；候选依赖可能形成环时排除该候选。选定尾巴暂未绘制时先等待依赖波次，真正不可挂载/失败时回退到基础 tailtip、hip、Body、原生单根/实体根；不改换另一条尾巴。手动绑定失效仍停止，不进入自动回退。
- 选择结果由 Lua 报告给组合的只读状态，后续编辑界面可标明实际默认；显式选择仍保存在配置的 preferred_instance_id。多根模型的实体根只作临时运行时目标，不伪造持久 BoneRef。
- 新增原生 TrailBuffer：按 `[outfit.limits]` 的 max_trails / max_trail_samples 限定启用实例和每条固定容量；Lua 提供距离/时间/有限寿命/传送策略，当前内置默认依次为 0.1 方块、1 tick、12 ticks、8 方块跳变。原生只校验有限值、维护有界环并提供不可变样本，不发送轨迹顶点。
- Trail 分类实例在 Lua 准备并提交挂载矩阵后采样。帧保留时冻结穿戴者世界对象、游戏时钟及前后 tick 位置，结合本帧原生实体根挂点的模型单位矩阵生成双精度世界位置；后来的实体移动或时钟变化不回写旧帧。GUI/手部 pass 不生成或清空世界轨迹，未加载世界的菜单预览不访问世界时钟。
- 按帧序列拒绝重复/旧帧采样及过期队列；目标切换、当前目标不可用、传送跳变、世界变化、Lua 停用/失败、原生绘制异常、组合关闭/资源替换均清理相应历史。逻辑 tick 处理寿命及切世界，即使不再绘制也会过期；退休组合的在途帧不能恢复效果。
- 自动验证新增 PASS：真实原生 Figura 顶点下的默认/显式/禁用/无效/重名/手动失效/潜在环七类尾巴组合；基础各级回退、1/3 容量环、距离/时间及稀疏移动、寿命、非有限输入、乱序与传送/世界/目标重置。世界采样用受控实体时钟夹具实际执行原生 Entity 插值、OutfitComposition 与内置 Lua，覆盖模型单位转世界位置、保留帧、GUI 隔离、max_trails=0 拒绝、切世界/过期/异常清理。该夹具不是实际运行的 Minecraft 世界。
- 采样初接线使无世界的隔离衣柜预览访问 level.getGameTime 失败；已将世界快照限定为存在真实世界对象时读取，并通过原有预览回归。世界采样新夹具先修正不可覆盖的 final getPosition，直接设置原生插值数据；再补齐普通 JVM 缺少的 EntityAccessor Mixin 接口，未修改生产插值或绕过真实 Lua 桥。
- 前台 JDK 25 自动验证：`:fabric:compileJava :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest :common:outfitContractSmokeTest`，退出码 0 / BUILD SUCCESSFUL；日志保留 `build/reports/outfit/stage3-trail-progress.log`。本轮后端未新增修改，上一轮 editable_bones 修改仍等待同一阶段检查点。
- **尚未实现，不能视为阶段三完成：** 采样轨迹的可视绘制及独立预览、拖尾样式/粒子设置、自定义贴图与精灵图粒子、动态光源、动画事件回传。实际世界移动/双尾巴/GPU/跨维度画面为 MANUAL/PENDING；继续同阶段，不进入阶段四，不调整版本或公共 bug 顺序。

## 阶段三增量：CustomParticle / Ribbon（2026-10-22）

- 已核对：阶段一、二检查点已完成，阶段三仍在进行；已通过的挂载、内部覆盖、尾尖选择及 TrailBuffer 世界采样保留。当前无自定义贴图粒子实现，不重复实现这些已完成能力，不回滚未提交工作。
- 用户新增的 newCustomParticle、Billboard/Ribbon/Layered、分层/平铺/拉伸、自由方向/内置轨迹和受限 Lua 回调已整合第 5.4、8、9、10 节及契约。图片补充的 width/length/lifetime/uvRepeat/uvOffset/taper/fade/emissive/light 均登记参数语义和验收。
- 本阶段内按依赖接续共享纹理/UV/生命周期与后端预算，再接渲染模式和内置运动，最后接回调/光源/事件；衣柜界面和示例按阶段四、远端授权事件按阶段五、真实性能按阶段六检查点处理，不强插未就绪基础能力。
- 新增存活粒子、纹理层、Ribbon 顶点及 tick/帧 Lua 回调五个 `[outfit.limits]` 键，动态光源沿用已有键。后端配置/缺省/安全边界校验与 capabilities 下发、客户端解析已实现；客户端可区分旧后端缺少新键（取默认值）与明确传 0（禁用）。实际消费/归还仍随粒子运行时接线，不能将协商测试写成粒子预算执行通过。
- 前台 JDK 25：`:fabric:compileJava :common:outfitContractSmokeTest :common:libraryClientSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL；覆盖新增键缺省/零值/边界/非法值/往返及既有挂载/内部覆盖/尾尖/世界采样/预览回归。日志 `build/reports/outfit/stage3-custom-particle-limits-client.log`。
- 后端前台 `cargo test ... outfit_limits` 3 PASS、`cargo test ... api::library::tests::outfits` 3 PASS，退出码均为 0；真实 HTTP 检验当前配置的五项新值下发，未宣称后端逐粒子执行预算。日志 `build/reports/outfit/stage3-custom-particle-limits-backend.log`、`stage3-custom-particle-limits-http.log`。Rust 仅格式化本轮两个已修改文件；两仓库 diff 检查通过。
- 后续运行时自动测试仍须覆盖实例汇总/回收及弧长 UV、裁剪/退化段、四类轨迹和回调重入；真实画面/GPU/光照仍为 MANUAL/PENDING。
- 阶段三未完成，未进入阶段四，未调整版本；继续遵守整阶段验证后提交、推送 main（后端 master）并核对远端 SHA 的检查点。

### CustomParticle 贴图基础继续实施

- 新增生产 ParticleSpriteSheet，固定有界参数集合并直接读取 Lua 表原始值；按单元尺寸或网格推导，校验边距/间距、整除、冲突、播放范围和有限时间。布局只保存数值，不分配逐帧图像/数组、不每帧解码纹理。marginX/marginY 为两侧边距，spacingX/spacingY 为相邻单元间距，单位均为像素。
- 实现静态图、once/loop/pingPong（端点不重复）、tick+partial tick 播放、暂停保留分数相位、手动帧、私有确定种子随机起点；作者修改原参数表不影响已创建播放对象。该基础将供 Billboard/Layered/Ribbon 共用，尚未对 Lua 暴露构造或执行粒子绘制。
- FiguraTexture 增加原生持有句柄：普通 close 无持有时行为保持；退休后拒绝新的消费者，已有句柄可以继续为在途渲染帧保留引用，最后一个句柄释放才回收 NativeImage/GPU 绑定。句柄释放幂等，生命周期验证已覆盖双粒子与晚结束帧；尚待粒子引擎和帧结束钩子接入实际持有/释放。
- 新增 `:common:customParticleSmokeTest`，覆盖实际生产 UV 数值、两种布局等价、播放端点、50 ticks 中不同调用频率结果一致、暂停/手动控制、非法/超大/非有限参数和实例种子隔离。首次失败为夹具将 22 像素误写成 23，生产整除校验正确拒绝；修正夹具后 PASS，未放宽校验。
- 前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest`，退出码 0 / BUILD SUCCESSFUL；包括原有纹理发布/dirty 更新及新增持有期数值检查。日志 `build/reports/outfit/stage3-particle-texture-lifetime.log`；纯精灵图日志 `stage3-sprite-foundation.log`。原生材质夹具替换 GPU 分配/提交设备，不能证明真实 GPU 或游戏画面通过。
- 下一步仍在阶段三：接 newCustomParticle/newTexturedParticle 与实际原生 Billboard、存活计数、实例清理及帧纹理释放，再按依赖继续 Layered/Ribbon/运动/回调/光源/事件。没有将基础模块测试视为完整粒子系统完成。

### CustomParticle 原生 Billboard 接线

- 已增加 particles:newCustomParticle(texture,pos,velocity) 和复用同一实现的 newTexturedParticle。两者沿用既有立即 spawn 习惯；纹理接受本实例的授权名称/句柄，位置/速度用 Vec3，速度缺省零。新 API 直接读取当前客户端世界，拒绝断线后旧 WorldAPI 缓存；原有粒子构造/工厂行为未替换。
- 实现原生 SingleQuadParticle 子类，使用 Minecraft 原有提取/quad 批次和纹理 Layer；静态图/精灵图 UV、自发光 full-bright 已接入。原有 LuaParticle 通用属性继续使用；新 setSpriteSheet/setFrame/getFrame/setPaused/isPaused/setEmissive/isEmissive 已通过真实 LuaTypeManager 包装调用。新粒子的位置/速度及颜色/透明度/重力/缩放等控制拒绝非有限输入，寿命及缩放须为正。
- 复用原有 PARTICLES 速率权限；新存活数量按 wearer UUID 汇总，清理按 execution UUID，两个同穿戴者副本共同消费 max_custom_particles，max_particle_layers=0 禁用当前单层绘制。重复 spawn 不重复入队/扣额度；失败、remove、寿命结束、资源重载及原生队列淘汰返还存活计数。未成功生成的新粒子 isAlive=false，可在合适上下文重试；已 remove 的粒子不能复活。
- 粒子激活时持有纹理，提取时按纹理为本帧额外持有；GameRenderer.render 的 finally 释放帧引用，即使预览帧清理抛错仍执行。纹理沿用作者当前过滤设置，共用上传。ParticleEngine.clearParticles 注销存活新粒子并移除其 owner 跟踪；实际原生 setLevel 字节码确认调用 clearParticles。ParticleGroup 满 EvictingQueue 时释放将被淘汰的新粒子，防止原生静默淘汰遗留纹理/数量。
- 新 CustomParticleNativeSmokeTest 接入现有原生材质夹具：通过生产 Lua 类型包装创建、设置、读取和删除，实际执行 SingleQuadParticle.extract 与 QuadParticleRenderState.add，核对作者纹理、行列 UV、位置插值、full-bright、存活配额及纹理最终释放。另覆盖缺省速度、重复 spawn、权限耗尽、暂停、断线/旧世界排队、非法设置、引擎提交失败、零预算、多实例竞争、执行隔离、资源重载；反射调用生产 Mixin 清理/淘汰钩子检验状态。这证明 CPU 提取和钩子实现，不证明真实 Mixin 装载、GPU 提交或像素结果。
- 初次编译发现 WorldAPI 返回 Level 而原生粒子要求 ClientLevel，已改为读取实际 Minecraft.level，既解决类型问题也避免旧世界缓存；未修改旧 WorldAPI。最终前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:ysmParticleNativeSmokeTest`，退出码 0 / BUILD SUCCESSFUL；日志 `build/reports/outfit/stage3-billboard-final.log`。此前同轮 `:common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest` 也 PASS，日志 `stage3-billboard-regression.log`。
- **继续待实现：** Layered/Ribbon（含九项参数、平铺/拉伸）、高级运动/受限回调、动态光源/动画事件，以及隔离的粒子预览绘制。当前 DRAFT/独立资源预览拒绝向世界引擎发射，不将此隔离保护冒充预览功能完成。普通 Avatar 无 outfit 能力上下文时使用保守默认；正式基础 Avatar 的协商预算绑定需随阶段五正式运行时接通，仍须在对应验收覆盖基础与装饰合计。阶段三尚未完成，不进入阶段四、不升级版本，也未到整阶段提交/推送检查点。

### CustomParticle Layered 接线

- 新增 setLayers/getLayerCount、setRenderMode/getRenderMode、Billboard 的 setBlendMode/getBlendMode；复用原生粒子、授权纹理查找、公共播放/颜色与生命周期。层定义有界复制，支持独立纹理/精灵图、帧内 UV 裁剪、Vec3/Vec4 tint、alpha、独立 emissive 和 alpha/additive/opaque 混合；缺省及公共/独立控制边界已写入契约。
- 从原生 QuadParticleRenderState 字节码确认提取/准备/渲染均按 HashMap 分批，不能用 Lua 提交顺序证明最终层序。新增固定 8×3 个按需初始化的渲染管线与仅重排自定义槽位的 render 钩子；保持原生粒子的已有相对位置，复用原生 shader/UV/顶点/纹理/光照路径。混合模式共用透明阶段并关闭深度写入，避免后续 opaque 层先于前面的 alpha 层绘制；仍保留深度测试。真实 GPU 结果及 Mixin 装载为 MANUAL/PENDING。
- 当前每个粒子的纹理持有改为按实际使用纹理去重；层列表/模式替换先验证所有声明、取得全部新持有后再替换。新资源失败时撤回临时持有且旧列表、模式及活跃状态不变。渲染仅在提取作用域乘入层 tint，finally 恢复公共粒子颜色/alpha；删除某层后，已有帧继续持有其纹理到帧结束。
- max_particle_layers 已接入实际声明、激活与提取校验；不同层共用一个存活粒子额度，不再因每层增加原生粒子对象。参数表/颜色向量后续变更不回写已有定义；空数组、稀疏/超大索引、未知字段/混合、无权贴图、非法 UV/alpha 及超限均拒绝，不静默截断。
- 新 CustomParticleLayeredSmokeTest 经真实 LuaTypeManager 调用 setLayers，实际执行 SingleQuadParticle.extract 与原生 quad 状态，核对两张纹理、独立精灵图/帧内 UV、RGBA 乘积、全亮/原生光照选择与重复提取稳定性。反射调用生产 render 钩子验证原生槽位保持及自定义层序，并验证混合/深度管线状态。另测失效新纹理导致整次更新回滚、临时持有归还、移除层在途帧保留及最终释放；真实画面未冒充 PASS。
- 首轮新光照用例失败是夹具把原生“区块未就绪”回退误认为全零；核对当前原生字节码，其回退为 0xF00000（天空光），已修正期望，生产光照逻辑未改。追加实际协商 1/8 层的原生提取数量及超限更新保持旧状态用例，均 PASS。
- 前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:ysmParticleNativeSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL，日志 `build/reports/outfit/stage3-layered-native.log`；追加边界用例后前三项再次 PASS，日志 `stage3-layered-limits.log`。本轮后端无新改动；原版本及阶段顺序保持。
- **仍未完成：** Ribbon 九项参数及实际几何、通用平铺/拉伸、运动模式/受限回调、动态光源/事件、隔离粒子预览，以及粒子层四边形与主体/装饰共用的同帧复杂度扣账（随 Ribbon 顶点/共享渲染预算继续接入）。当前存活数和层数消费已有证据，不能据此宣称全部性能预算完成。阶段三未完成，不进入下一阶段，真实 GUI/GPU/性能继续 MANUAL/PENDING。

### 粒子层与模型共用面数额度

- 已将每个 Billboard quad / 每层 Layered quad 计入现有 COMPLEXITY，复用 OutfitInstructionBudget 的帧代际、基础模型消费导入及不返还已用额度语义。自定义粒子在纹理上传/原生提取前整次申请；不足以画完整多层时拒绝本次提交，保留其寿命供后续帧继续，不截断成半个分层效果。实际重复提交继续计费。
- 新增仅供几何调用的 currentFaces 查询及按粒子持有的普通 Avatar 面数账本；装饰继续优先使用原有整套预算。Figura 原生普通/加速共用提交入口及 YSM 原生面数入口接此查询，Lua VM 仍使用原来的 current 查询，未将普通 Avatar 的 tick/init 绑定到未推进的装饰时钟。最后一个粒子移除后保留已花账本至帧末，再回收引用。
- Avatar 的已有 YSM 按帧复杂度准备扩为模型/粒子共同使用，Figura 入口也记录同一帧；即使主体不可见，粒子下一帧也会取得新额度，而不是永远停在旧 remaining=0。原有每 pass 重置仍由共享账本防止返还，未以关闭模型动作或动画来降低开销。
- 新 OutfitParticleFaceBudgetSmokeTest 使用现有原生 YSM 六面模型的实际 24 顶点和实际 Layered quad 提取，验证 6+2=8、模型/粒子先后互换、两者间重置个人计数仍不可超额、剩一面拒绝两层但粒子继续存活、主体不绘制时下一帧补充、最后粒子移除后同帧不返还及帧末回收。另将两个真实 CosmeticInstanceContext 绑定同一生产共享预算，实际提交基础模型后两个独立实例竞争剩余额度，第二实例不能另开额度。
- 前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL；包含新增 native 面数用例及既有挂载、Lua 隔离、内部覆盖、自动尾尖、世界采样、普通/加速 Figura/YSM 组合和原生预览回归。日志 `build/reports/outfit/stage3-particle-faces-native.log`；初接线回归日志 `stage3-particle-faces-progress.log`。
- 证据限于 CPU/native 提交与计数，真实 GPU/GUI/帧时间仍 MANUAL/PENDING。正式基础 Avatar 在装饰作用域外产生粒子的持续预算绑定，仍按既定阶段五正式运行时检查点补齐；本次共享基础模型用例明确在生产预算作用域内绘制基础，再在其作用域外绘制装饰粒子，没有将此用例扩大为全部跨端正式应用已通过。
- **继续阶段三：** Ribbon 实际几何/九项参数、通用 UV 平铺/拉伸、Ribbon 顶点预算、内置运动/回调次数预算、动态光源数量及清理、动画事件和隔离粒子预览尚需实现。已验证存活数、层数和当前几何面数消费，不宣称全部特效或性能完成。未到整阶段提交/推送检查点，未进入阶段四，未升级版本。

### Ribbon 世界弧长与几何基础

- 复用原有 TrailBuffer 增加固定容量的累计距离旁路及不可变 snapshotPath；旧 Sample/snapshot 接口及采样/清理语义保持。环形淘汰和寿命过期仍保留累计接受路径相位，世界/目标/传送等 clear 重置相位，旧快照不受后续采样影响。
- 新增 RibbonMesh 生产几何模块：世界空间宽度、弧长 length / tick lifetime 裁剪及边界插值、随摄像机视线确定共同横截面、重复点/平行视线/急转弯/回头的有限退化处理、尾端 taper 和年龄 fade。相邻段及 UV 分割共享同一边界位置，网格冻结为不可变四边形/顶点结果。
- repeat 按累计接受弧长计算 uvRepeat/uvOffset；stretch 对当前可见长度映射一个周期。实际在整数 UV 边界分割，再把每段压入帧内 [0,1] UV；可供后续精灵图/分层帧内映射使用，不能以整张精灵图的 sampler 重复冒充单帧平铺。共同相位平移避免旧点淘汰导致花纹突然归零。
- 先预检样本上限、平铺细分四边形数量、层数及传入的剩余顶点额度，再创建任何 Ribbon 顶点/四边形；高 uvRepeat 在预检阶段明确拒绝。成本为每四边形 4 个实际顶点，乘全部层，面数同样乘层。该入口的额度参数仍需原生提交时接入穿戴者共享账本，尚不宣称多条 Ribbon 的实扣已完成。
- 新 RibbonMeshSmokeTest 验证实际坐标/宽度、精确共享接缝、不同采样密度下相同世界点 UV 相位、正负流动偏移、repeat/stretch、时间/长度/未来头端裁剪、渐缩/渐隐、急转弯/平行/回头/重复点；覆盖历史环绕/过期相位、目标重置、两层 8 顶点恰好通过/7 顶点拒绝、零额度、高重复密度及非有限/倒序输入拒绝。仅检查生产 CPU 几何，不是实际画面或原生 Ribbon 提交。
- 前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL；新几何用例与原有世界采样、挂载/尾尖、原生预览及普通/加速组合回归 PASS。日志 `build/reports/outfit/stage3-ribbon-geometry.log`。本轮后端未新增改动；两仓库分支仍按既定 main/master。
- **下一步仍在阶段三：** 将 Ribbon 几何接到原生提交、当前实例世界采样/包围盒、Lua 参数、逐穿戴者顶点与共享面数预算，再继续运动/回调、动态光源、动画事件及隔离预览。当前 particles API 尚未暴露 RIBBON 模式，不将单独几何测试冒充可用 Ribbon 功能。真实 GPU/GUI/生命周期画面继续 MANUAL/PENDING；整阶段未完成，未进入阶段四或提交完成阶段。

### Ribbon 参数解析与面数预检

- 在已通过的世界弧长/几何基础上新增 RibbonOptions：width/length/lifetime/uvRepeat/uvOffset/taper/fade/uvMode 及采样距离/间隔/传送阈值的不可变配置、部分更新和独立 Lua 表快照。固定 rawget 字段读取不执行作者元方法，不扫描任意长度表；拒绝字符串代替数字、非有限数、非法正数/布尔值和寿命保留时间溢出。emissive/light 仍由后续粒子统一设置/动态灯路径接通，本次未伪造光照效果。
- 采样策略额外保留一个间隔，实际 RibbonMesh 仍按可见 lifetime 精确裁剪，使边界段可以插值而非提前丢弃；新用例验证寿命 1.5 tick 时准确裁到世界 x=.5，以及额外保留历史最终到期清理。
- RibbonMesh 增加 availableFaces 入口，按细分四边形乘全部层在顶点/四边形分配前检查剩余面数；保留已有调用入口兼容性。新用例覆盖两层恰好两面、剩一面拒绝、零值和负值拒绝。真正的逐穿戴者额度实扣仍待原生 Ribbon 提交接线，本次仅证明生产几何入口的预检。
- 前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest`，退出码 0 / BUILD SUCCESSFUL；新 RibbonOptionsSmokeTest、扩展面数预检和已有精灵图/几何用例 PASS。日志 `build/reports/outfit/stage3-ribbon-options.log`。客户端仍 main，后端仍 master，未创建/切换分支；本轮后端无新增修改。
- **阶段三仍进行中：** 当前 Lua particles 尚未暴露 RIBBON 模式；本次完成配置解析基础，未把它写成参数已影响实际画面。原生提交、世界采样接线/包围盒、共享顶点账本、运动/回调、实际动态光源、动画事件及隔离预览继续待办。真实渲染与性能 MANUAL/PENDING；未到整阶段提交/推送检查点，未进入阶段四。

### Ribbon Lua 与原生粒子提交

- 在当前实现上接通 `setRenderMode("RIBBON")`、`setRibbon(table)`、`getRibbon()` 和 `getRenderError()`；前两项可链式调用，配置快照独立，非法更新不部分发布。Ribbon 可用基础贴图，也可用现有分层列表；在 Ribbon 模式下 setLayers 保持模式和同一条轨迹。emissive 共用已有独立自发光设置；light=false 可显式关闭，light=true/配置表目前明确拒绝，待真实动态灯实现后接通，不静默接受或以 full-bright 冒充照明。
- 粒子引擎 extract 尾部提取 Ribbon，普通 SingleQuad 提取跳过该模式；复用 TrailBuffer 的有界世界位置/弧长历史，按已有全局渲染帧号去重，使用当前原生粒子的插值位置。tick 过期、移除、模式退出、传送和世界失效清理历史；轨迹整体包围盒含历史/头端与半宽，避免仅按头端剔除可见尾部。
- 新 RibbonRenderState 冻结几何、各层帧内 UV/颜色/alpha/光照和摄像机位置。实现原生 ParticleGroupRenderState / ParticleGroupRenderer，写入原生 PARTICLE 格式任意四边形，经原生粒子缓存准备；材质顺序、顶点偏移和每层索引数交给原生 QuadParticleRenderState.render 绘制。保持原生透明粒子目标、shader、sampler、lightmap 和混合配置，没有额外 RenderType/纹理全局缓存，也没有把 Ribbon 伪装成 Billboard 方形。
- 每穿戴者共享 maxRibbonVertices 帧账本，包含全部层和平铺细分；先按剩余顶点/原有共享面数完成预检，再整次实扣，之后才上传贴图并提交。拒绝时保留粒子寿命并报告错误，移除/切模式不返还同帧已提交的顶点；下一渲染帧重新补充。Ribbon 历史数量以现有 maxTrails 限制同一穿戴者的活跃 Ribbon，模式切换和移除准确归还槽位。与既有 OutfitComposition 控制器拖尾的绘制/统一槽位桥仍待完成，未将当前两条独立入口写成已统一。
- 新 RibbonNativeSmokeTest 通过真实 Lua 类型包装配置模式/层/参数，实际调用生产提取钩子、提交到原生 SubmitNodeStorage，并读取 BufferBuilder 的真实 PARTICLE 顶点字节，核对世界到摄像机相对坐标、宽度、帧内 UV 和每层亮度。覆盖不可变提取帧/移除后重放、传送重置、剔除包含尾端且不扣额度、剩一面拒绝两层且不扣顶点、双执行实例竞争八顶点、同帧移除不返还/下一帧补充、trail 槽位切换拒绝的原子性、断连和延迟贴图释放。
- 首次新用例在旧 setPos 的 Mixin accessor 强转处失败：普通测试 JVM 未变换原生 Particle，不能据此证明游戏内错误。调整为原生 setPos + partialTick=1 验证实际传送历史路径，未为测试修改生产 API；新 Lua 模式/设置及生产提取钩子仍实际执行。失败日志 `stage3-ribbon-native-test.log`；修正后完整原生材质任务 PASS，日志 `stage3-ribbon-native-test-corrected.log`。
- 最终前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL（31 秒），包含全部新增用例及既有 Figura/YSM 组合、预算、预览和拖尾采样回归。日志 `build/reports/outfit/stage3-ribbon-native-regression.log`。本次自动验证不运行真实 GPU 上传/draw 或 Mixin 变换后的游戏窗口；这些与真实画面/性能保持 MANUAL/PENDING。
- **仍在阶段三：** 继续控制器拖尾绘制/预算统一、内置运动/自由加速度与旋转、受限回调、真实动态光源、动画事件和隔离预览；不能把三种几何入口接通写成整个 CustomParticle 或阶段三完成。未进入阶段四，未到整阶段提交/推送检查点，当前分支保持 main/master。

### 粒子加速度、旋转与四类内置轨迹

- 新增 ParticleMotion，固定读取 Lua 参数并复制向量/控制点；提供 free、任意轴 orbit/helix、四点 cubic bezier、固定位置或 EntityAPI 目标 tracking。内置运动每原生 tick 定成本计算，不调用作者 Lua 函数、不按渲染次数推进；仍受原有存活数/寿命/几何与实例清理限制，未新增不受限粒子入口。参数单位/默认值及轨迹边界写入契约和计划，未进入阶段四设置界面。
- LuaParticle 接通 set/getAcceleration、setMotion/getMotionMode/getMotionError、set/getRotation 和 set/getAngularVelocity。free 模式先加加速度再执行原生重力/阻力/物理；内置轨迹使用显式位置控制，不叠加 free 的加速度/重力。配置失败保留原运动，切换运动重新以当前 age 计时且不延长寿命，Ribbon 历史同步清空。数值溢出只退役本粒子，避免非有限位置进入渲染。
- 实体追踪只接受已有 EntityAPI 句柄，配置时核对当前粒子世界及实际加载实体；持续检查世界、对象身份、ID、UUID，不按相同 ID/UUID 偷换为新对象。目标失效默认移除，也可 stop；stop 立即丢弃目标引用，冻结位置但保留原生寿命计数，到期照常归还容量。未开放新的世界/网络 Lua 能力，高级回调仍待后续预算实现。
- 旋转单位为度，角速度为度/tick；Billboard/Layered 复用原生 roll。跨 360 度保留正确本 tick 插值，Ribbon 在同一插值角度下绕切线旋转横截面，不改 UV/顶点成本。纯几何与原生顶点字节用例均覆盖 90 度转动和连续接缝，冻结旧帧继续不变。
- 核对原生 Particle.move 字节码发现，碰撞后 stoppedByCollision 会在 hasPhysics=false 时仍提前返回。内置轨迹 tick 因而通过仅在该 tick 生效的 move 分支更新明确位置，继续由 super.tick 管理年龄/生命周期；free 仍走原生 move，不改其私有碰撞状态。新增受控碰撞停止状态验证，确认 helix/后续曲线不被冻结，同时保留原物理配置。
- 新 ParticleMotionSmokeTest 覆盖实际 Lua 参数读取、任意轴/大有限轴归一化、角速度与时间求值、四点曲线端点/中点/循环/复制、追踪步长和不越界、目标失效结果、严格类型/有限值及元方法不执行。新 ParticleMotionNativeSmokeTest 使用 Lua 包装、原生 tick/extract 与受控 ClientLevel/ArmorStand fixture，通过实际 EntityAPI 捕获及查询验证目标移动、相同 ID/UUID 的替代对象拒绝、remove/stop 和寿命、溢出释放；另验证真实加速度位置、重力恢复、所有内置模式及原生 roll 插值。该证据不是实际游戏实体/画面端到端 PASS。
- 初轮新增 Ribbon 旋转接缝测试错误比较了完整 Vertex（包含周期边界有意不同的 UV 1/0），已修正为契约要求的位置一致，保留既有 UV 范围/分段验证；未修改生产 UV 来迎合测试。失败日志 `stage3-particle-motion-math.log`。随后完整回归 PASS，日志 `stage3-particle-motion-native.log`。
- 最终前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL（36 秒）；包含原生碰撞状态边界补充及既有挂点/内部覆盖/尾尖/采样、面数、原生预览和普通/加速 Figura/YSM 回归。日志 `build/reports/outfit/stage3-particle-motion-final.log`。本轮后端无新增改动，两仓库仍为 main/master。
- **阶段三仍未完成：** 控制器拖尾绘制/统一槽位、Lua 回调次数与指令预算、实际动态光源、动画事件及隔离粒子预览继续待办；真实 GPU/轨迹画面与性能 MANUAL/PENDING。未到整阶段提交/推送检查点，未开始阶段四，未更新版本。

### 粒子 Lua 回调次数、指令及重入边界

- 新增 CustomParticleCallbacks 及 Lua setTickCallback/setFrameCallback/getCallbackError，支持 nil 注销和链式设置。tick 在原生运动前运行；frame 在粒子引擎 extract 头部运行，一帧只派发一次固定存活列表，新增粒子的 frame 回调等下一帧。逐粒子先记录 tick/frame 身份再调用，运行中递归 tick 直接返回，整帧派发也有重入保护；不在冻结帧重放中执行 Lua。
- 每穿戴者共享 maxParticleCallbacksPerTick / maxParticleCallbacksPerFrame 的独立实扣账本；零值拒绝注册，超限只跳过本次回调，原生寿命/运动继续。注销/移除不返还当前 tick/frame 的计数。Minecraft.tick HEAD 先推进时钟，避免原生粒子 tick 与现有 RETURN 处的 Figura/Avatar tick 各自取得一份额度；所有实例使用同一真实逻辑 tick。
- 回调复用所属 FiguraLuaRuntime 的既有 TICK_INST/RENDER_INST 钩子及 OutfitInstructionBudget。普通几何粒子仍不改变原 Lua 预算行为；只有显式启用回调才引入随粒子生命周期保留的普通 Avatar Lua 账本。装饰继续优先使用原整套账本；隐藏所有者在新 tick/frame 有新余量，多 pass 重置个人 counter 不会退款。失败 VM 沿用脚本停用/本执行实例清理，回调错误报告过程本身异常也不会向粒子引擎抛出。
- 补齐中途注册边界：旧普通脚本或 init 运行时首次注册回调，保留外层钩子，嵌套调用不重置 hook；外层成功及失败的全部指令只计一次。否则注册前的工作或首次失败可能脱离新账本。修改仅为显式回调注册接入所需；没有回调时维持原普通路径，几何-only 现有验收继续通过。
- 新 OutfitParticleCallbackSmokeTest 使用真实 FiguraLuaRuntime/LuaClosure、Lua 类型包装、原生粒子 tick 与受控客户端/声音清理 fixture，验证一次/tick 与一次/frame、递归 tick/frame、隐藏主体补充、个人 counter 重置、双执行实例争用一个次数、移除后新注册不退款、tick/frame 分离、零值、真实无限 Lua 循环在剩余共享指令处停止且健康实例继续、下一帧恢复、普通调用中首次注册/嵌套 hook 递增、首次注册后的无限循环仍计满失败指令，以及 init 首次注册不漏计/重复。记录器不证明真实游戏窗口/Mixin 变换后的端到端表现。
- 初次编译 PASS，日志 `stage3-particle-callback-compile.log`；第一轮原生任务 PASS，日志 `stage3-particle-callback-native.log`。补齐中途注册边界后，前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL（44 秒）；日志 `build/reports/outfit/stage3-particle-callback-regression.log`。包含既有默认动画、内部覆盖、自动尾尖、世界采样、三种粒子/轨迹、普通与加速模型面数及原生预览回归。
- **继续阶段三：** 控制器拖尾绘制/统一槽位、真实动态光源、动画事件、隔离粒子预览及其后才能开放的装饰 particles 入口仍待完成。正式基础 Avatar 在装扮作用域外的持久预算/协商上限绑定仍在既定阶段五检查点处理；本次不同装饰副本共享用例没有覆盖这条完整正式应用路径。真实 GPU/性能 MANUAL/PENDING；尚未到阶段三提交/推送检查点，两仓库仍 main/master，本轮后端未新增修改。

### 粒子动态光源设置、世界/实体采光与清理

- 在现有粒子上新增独立 dynamicLight 开关、lightLevel/lightRadius 及 Lua 设置/读取方法，默认 false/15/7.5；半径/亮度严格校验非有限、整数和硬边界。Ribbon light 布尔/表别名接同一配置，getRibbon 返回独立 enabled/level/radius 表；三种模式及多层只持有一个源。旧记录中的 light=true 暂拒状态在此接通，没有以 emissive/full-bright 代替世界照明。
- 新 ParticleDynamicLights 按实际执行实例持有源，按 wearer 汇总 maxDynamicLights；启用、激活、参数更新和运行中消费 dynamicLightingEnabled/maxDynamicLightLevel/maxDynamicLightRadius。启用失败保留旧设置/光源；配置收紧或世界不匹配时解除源并关闭该粒子的动态灯配置，粒子仍按正常寿命运行。显式关闭、过期、移除、重载、批量实例清理和断线回收计数及源；传送使旧/新受影响区域重建。
- 新 ParticleLightField 是不可变场景快照和节坐标空间索引，工作线程不接触粒子/Lua/可变实例；同世界查询只读取该节候选源，径向线性衰减取原生整数亮度，源叠加取最大值，原天空光及更亮原光不变。半径上限 32、加一个方块采光边界，最多 216 节/源；零亮度不创建索引项或标脏地形。未改服务端光照数据，也未引入外部照明依赖。
- 核对当前 Minecraft 26.1.2 字节码：BlockModelLighter/其 cache 与 FluidRenderer 走 LevelRenderer.getLightCoords；实体走 EntityRenderer.getPackedLightCoords。相应注册 ParticleWorldLightMixin / ParticleEntityLightMixin，RenderSectionRegion 的 level 由新访问器提供精确世界身份。世界提取前/客户端 tick 后发布快照；地形用稳定 tick 位置并合并旧/新节标脏，实体用帧内插值位置，重复绘制帧不重复触发地形标脏。原生区块重建仍由 Minecraft 自身调度，不同步重建整个世界。
- 新 ParticleLightFieldSmokeTest 覆盖源中心/半径边界/衰减、天空光/原亮度保留、重叠 max、负节坐标、世界隔离、不可变输入、最大有界节数、零亮度及严格 alias 设置。新 ParticleLightNativeSmokeTest 通过真实 Lua 类型包装、生产世界/实体钩子方法、受控区域访问器/ClientLevel/ArmorStand 和 LevelRenderer 标脏记录，覆盖亮度与 emissive 分离、插值实体/稳定地形、重复帧不标脏、传送旧/新区、关闭/过期/真实 resourceReloadEvent 清理、Ribbon/多层仅一灯、双实例数量竞争、失败设置原子性、数量/亮度上限降低、后端禁用/零值及断线回收。
- 这些是 CPU/原生钩子调用和重建请求证据，未在真实 Minecraft 窗口运行 Mixin 变换、异步区块完成与 shader 画面；实际亮暗世界/实体、墙面/透明地形、移动/清理画面及性能仍 MANUAL/PENDING。原生/加速组合和预览现有回归没有被当作这些新场景的像素证据。
- 前台 JDK 25 初次编译与原粒子任务 PASS，日志 `stage3-particle-light-compile.log`；新光场/原生任务 PASS，日志 `stage3-particle-light-native.log`。最终 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest` 退出码 0 / BUILD SUCCESSFUL（28 秒），日志 `build/reports/outfit/stage3-particle-light-regression.log`。相关轨迹/回调/面数/挂点/内部覆盖/尾尖/预览及普通/加速 Figura/YSM 回归 PASS。
- **继续阶段三：** 控制器拖尾可视绘制/统一槽位、受限动画事件、隔离粒子预览及之后的装饰 particles 入口仍待完成；正式基础 Avatar 的作用域外持久预算/协商上限绑定沿既定阶段五处理。当前阶段未完成，未开始阶段四，未到整阶段提交/推送检查点；本轮后端未新增改动，两仓库保持 main/master。新增 MySQL 复用规则已接收；本轮未使用或安装数据库。

### 控制器拖尾与 CustomParticle Ribbon 的统一预算

- 新增 OutfitTrailBudget，将原 CustomParticleRuntime 的 Ribbon 数量与每帧顶点账本移到穿戴者共享的唯一消费路径。控制器世界轨迹首次有效采样时保留槽位，容量已满时不创建历史；GUI/手部 pass 沿用原隔离行为。保留帧重放不重复取得槽位。
- 目标失效、原生绘制失败、世界切换、无人渲染时的轨迹到期及组合关闭均释放控制器槽位；旧帧失效仍尊重 TrailBuffer 序列号，不清除较新历史或返还其容量。Ribbon 的激活、模式切换、移除和上限检查消费同一账本。顶点提交仍在完整几何预检后计费，删除粒子或释放槽位不退款，下一全局渲染帧才重置。
- 扩展实际生产 composition/Lua/frame 采样测试，验证其他来源占满后控制器不能另领额度，以及成功采样、目标失效、换世界、到期和关闭后的实际计数。扩展原生 Ribbon 测试，验证 TrailBuffer 槽位阻止额外粒子激活、释放后恢复，以及外部顶点收费后实际两层 Ribbon 被拒绝、较小 Ribbon 可提交。外部收费用例只证明共享账本集成，尚未证明控制器提交了可见顶点。
- 前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL（34 秒），日志 `build/reports/outfit/stage3-shared-trail-budget.log`。既有运动、回调、动态光源、面数、锚点、内部骨骼、自动尾尖及原生预览回归通过。
- **阶段三继续：** 统一槽位已接通，控制器拖尾实际绘制仍待完成；另有动画事件、隔离粒子预览及其后才能开放的装饰 particles 入口。真实游戏/GPU/性能仍 MANUAL/PENDING，未进入阶段四，未达到阶段三提交/推送检查点。本轮后端无新增改动，分支保持 main/master。

### 控制器世界轨迹到原生 Ribbon 绘制

- CosmeticInstanceContext 仅为所属 Trail 实例绑定只读轨迹快照；TrailBuffer.snapshotAt 校验世界/时间/历史有效性，复制有界路径且不采样、不延长历史。组合关闭、目标失效/过期或换世界后不给旧粒子重建历史。
- CustomParticle/LuaParticle 新增显式 setAnchorTrail/isAnchorTrail，默认关闭。Ribbon 链接后复用自己的装饰控制器轨迹、现有几何/材质/面数与顶点预检、原生粒子提交和帧纹理租约。控制器保留一份历史槽位，多个材质粒子可复用历史但各自付粒子/层/面/顶点/光源预算；移除材质不会释放控制器槽位。回到独立 Ribbon 若容量不足会拒绝并保留原链接。普通 Avatar 或没有控制器历史的装饰不能启用，也没有跨实例选择入口。
- Lua 控制器继续决定源历史采样策略；链接粒子的 setRibbon 只裁剪和渲染已有历史，不修改其他共享消费者的采样。总粒子寿命照常，世界坐标/UV/颜色/纹理在提交后冻结。非自发光读取锚点头部世界光；独立动态光源同样取头部位置，历史暂不可用时撤下照明而保留已预留光源槽位及设置，恢复采样后可再照明。
- 新 AnchorTrailNativeSmokeTest 使用实际 composition/内置 Lua/原生骨架与受控世界时钟，通过 Lua 类型包装设置链接，核对原生 PARTICLE 顶点坐标和 SubmitNodeStorage 队列；覆盖无历史、首点不虚构段、同轨迹两个绘制粒子、独立 Ribbon 被共享上限拒绝、切换失败原子性、移除材质不释放历史、目标失效/恢复、头部动态光、传送和整个组合退休/冻结顶点重放。旧原生入口新增普通 Avatar 无法链接的验证。
- 初轮编译 PASS，新增夹具在清理时缺少声音管理器导致任务失败，日志 `stage3-anchor-ribbon-native.log`；复用既有 recordingSoundManager 夹具后原生任务 PASS，日志 `stage3-anchor-ribbon-fixture.log`。没有更改生产声音行为。
- 最终前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL（31 秒），日志 `build/reports/outfit/stage3-anchor-ribbon-regression.log`。包含共享额度、回调、动态光源、运动、标准/虚拟锚点、内部覆盖、自动尾尖、控制器采样及普通/加速 Figura/YSM 与原生预览回归。
- **阶段三继续：** 绘制桥自动检查通过；真实 Minecraft 的渲染时序、光照和 GPU 像素仍 MANUAL/PENDING。动画事件、隔离粒子预览及后续开放装饰 particles 全局仍待完成。阶段四的模板/作者示例入口依赖这些能力，不提前生成资源。未到整阶段提交/推送检查点，未进入阶段四，分支仍 main/master，本轮后端无改动。

### 动画通知队列、Lua 订阅与 Figura 事件源

- 新 CosmeticAnimationsAPI，仅在装饰实例的实际 FiguraLuaRuntime 注册 cosmetic_animations。onEvent 替换单一监听，nil 注销；事件为新的数据表 animation/type/marker/time/generation，不暴露或执行通知中的源码。待派发队列数量使用协商 maxParticleCallbacksPerTick，UTF-8 字符串使用 maxStateStringBytes；拒绝/队列满/执行失败由 getEventError 查询。
- 与高级粒子 tick 回调复用同一穿戴者实扣次数账本，执行走原 VM 和整套 tick 指令预算。生产 Avatar.tick 在重置 tick 指令后派发；严格比较全局逻辑 tick，固定本次候选数并防重入，回调新事件等下一 tick。VM 替换、监听替换、重载、退休和播放代际失效拒绝旧工作；失败只清所属执行实例及其粒子，不能从错误报告路径向客户端 tick 抛出。
- Figura 原生代码关键帧经过时产生 keyframe 通知（marker 为空，不返回代码）；ONCE/HOLD 第一次结束产生 end。自然完成保留播放代际，手动 stop/restart 使旧通知失效，原关键帧脚本执行及自然停止/保持行为保留。没有改动 YSM 公共 bug 的动作/姿态逻辑。
- 新 CosmeticAnimationEventSmokeTest 使用实际 Lua 类型包装/订阅、原 Animation.playCode 和受控时钟下的原 ONCE 完成分支，覆盖下一 tick、代际/实例隔离、队列满、同穿戴者次数、回调生产新事件/重入、nil/零值、重载、无限 Lua 循环耗尽共享指令且健康实例下一 tick 恢复。测试直接调用生产派发器；生产 Avatar.tick 调用点已编译接入，但未据此宣称真实游戏时序/粒子画面通过。
- 初次前台编译 PASS（12 秒），日志 `stage3-animation-event-compile.log`。原生测试首次失败及诊断日志分别为 `stage3-animation-event-native.log` / `stage3-animation-event-diagnostic.log`：夹具在 Avatar 延迟初始化完成前注册，首次原代码关键帧执行刷新了该 VM。夹具按原生 initializePreviewPlayer 先完成初始化后通过，日志 `stage3-animation-event-initialization.log`；没有修改生产初始化逻辑来适配测试。
- 最终前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL（25 秒），日志 `build/reports/outfit/stage3-animation-event-regression.log`；既有粒子/拖尾/动态光源/回调、组合、默认动画、原生/加速和隔离模型预览回归通过。
- **阶段三继续：** 动画事件尚缺 YSM 源接线；隔离粒子预览及后续开放装饰 particles 全局仍待完成。setPage 继续留在阶段四轮盘登记，不提前做界面；授权跨端事件仍阶段五。真实游戏/GPU/性能 MANUAL/PENDING。两仓库仍 main/master，本轮未改后端，尚未到整阶段提交/推送检查点。

### YSM 动画事件源和播放生命周期

- YsmAnimationPlayer 在原初始事件与原 YsmAnimationEventWindow 经过窗口给所属实例的 CosmeticAnimationsAPI 排队，原事件仍由既有 sound/particle/Molang 处理。marker 仅取事件参数中显式 marker，不传递或二次解释事件表达式。原初始声音延迟到实体绑定时处理不会再排一次同一通知。
- ONCE/HOLD_ON_LAST_FRAME 首次到端点生产一次 end；自然淡出移除不取消其通知。显式 stop、fadeOut、控制器退休/重置取消该播放的排队通知，主动淡出不再生产 Lua 通知，原姿态/声音/粒子处理不改。关闭或被替换的运行时、子实体和 observerControlled 投影不生产本地通知；Molang 预算失败同时丢弃本实例待派发通知，避免失败帧残留回调。
- 新增内部通知代际，保留原同步 playbackGeneration。检查发现原 playInSlot 也用于控制器逐帧权重刷新；这种连续播放只保留一个通知代际和结束标记，不能每帧取消队列/重复结束。新槽位、直接 play 或取消后重新激活才开始新通知代际。
- 扩展 CosmeticAnimationEventSmokeTest，通过实际 Avatar YSM NBT 装配、原解析器、原 play/stop/fade/controller 入口及受控 delta 下的生产 advanceAnimationTimeline 验证：初始事件、时间线/粒子 marker 顺序、原 Molang 赋值仍生效、自然移除后结束仍送达、复用对象的新代际、主动淡出取消、HOLD 单次结束、连续控制器权重刷新不丢标记/不重复结束、控制器重置及观察端投影拒绝旧通知。没有把直接时间线方法调用当作真实游戏或跨端授权事件的证据。
- 初轮主代码编译通过，测试编译发现夹具误用了不存在的 Format.YSM，已改用既有 YSM_PLAIN；失败日志 `stage3-ysm-animation-events.log`。随后原生用例 PASS（14 秒），日志 `stage3-ysm-animation-event-native.log`。
- 完成控制器连续刷新边界后，前台 JDK 25 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL（36 秒），日志 `build/reports/outfit/stage3-ysm-event-regression.log`；包含既有粒子、Lua 指令、动态光源、拖尾、挂点/内部骨骼、默认动画及原生/加速组合和预览回归。
- **阶段三继续：** Figura/YSM 本地动画通知已接线，隔离粒子预览和其后开放装饰 particles 全局仍未完成；完整“作者动画通知 → 自定义粒子”入口需在该检查点验证。阶段四页面/模板和阶段五授权跨端事件未提前实施。真实游戏时序、像素及性能 MANUAL/PENDING；未到阶段三提交/推送检查点，仍 main/master，本轮后端无改动。

### 隔离粒子预览的所属关系、预算与生命周期

- 新 PreviewParticleScope，接入 LibraryPreviewBinding 创建/关闭、composition 的实例绑定/退休及预览 tick。scope 使用私有 UUID 汇总粒子数量、轨迹和回调额度，借用的在线玩家原预览路径没有新建该 binding。scope 的 particle tick 在预览 Avatar tick 前推进，避免同一预览有多个显示入口时重复推进；有组合基础时进入其共享 Lua 预算作用域。
- CustomParticle 捕获创建时所属 scope；隔离对象仍复用原运动/寿命/回调/贴图租约，但不添加到世界 ParticleEngine。原世界 frame 回调和 Ribbon 提交明确跳过隔离对象；本地 prepareFrame 只调用一次自己的回调，GUI 调用点继续待办。局部运动跳过真实碰撞，采光不访问真实世界光场；动态光仅验证局部开关/数量/设置，不能发布世界照明。配置收紧会在后续局部 tick 退役超量对象或关闭不再允许的局部光设置。
- 绑定前缓存的句柄拒绝借 scope 激活，已有世界粒子的同一个 Avatar 对象也不能被新 scope 接管。原生粒子工厂可能有二次世界副作用，因此预览仍保持原生效果禁用：普通链式调用返回不存活的 NoRenderParticle，isPresent=false，不调用实际工厂；CustomParticle 才由局部集合持有。没有修改世界原生粒子路径。
- Avatar 资源重载/clearParticles、粒子/动画回调失败及 particles.removeParticles 在绑定预览中按对象身份清理，不清相同 UUID 的世界对象或兄弟实例。composition.close 立即 retire 发射者，保留帧不能复活粒子；binding.close 立即清 scope，Avatar/纹理仍沿用原帧引用退休逻辑。
- 新 PreviewParticleNativeSmokeTest 通过实际 ParticleAPI/LuaParticle、实际 Lua 回调和原生 tick 验证：不向记录的世界引擎提交、不发布世界光、私有预览/同 scope 预算、同 UUID 的世界粒子保留、旧句柄/活世界 Avatar 绑定拒绝、原生链式调用不触发工厂、局部 tick/frame 一次及限额、原生到期、资源重载、发射者退休和世界丢失清理。测试显式安装夹具的粒子权限，不代表生产默认预览权限已经开放，也不验证 GUI 粒子像素。
- 初轮前台 JDK 25 编译/原生粒子/原生预览任务 PASS（37 秒），日志 `stage3-preview-particle-lifecycle.log`。补齐原生链式兼容及绑定拒绝前置后，前台 `:fabric:compileJava :common:customParticleSmokeTest :common:ysmMaterialNativeSmokeTest :common:cosmeticSourceSmokeTest :common:libraryPreviewRuntimeSmokeTest`，退出码 0 / BUILD SUCCESSFUL（34 秒），日志 `build/reports/outfit/stage3-preview-particle-regression.log`。既有普通粒子、动画事件、挂点、控制器拖尾、默认动画和普通/加速组合回归通过。
- **阶段三继续：** 隔离生命周期通过；仍需 GUI 粒子提交/坐标与材质、预览权限受控启用、准备期/延迟初始化的副作用边界，以及之后开放装饰 particles 全局并验证作者动画通知到粒子入口。真实 GUI/GPU/性能为 MANUAL/PENDING。尚未到整阶段提交/推送检查点，仍 main/master，本轮后端无改动。

### 隔离粒子 GUI 几何与控制器拖尾

- 新 PreviewParticleFrame 冻结原生 PARTICLE 格式顶点；Billboard 直接使用 QuadParticleRenderState 原生旋转写入器，Ribbon 复用原网格/分层 UV 写入器。提取消费既有面数和 Ribbon 顶点预算并保留纹理帧租约；重放只写顶点，作者后续模式变化或粒子移除不改写已冻结帧。
- LibraryPreviewBinding 在视图提交时捕获实体原点，在该 EntityRenderState 上缓存一次提取；独立 Figura 与原生实体 GUI 均接线，后者使用 SubmitNodeCollector custom geometry 队列，按作者层号保持不同混合材质的顺序。朝向与原生 PiP 的负 Z 缩放分离，原生实体预览粒子使用与其基础姿态相同的 partial tick 1。帧结束清缓存，贴图仍沿用既有帧租约回收。
- 绑定 scope 的组合控制器只在自己的 FIGURA_GUI 帧采样，轨迹槽位归 scope 私有 UUID；世界组合的原规则不变。linked Ribbon 可读取这份控制器只读历史，重复保留帧不能追加；退休清历史与局部槽位，不归还真实世界持有的槽位。
- PreviewParticleNativeSmokeTest 增补实际原生顶点缓冲及 GUI 提交节点验证：Billboard 原点转换一次、Ribbon 与 Billboard 共存、多层/混合顺序、PiP 旋转/缩放/Z 翻转、模式修改/粒子移除后的冻结重放，以及预览不发布世界引擎/动态灯。OutfitTrailRuntimeSmokeTest 增补实际 Lua/GUI 帧采样、私有额度、只读 linked 源、保留帧重放与退休。测试显式赋予粒子权限，不代表生产默认权限已开放。
- 验证中修正两个夹具边界：纯 JVM 无 Mixin accessor，冻结重放改以实际模式切换验证；无 ClientLevel 的既有 fixture 没有粒子 scope，不应读取未初始化的世界位置。原生预览捕获发生在 extractRenderState 前，独立 Figura 状态也没有世界坐标，因此原点取提交时实体位置，而不读取尚未填写的 state.x/y/z。没有为夹具更改生产 UI 行为。
- 前台 JDK 25 `:fabric:compileJava :common:ysmMaterialNativeSmokeTest :common:libraryPreviewRuntimeSmokeTest` 退出码 0 / BUILD SUCCESSFUL（35 秒）；日志 `build/reports/outfit/stage3-preview-particle-controller.log`。此前三模式、PiP 翻转及分层提交回归 PASS（43 秒），日志 `stage3-preview-particle-panel-complete.log`；完整既有挂点、世界拖尾、内部骨骼、动画事件、普通/加速组合和预览回归均通过。
- **阶段三继续：** 已接 GUI 几何/材质及控制器源，仍需受控启用预览粒子权限、解决准备期/延迟初始化副作用边界，随后开放装饰 particles 全局并验证作者动画通知到粒子入口。真实 GUI/GPU/性能 MANUAL/PENDING；未到整阶段提交/推送检查点，保持 main/master，本次后端无改动。

### 准备期粒子隔离、生产权限和作者入口

- LibraryPreviewSession 的当前玩家 Figura 副本在异步 load 前使用 createPlayerModelPreview，保留原权限/host 身份并标记粒子归属。usesPreviewParticles 统一区分准备中的副本、独立评估器、DRAFT 及已绑定 scope。绑定前原生工厂不执行、自定义粒子不进入世界引擎，clearParticles/RESOURCE_RELOAD/clean 不按共享 UUID 清世界粒子；OWNER/OBSERVER 的既有实例清理语义不变。
- 新 createParticlePreview 只供实际独立模型预览采用默认粒子生成速率，仍要求私有 scope 才能激活；原评估器 createLibraryPreview 继续零粒子权限，network/sound 等限制不变。PreviewParticleNativeSmokeTest 移除人工粒子权限/速率注入，直接测试生产工厂；实际无效 YSM load、排队清理、资源重载和重复 clean 保留同 UUID 世界粒子。补足纯 JVM fixture 的 ClientLevelInvoker，仅模拟无实体查询，不更改生产查询。
- 装饰 Lua 仅新增 particles 全局，host/net/world/file/textures 等原边界不变。新增 CosmeticParticleEntrySmokeTest 从实际 NBT、PNG 和作者脚本，经原生 Figura/YSM 动画通知、Avatar 下一 tick 派发和公开 newCustomParticle，验证 DRAFT 私有 GUI 几何与 OWNER 世界提交、同资源双副本、重载撤销、兄弟保留及退休拒绝，不以手工调用回调代替完整入口。
- 补齐 YSM 无 FiguraRenderer 时的包内纹理解析；复用当前原生 FiguraTexture，其他已加载纹理按原始数据身份惰性缓存，ID/路径别名共享，既有 GUI 图像表也可解析。没有新增文件系统/远程读取，也没有改原生皮肤切换行为。运行时关闭清缓存，粒子/帧租约延迟最终释放；真实 GPU 对象在 recording device 下释放验证通过，真实画面仍 MANUAL/PENDING。
- 本轮前台 JDK 25 完整编译/原生材质、作者粒子入口、源/控制器和预览回归 PASS（59 秒），日志 `build/reports/outfit/stage3-cosmetic-particle-formats.log`；加入纹理缓存关闭及大小写皮肤别名复用证据后的编译/原生回归 PASS（36 秒），日志 `stage3-checkpoint-native.log`。契约/几何任务 PASS（4 秒），日志 `stage3-checkpoint-contract-geometry.log`。早先准备期 PASS（21 秒），日志 `stage3-preview-particle-preparation.log`；生产权限/预览回归 PASS（17 秒），日志 `stage3-preview-particle-permission-regression.log`。

### 阶段三验收检查点（2026-10-22）

| 本阶段要求 | 自动证据 | 结果/边界 |
| --- | --- | --- |
| 标准/空/重复挂点，虚拟锚点，依赖与骨架失效 | OutfitAnchor/AutomaticAnchor/NativeRender 的原生矩阵与 Lua 控制器用例 | PASS；动态游戏画面 MANUAL/PENDING |
| 装饰内部覆盖、editable_bones 范围/权限及实例隔离 | CosmeticBoneOverride、Figura/YSM 原生姿态、后端元数据用例 | PASS；不改作者文件或基础骨架 |
| 尾尖选择/回退、世界空间采样及局部预览 | TrailBuffer/OutfitTrailRuntime/AnchorTrail 原生采样与顶点 | PASS；传送/世界/失效/退休清理已覆盖 |
| 静态/精灵图，Billboard/Layered/Ribbon，九项 Ribbon 参数 | customParticle、Layered/RibbonNative 及 PreviewParticle 原生顶点/提交 | PASS；透明/UV 接缝/真实像素 MANUAL/PENDING |
| 自由/螺旋/环绕/追踪/贝塞尔运动、受限 Lua 回调和动画事件 | ParticleMotionNative、OutfitParticleCallback、CosmeticAnimationEvent/ParticleEntry | PASS；真实游戏时序 MANUAL/PENDING |
| 独立动态灯和各项后端动态上限 | 原生采光钩子/区块标脏、数量/面/顶点/回调账本、HTTP capabilities 用例 | PASS；实际世界/实体照明和性能 MANUAL/PENDING |
| 构建、兼容性与后端 | Fabric 编译、上述完整源/原生/预览回归；cargo test 126 PASS/0 FAIL/1 原有 ignored；定向 rustfmt --check | PASS；旧协议和当前版本未变 |

- 后端最终日志：`omnm-cloud/target/outfit-reports/stage3-checkpoint.log`；保留原始未跟踪 build.log/spm-cloud，不提交测试产物。
- 后端 master 检查点 `d3121f02bcef4272b3e39a963e4737bbf2b7995a` 已推送，并通过 ls-remote 与本地 HEAD 相等核对。
- 本阶段检查点提交名：客户端 `feat(outfits): implement mounted cosmetics and custom particles`；后端 `feat(outfits): configure custom particle budgets and editable bones`。提交/推送当前 main/master 并核对远端 SHA 后，才开始阶段四。
- 阶段四的编辑 UI/设置/模板、阶段五的正式 Apply/授权跨端事件与持久基础预算绑定、阶段六的真实验收/性能和 alpha.4 交付保持原顺序。人工项按用户要求明确标记并跳过；不宣称真实 GUI/GPU、双客户端或 alpha.4 发布已完成。三个公共 YSM bug 保持 OPEN，按用户指定在阶段六后全局修复。

## alpha.3 已知公共 bug（用户指定在阶段 1～6 完成后修复）

- **共同修复约束（用户明确要求）：必须全局修复，禁止特化修复。** 所有已登记缺陷均须定位通用 YSM 运行时、动画或渲染路径的根因并在共享路径修正；禁止按模型名称、文件路径/哈希、作者或指定模型添加专用分支、参数补丁或白名单。`DS鲸鱼娘.ysm` 与 `少女-哥伦比娅.ysm` 仅作为复现/回归样本，修复后同时验证其他 YSM 模型，确认共享行为正确且无回归。执行顺序不变，仍在阶段 1～6 完成后处理。

- **YSM 第一人称手部异常 — OPEN / MANUAL/PENDING。** 测试模型：`DS鲸鱼娘.ysm`。用户提供图一为 Sparkle-Morpher 加载该模型的参考效果，图二为当前项目效果，第一人称手部姿态/位置异常。截图仅作为现象证据，根因尚未确认。
- 图一：`D:/Temp/User/codex-clipboard-34c0b41f-8917-487b-bf0c-e6f47cc2fcfe.png`；图二：`D:/Temp/User/codex-clipboard-82ca8016-c362-4ac0-b8b8-f75fb90f4dcc.png`。
- 执行顺序：先完成本计划阶段 1～6 及各阶段检查点，再参考 Sparkle-Morpher 定位并修复；使用同一模型、相同视角与动作条件对照验证。当前仅登记，不将该 bug 修复并入阶段二或扩张当前阶段验收范围。

- **公共 bug 2：YSM 仰头等动作的头发及头部朝向异常 — OPEN / MANUAL/PENDING。** 测试模型：`少女-哥伦比娅.ysm`；视角：第三人称正面。用户反馈仰头等动作时头发表现不正常，且头部未像参考效果一样面朝玩家/镜头。图一为当前项目，图二为 Sparkle-Morpher；截图中的头发姿态和面部可见情况存在差异，根因尚未确认。
- bug 2 图一（当前项目）：`D:/Temp/User/codex-clipboard-c4cf7ec3-2c75-4c00-b0b2-bdfaece9f8ed.png`；图二（Sparkle-Morpher）：`D:/Temp/User/codex-clipboard-093afd31-c649-4933-b180-8b91b7f973b4.png`。
- bug 2 同样排在阶段 1～6 完成后定位修复；使用同一模型、第三人称正面视角及相同俯仰/动作条件，与 Sparkle-Morpher 对照头部朝向和头发姿态。当前仅登记，不提前修改 YSM 动画、头部旋转或头发物理行为。

- **公共 bug 3：YSM 睡姿异常 — OPEN / MANUAL/PENDING。** 用户说明任意 `.ysm` 模型均可测试。图一为当前项目，睡眠界面中模型仍呈竖起/倾斜姿态；图二为 Sparkle-Morpher，参考模型横卧于床上。当前只记录现象，尚未确认通用睡眠状态、动画或整体变换中的根因。
- bug 3 图一（当前项目）：`D:/Temp/User/codex-clipboard-05148e6b-2485-497c-a2af-0a112be5132e.png`；图二（Sparkle-Morpher）：`D:/Temp/User/codex-clipboard-db986dde-1796-42f6-b716-fd6478958ffa.png`。
- bug 3 在阶段 1～6 完成后，与前两项一起按通用路径定位修复；以相同模型、床朝向和睡眠条件对照 Sparkle-Morpher，并用不同 YSM 模型回归，禁止模型特化。当前不提前修改睡眠状态或姿态行为。
