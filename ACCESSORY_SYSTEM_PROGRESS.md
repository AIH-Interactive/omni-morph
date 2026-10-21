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
| 2 解析/原生渲染/Lua 实例 | 自动验收 PASS；人工项按用户要求跳过 | PASS | MANUAL/PENDING：真实游戏/GPU/性能矩阵 | 本阶段检查点提交（客户端 main）；后端无改动 |
| 3 挂载/骨骼/拖尾/粒子 | NOT STARTED | PENDING | MANUAL/PENDING | PENDING |
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
